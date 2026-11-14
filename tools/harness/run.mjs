import fs from 'node:fs/promises';
import path from 'node:path';

import { runHarness } from './runner.mjs';

const args = parseArgs(process.argv.slice(2));

if (args.help) {
  console.log(usage());
  process.exit(0);
}

const mode = args.mode ?? 'live';
const baseUrl = args['base-url'] ?? 'http://127.0.0.1:8899';
const token = args.token ?? process.env.HARNESS_TOKEN ?? (args['token-file']
  ? (await fs.readFile(args['token-file'], 'utf8')).trim()
  : '');
const panelBaseUrl = args['panel-url'] ?? 'http://127.0.0.1:8888';
const panelToken = args['panel-token'] ?? process.env.PANEL_TOKEN ?? (args['panel-token-file']
  ? (await fs.readFile(args['panel-token-file'], 'utf8')).trim()
  : '');

try {
  const result = await runHarness({
    mode,
    baseUrl,
    token,
    panelBaseUrl,
    panelToken,
    bot: args.bot ?? '',
    outDir: args.out,
    scenarioIds: args.scenario ? args.scenario.split(',') : null,
    hostLog: args['host-log'],
    botLog: args['bot-log'],
    hostQueue: args['host-queue'],
    botQueue: args['bot-queue']
  });

  if (result.skipped) {
    console.log(`SKIP: ${result.reason}`);
    console.log(`evidence: ${path.resolve(result.outDir)}`);
    process.exit(0);
  }

  console.log(`mode: ${result.mode}`);
  console.log(`bridge: ${result.baseUrl}`);
  if (result.panelBaseUrl) console.log(`panel: ${result.panelBaseUrl}`);
  console.log(`evidence: ${path.resolve(result.outDir)}`);
  console.log(`status: ${result.status}`);
  for (const scenario of result.results ?? []) {
    console.log(`${scenario.id}: ${scenario.status}${scenario.error ? ` — ${scenario.error}` : ''}`);
  }
  process.exitCode = result.ok ? 0 : 1;
} catch (error) {
  console.error(`FAIL: ${error instanceof Error ? error.message : String(error)}`);
  process.exitCode = 1;
}

function parseArgs(argv) {
  const parsed = {};
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (!arg.startsWith('--')) continue;
    const key = arg.slice(2);
    if (key === 'help') {
      parsed.help = true;
      continue;
    }
    parsed[key] = argv[i + 1];
    i += 1;
  }
  return parsed;
}

function usage() {
  return `Usage: node run.mjs [options]

Options:
  --mode <live|stub>          Default: live
  --base-url <url>            HarnessBridge URL. Default: http://127.0.0.1:8899
  --token <token>             Harness token (or HARNESS_TOKEN)
  --token-file <path>         Read harness token from a file
  --panel-url <url>           Panel URL. Default: http://127.0.0.1:8888
  --panel-token <token>       Panel token (or PANEL_TOKEN)
  --panel-token-file <path>   Read panel token from a file
  --bot <name>                Worker name for task scenarios (or BOT)
  --scenario <ids>            Comma-separated scenario IDs
  --host-log <path>           Copy host latest.log into evidence
  --bot-log <path>            Copy worker latest.log into evidence
  --host-queue <path>         Copy host task-queue.json into evidence
  --bot-queue <path>          Copy worker task-queue.json into evidence
  --out <directory>           Evidence directory
  --help                      Show this help
`;
}
