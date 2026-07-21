import { spawnSync } from "node:child_process";
import { createHash, type Hash } from "node:crypto";
import {
  closeSync,
  constants as fsConstants,
  fstatSync,
  lstatSync,
  openSync,
  readlinkSync,
  readSync,
  realpathSync,
  type BigIntStats,
} from "node:fs";
import { isAbsolute, relative, resolve } from "node:path";

export interface CommitIdentity {
  readonly kind: "COMMIT";
  readonly commitSha: string;
}

export interface WorktreeIdentity {
  readonly kind: "WORKTREE";
  readonly headSha: string;
  readonly changedFiles: readonly string[];
  readonly patchFingerprint: string;
}

export type EvidenceIdentity = CommitIdentity | WorktreeIdentity;

export interface GitSnapshot {
  readonly headSha: string;
  readonly branch: string;
  readonly clean: boolean;
  readonly changedFiles: readonly string[];
  readonly identity: EvidenceIdentity;
}

export interface UnavailableGitSnapshot {
  readonly headSha: null;
  readonly branch: null;
  readonly clean: null;
  readonly changedFiles: readonly [];
  readonly identity: {
    readonly kind: "UNAVAILABLE";
    readonly reason: "NODE_GIT_BOUNDARY";
  };
}

export type EvidenceGitSnapshot = GitSnapshot | UnavailableGitSnapshot;

export interface GitFileOps {
  readonly lstat: (path: string) => BigIntStats;
  readonly open: (path: string, flags: number) => number;
  readonly fstat: (descriptor: number) => BigIntStats;
  readonly read: (
    descriptor: number,
    buffer: Buffer,
    offset: number,
    length: number,
    position: null,
  ) => number;
  readonly close: (descriptor: number) => void;
  readonly readlink: (path: string) => string;
  readonly realpath: (path: string) => string;
}

export interface CollectGitSnapshotOptions {
  readonly fileOps?: GitFileOps;
}

export interface OpenFlagConstants {
  readonly O_RDONLY: number;
  readonly O_NOFOLLOW?: number;
}

interface GitErrorRecovery {
  readonly safeRetry: string;
  readonly stopCondition: string;
}

export class GitSnapshotError extends Error {
  readonly code: string;
  readonly reason: string;
  readonly rootCause: string;
  readonly safeRetry: string;
  readonly stopCondition: string;
  readonly cleanupWarnings: readonly string[];

  constructor(
    code: string,
    reason = "Git 只读快照采集失败",
    cleanupWarnings: readonly string[] = [],
    recovery?: GitErrorRecovery,
  ) {
    const safeCode = /^[A-Z0-9_-]+$/u.test(code) ? code : "GIT_SNAPSHOT_ERROR";
    const rootCause = `${reason}（${safeCode}）。`;
    super(rootCause);
    this.name = "GitSnapshotError";
    this.code = safeCode;
    this.reason = reason;
    this.rootCause = rootCause;
    this.safeRetry = recovery?.safeRetry ??
      "确认仓库、Git 工具链和文件读取权限后，可重试一次。";
    this.stopCondition = recovery?.stopCondition ??
      "若错误再次出现，停止重试并修复仓库或工具链。";
    this.cleanupWarnings = [...cleanupWarnings];
  }
}

const SHA_PATTERN = /^(?:[a-f0-9]{40}|[a-f0-9]{64})$/u;
const MAX_GIT_OUTPUT = 128 * 1024 * 1024;
const FILE_HASH_BUFFER_SIZE = 64 * 1024;

const NODE_FILE_OPS: GitFileOps = {
  lstat: (path) => lstatSync(path, { bigint: true }),
  open: (path, flags) => openSync(path, flags),
  fstat: (descriptor) => fstatSync(descriptor, { bigint: true }),
  read: (descriptor, buffer, offset, length, position) =>
    readSync(descriptor, buffer, offset, length, position),
  close: (descriptor) => closeSync(descriptor),
  readlink: (path) => readlinkSync(path),
  realpath: (path) => realpathSync.native(path),
};

