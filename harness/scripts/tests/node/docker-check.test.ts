import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { afterEach, describe, expect, it, vi } from "vitest";

import type { ProcessOptions, ProcessResult } from "../../lib/node/core/process-runner.js";
import {
  buildDockerCommandPlan,
  runDockerCheck,
} from "../../lib/node/checks/docker.js";

function successfulProcessResult(options: ProcessOptions): ProcessResult {
  return {
    commandDisplay: [options.command, ...options.args].join(" "),
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

const temporaryDirectories: string[] = [];

function temporaryRepository(): string {
  const root = mkdtempSync(join(tmpdir(), "harness-docker-check-"));
  temporaryDirectories.push(root);
  return root;
}

afterEach(() => {
  for (const directory of temporaryDirectories.splice(0)) {
    rmSync(directory, { recursive: true, force: true });
  }
});

describe("Docker 检查命令计划", () => {
  it("full scope 只生成固定 Compose up 与 ps 参数并按后端、前端顺序选服务", () => {
    const plan = buildDockerCommandPlan({
      environment: "real-pre",
      scope: "full",
      envFileExists: true,
    });

    expect(plan).toEqual([
      {
        stepId: "docker-up",
        command: "docker",
        args: [
          "compose",
          "--env-file",
          ".env.real-pre",
          "-f",
          "docker-compose.real-pre.yml",
          "up",
          "-d",
          "--build",
          "--no-deps",
          "backend-real-pre",
          "frontend-real-pre",
        ],
        timeoutMs: 15 * 60_000,
      },
      {
        stepId: "docker-ps",
        command: "docker",
        args: [
          "compose",
          "--env-file",
          ".env.real-pre",
          "-f",
          "docker-compose.real-pre.yml",
          "ps",
        ],
        timeoutMs: 2 * 60_000,
      },
    ]);
    expect(JSON.stringify(plan)).not.toMatch(/\bdown\b|volume|postgres|redis|"-v"/iu);
  });

  it.each([
    ["backend", ["backend"]],
    ["frontend", ["frontend"]],
    ["full", ["backend", "frontend"]],
  ] as const)("test 环境 scope=%s 只选择目标应用服务且 env 不存在时省略 --env-file", (scope, services) => {
    const plan = buildDockerCommandPlan({
      environment: "test",
      scope,
      envFileExists: false,
    });

    expect(plan[0]?.args).toEqual([
      "compose",
      "-f",
      "docker-compose.test.yml",
      "up",
      "-d",
      "--build",
      "--no-deps",
      ...services,
    ]);
    expect(plan[1]?.args).toEqual([
      "compose",
      "-f",
      "docker-compose.test.yml",
      "ps",
    ]);
    expect(Object.isFrozen(plan)).toBe(true);
    expect(Object.isFrozen(plan[0]?.args)).toBe(true);
  });

  it("up 成功后才执行 ps，并为两步写入独立固定日志", async () => {
    const executed: ProcessOptions[] = [];
    const processRunner = vi.fn(async (options: ProcessOptions) => {
      executed.push(options);
      return successfulProcessResult(options);
    });
    const logWriter = vi.fn(async (input: { rawDir: string; stepId: string }) =>
      `${input.rawDir}/${input.stepId}.log`
    );

    const result = await runDockerCheck({
      environment: "real-pre",
      scope: "backend",
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task9",
      fileExists: () => true,
      processRunner,
      logWriter,
    });

    expect(result.status).toBe("PASS");
    expect(executed).toEqual(buildDockerCommandPlan({
      environment: "real-pre",
      scope: "backend",
      envFileExists: true,
    }).map(({ command, args, timeoutMs }) => ({
      command,
      args,
      timeoutMs,
      cwd: "D:/repo",
      secrets: [],
    })));
    expect(result.artifacts).toEqual([
      "runtime/qa/out/run-task9/docker-up.log",
      "runtime/qa/out/run-task9/docker-ps.log",
    ]);
  });

  it("up 失败时记录中文恢复信息并停止 ps", async () => {
    const processRunner = vi.fn(async (options: ProcessOptions): Promise<ProcessResult> => ({
      ...successfulProcessResult(options),
      exitCode: 17,
      success: false,
      rootCause: "Compose up 退出码 17。",
      safeRetry: "确认容器状态后重试一次。",
      stopCondition: "错误未变化时停止重试。",
    }));
    const logWriter = vi.fn(async (input: { rawDir: string; stepId: string }) =>
      `${input.rawDir}/${input.stepId}.log`
    );

    const result = await runDockerCheck({
      environment: "real-pre",
      scope: "full",
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task9-fail",
      fileExists: () => false,
      processRunner,
      logWriter,
    });

    expect(processRunner).toHaveBeenCalledTimes(1);
    expect(logWriter).toHaveBeenCalledTimes(1);
    expect(result.status).toBe("FAIL");
    expect(result.summary).toContain("根因：Compose up 退出码 17");
    expect(result.nextActions).toEqual([
      "安全重试：确认容器状态后重试一次。",
      "停止条件：错误未变化时停止重试。",
    ]);
    expect(result.artifacts).toEqual([
      "runtime/qa/out/run-task9-fail/docker-up.log",
    ]);
  });

  it("dry-run 为阻断性 SKIPPED 且不调用 runner 或 writer", async () => {
    const processRunner = vi.fn(async (options: ProcessOptions) =>
      successfulProcessResult(options)
    );
    const logWriter = vi.fn(async () => "不应写入.log");

    const result = await runDockerCheck({
      environment: "test",
      scope: "frontend",
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task9-dry",
      fileExists: () => true,
      processRunner,
      logWriter,
      dryRun: true,
    });

    expect(processRunner).not.toHaveBeenCalled();
    expect(logWriter).not.toHaveBeenCalled();
    expect(result).toMatchObject({ status: "SKIPPED", blocking: true, artifacts: [] });
    expect(result.nextActions.join("\n")).toContain("docker compose --env-file .env.test");
  });

  it("默认日志写入器只在固定 rawDir 下创建两份脱敏日志", async () => {
    const root = temporaryRepository();
    const secret = "docker-secret-417";
    const result = await runDockerCheck({
      environment: "test",
      scope: "backend",
      repoRoot: root,
      rawDir: "runtime/qa/out/run-task9-default-log",
      fileExists: () => false,
      processRunner: vi.fn(async (options: ProcessOptions) => ({
        ...successfulProcessResult(options),
        stdout: secret,
      })),
      secrets: [secret],
    });

    expect(result.status).toBe("PASS");
    expect(result.artifacts).toEqual([
      "runtime/qa/out/run-task9-default-log/docker-up.log",
      "runtime/qa/out/run-task9-default-log/docker-ps.log",
    ]);
    for (const artifact of result.artifacts) {
      const content = readFileSync(join(root, ...artifact.split("/")), "utf8");
      expect(content).toContain("[REDACTED]");
      expect(content).not.toContain(secret);
    }
  });

  it("畸形 runner 与伪造 artifact 均不能制造 PASS 或泄密", async () => {
    const secret = "docker-malformed-secret-528";
    const malformed = { ...successfulProcessResult({ command: "docker", args: [] }) };
    Object.defineProperty(malformed, "success", {
      enumerable: true,
      get: () => {
        throw new Error(`畸形结果 ${secret}`);
      },
    });
    const malformedResult = await runDockerCheck({
      environment: "test",
      scope: "backend",
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task9-malformed",
      fileExists: () => false,
      processRunner: vi.fn(async () => malformed as unknown as ProcessResult),
      logWriter: vi.fn(async () => "runtime/qa/out/run-task9-malformed/docker-up.log"),
      secrets: [secret],
    });
    const forgedResult = await runDockerCheck({
      environment: "test",
      scope: "backend",
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task9-forged",
      fileExists: () => false,
      processRunner: vi.fn(async (options: ProcessOptions) =>
        successfulProcessResult(options)
      ),
      logWriter: vi.fn(async () => "../../outside.log"),
    });

    expect(malformedResult.status).toBe("FAIL");
    expect(JSON.stringify(malformedResult)).not.toContain(secret);
    expect(forgedResult.status).toBe("FAIL");
    expect(forgedResult.artifacts).toEqual([]);
  });
});
