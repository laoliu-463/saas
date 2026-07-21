import { readFileSync } from "node:fs";

import { describe, expect, it, vi } from "vitest";

import { createCheckResult, createRunResult, type CheckStatus } from "../../lib/node/core/result.js";
import {
  parseInjectedGitSnapshot,
  parseVerifyArguments,
  renderVerifyHelp,
  VERIFY_RECEIPT_PREFIX,
  verifyCli,
  type VerifyCliDependencies,
} from "../../lib/node/cli/verify.js";

function run(status: CheckStatus) {
  return createRunResult([createCheckResult({
    checkId: "verify.fixture",
    title: "验证命令入口",
    status,
    blocking: true,
    summary: `入口返回 ${status}。`,
    nextActions: [],
    artifacts: [],
  })]);
}

function fixture(status: CheckStatus = "PASS") {
  const stdout: string[] = [];
  const stderr: string[] = [];
  const dependency: VerifyCliDependencies = {
    cwd: () => "D:/repo",
    runVerify: vi.fn(async () => ({
      context: { runId: "run-task10", repoRoot: "D:/repo" },
      result: run(status),
      evidence: {
        rawJson: "runtime/qa/out/run-task10/run.json",
        stableJson: "runtime/qa/out/latest-task10.json",
        stableMarkdown: "runtime/qa/out/latest-task10.md",
        cleanupWarnings: [],
      },
    }) as never),
    writeStdout: (message) => stdout.push(message),
    writeStderr: (message) => stderr.push(message),
    readEnvironmentVariable: () => undefined,
    digestFile: () => `sha256:${"d".repeat(64)}`,
  };
  return { dependency, stdout, stderr };
}

