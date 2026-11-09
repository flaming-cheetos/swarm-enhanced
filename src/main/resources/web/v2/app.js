'use strict';

const MAX_LOGS = 2000;
const TASK_STATUSES = new Set(['PENDING', 'ACTIVE', 'BLOCKED', 'COMPLETE', 'FAILED', 'CANCELLED']);
const TIERS = new Set(['WOOD', 'STONE', 'IRON', 'DIAMOND', 'NETHERITE']);

const $ = (id) => document.getElementById(id);
const metaToken = $('swarmTokenMeta') || document.querySelector('meta[name="swarm-token"]');
const bootToken = metaToken && metaToken.content ? metaToken.content.trim() : '';
const urlToken = new URLSearchParams(location.search).get('token') || '';
if (urlToken) storageRemove('swarmToken');

let currentToken = urlToken || storageGet('swarmToken') || bootToken;
let ws = null;
let rejected = false;
let retryDelay = 3000;
let reconnectTimer = 0;
let lastStateAt = 0;
let paused = false;
let activeChip = '';
let itemsRequested = false;
let botSelectSig = '';

let state = { type: 'state', bots: [], tasks: [], dimension: '', hostName: '', modVersion: '', buildCommit: '' };
let logs = [];

const botNodes = new Map();
const taskNodes = new Map();

function storageGet(key) {
  try { return sessionStorage.getItem(key) || ''; } catch (e) { return ''; }
}
function storageSet(key, value) {
  try { sessionStorage.setItem(key, value); } catch (e) { /* private mode */ }
}
function storageRemove(key) {
  try { sessionStorage.removeItem(key); } catch (e) { /* private mode */ }
}

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined && text !== null) node.textContent = String(text);
  return node;
}

function setText(node, value, fallback) {
  if (!node) return;
  const empty = value === undefined || value === null || value === '';
  const text = empty ? (fallback !== undefined ? fallback : 'unknown') : String(value);
  if (node.textContent !== text) node.textContent = text;
}

function option(value, label) {
  const node = document.createElement('option');
  node.value = value;
  node.textContent = label;
  return node;
}

function ageLabel(ms) {
  const seconds = Math.max(0, Math.floor(ms / 1000));
  if (seconds < 60) return seconds + 's';
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return minutes + 'm';
  return Math.floor(minutes / 60) + 'h';
}

function authHeaders(extra) {
  const headers = Object.assign({}, extra || {});
  if (currentToken) headers['X-Swarm-Token'] = currentToken;
  return headers;
}

/* ---------------------------------------------------------------- transport */

function setConnection(mode) {
  const labels = {
    connected: ['Connected', 'connected'],
    reconnecting: ['Reconnecting...', ''],
    rejected: ['Token rejected', 'rejected']
  };
  const pair = labels[mode] || labels.reconnecting;
  setText($('wsStatus'), pair[0], pair[0]);
  setText($('stripConn'), pair[0], pair[0]);
  for (const id of ['wsIndicator', 'stripIndicator']) {
    const dot = $(id);
    if (dot) dot.className = 'ws-indicator' + (pair[1] ? ' ' + pair[1] : '');
  }
}

function connect() {
  clearTimeout(reconnectTimer);
  reconnectTimer = 0;
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
  let url = scheme + '://' + location.host + '/ws';
  if (currentToken) url += '?token=' + encodeURIComponent(currentToken);
  setConnection('reconnecting');
  try {
    ws = new WebSocket(url);
  } catch (e) {
    scheduleReconnect();
    return;
  }

  ws.onopen = () => {
    rejected = false;
    retryDelay = 3000;
    $('tokenStrip').hidden = true;
    setConnection('connected');
  };

  ws.onclose = () => {
    ws = null;
    if (rejected) {
      setConnection('rejected');
      return;
    }
    setConnection('reconnecting');
    probeAuth().then((ok) => {
      if (!ok) rejectToken();
      else scheduleReconnect();
    });
  };

  ws.onerror = () => { /* onclose carries the retry */ };

  ws.onmessage = (event) => {
    let message;
    try {
      message = JSON.parse(event.data);
    } catch (e) {
      console.error('Bad panel message', e);
      return;
    }
    if (message.type === 'state') {
      state = message;
      lastStateAt = Date.now();
      renderState();
    } else if (message.type === 'log') {
      addLog(message);
    } else if (message.type === 'log_history') {
      logs = Array.isArray(message.logs) ? message.logs.slice(-MAX_LOGS) : [];
      renderLogs();
    }
  };
}

