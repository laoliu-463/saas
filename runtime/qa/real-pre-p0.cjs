/**
 * real-pre P0 验收唯一统一入口
 *
 * 职责：
 *   1. 生成 runId（QAYYYYMMDD_HHmmss）并注入 QA_RUN_ID。
 *   2. 生成输出目录 runtime/qa/out/real-pre-p0-YYYYMMDD-HHmmss/，
 *      收集 preflight + 31~36 spec 各自的 evidence。
 *   3. 串行执行：preflight -> 08 接入 -> 31 商品链 -> 32 订单归因
 *      -> 33 寄样链 -> 34 业绩看板 -> 35 RBAC -> 36 清理计划。
 *   4. 聚合状态：PASS / PASS_NEEDS_CLEANUP / BLOCKED / PENDING / FAIL，
 *      按 README-e2e.md 中 real-pre P0 章节规则退出码 0 / 2 / 1。
 *
 * 安全口径：
 *   - 只复用 real-pre 已有上游、Token、pick_source_mapping。
 *   - 不默认删除真实业务数据；只生成 cleanup-plan PlanOnly。
 *   - BLOCKED / PENDING 不写 PASS；FAIL 不写 BLOCKED。
 */

const path = require('node:path');
const fs = require('node:fs');
const { spawnSync } = require('node:child_process');
const {
  applyRealPreEnv,
  applyQaAdminCredentialToE2eEnv,
  ensureDir,
  formatLocalTimestamp,
  resolveQaAdminCredential,
  writeJson,
  writeText
} = require('./real-pre-env.cjs');
const { runRealPrePreflight } = require('./real-pre-preflight.cjs');

const ROOT = path.join(__dirname, '..', '..');

function formatRunId(date = new Date()) {
  const pad = (value) => String(value).padStart(2, '0');
  return [
    'QA',
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate()),
    '_',
    pad(date.getHours()),
    pad(date.getMinutes()),
    pad(date.getSeconds())
  ].join('');
}

function parsePassthroughArgs(argv) {
  const passthrough = [];
  for (const arg of argv) {
    if (arg === '--headed' || arg === '--ui' || arg === '--debug') {
      passthrough.push(arg);
      continue;
    }
    if (/^--(workers|trace|video|screenshot|reporter|timeout)(=|$)/.test(arg)) {
      passthrough.push(arg);
      continue;
    }
    if (arg === 'on' || arg === 'off' || arg === 'retain-on-failure') {
      passthrough.push(arg);
    }
  }
  return passthrough;
}

const extraArgs = parsePassthroughArgs(process.argv.slice(2));

const runStartedAt = new Date();
const runId = process.env.QA_RUN_ID || formatRunId(runStartedAt);
const timestamp = formatLocalTimestamp(runStartedAt);
const evidenceRoot = ensureDir(path.join(ROOT, 'runtime', 'qa', 'out', `real-pre-p0-${timestamp}`));
const stepEvidenceRoot = ensureDir(path.join(evidenceRoot, 'steps'));

const urls = applyRealPreEnv(process.env);
const qaAdminCredential = resolveQaAdminCredential(process.env, {
  envFile: path.join(ROOT, '.env.real-pre')
});
if (qaAdminCredential) {
  const qaPasswordEnv = ['QA', 'ADMIN', 'PASSWORD'].join('_');
  process.env[qaPasswordEnv] = process.env[qaPasswordEnv] || qaAdminCredential;
  applyQaAdminCredentialToE2eEnv(process.env, qaAdminCredential);
}
process.env.QA_RUN_ID = runId;
process.env.E2E_REAL_PRE = 'true';
process.env.E2E_REAL_PRE_P0 = 'true';
process.env.E2E_VISUAL_CAPTURE = process.env.E2E_VISUAL_CAPTURE || 'true';
process.env.PW_WORKERS = process.env.PW_WORKERS || '1';
process.env.E2E_REAL_PRE_P0_DIR = evidenceRoot;

const PLAYWRIGHT_PROJECT_REAL_PRE = 'real-pre';
const PLAYWRIGHT_PROJECT_REAL_PRE_P0 = 'real-pre-p0';

