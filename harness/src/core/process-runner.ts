import { spawn, type ChildProcessByStdio } from "node:child_process";
import type { Readable } from "node:stream";

import { redactEvidenceText } from "./redact.js";

export const BUSINESS_VALIDATION_PURPOSE = "BUSINESS_VALIDATION" as const;
export const DEFAULT_PROCESS_TIMEOUT_MS = 60_000;
export const MAX_PROCESS_TIMEOUT_MS = 30 * 60_000;
const EXITED_ROOT_WATCHDOG_MS = 1_000;
const ACTIVE_TREE_WATCHDOG_MS = 2_500;
const TERMINATION_COMMAND_TIMEOUT_MS = 2_000;
const POSIX_TERM_GRACE_MS = 150;

export class ProcessConfigurationError extends Error {
  override readonly name = "ProcessConfigurationError";
}

interface SharedProcessOptions {
  readonly cwd?: string;
  readonly timeoutMs?: number;
  readonly env?: Readonly<Record<string, string | undefined>>;
  readonly secrets?: readonly string[];
}

export interface ProcessOptions extends SharedProcessOptions {
  readonly command: string;
  readonly args: readonly string[];
}

export interface BusinessValidationShellOptions extends SharedProcessOptions {
  readonly purpose: typeof BUSINESS_VALIDATION_PURPOSE;
  readonly command: string;
}

export interface ProcessResult {
  readonly commandDisplay: string;
  readonly exitCode: number | null;
  readonly signal: NodeJS.Signals | null;
  readonly timedOut: boolean;
  readonly durationMs: number;
  readonly stdout: string;
  readonly stderr: string;
  readonly success: boolean;
  readonly rootCause: string | null;
  readonly safeRetry: string | null;
  readonly stopCondition: string | null;
}

interface ExecutionRequest {
  readonly command: string;
  readonly args: readonly string[];
  readonly shell: boolean;
  readonly cwd: string;
  readonly timeoutMs: number;
  readonly env: NodeJS.ProcessEnv;
  readonly secrets: readonly string[];
}

interface Recovery {
  readonly rootCause: string;
  readonly safeRetry: string;
  readonly stopCondition: string;
}

type TerminationStatus =
  | "NOT_REQUIRED"
  | "PENDING"
  | "CONFIRMED"
  | "UNCONFIRMED";

interface TreeTerminationOutcome {
  readonly status: "CONFIRMED" | "UNCONFIRMED";
  readonly reason:
    | "ROOT_ALREADY_EXITED"
    | "POSIX_GROUP_STOPPED"
    | "POSIX_SIGNAL_FAILED"
    | "TASKKILL_SUCCEEDED"
    | "TASKKILL_NONZERO"
    | "TASKKILL_TIMEOUT"
    | "TASKKILL_START_FAILED";
}

type TerminationReason =
  | TreeTerminationOutcome["reason"]
  | "CHILD_ERROR_AFTER_TIMEOUT"
  | "WATCHDOG_EXPIRED";

function configurationError(message: string): ProcessConfigurationError {
  return new ProcessConfigurationError(`进程执行配置错误：${message}`);
}

function validateSharedOptions(options: SharedProcessOptions): void {
  const timeoutMs = options.timeoutMs ?? DEFAULT_PROCESS_TIMEOUT_MS;
  if (!Number.isFinite(timeoutMs) || timeoutMs <= 0) {
    throw configurationError("超时时间必须大于 0 毫秒。");
  }
  if (timeoutMs > MAX_PROCESS_TIMEOUT_MS) {
    throw configurationError(`超时时间不能超过 ${MAX_PROCESS_TIMEOUT_MS} 毫秒。`);
  }
  if (options.cwd !== undefined && options.cwd.trim().length === 0) {
    throw configurationError("工作目录不能为空。");
  }
}

function normalizeOutput(value: string, secrets: readonly string[]): string {
  const normalized = value
    .replace(/\r\n|\r/gu, "\n")
    .split("\n")
    .map((line) => line.replace(/[\t ]+$/gu, ""))
    .join("\n")
    .replace(/\n+$/gu, "");
  return redactEvidenceText(normalized, secrets);
}

function buildCommandDisplay(
  command: string,
  args: readonly string[],
  secrets: readonly string[],
): string {
  return [command, ...args]
    .map((part) => JSON.stringify(redactEvidenceText(part, secrets)))
    .join(" ");
}

