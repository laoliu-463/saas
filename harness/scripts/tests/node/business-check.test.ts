import {
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  symlinkSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { afterEach, describe, expect, it, vi } from "vitest";

import {
  buildBusinessCommandPlan,
  runBusinessCheck,
} from "../../lib/node/checks/business.js";
import type { ProcessOptions, ProcessResult } from "../../lib/node/core/process-runner.js";

const temporaryDirectories: string[] = [];

function temporaryRepository(): string {
  const root = mkdtempSync(join(tmpdir(), "harness-business-check-"));
  temporaryDirectories.push(root);
  return root;
}

afterEach(() => {
  for (const directory of temporaryDirectories.splice(0)) {
    rmSync(directory, { recursive: true, force: true });
  }
});

function processResult(options: ProcessOptions, success = true): ProcessResult {
  return {
    commandDisplay: [options.command, ...options.args].join(" "),
    exitCode: success ? 0 : 1,
    signal: null,
    timedOut: false,
    durationMs: 1,
    stdout: success ? "ok" : "",
    stderr: success ? "" : "failed",
    success,
    rootCause: success ? null : "业务命令退出码为 1。",
    safeRetry: success ? null : "修复直接失败原因后重试一次。",
    stopCondition: success ? null : "相同错误再次出现时停止重试。",
  };
}

describe("业务验证检查", () => {
  it.each([
    ["test", ["run", "e2e:v1-p0"]],
    ["real-pre", ["run", "e2e:real-pre:p0:preflight"]],
  ] as const)("%s 环境使用固定 npm 参数数组", (environment, args) => {
    expect(buildBusinessCommandPlan({ environment })).toEqual({
      stepId: "business-validation",
      command: "npm",
      args,
      timeoutMs: 30 * 60_000,
      custom: false,
    });
  });

  it.each([
    ["win32", "powershell", ["-NoProfile", "-NonInteractive", "-Command"]],
    ["linux", "sh", ["-lc"]],
  ] as const)("自定义命令在 %s 显式使用平台 shell 可执行文件", (platform, command, prefix) => {
    const customCommand = "mvn -f backend/pom.xml test; npm run e2e:v1-p0";
    const plan = buildBusinessCommandPlan({
      environment: "test",
      businessCommand: customCommand,
      platform,
    });

    expect(plan).toMatchObject({ command, custom: true });
    expect(plan.args).toEqual([...prefix, customCommand]);
    expect(Object.isFrozen(plan)).toBe(true);
    expect(Object.isFrozen(plan.args)).toBe(true);
  });

  it.each([
    "git push origin main",
    "git commit -m unsafe",
    "git -C . push origin main",
    "ssh app@server deploy",
    "scp artifact app@server:/opt/app",
    "powershell -File harness/scripts/commands/deploy-remote.ps1",
    "docker compose down -v",
    "docker compose up -d backend-real-pre",
    "docker --context remote compose up -d backend-real-pre",
    "npm run deploy:real-pre",
    "mvn deploy",
    "npm run test; & $env:REMOTE_DEPLOY_COMMAND",
    "npm run test; Invoke-Expression $env:COMMAND",
    "npm --prefix ../../outside run test",
    "npm --prefix C:/outside run test",
    "mvn -f ../../outside/pom.xml test",
    "mvn --file C:/outside/pom.xml test",
    "mvn -f backend/pom.xml --file=C:/outside/pom.xml test",
    "mvn -f backend/pom.xml -f../../outside/pom.xml test",
    "mvn -f backend/pom.xml test org.apache.maven.plugins:maven-deploy-plugin:3.1.4:deploy",
    "mvn -f backend/pom.xml test release:prepare",
    "npm --prefix frontend run test -- --config ../../outside/vitest.config.ts",
    "mvn -f backend/pom.xml test -Dtest=$MAVEN_TEST_SELECTOR",
  ])("拒绝可能提交、推送、远端部署或破坏容器的自定义命令：%s", (businessCommand) => {
    expect(() => buildBusinessCommandPlan({ environment: "real-pre", businessCommand }))
      .toThrow(/禁止|拒绝/u);
  });

  it("npm 尾随测试文件必须真实存在且 canonical 路径留在 frontend 内", () => {
    const root = temporaryRepository();
    const frontendSource = join(root, "frontend", "src");
    mkdirSync(frontendSource, { recursive: true });
    writeFileSync(join(frontendSource, "safe.test.ts"), "export {};", "utf8");
    const outside = temporaryRepository();
    writeFileSync(join(outside, "outside.test.ts"), "throw new Error('unsafe');", "utf8");
    symlinkSync(outside, join(frontendSource, "linked"), process.platform === "win32" ? "junction" : "dir");

    expect(buildBusinessCommandPlan({
      environment: "test",
      repoRoot: root,
      businessCommand: "npm --prefix frontend run test -- src/safe.test.ts",
    }).args.at(-1)).toBe("npm --prefix frontend run test -- src/safe.test.ts");
    expect(() => buildBusinessCommandPlan({
      environment: "test",
      repoRoot: root,
      businessCommand: "npm --prefix frontend run test -- src/linked/outside.test.ts",
    })).toThrow(/仓库|frontend|拒绝/u);
  });

  it("默认命令成功后写入固定脱敏日志并返回 PASS", async () => {
    const root = temporaryRepository();
    const secret = "business-secret-852";
    const executed: ProcessOptions[] = [];

    const result = await runBusinessCheck({
      environment: "real-pre",
      repoRoot: root,
      rawDir: "runtime/qa/out/run-task9-business",
      processRunner: vi.fn(async (options) => {
        executed.push(options);
        return { ...processResult(options), stdout: secret };
      }),
      secrets: [secret],
    });

    expect(result.status).toBe("PASS");
    expect(executed).toEqual([{
      command: "npm",
      args: ["run", "e2e:real-pre:p0:preflight"],
      cwd: root,
      timeoutMs: 30 * 60_000,
      secrets: [secret],
    }]);
    expect(result.artifacts).toEqual([
      "runtime/qa/out/run-task9-business/business-validation.log",
    ]);
    const content = readFileSync(
      join(root, "runtime", "qa", "out", "run-task9-business", "business-validation.log"),
      "utf8",
    );
    expect(content).toContain("[REDACTED]");
    expect(content).not.toContain(secret);
  });

  it("SkipBusinessValidation 返回阻断性 SKIPPED 且不执行或写日志", async () => {
    const processRunner = vi.fn(async (options: ProcessOptions) => processResult(options));
    const logWriter = vi.fn(async () => "不应写入.log");

    const result = await runBusinessCheck({
      environment: "test",
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task9-business-skip",
      processRunner,
      logWriter,
      skipBusinessValidation: true,
    });

    expect(result).toMatchObject({ status: "SKIPPED", blocking: true, artifacts: [] });
    expect(result.summary).toContain("显式跳过");
    expect(processRunner).not.toHaveBeenCalled();
    expect(logWriter).not.toHaveBeenCalled();
  });

  it("dry-run 返回命令计划但不执行或写日志", async () => {
    const processRunner = vi.fn(async (options: ProcessOptions) => processResult(options));
    const logWriter = vi.fn(async () => "不应写入.log");

    const result = await runBusinessCheck({
      environment: "test",
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task9-business-dry",
      processRunner,
      logWriter,
      dryRun: true,
    });

    expect(result).toMatchObject({ status: "SKIPPED", blocking: true, artifacts: [] });
    expect(result.nextActions).toContain("计划命令：npm run e2e:v1-p0");
    expect(processRunner).not.toHaveBeenCalled();
    expect(logWriter).not.toHaveBeenCalled();
  });

  it("失败结果返回中文恢复边界，畸形结果与伪造 artifact 不能制造 PASS", async () => {
    const logWriter = vi.fn(async (input: { rawDir: string; stepId: string }) =>
      `${input.rawDir}/${input.stepId}.log`
    );
    const failed = await runBusinessCheck({
      environment: "test",
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task9-business-fail",
      processRunner: vi.fn(async (options) => processResult(options, false)),
      logWriter,
    });
    const malformed = await runBusinessCheck({
      environment: "test",
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task9-business-malformed",
      processRunner: vi.fn(async () => ({ success: true }) as unknown as ProcessResult),
      logWriter,
    });
    const forged = await runBusinessCheck({
      environment: "test",
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task9-business-forged",
      processRunner: vi.fn(async (options) => processResult(options)),
      logWriter: vi.fn(async () => "../../outside.log"),
    });

    expect(failed.status).toBe("FAIL");
    expect(failed.summary).toContain("根因：业务命令退出码为 1");
    expect(failed.nextActions).toEqual([
      "安全重试：修复直接失败原因后重试一次。",
      "停止条件：相同错误再次出现时停止重试。",
    ]);
    expect(malformed.status).toBe("FAIL");
    expect(forged.status).toBe("FAIL");
    expect(forged.artifacts).toEqual([]);
  });
});
