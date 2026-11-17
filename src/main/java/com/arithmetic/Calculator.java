package com.arithmetic;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Pure arithmetic core. No framework, no dependencies - easy to unit test
 * and easy to demonstrate in a lab exam.
 */
public final class Calculator {

    private static final MathContext DIVISION_CONTEXT = new MathContext(16, RoundingMode.HALF_UP);
    private static final int DIVISION_SCALE = 10;

    private Calculator() {
    }

    public enum Operation {
        ADD("add", "+", "Addition"),
        SUBTRACT("subtract", "\u2212", "Subtraction"),
        MULTIPLY("multiply", "\u00d7", "Multiplication"),
        DIVIDE("divide", "\u00f7", "Division");

        private final String id;
        private final String symbol;
        private final String label;

        Operation(String id, String symbol, String label) {
            this.id = id;
            this.symbol = symbol;
            this.label = label;
        }

        public String id() {
            return id;
        }

        public String symbol() {
            return symbol;
        }

        public String label() {
            return label;
        }

        public static Operation from(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("Choose an operation.");
            }
            String value = raw.trim().toLowerCase();
            return switch (value) {
                case "add", "+", "addition" -> ADD;
                case "subtract", "-", "\u2212", "subtraction" -> SUBTRACT;
                case "multiply", "*", "x", "\u00d7", "multiplication" -> MULTIPLY;
                case "divide", "/", "\u00f7", "division" -> DIVIDE;
                default -> throw new IllegalArgumentException("Unknown operation: " + raw);
            };
        }
    }

    public record Result(BigDecimal a, BigDecimal b, Operation operation, BigDecimal value) {
        public String expression() {
            return format(a) + " " + operation.symbol() + " " + format(b);
        }
    }

    public static Result calculate(BigDecimal a, BigDecimal b, Operation operation) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Both operands are required.");
        }
        if (operation == null) {
            throw new IllegalArgumentException("Choose an operation.");
        }

        BigDecimal value = switch (operation) {
            case ADD -> a.add(b);
            case SUBTRACT -> a.subtract(b);
            case MULTIPLY -> a.multiply(b);
            case DIVIDE -> {
                if (b.signum() == 0) {
                    throw new ArithmeticException("Division by zero is undefined.");
                }
                yield a.divide(b, DIVISION_SCALE, RoundingMode.HALF_UP).round(DIVISION_CONTEXT);
            }
        };

        return new Result(a, b, operation, normalise(value));
    }

    public static BigDecimal parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Enter both operands.");
        }
        String value = raw.trim().replace("_", "").replace(",", "");
        if (value.length() > 40) {
            throw new IllegalArgumentException("Keep each number under 40 characters.");
        }
        try {
            return normalise(new BigDecimal(value));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("\"" + raw.trim() + "\" is not a valid number.");
        }
    }

    public static String format(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        BigDecimal normalised = normalise(value);
        String plain = normalised.toPlainString();
        if (plain.length() > 48) {
            return normalised.round(new MathContext(12, RoundingMode.HALF_UP)).toString();
        }
        return plain;
    }

    private static BigDecimal normalise(BigDecimal value) {
        if (value.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