function recoveryFor(
  exitCode: number | null,
  signal: NodeJS.Signals | null,
  timedOut: boolean,
  launchErrorCode: string | null,
  terminationStatus: TerminationStatus,
  terminationReason: TerminationReason | null,
): Recovery | null {
  if (timedOut) {
    const terminationConfirmed = terminationStatus === "CONFIRMED";
    return {
      rootCause: terminationConfirmed
        ? "命令执行超时，进程树终止已确认。"
        : `命令执行超时，进程树终止未确认（${terminationReasonLabel(terminationReason)}），watchdog 已收敛输出管道。`,
      safeRetry: terminationConfirmed
        ? "确认任务可重复执行且无残留副作用后，可调整合理超时并重试一次。"
        : "先检查并清理残留进程及延迟副作用，确认环境安全后才可重试。",
      stopCondition: terminationConfirmed
        ? "若再次超时，停止重试并排查任务耗时或终止响应。"
        : "在进程树终止得到确认前停止重试，避免累积孤儿进程。",
    };
  }
  if (launchErrorCode !== null) {
    return {
      rootCause: `命令无法启动（${launchErrorCode}），请确认可执行文件、工作目录与权限。`,
      safeRetry: "确认命令路径和工作目录有效后，可在相同环境中重试一次。",
      stopCondition: "若命令仍无法启动，停止重试并修复工具链或权限配置。",
    };
  }
  if (signal !== null) {
    return {
      rootCause: `命令被信号 ${signal} 终止。`,
      safeRetry: "确认终止信号来源且任务可重复执行后，可重试一次。",
      stopCondition: "若再次收到信号或无法确认数据一致性，停止重试。",
    };
  }
  if (exitCode !== 0) {
    return {
      rootCause: `命令以退出码 ${exitCode ?? "未知"} 结束。`,
      safeRetry: "根据已脱敏的标准错误修复直接原因后，可重试一次。",
      stopCondition: "若退出码和错误摘要未变化，停止重复执行并升级排查。",
    };
  }
  return null;
}

function terminationReasonLabel(reason: TerminationReason | null): string {
  switch (reason) {
    case "ROOT_ALREADY_EXITED":
      return "根进程已提前退出";
    case "POSIX_SIGNAL_FAILED":
      return "POSIX 进程组信号失败";
    case "TASKKILL_NONZERO":
      return "taskkill 返回非零退出码";
    case "TASKKILL_TIMEOUT":
      return "taskkill 等待超时";
    case "TASKKILL_START_FAILED":
      return "taskkill 无法启动";
    case "CHILD_ERROR_AFTER_TIMEOUT":
      return "超时后的终止操作发生错误";
    case "WATCHDOG_EXPIRED":
      return "终止操作超过最终宽限";
    case "POSIX_GROUP_STOPPED":
    case "TASKKILL_SUCCEEDED":
      return "终止操作已完成";
    default:
      return "终止状态未知";
  }
}

function redactRecovery(
  recovery: Recovery | null,
  secrets: readonly string[],
): Recovery | null {
  if (recovery === null) return null;
  return {
    rootCause: redactEvidenceText(recovery.rootCause, secrets),
    safeRetry: redactEvidenceText(recovery.safeRetry, secrets),
    stopCondition: redactEvidenceText(recovery.stopCondition, secrets),
  };
}

function wait(milliseconds: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

function processGroupExists(pid: number): boolean {
  try {
    process.kill(-pid, 0);
    return true;
  } catch (error) {
    return (error as NodeJS.ErrnoException).code !== "ESRCH";
  }
}

async function terminatePosixProcessTree(pid: number): Promise<TreeTerminationOutcome> {
  try {
    process.kill(-pid, "SIGTERM");
  } catch (error) {
    return {
      status: (error as NodeJS.ErrnoException).code === "ESRCH"
        ? "CONFIRMED"
        : "UNCONFIRMED",
      reason: (error as NodeJS.ErrnoException).code === "ESRCH"
        ? "POSIX_GROUP_STOPPED"
        : "POSIX_SIGNAL_FAILED",
    };
  }

  await wait(POSIX_TERM_GRACE_MS);
  if (!processGroupExists(pid)) {
    return { status: "CONFIRMED", reason: "POSIX_GROUP_STOPPED" };
  }
  try {
    process.kill(-pid, "SIGKILL");
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code !== "ESRCH") {
      return { status: "UNCONFIRMED", reason: "POSIX_SIGNAL_FAILED" };
    }
  }
  await wait(50);
  return processGroupExists(pid)
    ? { status: "UNCONFIRMED", reason: "POSIX_SIGNAL_FAILED" }
    : { status: "CONFIRMED", reason: "POSIX_GROUP_STOPPED" };
}

