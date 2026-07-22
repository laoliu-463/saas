import type { ProcessResult } from "../core/process-runner.js";
import {
  safeLogArtifactPath,
  writeSafeLog,
  type SafeLogFileSystem,
} from "./safe-log.js";

export interface BuildLogInput {
  readonly repoRoot: string;
  readonly rawDir: string;
  readonly stepId: string;
  readonly result: ProcessResult;
}

export type BuildLogWriter = (input: BuildLogInput) => Promise<string>;
export type BuildLogFileSystem = SafeLogFileSystem;

const ALLOWED_STEP_IDS = new Set([
  "backend-test",
  "backend-package",
  "frontend-install",
  "frontend-typecheck",
  "frontend-test",
  "frontend-build",
  "docker-up",
  "docker-ps",
  "business-validation",
]);

export function buildLogArtifactPath(rawDir: string, stepId: string): string {
  return safeLogArtifactPath(rawDir, stepId, ALLOWED_STEP_IDS);
}

function serializeProcessResult(result: ProcessResult): string {
  return `${JSON.stringify({
    commandDisplay: result.commandDisplay,
    exitCode: result.exitCode,
    signal: result.signal,
    timedOut: result.timedOut,
    durationMs: result.durationMs,
    stdout: result.stdout,
    stderr: result.stderr,
    success: result.success,
    rootCause: result.rootCause,
    safeRetry: result.safeRetry,
    stopCondition: result.stopCondition,
  }, null, 2)}\n`;
}

export function writeBuildStepLog(
  input: BuildLogInput,
  fileSystem?: BuildLogFileSystem,
): Promise<string> {
  return writeSafeLog(
    {
      repoRoot: input.repoRoot,
      rawDir: input.rawDir,
      logId: input.stepId,
      content: serializeProcessResult(input.result),
    },
    ALLOWED_STEP_IDS,
    fileSystem,
  );
}