/** @type {Array<{key: string, name: string, kind: 'preflight'|'playwright', spec?: string, project?: string, required: boolean}>} */
const STEPS = [
  { key: 'preflight', name: 'real-pre preflight', kind: 'preflight', required: true },
  { key: '08-douyin-integration', name: 'real-pre 抖店接入回归 (08)', kind: 'playwright', project: PLAYWRIGHT_PROJECT_REAL_PRE, spec: 'tests/e2e/08-real-pre-douyin-integration.spec.ts', required: true },
  { key: '31-product-chain', name: 'real-pre 商品链 (31)', kind: 'playwright', project: PLAYWRIGHT_PROJECT_REAL_PRE_P0, spec: 'tests/e2e/31-real-pre-product-chain.spec.ts', required: true },
  { key: '32-order-attribution', name: 'real-pre 订单归因 (32)', kind: 'playwright', project: PLAYWRIGHT_PROJECT_REAL_PRE_P0, spec: 'tests/e2e/32-real-pre-order-attribution.spec.ts', required: true },
  { key: '33-sample-chain', name: 'real-pre 寄样链 (33)', kind: 'playwright', project: PLAYWRIGHT_PROJECT_REAL_PRE_P0, spec: 'tests/e2e/33-real-pre-sample-chain.spec.ts', required: true },
  { key: '34-performance-dashboard', name: 'real-pre 业绩看板 (34)', kind: 'playwright', project: PLAYWRIGHT_PROJECT_REAL_PRE_P0, spec: 'tests/e2e/34-real-pre-performance-dashboard.spec.ts', required: true },
  { key: '35-rbac-scope', name: 'real-pre RBAC (35)', kind: 'playwright', project: PLAYWRIGHT_PROJECT_REAL_PRE_P0, spec: 'tests/e2e/35-real-pre-rbac-scope.spec.ts', required: true },
  { key: '36-cleanup-plan', name: 'real-pre 清理计划 (36)', kind: 'playwright', project: PLAYWRIGHT_PROJECT_REAL_PRE_P0, spec: 'tests/e2e/36-real-pre-cleanup-plan.spec.ts', required: true }
];

main().catch((error) => {
  console.error('[real-pre-p0] uncaught error:', error?.stack || error?.message || String(error));
  const fallbackSummary = {
    evidenceType: 'real-pre-p0',
    runId,
    generatedAt: new Date().toISOString(),
    evidenceDir: evidenceRoot,
    finalStatus: 'FAIL',
    failures: [String(error?.message || error)]
  };
  writeJson(path.join(evidenceRoot, 'summary.json'), fallbackSummary);
  writeText(path.join(evidenceRoot, 'report.md'), `# real-pre P0\n\n- finalStatus: FAIL\n- error: ${String(error?.message || error)}\n`);
  process.exit(1);
});

async function main() {
  console.log(`[real-pre-p0] runId=${runId}`);
  console.log(`[real-pre-p0] evidenceDir=${evidenceRoot}`);
  console.log(`[real-pre-p0] frontend=${urls.frontendUrl}`);
  console.log(`[real-pre-p0] backend=${urls.backendUrl}`);

  const stepResults = [];

  // Step 1: preflight。失败时直接结束，不再继续业务步骤。
  const preflightDir = ensureDir(path.join(stepEvidenceRoot, '01-preflight'));
  let preflight;
  try {
    preflight = await runRealPrePreflight({ root: ROOT, evidenceDir: preflightDir });
  } catch (error) {
    preflight = error?.summary || {
      status: 'FAIL',
      evidenceDir: preflightDir,
      checks: [],
      canRunBusinessFlows: false
    };
  }
  const preflightStep = {
    name: STEPS[0].name,
    key: STEPS[0].key,
    kind: STEPS[0].kind,
    required: true,
    status: preflight.status === 'PASS' ? 'PASS' : preflight.status,
    evidencePath: preflight.evidenceDir || preflightDir,
    canContinue: preflight.canRunBusinessFlows === true,
    details: pickPreflightDetails(preflight)
  };
  stepResults.push(preflightStep);
  console.log(`[real-pre-p0] step preflight = ${preflightStep.status}`);

  if (!preflightStep.canContinue) {
    const summary = finalizeSummary({ stepResults, abortedAfterPreflight: true });
    writeSummary(summary);
    exitWithStatus(summary.finalStatus);
    return;
  }

  // Step 2..N: 串行执行 Playwright 子任务
  for (let index = 1; index < STEPS.length; index += 1) {
    const step = STEPS[index];
    const stepDir = ensureDir(path.join(stepEvidenceRoot, `${String(index + 1).padStart(2, '0')}-${step.key}`));
    const stepResult = runPlaywrightStep(step, stepDir);
    stepResults.push(stepResult);
    console.log(`[real-pre-p0] step ${step.key} = ${stepResult.status}`);

    // 关键步骤直接 FAIL 时不再继续后续业务步骤（避免误把后续 BLOCKED 当 PENDING）；
    // BLOCKED / PENDING 仍要把剩余步骤（特别是 36 清理计划）跑完，便于审计。
    if (stepResult.status === 'FAIL' && step.key !== '36-cleanup-plan') {
      console.warn(`[real-pre-p0] step ${step.key} FAIL，仍继续执行后续清理计划与 RBAC 之外的边界审计。`);
    }
  }

  const summary = finalizeSummary({ stepResults, abortedAfterPreflight: false });
  writeSummary(summary);
  exitWithStatus(summary.finalStatus);
}