export function createUntrackedOpenFlags(
  platform: NodeJS.Platform = process.platform,
  constants: OpenFlagConstants = fsConstants,
): number {
  const noFollow = platform === "win32" ? undefined : constants.O_NOFOLLOW;
  return typeof noFollow === "number"
    ? constants.O_RDONLY | noFollow
    : constants.O_RDONLY;
}

function errorCode(error: unknown, fallback: string): string {
  return (error as NodeJS.ErrnoException).code ?? fallback;
}

function asGitSnapshotError(
  error: unknown,
  fallbackCode: string,
  reason: string,
): GitSnapshotError {
  return error instanceof GitSnapshotError
    ? error
    : new GitSnapshotError(errorCode(error, fallbackCode), reason);
}

function withCloseWarning(error: GitSnapshotError, closeError: unknown): GitSnapshotError {
  const warningCode = new GitSnapshotError(
    errorCode(closeError, "UNTRACKED_CLOSE_FAILED"),
  ).code;
  return new GitSnapshotError(error.code, error.reason, [
    ...error.cleanupWarnings,
    `文件描述符关闭失败（${warningCode}）。`,
  ], {
    safeRetry: error.safeRetry,
    stopCondition: error.stopCondition,
  });
}

function git(
  repoRoot: string,
  args: readonly string[],
  allowedStatuses: readonly number[] = [0],
): { readonly stdout: Buffer; readonly status: number } {
  const result = spawnSync("git", args, {
    cwd: repoRoot,
    encoding: "buffer",
    maxBuffer: MAX_GIT_OUTPUT,
    windowsHide: true,
    stdio: ["ignore", "pipe", "pipe"],
  });
  if (result.error) {
    throw new GitSnapshotError(errorCode(result.error, "GIT_START_FAILED"));
  }
  const status = result.status ?? -1;
  if (!allowedStatuses.includes(status)) {
    throw new GitSnapshotError(`GIT_EXIT_${status}`);
  }
  return { stdout: result.stdout, status };
}

function branchName(repoRoot: string): string {
  const result = git(
    repoRoot,
    ["symbolic-ref", "--quiet", "--short", "HEAD"],
    [0, 1],
  );
  if (result.status === 1) return "DETACHED";
  const branch = result.stdout.toString("utf8").trim();
  if (branch.length === 0) {
    throw new GitSnapshotError("EMPTY_BRANCH", "Git 分支识别失败");
  }
  return branch;
}