function scheduleReconnect() {
  clearTimeout(reconnectTimer);
  const delay = retryDelay;
  retryDelay = Math.min(30000, retryDelay * 2);
  reconnectTimer = setTimeout(connect, delay);
}

async function probeAuth() {
  try {
    const response = await fetch('/v2/app.js', {
      cache: 'no-store',
      credentials: 'same-origin',
      headers: authHeaders()
    });
    return response.status !== 401;
  } catch (e) {
    return true;
  }
}

function rejectToken() {
  if (rejected) return;
  rejected = true;
  storageRemove('swarmToken');
  currentToken = '';
  if (ws) {
    try { ws.close(); } catch (e) { /* already closed */ }
    ws = null;
  }
  setConnection('rejected');
  $('tokenStrip').hidden = false;
  $('tokenInput').focus();
}

function retryToken() {
  const input = $('tokenInput');
  const token = input.value.trim();
  if (!token) {
    input.focus();
    return;
  }
  storageSet('swarmToken', token);
  currentToken = token;
  input.value = '';
  rejected = false;
  retryDelay = 3000;
  $('tokenStrip').hidden = true;
  connect();
}

/* -------------------------------------------------------------------- state */

function renderState() {
  const bots = Array.isArray(state.bots) ? state.bots : [];
  const tasks = Array.isArray(state.tasks) ? state.tasks : [];
  const online = bots.filter((bot) => bot && bot.online).length;
  setText($('connectedCount'), String(online), '0');
  setText($('modVersion'), state.modVersion || 'version unknown', 'version unknown');
  setText($('buildCommit'), state.buildCommit || 'commit unknown', 'commit unknown');
  if (state.hostName) $('followButton').title = 'swarm:all follow player ' + state.hostName;
  renderBots(bots);
  renderTasks(tasks);
  renderBotSelects(bots);
  renderStrip(bots, Date.now() - lastStateAt);
}

function firstOnline(bots) {
  return bots.find((bot) => bot && bot.online) || null;
}

function renderStrip(bots, ageMs) {
  const bot = firstOnline(bots || []);
  const stale = ageMs > 5000;
  const staleText = stale ? 'unknown (stale ' + ageLabel(ageMs) + ')' : 'unknown';

  if (!bot) {
    setText($('stripBot'), 'no bot online', 'no bot online');
    setText($('stripDim'), stale ? staleText : 'dimension unknown', 'dimension unknown');
    setText($('stripCoords'), staleText, 'unknown');
    setText($('stripTask'), 'idle', 'idle');
    setText($('stripVitals'), '❤ ' + staleText + ' · 🍗 ' + staleText, 'unknown');
    setText($('stripHalt'), stale ? staleText : 'unknown', 'unknown');
    return;
  }

  const name = bot.nickname || bot.username || 'unknown';
  const dimension = state.dimension || 'unknown';
  const coordsKnown = [bot.x, bot.y, bot.z].every((value) => Number.isFinite(Number(value)));
  const coords = coordsKnown ? (bot.x + ' ' + bot.y + ' ' + bot.z) : 'unknown';
  const task = (bot.task || 'idle') + (bot.progress ? ' · ' + bot.progress : '');
  const health = bot.health === undefined || bot.health === null || bot.health === '' ? 'unknown' : bot.health;
  const food = bot.food === undefined || bot.food === null || bot.food === '' ? 'unknown' : bot.food;

  setText($('stripBot'), name);
  setText($('stripDim'), stale ? staleText : dimension);
  setText($('stripCoords'), stale ? staleText : coords);
  setText($('stripTask'), stale ? staleText : task);
  setText($('stripVitals'), stale ? ('❤ ' + staleText + ' · 🍗 ' + staleText) : ('❤ ' + health + ' · 🍗 ' + food));
  setText($('stripHalt'), stale ? staleText : (bot.halted ? 'halted' : (bot.state || 'running')));
}

/* --------------------------------------------------------------------- bots */

