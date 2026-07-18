import { execFileSync } from "node:child_process";
import {
  closeSync,
  fstatSync,
  lstatSync,
  mkdirSync,
  mkdtempSync,
  openSync,
  readFileSync,
  readlinkSync,
  readSync,
  realpathSync,
  rmSync,
  symlinkSync,
  unlinkSync,
  utimesSync,
  writeFileSync,
  type BigIntStats,
} from "node:fs";
import { join, win32 } from "node:path";
import { tmpdir } from "node:os";

import { afterEach, describe, expect, it } from "vitest";

import { collectGitSnapshot, GitSnapshotError } from "../src/core/git.js";

const repositories: string[] = [];

interface GitFileOpsDouble {
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

function nativeGitFileOps(): GitFileOpsDouble {
  return {
    lstat: (path) => lstatSync(path, { bigint: true }),
    open: (path, flags) => openSync(path, flags),
    fstat: (descriptor) => fstatSync(descriptor, { bigint: true }),
    read: (descriptor, buffer, offset, length, position) =>
      readSync(descriptor, buffer, offset, length, position),
    close: (descriptor) => closeSync(descriptor),
    readlink: (path) => readlinkSync(path),
    realpath: (path) => realpathSync.native(path),
  };
}

function collectWithFileOps(repoRoot: string, fileOps: GitFileOpsDouble) {
  return (collectGitSnapshot as unknown as (
    root: string,
    options: { readonly fileOps: GitFileOpsDouble },
  ) => ReturnType<typeof collectGitSnapshot>)(repoRoot, { fileOps });
}

function git(repoRoot: string, ...args: string[]): string {
  return execFileSync("git", args, {
    cwd: repoRoot,
    encoding: "utf8",
    env: { ...process.env, GIT_CONFIG_NOSYSTEM: "1" },
  }).trim();
}

function createRepository(): string {
  const repoRoot = mkdtempSync(join(tmpdir(), "harness-git-中文-"));
  repositories.push(repoRoot);
  git(repoRoot, "init", "--quiet");
  mkdirSync(join(repoRoot, ".git", "no-hooks"));
  git(repoRoot, "config", "core.hooksPath", ".git/no-hooks");
  git(repoRoot, "config", "core.autocrlf", "false");
  git(repoRoot, "config", "user.name", "Harness Test");
  git(repoRoot, "config", "user.email", "harness@example.invalid");

  mkdirSync(join(repoRoot, "目录"));
  writeFileSync(join(repoRoot, ".gitignore"), "ignored.txt\n", "utf8");
  writeFileSync(join(repoRoot, "tracked.txt"), "base\n", "utf8");
  writeFileSync(join(repoRoot, "delete-me.txt"), "delete\n", "utf8");
  writeFileSync(join(repoRoot, "rename-source.txt"), "rename\n", "utf8");
  writeFileSync(join(repoRoot, "目录", "初始 文件.txt"), "中文\n", "utf8");
  git(repoRoot, "add", "--all");
  git(repoRoot, "commit", "--quiet", "-m", "initial");
  return repoRoot;
}

afterEach(() => {
  while (repositories.length > 0) {
    rmSync(repositories.pop()!, { recursive: true, force: true });
  }
});

describe("Git 只读快照", () => {
  it("按 path.win32 语义拒绝跨盘和相邻前缀 canonical 路径", async () => {
    const module = await import("../src/core/git.js");
    const containment = (module as unknown as {
      isGitPathInside?: (
        root: string,
        candidate: string,
        semantics: Pick<typeof win32, "relative" | "isAbsolute">,
      ) => boolean;
    }).isGitPathInside;

    expect(typeof containment).toBe("function");
    if (!containment) return;
    expect(containment("D:\\repo", "C:\\outside", win32)).toBe(false);
    expect(containment("D:\\repo", "D:\\repo2\\outside", win32)).toBe(false);
    expect(containment("D:\\repo", "D:\\repo\\inside", win32)).toBe(true);
    expect(containment("D:\\Repo", "d:\\repo\\inside", win32)).toBe(true);
  });

  it("POSIX 打开普通文件时加入 O_NOFOLLOW", async () => {
    const module = await import("../src/core/git.js");
    const createFlags = (module as unknown as {
      createUntrackedOpenFlags?: (
        platform: NodeJS.Platform,
        constants: { readonly O_RDONLY: number; readonly O_NOFOLLOW?: number },
      ) => number;
    }).createUntrackedOpenFlags;

    expect(typeof createFlags).toBe("function");
    if (!createFlags) return;
    expect(createFlags("linux", { O_RDONLY: 1, O_NOFOLLOW: 0x20_000 })).toBe(
      0x20_001,
    );
    expect(createFlags("win32", { O_RDONLY: 1, O_NOFOLLOW: 0x20_000 })).toBe(1);
  });

  it("lstat 后打开到外部不同文件时拒绝且不读取外部内容", () => {
    const repoRoot = createRepository();
    const untracked = join(repoRoot, "race.txt");
    writeFileSync(untracked, "inside-before-open\n", "utf8");
    const externalRoot = mkdtempSync(join(tmpdir(), "harness-git-race-"));
    repositories.push(externalRoot);
    const external = join(externalRoot, "outside.txt");
    writeFileSync(external, "outside-must-not-be-read\n", "utf8");

    const base = nativeGitFileOps();
    let externalReadCount = 0;
    const fileOps: GitFileOpsDouble = {
      ...base,
      open: (path, flags) => base.open(path === untracked ? external : path, flags),
      read(descriptor, buffer, offset, length, position) {
        externalReadCount += 1;
        return base.read(descriptor, buffer, offset, length, position);
      },
    };

    expect(() => collectWithFileOps(repoRoot, fileOps)).toThrowError(GitSnapshotError);
    expect(externalReadCount).toBe(0);
  }, 20_000);

  it("untracked 文件保持同 inode 但读取中修改时连续两次采集均判定不稳定", () => {
    const repoRoot = createRepository();
    const untracked = join(repoRoot, "same-inode.txt");
    writeFileSync(untracked, "alpha\n", "utf8");
    const initial = lstatSync(untracked, { bigint: true });
    const base = nativeGitFileOps();
    let nonEmptyReads = 0;
    const fileOps: GitFileOpsDouble = {
      ...base,
      read(descriptor, buffer, offset, length, position) {
        const bytesRead = base.read(descriptor, buffer, offset, length, position);
        if (bytesRead > 0) {
          nonEmptyReads += 1;
          writeFileSync(
            untracked,
            nonEmptyReads % 2 === 1 ? "bravo\n" : "alpha\n",
            "utf8",
          );
          const stamp = new Date(`2026-07-18T04:05:0${nonEmptyReads}.000Z`);
          utimesSync(untracked, stamp, stamp);
        }
        return bytesRead;
      },
    };

    expect(() => collectWithFileOps(repoRoot, fileOps)).toThrowError(GitSnapshotError);
    const final = lstatSync(untracked, { bigint: true });
    expect({ dev: final.dev, ino: final.ino }).toEqual({
      dev: initial.dev,
      ino: initial.ino,
    });
    expect(nonEmptyReads).toBe(2);
  });

  it("第一次 untracked final fstat 后同 inode 变化时重试一次并返回新指纹", () => {
    const repoRoot = createRepository();
    const untracked = join(repoRoot, "after-final-fstat.txt");
    writeFileSync(untracked, "alpha\n", "utf8");
    const initial = lstatSync(untracked, { bigint: true });
    const headBefore = git(repoRoot, "rev-parse", "HEAD");
    const statusBefore = git(repoRoot, "status", "--porcelain=v1", "--untracked-files=all");
    const diffBefore = git(repoRoot, "diff", "--binary", "HEAD", "--");
    const base = nativeGitFileOps();
    let fstatCalls = 0;
    const fileOps: GitFileOpsDouble = {
      ...base,
      fstat(descriptor) {
        const stat = base.fstat(descriptor);
        fstatCalls += 1;
        if (fstatCalls === 2) {
          writeFileSync(untracked, "bravo\n", "utf8");
          const stamp = new Date("2026-07-18T04:06:01.000Z");
          utimesSync(untracked, stamp, stamp);
        }
        return stat;
      },
    };

    const snapshot = collectWithFileOps(repoRoot, fileOps);
    const stableSnapshot = collectGitSnapshot(repoRoot);
    const final = lstatSync(untracked, { bigint: true });

    expect(snapshot.identity).toEqual(stableSnapshot.identity);
    expect(fstatCalls).toBe(8);
    expect({ dev: final.dev, ino: final.ino }).toEqual({
      dev: initial.dev,
      ino: initial.ino,
    });
    expect(git(repoRoot, "rev-parse", "HEAD")).toBe(headBefore);
    expect(git(repoRoot, "status", "--porcelain=v1", "--untracked-files=all"))
      .toBe(statusBefore);
    expect(git(repoRoot, "diff", "--binary", "HEAD", "--")).toBe(diffBefore);
  }, 30_000);

  it("untracked 在每次 final fstat 后持续变化时有限重试后失败", () => {
    const repoRoot = createRepository();
    const untracked = join(repoRoot, "always-after-final-fstat.txt");
    writeFileSync(untracked, "alpha\n", "utf8");
    const initial = lstatSync(untracked, { bigint: true });
    const base = nativeGitFileOps();
    let fstatCalls = 0;
    const fileOps: GitFileOpsDouble = {
      ...base,
      fstat(descriptor) {
        const stat = base.fstat(descriptor);
        fstatCalls += 1;
        if (fstatCalls % 2 === 0) {
          const mutation = fstatCalls / 2;
          writeFileSync(
            untracked,
            mutation % 2 === 1 ? "bravo\n" : "alpha\n",
            "utf8",
          );
          const stamp = new Date(`2026-07-18T04:06:0${mutation}.000Z`);
          utimesSync(untracked, stamp, stamp);
        }
        return stat;
      },
    };

    try {
      collectWithFileOps(repoRoot, fileOps);
      throw new Error("持续变化的 untracked 快照本应失败");
    } catch (error) {
      expect(error).toBeInstanceOf(GitSnapshotError);
      expect(error).toMatchObject({
        rootCause: expect.stringMatching(/持续变化|不稳定/),
        safeRetry: expect.stringMatching(/等待|不要/),
        stopCondition: expect.stringMatching(/停止/),
      });
    }
    const final = lstatSync(untracked, { bigint: true });
    expect({ dev: final.dev, ino: final.ino }).toEqual({
      dev: initial.dev,
      ino: initial.ino,
    });
    expect(fstatCalls).toBe(8);
  }, 30_000);

  it("tracked 内容变化但 porcelain 状态不变时首次失效并仅重试一次后成功", () => {
    const repoRoot = createRepository();
    const tracked = join(repoRoot, "tracked.txt");
    const trigger = join(repoRoot, "trigger.txt");
    writeFileSync(tracked, "dirty-a\n", "utf8");
    writeFileSync(trigger, "trigger\n", "utf8");
    const statusBefore = git(repoRoot, "status", "--porcelain=v1", "--untracked-files=all");
    const base = nativeGitFileOps();
    let fstatCalls = 0;
    const fileOps: GitFileOpsDouble = {
      ...base,
      fstat(descriptor) {
        fstatCalls += 1;
        if (fstatCalls === 1) writeFileSync(tracked, "dirty-b\n", "utf8");
        return base.fstat(descriptor);
      },
    };

    const snapshot = collectWithFileOps(repoRoot, fileOps);

    expect(git(repoRoot, "status", "--porcelain=v1", "--untracked-files=all"))
      .toBe(statusBefore);
    expect(snapshot.identity.kind).toBe("WORKTREE");
    expect(fstatCalls).toBe(8);
  }, 20_000);

  it("tracked 内容在两次采集中持续变化时有限重试后返回中文结构化错误", () => {
    const repoRoot = createRepository();
    const tracked = join(repoRoot, "tracked.txt");
    writeFileSync(tracked, "dirty-0\n", "utf8");
    writeFileSync(join(repoRoot, "trigger.txt"), "trigger\n", "utf8");
    const base = nativeGitFileOps();
    let fstatCalls = 0;
    const fileOps: GitFileOpsDouble = {
      ...base,
      fstat(descriptor) {
        fstatCalls += 1;
        if (fstatCalls % 2 === 1) {
          writeFileSync(tracked, `dirty-${Math.ceil(fstatCalls / 2)}\n`, "utf8");
        }
        return base.fstat(descriptor);
      },
    };

    try {
      collectWithFileOps(repoRoot, fileOps);
      throw new Error("持续变化的快照本应失败");
    } catch (error) {
      expect(error).toBeInstanceOf(GitSnapshotError);
      expect(error).toMatchObject({
        rootCause: expect.stringMatching(/持续变化|不稳定/),
        safeRetry: expect.stringMatching(/等待|不要/),
        stopCondition: expect.stringMatching(/停止/),
      });
    }
    expect(fstatCalls).toBe(8);
  });

  it("读取成功但 close 失败时返回结构化中文错误", () => {
    const repoRoot = createRepository();
    writeFileSync(join(repoRoot, "close-failure.txt"), "content\n", "utf8");
    const base = nativeGitFileOps();
    const fileOps: GitFileOpsDouble = {
      ...base,
      close(descriptor) {
        base.close(descriptor);
        throw Object.assign(new Error("injected close failure"), {
          code: "INJECTED_CLOSE",
        });
      },
    };

    try {
      collectWithFileOps(repoRoot, fileOps);
      throw new Error("采集本应失败");
    } catch (error) {
      expect(error).toBeInstanceOf(GitSnapshotError);
      expect(error).toMatchObject({
        rootCause: expect.stringMatching(/关闭失败/),
        safeRetry: expect.stringMatching(/确认/),
        stopCondition: expect.stringMatching(/停止/),
      });
    }
  });

  it("读取主错误与 close 同时失败时保留主根因并记录关闭警告", () => {
    const repoRoot = createRepository();
    writeFileSync(join(repoRoot, "read-and-close-failure.txt"), "content\n", "utf8");
    const base = nativeGitFileOps();
    const fileOps: GitFileOpsDouble = {
      ...base,
      read() {
        throw Object.assign(new Error("injected read failure"), {
          code: "INJECTED_READ",
        });
      },
      close(descriptor) {
        base.close(descriptor);
        throw Object.assign(new Error("injected close failure"), {
          code: "INJECTED_CLOSE",
        });
      },
    };

    try {
      collectWithFileOps(repoRoot, fileOps);
      throw new Error("采集本应失败");
    } catch (error) {
      expect(error).toBeInstanceOf(GitSnapshotError);
      expect(error).toMatchObject({
        rootCause: expect.stringMatching(/摘要失败/),
        cleanupWarnings: [expect.stringMatching(/关闭失败/)],
      });
      expect((error as GitSnapshotError).rootCause).not.toContain("关闭失败");
    }
  });

  it("干净工作区生成绑定完整 SHA 的 COMMIT 身份", () => {
    const repoRoot = createRepository();
    const snapshot = collectGitSnapshot(repoRoot);

    expect(snapshot.headSha).toMatch(/^(?:[a-f0-9]{40}|[a-f0-9]{64})$/u);
    expect(snapshot.branch).toBe(git(repoRoot, "branch", "--show-current"));
    expect(snapshot.clean).toBe(true);
    expect(snapshot.changedFiles).toEqual([]);
    expect(snapshot.identity).toEqual({
      kind: "COMMIT",
      commitSha: snapshot.headSha,
    });
  });

  it("完整覆盖 staged、unstaged、删除、重命名和非 ignored untracked 内容", () => {
    const repoRoot = createRepository();
    writeFileSync(join(repoRoot, "tracked.txt"), "unstaged\n", "utf8");
    unlinkSync(join(repoRoot, "delete-me.txt"));
    git(repoRoot, "mv", "rename-source.txt", "重命名 文件.txt");
    writeFileSync(join(repoRoot, "未跟踪 空格.txt"), "untracked-v1\n", "utf8");
    writeFileSync(join(repoRoot, "ignored.txt"), "ignored-secret\n", "utf8");

    const first = collectGitSnapshot(repoRoot);
    const second = collectGitSnapshot(repoRoot);

    expect(first.clean).toBe(false);
    expect(first.identity.kind).toBe("WORKTREE");
    expect(first.changedFiles).toEqual([
      "delete-me.txt",
      "rename-source.txt",
      "tracked.txt",
      "未跟踪 空格.txt",
      "重命名 文件.txt",
    ].sort((left, right) => left.localeCompare(right, "en")));
    expect(first.changedFiles).not.toContain("ignored.txt");
    expect(first.identity).toMatchObject({
      kind: "WORKTREE",
      headSha: first.headSha,
      changedFiles: first.changedFiles,
      patchFingerprint: expect.stringMatching(/^sha256:[a-f0-9]{64}$/u),
    });
    expect(second.identity).toEqual(first.identity);
    expect(first).not.toHaveProperty("patch");
    expect(first).not.toHaveProperty("diff");

    writeFileSync(join(repoRoot, "tracked.txt"), "unstaged-v2\n", "utf8");
    const trackedChanged = collectGitSnapshot(repoRoot);
    expect(trackedChanged.identity).not.toEqual(first.identity);

    writeFileSync(join(repoRoot, "tracked.txt"), "unstaged\n", "utf8");
    writeFileSync(join(repoRoot, "未跟踪 空格.txt"), "untracked-v2\n", "utf8");
    const untrackedChanged = collectGitSnapshot(repoRoot);
    expect(untrackedChanged.identity).not.toEqual(first.identity);
    expect(readFileSync(join(repoRoot, "ignored.txt"), "utf8")).toContain(
      "ignored-secret",
    );
  }, 20_000);

  it("以固定块处理含 NUL 的大体积 untracked 二进制并保持指纹稳定", () => {
    const repoRoot = createRepository();
    const binaryPath = join(repoRoot, "large-binary.bin");
    const binary = Buffer.alloc(8 * 1024 * 1024, 0x5a);
    binary[0] = 0;
    binary[1024] = 0;
    binary[binary.length - 1] = 0xff;
    writeFileSync(binaryPath, binary);

    const first = collectGitSnapshot(repoRoot);
    const second = collectGitSnapshot(repoRoot);
    expect(second.identity).toEqual(first.identity);
    expect(first.changedFiles).toContain("large-binary.bin");

    binary[Math.floor(binary.length / 2)] = 0x33;
    writeFileSync(binaryPath, binary);
    expect(collectGitSnapshot(repoRoot).identity).not.toEqual(first.identity);
  }, 30_000);

  it("untracked 符号链接只绑定链接文本，不读取仓库外目标内容", (context) => {
    const repoRoot = createRepository();
    const externalRoot = mkdtempSync(join(tmpdir(), "harness-git-external-"));
    repositories.push(externalRoot);
    const firstTarget = join(externalRoot, "outside-one.txt");
    const secondTarget = join(externalRoot, "outside-two.txt");
    writeFileSync(firstTarget, "outside-v1\n", "utf8");
    writeFileSync(secondTarget, "outside-v2\n", "utf8");
    const linkPath = join(repoRoot, "outside-link.txt");
    try {
      symlinkSync(firstTarget, linkPath, "file");
    } catch (error) {
      if (
        process.platform === "win32" &&
        ["EPERM", "EACCES", "UNKNOWN"].includes(
          (error as NodeJS.ErrnoException).code ?? "",
        )
      ) {
        const junctionPath = join(repoRoot, "outside-junction");
        try {
          symlinkSync(externalRoot, junctionPath, "junction");
        } catch (junctionError) {
          if (["EPERM", "EACCES", "UNKNOWN"].includes(
            (junctionError as NodeJS.ErrnoException).code ?? "",
          )) {
            context.skip();
          }
          throw junctionError;
        }
        expect(() => collectGitSnapshot(repoRoot)).toThrowError(/链接|仓库外/);
        return;
      }
      throw error;
    }

    const first = collectGitSnapshot(repoRoot);
    writeFileSync(firstTarget, "outside-content-changed\n", "utf8");
    expect(collectGitSnapshot(repoRoot).identity).toEqual(first.identity);

    unlinkSync(linkPath);
    symlinkSync(secondTarget, linkPath, "file");
    expect(collectGitSnapshot(repoRoot).identity).not.toEqual(first.identity);
  }, 20_000);

  it("Git 命令失败时返回结构化中文恢复边界", () => {
    const notARepository = mkdtempSync(join(tmpdir(), "harness-not-git-"));
    repositories.push(notARepository);

    try {
      collectGitSnapshot(notARepository);
      throw new Error("采集本应失败");
    } catch (error) {
      expect(error).toMatchObject({
        name: "GitSnapshotError",
        rootCause: expect.stringMatching(/Git.*失败/),
        safeRetry: expect.stringMatching(/确认/),
        stopCondition: expect.stringMatching(/停止/),
      });
      expect(String(error)).not.toContain(notARepository);
    }
  });
});
