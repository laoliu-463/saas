/**
 * Real-pre business flow E2E entry.
 *
 * This script intentionally covers one narrow business smoke path only:
 * activity products -> local state -> promotion link -> pick_source mapping
 * -> order sync -> dashboard/pages.
 */

const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const root = path.join(__dirname, '..', '..');
const timestamp = formatLocalTimestamp(new Date());
const evidenceDir = path.join(root, 'runtime', 'qa', 'out', `real-pre-business-e2e-${timestamp}`);

fs.mkdirSync(evidenceDir, { recursive: true });

process.env.E2E_REAL_PRE = 'true';
process.env.E2E_REAL_PRE_BUSINESS = 'true';
process.env.E2E_BUSINESS_EVIDENCE_DIR = evidenceDir;
process.env.PLAYWRIGHT_HTML_OUTPUT_DIR = path.join(evidenceDir, 'playwright-report');
process.env.PLAYWRIGHT_HTML_OPEN = 'never';
if (!process.env.E2E_BASE_URL) {
  process.env.E2E_BASE_URL = process.env.FRONTEND_URL || 'http://localhost:3000';
}
if (!process.env.E2E_BACKEND_URL) {
  process.env.E2E_BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8080';
}
const extraArgs = normalizePlaywrightArgs(process.argv.slice(2));

const result = spawnSync(
  'npx',
  [
    'playwright',
    'test',
    '--project=real-pre-business',
    'tests/e2e/10-real-pre-business-flow.spec.ts',
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
        if (next !== 'off') process.env.E2E_VISUAL_CAPTURE = 'true';
        index += 1;
      } else {
        process.env.E2E_VISUAL_CAPTURE = 'true';
      }
      continue;
    }
    if (arg.startsWith('--video=') || arg.startsWith('--screenshot=')) {
      const [, value = 'on'] = arg.split('=', 2);
      if (value !== 'off') process.env.E2E_VISUAL_CAPTURE = 'true';
      continue;
    }
    passthrough.push(arg);
  }
  return passthrough;
}
