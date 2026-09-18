"use strict";
const $ = (selector) => document.querySelector(selector);
const state = {
  conversationId: crypto.randomUUID(),
  runs: new Map(),
  configured: false,

  pinned: true,
  chats: [],
  activeId: null,
  lastSaved: 0,
  storageWarning: false,
};
const messages = $("#messages");
const input = $("#query");
const user = $("#user");
const scrollArea = $("#scroll-area");
const toolNames = {
  list_tables: "查看业务数据表",
  describe_table: "读取表结构",
  validate_sql: "校验 SQL",
  execute_sql: "执行只读查询",
  current_time: "确认当前时间",
};
let toastTimer;

const preferences = { theme: "white", typing: true, speed: "natural" };
try {
  const saved = JSON.parse(
    localStorage.getItem("qiqi.preferences.v1") || "null",
  );
  if (["white", "sage"].includes(saved?.theme)) preferences.theme = saved.theme;
  if (typeof saved?.typing === "boolean") preferences.typing = saved.typing;
  if (["relaxed", "natural", "fast"].includes(saved?.speed))
    preferences.speed = saved.speed;
} catch {
  /* Use defaults when browser storage is unavailable. */
}
function applyPreferences(save = false) {
  document.documentElement.dataset.theme = preferences.theme;
  document
    .querySelectorAll('[name="theme"]')
    .forEach((radio) => (radio.checked = radio.value === preferences.theme));
  $("#typing-enabled").checked = preferences.typing;
  $("#typing-speed").value = preferences.speed;
  $("#typing-speed").disabled = !preferences.typing;
  if (save) {
    try {
      localStorage.setItem("qiqi.preferences.v1", JSON.stringify(preferences));
      $("#settings-feedback").textContent = "已自动保存";
    } catch {
      $("#settings-feedback").textContent =
        "浏览器存储不可用，设置仅在本次访问生效";
    }
  }
}
applyPreferences();
function closeSettings() {
  $("#settings-page").hidden = true;
  $(".workspace").inert = false;
  $("#open-settings").setAttribute("aria-expanded", "false");
  $("#open-settings").focus();
}
$("#open-settings").addEventListener("click", () => {
  $("#settings-page").hidden = false;
  $(".workspace").inert = true;
  $("#open-settings").setAttribute("aria-expanded", "true");
  $("#close-settings").focus();
});
$("#close-settings").addEventListener("click", closeSettings);
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && !$("#settings-page").hidden) closeSettings();
});
document.querySelectorAll('[name="theme"]').forEach((radio) =>
  radio.addEventListener("change", () => {
    preferences.theme = radio.value;
    applyPreferences(true);
  }),
);
$("#typing-enabled").addEventListener("change", (event) => {
  preferences.typing = event.target.checked;
  applyPreferences(true);
});
$("#typing-speed").addEventListener("change", (event) => {
  preferences.speed = event.target.value;
  applyPreferences(true);
});

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}
function toast(text) {
  $("#toast").textContent = text;
  $("#toast").hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    $("#toast").hidden = true;
  }, 2400);
}
async function copy(text) {
  try {
    await navigator.clipboard.writeText(text);
    toast("已复制");
  } catch {
    toast("复制失败，请选择内容后手动复制");
  }
}
function follow() {
  if (state.pinned) scrollArea.scrollTop = scrollArea.scrollHeight;
}
scrollArea.addEventListener(
  "scroll",
  () => {
    state.pinned =
      scrollArea.scrollHeight - scrollArea.scrollTop - scrollArea.clientHeight <
      90;
    $("#jump-latest").hidden = state.pinned || !messages.childElementCount;
  },
  { passive: true },
);
$("#jump-latest").addEventListener("click", () => {
  state.pinned = true;
  follow();
});

