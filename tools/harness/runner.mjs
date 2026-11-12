import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

import {
  BridgeAdapter,
  BridgeConnectionError,
  BridgeContractError
} from './bridge.mjs';
import {
  PanelAdapter,
  PanelContractError
} from './panel.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
export const REPO_ROOT = path.resolve(HERE, '../..');
export const DEFAULT_SCENARIO_DIR = path.join(HERE, 'scenarios');
export const DEFAULT_OUT_ROOT = path.join(REPO_ROOT, 'test-runs');

const PANEL_ACTIONS = new Set([
  'panelState',
  'taskAdd',
  'taskClearDone',
  'taskCancel',
  'taskPause',
  'taskResume',
  'panelAction',
  'waitForTask',
  'waitForBot',
  'logs',
  'http'
]);

export async function loadScenarios(dir = DEFAULT_SCENARIO_DIR, scenarioIds = null) {
  const entries = await fs.readdir(dir, { withFileTypes: true });
  const files = entries
    .filter((entry) => entry.isFile() && entry.name.endsWith('.json'))
    .map((entry) => path.join(dir, entry.name))
    .sort();
  const scenarios = [];

  for (const file of files) {
    const scenario = JSON.parse(await fs.readFile(file, 'utf8'));
    if (!scenario.id || !Array.isArray(scenario.steps)) {
      throw new Error(`invalid scenario ${file}: missing id or steps`);
    }
    scenarios.push(scenario);
  }

  const selected = scenarioIds
    ? scenarios.filter((scenario) => scenarioIds.includes(scenario.id))
    : scenarios;

  if (selected.length === 0) {
    throw new Error(`no scenarios selected from ${dir}`);
  }
  return selected;
}

export async function runHarness({
  mode = 'live',
  baseUrl = 'http://127.0.0.1:8899',
  token = '',
  panelBaseUrl = 'http://127.0.0.1:8888',
  panelToken = '',
  bot = '',
  outDir = null,
  scenarioDir = DEFAULT_SCENARIO_DIR,
  scenarioIds = null,
  hostLog = null,
  botLog = null,
  hostQueue = null,
  botQueue = null
} = {}) {
  const startedAt = new Date().toISOString();
  const runId = `${stamp(startedAt)}-${mode}`;
  const resolvedOut = path.resolve(outDir ?? path.join(DEFAULT_OUT_ROOT, runId));
  await fs.mkdir(path.join(resolvedOut, 'screens'), { recursive: true });

  const scenarios = await loadScenarios(scenarioDir, scenarioIds);
  const requiresPanel = scenarios.some(scenarioRequiresPanel);
  const bridge = new BridgeAdapter({ baseUrl, token });
  let panel = null;
  let probe;

  try {
    try {
      probe = await bridge.health();
    } catch (error) {
      if (error instanceof BridgeConnectionError) {
        const skip = {
          ok: true,
          skipped: true,
          status: 'SKIP',
          reason: error.message,
          mode,
          baseUrl,
          startedAt,
          finishedAt: new Date().toISOString(),
          outDir: resolvedOut
        };
        await writeEvidence(resolvedOut, 'skip.json', skip);
        await writeEvidence(resolvedOut, 'summary.md', renderSummary(skip, []));
        return skip;
      }
      throw error;
    }

    if (requiresPanel) {
      if (!panelToken) {
        throw new PanelContractError(
          'panel token is required for task scenarios; pass --panel-token or --panel-token-file'
        );
      }
      panel = new PanelAdapter({ baseUrl: panelBaseUrl, token: panelToken });
      try {
        await panel.connect();
      } catch (error) {
        throw new Error(
          `panel unavailable at ${panelBaseUrl}: ${error instanceof Error ? error.message : String(error)}`
        );
      }
    }

    const variables = { BOT: bot };
    const results = [];
    for (const scenario of scenarios) {
      const result = await runScenario(
        scenario,
        { bridge, panel },
        resolvedOut,
        variables
      );
      await writeEvidence(resolvedOut, `${scenario.id.toLowerCase()}.json`, result);
      await writeEvidence(
        resolvedOut,
        `${scenario.id.toLowerCase()}.scenario.json`,
        scenario
      );
      await writeEvidence(
        resolvedOut,
        `${scenario.id.toLowerCase()}.result.json`,
        result
      );
      results.push(result);
    }

    const finishedAt = new Date().toISOString();
    const artifacts = await captureArtifacts(resolvedOut, {
      hostLog,
      botLog,
      hostQueue,
      botQueue
    });
    const ok = results.every((result) => result.status === 'PASS');
    const run = {
      ok,
      skipped: false,
      status: ok ? 'PASS' : 'FAIL',
      mode,
      baseUrl,
      panelBaseUrl: requiresPanel ? panelBaseUrl : null,
      runId,
      startedAt,
      finishedAt,
      outDir: resolvedOut,
      probe,
      artifacts,
      results: results.map((result) => ({
        id: result.id,
        title: result.title,
        status: result.status,
        error: result.error ?? null,
        assertions: result.assertions,
        evidence: `${result.id.toLowerCase()}.json`
      })),
      evidence: results.map((result) => ({
        id: result.id,
        result: `${result.id.toLowerCase()}.result.json`,
        scenario: `${result.id.toLowerCase()}.scenario.json`
      }))
    };

    await writeEvidence(resolvedOut, 'run.json', run);
    await writeEvidence(resolvedOut, 'result.json', run);
    await writeEvidence(
      resolvedOut,
      'scenarios.json',
      scenarios
    );
    if (scenarios.length === 1) {
      await writeEvidence(resolvedOut, 'scenario.json', scenarios[0]);
    }
    await writeJsonl(
      resolvedOut,
      'events.jsonl',
      results.flatMap((result) => result.events)
    );
    await writeJsonl(
      resolvedOut,
      'state.jsonl',
      results.flatMap((result) => result.stateSnapshots)
    );
    await writeEvidence(resolvedOut, 'summary.md', renderSummary(run, results));
    return run;
  } finally {
    if (panel) await panel.close();
  }
}

