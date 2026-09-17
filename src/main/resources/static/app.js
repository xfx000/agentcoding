const state = { conversationId: crypto.randomUUID(), answer: '', tools: new Map() };
const messages = document.querySelector('#messages');
const user = document.querySelector('#user');
const notice = document.querySelector('#notice');
const status = document.querySelector('#status');

async function loadMeta() {
  const meta = await fetch('/api/meta').then(r => r.json());
  meta.demoUsers.forEach(item => {
    const option = document.createElement('option');
    option.value = item.username;
    option.textContent = `${item.displayName} · ${item.dataScope}`;
    user.append(option);
  });
  if (!meta.modelConfigured) {
    notice.textContent = '服务已启动，但尚未配置 DASHSCOPE_API_KEY；工具和测试可运行，聊天需配置模型密钥。';
  } else {
    notice.hidden = true;
  }
}

function addMessage(role, text) {
  const card = document.createElement('article');
  card.className = `message ${role}`;
  const title = document.createElement('strong');
  title.textContent = role === 'user' ? '你' : 'Qiqi';
  const content = document.createElement('pre');
  content.textContent = text;
  card.append(title, content);
  messages.append(card);
  card.scrollIntoView({ behavior: 'smooth', block: 'end' });
  return content;
}

function handle(event, output) {
  const type = event.type;
  const data = event.data || {};
  if (type === 'TEXT_BLOCK_DELTA') {
    state.answer += data.delta || '';
    output.textContent = state.answer;
  } else if (type === 'TOOL_CALL_START') {
    status.textContent = `调用工具：${data.toolCallName || 'unknown'}`;
  } else if (type === 'TOOL_RESULT_START') {
    status.textContent = `处理结果：${data.toolCallName || 'tool'}`;
  } else if (type === 'AGENT_END') {
    status.textContent = '完成';
  } else if (type === 'ERROR') {
    output.textContent += `\n错误：${data.message || '执行失败'}`;
    status.textContent = '失败';
  }
}

async function send(query) {
  addMessage('user', query);
  state.answer = '';
  const output = addMessage('assistant', '');
  status.textContent = '分析中…';
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Qiqi-User': user.value },
    body: JSON.stringify({ query, conversationId: state.conversationId })
  });
  if (!response.ok) throw new Error((await response.json()).error || `HTTP ${response.status}`);
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const chunks = buffer.split('\n\n');
    buffer = chunks.pop();
    for (const chunk of chunks) {
      const line = chunk.split('\n').find(item => item.startsWith('data:'));
      if (line) handle(JSON.parse(line.slice(5).trim()), output);
    }
  }
}

document.querySelector('#composer').addEventListener('submit', async event => {
  event.preventDefault();
  const input = document.querySelector('#query');
  const query = input.value.trim();
  if (!query) return;
  input.value = '';
  try { await send(query); }
  catch (error) { addMessage('assistant', `错误：${error.message}`); status.textContent = '失败'; }
});

loadMeta().catch(error => { notice.textContent = `初始化失败：${error.message}`; });
