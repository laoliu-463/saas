/**
 * real-pre P0 / 36 / 清理计划
 *
 * 通过 runtime/qa/real-pre-cleanup-plan.cjs 仅生成 PlanOnly，
 * 不执行实际清理；任何包含真实订单 / 真实活动 / 真实商品 / 真实 Token / truncate / delete-without-runId
 * 的高危语句都视为 CLEANUP_PLAN_UNSAFE。
 */
import { test, expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import {
  createRealPreP0Step,
  ensureRealPreP0Env,
  markFail,
  markPassNeedsCleanup,
  persistStepSummary,
  setDetail,
  shouldRunRealPreP0
} from './helpers/real-pre-p0-step';

const PROHIBITED_PATTERNS = [
  /TRUNCATE\b/i,
  /DROP\s+TABLE/i,
  /DROP\s+DATABASE/i,
  /DELETE\s+FROM\s+colonelsettlement_order\b/i,
  /DELETE\s+FROM\s+colonel_activity\b/i,
  /DELETE\s+FROM\s+product\b(?!_)/i,
  /DELETE\s+FROM\s+product_snapshot\b/i,
  /DELETE\s+FROM\s+douyin_token\b/i,
  /DELETE\s+FROM\s+sys_user\b/i,
  /DELETE\s+FROM\s+sys_role\b/i
];

test('real-pre P0 / 36 / 清理计划', async ({}, testInfo) => {
  test.skip(!shouldRunRealPreP0('36-real-pre-cleanup-plan'), 'Run via npm run e2e:real-pre:p0 or set E2E_REAL_PRE_P0=true');
  test.setTimeout(5 * 60_000);
  ensureRealPreP0Env();

  const ctx = createRealPreP0Step('36-real-pre-cleanup-plan', 'real-pre-p0/36/cleanup-plan');

  try {
    const root = process.cwd();
    const evidenceDir = ctx.evidenceDir;
    mkdirSync(evidenceDir, { recursive: true });
    const planJsonPath = join(evidenceDir, 'cleanup-plan.json');
    const planSqlPath = join(evidenceDir, 'cleanup-plan.sql');

    const helper = join(root, 'runtime', 'qa', 'real-pre-cleanup-plan.cjs');
    const stdout = execFileSync('node', [helper, '--run-id', ctx.runId, '--evidence-dir', evidenceDir], {
      cwd: root,
      encoding: 'utf8'
    });
    writeFileSync(planJsonPath, stdout, 'utf8');
    const plan = JSON.parse(stdout) as Record<string, unknown>;

    const executeSql = String((plan as { executeSql?: string }).executeSql || '');
    writeFileSync(planSqlPath, executeSql, 'utf8');

    const violations: string[] = [];
    if ((plan as { mode?: string }).mode !== 'PlanOnly') {
      violations.push(`mode 不是 PlanOnly，实际=${(plan as { mode?: string }).mode}`);
    }
    if (!/QA[A-Za-z0-9_-]+/i.test(String((plan as { runId?: string }).runId || ''))) {
      violations.push('plan 中没有 runId，无法做范围限定');
    }
    if (!new RegExp(`%${ctx.runId}%`, 'i').test(executeSql) && !executeSql.includes(ctx.runId)) {
      violations.push(`cleanup SQL 中没有 runId=${ctx.runId} 过滤，存在范围扩散风险`);
    }
    for (const pattern of PROHIBITED_PATTERNS) {
      if (pattern.test(executeSql)) {
        violations.push(`cleanup SQL 命中危险模式 ${pattern}`);
      }
    }
    // 任何 DELETE FROM ... WHERE 但没有引用 runId 的语句也视为不安全。
    const dangerousDeletes = executeSql.split(/;\s*/).filter((stmt) => {
      const trimmed = stmt.trim();
      if (!/^DELETE\s+FROM/i.test(trimmed)) return false;
      if (/RunId/i.test(trimmed)) return false;
      if (trimmed.includes(ctx.runId)) return false;
      // 允许通过子查询或 ILIKE %runId% 过滤，runId 已注入字符串，所以只要包含 runId 就算安全。
      return true;
    });
    if (dangerousDeletes.length) {
      violations.push(`存在 ${dangerousDeletes.length} 条无 runId 限定的 DELETE 语句`);
    }

    setDetail(ctx, 'cleanupPlanJsonPath', planJsonPath);
    setDetail(ctx, 'cleanupPlanSqlPath', planSqlPath);
    setDetail(ctx, 'mode', (plan as { mode?: string }).mode || '');
    setDetail(ctx, 'protectedTables', (plan as { protectedTables?: string[] }).protectedTables || []);
    setDetail(ctx, 'cleanupPlanStatus', violations.length ? 'CLEANUP_PLAN_UNSAFE' : 'CLEANUP_PLAN_SAFE');
    setDetail(ctx, 'violations', violations);

    if (violations.length) {
      markFail(ctx, `清理计划不安全：${violations.join(' | ')}`);
    } else {
      // 计划安全，但因为本次没有人工审核 + 执行，整体进入 PASS_NEEDS_CLEANUP。
      markPassNeedsCleanup(ctx);
    }
  } catch (error) {
    markFail(ctx, `生成清理计划失败：${error instanceof Error ? error.message : String(error)}`);
    setDetail(ctx, 'cleanupPlanStatus', 'PLAN_GENERATION_FAILED');
  } finally {
    persistStepSummary(ctx);
    await testInfo.attach('step-summary.json', {
      body: JSON.stringify(ctx.summary, null, 2),
      contentType: 'application/json'
    });
  }

  expect(
    ctx.summary.conclusion === 'FAIL' ? ctx.summary.failures.join('; ') : 'OK',
    `real-pre 36 清理计划 conclusion=${ctx.summary.conclusion}`
  ).toBe('OK');
});
