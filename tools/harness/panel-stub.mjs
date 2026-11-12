import http from 'node:http';
import crypto from 'node:crypto';

const STUB_TOKEN = 'stub-panel-token-not-a-real-secret';
const MAX_BODY = 16 * 1024;
const WS_GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';
const STALE_AGE_MS = 6000;

export async function startPanelStub({ port = 0 } = {}) {
  const state = makeState();
  const logs = [];
  const clients = new Set();
  const timers = new Set();

  const server = http.createServer(async (request, response) => {
    try {
      if (!authorized(request)) {
        return sendText(response, 401, 'Unauthorized');
      }

      const url = new URL(request.url, 'http://stub');
      if (request.method === 'GET' && url.pathname === '/api/items') {
        return sendJson(response, 200, {
          items: ['minecraft:oak_log', 'minecraft:oak_planks', 'minecraft:chest']
        });
      }

      if (request.method === 'POST' && url.pathname === '/api/simulate') {
        const body = await readJsonBody(request);
        const result = handleSimulation(body);
        return sendJson(response, 200, { ok: true, ...result });
      }

      if (request.method === 'POST' && url.pathname.startsWith('/api/')) {
        const body = await readJsonBody(request);
        handleApi(url.pathname, body);
        return sendJson(response, 200, { ok: true });
      }

      return sendText(response, 404, 'Not found');
    } catch (error) {
      if (response.headersSent) {
        response.destroy();
        return;
      }
      return sendJson(response, 400, {
        ok: false,
        error: error instanceof Error ? error.message : String(error)
      });
    }
  });

  server.on('upgrade', (request, socket) => {
    const url = new URL(request.url, 'http://stub');
    if (url.pathname !== '/ws' || !authorized(request)) {
      socket.end('HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\n\r\n');
      return;
    }

    const key = request.headers['sec-websocket-key'];
    if (!key) {
      socket.end('HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n');
      return;
    }

    const accept = crypto
      .createHash('sha1')
      .update(`${key}${WS_GUID}`)
      .digest('base64');
    socket.write(
      [
        'HTTP/1.1 101 Switching Protocols',
        'Upgrade: websocket',
        'Connection: Upgrade',
        `Sec-WebSocket-Accept: ${accept}`,
        '',
        ''
      ].join('\r\n')
    );

    clients.add(socket);
    sendFrame(socket, state);
    sendFrame(socket, { type: 'log_history', logs });

    let buffer = Buffer.alloc(0);
    socket.on('data', (chunk) => {
      buffer = Buffer.concat([buffer, chunk]);
      buffer = readFrames(buffer, socket);
    });
    socket.on('close', () => clients.delete(socket));
    socket.on('error', () => clients.delete(socket));
  });

  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, '127.0.0.1', resolve);
  });

  const address = server.address();
  return {
    server,
    port: address.port,
    baseUrl: `http://127.0.0.1:${address.port}`,
    token: STUB_TOKEN,
    state,
    simulate: (action, args = {}) => handleSimulation({ action, ...args }),
    close: async () => {
      for (const timer of timers) clearTimeout(timer);
      timers.clear();
      for (const client of clients) client.destroy();
      clients.clear();
      await new Promise((resolve) => server.close(resolve));
    }
  };

  function handleApi(pathname, body) {
    if (pathname === '/api/task/clear_done') {
      const before = state.tasks.length;
      state.tasks = state.tasks.filter((task) => !isTerminal(task.status));
      if (before !== state.tasks.length) broadcastState();
      log('INFO', 'Cleared completed tasks');
      return;
    }

    if (pathname === '/api/task/add') {
      addTask(body);
      return;
    }

    if (pathname === '/api/task/cancel') {
      const task = activeTaskFor(body.bot);
      if (task) {
        task.status = 'CANCELLED';
        task.reason = 'cancelled';
        for (const bot of state.bots) {
          if (bot.username === task.bot) {
            bot.halted = false;
            bot.state = 'IDLE';
            bot.task = 'Idle';
          }
        }
        log('INFO', `HALT: cancelled ${task.id}`);
        broadcastState();
      }
      return;
    }

    if (pathname === '/api/task/pause') {
      const task = activeTaskFor(body.bot);
      const bot = findBot(body.bot);
      if (task && bot) {
        bot.halted = true;
        log('INFO', `HALT: paused ${task.id}`);
        broadcastState();
      }
      return;
    }

    if (pathname === '/api/task/resume') {
      const bot = findBot(body.bot);
      const task = activeTaskFor(body.bot);
      if (task && bot) {
        bot.halted = false;
        log('INFO', `HALT: resumed ${task.id}`);
        broadcastState();
      }
      return;
    }

    if (pathname === '/api/task/remove') {
      state.tasks = state.tasks.filter((task) => task.id !== body.id);
      broadcastState();
      return;
    }

    if (pathname === '/api/action/cancel' || pathname === '/api/action/stop') {
      for (const task of state.tasks) {
        if (!isTerminal(task.status)) {
          task.status = 'CANCELLED';
          task.reason = 'cancelled';
        }
      }
      for (const bot of state.bots) {
        bot.halted = false;
        bot.state = 'IDLE';
        bot.task = 'Idle';
      }
      log('INFO', 'HALT: cancelled all');
      broadcastState();
    }
  }

  function handleSimulation(body) {
    const action = String(body.action ?? '');
    switch (action) {
      case 'reset':
        reset();
        return { action, state: structuredClone(state) };
      case 'holdTasks':
        state.holdTasks = Boolean(body.held ?? true);
        broadcastState();
        return { action, held: state.holdTasks };
      case 'disconnect':
        disconnect(body.bot);
        return { action, state: structuredClone(state) };
      case 'reconnect':
        reconnect(body.bot);
        return { action, state: structuredClone(state) };
      case 'hostRestart':
        hostRestart();
        return { action, state: structuredClone(state) };
      case 'hostRecover':
        hostRecover();
        return { action, state: structuredClone(state) };
      case 'stale':
        stale();
        return { action, state: structuredClone(state) };
      case 'refresh':
        refresh();
        return { action, state: structuredClone(state) };
      case 'retryThenFail':
        retryThenFail(body);
        return { action, state: structuredClone(state) };
      case 'depositMismatch':
        depositMismatch();
        return { action, state: structuredClone(state) };
      case 'secondWorker':
        secondWorker(body.name ?? 'StubBotTwo');
        return { action, state: structuredClone(state) };
      case 'raceClaim':
        raceClaim();
        return { action, state: structuredClone(state) };
      case 'completeClaimed':
        completeClaimed();
        return { action, state: structuredClone(state) };
      case 'death':
        death(body.bot);
        return { action, state: structuredClone(state) };
      case 'respawn':
        respawn(body.bot);
        return { action, state: structuredClone(state) };
      case 'knownLimitations':
        knownLimitations();
        return { action, state: structuredClone(state) };
      default:
        throw new Error(`unknown panel simulation action: ${action}`);
    }
  }

  function reset() {
    for (const timer of timers) clearTimeout(timer);
    timers.clear();
    const fresh = makeState();
    state.modVersion = fresh.modVersion;
    state.buildCommit = fresh.buildCommit;
    state.hostName = fresh.hostName;
    state.dimension = fresh.dimension;
    state.connected = fresh.connected;
    state.updatedAt = fresh.updatedAt;
    state.stale = false;
    state.holdTasks = false;
    state.claimCount = 0;
    state.completionCount = 0;
    state.rejectedClaims = 0;
    state.retries = 0;
    state.deaths = 0;
    state.deposit = { status: 'IDLE', reason: '' };
    state.bots.splice(0, state.bots.length, ...fresh.bots);
    state.tasks.length = 0;
    logs.length = 0;
    for (const client of clients) sendFrame(client, { type: 'log_history', logs });
    broadcastState();
  }

  function addTask(body) {
    const type = String(body.type || 'GATHER').toUpperCase();
    const item = String(body.item || 'oak_log');
    const qty = Number(body.qty) || 1;
    const requestedBot = String(body.bot || 'any');
    const task = newTask(type, item, qty, requestedBot);
    state.tasks.push(task);
    log('INFO', `Task added: ${item} x${qty} → ${requestedBot}`);
    broadcastState();

    schedule(() => {
      if (task.status !== 'PENDING') return;
      const bot = chooseBot(requestedBot);
      if (!bot) {
        task.status = 'FAILED';
        task.reason = 'no-worker';
        log('WARN', `TASK: failed ${task.id} reason=no-worker`);
        broadcastState();
        return;
      }
      claimTask(task, bot);
    }, 40);

    if (!state.holdTasks) {
      for (const delay of [180, 280]) {
        schedule(() => {
          if (task.status !== 'ACTIVE') return;
          const bot = botForTask(task);
          if (bot?.halted) return;
          task.progress = delay === 180
            ? `${Math.max(1, Math.floor(qty / 2))}/${qty}`
            : `${qty}/${qty}`;
          if (delay === 280) completeTask(task);
          else broadcastState();
        }, delay);
      }
    }
  }

  function claimTask(task, bot) {
    if (task.status !== 'PENDING') return false;
    task.status = 'ACTIVE';
    task.bot = bot.username;
    task.progress = `0/${task.quantity}`;
    task.reason = '';
    bot.state = 'GATHERING';
    bot.task = `Gather ${task.item}`;
    state.claimCount += 1;
    log('INFO', `TASK: active ${task.id}`);
    broadcastState();
    return true;
  }

  function completeTask(task) {
    if (task.status !== 'ACTIVE') return false;
    task.progress = `${task.quantity}/${task.quantity}`;
    task.status = 'COMPLETE';
    task.reason = '';
    state.completionCount += 1;
    const bot = botForTask(task);
    if (bot) {
      bot.state = 'IDLE';
      bot.task = 'Idle';
    }
    log('INFO', `TASK: complete ${task.id} evidence=inv`);
    broadcastState();
    return true;
  }

  function disconnect(name) {
    const bot = findBot(name);
    if (!bot) throw new Error(`no bot named ${name ?? '<first>'}`);
    const task = activeTaskFor(bot.username);
    bot.online = false;
    bot.state = 'DISCONNECTED';
    bot.task = 'Disconnected';
    updateConnected();
    log('WARN', 'Worker disconnected on ip: 127.0.0.1 (simulated)');
    log('INFO', 'Worker will attempt reconnect in 5s.');
    if (task) requeueTask(task, bot.username);
    broadcastState();
  }

  function reconnect(name) {
    const bot = findBot(name);
    if (!bot) throw new Error(`no bot named ${name ?? '<first>'}`);
    bot.online = true;
    bot.state = 'IDLE';
    bot.task = 'Idle';
    updateConnected();
    log('INFO', 'New worker connected on 127.0.0.1:0 (simulated)');
    broadcastState();
  }

  function hostRestart() {
    let requeued = 0;
    for (const bot of state.bots) {
      bot.online = false;
      bot.state = 'DISCONNECTED';
      bot.task = 'Disconnected';
    }
    for (const task of state.tasks) {
      if (task.status === 'ACTIVE') {
        requeueTask(task, task.bot, false);
        requeued += 1;
      }
    }
    if (requeued > 0) log('INFO', `Requeued ${requeued} active task(s) from StubBot`);
    updateConnected();
    log('WARN', 'Host restart (simulated)');
    broadcastState();
  }

  function hostRecover() {
    for (const bot of state.bots) {
      bot.online = true;
      bot.state = 'IDLE';
      bot.task = 'Idle';
    }
    updateConnected();
    log('INFO', 'Host recovery complete (simulated)');
    broadcastState();
  }

  function stale() {
    state.stale = true;
    state.updatedAt = new Date(Date.now() - STALE_AGE_MS).toISOString();
    const bot = state.bots[0];
    if (bot) bot.online = false;
    updateConnected();
    log('WARN', 'PANEL: state is stale (simulated)');
    broadcastState();
  }

  function refresh() {
    state.stale = false;
    state.updatedAt = new Date().toISOString();
    const bot = state.bots[0];
    if (bot) bot.online = true;
    updateConnected();
    log('INFO', 'PANEL: state refreshed (simulated)');
    broadcastState();
  }

  function retryThenFail(body) {
    const task = activeTaskFor(body.bot) || state.tasks.find((entry) => !isTerminal(entry.status));
    if (!task) throw new Error('no non-terminal task for retryThenFail');
    log('INFO', 'DROPS: skipped (inventory full)');
    log('WARN', `TASK: retry ${task.id} attempt=1/2 reason=unproven-completion`);
    task.status = 'FAILED';
    task.reason = 'unproven-completion';
    task.progress = `0/${task.quantity}`;
    state.retries += 1;
    const bot = botForTask(task);
    if (bot) {
      bot.state = 'IDLE';
      bot.task = 'Idle';
    }
    log('WARN', `TASK: failed ${task.id} reason=unproven-completion`);
    broadcastState();
  }

  function depositMismatch() {
    state.deposit = { status: 'REFUSED', reason: 'menu-mismatch' };
    log('WARN', 'DEPOSIT: refused reason=menu-mismatch');
    broadcastState();
  }

  function secondWorker(name) {
    if (state.bots.some((bot) => bot.username === name)) return;
    state.bots.push(makeBot(name));
    updateConnected();
    log('INFO', 'New worker connected on 127.0.0.1:0 (simulated)');
    broadcastState();
  }

  function raceClaim() {
    const task = state.tasks.find((entry) => entry.status === 'PENDING');
    if (!task) {
      state.rejectedClaims += 1;
      const active = state.tasks.find((entry) => entry.status === 'ACTIVE');
      log(
        'WARN',
        active
          ? `TASK: claim rejected id=${active.id} reason=already-claimed`
          : 'TASK: claim rejected reason=no-pending-task'
      );
      broadcastState();
      return;
    }
    const bot = state.bots.find((entry) => entry.online);
    if (!bot) {
      task.status = 'FAILED';
      task.reason = 'no-worker';
      log('WARN', `TASK: failed ${task.id} reason=no-worker`);
      broadcastState();
      return;
    }
    claimTask(task, bot);
  }

  function completeClaimed() {
    const task = state.tasks.find((entry) => entry.status === 'ACTIVE');
    if (!task) throw new Error('no active task for completeClaimed');
    completeTask(task);
  }

  function death(name) {
    const bot = findBot(name);
    if (!bot) throw new Error(`no bot named ${name ?? '<first>'}`);
    bot.health = 0;
    bot.state = 'DEAD';
    state.deaths += 1;
    log('WARN', 'Death recorded at 0 64 0 (minecraft:overworld)');
    log('WARN', 'Death was in minecraft:overworld; refusing cross-dimension recovery');
    const task = activeTaskFor(bot.username);
    if (task) {
      task.status = 'FAILED';
      task.reason = 'death';
      bot.state = 'DEAD';
      log('WARN', `TASK: failed ${task.id} reason=death`);
    }
    broadcastState();
  }

  function respawn(name) {
    const bot = findBot(name);
    if (!bot) throw new Error(`no bot named ${name ?? '<first>'}`);
    bot.health = 20;
    bot.state = 'IDLE';
    bot.task = 'Idle';
    log('INFO', 'Respawned after death (simulated)');
    broadcastState();
  }

  function knownLimitations() {
    const smelt = newTask('SMELT', 'iron_ingot', 1, 'StubBot');
    smelt.status = 'FAILED';
    smelt.reason = 'no-fuel';
    const build = newTask('BUILD', 'schematic', 1, 'StubBot');
    build.status = 'FAILED';
    build.reason = 'unproven-completion';
    state.tasks.push(smelt, build);
    log('WARN', 'SMELT: no usable fuel');
    log('WARN', `TASK: failed ${smelt.id} reason=no-fuel`);
    log('WARN', `TASK: failed ${build.id} reason=unproven-completion`);
    broadcastState();
  }

  function requeueTask(task, botName, announce = true) {
    task.status = 'PENDING';
    task.reason = 'requeued';
    task.bot = '';
    if (announce) log('INFO', `Requeued 1 active task(s) from ${botName}`);
  }

  function activeTaskFor(bot) {
    const wanted = String(bot ?? '').toLowerCase();
    return state.tasks.find((task) => {
      if (isTerminal(task.status)) return false;
      if (task.status !== 'ACTIVE') return false;
      if (wanted && wanted !== 'any' && wanted !== 'all' && String(task.bot).toLowerCase() !== wanted) {
        return false;
      }
      return true;
    });
  }

  function chooseBot(name) {
    const wanted = String(name ?? '').toLowerCase();
    if (!wanted || wanted === 'any' || wanted === 'all') {
      return state.bots.find((bot) => bot.online) ?? null;
    }
    return state.bots.find((bot) => bot.online && bot.username.toLowerCase() === wanted) ?? null;
  }

  function findBot(name) {
    const wanted = String(name ?? '').toLowerCase();
    if (!wanted || wanted === 'any' || wanted === 'all') return state.bots[0] ?? null;
    return state.bots.find((bot) =>
      bot.username.toLowerCase() === wanted || bot.nickname.toLowerCase() === wanted
    ) ?? null;
  }

  function botForTask(task) {
    return state.bots.find((bot) => bot.username === task.bot) ?? null;
  }

  function updateConnected() {
    state.connected = state.bots.filter((bot) => bot.online).length;
  }

  function schedule(fn, delay) {
    const timer = setTimeout(() => {
      timers.delete(timer);
      fn();
    }, delay);
    timers.add(timer);
  }

  function log(level, msg) {
    const entry = {
      type: 'log',
      time: new Date().toISOString().slice(11, 19),
      level,
      msg
    };
    logs.push(entry);
    if (logs.length > 500) logs.shift();
    broadcast(entry);
  }

  function broadcastState() {
    if (!state.stale) state.updatedAt = new Date().toISOString();
    broadcast(state);
  }

  function broadcast(message) {
    for (const client of clients) {
      if (client.destroyed) {
        clients.delete(client);
        continue;
      }
      sendFrame(client, message);
    }
  }
}

