// Self-test: starts the MCP server on a scratch port and exercises the transport
// contract without touching Minecraft. Run with `npm run selftest`.
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseTickQuery } from './lib/rcon.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const PORT = 8799;
const TOKEN = 'selftest-token';

const child = spawn(process.execPath, [path.join(HERE, 'server.mjs')], {
  env: { ...process.env, SWARM_MCP_PORT: String(PORT), SWARM_MCP_TOKEN: TOKEN },
  stdio: ['ignore', 'pipe', 'pipe'],
});
let out = '';
child.stdout.on('data', (d) => { out += d; });
child.stderr.on('data', (d) => { out += d; });

const url = `http://127.0.0.1:${PORT}`;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let pass = 0, fail = 0;
function check(name, cond, detail) {
  if (cond) { console.log(`PASS ${name}`); pass++; }
  else { console.log(`FAIL ${name}${detail ? ' — ' + detail : ''}`); fail++; }
}

async function rpc(body) {
  const res = await fetch(`${url}/mcp`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Swarm-Token': TOKEN },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  return { status: res.status, json: text ? JSON.parse(text) : null };
}

try {
  for (let i = 0; i < 40; i++) {
    await sleep(150);
    try { if ((await fetch(`${url}/health`)).ok) break; } catch { /* not up yet */ }
  }

  const realTick = ['The game is running normallyTarget tick rate: 60.0 per second.', 'Average time per tick: 3.2ms (Target: 16.7ms)Percentiles: P50: 3.1ms P95: 3.5ms P99: 4.1ms. Sample: 100'].join(String.fromCharCode(10));
  const q = parseTickQuery(realTick);
  check('parseTickQuery reads real RCON output', q.mspt === 3.2 && q.targetRate === 60 && q.tps === 60 && q.healthy === true, JSON.stringify(q));

  const health = await (await fetch(`${url}/health`)).json();
  check('health reports tools', health.ok && health.tools > 10, JSON.stringify(health));

  const noAuth = await fetch(`${url}/mcp`, { method: 'POST', body: '{}' });
  check('no token -> 401', noAuth.status === 401, String(noAuth.status));

  const badOrigin = await fetch(`${url}/mcp`, {
    method: 'POST',
    headers: { 'X-Swarm-Token': TOKEN, Origin: 'http://evil.example' },
    body: '{}',
  });
  check('foreign origin -> 403', badOrigin.status === 403, String(badOrigin.status));

  const init = await rpc({ jsonrpc: '2.0', id: 1, method: 'initialize', params: { protocolVersion: '2026-07-28', capabilities: {}, clientInfo: { name: 'selftest', version: '0' } } });
  check('initialize echoes version', init.json?.result?.protocolVersion === '2026-07-28', JSON.stringify(init.json));
  check('initialize advertises tools', init.json?.result?.capabilities?.tools !== undefined);

  const list = await rpc({ jsonrpc: '2.0', id: 2, method: 'tools/list' });
  const names = (list.json?.result?.tools ?? []).map((t) => t.name);
  check('tools/list returns the v0.1 surface', names.length >= 15, `${names.length} tools`);
  for (const must of ['swarm_state', 'task_add', 'tick_rate', 'tick_query', 'client_status', 'build', 'deploy', 'command']) {
    check(`  has ${must}`, names.includes(must));
  }

  const unknown = await rpc({ jsonrpc: '2.0', id: 3, method: 'tools/call', params: { name: 'nope', arguments: {} } });
  check('unknown tool is an error result', unknown.json?.result?.isError === true);

  const guarded = await rpc({ jsonrpc: '2.0', id: 4, method: 'tools/call', params: { name: 'client_stop', arguments: { instance: 'bot' } } });
  check('client_stop gated without destructive flag', /destructive_disabled/.test(guarded.json?.result?.content?.[0]?.text ?? ''));

  const rpcCommand = await rpc({ jsonrpc: '2.0', id: 5, method: 'tools/call', params: { name: 'mc_command', arguments: { cmd: '/stop' } } });
  check('mc_command refuses non-safe commands', /command_not_allowed/.test(rpcCommand.json?.result?.content?.[0]?.text ?? ''));

  const ping = await rpc({ jsonrpc: '2.0', id: 6, method: 'ping' });
  check('ping responds', ping.json?.result !== undefined);

  check('GET /mcp is 405', (await fetch(`${url}/mcp`, { headers: { 'X-Swarm-Token': TOKEN } })).status === 405);

  // Legacy HTTP+SSE transport, still promised by the spec.
  const sse = await fetch(`${url}/sse`, { headers: { 'X-Swarm-Token': TOKEN } });
  check('GET /sse opens', sse.status === 200, String(sse.status));
  const reader = sse.body.getReader();
  const decoder = new TextDecoder();
  let sseBuffer = '';
  while (!sseBuffer.includes('\n\n')) {
    const { value, done } = await reader.read();
    if (done) break;
    sseBuffer += decoder.decode(value, { stream: true });
  }
  const endpoint = sseBuffer.match(/data: (\/messages\?sessionId=[^\n]+)/)?.[1];
  check('GET /sse sends the messages endpoint', Boolean(endpoint), JSON.stringify(sseBuffer));
  if (endpoint) {
    const legacyInit = await fetch(`${url}${endpoint}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Swarm-Token': TOKEN },
      body: JSON.stringify({
        jsonrpc: '2.0', id: 7, method: 'initialize',
        params: { protocolVersion: '2026-07-28', capabilities: {}, clientInfo: { name: 'selftest-legacy', version: '0' } },
      }),
    });
    check('POST /messages accepts a legacy call', legacyInit.status === 202, String(legacyInit.status));
    let legacyEvent = '';
    while (!legacyEvent.includes('\n\n')) {
      const { value, done } = await reader.read();
      if (done) break;
      legacyEvent += decoder.decode(value, { stream: true });
    }
    check('legacy initialize reply arrives',
      legacyEvent.includes('event: message') && legacyEvent.includes('"protocolVersion":"2026-07-28"'),
      JSON.stringify(legacyEvent));
    await reader.cancel();
  }
} catch (e) {
  console.log('FAIL harness error — ' + e.message);
  fail++;
} finally {
  child.kill();
  await sleep(200);
  console.log(`\n${pass} passed, ${fail} failed`);
  if (fail) { console.log('--- server output ---\n' + out); process.exit(1); }
}