function createBotCard(bot) {
  const card = element('div', 'bot-card');
  const nameRow = element('div', 'bot-name');
  const dot = element('span', 'dot', '●');
  const name = element('span', 'bot-label', bot.nickname || bot.username || 'unknown');
  const tier = element('span', 'tier-badge', bot.tier || 'Wood');
  const nick = document.createElement('input');
  nick.className = 'nick-input';
  nick.type = 'text';
  nick.placeholder = 'nickname';
  nick.title = "Set this bot's nickname (/api/nickname)";
  nick.value = bot.nickname || bot.username || '';
  nick.dataset.serverValue = nick.value;
  nick.addEventListener('blur', () => {
    const value = nick.value.trim();
    if (value && value !== (nick.dataset.serverValue || '')) setNick(bot.username, value);
  });
  nameRow.append(dot, name, tier, nick);

  const hpBar = element('div', 'hp-bar');
  const hpFill = element('div', 'hp-fill');
  hpBar.appendChild(hpFill);

  const foodBar = element('div', 'hp-bar');
  const foodFill = element('div', 'hp-fill food-fill');
  foodBar.appendChild(foodFill);

  const stats = element('div', 'bot-stats');
  const health = element('span', '', '');
  const food = element('span', '', '');
  const stateText = element('span', '', '');
  stats.append(health, food, stateText);

  const task = element('div', 'bot-task', 'Idle');
  const progress = element('div', 'bot-progress', '');
  const coords = element('div', 'bot-coords', '');

  const actions = element('div', 'bot-actions');
  const action = document.createElement('button');
  action.type = 'button';
  action.addEventListener('click', () => {
    if (action.dataset.halted === 'true') resumeBot(bot.username);
    else cancelBot(bot.username);
  });
  actions.appendChild(action);

  card.append(nameRow, hpBar, foodBar, stats, task, progress, coords, actions);
  return { card, dot, name, tier, nick, hpFill, foodFill, health, food, stateText, task, progress, coords, action };
}

function updateBotCard(node, bot) {
  const nickname = bot.nickname || bot.username || 'unknown';
  setText(node.name, nickname);

  node.dot.className = 'dot ' + (bot.online ? 'online' : 'offline');
  node.card.classList.toggle('offline', !bot.online);

  const tier = String(bot.tier || 'Wood').toUpperCase();
  node.tier.textContent = bot.tier || 'Wood';
  node.tier.className = 'tier-badge' + (TIERS.has(tier) ? ' tier-' + tier : '');

  node.nick.dataset.serverValue = nickname;
  if (document.activeElement !== node.nick) node.nick.value = nickname;

  const health = Number(bot.health);
  const food = Number(bot.food);
  node.hpFill.style.width = Math.max(0, Math.min(100, (Number.isFinite(health) ? health : 0) / 20 * 100)) + '%';
  node.foodFill.style.width = Math.max(0, Math.min(100, (Number.isFinite(food) ? food : 0) / 20 * 100)) + '%';

  setText(node.health, '❤ ' + (bot.health === undefined || bot.health === null ? 'unknown' : bot.health));
  setText(node.food, '🍗 ' + (bot.food === undefined || bot.food === null ? 'unknown' : bot.food));
  setText(node.stateText, bot.halted ? 'halted' : (bot.state || 'IDLE'));
  setText(node.task, bot.task || 'Idle');
  setText(node.progress, (bot.progress || '') + (bot.eta ? ' | ETA ' + bot.eta : ''), '');
  setText(node.coords, (bot.x ?? '?') + ' ' + (bot.y ?? '?') + ' ' + (bot.z ?? '?'));

  node.action.textContent = bot.halted ? '▶ Resume' : '⏹ Stop';
  node.action.className = bot.halted ? 'success' : 'danger';
  node.action.title = 'swarm:' + bot.username + ' ' + (bot.halted ? 'resume' : 'cancel');
  node.action.dataset.halted = bot.halted ? 'true' : 'false';
}

function renderBots(bots) {
  const list = $('botsList');
  const empty = $('botsEmpty');
  if (!bots.length) {
    if (empty) empty.hidden = false;
    for (const [name, node] of Array.from(botNodes)) {
      node.card.remove();
      botNodes.delete(name);
    }
    return;
  }
  if (empty) empty.hidden = true;

  const seen = new Set();
  for (const bot of bots) {
    if (!bot || !bot.username) continue;
    seen.add(bot.username);
    let node = botNodes.get(bot.username);
    if (!node) {
      node = createBotCard(bot);
      botNodes.set(bot.username, node);
      list.appendChild(node.card);
    }
    updateBotCard(node, bot);
  }
  for (const [name, node] of Array.from(botNodes)) {
    if (!seen.has(name)) {
      node.card.remove();
      botNodes.delete(name);
    }
  }
}

