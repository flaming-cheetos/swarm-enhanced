import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { BridgeAdapter } from './bridge.mjs';
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
      scenarioIds: ['H0', 'H0b', 'H1', 'H2', 'H3']
    });
    const expected = ['h0', 'h0b', 'h1', 'h2', 'h3'];
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
      exists(path.join(outDir, 'screens', 'h0b-screenshot.png'))
    ]);
    check(
      `${label} harness suite`,
      result.ok &&
        result.results?.length === 5 &&
        result.results.every((scenario) => scenario.status === 'PASS') &&
        files.every(Boolean) &&
        screenshots.every(Boolean),
      result.ok
        ? `H0/H0b/H1/H2/H3 PASS; evidence files=${files.every(Boolean)} screenshots=${screenshots.every(Boolean)}`
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
