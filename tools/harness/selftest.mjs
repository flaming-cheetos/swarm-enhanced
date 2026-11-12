import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { BridgeAdapter } from './bridge.mjs';
import { PanelAdapter } from './panel.mjs';
import { startPanelStub } from './panel-stub.mjs';
import { startStub } from './stub.mjs';
import {
  loadScenarios,
  runHarness,
  runScenario
} from './runner.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(HERE, '../..');
const RUN_ROOT = path.join(
  REPO_ROOT,
  'test-runs',
  `selftest-${new Date().toISOString().replace(/[:.]/g, '-')}`
);
const checks = [];

await fs.mkdir(RUN_ROOT, { recursive: true });

await checkNormal('normal');
await checkCorruptField();
await checkScenarioDrift();
await checkActionsDisabled();
await checkConcurrency();
await checkSimulatedNegatives();
await checkNormal('restored');

const ok = checks.every((check) => check.pass);
const report = {
  ok,
  status: ok ? 'PASS' : 'FAIL',
  startedAt: checks[0]?.startedAt ?? new Date().toISOString(),
  finishedAt: new Date().toISOString(),
  outDir: RUN_ROOT,
  checks
};
await fs.writeFile(
  path.join(RUN_ROOT, 'selftest.json'),
  JSON.stringify(report, null, 2),
  'utf8'
);
await fs.writeFile(
  path.join(RUN_ROOT, 'summary.md'),
  renderSummary(report),
  'utf8'
);

console.log(`selftest: ${report.status}`);
for (const check of checks) {
  console.log(`${check.pass ? 'PASS' : 'FAIL'} ${check.name}: ${check.detail}`);
}
console.log(`evidence: ${RUN_ROOT}`);
if (!ok) process.exitCode = 1;

