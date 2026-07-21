import { randomUUID } from "node:crypto";
import { resolve, posix, win32 } from "node:path";

import type {
  EvidenceGitSnapshot,
  GitSnapshot,
  UnavailableGitSnapshot,
} from "./git.js";

export type HarnessEnvironment = "test" | "real-pre";
export type HarnessScope = "backend" | "frontend" | "full";

export interface RunContextInput {
  readonly repoRoot: string;
  readonly environment: HarnessEnvironment;
  readonly scope: HarnessScope;
  readonly reportKey: string;
  readonly startedAt?: Date;
  readonly runId?: string;
  readonly gitSnapshot?: GitSnapshot;
}

export interface RunContext {
  readonly repoRoot: string;
  readonly runId: string;
  readonly reportKey: string;
  readonly environment: HarnessEnvironment;
  readonly scope: HarnessScope;
  readonly startedAt: string;
  readonly startedAtShanghai: string;
  readonly rawDir: string;
  readonly stableJsonPath: string;
  readonly stableMarkdownPath: string;
  readonly git: EvidenceGitSnapshot;
}

const REPORT_KEY_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/u;
const RUN_ID_PATTERN = /^[a-z0-9]+(?:[._-][a-z0-9]+)*$/u;

export function validateReportKey(reportKey: string): void {
  if (
    reportKey.length === 0 ||
    reportKey.length > 64 ||
    !REPORT_KEY_PATTERN.test(reportKey)
  ) {
    throw new Error(
      "报告键 ReportKey 必须是 1-64 位小写字母、数字和单连字符分段，不得包含路径或穿越片段。",
    );
  }
}

function validateRunId(runId: string): void {
  if (runId.length === 0 || runId.length > 128 || !RUN_ID_PATTERN.test(runId)) {
    throw new Error("运行标识 runId 格式非法，已在写入证据前停止。");
  }
}

export function formatShanghaiTime(date: Date): string {
  if (Number.isNaN(date.getTime())) throw new Error("运行时间无效。");
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23",
  }).formatToParts(date);
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${values["year"]}-${values["month"]}-${values["day"]} ${values["hour"]}:${values["minute"]}:${values["second"]} Asia/Shanghai`;
}

function outsideRepository(relativePath: string, style: typeof posix | typeof win32): boolean {
  return (
    relativePath === ".." ||
    relativePath.startsWith(`..${style.sep}`) ||
    style.isAbsolute(relativePath)
  );
}

export function toRepositoryRelativePath(repoRoot: string, candidate: string): string {
  if (candidate.length === 0 || candidate.includes("\0")) {
    throw new Error("证据路径必须是仓库内的非空路径。");
  }

  let relativePath: string;
  if (win32.isAbsolute(candidate)) {
    if (!win32.isAbsolute(repoRoot)) {
      throw new Error("证据绝对路径必须位于仓库内，已拒绝外部路径。");
    }
    relativePath = win32.relative(win32.normalize(repoRoot), win32.normalize(candidate));
    if (outsideRepository(relativePath, win32)) {
      throw new Error("证据绝对路径必须位于仓库内，已拒绝外部路径。");
    }
  } else if (posix.isAbsolute(candidate)) {
    if (!posix.isAbsolute(repoRoot)) {
      throw new Error("证据绝对路径必须位于仓库内，已拒绝外部路径。");
    }
    relativePath = posix.relative(posix.normalize(repoRoot), posix.normalize(candidate));
    if (outsideRepository(relativePath, posix)) {
      throw new Error("证据绝对路径必须位于仓库内，已拒绝外部路径。");
    }
  } else {
    relativePath = posix.normalize(candidate.replaceAll("\\", "/"));
    if (outsideRepository(relativePath, posix)) {
      throw new Error("证据路径必须位于仓库内，已拒绝路径穿越。");
    }
  }

  const normalized = relativePath.replaceAll("\\", "/").replace(/^\.\//u, "");
  if (normalized.length === 0 || normalized === ".") {
    throw new Error("证据路径必须指向仓库内的具体文件。");
  }
  return normalized;
}

function defaultRunId(startedAt: Date): string {
  const timestamp = startedAt.toISOString().replace(/[-:.TZ]/gu, "").toLowerCase();
  return `run-${timestamp}-${randomUUID()}`;
}

export function unavailableGitSnapshot(): UnavailableGitSnapshot {
  return Object.freeze({
    headSha: null,
    branch: null,
    clean: null,
    changedFiles: Object.freeze([]) as readonly [],
    identity: Object.freeze({
      kind: "UNAVAILABLE",
      reason: "NODE_GIT_BOUNDARY",
    }),
  });
}

export function createRunContext(input: RunContextInput): RunContext {
  validateReportKey(input.reportKey);
  if (!(["test", "real-pre"] as const).includes(input.environment)) {
    throw new Error("运行环境只支持 test 或 real-pre。");
  }
  if (!(["backend", "frontend", "full"] as const).includes(input.scope)) {
    throw new Error("验证范围只支持 backend、frontend 或 full。");
  }
  const startedAt = input.startedAt ?? new Date();
  if (Number.isNaN(startedAt.getTime())) throw new Error("运行开始时间无效。");
  const runId = input.runId ?? defaultRunId(startedAt);
  validateRunId(runId);
  const repoRoot = resolve(input.repoRoot);
  const rawDir = `runtime/qa/out/${runId}`;
  return {
    repoRoot,
    runId,
    reportKey: input.reportKey,
    environment: input.environment,
    scope: input.scope,
    startedAt: startedAt.toISOString(),
    startedAtShanghai: formatShanghaiTime(startedAt),
    rawDir,
    stableJsonPath: `runtime/qa/out/latest-${input.reportKey}.json`,
    stableMarkdownPath: `runtime/qa/out/latest-${input.reportKey}.md`,
    git: input.gitSnapshot ?? unavailableGitSnapshot(),
  };
}