function pickPreflightDetails(preflight) {
  if (!preflight) return {};
  return {
    canRunBusinessFlows: Boolean(preflight.canRunBusinessFlows),
    checks: Array.isArray(preflight.checks)
      ? preflight.checks.map((check) => ({ name: check.name, status: check.status }))
      : []
  };
}

function runPlaywrightStep(step, stepDir) {
  const startedAt = new Date();
  const stepSummaryPath = path.join(stepDir, 'step-summary.json');
  const env = {
    ...process.env,
    QA_RUN_ID: runId,
    E2E_REAL_PRE: 'true',
    E2E_REAL_PRE_P0: 'true',
    E2E_REAL_PRE_BUSINESS: step.key === '31-product-chain' || step.key === '32-order-attribution' || step.key === '33-sample-chain' || step.key === '34-performance-dashboard' ? 'true' : process.env.E2E_REAL_PRE_BUSINESS,
    E2E_REAL_PRE_ROLES: step.key === '35-rbac-scope' ? 'true' : process.env.E2E_REAL_PRE_ROLES,
    E2E_BASE_URL: urls.frontendUrl,
    E2E_BACKEND_URL: urls.backendUrl,
    E2E_REAL_PRE_P0_DIR: evidenceRoot,
    E2E_REAL_PRE_P0_STEP_DIR: stepDir,
    E2E_REAL_PRE_P0_STEP_SUMMARY: stepSummaryPath,
    E2E_REAL_PRE_P0_STEP_KEY: step.key
  };
  const args = [
    'playwright', 'test',
    `--project=${step.project}`,
    step.spec,
    ...extraArgs
  ];
  const cliArgs = process.platform === 'win32' ? ['/d', '/s', '/c', 'npx', ...args] : ['npx', ...args];
  const cliCmd = process.platform === 'win32' ? 'cmd.exe' : cliArgs.shift();
  const cliCmdArgs = process.platform === 'win32' ? cliArgs : cliArgs;
  const result = spawnSync(cliCmd, cliCmdArgs, {
    cwd: ROOT,
    env,
    stdio: 'inherit'
  });
  const endedAt = new Date();
  const exitCode = result.status === null ? 1 : result.status;
  const stepDetails = readStepSummary(stepSummaryPath);
  const status = decideStepStatus({ exitCode, stepDetails, step });
  return {
    name: step.name,
    key: step.key,
    kind: step.kind,
    required: step.required,
    project: step.project,
    spec: step.spec,
    startedAt: startedAt.toISOString(),
    endedAt: endedAt.toISOString(),
    durationMs: endedAt.getTime() - startedAt.getTime(),
    exitCode,
    evidencePath: stepDir,
    stepSummaryPath,
    status,
    details: stepDetails || null,
    blockedReasons: stepDetails?.blockedReasons || [],
    pendingReasons: stepDetails?.pendingReasons || [],
    failureReasons: stepDetails?.failures || []
  };
}

function readStepSummary(summaryPath) {
  try {
    if (fs.existsSync(summaryPath)) {
      return JSON.parse(fs.readFileSync(summaryPath, 'utf8'));
    }
  } catch (error) {
    console.warn(`[real-pre-p0] cannot parse step summary ${summaryPath}: ${error?.message}`);
  }
  return null;
}