describe("verify CLI", () => {
  it("帮助、阶段、摘要、错误边界和退出码说明均为中文", async () => {
    const target = fixture();

    expect(await verifyCli(["--help"], target.dependency)).toBe(0);
    const output = target.stdout.join("\n");
    expect(output).toBe(renderVerifyHelp());
    expect(output).toContain("Harness 本地验证");
    expect(output).toContain("--skip-business-validation");
    expect(output).toContain("退出码");
    expect(output).toContain("不会提交、推送、SSH 或部署");
  });

  it("解析六项兼容参数且允许显式业务命令", () => {
    expect(parseVerifyArguments([
      "--env", "real-pre",
      "--scope", "full",
      "--report-key", "task10",
      "--business-command", "npm run e2e:real-pre:p0:preflight",
      "--skip-business-validation",
      "--dry-run",
    ])).toEqual({
      environment: "real-pre",
      scope: "full",
      reportKey: "task10",
      businessCommand: "npm run e2e:real-pre:p0:preflight",
      skipBusinessValidation: true,
      dryRun: true,
    });
  });

  const invalidArgumentCases: readonly (readonly string[])[] = [
    [],
    ["--env", "production", "--scope", "full", "--report-key", "task10"],
    ["--env", "test", "--scope", "docs", "--report-key", "task10"],
    ["--env", "test", "--scope", "full", "--report-key", "../escape"],
    ["--env", "test", "--scope", "full", "--report-key", "task10", "--unknown"],
    ["--env", "test", "--env", "real-pre", "--scope", "full", "--report-key", "task10"],
  ];

  for (const args of invalidArgumentCases) {
    it(`非法参数返回 3 且不启动验证：${JSON.stringify(args)}`, async () => {
      const target = fixture();

      expect(await verifyCli(args, target.dependency)).toBe(3);
      expect(target.dependency.runVerify).not.toHaveBeenCalled();
      expect(target.stderr.join("\n")).toContain("参数错误");
      expect(target.stderr.join("\n")).toContain("--help");
    });
  }

  it.each([
    ["PASS", 0],
    ["FAIL", 1],
    ["BLOCKED", 2],
    ["WARN", 2],
  ] as const)("结果 %s 映射退出码 %i 且成功写证据后才输出摘要", async (status, code) => {
    const target = fixture(status);

    expect(await verifyCli([
      "--env", "test", "--scope", "backend", "--report-key", "task10",
    ], target.dependency)).toBe(code);
    expect(target.stdout.join("\n")).toContain("验证摘要");
    expect(target.stdout.join("\n")).toContain("latest-task10.json");
    expect(target.stdout.join("\n")).toContain("latest-task10.md");
  });

  it("证据 I/O 失败返回 2，契约错误返回 3，且错误内容脱敏无堆栈", async () => {
    const blocked = fixture();
    blocked.dependency.runVerify = vi.fn(async () => {
      throw new Error("证据写入失败 password=top-secret-value");
    });
    expect(await verifyCli([
      "--env", "test", "--scope", "backend", "--report-key", "task10",
    ], blocked.dependency)).toBe(2);
    expect(blocked.stderr.join("\n")).toContain("执行受阻");
    expect(blocked.stderr.join("\n")).not.toContain("top-secret-value");
    expect(blocked.stderr.join("\n")).not.toContain(" at ");

    const contract = fixture();
    contract.dependency.runVerify = vi.fn(async () => {
      const error = new Error("结果不符合 Schema");
      error.name = "VerifyContractError";
      throw error;
    });
    expect(await verifyCli([
      "--env", "test", "--scope", "backend", "--report-key", "task10",
    ], contract.dependency)).toBe(3);
    expect(contract.stderr.join("\n")).toContain("契约错误");
  });

  it("只消费 agent-do 注入的合法 Git 快照，并用不可猜调用标识输出唯一回执", async () => {
    const target = fixture("WARN");
    const snapshot = {
      headSha: "a".repeat(40),
      branch: "codex/task10",
      clean: false,
      changedFiles: ["harness/scripts/lib/node/cli/verify.ts"],
      identity: {
        kind: "WORKTREE",
        headSha: "a".repeat(40),
        changedFiles: ["harness/scripts/lib/node/cli/verify.ts"],
        patchFingerprint: `sha256:${"b".repeat(64)}`,
      },
    };
    target.dependency.readEnvironmentVariable = (name) => name === "HARNESS_VERIFY_INVOCATION_ID"
      ? "c".repeat(32)
      : name === "HARNESS_VERIFY_GIT_SNAPSHOT_JSON" ? JSON.stringify(snapshot) : undefined;

    expect(await verifyCli([
      "--env", "test", "--scope", "backend", "--report-key", "task10",
    ], target.dependency)).toBe(2);
    expect(target.dependency.runVerify).toHaveBeenCalledWith(expect.objectContaining({
      gitSnapshot: snapshot,
    }));
    const receiptLines = target.stdout.filter((line) => line.startsWith(VERIFY_RECEIPT_PREFIX));
    expect(receiptLines).toHaveLength(1);
    const payload = JSON.parse(Buffer.from(
      receiptLines[0]!.slice(VERIFY_RECEIPT_PREFIX.length),
      "base64url",
    ).toString("utf8")) as Record<string, unknown>;
    expect(payload).toMatchObject({
      schemaVersion: "1.0.0",
      invocationId: "c".repeat(32),
      runId: "run-task10",
      reportKey: "task10",
      status: "PARTIAL",
      evidenceDigests: {
        rawJson: `sha256:${"d".repeat(64)}`,
        stableJson: `sha256:${"d".repeat(64)}`,
        stableMarkdown: `sha256:${"d".repeat(64)}`,
      },
    });
  });

  it("拒绝伪造 Git 快照且不启动验证", async () => {
    const target = fixture();
    target.dependency.readEnvironmentVariable = (name) =>
      name === "HARNESS_VERIFY_GIT_SNAPSHOT_JSON" ? '{"clean":true}' : undefined;

    expect(await verifyCli([
      "--env", "test", "--scope", "backend", "--report-key", "task10",
    ], target.dependency)).toBe(3);
    expect(target.dependency.runVerify).not.toHaveBeenCalled();
    expect(() => parseInjectedGitSnapshot('{"clean":true}')).toThrow(/Git 快照/u);
  });

  it("优先从文件读取较大的 Git 快照，避免环境变量长度限制", async () => {
    const target = fixture();
    const snapshot = {
      headSha: "a".repeat(40),
      branch: "codex/task10",
      clean: true,
      changedFiles: [],
      identity: {
        kind: "COMMIT",
        commitSha: "a".repeat(40),
      },
    };
    target.dependency.readEnvironmentVariable = (name) =>
      name === "HARNESS_VERIFY_GIT_SNAPSHOT_FILE" ? "C:/temp/harness-snapshot.json" : undefined;
    target.dependency.readSnapshotFile = vi.fn(() => JSON.stringify(snapshot));

    expect(await verifyCli([
      "--env", "test", "--scope", "backend", "--report-key", "task10",
    ], target.dependency)).toBe(0);
    expect(target.dependency.readSnapshotFile).toHaveBeenCalledWith("C:/temp/harness-snapshot.json");
    expect(target.dependency.runVerify).toHaveBeenCalledWith(expect.objectContaining({
      gitSnapshot: snapshot,
    }));
  });

  it("CLI 与组合器源码不包含 Git 写操作、SSH 或部署调用", () => {
    const source = [
      readFileSync(new URL("../../lib/node/cli/verify.ts", import.meta.url), "utf8"),
      readFileSync(new URL("../../lib/node/workflows/run-verify.ts", import.meta.url), "utf8"),
    ].join("\n");

    expect(source).not.toMatch(/command\s*:\s*["']git["']/iu);
    expect(source).not.toMatch(/command\s*:\s*["'](?:ssh|scp|sftp|rsync)["']/iu);
    expect(source).not.toMatch(/deploy[-_]?remote|remote[-_]?deploy/iu);
    expect(source).not.toMatch(/node:child_process/iu);
  });
});
