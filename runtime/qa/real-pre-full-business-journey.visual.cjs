const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const {
  DEFAULT_JOURNEY_ACCOUNTS,
  assertJourneyPreflight
} = require('./real-pre-full-business-journey.preflight.cjs');
const { applyRealPreEnv } = require('./real-pre-env.cjs');
const { assertRealPrePreflight } = require('./real-pre-preflight.cjs');

const root = path.join(__dirname, '..', '..');
const timestamp = formatLocalTimestamp(new Date());
const evidenceDir = process.env.E2E_JOURNEY_EVIDENCE_DIR ||
  path.join(root, 'runtime', 'qa', 'out', `real-pre-full-business-journey-${timestamp}`);
const extraArgs = normalizePlaywrightArgs(process.argv.slice(2));

runCli().catch((error) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
});

async function runCli() {
  fs.mkdirSync(evidenceDir, { recursive: true });

  process.env.E2E_REAL_PRE = 'true';
  process.env.E2E_REAL_PRE_JOURNEY_VISUAL = 'true';
  process.env.E2E_VISUAL_CAPTURE = process.env.E2E_VISUAL_CAPTURE || 'true';
  process.env.E2E_HEADLESS = 'false';
  process.env.E2E_JOURNEY_EVIDENCE_DIR = evidenceDir;
  process.env.PLAYWRIGHT_HTML_OUTPUT_DIR = path.join(evidenceDir, 'playwright-report');
  process.env.PLAYWRIGHT_HTML_OPEN = 'never';
  process.env.PW_WORKERS = process.env.PW_WORKERS || '1';
  process.env.PW_SLOWMO_MS = process.env.PW_SLOWMO_MS || '700';
  process.env.PW_STEP_PAUSE_MS = process.env.PW_STEP_PAUSE_MS || '900';
  process.env.PW_AFTER_ACTION_PAUSE_MS = process.env.PW_AFTER_ACTION_PAUSE_MS || '700';
  applyRealPreEnv(process.env);

  await assertRealPrePreflight({ root });
  await assertJourneyPreflight({
    frontendUrl: process.env.E2E_BASE_URL,
    backendUrl: process.env.E2E_BACKEND_URL,
    root,
    accounts: DEFAULT_JOURNEY_ACCOUNTS
  });

  console.log(`Evidence directory: ${evidenceDir}`);
  console.log(`Visual timing: slowMo=${process.env.PW_SLOWMO_MS}ms, stepPause=${process.env.PW_STEP_PAUSE_MS}ms, afterAction=${process.env.PW_AFTER_ACTION_PAUSE_MS}ms`);

  const result = spawnSync(
    'npx',
    [
      'playwright',
      'test',
      '--config=playwright.config.ts',
      '--project=real-pre-journey-visual',
      'tests/e2e/12-real-pre-full-business-journey.visual.spec.ts',
      '--headed',
      '--workers=1',
      '--trace',
      'on',
      '--reporter=line,html',
      ...extraArgs
    ],
    {
      cwd: root,
      stdio: 'inherit',
      shell: true,
      env: process.env
    }
  );

  process.exit(result.status === null ? 1 : result.status);
}

function formatLocalTimestamp(date) {
  const pad = (value) => String(value).padStart(2, '0');
  return [
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate())
  ].join('') + '-' + [
    pad(date.getHours()),
    pad(date.getMinutes()),
    pad(date.getSeconds())
  ].join('');
}

function normalizePlaywrightArgs(argv) {
  const passthrough = [];
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === '--video' || arg === '--screenshot') {
      const next = argv[index + 1];
      if (next && !next.startsWith('-')) {
        if (next === 'off') process.env.E2E_VISUAL_CAPTURE = 'false';
        index += 1;
      }
      continue;
    }
    if (arg.startsWith('--video=') || arg.startsWith('--screenshot=')) {
      const [, value = 'on'] = arg.split('=', 2);
      if (value === 'off') process.env.E2E_VISUAL_CAPTURE = 'false';
      continue;
    }
    passthrough.push(arg);
  }
  return passthrough;
}