export async function runScenario(scenario, context, outDir, variables = {}) {
  await fs.mkdir(path.join(outDir, 'screens'), { recursive: true });
  const startedAt = new Date().toISOString();
  const captures = {};
  const assertions = [];
  const events = [];
  const stateSnapshots = [];
  let status = 'PASS';
  let errorText = null;

  try {
    for (const [index, sourceStep] of scenario.steps.entries()) {
      const step = resolveTemplates(sourceStep, variables);
      const name = step.as ?? step.action;
      if (!name) throw new Error('scenario step has no capture name');
      const event = {
        at: new Date().toISOString(),
        scenario: scenario.id,
        step: index,
        action: step.action,
        as: name,
        status: 'started'
      };
      events.push(event);

      const value = await executeStep(step, context, outDir, scenario);
      captures[name] = value;
      event.status = 'ok';
      event.finishedAt = new Date().toISOString();

      if (isStateAction(step.action)) {
        stateSnapshots.push({
          at: event.finishedAt,
          scenario: scenario.id,
          source: step.action,
          as: name,
          state: value
        });
      }
    }

    const logs = context.panel ? context.panel.logs : [];
    if (logs.length > 0) captures.logs = logs;

    for (const [index, assertion] of (scenario.assert ?? []).entries()) {
      const outcome = evaluateAssertion(assertion, captures);
      assertions.push(outcome);
      if (!outcome.pass) {
        throw new Error(`assertion ${index + 1} failed: ${outcome.detail}`);
      }
    }
  } catch (error) {
    status = 'FAIL';
    errorText = error instanceof Error ? error.message : String(error);
    events.push({
      at: new Date().toISOString(),
      scenario: scenario.id,
      action: 'failure',
      status: 'failed',
      error: errorText
    });
  }

  return {
    id: scenario.id,
    title: scenario.title ?? scenario.id,
    priority: scenario.priority ?? null,
    status,
    error: errorText,
    startedAt,
    finishedAt: new Date().toISOString(),
    captures,
    assertions,
    events,
    stateSnapshots,
    logs: context.panel ? context.panel.logs : []
  };
}