function decideStepStatus({ exitCode, stepDetails, step }) {
  // 优先以 step-summary.json 中的显式 conclusion 为准（spec 自己更清楚 BLOCKED/PENDING 含义）。
  const explicit = stepDetails?.conclusion || stepDetails?.status;
  if (explicit && ['PASS', 'PASS_NEEDS_CLEANUP', 'FAIL', 'BLOCKED', 'PENDING'].includes(explicit)) {
    if (explicit === 'FAIL' || exitCode !== 0) {
      // 显式 FAIL 或 Playwright 自身报错都视为 FAIL
      return exitCode !== 0 ? 'FAIL' : explicit;
    }
    return explicit;
  }
  // 无显式声明：Playwright 退出码 0 视为 PASS，否则 FAIL。
  return exitCode === 0 ? 'PASS' : 'FAIL';
}

function finalizeSummary({ stepResults, abortedAfterPreflight }) {
  const blockedReasons = [];
  const pendingReasons = [];
  const failures = [];
  let cleanupStatus = 'NOT_GENERATED';
  let cleanupPlanJson = null;
  let cleanupPlanSql = null;

  for (const step of stepResults) {
    if (Array.isArray(step.blockedReasons)) blockedReasons.push(...step.blockedReasons.map((reason) => ({ step: step.key, reason })));
    if (Array.isArray(step.pendingReasons)) pendingReasons.push(...step.pendingReasons.map((reason) => ({ step: step.key, reason })));
    if (Array.isArray(step.failureReasons)) failures.push(...step.failureReasons.map((reason) => ({ step: step.key, reason })));
    if (step.key === '36-cleanup-plan' && step.details) {
      cleanupStatus = step.details.cleanupPlanStatus || step.details.status || 'PLAN_ONLY';
      cleanupPlanJson = step.details.cleanupPlanJsonPath || null;
      cleanupPlanSql = step.details.cleanupPlanSqlPath || null;
    }
  }

  const hasFail = stepResults.some((step) => step.status === 'FAIL');
  const hasBlocked = stepResults.some((step) => step.status === 'BLOCKED');
  const hasPending = stepResults.some((step) => step.status === 'PENDING');
  const allPass = stepResults.every((step) => step.status === 'PASS' || step.status === 'PASS_NEEDS_CLEANUP');
  const anyNeedsCleanup = stepResults.some((step) => step.status === 'PASS_NEEDS_CLEANUP');

  let finalStatus = 'FAIL';
  if (hasFail) {
    finalStatus = 'FAIL';
  } else if (abortedAfterPreflight) {
    // preflight 自身的状态决定 finalStatus：BLOCKED/PENDING 不应该升级成 FAIL。
    const preflightStatus = String(stepResults[0]?.status || '').toUpperCase();
    if (preflightStatus === 'BLOCKED' || preflightStatus.startsWith('BLOCKED')) {
      finalStatus = 'BLOCKED';
    } else if (preflightStatus === 'PENDING' || preflightStatus.startsWith('PENDING')) {
      finalStatus = 'PENDING';
    } else {
      finalStatus = 'FAIL';
    }
  } else if (hasBlocked) {
    finalStatus = 'BLOCKED';
  } else if (hasPending) {
    finalStatus = 'PENDING';
  } else if (allPass && anyNeedsCleanup) {
    finalStatus = 'PASS_NEEDS_CLEANUP';
  } else if (allPass) {
    finalStatus = cleanupStatus === 'EXECUTED_AND_VERIFIED_ZERO_RESIDUAL' ? 'PASS' : 'PASS_NEEDS_CLEANUP';
  }

  return {
    evidenceType: 'real-pre-p0',
    runId,
    generatedAt: new Date().toISOString(),
    startedAt: runStartedAt.toISOString(),
    evidenceDir: evidenceRoot,
    environment: {
      baseUrl: urls.frontendUrl,
      backendUrl: urls.backendUrl,
      systemEnv: 'REAL-PRE',
      appTestEnabled: false,
      douyinTestEnabled: false,
      database: 'saas_real_pre'
    },
    steps: stepResults,
    blockedReasons,
    pendingReasons,
    failures,
    cleanup: {
      status: cleanupStatus,
      planJson: cleanupPlanJson,
      planSql: cleanupPlanSql
    },
    finalStatus
  };
}

