import http from 'node:http';
import crypto from 'node:crypto';

const STUB_TOKEN = 'stub-panel-token-not-a-real-secret';
const MAX_BODY = 16 * 1024;
const WS_GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';

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
      if (before !== state.tasks.length) broadcast();
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
        log('INFO', `HALT: cancelled ${task.id}`);
        broadcast();
      }
      return;
    }

    if (pathname === '/api/task/pause') {
      const task = activeTaskFor(body.bot);
      const bot = state.bots[0];
      if (task && bot) {
        bot.halted = true;
        log('INFO', `HALT: paused ${task.id}`);
        broadcast();
      }
      return;
    }

    if (pathname === '/api/task/resume') {
      const bot = state.bots[0];
      const task = activeTaskFor(body.bot);
      if (task && bot) {
        bot.halted = false;
        log('INFO', `HALT: resumed ${task.id}`);
        broadcast();
      }
      return;
    }

    if (pathname === '/api/task/remove') {
      state.tasks = state.tasks.filter((task) => task.id !== body.id);
      broadcast();
      return;
    }

    if (pathname === '/api/action/cancel' || pathname === '/api/action/stop') {
      for (const task of state.tasks) {
        if (!isTerminal(task.status)) {
          task.status = 'CANCELLED';
          task.reason = 'cancelled';
        }
      }
      state.bots[0].halted = false;
      log('INFO', 'HALT: cancelled all');
      broadcast();
    }
  }

  function addTask(body) {
    const type = String(body.type || 'GATHER').toUpperCase();
    const item = String(body.item || 'oak_log');
    const qty = Number(body.qty) || 1;
    const task = {
      id: crypto.randomUUID(),
      type,
      item,
      quantity: qty,
      progress: `0/${qty}`,
      status: 'PENDING',
      reason: '',
      bot: 'StubBot'
    };
    state.tasks.push(task);
    log('INFO', `Task added: ${item} x${qty} → StubBot`);
    broadcast();

    schedule(() => {
      if (task.status !== 'PENDING') return;
      task.status = 'ACTIVE';
      task.progress = `0/${qty}`;
      state.bots[0].state = 'GATHERING';
      state.bots[0].task = `Gather ${item}`;
      log('INFO', `TASK: active ${task.id}`);
      broadcast();
    }, 40);

    for (const delay of [180, 280]) {
      schedule(() => {
        if (task.status !== 'ACTIVE') return;
        if (state.bots[0].halted) return;
        task.progress = delay === 180 ? `${Math.max(1, Math.floor(qty / 2))}/${qty}` : `${qty}/${qty}`;
        if (delay === 280) {
          task.status = 'COMPLETE';
          task.reason = '';
          state.bots[0].state = 'IDLE';
          state.bots[0].task = 'Idle';
          log('INFO', `TASK: complete ${task.id} evidence=inv`);
        }
        broadcast();
      }, delay);
    }
  }

  function activeTaskFor(bot) {
    const wanted = String(bot ?? '').toLowerCase();
    return state.tasks.find((task) => {
      if (isTerminal(task.status)) return false;
      if (wanted && wanted !== 'any' && wanted !== 'all' && String(task.bot).toLowerCase() !== wanted) {
        return false;
      }
      return true;
    });
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

  function broadcast(message = state) {
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
    bots: [
      {
        username: 'StubBot',
        nickname: 'StubBot',
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
      }
    ],
    tasks: []
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