function makeState() {
  return {
    type: 'state',
    modVersion: 'stub',
    buildCommit: 'stub',
    hostName: 'StubHost',
    dimension: 'minecraft:overworld',
    connected: 1,
    updatedAt: new Date().toISOString(),
    stale: false,
    holdTasks: false,
    claimCount: 0,
    completionCount: 0,
    rejectedClaims: 0,
    retries: 0,
    deaths: 0,
    deposit: { status: 'IDLE', reason: '' },
    bots: [makeBot('StubBot')],
    tasks: []
  };
}

function makeBot(username) {
  return {
    username,
    nickname: username,
    online: true,
    health: 20,
    food: 20,
    x: 0,
    y: 64,
    z: 0,
    task: 'Idle',
    progress: '',
    eta: '',
    tier: 'Wood',
    state: 'IDLE',
    halted: false
  };
}

function newTask(type, item, quantity, bot) {
  return {
    id: crypto.randomUUID(),
    type,
    item,
    quantity,
    progress: `0/${quantity}`,
    status: 'PENDING',
    reason: '',
    bot
  };
}

function isTerminal(status) {
  return status === 'COMPLETE' || status === 'FAILED' || status === 'CANCELLED';
}

function authorized(request) {
  const header = request.headers['x-swarm-token'];
  if (header === STUB_TOKEN) return true;
  const url = new URL(request.url, 'http://stub');
  return url.searchParams.get('token') === STUB_TOKEN;
}