function renderBotSelects(bots) {
  const signature = bots.map((bot) => (bot.username || '') + '\u0000' + (bot.nickname || bot.username || '')).join('\u0001');
  if (signature === botSelectSig) return;
  const taskBot = $('taskBot');
  const cmdTarget = $('cmdTarget');
  if (document.activeElement === taskBot || document.activeElement === cmdTarget) return;

  botSelectSig = signature;
  const taskValue = taskBot.value;
  const cmdValue = cmdTarget.value;
  const taskOptions = [option('any', 'Any bot')];
  const cmdOptions = [option('all', 'All')];
  for (const bot of bots) {
    if (!bot || !bot.username) continue;
    const nickname = bot.nickname || bot.username;
    taskOptions.push(option(nickname, nickname));
    cmdOptions.push(option(bot.username, nickname));
  }
  taskBot.replaceChildren(...taskOptions);
  cmdTarget.replaceChildren(...cmdOptions);
  if (Array.from(taskBot.options).some((item) => item.value === taskValue)) taskBot.value = taskValue;
  if (Array.from(cmdTarget.options).some((item) => item.value === cmdValue)) cmdTarget.value = cmdValue;
}

/* -------------------------------------------------------------------- tasks */

function createTaskRow(task) {
  const row = element('div', 'task-item');
  const info = element('div', 'task-info');
  const name = element('div', 'task-name', '');
  const meta = element('div', 'task-meta', '');
  const reason = element('div', 'task-meta reason', '');
  reason.hidden = true;
  info.append(name, meta, reason);

  const status = element('span', 'task-status', '');
  const remove = document.createElement('button');
  remove.className = 'danger';
  remove.type = 'button';
  remove.textContent = '✕';
  remove.addEventListener('click', () => removeTask(task.id));

  row.append(info, status, remove);
  return { row, name, meta, reason, status, remove };
}

function updateTaskRow(node, task) {
  setText(node.name, (task.item || task.type || 'Task') + ' × ' + (task.quantity ?? 0));
  setText(node.meta, (task.progress || '') + ' | ' + (task.bot || 'any'));
  const reason = task.reason || '';
  node.reason.hidden = !reason;
  setText(node.reason, reason, '');

  const status = String(task.status || '').toUpperCase();
  node.status.textContent = task.status || '';
  node.status.className = 'task-status ' + (TASK_STATUSES.has(status) ? 'status-' + status : 'status-UNKNOWN');
  node.remove.title = 'Remove task ' + (task.id || '') + ' (/api/task/remove)';
}

function renderTasks(tasks) {
  const list = $('tasksList');
  const empty = $('tasksEmpty');
  if (!tasks.length) {
    if (empty) empty.hidden = false;
    for (const [id, node] of Array.from(taskNodes)) {
      node.row.remove();
      taskNodes.delete(id);
    }
    return;
  }
  if (empty) empty.hidden = true;

  const seen = new Set();
  for (const task of tasks) {
    if (!task || !task.id) continue;
    seen.add(task.id);
    let node = taskNodes.get(task.id);
    if (!node) {
      node = createTaskRow(task);
      taskNodes.set(task.id, node);
      list.appendChild(node.row);
    }
    updateTaskRow(node, task);
  }
  for (const [id, node] of Array.from(taskNodes)) {
    if (!seen.has(id)) {
      node.row.remove();
      taskNodes.delete(id);
    }
  }
}

/* --------------------------------------------------------------------- logs */

function matchesLog(entry) {
  const filter = ($('logFilter').value || '').trim().toLowerCase();
  const message = String(entry.msg || '');
  if (filter && !message.toLowerCase().includes(filter)) return false;
  if (!activeChip) return true;
  if (activeChip === 'WARN' || activeChip === 'ERROR') return (entry.level || 'INFO') === activeChip;
  return message.toUpperCase().startsWith(activeChip);
}

function makeLogRow(entry) {
  const level = entry.level || 'INFO';
  const row = element('div', 'log-entry log-' + level);
  row.append(
    element('span', 'time', entry.time || ''),
    element('span', 'level-' + level, '[' + level + ']'),
    document.createTextNode(' '),
    element('span', 'msg', entry.msg || '')
  );
  return row;
}

