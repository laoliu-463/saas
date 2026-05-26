import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

export type RealPreP0StepStatus = 'PASS' | 'PASS_NEEDS_CLEANUP' | 'BLOCKED' | 'PENDING' | 'FAIL';

export interface RealPreP0StepSummary {
  evidenceType: string;
  stepKey: string;
  runId: string;
  startedAt: string;
  finishedAt?: string;
  evidenceDir: string;
  status: RealPreP0StepStatus;
  conclusion: RealPreP0StepStatus;
  blockedReasons: string[];
  pendingReasons: string[];
  failures: string[];
  details: Record<string, unknown>;
}

export interface RealPreP0Context {
  runId: string;
  stepKey: string;
  evidenceDir: string;
  summaryPath: string;
  summary: RealPreP0StepSummary;
}

export function ensureRealPreP0Env(): void {
  if (process.env.E2E_REAL_PRE !== 'true') {
    throw new Error('REAL_PRE_GATE_NOT_SET: E2E_REAL_PRE must be true; run via npm run e2e:real-pre:p0');
  }
}

export function shouldRunRealPreP0(stepKey: string): boolean {
  if (process.env.E2E_REAL_PRE_P0 === 'true') return true;
  if (process.env.E2E_REAL_PRE === 'true' && (process.env.E2E_REAL_PRE_P0_STEP_KEY === stepKey)) return true;
  if (process.argv.some((arg) => arg.includes(`/${stepKey}`) || arg.includes(`\\${stepKey}`))) return true;
  // 防御 R2 沉默 skip：当前进程已经处在 real-pre 环境，但绕过 orchestrator
  // 直接 `npx playwright test --project=real-pre-p0` 又没设 E2E_REAL_PRE_P0=true，
  // 三个上述判定都会返回 false，spec 会被 test.skip 跳过、Playwright exit 0，
  // 沉默通过的报告会误判为 PASS。这里显式抛错，让 Playwright 把这条 spec 标 FAIL，
  // 避免在 real-pre 真实环境下因用法不对而漏报。
  if (process.env.E2E_REAL_PRE === 'true' && process.env.E2E_REAL_PRE_P0 !== 'true') {
    throw new Error(
      `REAL_PRE_P0_GATE_NOT_SET: stepKey=${stepKey} 检测到 real-pre 环境（E2E_REAL_PRE=true）` +
      `但 E2E_REAL_PRE_P0 未显式设为 'true'；请通过 npm run e2e:real-pre:p0 入口运行，` +
      `避免沉默 skip 误判为 PASS。`
    );
  }
  return false;
}

export function createRealPreP0Step(stepKey: string, evidenceType: string): RealPreP0Context {
  const explicitDir = process.env.E2E_REAL_PRE_P0_STEP_DIR;
  const rootDir = process.env.E2E_REAL_PRE_P0_DIR;
  const fallbackDir = join(process.cwd(), 'runtime', 'qa', 'out', `real-pre-p0-standalone-${formatLocalTimestamp(new Date())}`, 'steps', stepKey);
  const evidenceDir = explicitDir || (rootDir ? join(rootDir, 'steps', stepKey) : fallbackDir);
  mkdirSync(evidenceDir, { recursive: true });
  const summaryPath = process.env.E2E_REAL_PRE_P0_STEP_SUMMARY || join(evidenceDir, 'step-summary.json');
  const runId = process.env.QA_RUN_ID || `QA${formatLocalTimestamp(new Date()).replace(/[^0-9]/g, '')}`;
  const summary: RealPreP0StepSummary = {
    evidenceType,
    stepKey,
    runId,
    startedAt: new Date().toISOString(),
    evidenceDir,
    status: 'PASS',
    conclusion: 'PASS',
    blockedReasons: [],
    pendingReasons: [],
    failures: [],
    details: {}
  };
  return { runId, stepKey, evidenceDir, summaryPath, summary };
}

export function markBlocked(ctx: RealPreP0Context, reason: string): void {
  if (ctx.summary.conclusion === 'FAIL') return;
  ctx.summary.blockedReasons.push(reason);
  ctx.summary.conclusion = 'BLOCKED';
  ctx.summary.status = 'BLOCKED';
}

export function markPending(ctx: RealPreP0Context, reason: string): void {
  if (ctx.summary.conclusion === 'FAIL' || ctx.summary.conclusion === 'BLOCKED') return;
  ctx.summary.pendingReasons.push(reason);
  ctx.summary.conclusion = 'PENDING';
  ctx.summary.status = 'PENDING';
}

export function markFail(ctx: RealPreP0Context, reason: string): void {
  ctx.summary.failures.push(reason);
  ctx.summary.conclusion = 'FAIL';
  ctx.summary.status = 'FAIL';
}

export function markPassNeedsCleanup(ctx: RealPreP0Context): void {
  if (['FAIL', 'BLOCKED', 'PENDING'].includes(ctx.summary.conclusion)) return;
  ctx.summary.conclusion = 'PASS_NEEDS_CLEANUP';
  ctx.summary.status = 'PASS_NEEDS_CLEANUP';
}

export function setDetail(ctx: RealPreP0Context, key: string, value: unknown): void {
  ctx.summary.details[key] = value;
}

export function persistStepSummary(ctx: RealPreP0Context): void {
  ctx.summary.finishedAt = new Date().toISOString();
  mkdirSync(dirname(ctx.summaryPath), { recursive: true });
  writeFileSync(ctx.summaryPath, JSON.stringify(ctx.summary, null, 2), 'utf8');
  const evidenceCopy = join(ctx.evidenceDir, 'summary.json');
  if (evidenceCopy !== ctx.summaryPath) {
    writeFileSync(evidenceCopy, JSON.stringify(ctx.summary, null, 2), 'utf8');
  }
}

export function formatLocalTimestamp(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`;
}

export function safeUnwrap<T = unknown>(body: unknown): T {
  if (body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'data')) {
    return (body as { data: T }).data;
  }
  return body as T;
}

export function isUpstreamSuccess(result: unknown): boolean {
  const code = findDeepValue(result, ['code', 'err_no', 'errorCode']);
  if (code === undefined || code === null || code === '') {
    const status = (result as { status?: string } | null | undefined)?.status;
    return status === 'success';
  }
  return ['10000', '0', '200'].includes(String(code));
}

export function findDeepValue(input: unknown, keys: string[], seen = new Set<unknown>()): unknown {
  if (!input || typeof input !== 'object' || seen.has(input)) return undefined;
  seen.add(input);
  const obj = input as Record<string, unknown>;
  for (const key of keys) {
    if (Object.prototype.hasOwnProperty.call(obj, key)) return obj[key];
  }
  for (const value of Object.values(obj)) {
    const found = findDeepValue(value, keys, seen);
    if (found !== undefined) return found;
  }
  return undefined;
}

export function formatLocalDateTime(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}