async function executeStep(step, context, outDir, scenario) {
  switch (step.action) {
    case 'health':
      return context.bridge.health();
    case 'state':
      return context.bridge.state();
    case 'screen':
      return context.bridge.screen();
    case 'menu':
      return context.bridge.menu();
    case 'screenshot': {
      const buffer = await context.bridge.screenshot();
      const file = path.basename(
        step.file ?? `${scenario.id.toLowerCase()}-screenshot.png`
      );
      await fs.writeFile(path.join(outDir, file), buffer);
      await fs.writeFile(path.join(outDir, 'screens', file), buffer);
      return {
        file,
        screensFile: `screens/${file}`,
        bytes: buffer.length,
        sha256: crypto.createHash('sha256').update(buffer).digest('hex'),
        contentType: 'image/png'
      };
    }
    case 'menuClick': {
      const result = await context.bridge.menuClick(step.slot);
      if (result.status !== 200) {
        throw new BridgeContractError(
          `menuClick returned HTTP ${result.status}`,
          { status: result.status, body: result.body }
        );
      }
      return result;
    }
    case 'panelState':
      return requirePanel(context).state;
    case 'taskAdd':
      return requirePanel(context).taskAdd({
        item: step.item,
        qty: step.qty,
        type: step.type,
        bot: step.bot
      });
    case 'taskClearDone':
      return requirePanel(context).taskClearDone();
    case 'taskCancel':
      return requirePanel(context).taskCancel(step.bot);
    case 'taskPause':
      return requirePanel(context).taskPause(step.bot);
    case 'taskResume':
      return requirePanel(context).taskResume(step.bot);
    case 'panelAction':
      return requirePanel(context).action(step.name);
    case 'waitForTask':
      return requirePanel(context).waitForTask({
        item: step.item,
        type: step.type,
        status: step.status,
        bot: step.bot,
        timeoutMs: timeoutMs(step.timeoutSec)
      });
    case 'waitForBot':
      return requirePanel(context).waitForBot({
        name: step.name,
        halted: step.halted,
        timeoutMs: timeoutMs(step.timeoutSec)
      });
    case 'logs':
      return requirePanel(context).logs;
    case 'http':
      return requirePanel(context).post(step.path, step.body ?? {});
    case 'sleep': {
      const ms = Number(step.ms ?? 0);
      await new Promise((resolve) => setTimeout(resolve, ms));
      return { sleptMs: ms };
    }
    case 'captureQueue': {
      const file = path.resolve(step.path);
      const text = await fs.readFile(file, 'utf8');
      return JSON.parse(text);
    }
    default:
      throw new Error(`unsupported scenario action: ${step.action}`);
  }
}

function requirePanel(context) {
  if (!context.panel) {
    throw new PanelContractError('scenario requires the panel adapter');
  }
  return context.panel;
}

function scenarioRequiresPanel(scenario) {
  return (
    (scenario.requires ?? []).includes('panel') ||
    scenario.steps.some((step) => PANEL_ACTIONS.has(step.action))
  );
}

function isStateAction(action) {
  return action === 'state' || action === 'panelState';
}

function timeoutMs(timeoutSec) {
  const seconds = Number(timeoutSec ?? 10);
  return Math.max(1, seconds) * 1000;
}

function evaluateAssertion(assertion, captures) {
  const actual = getPath(captures, assertion.path);
  let expected;
  let pass = false;

  if (Object.hasOwn(assertion, 'equals')) {
    expected = assertion.equals;
    pass = deepEqual(actual, expected);
  } else if (Object.hasOwn(assertion, 'equalsPath')) {
    expected = getPath(captures, assertion.equalsPath);
    pass = deepEqual(actual, expected);
  } else if (Object.hasOwn(assertion, 'exists')) {
    expected = assertion.exists;
    pass = expected ? actual !== undefined && actual !== null : actual === undefined || actual === null;
  } else if (Object.hasOwn(assertion, 'lengthMin')) {
    expected = `length >= ${assertion.lengthMin}`;
    pass = (Array.isArray(actual) || typeof actual === 'string') && actual.length >= assertion.lengthMin;
  } else if (Object.hasOwn(assertion, 'type')) {
    expected = assertion.type;
    pass = assertion.type === 'array' ? Array.isArray(actual) : typeof actual === assertion.type;
  } else if (Object.hasOwn(assertion, 'contains')) {
    expected = `contains ${JSON.stringify(assertion.contains)}`;
    pass = JSON.stringify(actual).includes(String(assertion.contains));
  } else if (Object.hasOwn(assertion, 'notContains')) {
    expected = `does not contain ${JSON.stringify(assertion.notContains)}`;
    pass = !JSON.stringify(actual).includes(String(assertion.notContains));
  } else {
    throw new Error(`unsupported assertion for ${assertion.path}`);
  }

  return {
    path: assertion.path,
    pass,
    expected,
    actual,
    detail: pass
      ? `${assertion.path} matched`
      : `${assertion.path}: expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`
  };
}