function relativeGitPath(value: string): string {
  return value.replaceAll("\\", "/").replace(/^\.\//u, "");
}

function parseStatusPaths(status: Buffer): string[] {
  const records = status.toString("utf8").split("\0");
  const paths: string[] = [];
  for (let index = 0; index < records.length; index += 1) {
    const record = records[index];
    if (!record) continue;
    if (record.length < 4 || record[2] !== " ") {
      throw new GitSnapshotError("INVALID_STATUS", "Git 状态格式解析失败");
    }
    const statusCode = record.slice(0, 2);
    paths.push(relativeGitPath(record.slice(3)));
    if (/[RC]/u.test(statusCode)) {
      const sourcePath = records[index + 1];
      if (!sourcePath) {
        throw new GitSnapshotError(
          "MISSING_RENAME_SOURCE",
          "Git 重命名状态解析失败",
        );
      }
      paths.push(relativeGitPath(sourcePath));
      index += 1;
    }
  }
  return [...new Set(paths)].sort((left, right) => left.localeCompare(right, "en"));
}

function untrackedPaths(repoRoot: string): string[] {
  return git(repoRoot, ["ls-files", "--others", "--exclude-standard", "-z"])
    .stdout.toString("utf8")
    .split("\0")
    .filter((value) => value.length > 0)
    .map(relativeGitPath)
    .sort((left, right) => left.localeCompare(right, "en"));
}

function frame(hash: Hash, label: string, value: Buffer | string): void {
  const labelBytes = Buffer.from(label, "utf8");
  const valueBytes = typeof value === "string" ? Buffer.from(value, "utf8") : value;
  const lengths = Buffer.allocUnsafe(16);
  lengths.writeBigUInt64BE(BigInt(labelBytes.length), 0);
  lengths.writeBigUInt64BE(BigInt(valueBytes.length), 8);
  hash.update(lengths);
  hash.update(labelBytes);
  hash.update(valueBytes);
}

function hasStableFileIdentity(stat: BigIntStats): boolean {
  return stat.ino > 0n;
}

function assertSameOpenedFile(initial: BigIntStats, opened: BigIntStats): void {
  if (!initial.isFile() || !opened.isFile()) {
    throw new GitSnapshotError(
      "UNTRACKED_TYPE_CHANGED",
      "Git untracked 文件打开后类型发生变化",
    );
  }
  if (!hasStableFileIdentity(initial) || !hasStableFileIdentity(opened)) {
    throw new GitSnapshotError(
      "UNTRACKED_IDENTITY_UNAVAILABLE",
      "Git untracked 文件身份不可可靠确认",
    );
  }
  if (initial.dev !== opened.dev || initial.ino !== opened.ino) {
    throw new GitSnapshotError(
      "UNTRACKED_IDENTITY_CHANGED",
      "Git untracked 文件在检查与打开之间发生变化",
    );
  }
  if (
    initial.size !== opened.size ||
    initial.mtimeNs !== opened.mtimeNs ||
    initial.ctimeNs !== opened.ctimeNs
  ) {
    throw new GitSnapshotError(
      "UNTRACKED_CHANGED_BEFORE_READ",
      "Git untracked 文件在打开前发生变化",
    );
  }
}

function assertStableAfterRead(
  initial: BigIntStats,
  opened: BigIntStats,
  finished: BigIntStats,
): void {
  if (!finished.isFile()) {
    throw new GitSnapshotError(
      "UNTRACKED_TYPE_CHANGED",
      "Git untracked 文件读取后类型发生变化",
    );
  }
  if (
    initial.dev !== finished.dev ||
    initial.ino !== finished.ino ||
    opened.dev !== finished.dev ||
    opened.ino !== finished.ino ||
    initial.size !== finished.size ||
    opened.size !== finished.size ||
    initial.mtimeNs !== finished.mtimeNs ||
    opened.mtimeNs !== finished.mtimeNs ||
    initial.ctimeNs !== finished.ctimeNs ||
    opened.ctimeNs !== finished.ctimeNs
  ) {
    throw new GitSnapshotError(
      "UNTRACKED_CHANGED_DURING_READ",
      "Git untracked 文件在读取期间发生变化，本次采集无效",
    );
  }
}

function digestRegularFile(
  path: string,
  initial: BigIntStats,
  fileOps: GitFileOps,
): Buffer {
  const hash = createHash("sha256");
  const buffer = Buffer.allocUnsafe(FILE_HASH_BUFFER_SIZE);
  let descriptor: number | undefined;
  let digest: Buffer | undefined;
  let primaryError: GitSnapshotError | undefined;
  try {
    descriptor = fileOps.open(path, createUntrackedOpenFlags());
    const opened = fileOps.fstat(descriptor);
    assertSameOpenedFile(initial, opened);
    for (;;) {
      const bytesRead = fileOps.read(descriptor, buffer, 0, buffer.length, null);
      if (bytesRead === 0) break;
      hash.update(buffer.subarray(0, bytesRead));
    }
    const finished = fileOps.fstat(descriptor);
    assertStableAfterRead(initial, opened, finished);
    digest = hash.digest();
  } catch (error) {
    primaryError = asGitSnapshotError(
      error,
      "UNTRACKED_READ_FAILED",
      "Git untracked 文件摘要失败",
    );
  }

  let closeError: unknown;
  if (descriptor !== undefined) {
    try {
      fileOps.close(descriptor);
    } catch (error) {
      closeError = error;
    }
  }

  if (primaryError) {
    throw closeError ? withCloseWarning(primaryError, closeError) : primaryError;
  }
  if (closeError) {
    throw new GitSnapshotError(
      errorCode(closeError, "UNTRACKED_CLOSE_FAILED"),
      "Git untracked 文件描述符关闭失败",
    );
  }
  if (!digest) {
    throw new GitSnapshotError(
      "UNTRACKED_DIGEST_MISSING",
      "Git untracked 文件摘要结果缺失",
    );
  }
  return digest;
}

interface PathSemantics {
  readonly relative: (from: string, to: string) => string;
  readonly isAbsolute: (path: string) => boolean;
}

const NATIVE_PATH_SEMANTICS: PathSemantics = { relative, isAbsolute };

export function isGitPathInside(
  repoRoot: string,
  candidate: string,
  semantics: PathSemantics = NATIVE_PATH_SEMANTICS,
): boolean {
  const relation = semantics.relative(repoRoot, candidate);
  return relation === "" || (
    !semantics.isAbsolute(relation) &&
    relation !== ".." &&
    !relation.startsWith("../") &&
    !relation.startsWith("..\\")
  );
}

function digestUntracked(
  repoRoot: string,
  canonicalRoot: string,
  relativePath: string,
  fileOps: GitFileOps,
): { readonly kind: string; readonly digest: Buffer } {
  const absolute = resolve(repoRoot, ...relativePath.split("/"));
  try {
    const stat = fileOps.lstat(absolute);
    if (stat.isSymbolicLink()) {
      return {
        kind: "SYMLINK",
        digest: createHash("sha256").update(fileOps.readlink(absolute), "utf8").digest(),
      };
    }
    const canonical = fileOps.realpath(absolute);
    if (!isGitPathInside(canonicalRoot, canonical)) {
      throw new GitSnapshotError(
        "UNTRACKED_LINK_ESCAPE",
        "Git untracked 链接解析到仓库外",
      );
    }
    if (stat.isFile()) {
      return { kind: "FILE", digest: digestRegularFile(absolute, stat, fileOps) };
    }
    throw new GitSnapshotError(
      "UNSUPPORTED_UNTRACKED_TYPE",
      "Git untracked 文件类型不受支持",
    );
  } catch (error) {
    if (error instanceof GitSnapshotError) throw error;
    throw new GitSnapshotError(
      errorCode(error, "UNTRACKED_LSTAT_FAILED"),
      "Git untracked 文件检查失败",
    );
  }
}

interface UntrackedDigestEntry {
  readonly relativePath: string;
  readonly kind: string;
  readonly digest: Buffer;
}

function collectUntrackedEntries(
  repoRoot: string,
  fileOps: GitFileOps,
): readonly UntrackedDigestEntry[] {
  const paths = untrackedPaths(repoRoot);
  if (paths.length === 0) return [];
  let canonicalRoot: string;
  try {
    canonicalRoot = fileOps.realpath(repoRoot);
  } catch (error) {
    throw new GitSnapshotError(errorCode(error, "REPO_REALPATH_FAILED"));
  }
  return paths.map((relativePath) => ({
    relativePath,
    ...digestUntracked(repoRoot, canonicalRoot, relativePath, fileOps),
  }));
}

function sameUntrackedEntries(
  before: readonly UntrackedDigestEntry[],
  after: readonly UntrackedDigestEntry[],
): boolean {
  return before.length === after.length && before.every((entry, index) => {
    const candidate = after[index];
    return candidate !== undefined &&
      entry.relativePath === candidate.relativePath &&
      entry.kind === candidate.kind &&
      entry.digest.equals(candidate.digest);
  });
}

function patchFingerprint(
  headSha: string,
  trackedDiff: Buffer,
  untrackedEntries: readonly UntrackedDigestEntry[],
): string {
  const hash = createHash("sha256");
  frame(hash, "schema", "HARNESS_WORKTREE_V2");
  frame(hash, "headSha", headSha);
  frame(hash, "trackedDiffDigest", createHash("sha256").update(trackedDiff).digest());

  for (const entry of untrackedEntries) {
    frame(hash, "untrackedPath", entry.relativePath);
    frame(hash, "untrackedType", entry.kind);
    frame(hash, "untrackedContentDigest", entry.digest);
  }
  return `sha256:${hash.digest("hex")}`;
}

interface SnapshotObservation {
  readonly headSha: string;
  readonly status: Buffer;
  readonly trackedDiff: Buffer;
}

function readHeadSha(repoRoot: string): string {
  const headSha = git(repoRoot, ["rev-parse", "--verify", "HEAD"])
    .stdout.toString("utf8")
    .trim()
    .toLowerCase();
  if (!SHA_PATTERN.test(headSha)) {
    throw new GitSnapshotError("INVALID_HEAD_SHA", "Git HEAD 校验失败");
  }
  return headSha;
}

function readStatus(repoRoot: string): Buffer {
  return git(repoRoot, [
    "status",
    "--porcelain=v1",
    "-z",
    "--untracked-files=all",
    "--ignored=no",
  ]).stdout;
}

function readTrackedDiff(repoRoot: string): Buffer {
  return git(repoRoot, [
    "diff",
    "--binary",
    "--no-ext-diff",
    "--no-color",
    "HEAD",
    "--",
  ]).stdout;
}

function observeSnapshot(repoRoot: string): SnapshotObservation {
  return {
    headSha: readHeadSha(repoRoot),
    status: readStatus(repoRoot),
    trackedDiff: readTrackedDiff(repoRoot),
  };
}

function sameObservation(
  before: SnapshotObservation,
  after: SnapshotObservation,
): boolean {
  return before.headSha === after.headSha &&
    before.status.equals(after.status) &&
    before.trackedDiff.equals(after.trackedDiff);
}

function collectGitSnapshotAttempt(
  repoRoot: string,
  fileOps: GitFileOps,
): GitSnapshot {
  const before = observeSnapshot(repoRoot);
  const changedFiles = parseStatusPaths(before.status);
  const clean = changedFiles.length === 0;
  const firstUntracked = clean ? [] : collectUntrackedEntries(repoRoot, fileOps);
  const branch = branchName(repoRoot);
  const secondUntracked = clean ? [] : collectUntrackedEntries(repoRoot, fileOps);
  const after = observeSnapshot(repoRoot);
  if (
    !sameUntrackedEntries(firstUntracked, secondUntracked) ||
    !sameObservation(before, after)
  ) {
    throw new GitSnapshotError(
      "GIT_SNAPSHOT_CHANGED",
      "Git 工作区在快照采集期间发生变化，本次采集无效",
    );
  }
  const identity: EvidenceIdentity = clean
    ? { kind: "COMMIT", commitSha: before.headSha }
    : {
        kind: "WORKTREE",
        headSha: before.headSha,
        changedFiles,
        patchFingerprint: patchFingerprint(
          before.headSha,
          before.trackedDiff,
          firstUntracked,
        ),
      };
  return {
    headSha: before.headSha,
    branch,
    clean,
    changedFiles,
    identity,
  };
}

const RETRYABLE_CHANGE_CODES = new Set([
  "GIT_SNAPSHOT_CHANGED",
  "UNTRACKED_TYPE_CHANGED",
  "UNTRACKED_IDENTITY_CHANGED",
  "UNTRACKED_CHANGED_BEFORE_READ",
  "UNTRACKED_CHANGED_DURING_READ",
]);

function isRetryableSnapshotChange(error: unknown): error is GitSnapshotError {
  return error instanceof GitSnapshotError && RETRYABLE_CHANGE_CODES.has(error.code);
}

export function collectGitSnapshot(
  repoRoot: string,
  options: CollectGitSnapshotOptions = {},
): GitSnapshot {
  const fileOps = options.fileOps ?? NODE_FILE_OPS;
  for (let attempt = 0; attempt < 2; attempt += 1) {
    try {
      return collectGitSnapshotAttempt(repoRoot, fileOps);
    } catch (error) {
      if (!isRetryableSnapshotChange(error)) throw error;
      if (attempt === 0) continue;
      throw new GitSnapshotError(
        "GIT_SNAPSHOT_UNSTABLE",
        "Git 工作区在两次快照采集中持续变化，无法生成稳定证据",
        error.cleanupWarnings,
        {
          safeRetry: "不要立即自动重试；等待写入任务结束并确认工作区稳定后再采集。",
          stopCondition: "若工作区仍变化，停止采集并先定位持续写入来源。",
        },
      );
    }
  }
  throw new GitSnapshotError("GIT_SNAPSHOT_ATTEMPT_EXHAUSTED");
}
