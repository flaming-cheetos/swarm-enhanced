// Minimal RCON client for the Minecraft test server. Zero dependencies.
// Protocol: int32 length | int32 requestId | int32 type | body | 0x00 0x00
import net from 'node:net';

const TYPE_AUTH = 3;
const TYPE_AUTH_RESPONSE = 2;
const TYPE_EXEC = 2;

function encodePacket(id, type, body) {
  const bodyBuf = Buffer.from(body, 'utf8');
  const buf = Buffer.alloc(4 + 4 + 4 + bodyBuf.length + 2);
  buf.writeInt32LE(buf.length - 4, 0);
  buf.writeInt32LE(id, 4);
  buf.writeInt32LE(type, 8);
  bodyBuf.copy(buf, 12);
  buf.writeUInt8(0, 12 + bodyBuf.length);
  buf.writeUInt8(0, 13 + bodyBuf.length);
  return buf;
}

export class Rcon {
  constructor({ host, port = 25575, password, timeoutMs = 8000 }) {
    this.host = host;
    this.port = port;
    this.password = password;
    this.timeoutMs = timeoutMs;
    this.socket = null;
    this.nextId = 1;
    this.pending = new Map(); // id -> {resolve, reject, chunks:[], timer}
    this.buffer = Buffer.alloc(0);
    this.authed = false;
  }

  connect() {
    return new Promise((resolve, reject) => {
      if (!this.password) return reject(new Error('rcon_password_missing: set SWARM_MC_RCON_PASSWORD'));
      const sock = net.connect({ host: this.host, port: this.port });
      this.socket = sock;
      let settled = false;
      const fail = (err) => {
        if (settled) return;
        settled = true;
        try { sock.destroy(); } catch { /* ignore */ }
        reject(err);
      };
      sock.setTimeout(this.timeoutMs, () => fail(new Error('rcon_connect_timeout')));
      sock.on('error', (err) => fail(new Error('rcon_socket: ' + err.message)));
      sock.on('data', (d) => this._onData(d));
      sock.on('close', () => {
        for (const [, p] of this.pending) { clearTimeout(p.timer); p.reject(new Error('rcon_closed')); }
        this.pending.clear();
      });
      sock.once('connect', async () => {
        try {
          sock.setTimeout(0);
          const id = this.nextId++;
          const res = await this._send(id, TYPE_AUTH, this.password);
          if (res.id === -1) return fail(new Error('rcon_auth_failed'));
          this.authed = true;
          settled = true;
          resolve(this);
        } catch (e) { fail(e); }
      });
    });
  }

  _onData(chunk) {
    this.buffer = Buffer.concat([this.buffer, chunk]);
    for (;;) {
      if (this.buffer.length < 4) return;
      const len = this.buffer.readInt32LE(0);
      if (len < 10 || this.buffer.length < 4 + len) return;
      const id = this.buffer.readInt32LE(4);
      const type = this.buffer.readInt32LE(8);
      const body = this.buffer.subarray(12, 4 + len - 2).toString('utf8');
      this.buffer = this.buffer.subarray(4 + len);
      const p = this.pending.get(id);
      if (!p) continue;
      if (type === TYPE_AUTH_RESPONSE) {
        clearTimeout(p.timer);
        this.pending.delete(id);
        p.resolve({ id, body });
        continue;
      }
      // RESPONSE_VALUE: one packet per fragment. Collect until the caller's
      // sentinel packet (same id pattern) arrives or the idle timer fires.
      p.chunks.push(body);
      clearTimeout(p.timer);
      p.timer = setTimeout(() => {
        this.pending.delete(id);
        p.resolve({ id, body: p.chunks.join('') });
      }, p.idleMs);
    }
  }

  _send(id, type, body, idleMs = 250) {
    return new Promise((resolve, reject) => {
      if (!this.socket) return reject(new Error('rcon_not_connected'));
      const entry = { resolve, reject, chunks: [], idleMs, timer: null };
      entry.timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error('rcon_timeout'));
      }, Math.max(this.timeoutMs, idleMs + 100));
      this.pending.set(id, entry);
      this.socket.write(encodePacket(id, type, body));
    });
  }

  async exec(command) {
    if (!this.authed) throw new Error('rcon_not_authed');
    const id = this.nextId++;
    const res = await this._send(id, TYPE_EXEC, command);
    return { command, response: res.body.replace(/\u00a7./g, '').trim() };
  }

  close() {
    if (this.socket) { try { this.socket.end(); this.socket.destroy(); } catch { /* ignore */ } }
    this.socket = null;
    this.authed = false;
  }
}

// Parses real `/tick query` output. Verified against the Pi test server
// (2026-11-13), which prints:
//   "The game is running normallyTarget tick rate: 60.0 per second.
//    Average time per tick: 3.2ms (Target: 16.7ms)Percentiles: P50: 3.1ms ..."
// Recent versions print `(Target: Xms)`, not `(Y TPS)`, and RCON strips the
// newlines so the sections run together - nothing here may assume whitespace.
export function parseTickQuery(text) {
  const flat = String(text ?? '').replace(/§./g, '');
  const out = { raw: flat.trim(), targetRate: null, mspt: null, tps: null, p99: null, healthy: null, behind: false };
  const target = /Target tick rate:\s*([\d.]+)/i.exec(flat);
  if (target) out.targetRate = Number(target[1]);
  const avg = /Average time per tick:\s*([\d.]+)\s*ms/i.exec(flat);
  if (avg) out.mspt = Number(avg[1]);
  const p99 = /P99:\s*([\d.]+)\s*ms/i.exec(flat);
  if (p99) out.p99 = Number(p99[1]);
  if (out.mspt !== null) {
    const derived = 1000 / out.mspt;
    out.tps = out.targetRate ? Math.min(out.targetRate, derived) : derived;
    out.healthy = out.mspt <= 45;
  }
  out.behind = /running behind|Can't keep up/i.test(flat);
  return out;
}
