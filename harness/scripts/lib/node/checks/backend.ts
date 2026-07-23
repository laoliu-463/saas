import {
  runBuildCheck,
  type BuildCheckDefinition,
  type BuildCommandStep,
  type RunBuildCheckOptions,
} from "./build-check.js";

export type {
  BuildCommandStep,
  BuildProcessRunner,
  RunBuildCheckOptions as RunBackendCheckOptions,
} from "./build-check.js";
export type { BuildLogInput, BuildLogWriter } from "./build-log.js";

const BACKEND_COMMAND_PLAN: readonly BuildCommandStep[] = Object.freeze([
  Object.freeze({
    stepId: "backend-package",
    command: "mvn",
    args: Object.freeze(["-f", "backend/pom.xml", "package"]),
    timeoutMs: 30 * 60_000,
  }),
]);

const BACKEND_DEFINITION: BuildCheckDefinition = Object.freeze({
  checkId: "backend",
  title: "验证后端构建",
  subject: "后端",
  successSummary: "后端测试与打包已由一次 Maven package 完成。",
  dryRunSummary: "dry-run 模式仅展示后端命令计划，未执行构建。",
  dryRunAction: "移除 dry-run 参数后执行后端验证。",
  plan: BACKEND_COMMAND_PLAN,
});

export function buildBackendCommandPlan(): readonly BuildCommandStep[] {
  return BACKEND_COMMAND_PLAN;
}

export function runBackendCheck(options: RunBuildCheckOptions) {
  return runBuildCheck(BACKEND_DEFINITION, options);
}