// Parse Markdown, then allow only report markup. Model-provided HTML never gets
// access to application controls, images, scripts, styles or event handlers.
function renderMarkdown(target, markdown) {
  const html = marked.parse(markdown, { gfm: true, breaks: false });
  target.innerHTML = DOMPurify.sanitize(html, {
    ALLOWED_TAGS: [
      "p",
      "br",
      "h1",
      "h2",
      "h3",
      "h4",
      "h5",
      "h6",
      "strong",
      "em",
      "del",
      "ul",
      "ol",
      "li",
      "blockquote",
      "hr",
      "pre",
      "code",
      "table",
      "thead",
      "tbody",
      "tr",
      "th",
      "td",
      "a",
    ],
    ALLOWED_ATTR: ["href", "title", "colspan", "rowspan", "start"],
    ALLOW_DATA_ATTR: false,
    ALLOW_ARIA_ATTR: false,
  });
  target.querySelectorAll("a").forEach((link) => {
    const href = link.getAttribute("href") || "";
    if (!/^(https?:\/\/|mailto:|#)/i.test(href)) link.removeAttribute("href");
    if (/^https?:\/\//i.test(href)) {
      link.target = "_blank";
      link.rel = "noopener noreferrer";
    }
  });
  target.querySelectorAll("table").forEach((table) => {
    const wrap = element("div", "table-wrap");
    wrap.tabIndex = 0;
    wrap.setAttribute("role", "region");
    wrap.setAttribute("aria-label", "数据表格，可横向滚动");
    table.replaceWith(wrap);
    wrap.append(table);
  });
  target.querySelectorAll("pre").forEach((pre) => {
    const wrap = element("div", "code-block");
    const toolbar = element("div", "code-toolbar");
    const code = pre.querySelector("code");
    const text = code ? code.textContent : pre.textContent;
    toolbar.append(
      element(
        "span",
        "",
        /^\s*(SELECT|WITH|EXPLAIN)\b/i.test(text) ? "SQL" : "代码",
      ),
    );
    const button = element("button", "", "复制代码");
    button.type = "button";
    button.addEventListener("click", () => copy(text));
    toolbar.append(button);
    pre.replaceWith(wrap);
    wrap.append(toolbar, pre);
  });
}
function currentRun() {
  return state.runs.get(state.activeId);
}
function refreshControls() {
  const run = currentRun();
  $("#send").hidden = Boolean(run);
  $("#stop").hidden = !run;
  $("#send").disabled = !state.configured || !input.value.trim();
  // Identity changes wait for all requests, so replies cannot cross user stores.
  user.disabled = state.runs.size > 0 || !user.options.length;
  $("#new-chat").disabled = false;
  $("#status").textContent = run
    ? run.response.progress.textContent
    : activeChat()
      ? activeChat().turns.at(-1)?.status || "可以继续追问"
      : state.configured
        ? "准备好，开始探索数据"
        : "请先配置模型";
}
const historyKey = () => `qiqi.conversations.v1.${user.value}`;
function activeChat() {
  return state.chats.find((chat) => chat.id === state.activeId);
}
function saveHistory() {
  if (!user.value) return;
  try {
    localStorage.setItem(
      historyKey(),
      JSON.stringify({ chats: state.chats, activeId: state.activeId }),
    );
    state.lastSaved = Date.now();
  } catch {
    if (!state.storageWarning)
      toast("浏览器存储不可用或已满，本次记录暂未保存，请下载报告。");
    state.storageWarning = true;
  }
}
function renderHistory() {
  const history = $("#history");
  history.replaceChildren();
  $("#history-count").textContent = state.chats.length;
  $("#conversation-title").textContent = activeChat()?.title || "新建分析";
  if (!state.chats.length)
    history.append(element("p", "history-empty", "还没有对话，开始一次分析吧"));
  for (const chat of [...state.chats].sort(
    (a, b) => b.updatedAt - a.updatedAt,
  )) {
    const row = element("div", "history-row");
    row.classList.toggle("selected", chat.id === state.activeId);
    const button = element("button", "history-open");
    button.title = chat.title;
    button.disabled = false;
    button.setAttribute(
      "aria-current",
      chat.id === state.activeId ? "true" : "false",
    );
    button.append(
      element("span", "history-title", chat.title),
      element(
        "span",
        "history-date",
        new Date(chat.updatedAt).toLocaleDateString("zh-CN", {
          month: "numeric",
          day: "numeric",
        }) + ` · ${chat.turns.length} 轮对话`,
      ),
    );
    button.addEventListener("click", () => openConversation(chat.id));
    if (state.runs.has(chat.id)) {
      row.classList.add("running");
      button.querySelector(".history-date").textContent = "正在分析…";
    } else if (chat.unread) {
      row.classList.add("unread");
      button.querySelector(".history-date").textContent = "新回复 · 点击查看";
    }
    const remove = element("button", "history-delete");
    remove.innerHTML =
      '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 6h18M9 6V4h6v2M5 6l1 14h12l1-14M10 10v6M14 10v6"/></svg>';
    remove.title = "删除对话";
    remove.setAttribute("aria-label", `删除对话：${chat.title}`);
    remove.addEventListener("click", () => requestDelete(chat.id));
    row.append(button, remove);
    history.append(row);
  }
}
let deletionId = null;
function requestDelete(id) {
  const chat = state.chats.find((item) => item.id === id);
  if (!chat) return;
  deletionId = id;
  $("#delete-title").textContent = chat.title;
  $("#delete-description").textContent = state.runs.has(id)
    ? "这条分析仍在生成。删除会同时停止生成，并移除本地对话记录。"
    : "删除后，这条对话及其分析记录将从当前浏览器移除。";
  $("#confirm-delete").textContent = state.runs.has(id)
    ? "停止并删除"
    : "删除对话";
  $("#delete-dialog").showModal();
}
$("#cancel-delete").addEventListener("click", () =>
  $("#delete-dialog").close(),
);
$("#confirm-delete").addEventListener("click", () => {
  const id = deletionId;
  state.runs.get(id)?.controller.abort();
  state.chats = state.chats.filter((chat) => chat.id !== id);
  if (state.activeId === id) resetConversation();
  saveHistory();
  renderHistory();
  $("#delete-dialog").close();
  toast("对话已删除");
});
function loadHistory() {
  state.chats = [];
  state.activeId = null;
  try {
    const saved = JSON.parse(localStorage.getItem(historyKey()) || "null");
    if (Array.isArray(saved?.chats)) {
      state.chats = saved.chats.filter(
        (chat) =>
          typeof chat.id === "string" &&
          typeof chat.title === "string" &&
          Array.isArray(chat.turns),
      );
      for (const chat of state.chats) {
        // Refresh during a stream keeps the partial response, but does not reuse
        // a server turn that may still be running.
        if (chat.turns.some((turn) => turn.pending)) {
          chat.runtimeId = crypto.randomUUID();
          for (const turn of chat.turns.filter((turn) => turn.pending)) {
            turn.pending = false;
            turn.status = "生成已中断 · 已保留部分内容";
          }
        }
      }
      const chat = state.chats.find((item) => item.id === saved.activeId);
      if (chat) {
        openConversation(chat.id);
        return;
      }
    }
  } catch {
    toast("历史记录暂时无法读取");
  }
  resetConversation();
}
function openConversation(id) {
  if (!$("#settings-page").hidden) closeSettings();
  const chat = state.chats.find((item) => item.id === id);
  if (!chat) return;
  if (activeChat()) activeChat().draft = input.value;
  chat.unread = false;
  state.activeId = chat.id;
  state.conversationId = chat.runtimeId;
  messages.replaceChildren();
  input.value = chat.draft || "";
  resizeInput();
  $("#welcome").hidden = true;
  for (const turn of chat.turns) {
    messages.append(element("article", "message user", turn.query));
    const live = state.runs.get(chat.id);
    if (live?.response.savedTurn === turn) {
      messages.append(live.response.card);
      continue;
    }
    const response = createResponse(turn.query, turn.time);
    response.text = turn.text || "";
    render(response);
    response.progress.classList.remove("busy");
    response.progress.textContent = turn.status || "分析完成";
    response.error.textContent = turn.error || "";
    response.error.hidden = !turn.error;
    for (const step of turn.steps || [])
      response.steps.append(
        element("li", step.failed ? "failed" : "", step.text),
      );
    response.trace.hidden = !response.steps.children.length;
    response.summary.textContent = `分析过程 · ${response.steps.children.length} 次工具调用`;
    for (const raw of turn.evidence || []) addEvidence(response, raw);
    response.actions.hidden = !response.text;
  }
  state.pinned = true;
  follow();
  saveHistory();
  renderHistory();
  refreshControls();
}
function resetConversation() {
  if (activeChat()) activeChat().draft = input.value;
  state.activeId = null;
  state.conversationId = crypto.randomUUID();
  messages.replaceChildren();
  input.value = "";
  input.style.height = "";
  $("#welcome").hidden = false;
  $("#jump-latest").hidden = true;
  state.pinned = true;
  scrollArea.scrollTop = 0;
  $("#status").textContent = state.configured
    ? "准备好，开始探索数据"
    : "请先配置模型";
  saveHistory();
  renderHistory();
  refreshControls();
  input.focus();
}
function captureResponse(response, force = false) {
  if (!response.savedTurn) return;
  Object.assign(response.savedTurn, {
    text: response.text,
    status: response.progress.textContent,
    error: response.error.hidden ? "" : response.error.textContent,
    steps: [...response.steps.children].map((step) => ({
      text: step.textContent,
      failed: step.classList.contains("failed"),
    })),
    evidence: [...response.results.values()],
  });
  if (force || Date.now() - state.lastSaved > 1000) saveHistory();
}
window.addEventListener("pagehide", () => {
  for (const run of state.runs.values()) captureResponse(run.response);
  saveHistory();
});
function updateScope() {
  const option = user.selectedOptions[0];
  $("#scope").textContent =
    option?.dataset.scope === "ALL" ? "全部数据" : "本部门数据";
}
async function loadMeta() {
  const response = await fetch("/api/meta");
  if (!response.ok) throw new Error("服务暂不可用，请稍后刷新页面");
  const meta = await response.json();
  for (const item of meta.demoUsers) {
    const option = element("option", "", item.displayName);
    option.value = item.username;
    option.dataset.scope = item.dataScope;
    user.append(option);
  }
  state.configured = Boolean(
    meta.modelConfigured &&
    user.options.length &&
    window.marked &&
    window.DOMPurify,
  );
  $(".connection").classList.add("ready");
  $("#connection-label").textContent = "示例数据库已连接";
  if (!state.configured) {
    $("#notice").textContent =
      !window.marked || !window.DOMPurify
        ? "页面资源加载失败，请刷新后重试。"
        : "模型尚未配置，暂时无法发起分析。请完成本地模型配置后刷新页面。";
    $("#notice").hidden = false;
  }
  updateScope();
  loadHistory();
  refreshControls();
}
function createResponse(query, timestamp = new Date().toISOString()) {
  const card = element("article", "message assistant");
  const heading = element("div", "assistant-heading");
  const time = element(
    "time",
    "",
    new Date(timestamp).toLocaleTimeString("zh-CN", {
      hour: "2-digit",
      minute: "2-digit",
    }),
  );
  time.dateTime = timestamp;
  heading.append(
    element("span", "mini-mark", "Q"),
    element("strong", "", "Qiqi"),
    element("span", "report-label", "数据分析"),
    time,
  );
  const trace = element("details", "tool-trace");
  trace.hidden = true;
  const summary = element("summary", "", "分析过程");
  const steps = element("ol");
  trace.append(summary, steps);
  const report = element("div", "report");
  const progress = element("div", "response-status busy", "正在理解你的问题…");
  const error = element("div", "response-error");
  error.hidden = true;
  error.setAttribute("role", "alert");
  const evidence = element("div", "query-evidence");
  const actions = element("div", "report-actions");
  actions.hidden = true;
  const response = {
    card,
    report,
    progress,
    error,
    trace,
    summary,
    steps,
    actions,
    evidence,
    query,
    text: "",
    tools: new Map(),
    results: new Map(),
    replyId: null,
    renderTimer: null,
    visibleLength: 0,
    done: false,
    failed: false,
    queries: new Set(),
  };
  const copyButton = element("button", "", "复制 Markdown");
  copyButton.addEventListener("click", () => copy(response.text));
  const exportButton = element("button", "", "下载报告");
  exportButton.addEventListener("click", () => {
    const url = URL.createObjectURL(
      new Blob([response.text], { type: "text/markdown;charset=utf-8" }),
    );
    const link = element("a");
    link.href = url;
    link.download = `Qiqi-分析报告-${new Date().toISOString().slice(0, 10)}.md`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  });
  actions.append(copyButton, exportButton);
  card.append(heading, trace, report, progress, error, evidence, actions);
  messages.append(card);
  return response;
}
function paintResponse(response, text) {
  if (text) renderMarkdown(response.report, text);
  captureResponse(response);
  if (response.card.isConnected) follow();
}
function render(response) {
  clearTimeout(response.renderTimer);
  response.renderTimer = null;
  response.visibleLength = response.text.length;
  response.report.classList.remove("typing");
  paintResponse(response, response.text);
}
function scheduleRender(response) {
  if (response.renderTimer) return;
  response.renderTimer = setTimeout(() => {
    response.renderTimer = null;
    const remaining = Array.from(response.text.slice(response.visibleLength));
    const animate =
      preferences.typing &&
      !window.matchMedia("(prefers-reduced-motion: reduce)").matches &&
      !document.hidden;
    const base = { relaxed: 1, natural: 2, fast: 4 }[preferences.speed];
    // Catch up gently when the gateway delivers a large burst. Full received
    // text is saved independently so interruption never drops buffered output.
    const count = animate
      ? Math.max(base, Math.ceil(remaining.length / 65))
      : remaining.length;
    response.visibleLength += remaining.slice(0, count).join("").length;
    response.report.classList.toggle("typing", animate);
    paintResponse(response, response.text.slice(0, response.visibleLength));
    if (response.visibleLength < response.text.length) scheduleRender(response);
  }, 32);
}
function finishTyping(response, signal) {
  return new Promise((resolve) => {
    let timer;
    const finish = () => {
      clearTimeout(timer);
      signal.removeEventListener("abort", finish);
      resolve();
    };
    const check = () => {
      if (signal.aborted || response.visibleLength >= response.text.length)
        finish();
      else {
        scheduleRender(response);
        timer = setTimeout(check, 32);
      }
    };
    signal.addEventListener("abort", finish, { once: true });
    check();
  });
}
function addEvidence(response, raw) {
  try {
    let result = JSON.parse(raw);
    if (typeof result === "string") result = JSON.parse(result);
    if (
      !result.queryId ||
      !result.executedSql ||
      response.queries.has(result.queryId)
    )
      return;
    response.queries.add(result.queryId);
    const details = element("details", "evidence-item");
    details.append(
      element(
        "summary",
        "",
        `查询证据 · ${result.rowCount} 行 · ${result.durationMs} ms`,
      ),
    );
    const id = element("div", "evidence-id", `queryId: ${result.queryId}`);
    const sql = element("div", "report");
    // Render SQL as text nodes so neither SQL nor gateway data can inject markup.
    const block = element("div", "code-block");
    const toolbar = element("div", "code-toolbar");
    const button = element("button", "", "复制 SQL");
    button.addEventListener("click", () => copy(result.executedSql));
    toolbar.append(element("span", "", "实际执行 SQL"), button);
    const pre = element("pre");
    pre.append(element("code", "", result.executedSql));
    block.append(toolbar, pre);
    sql.append(block);
    details.append(id, sql);
    response.evidence.append(details);
  } catch {
    /* Tool errors and partial JSON do not create evidence. */
  }
}
function handleEvent(event, response) {
  const data = event.data || {};
  switch (event.type) {
    case "TEXT_BLOCK_DELTA":
      if (
        response.replyId &&
        data.replyId !== response.replyId &&
        response.text
      )
        response.text += "\n\n";
      response.replyId = data.replyId;
      response.text += data.delta || "";
      response.progress.textContent = "正在生成分析…";
      scheduleRender(response);
      break;
    case "TOOL_CALL_START": {
      response.trace.hidden = false;
      if (!response.tools.has(data.toolCallId)) {
        const step = element(
          "li",
          "",
          toolNames[data.toolCallName] || "处理数据",
        );
        response.tools.set(data.toolCallId, step);
        response.steps.append(step);
      }
      const label = toolNames[data.toolCallName] || "处理数据";
      response.summary.textContent = `分析中 · ${label}`;
      response.progress.textContent = label + "…";
      if (response.card.isConnected) {
        refreshControls();
        follow();
      }
      break;
    }
    case "TOOL_RESULT_TEXT_DELTA":
      if (data.toolCallName === "execute_sql")
        response.results.set(
          data.toolCallId,
          (response.results.get(data.toolCallId) || "") + (data.delta || ""),
        );
      break;
    case "TOOL_RESULT_END": {
      const step = response.tools.get(data.toolCallId);
      if (step) {
        const failed = data.state === "error";
        step.textContent += failed ? " · 未成功" : " · 完成";
        step.classList.toggle("failed", failed);
      }
      if (response.results.has(data.toolCallId))
        addEvidence(response, response.results.get(data.toolCallId));
      break;
    }
    case "ERROR":
      response.failed = true;
      response.error.hidden = false;
      response.error.textContent = data.message || "分析失败，请稍后重试。";
      break;
    case "AGENT_END":
      response.done = true;
      break;
  }
}
async function consumeStream(body, response) {
  if (!body) throw new Error("未收到响应内容，请重试。");
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  const dispatch = (block) => {
    const payload = block
      .split(/\r?\n/)
      .filter((line) => line.startsWith("data:"))
      .map((line) => line.slice(5).trimStart())
      .join("\n");
    if (payload && payload !== "[DONE]")
      handleEvent(JSON.parse(payload), response);
  };
  try {
    while (true) {
      const { value, done } = await reader.read();
      buffer += done
        ? decoder.decode()
        : decoder.decode(value, { stream: true });
      let match;
      while ((match = /\r?\n\r?\n/.exec(buffer))) {
        dispatch(buffer.slice(0, match.index));
        buffer = buffer.slice(match.index + match[0].length);
      }
      if (done) {
        if (buffer.trim()) dispatch(buffer);
        break;
      }
    }
  } finally {
    reader.releaseLock();
  }
}
async function send(query) {
  if (currentRun() || !state.configured || !query.trim()) return;
  state.pinned = true;
  const controller = new AbortController();
  let chat = activeChat();
  if (!chat) {
    chat = {
      id: crypto.randomUUID(),
      runtimeId: state.conversationId,
      title: query.replace(/\s+/g, " ").slice(0, 48),
      updatedAt: Date.now(),
      turns: [],
    };
    state.chats.push(chat);
    state.activeId = chat.id;
  }
  chat.updatedAt = Date.now();
  const turn = {
    query,
    time: new Date().toISOString(),
    text: "",
    pending: true,
    status: "正在分析…",
    steps: [],
    evidence: [],
  };
  chat.turns.push(turn);
  saveHistory();
  renderHistory();
  refreshControls();
  $("#welcome").hidden = true;
  messages.append(element("article", "message user", query));
  const response = createResponse(query, turn.time);
  response.savedTurn = turn;
  state.runs.set(chat.id, { controller, response });
  chat.draft = "";
  renderHistory();
  refreshControls();
  input.value = "";
  input.style.height = "";
  $("#status").textContent = "正在分析…";
  follow();
  let stopped = false;
  try {
    const result = await fetch("/api/chat/stream", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Qiqi-User": user.value,
      },
      body: JSON.stringify({ query, conversationId: chat.runtimeId }),
      signal: controller.signal,
    });
    if (!result.ok) {
      let detail;
      try {
        detail = (await result.json()).error;
      } catch {
        /* fall back to status */
      }
      throw new Error(detail || `请求失败（${result.status}），请稍后重试。`);
    }
    await consumeStream(result.body, response);
    if (!response.done && !response.failed)
      throw new Error("连接已中断，回答可能不完整。请重新提问。");
  } catch (error) {
    if (error.name === "AbortError") stopped = true;
    else {
      response.failed = true;
      response.error.hidden = false;
      response.error.textContent = error.message || "连接失败，请稍后重试。";
    }
  } finally {
    if (!stopped && !response.failed)
      await finishTyping(response, controller.signal);
    stopped ||= controller.signal.aborted;
    render(response);
    response.progress.classList.remove("busy");
    response.progress.textContent = stopped
      ? "已停止 · 已保留生成内容"
      : response.failed
        ? "本次分析未完成"
        : "分析完成";
    response.summary.textContent = `分析过程 · ${response.tools.size} 次工具调用${stopped || response.failed ? " · 已中断" : ""}`;
    response.actions.hidden = !response.text;
    state.runs.delete(chat.id);
    if (response.failed || stopped) {
      // A cancelled server turn may still be unwinding. Use a fresh state slot.
      chat.runtimeId = crypto.randomUUID();
      if (state.activeId === chat.id) state.conversationId = chat.runtimeId;
      response.progress.textContent += " · 下次提问将开启新会话";
      const retry = element("button", "", "重新提问");
      retry.addEventListener("click", () => {
        if (currentRun()) return;
        input.value = query;
        resizeInput();
        refreshControls();
        input.focus();
      });
      response.actions.append(retry);
      response.actions.hidden = false;
    }
    turn.pending = false;
    captureResponse(response, true);
    if (state.activeId !== chat.id) chat.unread = true;
    saveHistory();
    renderHistory();
    refreshControls();
    if (response.card.isConnected) follow();
  }
}
function resizeInput() {
  input.style.height = "auto";
  input.style.height = Math.min(input.scrollHeight, 150) + "px";
}
input.addEventListener("input", () => {
  resizeInput();
  refreshControls();
});
input.addEventListener("keydown", (event) => {
  if (
    event.key === "Enter" &&
    !event.shiftKey &&
    !event.isComposing &&
    window.matchMedia("(min-width: 761px)").matches
  ) {
    event.preventDefault();
    if (!currentRun()) $("#composer").requestSubmit();
  }
});
$("#composer").addEventListener("submit", (event) => {
  event.preventDefault();
  send(input.value.trim());
});
$("#stop").addEventListener("click", () => currentRun()?.controller.abort());
$("#new-chat").addEventListener("click", () => {
  if (!$("#settings-page").hidden) closeSettings();
  resetConversation();
});
user.addEventListener("change", () => {
  updateScope();
  loadHistory();
  toast("已切换身份和对话记录");
});
document.querySelectorAll(".suggestion").forEach((button) =>
  button.addEventListener("click", () => {
    input.value = button.dataset.prompt;
    resizeInput();
    refreshControls();
    input.focus();
  }),
);
loadMeta().catch(() => {
  $("#notice").textContent = "无法连接服务，请确认服务已启动后刷新页面。";
  $("#notice").hidden = false;
  $("#status").textContent = "连接失败";
  $("#connection-label").textContent = "服务未连接";
});
