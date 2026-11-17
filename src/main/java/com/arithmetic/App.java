package com.arithmetic;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Dependency-free Java 17+ HTTP service for the Arithmetic Operations lab.
 *
 * <p>Routes:
 * <ul>
 *   <li>GET  /                  - the calculator UI</li>
 *   <li>GET  /api/health        - Kubernetes readiness/liveness payload</li>
 *   <li>GET  /api/history       - recent calculations for this replica</li>
 *   <li>GET  /api/calculate     - calculate with query parameters</li>
 *   <li>POST /api/calculate     - calculate with form parameters</li>
 * </ul>
 */
public final class App {

    private static final String VERSION = "1.0.0";
    private static final Instant STARTED_AT = Instant.now();
    private static final int MAX_BODY_BYTES = 8_192;
    private static final int MAX_HISTORY = 12;

    private static final String INSTANCE = firstNonBlank(
            System.getenv("POD_NAME"),
            System.getenv("HOSTNAME"),
            "local-" + Long.toHexString(System.nanoTime() & 0xfffff));
    private static final String NODE = firstNonBlank(System.getenv("NODE_NAME"), "local-machine");

    private static final Deque<Map<String, String>> HISTORY = new ArrayDeque<>();
    private static final Object HISTORY_LOCK = new Object();

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "svg", "image/svg+xml",
            "woff2", "font/woff2",
            "png", "image/png",
            "ico", "image/x-icon");

    public static void main(String[] args) throws IOException {
        int port = parsePort(System.getenv("PORT"));
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/", App::handle);
        server.setExecutor(Executors.newFixedThreadPool(16));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Arithmetic service stopping...");
            server.stop(0);
        }));

        server.start();
        System.out.printf("""
                Arithmetic Operations Console %s
                Listening on http://0.0.0.0:%d
                Instance: %s   Node: %s
                Health:   http://localhost:%d/api/health
                """, VERSION, port, INSTANCE, NODE, port);
    }

    private static void handle(HttpExchange exchange) {
        long started = System.nanoTime();
        String path = exchange.getRequestURI().getPath();
        try {
            addSecurityHeaders(exchange.getResponseHeaders());

            if ("/api/health".equals(path)) {
                sendJson(exchange, 200, healthJson());
                return;
            }
            if ("/api/history".equals(path)) {
                sendJson(exchange, 200, historyJson());
                return;
            }
            if ("/api/calculate".equals(path)) {
                handleCalculate(exchange);
                return;
            }
            if ("/favicon.svg".equals(path)) {
                sendResource(exchange, "/static/favicon.svg", "svg", true);
                return;
            }
            if ("/".equals(path) || "/index.html".equals(path)) {
                sendResource(exchange, "/static/index.html", "html", false);
                return;
            }
            if (path.startsWith("/fonts/") || path.startsWith("/assets/")) {
                sendStatic(exchange, path);
                return;
            }
            if ("/app.js".equals(path)) {
                sendResource(exchange, "/static/app.js", "js", true);
                return;
            }
            if ("/styles.css".equals(path)) {
                sendResource(exchange, "/static/styles.css", "css", true);
                return;
            }

            sendJson(exchange, 404, "{\"error\":\"Not found\"}");
        } catch (Exception ex) {
            System.err.println("Request failed: " + ex.getMessage());
            try {
                sendJson(exchange, 500, "{\"error\":\"Internal server error\"}");
            } catch (IOException ignored) {
                // Client disconnected.
            }
        } finally {
            long millis = (System.nanoTime() - started) / 1_000_000;
            System.out.printf("%s %s%n  -> %s (%d ms)%n",
                    exchange.getRequestMethod(), exchange.getRequestURI(), statusLabel(exchange), millis);
        }
    }

    private static void handleCalculate(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"GET".equals(method) && !"POST".equals(method)) {
            exchange.getResponseHeaders().set("Allow", "GET, POST");
            sendJson(exchange, 405, "{\"error\":\"Use GET or POST for this endpoint.\"}");
            return;
        }

        Map<String, String> params = new HashMap<>();
        if ("GET".equals(method)) {
            params.putAll(parseForm(exchange.getRequestURI().getRawQuery()));
        } else {
            String contentType = firstNonBlank(exchange.getRequestHeaders().getFirst("Content-Type"), "");
            if (!contentType.contains("application/x-www-form-urlencoded")) {
                sendJson(exchange, 415, "{\"error\":\"Send form-urlencoded data.\"}");
                return;
            }
            params.putAll(parseForm(readBody(exchange)));
        }

        try {
            Calculator.Operation operation = Calculator.Operation.from(params.get("op"));
            var result = Calculator.calculate(
                    Calculator.parse(params.get("a")),
                    Calculator.parse(params.get("b")),
                    operation);

            String json = new LinkedHashMap<String, String>() {{
                put("a", Calculator.format(result.a()));
                put("b", Calculator.format(result.b()));
                put("operation", result.operation().id());
                put("label", result.operation().label());
                put("symbol", result.operation().symbol());
                put("result", Calculator.format(result.value()));
                put("expression", result.expression() + " = " + Calculator.format(result.value()));
                put("servedBy", INSTANCE);
                put("at", Instant.now().toString());
            }}.entrySet().stream()
                    .map(e -> quote(e.getKey()) + ":" + quote(e.getValue()))
                    .reduce((a, b) -> a + "," + b)
                    .map(body -> "{" + body + "}")
                    .orElse("{}");

            remember(result, json);
            sendJson(exchange, 200, json);
        } catch (IllegalArgumentException ex) {
            sendJson(exchange, 400, errorJson(ex.getMessage()));
        } catch (ArithmeticException ex) {
            sendJson(exchange, 422, errorJson(ex.getMessage()));
        }
    }

    private static void remember(Calculator.Result result, String json) {
        String at = extractJson(json, "at");
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("id", Long.toString(System.nanoTime()));
        entry.put("expression", result.expression() + " = " + Calculator.format(result.value()));
        entry.put("result", Calculator.format(result.value()));
        entry.put("operation", result.operation().id());
        entry.put("symbol", result.operation().symbol());
        entry.put("a", Calculator.format(result.a()));
        entry.put("b", Calculator.format(result.b()));
        entry.put("at", at);

        synchronized (HISTORY_LOCK) {
            HISTORY.addFirst(entry);
            while (HISTORY.size() > MAX_HISTORY) {
                HISTORY.removeLast();
            }
        }
    }

    private static String historyJson() {
        synchronized (HISTORY_LOCK) {
            String body = HISTORY.stream()
                    .map(entry -> entry.entrySet().stream()
                            .map(e -> quote(e.getKey()) + ":" + quote(e.getValue()))
                            .reduce((a, b) -> a + "," + b)
                            .map(items -> "{" + items + "}")
                            .orElse("{}"))
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
            return "{\"items\":[" + body + "],\"servedBy\":" + quote(INSTANCE) + "}";
        }
    }

    private static String healthJson() {
        long uptime = Duration.between(STARTED_AT, Instant.now()).toSeconds();
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        return "{"
                + quote("status") + ":" + quote("ok") + ","
                + quote("service") + ":" + quote("arithmetic-operations") + ","
                + quote("version") + ":" + quote(VERSION) + ","
                + quote("instance") + ":" + quote(INSTANCE) + ","
                + quote("node") + ":" + quote(NODE) + ","
                + quote("uptimeSeconds") + ":" + uptime + ","
                + quote("startedAt") + ":" + quote(STARTED_AT.toString()) + ","
                + quote("javaVersion") + ":" + quote(System.getProperty("java.version")) + ","
                + quote("memoryUsedMb") + ":" + usedMb + ","
                + quote("checks") + ":{\"application\":\"UP\",\"arithmeticCore\":\"UP\"}"
                + "}";
    }

    private static void sendStatic(HttpExchange exchange, String path) throws IOException {
        String relative = path.substring(1);
        if (relative.contains("..") || relative.contains("\\")) {
            sendJson(exchange, 400, "{\"error\":\"Invalid asset path\"}");
            return;
        }
        int dot = relative.lastIndexOf('.');
        String extension = dot >= 0 ? relative.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (!CONTENT_TYPES.containsKey(extension)) {
            sendJson(exchange, 404, "{\"error\":\"Not found\"}");
            return;
        }
        sendResource(exchange, "/static/" + relative, extension, true);
    }

    private static void sendResource(HttpExchange exchange, String resource, String extension,
                                     boolean cacheable) throws IOException {
        try (InputStream input = App.class.getResourceAsStream(resource)) {
            if (input == null) {
                sendJson(exchange, 404, "{\"error\":\"Resource not found\"}");
                return;
            }
            byte[] bytes = input.readAllBytes();
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", CONTENT_TYPES.getOrDefault(extension, "application/octet-stream"));
            headers.set("Cache-Control", cacheable ? "public, max-age=3600" : "no-store");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void addSecurityHeaders(Headers headers) {
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Content-Security-Policy",
                "default-src 'self'; style-src 'self'; script-src 'self'; "
                        + "img-src 'self' data:; font-src 'self'; connect-src 'self'; "
                        + "base-uri 'none'; form-action 'self'");
    }

    private static Map<String, String> parseForm(String raw) {
        Map<String, String> values = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return values;
        }
        for (String pair : raw.split("&")) {
            int equals = pair.indexOf('=');
            String key = equals >= 0 ? pair.substring(0, equals) : pair;
            String value = equals >= 0 ? pair.substring(equals + 1) : "";
            values.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return values;
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            byte[] bytes = input.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                throw new IllegalArgumentException("Request body is too large.");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static String errorJson(String message) {
        return "{\"error\":" + quote(message == null ? "Invalid calculation." : message) + "}";
    }

    private static String quote(String value) {
        return "\"" + escape(value) + "\"";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private static String extractJson(String json, String key) {
        String marker = "\"" + key + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            return Instant.now().toString();
        }
        start += marker.length();
        int end = json.indexOf('"', start);
        return end > start ? json.substring(start, end) : Instant.now().toString();
    }

    private static int parsePort(String raw) {
        if (raw == null || raw.isBlank()) {
            return 8080;
        }
        try {
            int port = Integer.parseInt(raw.trim());
            return port > 0 && port < 65_536 ? port : 8080;
        } catch (NumberFormatException ex) {
            return 8080;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String statusLabel(HttpExchange exchange) {
        int code = exchange.getResponseCode();
        return code > 0 ? Integer.toString(code) : "closed";
    }
}
