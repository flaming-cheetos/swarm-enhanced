import path from 'node:path';

import { startStub } from './stub.mjs';
import { startPanelStub } from './panel-stub.mjs';
import { runHarness } from './runner.mjs';

const args = parseArgs(process.argv.slice(2));
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
    outDir: args.out,
    scenarioIds: ['H0', 'H0b', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6', 'H8', 'H9', 'H10', 'H11', 'H12']
  });

  console.log(`stub bridge: ${bridge.baseUrl}`);
  console.log(`stub panel: ${panel.baseUrl}`);
  console.log(`evidence: ${path.resolve(result.outDir)}`);
  console.log(`status: ${result.status}`);

  for (const scenario of result.results ?? []) {
    const label = scenario.simulated ? ' (simulated)' : '';
    console.log(`${scenario.id}: ${scenario.status}${label}${scenario.error ? ` — ${scenario.error}` : ''}`);
  }

  process.exitCode = result.ok ? 0 : 1;
} finally {
  await panel.close();
  await bridge.close();
}

function parseArgs(argv) {
  const outIndex = argv.indexOf('--out');
  return {
    out: outIndex >= 0 ? argv[outIndex + 1] : undefined
  };
}
