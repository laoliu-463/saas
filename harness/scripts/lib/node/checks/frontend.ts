import {
  runBuildCheck,
  type BuildCheckDefinition,
  type BuildCommandStep,
  type RunBuildCheckOptions,
} from "./build-check.js";

export type { RunBuildCheckOptions as RunFrontendCheckOptions } from "./build-check.js";

const FRONTEND_COMMAND_PLAN: readonly BuildCommandStep[] = Object.freeze([
  Object.freeze({
    stepId: "frontend-install",
    command: "npm",
    args: Object.freeze(["--prefix", "frontend", "ci"]),
    timeoutMs: 10 * 60_000,
  }),
  Object.freeze({
    stepId: "frontend-typecheck",
    command: "npm",
    args: Object.freeze(["--prefix", "frontend", "run", "typecheck"]),
    timeoutMs: 10 * 60_000,
  }),
  Object.freeze({
    stepId: "frontend-test",
    command: "npm",
    args: Object.freeze(["--prefix", "frontend", "run", "test"]),
    timeoutMs: 15 * 60_000,
  }),
]);

const FRONTEND_DEFINITION: BuildCheckDefinition = Object.freeze({
  checkId: "frontend",
  title: "验证前端构建",
  subject: "前端",
  successSummary: "前端依赖安装、类型检查与测试均已通过；生产构建由 Docker 镜像阶段负责。",
  dryRunSummary: "dry-run 模式仅展示前端命令计划，未执行构建。",
  dryRunAction: "移除 dry-run 参数后执行前端验证。",
  plan: FRONTEND_COMMAND_PLAN,
});

export function buildFrontendCommandPlan(): readonly BuildCommandStep[] {
  return FRONTEND_COMMAND_PLAN;
}

export function runFrontendCheck(options: RunBuildCheckOptions) {
  return runBuildCheck(FRONTEND_DEFINITION, options);
}
