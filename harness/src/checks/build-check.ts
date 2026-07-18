import type {
  ProcessOptions,
  ProcessResult,
  ProcessRunner,
} from "../core/process-runner.js";
import { runPlatformProcess } from "../core/platform-process-runner.js";
import { redactEvidenceText } from "../core/redact.js";
import { createCheckResult, type CheckResult } from "../core/result.js";
import {
  buildLogArtifactPath,
  writeBuildStepLog,
  type BuildLogWriter,
} from "./build-log.js";

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

const VALID_PROCESS_SIGNALS: ReadonlySet<NodeJS.Signals> = new Set([
  "SIGABRT", "SIGALRM", "SIGBREAK", "SIGBUS", "SIGCHLD", "SIGCONT",
  "SIGFPE", "SIGHUP", "SIGILL", "SIGINT", "SIGIO", "SIGIOT", "SIGKILL",
  "SIGPIPE", "SIGPOLL", "SIGPROF", "SIGPWR", "SIGQUIT", "SIGSEGV",
  "SIGSTKFLT", "SIGSTOP", "SIGSYS", "SIGTERM", "SIGTRAP", "SIGTSTP",
  "SIGTTIN", "SIGTTOU", "SIGURG", "SIGUSR1", "SIGUSR2", "SIGVTALRM",
  "SIGWINCH", "SIGXCPU", "SIGXFSZ",
]);

function safeErrorMessage(error: unknown, secrets: readonly string[]): string {
  let message: string;
  try {
    message = error instanceof Error ? error.message : String(error);
  } catch {
    message = "异常详情无法安全读取";
  }
  return redactEvidenceText(message.slice(0, 2_000), secrets)
    .replace(/[\u0000-\u001f\u007f-\u009f]/gu, " ")
    .replace(/\s+/gu, " ")
    .trim()
    .slice(0, 500) || "未知异常";
}

function normalizedNullableText(
  value: unknown,
  field: string,
  secrets: readonly string[],
): string | null {
  if (value === null) return null;
  if (typeof value !== "string") {
    throw new Error(`字段 ${field} 必须是字符串或 null`);
  }
  return safeErrorMessage(value, secrets);
}

function normalizeProcessResult(
  value: unknown,
  secrets: readonly string[],
): ProcessResult {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new Error("ProcessResult 必须是对象");
  }
  const candidate = value as Record<string, unknown>;
  const commandDisplay = candidate["commandDisplay"];
  const exitCode = candidate["exitCode"];
  const signal = candidate["signal"];
  const timedOut = candidate["timedOut"];
  const durationMs = candidate["durationMs"];
  const stdout = candidate["stdout"];
  const stderr = candidate["stderr"];
  const success = candidate["success"];
  if (typeof commandDisplay !== "string" || commandDisplay.length === 0) {
    throw new Error("字段 commandDisplay 必须是非空字符串");
  }
  if (exitCode !== null && (!Number.isInteger(exitCode) || (exitCode as number) < 0)) {
    throw new Error("字段 exitCode 必须是非负整数或 null");
  }
  if (signal !== null && (typeof signal !== "string" ||
    !VALID_PROCESS_SIGNALS.has(signal as NodeJS.Signals))) {
    throw new Error("字段 signal 必须是合法 Node.js 信号或 null");
  }
  if (typeof signal === "string" && redactEvidenceText(signal, secrets) !== signal) {
    throw new Error("字段 signal 与敏感值冲突，无法保留可信运行时契约");
  }
  if (typeof timedOut !== "boolean") {
    throw new Error("字段 timedOut 必须是布尔值");
  }
  if (typeof durationMs !== "number" || !Number.isFinite(durationMs) || durationMs < 0) {
    throw new Error("字段 durationMs 必须是非负有限数值");
  }
  if (typeof stdout !== "string" || typeof stderr !== "string") {
    throw new Error("字段 stdout 和 stderr 必须是字符串");
  }
  if (typeof success !== "boolean") {
    throw new Error("字段 success 必须是布尔值");
  }
  const rootCause = normalizedNullableText(candidate["rootCause"], "rootCause", secrets);
  const safeRetry = normalizedNullableText(candidate["safeRetry"], "safeRetry", secrets);
  const stopCondition = normalizedNullableText(
    candidate["stopCondition"],
    "stopCondition",
    secrets,
  );
  if (success && (exitCode !== 0 || signal !== null || timedOut ||
    rootCause !== null || safeRetry !== null || stopCondition !== null)) {
    throw new Error("成功 ProcessResult 的退出状态或恢复字段不一致");
  }
  if (!success && (rootCause === null || safeRetry === null || stopCondition === null)) {
    throw new Error("失败 ProcessResult 必须包含完整恢复字段");
  }
  return Object.freeze({
    commandDisplay: redactEvidenceText(commandDisplay, secrets),
    exitCode: exitCode as number | null,
    signal: signal as NodeJS.Signals | null,
    timedOut,
    durationMs,
    stdout: redactEvidenceText(stdout, secrets),
    stderr: redactEvidenceText(stderr, secrets),
    success,
    rootCause,
    safeRetry,
    stopCondition,
  });
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
      `${definition.subject}构建检查配置无效：${safeErrorMessage(error, secrets)}。`,
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
        `${definition.subject}步骤 ${step.stepId} 执行器异常：${safeErrorMessage(error, secrets)}。`,
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
        `${definition.subject}步骤 ${step.stepId} 的结果不符合契约：${safeErrorMessage(error, secrets)}。`,
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
        `${definition.subject}步骤 ${step.stepId} 的日志写入失败：${safeErrorMessage(error, secrets)}。`,
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
