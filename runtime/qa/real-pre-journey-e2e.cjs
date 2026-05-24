const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { DEFAULT_JOURNEY_ACCOUNTS, assertJourneyPreflight } = require('./real-pre-full-business-journey.preflight.cjs');
const { applyRealPreEnv, createEvidenceDir } = require('./real-pre-env.cjs');
const { assertRealPrePreflight } = require('./real-pre-preflight.cjs');

const root = path.join(__dirname, '..', '..');
const evidenceDir = createEvidenceDir(root, 'real-pre-journey-e2e', 'E2E_JOURNEY_EVIDENCE_DIR');
const urls = applyRealPreEnv(process.env);
const extraArgs = process.argv.slice(2);

runCli().catch((error) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(error?.summary?.status === 'BLOCKED' || error?.summary?.status === 'PENDING' ? 2 : 1);
});

async function runCli() {
  process.env.E2E_JOURNEY_EVIDENCE_DIR = evidenceDir;
  process.env.PLAYWRIGHT_HTML_OUTPUT_DIR = path.join(evidenceDir, 'playwright-report');
  process.env.PLAYWRIGHT_HTML_OPEN = 'never';

  await assertRealPrePreflight({ root });
  await assertJourneyPreflight({
    frontendUrl: urls.frontendUrl,
    backendUrl: urls.backendUrl,
    root,
    accounts: DEFAULT_JOURNEY_ACCOUNTS
  });

  const result = spawnSync(
    'npx',
    [
      'playwright',
      'test',
      '--config=playwright.config.ts',
      '--project=full-journey',
      'tests/e2e/09-full-user-journey.spec.ts',
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