async function checkNormal(label) {
  const startedAt = new Date().toISOString();
  const outDir = path.join(RUN_ROOT, label);
  const bridge = await startStub({ allowActions: true });
  const panel = await startPanelStub();
  try {
    const result = await runHarness({
      mode: 'stub',
      baseUrl: bridge.baseUrl,
      token: bridge.token,
      panelBaseUrl: panel.baseUrl,
      panelToken: panel.token,
      bot: 'StubBot',
      outDir,
      scenarioIds: ['H0', 'H0b', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6', 'H8', 'H9', 'H10', 'H11', 'H12']
    });
    const expected = ['h0', 'h0b', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'h8', 'h9', 'h10', 'h11', 'h12'];
    const files = await Promise.all(
      expected.flatMap((id) => [
        exists(path.join(outDir, `${id}.json`)),
        exists(path.join(outDir, `${id}.result.json`)),
        exists(path.join(outDir, `${id}.scenario.json`))
      ])
    );
    const screenshots = await Promise.all([
      exists(path.join(outDir, 'h0-screenshot.png')),
      exists(path.join(outDir, 'h0b-screenshot.png')),
      exists(path.join(outDir, 'h1-screenshot.png')),
      exists(path.join(outDir, 'h8-screenshot.png')),
      exists(path.join(outDir, 'screens', 'h0b-screenshot.png'))
    ]);
    check(
      `${label} harness suite`,
      result.ok &&
        result.results?.length === 13 &&
        result.results.every((scenario) => scenario.status === 'PASS') &&
        result.results.filter((scenario) => scenario.simulated).length === 8 &&
        files.every(Boolean) &&
        screenshots.every(Boolean),
      result.ok
        ? `H0/H0b/H1-H6/H8-H12 PASS; simulated=8; evidence files=${files.every(Boolean)} screenshots=${screenshots.every(Boolean)}`
        : `failed: ${result.results?.map((r) => `${r.id}: ${r.error}`).join('; ')}`,
      startedAt
    );
  } finally {
    await panel.close();
    await bridge.close();
  }
}

async function checkCorruptField() {
  const startedAt = new Date().toISOString();
  const outDir = path.join(RUN_ROOT, 'corrupt-state-player');
  const bridge = await startStub({ allowActions: true, corrupt: 'state.player' });
  try {
    const result = await runHarness({
      mode: 'stub',
      baseUrl: bridge.baseUrl,
      token: bridge.token,
      outDir,
      scenarioIds: ['H0']
    });
    const failed = result.results?.[0];
    check(
      'corrupt state.player is caught',
      result.ok === false &&
        failed?.status === 'FAIL' &&
        /state\.player/.test(failed?.error ?? ''),
      failed?.error ?? 'scenario unexpectedly passed',
      startedAt
    );
  } finally {
    await bridge.close();
  }
}

async function checkScenarioDrift() {
  const startedAt = new Date().toISOString();
  const outDir = path.join(RUN_ROOT, 'scenario-drift');
  await fs.mkdir(outDir, { recursive: true });
  const bridge = await startStub({ allowActions: true });
  try {
    const [base] = await loadScenarios(undefined, ['H0']);
    const drifted = structuredClone(base);
    drifted.assert.push({
      path: 'state.player.name',
      equals: 'not-the-stub-player'
    });
    const result = await runScenario(
      drifted,
      {
        bridge: new BridgeAdapter({ baseUrl: bridge.baseUrl, token: bridge.token }),
        panel: null
      },
      outDir
    );
    await fs.writeFile(
      path.join(outDir, 'drift-result.json'),
      JSON.stringify(result, null, 2),
      'utf8'
    );
    check(
      'wrong scenario assertion is caught',
      result.status === 'FAIL' && /state\.player\.name/.test(result.error ?? ''),
      result.error ?? 'scenario unexpectedly passed',
      startedAt
    );
  } finally {
    await bridge.close();
  }
}

async function checkActionsDisabled() {
  const startedAt = new Date().toISOString();
  const bridge = await startStub({ allowActions: false });
  try {
    const adapter = new BridgeAdapter({
      baseUrl: bridge.baseUrl,
      token: bridge.token
    });
    const response = await adapter.menuClick(0);
    check(
      'guard-off returns actions-disabled',
      response.status === 403 && response.body?.code === 'actions-disabled',
      `status=${response.status} code=${response.body?.code ?? '<none>'}`,
      startedAt
    );
  } finally {
    await bridge.close();
  }
}

async function checkConcurrency() {
  const startedAt = new Date().toISOString();
  const bridge = await startStub({ allowActions: true });
  try {
    const adapter = new BridgeAdapter({
      baseUrl: bridge.baseUrl,
      token: bridge.token
    });
    const [a, b] = await Promise.all([
      adapter.menuClick(0),
      adapter.menuClick(0)
    ]);
    const statuses = [a.status, b.status].sort((x, y) => x - y);
    const rejected = [a, b].find((response) => response.status === 409);
    check(
      'one action in flight',
      statuses[0] === 200 &&
        statuses[1] === 409 &&
        rejected?.body?.code === 'action-in-flight',
      `statuses=${statuses.join(',')} rejected=${rejected?.body?.code ?? '<none>'}`,
      startedAt
    );
  } finally {
    await bridge.close();
  }
}

async function checkSimulatedNegatives() {
  const cases = [
    { id: 'H4', path: 'task.status', equals: 'ACTIVE', label: 'reconnect requeue' },
    { id: 'H5', path: 'afterRestart.tasks.0.status', equals: 'ACTIVE', label: 'restart no-stranded-active' },
    { id: 'H6', path: 'task.status', equals: 'COMPLETE', label: 'full-inventory failure' },
    { id: 'H8', path: 'panel.deposit.status', equals: 'COMPLETE', label: 'menu-mismatch refusal' },
    { id: 'H9', path: 'staleTruth.fresh', equals: true, label: 'stale-state detection' },
    { id: 'H10', path: 'task.status', equals: 'COMPLETE', label: 'death explicit failure' },
    { id: 'H11', path: 'claimTruth.claimCount', equals: 2, label: 'single-claim count' },
    { id: 'H12', path: 'state.tasks.0.status', equals: 'COMPLETE', label: 'known-limitation failure' }
  ];

  for (const entry of cases) {
    const startedAt = new Date().toISOString();
    const [scenario] = await loadScenarios(undefined, [entry.id]);
    const normalOut = path.join(RUN_ROOT, `negative-${entry.id}-normal`);
    const wrongOut = path.join(RUN_ROOT, `negative-${entry.id}-wrong`);
    const normal = await runSimulatedScenario(scenario, normalOut);
    const wrong = structuredClone(scenario);
    wrong.assert.push({ path: entry.path, equals: entry.equals });
    const failed = await runSimulatedScenario(wrong, wrongOut);
    await fs.writeFile(
      path.join(RUN_ROOT, `negative-${entry.id}.json`),
      JSON.stringify({ scenario: entry, normal, failed }, null, 2),
      'utf8'
    );
    const caught = (failed.error ?? '').includes(entry.path);
    check(
      `negative ${entry.id} ${entry.label}`,
      normal.status === 'PASS' && failed.status === 'FAIL' && caught,
      normal.status !== 'PASS'
        ? `normal run unexpectedly failed: ${normal.error}`
        : failed.status !== 'FAIL'
          ? 'corrupted assertion unexpectedly passed'
          : caught
            ? `caught ${entry.path}: ${failed.error}`
            : `failed without naming ${entry.path}: ${failed.error}`,
      startedAt
    );
  }
}

async function runSimulatedScenario(scenario, outDir) {
  const bridge = await startStub({ allowActions: true });
  const panelStub = await startPanelStub();
  const panel = new PanelAdapter({
    baseUrl: panelStub.baseUrl,
    token: panelStub.token
  });
  const bridgeAdapter = new BridgeAdapter({
    baseUrl: bridge.baseUrl,
    token: bridge.token
  });
  try {
    await panel.connect();
    return await runScenario(
      scenario,
      { bridge: bridgeAdapter, panel, mode: 'stub' },
      outDir,
      { BOT: 'StubBot' }
    );
  } finally {
    await panel.close();
    await panelStub.close();
    await bridge.close();
  }
}

function check(name, pass, detail, startedAt = new Date().toISOString()) {
  checks.push({
    name,
    pass,
    detail,
    startedAt,
    finishedAt: new Date().toISOString()
  });
}

async function exists(file) {
  try {
    await fs.access(file);
    return true;
  } catch {
    return false;
  }
}

function renderSummary(report) {
  const lines = [
    '# Harness self-test',
    '',
    `- Status: ${report.status}`,
    `- Started: ${report.startedAt}`,
    `- Finished: ${report.finishedAt}`,
    `- Evidence: ${report.outDir}`,
    '',
    '## Checks',
    ''
  ];
  for (const check of report.checks) {
    lines.push(`- ${check.pass ? 'PASS' : 'FAIL'} ${check.name}: ${check.detail}`);
  }
  return `${lines.join('\n')}\n`;
}