function getPath(root, dottedPath) {
  if (!dottedPath) return root;
  let current = root;
  for (const part of dottedPath.split('.')) {
    if (current === null || current === undefined) return undefined;
    if (Array.isArray(current)) {
      const index = Number(part);
      if (!Number.isInteger(index)) return undefined;
      current = current[index];
    } else {
      current = current[part];
    }
  }
  return current;
}

function deepEqual(a, b) {
  return JSON.stringify(a) === JSON.stringify(b);
}

function resolveTemplates(value, variables) {
  if (typeof value === 'string') {
    return value.replace(/\$\{([A-Z0-9_]+)\}/g, (_, key) => {
      if (!Object.hasOwn(variables, key) || variables[key] === undefined || variables[key] === '') {
        throw new Error(`missing scenario variable: ${key}`);
      }
      return String(variables[key]);
    });
  }
  if (Array.isArray(value)) {
    return value.map((entry) => resolveTemplates(entry, variables));
  }
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([key, entry]) => [key, resolveTemplates(entry, variables)])
    );
  }
  return value;
}

async function writeEvidence(dir, name, value) {
  await fs.writeFile(
    path.join(dir, name),
    typeof value === 'string' ? value : JSON.stringify(value, null, 2),
    'utf8'
  );
}

async function writeJsonl(dir, name, entries) {
  const lines = entries.map((entry) => JSON.stringify(entry)).join('\n');
  await fs.writeFile(path.join(dir, name), lines ? `${lines}\n` : '', 'utf8');
}

async function captureArtifacts(dir, sources) {
  const artifacts = {};
  const entries = [
    ['hostLog', 'host.log'],
    ['botLog', 'bot.log'],
    ['hostQueue', 'task-queue.host.json'],
    ['botQueue', 'task-queue.bot.json']
  ];

  for (const [key, destination] of entries) {
    const source = sources[key];
    if (!source) {
      artifacts[key] = null;
      continue;
    }
    try {
      const text = await fs.readFile(path.resolve(source), 'utf8');
      const output = redactSecrets(text);
      await fs.writeFile(path.join(dir, destination), output, 'utf8');
      artifacts[key] = {
        source: path.resolve(source),
        file: destination,
        bytes: Buffer.byteLength(output)
      };
    } catch (error) {
      artifacts[key] = {
        source: path.resolve(source),
        error: error instanceof Error ? error.message : String(error)
      };
    }
  }
  return artifacts;
}

function redactSecrets(text) {
  return text
    .replace(/([?&]token=)[^&\s]+/gi, '$1<redacted>')
    .replace(/"token"\s*:\s*"[^"]*"/gi, '"token":"<redacted>"');
}

function renderSummary(run, results) {
  const lines = [
    '# Enhanced Swarm harness run',
    '',
    `- Mode: ${run.mode}`,
    `- Status: ${run.status}`,
    `- Bridge: ${run.baseUrl}`,
    ...(run.panelBaseUrl ? [`- Panel: ${run.panelBaseUrl}`] : []),
    `- Started: ${run.startedAt}`,
    `- Finished: ${run.finishedAt ?? 'n/a'}`,
    `- Output: ${run.outDir}`,
    ''
  ];

  if (run.skipped) {
    lines.push('## Skip reason', '', run.reason, '');
    return `${lines.join('\n')}\n`;
  }

  lines.push('## Scenarios', '');
  for (const result of results) {
    lines.push(`### ${result.id} — ${result.status}`, '', result.title, '');
    if (result.error) lines.push(`First divergence: ${result.error}`, '');
    if (result.assertions?.length) {
      lines.push('Assertions:');
      for (const assertion of result.assertions) {
        lines.push(
          `- ${assertion.pass ? 'PASS' : 'FAIL'} ${assertion.path}: ${assertion.detail}`
        );
      }
      lines.push('');
    }
  }
  return `${lines.join('\n')}\n`;
}

function stamp(iso) {
  return iso.replace(/[:.]/g, '-');
}
