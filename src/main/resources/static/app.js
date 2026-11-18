(() => {
  "use strict";

  const SYMBOLS = {
    add: "+",
    subtract: "−",
    multiply: "×",
    divide: "÷",
  };

  const state = {
    operation: "add",
    busy: false,
    lastResult: null,
  };

  const els = {
    form: document.getElementById("calcForm"),
    a: document.getElementById("a"),
    b: document.getElementById("b"),
    operator: document.getElementById("operatorSymbol"),
    result: document.getElementById("result"),
    expression: document.getElementById("expression"),
    message: document.getElementById("message"),
    calculate: document.getElementById("calculateButton"),
    clear: document.getElementById("clearButton"),
    history: document.getElementById("history"),
    historyCount: document.getElementById("historyCount"),
    status: document.getElementById("status"),
    statusText: document.getElementById("statusText"),
    serverStatus: document.getElementById("serverStatus"),
    podName: document.getElementById("podName"),
    nodeName: document.getElementById("nodeName"),
    version: document.getElementById("version"),
    uptime: document.getElementById("uptime"),
  };

  const defaultMessage = "Press Calculate to evaluate.";

  function formatNumber(value) {
    if (value === null || value === undefined) return "—";
    const text = String(value);
    if (/[eE]/.test(text)) return text.replace("-", "−");
    const negative = text.startsWith("-");
    const raw = negative ? text.slice(1) : text;
    const [integer, fraction] = raw.split(".");
    const grouped = (integer || "0").replace(/\B(?=(\d{3})+(?!\d))/g, ",");
    return (negative ? "−" : "") + grouped + (fraction ? "." + fraction : "");
  }

  function formatUptime(seconds) {
    const value = Number(seconds) || 0;
    if (value < 60) return value + " sec";
    if (value < 3600) return Math.floor(value / 60) + " min";
    if (value < 86400) return Math.floor(value / 3600) + " h " + Math.floor((value % 3600) / 60) + " m";
    return Math.floor(value / 86400) + " d " + Math.floor((value % 86400) / 3600) + " h";
  }

  function setMessage(text, isError = false) {
    els.message.textContent = text || defaultMessage;
    els.message.classList.toggle("is-error", Boolean(isError));
  }

  function updateExpression() {
    const a = els.a.value.trim();
    const b = els.b.value.trim();
    const symbol = SYMBOLS[state.operation];
    els.expression.textContent = a && b ? a + " " + symbol + " " + b : "A " + symbol + " B";
  }

  function setOperation(operation) {
    if (!SYMBOLS[operation]) return;
    state.operation = operation;
    document.querySelectorAll(".operation").forEach((button) => {
      const active = button.dataset.op === operation;
      button.classList.toggle("is-active", active);
      button.setAttribute("aria-pressed", String(active));
    });
    els.operator.textContent = SYMBOLS[operation];
    updateExpression();
  }

  function clearInvalid() {
    els.a.classList.remove("is-invalid");
    els.b.classList.remove("is-invalid");
  }

  function showResult(value, expression) {
    els.result.textContent = formatNumber(value);
    els.result.classList.remove("is-error");
    els.expression.textContent = expression || "";
    state.lastResult = value;
  }

  function showError(text) {
    els.result.textContent = "Error";
    els.result.classList.add("is-error");
    els.expression.textContent = "";
    setMessage(text, true);
  }

  async function calculate() {
    if (state.busy) return;

    const a = els.a.value.trim();
    const b = els.b.value.trim();
    clearInvalid();

    if (!a || !b) {
      if (!a) els.a.classList.add("is-invalid");
      if (!b) els.b.classList.add("is-invalid");
      setMessage("Please enter both numbers.", true);
      (!a ? els.a : els.b).focus();
      return;
    }

    state.busy = true;
    els.calculate.disabled = true;
    els.calculate.textContent = "Calculating...";
    setMessage("Sending request to the Java server...");

    try {
      const response = await fetch("/api/calculate", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({ a, b, op: state.operation }),
        cache: "no-store",
      });

      const payload = await response.json().catch(() => ({}));

      if (!response.ok) {
        showError(payload.error || "The calculation could not be completed.");
        return;
      }

      showResult(payload.result, payload.expression);
      els.podName.textContent = payload.servedBy || "—";
      setMessage("Calculated on " + payload.servedBy + ".");
      await loadHistory();
    } catch (error) {
      showError("The server is unreachable. Check the pod and try again.");
    } finally {
      state.busy = false;
      els.calculate.disabled = false;
      els.calculate.textContent = "Calculate";
    }
  }

  function renderHistory(items) {
    els.history.replaceChildren();
    els.historyCount.textContent = String(items.length);

    if (!items.length) {
      const empty = document.createElement("li");
      empty.className = "history-empty";
      empty.textContent = "No calculations yet.";
      els.history.append(empty);
      return;
    }

    items.forEach((item) => {
      const li = document.createElement("li");
      const button = document.createElement("button");
      button.type = "button";
      button.className = "history-item";
      button.title = "Load this calculation";

      const expression = document.createElement("span");
      expression.className = "history-expression";
      expression.textContent = item.expression;

      const time = document.createElement("span");
      time.className = "history-time";
      time.textContent = new Date(item.at).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

      button.append(expression, time);
      button.addEventListener("click", () => {
        els.a.value = item.a;
        els.b.value = item.b;
        setOperation(item.operation);
        showResult(item.result, item.expression);
        setMessage("Loaded from recent calculations.");
      });

      li.append(button);
      els.history.append(li);
    });
  }

  async function loadHistory() {
    try {
      const response = await fetch("/api/history", { cache: "no-store" });
      if (!response.ok) return;
      const payload = await response.json();
      renderHistory(Array.isArray(payload.items) ? payload.items : []);
    } catch (error) {
      // The calculator stays usable when history is temporarily unavailable.
    }
  }

  async function loadHealth() {
    try {
      const response = await fetch("/api/health", { cache: "no-store" });
      if (!response.ok) throw new Error("offline");
      const health = await response.json();

      els.status.classList.remove("is-offline");
      els.status.classList.add("is-online");
      els.statusText.textContent = "Online";
      els.serverStatus.className = "badge badge-green";
      els.serverStatus.textContent = "Online";

      els.podName.textContent = health.instance || "unknown";
      els.nodeName.textContent = health.node || "unknown";
      els.version.textContent = "v" + (health.version || "—");
      els.uptime.textContent = formatUptime(health.uptimeSeconds);
    } catch (error) {
      els.status.classList.remove("is-online");
      els.status.classList.add("is-offline");
      els.statusText.textContent = "Offline";
      els.serverStatus.className = "badge badge-red";
      els.serverStatus.textContent = "Offline";
      els.podName.textContent = "unreachable";
      els.nodeName.textContent = "—";
      els.version.textContent = "—";
      els.uptime.textContent = "—";
    }
  }

  function clearForm() {
    els.a.value = "";
    els.b.value = "";
    els.result.textContent = "—";
    els.result.classList.remove("is-error");
    state.lastResult = null;
    clearInvalid();
    updateExpression();
    setMessage(defaultMessage);
    els.a.focus();
  }

  document.querySelectorAll(".operation").forEach((button) => {
    button.addEventListener("click", () => setOperation(button.dataset.op));
  });

  els.form.addEventListener("submit", (event) => {
    event.preventDefault();
    calculate();
  });

  [els.a, els.b].forEach((input) => {
    input.addEventListener("input", () => {
      input.classList.remove("is-invalid");
      updateExpression();
    });
  });

  els.clear.addEventListener("click", clearForm);

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") clearForm();
  });

  loadHealth();
  loadHistory();
  window.setInterval(loadHealth, 20000);
})();
