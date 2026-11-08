package com.rdh.swarm.web;

public class SwarmWebPanel {
    public static String getHtml() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Enhanced Swarm</title>
<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
body {
  font-family: 'Segoe UI', monospace;
  background: #0d0d0d;
  color: #e0e0e0;
  min-height: 100vh;
}
.header {
  background: #1a1a2e;
  padding: 12px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 2px solid #4a4aff;
  position: sticky;
  top: 0;
  z-index: 100;
}
.header h1 { font-size: 1.1rem; color: #7b7bff; }
.header .status { font-size: 0.8rem; color: #888; }
.connected { color: #4cff8a !important; }
.layout {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
  padding: 12px;
  max-width: 1400px;
  margin: 0 auto;
}
@media (min-width: 768px) {
  .layout { grid-template-columns: 1fr 1fr; }
}
@media (min-width: 1100px) {
  .layout { grid-template-columns: 1fr 1fr 1fr; }
}
.card {
  background: #1a1a1a;
  border: 1px solid #2a2a2a;
  border-radius: 8px;
  padding: 12px;
}
.card h2 {
  font-size: 0.85rem;
  color: #7b7bff;
  text-transform: uppercase;
  letter-spacing: 1px;
  margin-bottom: 10px;
  border-bottom: 1px solid #2a2a2a;
  padding-bottom: 6px;
}
.bot-card {
  border-left: 3px solid #4cff8a;
  margin-bottom: 8px;
  padding: 10px;
  background: #111;
  border-radius: 6px;
}
.bot-card.offline { border-left-color: #ff4c4c; }
.bot-name {
  font-size: 0.95rem;
  font-weight: bold;
  color: #fff;
  display: flex;
  align-items: center;
  gap: 6px;
}
.dot { font-size: 0.7rem; }
.dot.online { color: #4cff8a; }
.dot.offline { color: #ff4c4c; }
.bot-stats {
  display: flex;
  gap: 12px;
  margin: 4px 0;
  font-size: 0.8rem;
  color: #aaa;
}
.bot-task {
  font-size: 0.82rem;
  color: #ccc;
  margin: 4px 0;
}
.bot-progress {
  font-size: 0.8rem;
  color: #7b7bff;
}
.bot-coords {
  font-size: 0.75rem;
  color: #666;
  font-family: monospace;
}
.bot-actions {
  display: flex;
  gap: 4px;
  margin-top: 6px;
}
.nick-input {
  background: #222;
  border: 1px solid #333;
  color: #fff;
  padding: 3px 6px;
  border-radius: 4px;
  font-size: 0.75rem;
  width: 80px;
}
.tier-badge {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 0.7rem;
  font-weight: bold;
}
.tier-WOOD { background: #5c3d1e; color: #d4a054; }
.tier-STONE { background: #3a3a3a; color: #aaa; }
.tier-IRON { background: #2a3a4a; color: #88aacc; }
.tier-DIAMOND { background: #0d2a3a; color: #4ae8ff; }
.tier-NETHERITE { background: #2a1a2a; color: #cc88ff; }
.task-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px;
  background: #111;
  border-radius: 6px;
  margin-bottom: 6px;
  font-size: 0.82rem;
}
.task-info { flex: 1; }
.task-name { color: #fff; font-weight: bold; }
.task-meta { color: #888; font-size: 0.75rem; }
.task-status {
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 0.7rem;
  margin: 0 6px;
}
.status-ACTIVE { background: #1a3a1a; color: #4cff8a; }
.status-PENDING { background: #2a2a1a; color: #ffcc44; }
.status-COMPLETE { background: #1a1a3a; color: #7b7bff; }
.status-FAILED { background: #3a1a1a; color: #ff4c4c; }
.add-task {
  display: grid;
  grid-template-columns: 1fr 60px 1fr auto;
  gap: 6px;
  margin-top: 10px;
}
input, select {
  background: #222;
  border: 1px solid #333;
  color: #fff;
  padding: 6px 8px;
  border-radius: 4px;
  font-size: 0.82rem;
  width: 100%;
}
input:focus, select:focus {
  outline: none;
  border-color: #7b7bff;
}
button {
  background: #2a2a4a;
  color: #7b7bff;
  border: 1px solid #4a4aff;
  padding: 6px 10px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.8rem;
  transition: all 0.15s;
  white-space: nowrap;
}
button:hover { background: #3a3a6a; color: #fff; }
button.danger { background: #3a1a1a; color: #ff4c4c; border-color: #ff4c4c; }
button.danger:hover { background: #5a2a2a; }
button.success { background: #1a3a1a; color: #4cff8a; border-color: #4cff8a; }
button.success:hover { background: #2a5a2a; }
button.warn { background: #3a2a1a; color: #ffaa44; border-color: #ffaa44; }
button.warn:hover { background: #5a3a1a; }
.actions-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
}
.cmd-row {
  display: flex;
  gap: 6px;
  margin-top: 8px;
}
.cmd-row input { flex: 1; }
.log-panel {
  grid-column: 1 / -1;
  max-height: 300px;
}
.log-entries {
  height: 250px;
  overflow-y: auto;
  font-family: monospace;
  font-size: 0.75rem;
  background: #080808;
  border-radius: 4px;
  padding: 8px;
  border: 1px solid #1a1a1a;
}
.log-entry { padding: 2px 0; border-bottom: 1px solid #111; }
.log-entry .time { color: #444; margin-right: 6px; }
.log-INFO .msg { color: #ccc; }
.log-WARN .msg { color: #ffaa44; }
.log-ERROR .msg { color: #ff4c4c; }
.log-entry .level-INFO { color: #4cff8a; }
.log-entry .level-WARN { color: #ffaa44; }
.log-entry .level-ERROR { color: #ff4c4c; }
.log-controls {
  display: flex;
  gap: 6px;
  margin-bottom: 6px;
  align-items: center;
}
.log-filter { flex: 1; }
::-webkit-scrollbar { width: 6px; }
::-webkit-scrollbar-track { background: #111; }
::-webkit-scrollbar-thumb { background: #333; border-radius: 3px; }
.hp-bar {
  height: 3px;
  background: #222;
  border-radius: 2px;
  margin: 3px 0;
  overflow: hidden;
}
.hp-fill { height: 100%; background: #4cff8a; border-radius: 2px; transition: width 0.5s; }
.food-fill { background: #ffaa44; }
.ws-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ff4c4c;
  display: inline-block;
  margin-right: 4px;
}
.ws-indicator.connected { background: #4cff8a; }
</style>
</head>
<body>

<div class="header">
  <h1>⚡ Enhanced Swarm</h1>
  <div class="status">
    <span class="ws-indicator" id="wsIndicator"></span>
    <span id="wsStatus">Connecting...</span>
    &nbsp;|&nbsp;
    <span id="connectedCount">0</span> bots online
  </div>
</div>

<div class="layout" id="layout">
  <!-- Bots section -->
  <div class="card" id="botsCard">
    <h2>🤖 Workers</h2>
    <div id="botsList">
      <div style="color:#555;font-size:0.82rem;">No bots connected</div>
    </div>
  </div>

  <!-- Tasks section -->
  <div class="card" id="tasksCard">
    <h2>📋 Task Queue</h2>
    <div id="tasksList">
      <div style="color:#555;font-size:0.82rem;">No tasks</div>
    </div>
    <div class="add-task" style="margin-top:10px;">
      <input id="taskItem" placeholder="item (e.g. cobblestone)" type="text">
      <input id="taskQty" placeholder="qty" type="number" value="64">
      <select id="taskType">
        <option value="AUTO">Auto</option>
        <option value="CRAFT">Craft</option>
        <option value="GATHER">Gather</option>
        <option value="HUNT_FOOD">Hunt food</option>
        <option value="GRIND">Grind</option>
        <option value="SMITH">Smith</option>
      </select>
      <select id="taskBot">
        <option value="any">Any bot</option>
      </select>
      <button class="success" onclick="addTask()">+ Add</button>
    </div>
    <div style="margin-top:8px;">
      <button onclick="post('/api/task/clear_done',{})">Clear Done</button>
    </div>
  </div>

  <!-- Quick Actions -->
  <div class="card">
    <h2>⚡ Quick Actions</h2>
    <div class="actions-grid">
      <button onclick="action('follow')">👥 Follow Me</button>
      <button class="danger" onclick="action('stop')">🛑 Stop All</button>
      <button onclick="action('deposit')">📦 Deposit All</button>
      <button onclick="action('deposit_ores')">⛏ Dep. Ores</button>
      <button onclick="action('deposit_shulkers')">📫 Dep. Shulkers</button>
      <button onclick="action('scan_chests')">🔍 Scan Chests</button>
      <button onclick="action('check_fuel')">🔥 Check Fuel</button>
      <button class="success" onclick="action('pvp_on')">⚔ PVP ON</button>
      <button class="danger" onclick="action('pvp_off')">🕊 PVP OFF</button>
    </div>
    <div style="margin-top:10px;">
      <h2 style="margin-bottom:6px;">💬 Send Command</h2>
      <div class="cmd-row">
        <select id="cmdTarget" style="width:100px;flex:none;">
          <option value="all">All</option>
        </select>
        <input id="cmdInput" placeholder="command..." type="text" 
               onkeydown="if(event.key==='Enter')sendCmd()">
        <button onclick="sendCmd()">Send</button>
      </div>
    </div>
  </div>

  <!-- Log Panel (full width) -->
  <div class="card log-panel">
    <h2>📜 Live Log</h2>
    <div class="log-controls">
      <input class="log-filter" id="logFilter" placeholder="Filter logs..." 
             type="text" oninput="filterLogs()">
      <button onclick="clearLogs()">Clear</button>
      <label style="font-size:0.75rem;color:#888;display:flex;align-items:center;gap:4px;">
        <input type="checkbox" id="autoScroll" checked> Auto-scroll
      </label>
    </div>
    <div class="log-entries" id="logEntries"></div>
  </div>
</div>

<script>
let ws;
let state = { bots: [], tasks: [], connected: 0 };
let logs = [];
const MAX_LOGS = 500;
let wsConnected = false;

function connect() {
  ws = new WebSocket('ws://' + location.host + '/ws');
  
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
  
  ws.onmessage = (e) => {
    try {
      const msg = JSON.parse(e.data);
      if (msg.type === 'state') {
        state = msg;
        renderState();
      } else if (msg.type === 'log') {
        addLog(msg);
      } else if (msg.type === 'log_history') {
        logs = msg.logs || [];
        renderLogs();
      }
    } catch(err) {}
  };
}

function renderState() {
  document.getElementById('connectedCount').textContent = state.connected || 0;
  renderBots();
  renderTasks();
}

function renderBots() {
  const el = document.getElementById('botsList');
  const botSel = document.getElementById('taskBot');
  const cmdSel = document.getElementById('cmdTarget');
  
  // Update bot selectors
  const currentBot = botSel.value;
  const currentCmd = cmdSel.value;
  botSel.innerHTML = '<option value="any">Any bot</option>';
  cmdSel.innerHTML = '<option value="all">All</option>';
  
  if (!state.bots || state.bots.length === 0) {
    el.innerHTML = '<div style="color:#555;font-size:0.82rem;">No bots connected</div>';
    return;
  }
  
  el.innerHTML = state.bots.map(bot => {
    const hpPct = Math.min(100, (bot.health / 20) * 100);
    const foodPct = Math.min(100, (bot.food / 20) * 100);
    const tierClass = 'tier-' + (bot.tier || 'WOOD').toUpperCase();
    
    // Add to selectors
    botSel.innerHTML += `<option value="${bot.nickname}">${bot.nickname}</option>`;
    cmdSel.innerHTML += `<option value="${bot.username}">${bot.nickname}</option>`;
    
    return `
    <div class="bot-card ${bot.online ? '' : 'offline'}">
      <div class="bot-name">
        <span class="dot ${bot.online ? 'online' : 'offline'}">●</span>
        <span id="name-${bot.username}">${bot.nickname}</span>
        <span class="tier-badge ${tierClass}">${bot.tier || 'Wood'}</span>
        <input class="nick-input" value="${bot.nickname}" 
               placeholder="nickname"
               onblur="setNick('${bot.username}', this.value)">
      </div>
      <div class="hp-bar"><div class="hp-fill" style="width:${hpPct}%"></div></div>
      <div class="hp-bar"><div class="hp-fill food-fill" style="width:${foodPct}%"></div></div>
      <div class="bot-stats">
        <span>❤ ${bot.health}</span>
        <span>🍗 ${bot.food}</span>
        <span>${bot.state || 'IDLE'}</span>
      </div>
      <div class="bot-task">${bot.task || 'Idle'}</div>
      <div class="bot-progress">${bot.progress || ''} ${bot.eta ? '| ETA ' + bot.eta : ''}</div>
      <div class="bot-coords">${bot.x} ${bot.y} ${bot.z}</div>
      <div class="bot-actions">
        <button onclick="pauseBot('${bot.username}')">⏸ Pause</button>
        <button onclick="resumeBot('${bot.username}')">▶ Resume</button>
      </div>
    </div>`;
  }).join('');
  
  // Restore selections
  if (currentBot) botSel.value = currentBot;
  if (currentCmd) cmdSel.value = currentCmd;
}

function renderTasks() {
  const el = document.getElementById('tasksList');
  if (!state.tasks || state.tasks.length === 0) {
    el.innerHTML = '<div style="color:#555;font-size:0.82rem;">No tasks</div>';
    return;
  }
  el.innerHTML = state.tasks.map(t => `
    <div class="task-item">
      <div class="task-info">
        <div class="task-name">${t.item || t.type} × ${t.quantity}</div>
        <div class="task-meta">${t.progress} | ${t.bot}</div>
      </div>
      <span class="task-status status-${t.status}">${t.status}</span>
      <button class="danger" onclick="removeTask('${t.id}')" style="padding:3px 8px;">✕</button>
    </div>
  `).join('');
}

function addLog(msg) {
  logs.push(msg);
  if (logs.length > MAX_LOGS) logs.shift();
  appendLogEntry(msg);
}

function appendLogEntry(msg) {
  const el = document.getElementById('logEntries');
  const filter = document.getElementById('logFilter').value.toLowerCase();
  if (filter && !msg.msg.toLowerCase().includes(filter)) return;
  
  const div = document.createElement('div');
  div.className = 'log-entry log-' + msg.level;
  div.innerHTML = `<span class="time">${msg.time}</span><span class="level-${msg.level}">[${msg.level}]</span> <span class="msg">${msg.msg}</span>`;
  el.appendChild(div);
  
  if (document.getElementById('autoScroll').checked) {
    el.scrollTop = el.scrollHeight;
  }
}

function renderLogs() {
  const el = document.getElementById('logEntries');
  el.innerHTML = '';
  logs.forEach(appendLogEntry);
}

function filterLogs() {
  renderLogs();
}

function clearLogs() {
  logs = [];
  document.getElementById('logEntries').innerHTML = '';
}

async function post(path, body) {
  try {
    await fetch(path, {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(body)
    });
  } catch(e) {}
}

function addTask() {
  const item = document.getElementById('taskItem').value.trim();
  const qty = parseInt(document.getElementById('taskQty').value) || 64;
  const type = document.getElementById('taskType').value || 'AUTO';
  const bot = document.getElementById('taskBot').value;
  const actionType = type === 'HUNT_FOOD' || type === 'GRIND';
  if (!item && !actionType) return;
  // If AUTO selected, omit type so server will auto-detect
  const payload = {item, qty, bot};
  if (type && type !== 'AUTO') payload.type = type;
  post('/api/task/add', payload);
  document.getElementById('taskItem').value = '';
}

function removeTask(id) {
  post('/api/task/remove', {id});
}

function pauseBot(username) {
  post('/api/task/pause', {bot: username});
}

function resumeBot(username) {
  post('/api/task/resume', {bot: username});
}

function action(name) {
  post('/api/action/' + name, {});
}

function sendCmd() {
  const target = document.getElementById('cmdTarget').value;
  const cmd = document.getElementById('cmdInput').value.trim();
  if (!cmd) return;
  post('/api/command', {target, cmd});
  document.getElementById('cmdInput').value = '';
}

function setNick(username, nickname) {
  post('/api/nickname', {username, nickname});
}

connect();
</script>
</body>
</html>
""";
    }
}
