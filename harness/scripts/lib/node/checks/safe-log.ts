import {
  closeSync,
  constants,
  fstatSync,
  ftruncateSync,
  lstatSync,
  mkdirSync,
  openSync,
  realpathSync,
  unlinkSync,
  writeFileSync,
  type BigIntStats,
} from "node:fs";
import { isAbsolute, join, relative, resolve } from "node:path";

export interface SafeLogInput {
  readonly repoRoot: string;
  readonly rawDir: string;
  readonly logId: string;
  readonly content: string;
}

export interface SafeLogFileSystem {
  readonly mkdir: (path: string) => void;
  readonly lstat: (path: string) => BigIntStats;
  readonly realpath: (path: string) => string;
  readonly open: (path: string, flags: number, mode: number) => number;
  readonly fstat: (descriptor: number) => BigIntStats;
  readonly write: (descriptor: number, content: string) => void;
  readonly truncate: (descriptor: number) => void;
  readonly close: (descriptor: number) => void;
  readonly unlink: (path: string) => void;
}

const NODE_FILE_SYSTEM = Object.freeze({
  mkdir: (path: string) => mkdirSync(path, { mode: 0o700 }),
  lstat: (path: string) => lstatSync(path, { bigint: true }),
  realpath: (path: string) => realpathSync(path),
  open: (path: string, flags: number, mode: number) => openSync(path, flags, mode),
  fstat: (descriptor: number) => fstatSync(descriptor, { bigint: true }),
  write: (descriptor: number, content: string) =>
    writeFileSync(descriptor, content, "utf8"),
  truncate: (descriptor: number) => ftruncateSync(descriptor, 0),
  close: (descriptor: number) => closeSync(descriptor),
  unlink: (path: string) => unlinkSync(path),
}) satisfies SafeLogFileSystem;

const RAW_DIR_PATTERN =
  /^runtime\/qa\/out\/[a-z0-9]+(?:[._-][a-z0-9]+)*$/u;
const RAW_DIR_PREFIX = "runtime/qa/out/";

export function safeLogArtifactPath(
  rawDir: string,
  logId: string,
  allowedLogIds: ReadonlySet<string>,
): string {
  const runId = rawDir.startsWith(RAW_DIR_PREFIX)
    ? rawDir.slice(RAW_DIR_PREFIX.length)
    : "";
  if (!RAW_DIR_PATTERN.test(rawDir) || runId.length > 128) {
    throw new Error("检查日志 rawDir 必须是 runtime/qa/out/<runId> 仓库相对路径。");
  }
  if (!allowedLogIds.has(logId)) {
    throw new Error("检查日志 id 不在固定计划中，已拒绝写入。");
  }
  return `${rawDir}/${logId}.log`;
}

function errorCode(error: unknown): string | undefined {
  if (typeof error !== "object" || error === null) return undefined;
  const code = Reflect.get(error, "code");
  return typeof code === "string" ? code : undefined;
}

function assertInsideRepository(
  canonicalRoot: string,
  canonicalPath: string,
  message: string,
): void {
  const relativePath = relative(canonicalRoot, canonicalPath);
  if (
    isAbsolute(relativePath) ||
    relativePath === ".." ||
    /^\.\.(?:[\\/]|$)/u.test(relativePath)
  ) {
    throw new Error(message);
  }
}

function ensureDirectoryPath(
  repoRoot: string,
  rawDir: string,
  fileSystem: SafeLogFileSystem,
): { readonly directory: string; readonly canonicalRoot: string } {
  const absoluteRoot = resolve(repoRoot);
  const canonicalRoot = fileSystem.realpath(absoluteRoot);
  let current = absoluteRoot;
  for (const segment of rawDir.split("/")) {
    current = join(current, segment);
    try {
      fileSystem.mkdir(current);
    } catch (error) {
      if (errorCode(error) !== "EEXIST") throw error;
    }
    const metadata = fileSystem.lstat(current);
    if (metadata.isSymbolicLink() || !metadata.isDirectory()) {
      throw new Error("检查日志目录包含符号链接、junction 或非目录节点，已拒绝写入。");
    }
    assertInsideRepository(
      canonicalRoot,
      fileSystem.realpath(current),
      "检查日志目录越出仓库，已拒绝写入。",
    );
  }
  return { directory: current, canonicalRoot };
}

