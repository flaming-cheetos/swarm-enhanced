// Enhanced Swarm MCP server — Streamable HTTP (single POST /mcp endpoint),
// zero dependencies. Spec: docs/MCP_SERVER_SPEC.md
//
// Security: binds 127.0.0.1 only, token on every request, Origin allowlist,
// no CORS wildcard. Destructive tools require SWARM_MCP_ALLOW_DESTRUCTIVE=1.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { exec, spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { Rcon, parseTickQuery } from './lib/rcon.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const CONFIG_PATH = process.env.SWARM_MCP_CONFIG || path.join(HERE, 'config.json');
const EXAMPLE_PATH = path.join(HERE, 'config.example.json');

const PROTOCOL_VERSIONS = ['2026-07-28', '2025-11-25', '2025-06-18', '2025-03-26'];
const SERVER_INFO = { name: 'enhanced-swarm-mcp', version: '0.1.0' };

// ---------------------------------------------------------------- config ----
function loadConfig() {
  const base = JSON.parse(fs.readFileSync(EXAMPLE_PATH, 'utf8'));
  if (fs.existsSync(CONFIG_PATH)) {
    Object.assign(base, JSON.parse(fs.readFileSync(CONFIG_PATH, 'utf8')));
  }
  if (process.env.SWARM_MCP_PORT) base.port = Number(process.env.SWARM_MCP_PORT);
  if (process.env.SWARM_MCP_PANEL) base.panel = process.env.SWARM_MCP_PANEL;
  return base;
}

const CFG = loadConfig();
const DESTRUCTIVE = process.env.SWARM_MCP_ALLOW_DESTRUCTIVE === '1' || CFG.destructive === true;
const RC_LOCK = { chain: Promise.resolve() };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** Token: env → config → newest `Web panel: …?token=` line in main's log. */
function resolveToken() {
  if (process.env.SWARM_MCP_TOKEN) return process.env.SWARM_MCP_TOKEN;
  if (CFG.token) return CFG.token;
  try {
    const log = fs.readFileSync(CFG.instances.main.log, 'utf8');
    const m = [...log.matchAll(/Web panel: http:\/\/[\d.]+:\d+\/\?token=([A-Za-z0-9_-]+)/g)].pop();
    if (m) return m[1];
  } catch { /* log not there yet */ }
  return null;
}
let TOKEN = resolveToken();

function logTool(name, args, ok, ms, detail) {
  try {
    const dir = path.join(HERE, 'logs');
    fs.mkdirSync(dir, { recursive: true });
    const line = JSON.stringify({
      t: Date.now(), tool: name, ok, ms,
      args: redact(args), detail: detail ? String(detail).slice(0, 400) : undefined,
    });
    fs.appendFileSync(path.join(dir, `session-${new Date().toISOString().slice(0, 10)}.jsonl`), line + '\n');
  } catch { /* logging must never fail a tool */ }
}
function redact(o) {
  if (!o || typeof o !== 'object') return o;
  const out = {};
  for (const [k, v] of Object.entries(o)) out[k] = /token|password|secret/i.test(k) ? '••••' : v;
  return out;
}

