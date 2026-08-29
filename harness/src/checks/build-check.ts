import type {
  ProcessOptions,
  ProcessResult,
  ProcessRunner,
} from "../core/process-runner.js";
import { runPlatformProcess } from "../core/platform-process-runner.js";
import { createCheckResult, type CheckResult } from "../core/result.js";
import {
  buildLogArtifactPath,
  writeBuildStepLog,
  type BuildLogWriter,
} from "./build-log.js";
import { normalizeProcessResult, safeCheckText } from "./process-result.js";

export interface BuildCommandStep {
  readonly stepId: string;
  readonly command: string;
  readonly args: readonly string[];
  readonly timeoutMs: number;
}

export type BuildProcessRunner = ProcessRunner;

export interface RunBuildCheckOptions {
  readonly repoRoot: string;
  readonly rawDir: string;
  readonly processRunner?: BuildProcessRunner;
  readonly logWriter?: BuildLogWriter;
  readonly dryRun?: boolean;
  readonly secrets?: readonly string[];
}

export interface BuildCheckDefinition {
  readonly checkId: "backend" | "frontend";
  readonly title: string;
  readonly subject: string;
  readonly successSummary: string;
  readonly dryRunSummary: string;
  readonly dryRunAction: string;
  readonly plan: readonly BuildCommandStep[];
}

function failure(
  definition: BuildCheckDefinition,
  summary: string,
  nextActions: readonly string[],
  artifacts: readonly string[],
): CheckResult {
  return createCheckResult({
    checkId: definition.checkId,
    title: definition.title,
    status: "FAIL",
    blocking: true,
    summary,
    nextActions,
    artifacts,
  });
}

function commandPlanActions(plan: readonly BuildCommandStep[]): string[] {
  return plan.map((step) =>
    `计划命令：${[step.command, ...step.args].join(" ")}`
  );
}

export async function runBuildCheck(
  definition: BuildCheckDefinition,
  options: RunBuildCheckOptions,
): Promise<CheckResult> {
  const secrets = Object.freeze([...(options.secrets ?? [])]);
  let expectedArtifacts: readonly string[];
  try {
    expectedArtifacts = definition.plan.map((step) =>
      buildLogArtifactPath(options.rawDir, step.stepId)
    );
  } catch (error) {
    return failure(
      definition,
      `${definition.subject}构建检查配置无效：${safeCheckText(error, secrets)}。`,
      ["修复 rawDir 或固定步骤配置后重新运行验证。"],
      [],
    );
  }

  if (options.dryRun === true) {
    return createCheckResult({
      checkId: definition.checkId,
      title: definition.title,
      status: "SKIPPED",
      blocking: true,
      summary: definition.dryRunSummary,
      nextActions: [...commandPlanActions(definition.plan), definition.dryRunAction],
      artifacts: [],
    });
  }

  const runner = options.processRunner ?? runPlatformProcess;
  const logWriter = options.logWriter ?? writeBuildStepLog;
  const artifacts: string[] = [];

  for (const [index, step] of definition.plan.entries()) {
    let rawResult: ProcessResult;
    try {
      const processOptions: ProcessOptions = {
        command: step.command,
        args: [...step.args],
        cwd: options.repoRoot,
        timeoutMs: step.timeoutMs,
        secrets: [...secrets],
      };
      rawResult = await runner(processOptions);
    } catch (error) {
      return failure(
        definition,
        `${definition.subject}步骤 ${step.stepId} 执行器异常：${safeCheckText(error, secrets)}。`,
        [
          "安全重试：确认工具链、工作目录和权限后，可重新运行一次。",
          "停止条件：异常原因未变化或执行副作用无法确认时停止重试。",
        ],
        artifacts,
      );
    }

    let result: ProcessResult;
    try {
      result = normalizeProcessResult(rawResult, secrets);
    } catch (error) {
      return failure(
        definition,
        `${definition.subject}步骤 ${step.stepId} 的结果不符合契约：${safeCheckText(error, secrets)}。`,
        [
          "安全重试：修复 ProcessRunner 适配或结果契约后，可重新运行一次。",
          "停止条件：执行结果无法被可信解析时停止验证，不得写入成功证据。",
        ],
        artifacts,
      );
    }

    try {
      const artifact = await logWriter({
        repoRoot: options.repoRoot,
        rawDir: options.rawDir,
        stepId: step.stepId,
        result,
      });
      if (artifact !== expectedArtifacts[index]) {
        throw new Error("artifact 路径不符合固定计划");
      }
      artifacts.push(artifact);
    } catch (error) {
      return failure(
        definition,
        `${definition.subject}步骤 ${step.stepId} 的日志写入失败：${safeCheckText(error, secrets)}。`,
        [
          "安全重试：确认 rawDir、磁盘空间和文件权限后，可重新运行一次。",
          "停止条件：日志仍无法独占写入时停止验证，不得把未留证据的构建标记为通过。",
        ],
        artifacts,
      );
    }

    if (!result.success) {
      return failure(
        definition,
        `${definition.subject}步骤 ${step.stepId} 失败。根因：${result.rootCause ?? "命令执行失败，未返回根因。"}`,
        [
          `安全重试：${result.safeRetry ?? "确认失败原因和幂等性后再重试。"}`,
          `停止条件：${result.stopCondition ?? "无法确认安全边界时停止重试。"}`,
        ],
        artifacts,
      );
    }
  }

  return createCheckResult({
    checkId: definition.checkId,
    title: definition.title,
    status: "PASS",
    blocking: true,
    summary: definition.successSummary,
    nextActions: [],
    artifacts,
  });
}