function terminateWindowsProcessTree(pid: number): Promise<TreeTerminationOutcome> {
  return new Promise((resolve) => {
    let settled = false;
    let commandTimeout: NodeJS.Timeout | undefined;
    const finish = (
      status: TreeTerminationOutcome["status"],
      reason: TreeTerminationOutcome["reason"],
    ): void => {
      if (settled) return;
      settled = true;
      if (commandTimeout !== undefined) clearTimeout(commandTimeout);
      resolve({ status, reason });
    };

    try {
      const killer = spawn("taskkill", ["/PID", String(pid), "/T", "/F"], {
        shell: false,
        stdio: "ignore",
        windowsHide: true,
      });
      commandTimeout = setTimeout(() => {
        killer.kill("SIGKILL");
        finish("UNCONFIRMED", "TASKKILL_TIMEOUT");
      }, TERMINATION_COMMAND_TIMEOUT_MS);
      killer.once("error", () => finish("UNCONFIRMED", "TASKKILL_START_FAILED"));
      killer.once("close", (exitCode) => {
        finish(
          exitCode === 0 ? "CONFIRMED" : "UNCONFIRMED",
          exitCode === 0 ? "TASKKILL_SUCCEEDED" : "TASKKILL_NONZERO",
        );
      });
    } catch {
      finish("UNCONFIRMED", "TASKKILL_START_FAILED");
    }
  });
}

function terminateProcessTree(
  child: ChildProcessByStdio<null, Readable, Readable>,
): Promise<TreeTerminationOutcome> {
  const pid = child.pid;
  if (
    pid === undefined ||
    child.exitCode !== null ||
    child.signalCode !== null
  ) {
    return Promise.resolve({
      status: "UNCONFIRMED",
      reason: "ROOT_ALREADY_EXITED",
    });
  }
  return process.platform === "win32"
    ? terminateWindowsProcessTree(pid)
    : terminatePosixProcessTree(pid);
}

function forceKillAtWatchdog(
  child: ChildProcessByStdio<null, Readable, Readable>,
): void {
  if (process.platform !== "win32" && child.pid !== undefined) {
    try {
      process.kill(-child.pid, "SIGKILL");
      return;
    } catch {
      // 进程组可能已退出；继续尝试直接子进程作为最终收敛。
    }
  }
  if (child.exitCode === null && child.signalCode === null) {
    child.kill("SIGKILL");
  }
}

