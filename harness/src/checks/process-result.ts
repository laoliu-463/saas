import type { ProcessResult } from "../core/process-runner.js";
import { redactEvidenceText } from "../core/redact.js";

const VALID_PROCESS_SIGNALS: ReadonlySet<NodeJS.Signals> = new Set([
  "SIGABRT", "SIGALRM", "SIGBREAK", "SIGBUS", "SIGCHLD", "SIGCONT",
  "SIGFPE", "SIGHUP", "SIGILL", "SIGINT", "SIGIO", "SIGIOT", "SIGKILL",
  "SIGPIPE", "SIGPOLL", "SIGPROF", "SIGPWR", "SIGQUIT", "SIGSEGV",
  "SIGSTKFLT", "SIGSTOP", "SIGSYS", "SIGTERM", "SIGTRAP", "SIGTSTP",
  "SIGTTIN", "SIGTTOU", "SIGURG", "SIGUSR1", "SIGUSR2", "SIGVTALRM",
  "SIGWINCH", "SIGXCPU", "SIGXFSZ",
]);

export function safeCheckText(
  value: unknown,
  secrets: readonly string[] = [],
  maximumLength = 500,
): string {
  let message: string;
  try {
    message = value instanceof Error ? value.message : String(value);
  } catch {
    message = "异常详情无法安全读取";
  }
  return redactEvidenceText(message.slice(0, 2_000), secrets)
    .replace(/[\u0000-\u001f\u007f-\u009f]/gu, " ")
    .replace(/\s+/gu, " ")
    .trim()
    .slice(0, maximumLength) || "未知异常";
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
  return safeCheckText(value, secrets);
}

export function normalizeProcessResult(
  value: unknown,
  secrets: readonly string[] = [],
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