async function readJsonBody(request) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > MAX_BODY) throw new Error('request body is too large');
    chunks.push(chunk);
  }
  if (chunks.length === 0) return {};
  const parsed = JSON.parse(Buffer.concat(chunks).toString('utf8'));
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('request body must be a JSON object');
  }
  return parsed;
}

function sendJson(response, status, value) {
  const body = Buffer.from(JSON.stringify(value));
  response.writeHead(status, {
    'Content-Type': 'application/json',
    'Cache-Control': 'no-store',
    'Content-Length': body.length
  });
  response.end(body);
}

function sendText(response, status, text) {
  const body = Buffer.from(text);
  response.writeHead(status, {
    'Content-Type': 'text/plain; charset=UTF-8',
    'Content-Length': body.length
  });
  response.end(body);
}

function sendFrame(socket, value) {
  const payload = Buffer.from(JSON.stringify(value));
  const header = frameHeader(payload.length);
  socket.write(Buffer.concat([header, payload]));
}

function frameHeader(length) {
  if (length <= 125) {
    return Buffer.from([0x81, length]);
  }
  if (length <= 65535) {
    const header = Buffer.alloc(4);
    header[0] = 0x81;
    header[1] = 126;
    header.writeUInt16BE(length, 2);
    return header;
  }
  const header = Buffer.alloc(10);
  header[0] = 0x81;
  header[1] = 127;
  header.writeBigUInt64BE(BigInt(length), 2);
  return header;
}