// ----------------------------------------------------------------- panel ----
async function panel(method, route, body) {
  if (!TOKEN) TOKEN = resolveToken();
  if (!TOKEN) throw toolError('no_token', 'no panel token: set SWARM_MCP_TOKEN or start the host');
  const url = CFG.panel.replace(/\/$/, '') + route;
  const res = await fetch(url, {
    method,
    headers: { 'X-Swarm-Token': TOKEN, 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { json = { raw: text }; }
  if (!res.ok) {
    if (res.status === 404 && route.startsWith('/api/debug')) {
      throw toolError('endpoint_missing',
        `${route} is 404 — the running host jar predates the debug endpoints; rebuild and restart the host`);
    }
    throw toolError(`panel_${res.status}`, text.slice(0, 300));
  }
  return json;
}

function toolError(code, message) {
  const e = new Error(message);
  e.code = code;
  return e;
}

// ------------------------------------------------------------------ rcon ----
function rconPassword() {
  if (process.env.SWARM_MC_RCON_PASSWORD) return process.env.SWARM_MC_RCON_PASSWORD;
  if (CFG.mcServer?.password) return CFG.mcServer.password;
  const file = CFG.mcServer?.passwordFile || path.join(HERE, '.rcon-password');
  try {
    const v = fs.readFileSync(file, 'utf8').trim();
    if (v) return v;
  } catch { /* no file */ }
  return null;
}
async function rcon(fn) {
  const run = async () => {
    const r = new Rcon({
      host: CFG.mcServer.host,
      port: CFG.mcServer.rconPort,
      password: rconPassword(),
      timeoutMs: 8000,
    });
    await r.connect();
    try { return await fn(r); } finally { r.close(); }
  };
  const p = RC_LOCK.chain.then(run, run);
  RC_LOCK.chain = p.catch(() => {});
  return p;
}

const MC_SAFE = [/^\/?tick\b/i, /^\/?fill\b/i, /^\/?setblock\b/i, /^\/?summon\b/i, /^\/?tp\b/i,
  /^\/?teleport\b/i, /^\/?give\b/i, /^\/?time\b/i, /^\/?weather\b/i, /^\/?gamerule\b/i,
  /^\/?data\b/i, /^\/?list\b/i, /^\/?seed\b/i, /^\/?say\b/i, /^\/?kill\b/i, /^\/?clear\b/i];

// --------------------------------------------------------------- helpers ----
function run(cmd, args, opts = {}) {
  return new Promise((resolve) => {
    exec([cmd, ...args].map((a) => (/\s/.test(a) ? `"${a}"` : a)).join(' '), { ...opts, maxBuffer: 8 << 20 },
      (err, stdout, stderr) => resolve({ ok: !err, code: err?.code ?? 0, stdout: stdout || '', stderr: stderr || '' }));
  });
}

async function listeningPid(port) {
  const { stdout } = await run('netstat', ['-ano']);
  for (const line of stdout.split(/\r?\n/)) {
    if (!line.includes(`:${port}`) || !line.includes('LISTENING')) continue;
    const parts = line.trim().split(/\s+/);
    const pid = Number(parts[parts.length - 1]);
    if (Number.isFinite(pid) && pid > 0) return pid;
  }
  return null;
}

function tailLog(file, bytes = 200000) {
  try {
    const st = fs.statSync(file);
    const start = Math.max(0, st.size - bytes);
    const fd = fs.openSync(file, 'r');
    const buf = Buffer.alloc(st.size - start);
    fs.readSync(fd, buf, 0, buf.length, start);
    fs.closeSync(fd);
    return buf.toString('utf8');
  } catch { return ''; }
}

function stripAnsi(s) { return s.replace(/\u00a7./g, '').replace(/\x1b\[[0-9;]*m/g, ''); }

// ----------------------------------------------------------------- tools ----
// The worker answers a WORLD_PROBE with one JSON snapshot, and the route only
// hands back what is already cached - so ask once, then poll until the reply
// lands. Never invent a result: if nothing arrives, say so.
async function probeWorld(bot, kind, args, tries = 12, id, closed = false) {
  let last = null;
  for (let i = 0; i < tries; i++) {
    // With an id the host resolves the position from the base registry - the
    // caller never supplies coordinates for a known base container.
    last = await panel('POST', '/api/debug/world',
      i === 0 ? (id ? { bot, id, open: !closed } : { bot, kind, args }) : { bot });
    if (last && last.ok && last.ageMs < 4000) return last;
    if (last && last.ok === false && last.error && last.error !== 'waiting') return last;
    await sleep(250);
  }
  return last;
}

// ---- B1: operator / diagnostic surface -------------------------------------
// Every instance runs the mod's own panel, so each instance can answer for itself.
// Reading the other instance's log means asking its panel, not reaching into a
// file path that only this machine happens to know.
// Every instance's panel mints its own token, printed once in that instance's log.
// Asking the bot's panel with the host's token is a 401, so resolve per instance.
const instanceTokens = new Map();
function tokenFor(instance) {
  const inst = CFG.instances[instance];
  if (!inst) throw toolError('unknown_instance', `no instance named ${instance}`);
  const cached = instanceTokens.get(instance);
  if (cached) return cached;
  try {
    const text = fs.readFileSync(inst.log, 'utf8');
    // Dot does not cross newlines, so this stays on the announcement line.
    const matches = [...text.matchAll(/Web panel:.+?token=([A-Za-z0-9]+)/g)];
    if (matches.length) {
      const tok = matches[matches.length - 1][1];
      instanceTokens.set(instance, tok);
      return tok;
    }
  } catch { /* log not readable yet */ }
  return resolveToken();
}

async function panelOn(instance, route, body) {
  const inst = CFG.instances[instance];
  if (!inst) throw toolError('unknown_instance', `no instance named ${instance}`);
  const tok = tokenFor(instance);
  const res = await fetch(`http://127.0.0.1:${inst.port}${route}`, {
    method: 'POST',
    headers: { 'X-Swarm-Token': tok, 'Content-Type': 'application/json' },
    body: JSON.stringify(body ?? {}),
  });
  const text = await res.text();
  try { return JSON.parse(text); } catch { return { ok: false, status: res.status, raw: text.slice(0, 400) }; }
}

// Chest contents as the probe reported them: id -> count.
function chestTotals(slots) {
  const out = {};
  for (const s of slots ?? []) {
    const id = String(s.item ?? '').replace('minecraft:', '');
    out[id] = (out[id] ?? 0) + (s.count ?? 0);
  }
  return out;
}

const chestBaselines = new Map();

const TERMINAL = ['COMPLETE', 'COMPLETED', 'FAILED', 'CANCELLED', 'CANCELED'];

const TOOLS = [
  // ---- observe
  {
    name: 'swarm_state',
    description: 'Whole-swarm state from the host: version, commit, dimension, connected count, bots[] and tasks[]. Use this first.',
    inputSchema: { type: 'object', properties: {} },
    handler: async () => panel('POST', '/api/debug/state', {}),
  },
  {
    name: 'task_list',
    description: 'Task queue rows only: id, type, item, qty, collected, status, owner, lease, attempt.',
    inputSchema: { type: 'object', properties: {} },
    handler: async () => {
      const s = await panel('POST', '/api/debug/state', {});
      return { ok: true, tasks: s.tasks ?? [] };
    },
  },
  {
    name: 'client_status',
    description: 'Local client instances: panel port, listening PID, whether the log shows a join, log path. No secrets.',
    inputSchema: { type: 'object', properties: {} },
    handler: async () => {
      const out = [];
      for (const [name, inst] of Object.entries(CFG.instances)) {
        const pid = await listeningPid(inst.port);
        const log = tailLog(inst.log, 60000);
        out.push({
          instance: name, port: inst.port, pid, listening: pid !== null,
          joinedRecently: log.includes(inst.joined),
          lastLine: stripAnsi(log.trim().split(/\r?\n/).pop() ?? '').slice(0, 160),
          log: inst.log,
        });
      }
      return { ok: true, instances: out, panelTokenFound: Boolean(resolveToken()) };
    },
  },
  {
    name: 'swarm_health',
    description: 'Host identity and readiness: mod version, build commit, dimension, connected bots, panel token present, RCON password present.',
    inputSchema: { type: 'object', properties: {} },
    handler: async () => {
      const s = await panel('POST', '/api/debug/state', {});
      return {
        ok: true, modVersion: s.modVersion, buildCommit: s.buildCommit,
        hostName: s.hostName, dimension: s.dimension, connected: s.connected,
        panelToken: Boolean(TOKEN || resolveToken()),
        rconPassword: Boolean(rconPassword()),
        destructiveTools: DESTRUCTIVE,
      };
    },
  },

  {
    name: 'bot_state',
    description: 'Brain state for one bot: plan, current task, withdraw guard, smelt baseline, live inventory, storage cache and base anchor. Asks the worker for a fresh snapshot and polls briefly for the reply.',
    inputSchema: { type: 'object', properties: { bot: { type: 'string' }, refresh: { type: 'boolean' } }, required: ['bot'] },
    handler: async (a) => {
      const wantFresh = a.refresh !== false;
      let last = null;
      for (let i = 0; i < 8; i++) {
        last = await panel('POST', '/api/debug/bot', { bot: a.bot, refresh: wantFresh && i === 0 });
        if (last && last.ok && (!wantFresh || last.ageMs < 3000)) return last;
        await sleep(250);
      }
      return last;
    },
  },
  {
    name: 'world_container',
    description: 'What is really in one container at x,y,z. With open=true (default) the bot walks over, opens it and reports the menu contents, which is the only authoritative answer - a client-side block entity never holds a chest inventory. open=false reads the block entity only: type and position, no contents.',
    inputSchema: { type: 'object', properties: { bot: { type: 'string' }, id: { type: 'string', description: 'registry id, e.g. bf1 or chest-main (preferred over raw coords)' }, x: { type: 'number' }, y: { type: 'number' }, z: { type: 'number' }, open: { type: 'boolean' } }, required: ['bot'] },
    handler: async (a) => a.id
      ? probeWorld(a.bot, null, null, 90, a.id, a.open === false)
      : probeWorld(a.bot, a.open === false ? 'container' : 'container_open', `${a.x},${a.y},${a.z}`, a.open === false ? 12 : 90),
  },
  {
    name: 'world_containers',
    description: 'Every container within radius r of the bot: type and exact position, which is authoritative. Slot data here is the client-side view and is empty for chests, whose inventory only exists in the menu once opened - use world_container (open=true) or scan_chests for contents. A double chest is two blocks with one inventory, so both halves report the same contents - open either half, never sum them.',
    inputSchema: { type: 'object', properties: { bot: { type: 'string' }, radius: { type: 'number' } }, required: ['bot'] },
    handler: async (a) => probeWorld(a.bot, 'containers', String(Math.min(24, Math.max(1, a.radius ?? 12)))),
  },
  {
    name: 'world_blocks',
    description: 'Non-air blocks in a box around x,y,z (radius max 8). Use it to confirm a structure is where the log claims, instead of believing the log.',
    inputSchema: { type: 'object', properties: { bot: { type: 'string' }, x: { type: 'number' }, y: { type: 'number' }, z: { type: 'number' }, radius: { type: 'number' } }, required: ['bot', 'x', 'y', 'z'] },
    handler: async (a) => probeWorld(a.bot, 'blocks', `${a.x},${a.y},${a.z},${Math.min(8, Math.max(1, a.radius ?? 4))}`),
  },
  {
    name: 'base_registry',
    description: 'The base registry: every storage and furnace location the mod knows, by id, with the measurement that put it there. This is data on disk, not literals in code - it survives a restart without re-seeding.',
    inputSchema: { type: 'object', properties: {} },
    handler: async () => panel('POST', '/api/debug/registry', {}),
  },
  {
    name: 'log_tail',
    description: "Tail of a running instance's own log (that instance's /api/debug/logs), newest last, with an optional substring filter. Use it instead of guessing why something stopped.",
    inputSchema: { type: 'object', properties: { instance: { type: 'string', enum: ['main', 'bot'] }, lines: { type: 'number' }, filter: { type: 'string' } }, required: ['instance'] },
    handler: async (a) => {
      const lines = a.lines ?? 40;
      try {
        const r = await panelOn(a.instance, '/api/debug/logs', { lines, filter: a.filter });
        if (r && r.ok) return { ...r, source: 'api' };
      } catch { /* fall through to the file */ }
      // A worker instance serves no debug panel of its own, so ask its log directly.
      // The path comes from config, which is the same source client_launch uses.
      const inst = CFG.instances[a.instance];
      if (!inst) throw toolError('unknown_instance', `no instance named ${a.instance}`);
      let text = '';
      try { text = fs.readFileSync(inst.log, 'utf8'); }
      catch (e) { return { ok: false, error: 'log_unreadable', path: inst.log, detail: String(e.message) }; }
      const all = text.split(String.fromCharCode(10)).map((l) => l.replace(String.fromCharCode(13), '')).filter(Boolean);
      const kept = a.filter ? all.filter((l) => l.includes(a.filter)) : all;
      return {
        ok: true, source: 'file', path: inst.log,
        totalLines: all.length, matched: kept.length,
        lines: kept.slice(Math.max(0, kept.length - lines)),
      };
    },
  },
  {
    name: 'task_watch',
    description: 'Watch the live TaskQueue until one task reaches a terminal state (or the timeout), then return that task plus the queue. With no id it follows the busiest task. Replaces polling task_list by hand.',
    inputSchema: { type: 'object', properties: { id: { type: 'string' }, timeoutMs: { type: 'number' } } },
    handler: async (a) => {
      const deadline = Date.now() + Math.min(600000, a.timeoutMs ?? 120000);
      let last = null;
      while (Date.now() < deadline) {
        last = await panel('POST', '/api/debug/tasks', {});
        const tasks = last?.tasks ?? [];
        const tracked = a.id ? tasks.filter((t) => (t.id ?? t.taskId) === a.id) : tasks;
        const done = tracked.find((t) => TERMINAL.includes(String(t.status ?? '').toUpperCase()));
        if (done) return { ok: true, reached: true, task: done, queueSize: last?.size ?? tasks.length, waitedMs: Math.max(0, Date.now() - (deadline - Math.min(600000, a.timeoutMs ?? 120000))) };
        if (a.id && tracked.length === 0) {
          const state = await panel('POST', '/api/debug/state', {});
          const gone = (state?.tasks ?? []).find((t) => (t.id ?? t.taskId) === a.id);
          if (!gone) return { ok: true, reached: true, task: { id: a.id, status: 'GONE' }, note: 'not in the queue any more - cancelled, cleared or already reaped' };
        }
        await sleep(1000);
      }
      return { ok: true, reached: false, timedOut: true, queue: last?.tasks ?? [], panicked: last?.panicked, queueSize: last?.size ?? 0 };
    },
  },
  {
    name: 'chest_delta',
    description: 'Open a registry chest by id and report what changed since the previous call for that chest: per-item delta, current totals and slot usage. First call establishes the baseline and says so.',
    inputSchema: { type: 'object', properties: { bot: { type: 'string' }, id: { type: 'string' }, reset: { type: 'boolean' } }, required: ['bot', 'id'] },
    handler: async (a) => {
      const r = await panel('POST', '/api/debug/chest', { bot: a.bot, id: a.id });
      if (!r || r.ok === false) return r;
      const totals = chestTotals(r.data?.slots);
      const key = `${a.bot}:${a.id}`;
      const before = chestBaselines.get(key);
      if (a.reset) chestBaselines.delete(key);
      const delta = {};
      if (before) {
        for (const k of new Set([...Object.keys(before), ...Object.keys(totals)])) {
          const d = (totals[k] ?? 0) - (before[k] ?? 0);
          if (d !== 0) delta[k] = d;
        }
      }
      chestBaselines.set(key, totals);
      return {
        ok: true, id: a.id, pos: r.pos, type: r.data?.type,
        slotsUsed: r.data?.usedSlots, slotsTotal: r.data?.totalSlots,
        totals, delta,
        baseline: !before,
        note: before ? undefined : 'first read for this chest: baseline stored, call again after a change to see the delta',
      };
    },
  },
  {
    name: 'server_restart',
    description: "Restart one Minecraft instance under process control: stop the process listening on its panel port, relaunch it through the configured launcher, then wait until its own /api/debug/health answers from a new pid and report bootMs. The bot brain always comes back off.",
    inputSchema: { type: 'object', properties: { instance: { type: 'string', enum: ['main', 'bot'] }, timeoutMs: { type: 'number' } }, required: ['instance'] },
    handler: async (a) => {
      const inst = CFG.instances[a.instance];
      if (!inst) throw toolError('unknown_instance', `no instance named ${a.instance}`);
      const before = await listeningPid(inst.port);
      const t0 = Date.now();
      if (before !== null) {
        try { process.kill(before); } catch { /* already gone */ }
        const gone = Date.now() + 30000;
        while (Date.now() < gone && (await listeningPid(inst.port)) !== null) await sleep(500);
      }
      const child = spawn(inst.launcher, inst.args, { cwd: path.dirname(inst.launcher), detached: true, stdio: 'ignore' });
      child.unref();
      const deadline = Date.now() + (a.timeoutMs ?? 240000);
      let health = null;
      let readyBy = null;
      while (Date.now() < deadline) {
        await sleep(1500);
        const pid = await listeningPid(inst.port);
        if (pid === null) continue;
        if (pid === before) continue; // still the old process
        // Host: ask its own health endpoint. Worker: it serves no debug panel, so a
        // resolved-but-not-ok answer is expected and must not end the wait.
        let h = null;
        try { h = await panelOn(a.instance, '/api/debug/health', {}); } catch { h = null; }
        if (h && h.ok) { health = h; readyBy = 'health'; break; }
        try {
          const st = fs.statSync(inst.log);
          const text = fs.readFileSync(inst.log, 'utf8');
          const initIdx = text.lastIndexOf('Initializing Enhanced Swarm');
          const joinedSeen = initIdx >= 0 && text.indexOf(inst.joined, initIdx) !== -1;
          if (initIdx >= 0 && st.mtimeMs > t0) {
            readyBy = joinedSeen ? 'log+join' : 'log';
            health = { pid, joined: joinedSeen };
            break;
          }
        } catch { /* log not there yet */ }
      }
      return {
        ok: health !== null, instance: a.instance,
        oldPid: before, newPid: health?.pid ?? null,
        bootMs: Date.now() - t0, jar: health?.jar, readyBy,
        reachable: health !== null,
        hint: health ? (a.instance === 'bot' ? 'bot brain is OFF — resume with command{target:"jamal",cmd:".toggle bot-brain"}' : undefined)
          : 'no answer before timeout; check log_tail on that instance',
      };
    },
  },
  {
    name: 'panic',
    description: 'Pull the lever: stop dispatching new work, clear the pending queue, tell every bot to abandon its path, and leave the switch visibly on. Resume with {resume:true}. Does not touch a bot brain that the operator turned off.',
    inputSchema: { type: 'object', properties: { resume: { type: 'boolean' }, reason: { type: 'string' } } },
    handler: async (a) => {
      const r = await panel('POST', a.resume ? '/api/debug/resume' : '/api/debug/panic', { reason: a.reason });
      const q = await panel('POST', '/api/debug/tasks', {});
      let brain = null;
      try {
        const b = await panel('POST', '/api/debug/bot', { bot: 'jamal', refresh: true });
        brain = b?.data?.moduleEnabled ?? null;
      } catch { /* bot may be down */ }
      return { ...r, resumed: !!a.resume, queueSizeAfter: q?.size ?? null, panickedAfter: q?.panicked ?? null, botBrainOn: brain };
    },
  },
  {
    name: 'swarm_preflight',
    description: 'Readiness checks before queuing work: host reachable, bot online, brain active, not halted, inventory clean, storage index fresh, fuel known, food and health. Every check is pass/fail with the reason.',
    inputSchema: { type: 'object', properties: { bot: { type: 'string' } }, required: ['bot'] },
    handler: async (a) => {
      const checks = [];
      const add = (name, pass, detail) => checks.push({ name, pass, ...(detail === undefined ? {} : { detail }) });
      let state = null;
      try { state = await panel('POST', '/api/debug/state', {}); } catch (e) { add('host_reachable', false, e.message); }
      if (state) {
        add('host_reachable', true);
        add('worker_connected', (state.connected ?? 0) > 0, { connected: state.connected });
        const row = (state.bots ?? []).find((b) => b.username === a.bot);
        add('bot_online', Boolean(row && row.online), row ? { state: row.state } : 'not in the host bot list');
      }
      let botState = null;
      try {
        botState = await TOOL_MAP.get('bot_state').handler({ bot: a.bot });
      } catch (e) { add('debug_state', false, e.message); }
      if (botState) {
        if (!botState.ok) {
          add('debug_state', false, botState.error + (botState.detail ? ': ' + botState.detail : ''));
        } else {
          const d = botState.data ?? {};
          add('debug_state', true, { ageMs: botState.ageMs });
          add('brain_active', d.moduleEnabled === true, d.moduleEnabled ? undefined : 'BotBrain module is not active');
          add('not_halted', d.halted !== true);
          // Junk is exactly what the mod's own keep predicate (shouldKeepStatic) did
          // not classify as keep. Tools, gear, food and fuel land in `keep`; the MCP
          // must never re-decide that with a regex of its own.
          const junk = Object.entries(d.deposit ?? {});
          add('inventory_clean', junk.length === 0, junk.length ? Object.fromEntries(junk) : undefined);
          // B3: freshness comes from the index the host holds, not the worker's own
          // client-side storage cache - that one reports -1 until the worker scans,
          // which made a perfectly good index look cold forever.
          let idxState = null;
          try { idxState = await panel('POST', '/api/debug/storage', {}); } catch (e) { idxState = null; }
          const idxItems = idxState && idxState.ok ? Object.keys(idxState.totals ?? {}).length : 0;
          add('index_fresh', idxItems > 0, { items: idxItems, iron: idxState?.totals?.['minecraft:iron_ingot'] ?? 0 });
          const fuel = ['minecraft:coal', 'minecraft:charcoal', 'minecraft:coal_block', 'minecraft:blaze_rod'];
          const hasFuel = fuel.some((f) => (d.inventory?.[f] ?? 0) > 0 || (d.storage?.items?.[f] ?? 0) > 0);
          add('fuel_known', hasFuel, hasFuel ? undefined : 'no coal/charcoal in inventory or storage index');
          add('food_ok', (d.food ?? 0) >= 12, { food: d.food });
          add('health_ok', (d.health ?? 0) >= 10, { health: d.health });
        }
      }
      return {
        ok: true, bot: a.bot,
        pass: checks.every((c) => c.pass === true),
        failed: checks.filter((c) => c.pass === false).map((c) => c.name),
        checks,
      };
    },
  },

  // ---- control (panel)
  {
    name: 'task_add',
    description: 'Queue a task for a bot. item is a namespaced id (minecraft:iron_ingot); qty is the amount.',
    inputSchema: {
      type: 'object',
      properties: { bot: { type: 'string' }, item: { type: 'string' }, qty: { type: 'number' } },
      required: ['bot', 'item', 'qty'],
    },
    handler: (a) => panel('POST', '/api/task/add', { bot: a.bot, item: a.item, qty: a.qty }),
  },
  {
    name: 'task_cancel',
    description: 'Cancel the running task (sets it cancelling); omit id to cancel whatever is active.',
    inputSchema: { type: 'object', properties: { id: { type: 'string' } } },
    handler: (a) => panel('POST', '/api/task/cancel', a.id ? { id: a.id } : {}),
  },
  {
    name: 'task_pause',
    description: 'Pause the queue (bots halt where they are).',
    inputSchema: { type: 'object', properties: {} },
    handler: () => panel('POST', '/api/task/pause', {}),
  },
  {
    name: 'task_resume',
    description: 'Resume a paused queue.',
    inputSchema: { type: 'object', properties: {} },
    handler: () => panel('POST', '/api/task/resume', {}),
  },
  {
    name: 'task_remove',
    description: 'Remove a pending task row by id.',
    inputSchema: { type: 'object', properties: { id: { type: 'string' } }, required: ['id'] },
    handler: (a) => panel('POST', '/api/task/remove', { id: a.id }),
  },
  {
    name: 'task_clear_done',
    description: 'Clear finished task rows from the queue.',
    inputSchema: { type: 'object', properties: {} },
    handler: () => panel('POST', '/api/task/clear_done', {}),
  },
  {
    name: 'command',
    description: 'Send a raw chat/command to one bot or "all" (e.g. ".toggle bot-brain").',
    inputSchema: { type: 'object', properties: { target: { type: 'string' }, cmd: { type: 'string' } }, required: ['target', 'cmd'] },
    handler: (a) => panel('POST', '/api/command', { target: a.target, cmd: a.cmd }),
  },
  {
    name: 'bot_stop',
    description: 'Stop a bot where it is (cancel + halt).',
    inputSchema: { type: 'object', properties: { bot: { type: 'string' } }, required: ['bot'] },
    handler: (a) => panel('POST', '/api/action/stop', { bot: a.bot }),
  },
  {
    name: 'deposit',
    description: 'Make a bot deposit its inventory into base chests now (the same path a task uses).',
    inputSchema: { type: 'object', properties: { bot: { type: 'string' } } },
    handler: () => panel('POST', '/api/action/deposit', {}),
  },
  {
    name: 'deposit_self',
    description: 'Deposit the host player\'s own inventory into base chests.',
    inputSchema: { type: 'object', properties: {} },
    handler: () => panel('POST', '/api/action/deposit_self', {}),
  },
  {
    name: 'scan_chests',
    description: 'Ask a bot to scan known chests and refresh the storage index. Takes ~40 s for 4 chests.',
    inputSchema: { type: 'object', properties: {} },
    handler: () => panel('POST', '/api/action/scan_chests', {}),
  },
  {
    name: 'give',
    description: 'TEST FIXTURE: /give an item to the host player. Labelled so runs never confuse a fixture with real play.',
    inputSchema: {
      type: 'object',
      properties: { item: { type: 'string' }, count: { type: 'number' } },
      required: ['item', 'count'],
    },
    handler: (a) => panel('POST', '/api/action/test_give', { item: a.item, count: a.count }),
  },

  // ---- server (RCON)
  {
    name: 'tick_query',
    description: 'Measure server tick health: target rate, mspt, TPS. Call before and after any tick_rate change.',
    inputSchema: { type: 'object', properties: {} },
    handler: async () => {
      const r = await rcon((c) => c.exec('/tick query'));
      return { ok: true, ...parseTickQuery(r.response) };
    },
  },
  {
    name: 'tick_rate',
    description: 'Set the test server tick rate (20 = normal). Climb the ladder 40/60/100/140 and keep the highest that stays under maxMspt; always set back to 20 when done.',
    inputSchema: { type: 'object', properties: { rate: { type: 'number' } }, required: ['rate'] },
    handler: async (a) => {
      const rate = Math.max(20, Math.min(200, Math.round(a.rate)));
      const r = await rcon((c) => c.exec(`/tick rate ${rate}`));
      const q = await rcon((c) => c.exec('/tick query'));
      return { ok: true, requested: rate, response: r.response, ...parseTickQuery(q.response) };
    },
  },
  {
    name: 'mc_command',
    description: 'Run a server console command over RCON (/fill, /tp, /summon, /give, /time, /weather, /gamerule, /data, /list, /seed, /say, /clear, /kill). Anything else needs destructive mode.',
    inputSchema: { type: 'object', properties: { cmd: { type: 'string' } }, required: ['cmd'] },
    handler: async (a) => {
      const cmd = a.cmd.trim();
      if (!DESTRUCTIVE && !MC_SAFE.some((re) => re.test(cmd))) {
        throw toolError('command_not_allowed',
          `refused "${cmd}": not on the safe list; set SWARM_MCP_ALLOW_DESTRUCTIVE=1 for arbitrary console commands`);
      }
      const r = await rcon((c) => c.exec(cmd));
      return { ok: true, command: r.command, response: r.response };
    },
  },

  // ---- clients (local)
  {
    name: 'client_launch',
    description: 'Launch a Prism instance detached and wait until its log shows a join. Returns elapsed ms. Marks the bot brain as OFF (it comes up off every start).',
    inputSchema: {
      type: 'object',
      properties: { instance: { type: 'string', enum: ['main', 'bot'] }, timeoutMs: { type: 'number' } },
      required: ['instance'],
    },
    handler: async (a) => {
      const inst = CFG.instances[a.instance];
      if (!inst) throw toolError('unknown_instance', `no instance named ${a.instance}`);
      const t0 = Date.now();
      const before = (() => { try { return fs.statSync(inst.log).size; } catch { return 0; } })();
      const child = spawn(inst.launcher, inst.args, { cwd: path.dirname(inst.launcher), detached: true, stdio: 'ignore' });
      child.unref();
      const deadline = Date.now() + (a.timeoutMs ?? 240000);
      let joined = false;
      let text = '';
      while (Date.now() < deadline) {
        await new Promise((r) => setTimeout(r, 2000));
        try {
          const size = fs.statSync(inst.log).size;
          if (size > before) {
            const fd = fs.openSync(inst.log, 'r');
            const buf = Buffer.alloc(Math.min(size - before, 2 << 20));
            fs.readSync(fd, buf, 0, buf.length, size - buf.length);
            fs.closeSync(fd);
            text = buf.toString('utf8');
            if (text.includes(inst.joined)) { joined = true; break; }
          }
        } catch { /* log not created yet */ }
      }
      return {
        ok: joined, instance: a.instance, joined,
        elapsedMs: Date.now() - t0,
        hint: joined ? (a.instance === 'bot' ? 'bot brain is OFF — toggle it with command{target:"jamal",cmd:".toggle bot-brain"}' : undefined)
          : 'did not see a join before timeout; check client_status',
      };
    },
  },
  {
    name: 'client_stop',
    description: 'Stop a local client by the PID listening on its panel port (never by name pattern).',
    inputSchema: { type: 'object', properties: { instance: { type: 'string', enum: ['main', 'bot'] } }, required: ['instance'] },
    handler: async (a) => {
      if (!DESTRUCTIVE) throw toolError('destructive_disabled', 'set SWARM_MCP_ALLOW_DESTRUCTIVE=1 to stop clients');
      const inst = CFG.instances[a.instance];
      const pid = await listeningPid(inst.port);
      if (!pid) return { ok: true, instance: a.instance, stopped: false, reason: 'nothing listening' };
      const r = await run('taskkill', ['/PID', String(pid), '/F']);
      return { ok: r.ok, instance: a.instance, pid, stopped: r.ok, out: (r.stdout + r.stderr).trim() };
    },
  },

  // ---- dev loop
  {
    name: 'build',
    description: 'Run the gradle build with the project JDK. Returns exit code, duration, jar path and the tail of the output.',
    inputSchema: { type: 'object', properties: {} },
    handler: async () => {
      const t0 = Date.now();
      const javaHome = process.env.JAVA_HOME || 'C:\\Program Files\\Java\\jdk-25.0.2';
      const r = await new Promise((resolve) => {
        exec('gradlew.bat build', { cwd: CFG.project, env: { ...process.env, JAVA_HOME: javaHome }, maxBuffer: 16 << 20 },
          (err, stdout, stderr) => resolve({ ok: !err, code: err?.code ?? 0, out: (stdout || '') + (stderr || '') }));
      });
      let jar = null;
      try {
        const files = fs.readdirSync(path.join(CFG.project, 'build', 'libs')).filter((f) => f.endsWith('.jar'));
        jar = files.sort().pop() ?? null;
      } catch { /* no build dir */ }
      return {
        ok: r.ok, exitCode: r.code, durationMs: Date.now() - t0, jar,
        tail: stripAnsi(r.out).trim().split(/\r?\n/).slice(-12).join('\n'),
      };
    },
  },
  {
    name: 'deploy',
    description: 'Copy the newest built jar into the mods folder of the named instances (default both). Requires a successful build first.',
    inputSchema: { type: 'object', properties: { instances: { type: 'array', items: { type: 'string' } } } },
    handler: async (a) => {
      const libs = path.join(CFG.project, 'build', 'libs');
      const jar = fs.readdirSync(libs).filter((f) => f.endsWith('.jar')).sort().pop();
      if (!jar) throw toolError('no_jar', 'no jar in build/libs — run build first');
      const targets = a.instances ?? Object.keys(CFG.instances);
      const out = [];
      for (const name of targets) {
        const inst = CFG.instances[name];
        const mods = path.join(path.dirname(inst.log), '..', 'mods');
        fs.mkdirSync(mods, { recursive: true });
        for (const old of fs.readdirSync(mods).filter((f) => f.startsWith('enhanced-swarm'))) fs.unlinkSync(path.join(mods, old));
        fs.copyFileSync(path.join(libs, jar), path.join(mods, jar));
        out.push({ instance: name, mods, jar });
      }
      return { ok: true, copied: out };
    },
  },
];

const TOOL_MAP = new Map(TOOLS.map((t) => [t.name, t]));

// ------------------------------------------------------------- json-rpc ----
async function callTool(name, args) {
  const t = TOOL_MAP.get(name);
  if (!t) return { isError: true, text: `unknown tool: ${name}` };
  const t0 = Date.now();
  try {
    const result = await t.handler(args ?? {});
    logTool(name, args, true, Date.now() - t0);
    return { isError: false, text: JSON.stringify(result, null, 2) };
  } catch (e) {
    logTool(name, args, false, Date.now() - t0, e.message);
    return { isError: true, text: JSON.stringify({ ok: false, error: e.code ?? 'tool_failed', detail: e.message }) };
  }
}

async function handleRpc(msg) {
  const { id, method, params } = msg;
  switch (method) {
    case 'initialize': {
      const want = params?.protocolVersion;
      const version = PROTOCOL_VERSIONS.includes(want) ? want : PROTOCOL_VERSIONS[0];
      return { jsonrpc: '2.0', id, result: { protocolVersion: version, capabilities: { tools: { listChanged: false } }, serverInfo: SERVER_INFO } };
    }
    case 'notifications/initialized':
    case 'notifications/cancelled':
      return null; // notification: no response
    case 'ping':
      return { jsonrpc: '2.0', id, result: {} };
    case 'tools/list':
      return { jsonrpc: '2.0', id, result: { tools: TOOLS.map(({ name, description, inputSchema }) => ({ name, description, inputSchema })) } };
    case 'tools/call': {
      const { name, arguments: args } = params ?? {};
      const r = await callTool(name, args);
      return { jsonrpc: '2.0', id, result: { content: [{ type: 'text', text: r.text }], isError: r.isError } };
    }
    default:
      return { jsonrpc: '2.0', id, error: { code: -32601, message: `method not found: ${method}` } };
  }
}

// ------------------------------------------------------------------ http ----
function originAllowed(req) {
  const origin = req.headers.origin;
  if (!origin) return true;
  const port = CFG.port;
  return origin === `http://127.0.0.1:${port}` || origin === `http://localhost:${port}` || origin === 'null';
}

function authorized(req, url) {
  const bearer = (req.headers.authorization || '').replace(/^Bearer\s+/i, '');
  const header = req.headers['x-swarm-token'];
  const query = url.searchParams.get('token');
  if (!TOKEN) TOKEN = resolveToken();
  return [bearer, header, query].some((v) => v && v === TOKEN);
}

function sendJson(res, status, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(status, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body), 'Cache-Control': 'no-store' });
  res.end(body);
}

const sseSessions = new Map(); // id -> res

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://127.0.0.1:${CFG.port}`);
  if (!originAllowed(req)) return sendJson(res, 403, { ok: false, error: 'origin_refused' });

  if (url.pathname === '/health') {
    return sendJson(res, 200, { ok: true, server: SERVER_INFO, tools: TOOLS.length, destructive: DESTRUCTIVE, tokenKnown: Boolean(resolveToken()) });
  }
  if (!authorized(req, url)) return sendJson(res, 401, { ok: false, error: 'unauthorized' });

  // Streamable HTTP: single POST endpoint
  if (url.pathname === '/mcp' || url.pathname === '/') {
    if (req.method === 'GET') {
      res.writeHead(405, { Allow: 'POST' });
      return res.end();
    }
    if (req.method !== 'POST') return sendJson(res, 405, { ok: false, error: 'method_not_allowed' });
    let raw = '';
    for await (const chunk of req) raw += chunk;
    let payload;
    try { payload = JSON.parse(raw || '{}'); } catch { return sendJson(res, 400, { jsonrpc: '2.0', id: null, error: { code: -32700, message: 'parse error' } }); }
    if (Array.isArray(payload)) {
      const results = (await Promise.all(payload.map(handleRpc))).filter(Boolean);
      return results.length ? sendJson(res, 200, results) : (res.writeHead(202), res.end());
    }
    const out = await handleRpc(payload);
    if (!out) { res.writeHead(202); return res.end(); }
    return sendJson(res, 200, out);
  }

  // Legacy HTTP+SSE transport (deprecated but promised by the spec)
  if (url.pathname === '/sse' && req.method === 'GET') {
    const id = crypto.randomUUID();
    res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-store', Connection: 'keep-alive' });
    res.write(`event: endpoint\ndata: /messages?sessionId=${id}\n\n`);
    sseSessions.set(id, res);
    const ping = setInterval(() => { try { res.write(': ping\n\n'); } catch { /* closed */ } }, 15000);
    req.on('close', () => { clearInterval(ping); sseSessions.delete(id); });
    return;
  }
  if (url.pathname === '/messages' && req.method === 'POST') {
    const id = url.searchParams.get('sessionId');
    const stream = sseSessions.get(id);
    if (!stream) return sendJson(res, 404, { ok: false, error: 'no_such_session' });
    let raw = '';
    for await (const chunk of req) raw += chunk;
    let payload;
    try { payload = JSON.parse(raw); } catch { return sendJson(res, 400, { ok: false, error: 'parse_error' }); }
    res.writeHead(202).end();
    const out = Array.isArray(payload) ? (await Promise.all(payload.map(handleRpc))).filter(Boolean) : await handleRpc(payload);
    for (const one of Array.isArray(out) ? out : out ? [out] : []) {
      stream.write(`event: message\ndata: ${JSON.stringify(one)}\n\n`);
    }
    return;
  }
  return sendJson(res, 404, { ok: false, error: 'not_found' });
});

if (CFG.bind !== '127.0.0.1' && CFG.bind !== 'localhost') {
  console.error(`refusing to bind ${CFG.bind}: loopback only`);
  process.exit(1);
}

server.listen(CFG.port, '127.0.0.1', () => {
  const t = resolveToken();
  console.log(`swarm-mcp: http://127.0.0.1:${CFG.port}/mcp  (legacy /sse)`);
  console.log(`  tools: ${TOOLS.length}   destructive: ${DESTRUCTIVE}   token: ${t ? '••••' + t.slice(-4) : 'MISSING'}`);
  if (!t) console.log('  warning: no panel token found — set SWARM_MCP_TOKEN or start the host');
  if (!rconPassword()) console.log('  warning: no RCON password — set SWARM_MC_RCON_PASSWORD for tick/server tools');
});

