import {
  closeSync,
  constants,
  existsSync,
  fstatSync,
  lstatSync,
  mkdirSync,
  mkdtempSync,
  openSync,
  readFileSync,
  realpathSync,
  renameSync,
  rmSync,
  symlinkSync,
  unlinkSync,
  ftruncateSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";

import { afterEach, describe, expect, it } from "vitest";

import {
  writeBuildStepLog,
  type BuildLogFileSystem,
} from "../src/checks/build-log.js";
import type { ProcessResult } from "../src/core/process-runner.js";

const temporaryDirectories: string[] = [];

function temporaryDirectory(prefix: string): string {
  const path = mkdtempSync(join(tmpdir(), prefix));
  temporaryDirectories.push(path);
  return path;
}

afterEach(() => {
  for (const directory of temporaryDirectories.splice(0)) {
    rmSync(directory, { recursive: true, force: true });
  }
});

function processResult(): ProcessResult {
  return {
    commandDisplay: "mvn test",
    exitCode: 0,
    signal: null,
    timedOut: false,
    durationMs: 1,
    stdout: "ok",
    stderr: "",
    success: true,
    rootCause: null,
    safeRetry: null,
    stopCondition: null,
  };
}

function buildLogInput(repoRoot: string) {
  return {
    repoRoot,
    rawDir: "runtime/qa/out/run-task8",
    stepId: "backend-test",
    result: processResult(),
  } as const;
}

function nativeFileSystem(): BuildLogFileSystem {
  return {
    mkdir: (path) => mkdirSync(path, { mode: 0o700 }),
    lstat: (path) => lstatSync(path, { bigint: true }),
    realpath: (path) => realpathSync(path),
    open: (path, flags, mode) => openSync(path, flags, mode),
    fstat: (descriptor) => fstatSync(descriptor, { bigint: true }),
    write: (descriptor, content) => writeFileSync(descriptor, content, "utf8"),
    truncate: (descriptor) => ftruncateSync(descriptor, 0),
    close: (descriptor) => closeSync(descriptor),
    unlink: (path) => unlinkSync(path),
  };
}

describe("构建日志安全落盘", () => {
  it("日志文件只能独占创建，既有证据不得覆盖", async () => {
    const root = temporaryDirectory("harness-build-log-");
    const input = buildLogInput(root);

    await writeBuildStepLog(input);
    const file = join(root, "runtime", "qa", "out", "run-task8", "backend-test.log");
    const original = readFileSync(file, "utf8");

    await expect(writeBuildStepLog(input)).rejects.toThrow();
    expect(readFileSync(file, "utf8")).toBe(original);
  });

  it("拒绝通过符号链接或 Windows junction 把日志目录引出仓库", async () => {
    const root = temporaryDirectory("harness-build-log-");
    const outside = temporaryDirectory("harness-build-log-outside-");
    const qa = join(root, "runtime", "qa");
    mkdirSync(qa, { recursive: true });
    symlinkSync(outside, join(qa, "out"), process.platform === "win32" ? "junction" : "dir");

    await expect(writeBuildStepLog(buildLogInput(root)))
      .rejects.toThrow(/符号链接|越出仓库/u);
    expect(existsSync(join(outside, "run-task8", "backend-test.log"))).toBe(false);
  });

  it("独占创建后 canonical 路径发生竞态时拒绝写入并清理空文件", async () => {
    const root = temporaryDirectory("harness-build-log-");
    const outside = temporaryDirectory("harness-build-log-outside-");
    const native = nativeFileSystem();
    let openedFile: string | undefined;
    const raced: BuildLogFileSystem = {
      ...native,
      open: (path, flags, mode) => {
        openedFile = path;
        return native.open(path, flags, mode);
      },
      realpath: (path) => path === openedFile
        ? join(outside, "backend-test.log")
        : native.realpath(path),
    };

    await expect(writeBuildStepLog(buildLogInput(root), raced))
      .rejects.toThrow(/越出仓库/u);
    expect(openedFile).toBeDefined();
    expect(existsSync(openedFile as string)).toBe(false);
  });

  it("写入失败时只清理本次创建的同一文件", async () => {
    const root = temporaryDirectory("harness-build-log-");
    const native = nativeFileSystem();
    let openedFile: string | undefined;
    const failing: BuildLogFileSystem = {
      ...native,
      open: (path, flags, mode) => {
        expect(flags & constants.O_EXCL).not.toBe(0);
        openedFile = path;
        return native.open(path, flags, mode);
      },
      write: () => {
        throw new Error("模拟磁盘写入失败");
      },
    };

    await expect(writeBuildStepLog(buildLogInput(root), failing))
      .rejects.toThrow("模拟磁盘写入失败");
    expect(openedFile).toBeDefined();
    expect(existsSync(openedFile as string)).toBe(false);
    expect(existsSync(dirname(openedFile as string))).toBe(true);
  });

  it("写入后文件被移走并替换时通过 fd 清空原文件且不删除替代文件", async () => {
    const root = temporaryDirectory("harness-build-log-");
    const outside = temporaryDirectory("harness-build-log-outside-");
    const native = nativeFileSystem();
    const movedFile = join(outside, "moved-backend-test.log");
    let openedFile: string | undefined;
    const raced: BuildLogFileSystem = {
      ...native,
      open: (path, flags, mode) => {
        openedFile = path;
        return native.open(path, flags, mode);
      },
      write: (descriptor, content) => {
        native.write(descriptor, content);
        renameSync(openedFile as string, movedFile);
        writeFileSync(openedFile as string, "替代文件不得删除", "utf8");
      },
    };

    await expect(writeBuildStepLog(buildLogInput(root), raced))
      .rejects.toThrow(/身份发生变化/u);
    expect(readFileSync(movedFile, "utf8")).toBe("");
    expect(readFileSync(openedFile as string, "utf8")).toBe("替代文件不得删除");
  });
});