function sameIdentity(left: BigIntStats, right: BigIntStats): boolean {
  return left.dev === right.dev && left.ino === right.ino;
}

function assertOpenedFileIdentity(
  descriptorMetadata: BigIntStats,
  linkedMetadata: BigIntStats,
): void {
  if (
    !descriptorMetadata.isFile() ||
    linkedMetadata.isSymbolicLink() ||
    !linkedMetadata.isFile() ||
    !sameIdentity(descriptorMetadata, linkedMetadata)
  ) {
    throw new Error("检查日志文件身份发生变化，已拒绝写入。");
  }
}

function removeCreatedFileIfUnchanged(
  fileSystem: SafeLogFileSystem,
  filePath: string,
  openedMetadata: BigIntStats | undefined,
): void {
  if (openedMetadata === undefined) return;
  try {
    const linkedMetadata = fileSystem.lstat(filePath);
    if (
      !linkedMetadata.isSymbolicLink() &&
      linkedMetadata.isFile() &&
      sameIdentity(openedMetadata, linkedMetadata)
    ) {
      fileSystem.unlink(filePath);
    }
  } catch {
    // 文件可能已经消失或被替换；不得删除身份不明的路径。
  }
}

export async function writeSafeLog(
  input: SafeLogInput,
  allowedLogIds: ReadonlySet<string>,
  fileSystem: SafeLogFileSystem = NODE_FILE_SYSTEM,
): Promise<string> {
  const artifactPath = safeLogArtifactPath(input.rawDir, input.logId, allowedLogIds);
  const { directory, canonicalRoot } = ensureDirectoryPath(
    input.repoRoot,
    input.rawDir,
    fileSystem,
  );
  const filePath = join(directory, `${input.logId}.log`);
  let descriptor: number | undefined;
  let openedMetadata: BigIntStats | undefined;
  try {
    descriptor = fileSystem.open(
      filePath,
      constants.O_CREAT |
        constants.O_EXCL |
        constants.O_WRONLY |
        (constants.O_NOFOLLOW ?? 0),
      0o600,
    );
    openedMetadata = fileSystem.fstat(descriptor);
    assertOpenedFileIdentity(openedMetadata, fileSystem.lstat(filePath));
    assertInsideRepository(
      canonicalRoot,
      fileSystem.realpath(filePath),
      "检查日志文件越出仓库，已拒绝写入。",
    );

    fileSystem.write(descriptor, input.content);

    const finalDescriptorMetadata = fileSystem.fstat(descriptor);
    const finalLinkedMetadata = fileSystem.lstat(filePath);
    assertOpenedFileIdentity(finalDescriptorMetadata, finalLinkedMetadata);
    if (!sameIdentity(openedMetadata, finalDescriptorMetadata)) {
      throw new Error("检查日志文件描述符身份发生变化，已拒绝保留证据。");
    }
    assertInsideRepository(
      canonicalRoot,
      fileSystem.realpath(filePath),
      "检查日志文件越出仓库，已拒绝保留证据。",
    );

    fileSystem.close(descriptor);
    descriptor = undefined;
  } catch (error) {
    let truncateFailed = false;
    if (descriptor !== undefined) {
      if (openedMetadata !== undefined) {
        try {
          const currentDescriptorMetadata = fileSystem.fstat(descriptor);
          if (
            currentDescriptorMetadata.isFile() &&
            sameIdentity(openedMetadata, currentDescriptorMetadata)
          ) {
            fileSystem.truncate(descriptor);
          } else {
            truncateFailed = true;
          }
        } catch {
          truncateFailed = true;
        }
      }
      try {
        fileSystem.close(descriptor);
      } catch {
        // 保留原始失败原因。
      }
      descriptor = undefined;
    }
    removeCreatedFileIfUnchanged(fileSystem, filePath, openedMetadata);
    if (truncateFailed) {
      throw new Error("检查日志失败后无法通过文件描述符清空本次证据，已停止后续验证。");
    }
    throw error;
  }
  return artifactPath;
}
