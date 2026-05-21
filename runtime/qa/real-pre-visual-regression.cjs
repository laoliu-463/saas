const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const root = path.join(__dirname, '..', '..');
const timestamp = formatLocalTimestamp(new Date());
const evidenceDir = path.join(root, 'runtime', 'qa', 'out', `real-pre-visual-regression-${timestamp}`);
const businessEvidenceDir = path.join(evidenceDir, 'business');
const roleEvidenceDir = path.join(evidenceDir, 'roles');
const extraArgs = normalizePlaywrightArgs(process.argv.slice(2));

fs.mkdirSync(businessEvidenceDir, { recursive: true });
fs.mkdirSync(roleEvidenceDir, { recursive: true });

process.env.E2E_REAL_PRE = 'true';
process.env.E2E_REAL_PRE_BUSINESS = 'true';
process.env.E2E_REAL_PRE_ROLES = 'true';
process.env.E2E_VISUAL_CAPTURE = process.env.E2E_VISUAL_CAPTURE || 'true';
process.env.E2E_BUSINESS_EVIDENCE_DIR = businessEvidenceDir;
process.env.E2E_ROLE_EVIDENCE_DIR = roleEvidenceDir;
process.env.PLAYWRIGHT_HTML_OUTPUT_DIR = path.join(evidenceDir, 'playwright-report');
process.env.PLAYWRIGHT_HTML_OPEN = 'never';
if (!process.env.E2E_BASE_URL) {
  process.env.E2E_BASE_URL = process.env.FRONTEND_URL || 'http://localhost:3001';
}
if (!process.env.E2E_BACKEND_URL) {
  process.env.E2E_BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8081';
}

const result = spawnSync(
  'npx',
  [
    'playwright',
    'test',
    '--config=playwright.config.ts',
    '--project=real-pre-business',
    '--project=real-pre-roles',
    'tests/e2e/10-real-pre-business-flow.spec.ts',
    'tests/e2e/11-real-pre-role-business-flow.spec.ts',
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