function appendLogRow(entry) {
  const list = $('logEntries');
  list.appendChild(makeLogRow(entry));
  while (list.childElementCount > MAX_LOGS) list.firstElementChild.remove();
  if (!paused && $('autoScroll').checked) list.scrollTop = list.scrollHeight;
}

function addLog(entry) {
  logs.push(entry);
  if (logs.length > MAX_LOGS) logs.shift();
  if (matchesLog(entry)) appendLogRow(entry);
}

function renderLogs() {
  const list = $('logEntries');
  const fragment = document.createDocumentFragment();
  for (const entry of logs) {
    if (matchesLog(entry)) fragment.appendChild(makeLogRow(entry));
  }
  list.replaceChildren(fragment);
  if (!paused && $('autoScroll').checked) list.scrollTop = list.scrollHeight;
}

function clearLogs() {
  logs = [];
  $('logEntries').replaceChildren();
}

function formatLogLine(entry) {
  return (entry.time || '') + ' [' + (entry.level || 'INFO') + '] ' + (entry.msg || '');
}

function showToast(message) {
  const toast = $('logToast');
  toast.textContent = message;
  toast.hidden = false;
  clearTimeout(showToast.timer);
  showToast.timer = setTimeout(() => { toast.hidden = true; }, 2500);
}

async function copyLogs() {
  const visible = logs.filter(matchesLog).slice(-200);
  if (!visible.length) {
    showToast('No visible lines to copy');
    return;
  }
  const text = visible.map(formatLogLine).join('\n');
  let copied = false;

  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      copied = true;
    }
  } catch (e) { /* fall through */ }

  if (!copied) {
    const area = document.createElement('textarea');
    area.value = text;
    area.setAttribute('readonly', '');
    area.style.position = 'fixed';
    area.style.top = '-1000px';
    area.style.opacity = '0';
    document.body.appendChild(area);
    area.select();
    try { copied = document.execCommand('copy'); } catch (e) { copied = false; }
    area.remove();
  }

  if (copied) {
    showToast('Copied ' + visible.length + ' lines');
    return;
  }

  const range = document.createRange();
  range.selectNodeContents($('logEntries'));
  const selection = window.getSelection();
  selection.removeAllRanges();
  selection.addRange(range);
  showToast('Selected — press Ctrl+C');
}

/* ------------------------------------------------------------------ actions */

async function post(path, body) {
  try {
    const response = await fetch(path, {
      method: 'POST',
      credentials: 'same-origin',
      headers: authHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(body)
    });
    if (response.status === 401) {
      rejectToken();
      return false;
    }
    if (!response.ok) {
      showToast('Request failed: ' + response.status);
      return false;
    }
    return true;
  } catch (e) {
    showToast('Panel request failed');
    return false;
  }
}

function addTask() {
  const item = $('taskItem').value.trim();
  const qty = parseInt($('taskQty').value, 10) || 64;
  const type = $('taskType').value || 'AUTO';
  const bot = $('taskBot').value;
  const actionType = type === 'HUNT_FOOD' || type === 'GRIND';
  if (!item && !actionType) {
    showToast('Enter an item');
    $('taskItem').focus();
    return;
  }
  const payload = { item, qty, bot };
  if (type && type !== 'AUTO') payload.type = type;
  post('/api/task/add', payload).then((ok) => {
    if (ok) $('taskItem').value = '';
  });
}

function removeTask(id) { post('/api/task/remove', { id }); }
function cancelBot(bot) { post('/api/task/cancel', { bot }); }
function resumeBot(bot) { post('/api/task/resume', { bot }); }
function setNick(username, nickname) { post('/api/nickname', { username, nickname }); }
function action(name) { post('/api/action/' + name, {}); }

function updateSendTitle() {
  const cmd = $('cmdInput').value.trim();
  $('sendButton').title = !cmd
    ? 'exec <command from the box>'
    : (cmd.startsWith('#') ? 'chat ' + cmd : 'exec ' + cmd);
}

function sendCmd() {
  const target = $('cmdTarget').value;
  const cmd = $('cmdInput').value.trim();
  if (!cmd) return;
  post('/api/command', { target, cmd }).then((ok) => {
    if (ok) {
      $('cmdInput').value = '';
      updateSendTitle();
    }
  });
}

