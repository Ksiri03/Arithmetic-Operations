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
    history: [],
  };

  const els = {
    form: document.getElementById("calcForm"),
    a: document.getElementById("a"),
    b: document.getElementById("b"),
    operator: document.getElementById("operator"),
    result: document.getElementById("result"),
    expression: document.getElementById("expression"),
    resultUnit: document.getElementById("resultUnit"),
    message: document.getElementById("message"),
    button: document.getElementById("calculateButton"),
    buttonLabel: document.querySelector("#calculateButton span:first-child"),
    clear: document.getElementById("clearButton"),
    copy: document.getElementById("copyButton"),
    history: document.getElementById("history"),
    historyCount: document.getElementById("historyCount"),
    servedBy: document.getElementById("servedBy"),
    status: document.getElementById("status"),
    statusText: document.getElementById("statusText"),
    podName: document.getElementById("podName"),
    nodeName: document.getElementById("nodeName"),
    version: document.getElementById("version"),
    uptime: document.getElementById("uptime"),
  };

  const defaultMessage = "Press Enter to evaluate. Answers are computed on the server.";

  function formatGrouped(value) {
    if (value === null || value === undefined) return "—";
    const negative = String(value).startsWith("-");
    const raw = negative ? String(value).slice(1) : String(value);
    if (/[eE]/.test(raw)) return (negative ? "−" : "") + raw;
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

  function updatePreview() {
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
    els.operator.classList.remove("is-switching");
    void els.operator.offsetWidth;
    els.operator.classList.add("is-switching");
    window.setTimeout(() => els.operator.classList.remove("is-switching"), 260);
    updatePreview();
  }

  function markInvalid() {
    els.a.closest(".field").classList.toggle("is-invalid", !els.a.value.trim());
    els.b.closest(".field").classList.toggle("is-invalid", !els.b.value.trim());
  }

  function animateResult() {
    els.result.classList.remove("is-settled");
    void els.result.offsetWidth;
    els.result.classList.add("is-settled");
  }

  function showResult(value, expression) {
    els.result.classList.remove("is-error");
    els.result.textContent = formatGrouped(value);
    els.expression.textContent = expression || "";
    els.resultUnit.textContent = "answer";
    animateResult();
  }

  function showError(message) {
    els.result.classList.remove("is-error");
    els.result.textContent = "Not defined";
    els.result.classList.add("is-error");
    els.resultUnit.textContent = "check input";
    els.message.textContent = message;
    els.message.classList.add("is-error");
    animateResult();
  }

  async function calculate(options = {}) {
    if (state.busy) return;
    const a = els.a.value.trim();
    const b = els.b.value.trim();

    markInvalid();
    if (!a || !b) {
      setMessage("Enter both operands before calculating.", true);
      els.a.closest(".field").classList.toggle("is-invalid", !a);
      els.b.closest(".field").classList.toggle("is-invalid", !b);
      (!a ? els.a : els.b).focus();
      return;
    }

    state.busy = true;
    els.button.disabled = true;
    els.buttonLabel.textContent = "Calculating";
    setMessage("Sending to the Java service…");

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

      state.lastResult = payload.result;
      showResult(payload.result, payload.expression);
      els.servedBy.textContent = "served by " + payload.servedBy;
      els.podName.textContent = payload.servedBy;
      setMessage(options.silent ? defaultMessage : "Calculated on " + payload.servedBy + ".");
      await loadHistory();
    } catch (error) {
      showError("The service is unreachable. Check the pod and try again.");
    } finally {
      state.busy = false;
      els.button.disabled = false;
      els.buttonLabel.textContent = "Calculate";
    }
  }

  function renderHistory(items) {
    state.history = items;
    els.history.replaceChildren();
    els.historyCount.textContent = "Last 12";

    if (!items.length) {
      const empty = document.createElement("li");
      empty.className = "tape__empty";
      empty.textContent = "No calculations yet. The last twelve will appear here.";
      els.history.append(empty);
      return;
    }

    items.forEach((item, index) => {
      const li = document.createElement("li");
      const button = document.createElement("button");
      button.type = "button";
      button.className = "tape__item";
      button.title = "Load this calculation";

      const number = document.createElement("span");
      number.className = "tape__index";
      number.textContent = String(index + 1).padStart(2, "0");

      const expression = document.createElement("span");
      expression.className = "tape__expression";
      expression.textContent = item.expression;

      const time = document.createElement("span");
      time.className = "tape__time";
      time.textContent = new Date(item.at).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

      button.append(number, expression, time);
      button.addEventListener("click", () => {
        els.a.value = item.a;
        els.b.value = item.b;
        setOperation(item.operation);
        calculate();
      });

      li.append(button);
      els.history.append(li);
    });
  }

  async function loadHistory() {
    try {
      const response = await fetch("/api/history", { cache: "no-store" });
      if (!response.ok) return [];
      const payload = await response.json();
      const items = Array.isArray(payload.items) ? payload.items : [];
      renderHistory(items);
      return items;
    } catch (error) {
      // The calculator remains usable even if history is unavailable.
      return [];
    }
  }

  async function loadHealth() {
    try {
      const response = await fetch("/api/health", { cache: "no-store" });
      if (!response.ok) throw new Error("offline");
      const health = await response.json();
      els.status.classList.remove("is-down");
      els.statusText.textContent = "Live";
      els.podName.textContent = health.instance || "unknown";
      els.nodeName.textContent = health.node || "unknown";
      els.version.textContent = "v" + (health.version || "—");
      els.uptime.textContent = formatUptime(health.uptimeSeconds);
      els.servedBy.textContent = "served by " + (health.instance || "unknown");
    } catch (error) {
      els.status.classList.add("is-down");
      els.statusText.textContent = "Offline";
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
    els.result.classList.remove("is-error", "is-settled");
    els.resultUnit.textContent = "answer";
    state.lastResult = null;
    els.a.closest(".field").classList.remove("is-invalid");
    els.b.closest(".field").classList.remove("is-invalid");
    setMessage(defaultMessage);
    updatePreview();
    els.a.focus();
  }

  async function copyResult() {
    if (state.lastResult === null) {
      setMessage("Calculate something first.", true);
      return;
    }
    try {
      await navigator.clipboard.writeText(state.lastResult);
      els.copy.textContent = "Copied";
      window.setTimeout(() => { els.copy.textContent = "Copy result"; }, 1400);
    } catch (error) {
      setMessage("Clipboard access is unavailable in this browser.", true);
    }
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
      input.closest(".field").classList.remove("is-invalid");
      updatePreview();
    });
  });

  els.clear.addEventListener("click", clearForm);
  els.copy.addEventListener("click", copyResult);

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") clearForm();
  });

  loadHealth();
  loadHistory().then((items) => {
    if (!items.length) {
      calculate({ silent: true });
      return;
    }
    const latest = items[0];
    els.a.value = latest.a;
    els.b.value = latest.b;
    state.lastResult = latest.result;
    setOperation(latest.operation);
    showResult(latest.result, latest.expression);
    setMessage(defaultMessage);
  });
  window.setInterval(loadHealth, 20000);
})();