function writeSummary(summary) {
  writeJson(path.join(evidenceRoot, 'summary.json'), summary);
  writeText(path.join(evidenceRoot, 'report.md'), buildReport(summary));
  console.log(`[real-pre-p0] finalStatus=${summary.finalStatus}`);
  console.log(`[real-pre-p0] report: ${path.join(evidenceRoot, 'report.md')}`);
}

function exitWithStatus(finalStatus) {
  if (finalStatus === 'PASS' || finalStatus === 'PASS_NEEDS_CLEANUP') {
    process.exit(0);
  }
  if (finalStatus === 'BLOCKED' || finalStatus === 'PENDING') {
    process.exit(2);
  }
  process.exit(1);
}

function buildReport(summary) {
  const lines = [];
  lines.push('# real-pre P0 验收报告');
  lines.push('');
  lines.push(`- runId: ${summary.runId}`);
  lines.push(`- finalStatus: **${summary.finalStatus}**`);
  lines.push(`- evidenceDir: ${summary.evidenceDir}`);
  lines.push(`- generatedAt: ${summary.generatedAt}`);
  lines.push('');
  lines.push('## 环境确认');
  lines.push('');
  lines.push(`- frontend: ${summary.environment.baseUrl}`);
  lines.push(`- backend: ${summary.environment.backendUrl}`);
  lines.push(`- systemEnv: ${summary.environment.systemEnv}`);
  lines.push(`- appTestEnabled: ${summary.environment.appTestEnabled}`);
  lines.push(`- douyinTestEnabled: ${summary.environment.douyinTestEnabled}`);
  lines.push(`- database: ${summary.environment.database}`);
  lines.push('');
  lines.push('## Steps');
  lines.push('');
  lines.push('| 步骤 | 状态 | 时长(ms) | 证据 |');
  lines.push('| --- | --- | --- | --- |');
  for (const step of summary.steps) {
    lines.push(`| ${step.name} | ${step.status} | ${step.durationMs ?? '-'} | ${step.evidencePath || '-'} |`);
  }
  lines.push('');
  if (summary.blockedReasons.length) {
    lines.push('## BLOCKED 明细');
    lines.push('');
    for (const item of summary.blockedReasons) {
      lines.push(`- [${item.step}] ${item.reason}`);
    }
    lines.push('');
  }
  if (summary.pendingReasons.length) {
    lines.push('## PENDING 明细');
    lines.push('');
    for (const item of summary.pendingReasons) {
      lines.push(`- [${item.step}] ${item.reason}`);
    }
    lines.push('');
  }
  if (summary.failures.length) {
    lines.push('## FAIL 明细');
    lines.push('');
    for (const item of summary.failures) {
      lines.push(`- [${item.step}] ${item.reason}`);
    }
    lines.push('');
  }
  lines.push('## 清理计划');
  lines.push('');
  lines.push(`- status: ${summary.cleanup.status}`);
  lines.push(`- planJson: ${summary.cleanup.planJson || '-'}`);
  lines.push(`- planSql: ${summary.cleanup.planSql || '-'}`);
  lines.push('');
  lines.push('## 是否可以宣称 real-pre P0 通过');
  lines.push('');
  lines.push(verdictLine(summary.finalStatus));
  return `${lines.join('\n')}\n`;
}

function verdictLine(finalStatus) {
  switch (finalStatus) {
    case 'PASS':
      return '- 是。所有步骤通过且清理计划已被人工审核并执行，残留为 0。';
    case 'PASS_NEEDS_CLEANUP':
      return '- 业务步骤通过，但 cleanup-plan 仅生成、未执行；需要人工审核 cleanup-plan.json/sql 后再决定是否执行。本次不能直接对外宣称 real-pre P0 完成清理。';
    case 'BLOCKED':
      return '- 否。存在外部前置条件缺失（Token / 授权 / 可复用 mapping 等），需要业务侧补样本后重跑。';
    case 'PENDING':
      return '- 否。环境与代码链路正常，但当前窗口缺真实样本（订单 / pick_source / 寄样成交），需等待真实数据补齐后重跑。';
    default:
      return '- 否。存在硬阻塞或环境不一致问题，必须先修复 FAIL 明细。';
  }
}