function execute(request: ExecutionRequest): Promise<ProcessResult> {
  const startedAt = Date.now();
  const stdoutChunks: Buffer[] = [];
  const stderrChunks: Buffer[] = [];
  const commandDisplay = buildCommandDisplay(
    request.command,
    request.args,
    request.secrets,
  );

  return new Promise((resolve) => {
    let child: ChildProcessByStdio<null, Readable, Readable> | undefined;
    let timedOut = false;
    let launchErrorCode: string | null = null;
    let terminationStatus: TerminationStatus = "NOT_REQUIRED";
    let terminationReason: TerminationReason | null = null;
    let observedExitCode: number | null = null;
    let observedSignal: NodeJS.Signals | null = null;
    let settled = false;
    let timeout: NodeJS.Timeout | undefined;
    let watchdog: NodeJS.Timeout | undefined;

    function createResult(
      exitCode: number | null,
      signal: NodeJS.Signals | null,
      didTimeOut: boolean,
      errorCode: string | null,
      stdout: string,
      stderr: string,
    ): ProcessResult {
      const recovery = redactRecovery(
        recoveryFor(
          exitCode,
          signal,
          didTimeOut,
          errorCode,
          terminationStatus === "PENDING" ? "UNCONFIRMED" : terminationStatus,
          terminationReason,
        ),
        request.secrets,
      );
      return {
        commandDisplay,
        exitCode,
        signal,
        timedOut: didTimeOut,
        durationMs: Math.max(0, Date.now() - startedAt),
        stdout: normalizeOutput(stdout, request.secrets),
        stderr: normalizeOutput(stderr, request.secrets),
        success: recovery === null,
        rootCause: recovery?.rootCause ?? null,
        safeRetry: recovery?.safeRetry ?? null,
        stopCondition: recovery?.stopCondition ?? null,
      };
    }

    function settleOnce(
      exitCode: number | null,
      signal: NodeJS.Signals | null,
      errorCode: string | null = launchErrorCode,
    ): void {
      if (settled) return;
      settled = true;
      if (timeout !== undefined) clearTimeout(timeout);
      if (watchdog !== undefined) clearTimeout(watchdog);
      resolve(
        createResult(
          errorCode === null ? exitCode : null,
          signal,
          timedOut,
          errorCode,
          Buffer.concat(stdoutChunks).toString("utf8"),
          Buffer.concat(stderrChunks).toString("utf8"),
        ),
      );
    }

    try {
      child = spawn(request.command, request.args, {
        cwd: request.cwd,
        env: request.env,
        shell: request.shell,
        stdio: ["ignore", "pipe", "pipe"],
        windowsHide: true,
        detached: process.platform !== "win32",
      });
    } catch {
      settleOnce(null, null, "SPAWN_ERROR");
      return;
    }

    timeout = setTimeout(() => {
      timedOut = true;
      terminationStatus = "PENDING";
      const watchdogDelay =
        child?.exitCode !== null || child?.signalCode !== null
          ? EXITED_ROOT_WATCHDOG_MS
          : ACTIVE_TREE_WATCHDOG_MS;
      watchdog = setTimeout(() => {
        if (settled || child === undefined) return;
        forceKillAtWatchdog(child);
        child.stdout.destroy();
        child.stderr.destroy();
        if (terminationStatus !== "CONFIRMED") {
          terminationStatus = "UNCONFIRMED";
          terminationReason ??= "WATCHDOG_EXPIRED";
        }
        settleOnce(
          observedExitCode ?? child.exitCode,
          observedSignal ?? child.signalCode,
        );
      }, watchdogDelay);

      void terminateProcessTree(child).then((outcome) => {
        if (settled || child === undefined) return;
        terminationStatus = outcome.status;
        terminationReason = outcome.reason;
        if (outcome.status === "CONFIRMED") {
          child.stdout.destroy();
          child.stderr.destroy();
          settleOnce(
            observedExitCode ?? child.exitCode,
            observedSignal ?? child.signalCode,
          );
        }
      });
    }, request.timeoutMs);

    child.stdout.on("data", (chunk: Buffer) => stdoutChunks.push(chunk));
    child.stderr.on("data", (chunk: Buffer) => stderrChunks.push(chunk));
    child.on("error", (error: NodeJS.ErrnoException) => {
      if (timedOut) {
        terminationStatus = "UNCONFIRMED";
        terminationReason = "CHILD_ERROR_AFTER_TIMEOUT";
        return;
      }
      launchErrorCode = error.code ?? "SPAWN_ERROR";
      child?.stdout.destroy();
      child?.stderr.destroy();
      settleOnce(null, null, launchErrorCode);
    });
    child.on("close", (exitCode, signal) => {
      observedExitCode = exitCode;
      observedSignal = signal;
      if (!timedOut || terminationStatus === "CONFIRMED") {
        settleOnce(exitCode, signal);
      }
    });
  });
}

function executionRequest(
  options: ProcessOptions,
  shell: boolean,
): ExecutionRequest {
  validateSharedOptions(options);
  if (options.command.trim().length === 0) throw configurationError("命令不能为空。");
  if (!Array.isArray(options.args) || options.args.some((arg) => typeof arg !== "string")) {
    throw configurationError("参数必须是字符串数组。");
  }
  return {
    command: options.command,
    args: [...options.args],
    shell,
    cwd: options.cwd ?? process.cwd(),
    timeoutMs: options.timeoutMs ?? DEFAULT_PROCESS_TIMEOUT_MS,
    env: { ...process.env, ...options.env },
    secrets: options.secrets ?? [],
  };
}

export function runProcess(options: ProcessOptions): Promise<ProcessResult> {
  if (Object.hasOwn(options, "shell")) {
    throw configurationError("标准入口禁止 Shell，请使用显式业务验证入口。");
  }
  return execute(executionRequest(options, false));
}

export function runBusinessValidationShell(
  options: BusinessValidationShellOptions,
): Promise<ProcessResult> {
  if (options.purpose !== BUSINESS_VALIDATION_PURPOSE) {
    throw configurationError("Shell 仅允许 BUSINESS_VALIDATION 业务验证用途。");
  }
  const request = executionRequest({ ...options, args: [] }, true);
  return execute(request);
}