/* --------------------------------------------------------------------- tabs */

function activateTab(name) {
  const names = Array.from(document.querySelectorAll('.tab')).map((tab) => tab.dataset.tab);
  if (!names.includes(name)) name = 'overview';
  for (const tab of document.querySelectorAll('.tab')) {
    const active = tab.dataset.tab === name;
    tab.classList.toggle('is-active', active);
    tab.setAttribute('aria-selected', active ? 'true' : 'false');
  }
  for (const panel of document.querySelectorAll('.panel')) {
    panel.hidden = panel.id !== 'panel-' + name;
  }
  storageSet('swarm.tab', name);
}

/* ---------------------------------------------------------------- item list */

async function loadItems() {
  if (itemsRequested) return;
  itemsRequested = true;
  try {
    const response = await fetch('/api/items', {
      credentials: 'same-origin',
      headers: authHeaders()
    });
    if (response.status === 401) {
      rejectToken();
      return;
    }
    if (!response.ok) return;
    const data = await response.json();
    const fragment = document.createDocumentFragment();
    for (const id of data.items || []) {
      fragment.appendChild(option(id, id));
    }
    $('itemOptions').replaceChildren(fragment);
  } catch (e) {
    /* plain text entry still works */
  }
}

/* --------------------------------------------------------------------- boot */

function bindPanel() {
  for (const tab of document.querySelectorAll('.tab')) {
    tab.addEventListener('click', () => activateTab(tab.dataset.tab));
  }

  $('addTaskButton').addEventListener('click', addTask);
  $('clearDoneButton').addEventListener('click', () => post('/api/task/clear_done', {}));
  $('sendButton').addEventListener('click', sendCmd);
  $('cmdInput').addEventListener('input', updateSendTitle);
  $('cmdInput').addEventListener('keydown', (event) => {
    if (event.key === 'Enter') sendCmd();
  });
  $('cmdInput').addEventListener('focus', () => {
    if (!window.matchMedia('(max-width: 899px)').matches) return;
    window.setTimeout(() => $('cmdInput').scrollIntoView({ block: 'center', behavior: 'smooth' }), 180);
  });

  $('logFilter').addEventListener('input', renderLogs);
  $('clearLogsButton').addEventListener('click', clearLogs);
  $('copyLogButton').addEventListener('click', copyLogs);
  $('autoScroll').addEventListener('change', () => {
    if ($('autoScroll').checked) {
      paused = false;
      $('jumpLatest').hidden = true;
      const list = $('logEntries');
      list.scrollTop = list.scrollHeight;
    }
  });

  $('logEntries').addEventListener('scroll', () => {
    const list = $('logEntries');
    paused = list.scrollHeight - list.scrollTop - list.clientHeight >= 24;
    $('jumpLatest').hidden = !paused;
  });
  $('jumpLatest').addEventListener('click', () => {
    const list = $('logEntries');
    list.scrollTop = list.scrollHeight;
    paused = false;
    $('jumpLatest').hidden = true;
    $('autoScroll').checked = true;
  });

  for (const chip of document.querySelectorAll('#logChips .chip')) {
    chip.addEventListener('click', () => {
      const value = chip.dataset.chip || '';
      activeChip = activeChip === value ? '' : value;
      for (const other of document.querySelectorAll('#logChips .chip')) {
        const active = other.dataset.chip === activeChip;
        other.classList.toggle('is-active', active);
        other.setAttribute('aria-pressed', active ? 'true' : 'false');
      }
      renderLogs();
    });
  }

  $('tokenRetry').addEventListener('click', retryToken);
  $('tokenInput').addEventListener('keydown', (event) => {
    if (event.key === 'Enter') retryToken();
  });

  for (const button of document.querySelectorAll('button[data-action]')) {
    button.addEventListener('click', () => action(button.dataset.action));
  }

  $('taskItem').addEventListener('focus', loadItems, { once: true });

  activateTab(storageGet('swarm.tab') || 'overview');
  updateSendTitle();
  setConnection('reconnecting');
  connect();

  setInterval(() => {
    if (!lastStateAt) return;
    const age = Date.now() - lastStateAt;
    if (age > 5000) renderStrip(Array.isArray(state.bots) ? state.bots : [], age);
  }, 1000);
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', bindPanel, { once: true });
} else {
  bindPanel();
}
