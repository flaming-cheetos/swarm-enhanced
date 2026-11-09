package com.rdh.swarm.web;

/** External panel script. Kept separate so the page can use script-src 'self'. */
public final class SwarmWebPanelScript {
    private SwarmWebPanelScript() {}

    public static String getScript() {
        return """
let ws = null;
let state = { bots: [], tasks: [], connected: 0 };
let logs = [];
const MAX_LOGS = 500;
let wsConnected = false;

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined && text !== null) node.textContent = String(text);
  return node;
}

function connect() {
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
  ws = new WebSocket(scheme + '://' + location.host + '/ws');

  ws.onopen = () => {
    wsConnected = true;
    document.getElementById('wsIndicator').className = 'ws-indicator connected';
    document.getElementById('wsStatus').textContent = 'Connected';
  };

  ws.onclose = () => {
    wsConnected = false;
    document.getElementById('wsIndicator').className = 'ws-indicator';
    document.getElementById('wsStatus').textContent = 'Reconnecting...';
    setTimeout(connect, 3000);
  };

  ws.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data);
      if (msg.type === 'state') {
        state = msg;
        renderState();
      } else if (msg.type === 'log') {
        addLog(msg);
      } else if (msg.type === 'log_history') {
        logs = Array.isArray(msg.logs) ? msg.logs : [];
        renderLogs();
      }
    } catch (err) {
      console.error('Bad panel message', err);
    }
  };
}

function renderState() {
  document.getElementById('connectedCount').textContent = String(state.connected || 0);
  document.getElementById('modVersion').textContent = state.modVersion || 'version unknown';
  document.getElementById('buildCommit').textContent = state.buildCommit || 'commit unknown';
  const follow = document.getElementById('followButton');
  if (follow) follow.title = 'swarm:all follow player ' + (state.hostName || 'your player name');
  renderBots();
  renderTasks();
}

function resetSelect(select, label, value) {
  select.replaceChildren();
  const option = document.createElement('option');
  option.value = value;
  option.textContent = label;
  select.appendChild(option);
}

function renderBots() {
  const list = document.getElementById('botsList');
  const botSelect = document.getElementById('taskBot');
  const cmdSelect = document.getElementById('cmdTarget');
  if (!list || !botSelect || !cmdSelect) return;

  const currentBot = botSelect.value;
  const currentCmd = cmdSelect.value;
  resetSelect(botSelect, 'Any bot', 'any');
  resetSelect(cmdSelect, 'All', 'all');

  const bots = Array.isArray(state.bots) ? state.bots : [];
  if (bots.length === 0) {
    const empty = element('div', '', 'No bots connected');
    empty.style.color = '#555';
    empty.style.fontSize = '0.82rem';
    list.replaceChildren(empty);
    return;
  }

  list.replaceChildren();
  for (const bot of bots) {
    const nickname = bot.nickname || bot.username || 'unknown';
    const username = bot.username || nickname;
    const online = !!bot.online;
    const tier = bot.tier || 'Wood';
    const tierClass = 'tier-' + String(tier).toUpperCase();

    const card = element('div', 'bot-card' + (online ? '' : ' offline'));
    const nameRow = element('div', 'bot-name');
    nameRow.append(
      element('span', 'dot ' + (online ? 'online' : 'offline'), '●'),
      element('span', '', nickname),
      element('span', 'tier-badge ' + tierClass, tier)
    );

    const nickInput = document.createElement('input');
    nickInput.className = 'nick-input';
    nickInput.value = nickname;
    nickInput.title = "Set this bot's nickname (/api/nickname)";
    nickInput.placeholder = 'nickname';
    nickInput.addEventListener('blur', () => setNick(username, nickInput.value));
    nameRow.append(nickInput);

    const hpPct = Math.min(100, (Number(bot.health) / 20) * 100);
    const foodPct = Math.min(100, (Number(bot.food) / 20) * 100);
    const hpBar = element('div', 'hp-bar');
    const hpFill = element('div', 'hp-fill');
    hpFill.style.width = hpPct + '%';
    hpBar.append(hpFill);
    const foodBar = element('div', 'hp-bar');
    const foodFill = element('div', 'hp-fill food-fill');
    foodFill.style.width = foodPct + '%';
    foodBar.append(foodFill);

    const stats = element('div', 'bot-stats');
    stats.append(
      element('span', '', '❤ ' + (bot.health ?? 0)),
      element('span', '', '🍗 ' + (bot.food ?? 0)),
      element('span', '', bot.halted ? 'halted' : (bot.state || 'IDLE'))
    );

    const task = element('div', 'bot-task', bot.task || 'Idle');
    const progress = element('div', 'bot-progress',
      (bot.progress || '') + (bot.eta ? ' | ETA ' + bot.eta : ''));
    const coords = element('div', 'bot-coords',
      (bot.x ?? 0) + ' ' + (bot.y ?? 0) + ' ' + (bot.z ?? 0));

    const actions = element('div', 'bot-actions');
    const actionButton = element('button', bot.halted ? 'success' : 'danger',
      bot.halted ? '▶ Resume' : '⏹ Stop');
    actionButton.title = 'swarm:' + username + ' ' + (bot.halted ? 'resume' : 'cancel');
    actionButton.addEventListener('click', () => {
      if (bot.halted) resumeBot(username);
      else cancelBot(username);
    });
    actions.append(actionButton);

    card.append(nameRow, hpBar, foodBar, stats, task, progress, coords, actions);
    list.append(card);

    const botOption = document.createElement('option');
    botOption.value = nickname;
    botOption.textContent = nickname;
    botSelect.append(botOption);

    const cmdOption = document.createElement('option');
    cmdOption.value = username;
    cmdOption.textContent = nickname;
    cmdSelect.append(cmdOption);
  }

  if (currentBot) botSelect.value = currentBot;
  if (currentCmd) cmdSelect.value = currentCmd;
}

function renderTasks() {
  const list = document.getElementById('tasksList');
  if (!list) return;
  const tasks = Array.isArray(state.tasks) ? state.tasks : [];
  if (tasks.length === 0) {
    const empty = element('div', '', 'No tasks');
    empty.style.color = '#555';
    empty.style.fontSize = '0.82rem';
    list.replaceChildren(empty);
    return;
  }

  list.replaceChildren();
  for (const task of tasks) {
    const row = element('div', 'task-item');
    const info = element('div', 'task-info');
    const name = element('div', 'task-name',
      (task.item || task.type || 'Task') + ' × ' + (task.quantity ?? 0));
    const meta = element('div', 'task-meta',
      (task.progress || '') + ' | ' + (task.bot || 'any'));
    info.append(name, meta);

    if (task.reason) {
      const reason = element('div', 'task-meta', task.reason);
      reason.style.color = '#c96';
      info.append(reason);
    }

    const status = element('span', 'task-status status-' + String(task.status || ''),
      task.status || '');
    const remove = element('button', 'danger', '✕');
    remove.title = 'Remove task ' + (task.id || '') + ' (/api/task/remove)';
    remove.style.padding = '3px 8px';
    remove.addEventListener('click', () => removeTask(task.id));

    row.append(info, status, remove);
    list.append(row);
  }
}

function addLog(msg) {
  logs.push(msg);
  if (logs.length > MAX_LOGS) logs.shift();
  appendLogEntry(msg);
}

function appendLogEntry(msg) {
  const list = document.getElementById('logEntries');
  const filter = document.getElementById('logFilter').value.toLowerCase();
  const text = msg.msg || '';
  if (filter && !text.toLowerCase().includes(filter)) return;

  const level = msg.level || 'INFO';
  const row = element('div', 'log-entry log-' + level);
  row.append(
    element('span', 'time', msg.time || ''),
    element('span', 'level-' + level, '[' + level + ']'),
    document.createTextNode(' '),
    element('span', 'msg', text)
  );
  list.appendChild(row);

  if (document.getElementById('autoScroll').checked) {
    list.scrollTop = list.scrollHeight;
  }
}

function renderLogs() {
  const list = document.getElementById('logEntries');
  list.replaceChildren();
  logs.forEach(appendLogEntry);
}

function filterLogs() {
  renderLogs();
}

function clearLogs() {
  logs = [];
  document.getElementById('logEntries').replaceChildren();
}

async function post(path, body) {
  try {
    await fetch(path, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
  } catch (err) {
    console.error('Panel request failed', path, err);
  }
}

function addTask() {
  const item = document.getElementById('taskItem').value.trim();
  const qty = parseInt(document.getElementById('taskQty').value, 10) || 64;
  const type = document.getElementById('taskType').value || 'AUTO';
  const bot = document.getElementById('taskBot').value;
  const actionType = type === 'HUNT_FOOD' || type === 'GRIND';
  if (!item && !actionType) return;

  const payload = { item, qty, bot };
  if (type && type !== 'AUTO') payload.type = type;
  post('/api/task/add', payload);
  document.getElementById('taskItem').value = '';
}

function removeTask(id) {
  post('/api/task/remove', { id });
}

function cancelBot(username) {
  post('/api/task/cancel', { bot: username });
}

function resumeBot(username) {
  post('/api/task/resume', { bot: username });
}

function action(name) {
  post('/api/action/' + name, {});
}

function updateSendTitle() {
  const cmd = document.getElementById('cmdInput').value.trim();
  document.getElementById('sendButton').title = cmd ? 'exec ' + cmd : 'exec <command from the box>';
}

function sendCmd() {
  const target = document.getElementById('cmdTarget').value;
  const cmd = document.getElementById('cmdInput').value.trim();
  if (!cmd) return;
  post('/api/command', { target, cmd });
  document.getElementById('cmdInput').value = '';
  updateSendTitle();
}

function setNick(username, nickname) {
  post('/api/nickname', { username, nickname });
}

function bindPanel() {
  const addTaskButton = document.getElementById('addTaskButton');
  if (addTaskButton) addTaskButton.addEventListener('click', addTask);

  const clearDone = document.getElementById('clearDoneButton');
  if (clearDone) clearDone.addEventListener('click', () => post('/api/task/clear_done', {}));

  const clearLogsButton = document.getElementById('clearLogsButton');
  if (clearLogsButton) clearLogsButton.addEventListener('click', clearLogs);

  const sendButton = document.getElementById('sendButton');
  if (sendButton) sendButton.addEventListener('click', sendCmd);

  const cmdInput = document.getElementById('cmdInput');
  if (cmdInput) {
    cmdInput.addEventListener('input', updateSendTitle);
    cmdInput.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') sendCmd();
    });
  }

  const logFilter = document.getElementById('logFilter');
  if (logFilter) logFilter.addEventListener('input', filterLogs);

  document.querySelectorAll('button[data-action]').forEach((button) => {
    button.addEventListener('click', () => action(button.dataset.action));
  });

  connect();
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', bindPanel, { once: true });
} else {
  bindPanel();
}
""";
    }
}