function readFrames(buffer, socket) {
  while (buffer.length >= 2) {
    const opcode = buffer[0] & 0x0f;
    const masked = (buffer[1] & 0x80) !== 0;
    let length = buffer[1] & 0x7f;
    let offset = 2;

    if (length === 126) {
      if (buffer.length < 4) return buffer;
      length = buffer.readUInt16BE(2);
      offset = 4;
    } else if (length === 127) {
      if (buffer.length < 10) return buffer;
      length = Number(buffer.readBigUInt64BE(2));
      offset = 10;
    }

    const maskLength = masked ? 4 : 0;
    if (buffer.length < offset + maskLength + length) return buffer;

    let payload = buffer.subarray(offset + maskLength, offset + maskLength + length);
    if (masked) {
      const mask = buffer.subarray(offset, offset + 4);
      payload = Buffer.from(payload);
      for (let i = 0; i < payload.length; i += 1) {
        payload[i] ^= mask[i % 4];
      }
    }
    buffer = buffer.subarray(offset + maskLength + length);

    if (opcode === 0x8) {
      socket.end(Buffer.from([0x88, 0x00]));
      return Buffer.alloc(0);
    }
    if (opcode === 0x9) {
      socket.write(Buffer.concat([Buffer.from([0x8a, payload.length]), payload]));
    }
  }
  return buffer;
}
