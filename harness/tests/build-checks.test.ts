import {
  mkdtempSync,
  readFileSync,
  rmSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { afterEach, describe, expect, it, vi } from "vitest";

import type { ProcessOptions, ProcessResult } from "../src/core/process-runner.js";
import {
  buildBackendCommandPlan,
  runBackendCheck,
  type BuildLogInput,
} from "../src/checks/backend.js";
import {
  buildFrontendCommandPlan,
  runFrontendCheck,
} from "../src/checks/frontend.js";

const temporaryDirectories: string[] = [];

function temporaryRepository(): string {
  const root = mkdtempSync(join(tmpdir(), "harness-build-check-"));
  temporaryDirectories.push(root);
  return root;
}

afterEach(() => {
  for (const directory of temporaryDirectories.splice(0)) {
    rmSync(directory, { recursive: true, force: true });
  }
});

function processResult(success = true): ProcessResult {
  return {
    commandDisplay: "已脱敏命令",
    exitCode: success ? 0 : 1,
    signal: null,
    timedOut: false,
    durationMs: 10,
    stdout: success ? "构建成功" : "",
    stderr: success ? "" : "构建失败",
    success,
    rootCause: success ? null : "命令以退出码 1 结束。",
    safeRetry: success ? null : "修复直接原因后重试一次。",
    stopCondition: success ? null : "错误未变化时停止重试。",
  };
}

describe("后端构建检查命令计划", () => {
  it("严格按测试、打包顺序生成固定 Maven 参数", () => {
    expect(buildBackendCommandPlan()).toEqual([
      {
        stepId: "backend-test",
        command: "mvn",
        args: ["-f", "backend/pom.xml", "test"],
        timeoutMs: 30 * 60_000,
      },
      {
        stepId: "backend-package",
        command: "mvn",
        args: ["-f", "backend/pom.xml", "-DskipTests", "package"],
        timeoutMs: 15 * 60_000,
      },
    ]);
  });

  it.each([
    ["backend", buildBackendCommandPlan],
    ["frontend", buildFrontendCommandPlan],
  ] as const)("%s 命令计划为深度冻结快照", (_name, createPlan) => {
    const plan = createPlan();
    const first = plan[0];
    expect(first).toBeDefined();
    expect(Object.isFrozen(plan)).toBe(true);
    expect(Object.isFrozen(first)).toBe(true);
    expect(Object.isFrozen(first?.args)).toBe(true);
    expect(() => {
      (first as { command: string }).command = "unsafe";
    }).toThrow();
    expect(createPlan()[0]?.command).not.toBe("unsafe");
  });

  it("两步成功时写入独立日志并返回全部 artifact", async () => {
    const executed: ProcessOptions[] = [];
    const written: BuildLogInput[] = [];
    const processRunner = vi.fn(async (options: ProcessOptions) => {
      executed.push(options);
      return processResult();
    });
    const logWriter = vi.fn(async (input: BuildLogInput) => {
      written.push(input);
      return `${input.rawDir}/${input.stepId}.log`;
    });

    const result = await runBackendCheck({
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task8",
      processRunner,
      logWriter,
    });

    expect(result.status).toBe("PASS");
    expect(executed.map(({ command, args, cwd, timeoutMs }) => ({ command, args, cwd, timeoutMs })))
      .toEqual(buildBackendCommandPlan().map(({ command, args, timeoutMs }) => ({
        command,
        args,
        cwd: "D:/repo",
        timeoutMs,
      })));
    expect(written.map(({ stepId }) => stepId)).toEqual([
      "backend-test",
      "backend-package",
    ]);
    expect(result.artifacts).toEqual([
      "runtime/qa/out/run-task8/backend-test.log",
      "runtime/qa/out/run-task8/backend-package.log",
    ]);
  });

  it("首步失败时停止后续命令并给出三段中文恢复提示", async () => {
    const processRunner = vi.fn(async () => processResult(false));
    const logWriter = vi.fn(async (input: BuildLogInput) =>
      `${input.rawDir}/${input.stepId}.log`
    );

    const result = await runBackendCheck({
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task8",
      processRunner,
      logWriter,
    });

    expect(processRunner).toHaveBeenCalledTimes(1);
    expect(logWriter).toHaveBeenCalledTimes(1);
    expect(result.status).toBe("FAIL");
    expect(result.summary).toContain("根因：命令以退出码 1 结束");
    expect(result.nextActions).toEqual([
      "安全重试：修复直接原因后重试一次。",
      "停止条件：错误未变化时停止重试。",
    ]);
    expect(result.artifacts).toEqual([
      "runtime/qa/out/run-task8/backend-test.log",
    ]);
  });

  it("dry-run 只返回完整计划，不执行进程也不创建日志", async () => {
    const processRunner = vi.fn(async () => processResult());
    const logWriter = vi.fn(async (input: BuildLogInput) =>
      `${input.rawDir}/${input.stepId}.log`
    );

    const result = await runBackendCheck({
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task8",
      processRunner,
      logWriter,
      dryRun: true,
    });

    expect(processRunner).not.toHaveBeenCalled();
    expect(logWriter).not.toHaveBeenCalled();
    expect(result.status).toBe("SKIPPED");
    expect(result.artifacts).toEqual([]);
    expect(result.nextActions).toEqual([
      "计划命令：mvn -f backend/pom.xml test",
      "计划命令：mvn -f backend/pom.xml -DskipTests package",
      "移除 dry-run 参数后执行后端验证。",
    ]);
  });

  it("默认日志写入器只在固定 rawDir 下记录 ProcessResult 白名单字段", async () => {
    const root = temporaryRepository();
    const safeResult = {
      ...processResult(),
      untrustedExtra: "不得写入日志的额外字段",
    } as ProcessResult;

    const result = await runBackendCheck({
      repoRoot: root,
      rawDir: "runtime/qa/out/run-task8",
      processRunner: vi.fn(async () => safeResult),
    });

    expect(result.status).toBe("PASS");
    for (const artifact of result.artifacts) {
      const content = readFileSync(join(root, ...artifact.split("/")), "utf8");
      expect(content).toContain('"commandDisplay": "已脱敏命令"');
      expect(content).not.toContain("untrustedExtra");
      expect(content).not.toContain("不得写入日志的额外字段");
    }
  });

  it("日志写入失败时停止后续命令并返回脱敏 FAIL", async () => {
    const secret = "writer-secret-731";
    const processRunner = vi.fn(async () => processResult());
    const logWriter = vi.fn(async () => {
      throw new Error(`日志磁盘失败 ${secret}`);
    });

    const result = await runBackendCheck({
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task8",
      processRunner,
      logWriter,
      secrets: [secret],
    });

    expect(processRunner).toHaveBeenCalledTimes(1);
    expect(result.status).toBe("FAIL");
    expect(result.summary).toContain("日志写入失败");
    expect(JSON.stringify(result)).not.toContain(secret);
    expect(result.artifacts).toEqual([]);
  });

  it("注入日志写入器不能伪造或穿越 artifact 路径", async () => {
    const processRunner = vi.fn(async () => processResult());
    const logWriter = vi.fn(async () => "../../outside.log");

    const result = await runBackendCheck({
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task8",
      processRunner,
      logWriter,
    });

    expect(processRunner).toHaveBeenCalledTimes(1);
    expect(result.status).toBe("FAIL");
    expect(result.summary).toContain("artifact 路径不符合固定计划");
    expect(result.artifacts).toEqual([]);
  });

  it("进程执行器抛异常时收敛成脱敏 FAIL 且不写日志", async () => {
    const secret = "runner-secret-842";
    const processRunner = vi.fn(async () => {
      throw new Error(`启动异常 ${secret}`);
    });
    const logWriter = vi.fn(async (input: BuildLogInput) =>
      `${input.rawDir}/${input.stepId}.log`
    );

    const result = await runBackendCheck({
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task8",
      processRunner,
      logWriter,
      secrets: [secret],
    });

    expect(processRunner).toHaveBeenCalledTimes(1);
    expect(logWriter).not.toHaveBeenCalled();
    expect(result.status).toBe("FAIL");
    expect(result.summary).toContain("执行器异常");
    expect(JSON.stringify(result)).not.toContain(secret);
  });

  it("畸形 ProcessResult 的恶意属性不会泄密或击穿", async () => {
    const secret = "malformed-secret-953";
    const malformed = { ...processResult() } as Record<string, unknown>;
    Object.defineProperty(malformed, "success", {
      enumerable: true,
      get: () => {
        throw new Error(`畸形结果 ${secret}`);
      },
    });
    const processRunner = vi.fn(async () => malformed as unknown as ProcessResult);
    const logWriter = vi.fn(async (input: BuildLogInput) =>
      `${input.rawDir}/${input.stepId}.log`
    );

    const result = await runBackendCheck({
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task8",
      processRunner,
      logWriter,
      secrets: [secret],
    });

    expect(processRunner).toHaveBeenCalledTimes(1);
    expect(logWriter).not.toHaveBeenCalled();
    expect(result.status).toBe("FAIL");
    expect(result.summary).toContain("结果不符合契约");
    expect(JSON.stringify(result)).not.toContain(secret);
  });

  it("拒绝携带秘密文本的非法 signal 且不落盘", async () => {
    const secret = "signal-secret-318";
    const malformed = {
      ...processResult(false),
      signal: `SIGTERM-${secret}`,
    } as unknown as ProcessResult;
    const processRunner = vi.fn(async () => malformed);
    const logWriter = vi.fn(async (input: BuildLogInput) =>
      `${input.rawDir}/${input.stepId}.log`
    );

    const result = await runBackendCheck({
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task8",
      processRunner,
      logWriter,
      secrets: [secret],
    });

    expect(result.status).toBe("FAIL");
    expect(result.summary).toContain("结果不符合契约");
    expect(JSON.stringify(result)).not.toContain(secret);
    expect(logWriter).not.toHaveBeenCalled();
  });

  it("合法 signal 命中秘密值时拒绝生成伪合法脱敏 signal", async () => {
    const secret = "SIGTERM";
    const signaled = {
      ...processResult(false),
      exitCode: null,
      signal: "SIGTERM",
    } satisfies ProcessResult;
    const processRunner = vi.fn(async () => signaled);
    const logWriter = vi.fn(async (input: BuildLogInput) =>
      `${input.rawDir}/${input.stepId}.log`
    );

    const result = await runBackendCheck({
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task8",
      processRunner,
      logWriter,
      secrets: [secret],
    });

    expect(result.status).toBe("FAIL");
    expect(result.summary).toContain("结果不符合契约");
    expect(JSON.stringify(result)).not.toContain(secret);
    expect(logWriter).not.toHaveBeenCalled();
  });

  it("执行器不能通过篡改 secrets 输入关闭后续日志脱敏", async () => {
    const secret = "opaque-secret-value-164";
    let serializedLogInput = "";
    const processRunner = vi.fn(async (options: ProcessOptions) => {
      (options.secrets as string[]).splice(0);
      return { ...processResult(), stdout: secret };
    });
    const logWriter = vi.fn(async (input: BuildLogInput) => {
      serializedLogInput = JSON.stringify(input.result);
      return `${input.rawDir}/${input.stepId}.log`;
    });

    const result = await runBackendCheck({
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task8",
      processRunner,
      logWriter,
      secrets: [secret],
    });

    expect(result.status).toBe("PASS");
    expect(serializedLogInput).toContain("[REDACTED]");
    expect(serializedLogInput).not.toContain(secret);
  });
});

describe("前端构建检查命令计划", () => {
  it("严格按安装、类型、测试、构建顺序生成固定 npm 参数", () => {
    expect(buildFrontendCommandPlan()).toEqual([
      {
        stepId: "frontend-install",
        command: "npm",
        args: ["--prefix", "frontend", "ci"],
        timeoutMs: 10 * 60_000,
      },
      {
        stepId: "frontend-typecheck",
        command: "npm",
        args: ["--prefix", "frontend", "run", "typecheck"],
        timeoutMs: 10 * 60_000,
      },
      {
        stepId: "frontend-test",
        command: "npm",
        args: ["--prefix", "frontend", "run", "test"],
        timeoutMs: 15 * 60_000,
      },
      {
        stepId: "frontend-build",
        command: "npm",
        args: ["--prefix", "frontend", "run", "build"],
        timeoutMs: 10 * 60_000,
      },
    ]);
  });

  it("四步成功时写入独立日志并返回全部 artifact", async () => {
    const executed: ProcessOptions[] = [];
    const processRunner = vi.fn(async (options: ProcessOptions) => {
      executed.push(options);
      return processResult();
    });
    const logWriter = vi.fn(async (input: BuildLogInput) =>
      `${input.rawDir}/${input.stepId}.log`
    );

    const result = await runFrontendCheck({
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task8",
      processRunner,
      logWriter,
    });

    expect(result.status).toBe("PASS");
    expect(executed.map(({ command, args, cwd, timeoutMs }) => ({ command, args, cwd, timeoutMs })))
      .toEqual(buildFrontendCommandPlan().map(({ command, args, timeoutMs }) => ({
        command,
        args,
        cwd: "D:/repo",
        timeoutMs,
      })));
    expect(result.artifacts).toEqual([
      "runtime/qa/out/run-task8/frontend-install.log",
      "runtime/qa/out/run-task8/frontend-typecheck.log",
      "runtime/qa/out/run-task8/frontend-test.log",
      "runtime/qa/out/run-task8/frontend-build.log",
    ]);
  });

  it("类型检查失败时停止测试与构建并返回恢复提示", async () => {
    const processRunner = vi.fn()
      .mockResolvedValueOnce(processResult())
      .mockResolvedValueOnce(processResult(false));
    const logWriter = vi.fn(async (input: BuildLogInput) =>
      `${input.rawDir}/${input.stepId}.log`
    );

    const result = await runFrontendCheck({
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task8",
      processRunner,
      logWriter,
    });

    expect(processRunner).toHaveBeenCalledTimes(2);
    expect(logWriter).toHaveBeenCalledTimes(2);
    expect(result.status).toBe("FAIL");
    expect(result.summary).toContain("frontend-typecheck");
    expect(result.summary).toContain("根因：命令以退出码 1 结束");
    expect(result.nextActions).toHaveLength(2);
    expect(result.artifacts).toEqual([
      "runtime/qa/out/run-task8/frontend-install.log",
      "runtime/qa/out/run-task8/frontend-typecheck.log",
    ]);
  });

  it("dry-run 只展示四步计划且不执行或写日志", async () => {
    const processRunner = vi.fn(async () => processResult());
    const logWriter = vi.fn(async (input: BuildLogInput) =>
      `${input.rawDir}/${input.stepId}.log`
    );

    const result = await runFrontendCheck({
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task8",
      processRunner,
      logWriter,
      dryRun: true,
    });

    expect(processRunner).not.toHaveBeenCalled();
    expect(logWriter).not.toHaveBeenCalled();
    expect(result.status).toBe("SKIPPED");
    expect(result.artifacts).toEqual([]);
    expect(result.nextActions).toEqual([
      "计划命令：npm --prefix frontend ci",
      "计划命令：npm --prefix frontend run typecheck",
      "计划命令：npm --prefix frontend run test",
      "计划命令：npm --prefix frontend run build",
      "移除 dry-run 参数后执行前端验证。",
    ]);
  });

  it("默认日志写入器为四个前端步骤生成固定 artifact", async () => {
    const root = temporaryRepository();

    const result = await runFrontendCheck({
      repoRoot: root,
      rawDir: "runtime/qa/out/run-task8-frontend",
      processRunner: vi.fn(async () => processResult()),
    });

    expect(result.status).toBe("PASS");
    expect(result.artifacts).toEqual([
      "runtime/qa/out/run-task8-frontend/frontend-install.log",
      "runtime/qa/out/run-task8-frontend/frontend-typecheck.log",
      "runtime/qa/out/run-task8-frontend/frontend-test.log",
      "runtime/qa/out/run-task8-frontend/frontend-build.log",
    ]);
    for (const artifact of result.artifacts) {
      expect(readFileSync(join(root, ...artifact.split("/")), "utf8"))
        .toContain('"success": true');
    }
  });
});
