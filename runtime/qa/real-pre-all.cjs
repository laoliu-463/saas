const path = require('node:path');
const { spawnSync } = require('node:child_process');
const {
  applyRealPreEnv,
  createEvidenceDir,
  writeJson,
  writeText
} = require('./real-pre-env.cjs');
const { runRealPrePreflight } = require('./real-pre-preflight.cjs');

const root = path.join(__dirname, '..', '..');
const evidenceDir = createEvidenceDir(root, 'real-pre-all');
const urls = applyRealPreEnv(process.env);

const steps = [
  ['e2e:real-pre', ['run', 'e2e:real-pre']],
  ['e2e:real-pre:business', ['run', 'e2e:real-pre:business']],
  ['e2e:real-pre:roles', ['run', 'e2e:real-pre:roles']],
  ['e2e:real-pre:journey:visual', ['run', 'e2e:real-pre:journey:visual']]
];

runCli().catch((error) => {
  console.error(error?.stack || error?.message || String(error));
  process.exit(1);
});

async function runCli() {
  const preflightDir = path.join(evidenceDir, 'preflight');
  const preflight = await runRealPrePreflight({ root, evidenceDir: preflightDir });
  const results = [];
  if (preflight.status === 'PASS') {
    for (const [name, args] of steps) {
      results.push(runStep(name, args));
      if (results[results.length - 1].status !== 'PASS') {
        break;
      }
    }
  }

  const summary = {
    evidenceType: 'real-pre-all',
    generatedAt: new Date().toISOString(),
    evidenceDir,
    urls,
    preflight: {
      status: preflight.status,
      evidenceDir: preflight.evidenceDir,
      canRunBusinessFlows: preflight.canRunBusinessFlows
    },
    steps: results,
    status: summarize(preflight, results)
  };
  writeJson(path.join(evidenceDir, 'summary.json'), summary);
  writeText(path.join(evidenceDir, 'report.md'), buildReport(summary));
  console.log(`real-pre unified report: ${evidenceDir}`);

  if (summary.status === 'PASS') process.exit(0);
  if (summary.status === 'BLOCKED' || summary.status === 'PENDING') process.exit(2);
  process.exit(1);
}

function runStep(name, args) {
  const startedAt = new Date();
  console.log(`[real-pre all] RUN ${name}`);
  const result = spawnSync('npm', args, {
    cwd: root,
    stdio: 'inherit',
    shell: true,
    env: {
      ...process.env,
      E2E_BASE_URL: urls.frontendUrl,
      E2E_BACKEND_URL: urls.backendUrl
    }
  });
  const exitCode = result.status === null ? 1 : result.status;
  const endedAt = new Date();
  return {
    name,
    status: exitCode === 0 ? 'PASS' : exitCode === 2 ? 'BLOCKED' : 'FAIL',
    exitCode,
    startedAt: startedAt.toISOString(),
    endedAt: endedAt.toISOString(),
    durationMs: endedAt.getTime() - startedAt.getTime()
  };
}

function summarize(preflight, results) {
  if (preflight.status !== 'PASS') return preflight.status;
  if (results.some((item) => item.status === 'FAIL')) return 'FAIL';
  if (results.some((item) => item.status === 'BLOCKED')) return 'BLOCKED';
  if (results.length !== steps.length) return 'FAIL';
  return 'PASS';
}

function buildReport(summary) {
  const lines = [];
  lines.push('# real-pre all business flow');
  lines.push('');
  lines.push(`- generatedAt: ${summary.generatedAt}`);
  lines.push(`- status: ${summary.status}`);
  lines.push(`- frontend: ${summary.urls.frontendUrl}`);
  lines.push(`- backend: ${summary.urls.backendUrl}`);
  lines.push(`- preflight: ${summary.preflight.status} (${summary.preflight.evidenceDir})`);
  lines.push('');
  lines.push('## Steps');
  lines.push('');
  for (const step of summary.steps) {
    lines.push(`- [${step.status}] ${step.name} (${step.durationMs}ms, exit=${step.exitCode})`);
  }
  if (summary.steps.length === 0) {
    lines.push('- No business flow step ran because preflight did not pass.');
  }
  lines.push('');
  lines.push('## Result Policy');
  lines.push('');
  lines.push('- PASS requires preflight plus every listed real-pre business script to pass.');
  lines.push('- BLOCKED/PENDING means external prerequisites are missing; this is evidence, not a business-flow pass.');
  return `${lines.join('\n')}\n`;
}
