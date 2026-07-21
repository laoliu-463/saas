import { join, resolve } from "node:path";
import { pathToFileURL } from "node:url";

import { describe, expect, it, vi } from "vitest";

import { createCheckResult, createRunResult } from "../src/core/result.js";
import {
  inspectCli,
  renderInspectHelp,
  resolveInspectRepoRoot,
  type InspectCliDependencies,
} from "../src/cli/inspect.js";

function dependencies(status: "PASS" | "FAIL" | "BLOCKED"): {
  readonly dependency: InspectCliDependencies;
  readonly stdout: string[];
  readonly stderr: string[];
} {
  const stdout: string[] = [];
  const stderr: string[] = [];
  return {
    stdout,
    stderr,
    dependency: {
      cwd: () => "D:/repo",
      runInspect: vi.fn(async () => createRunResult([
        createCheckResult({
          checkId: "inspect.fixture",
          title: "检查测试前置条件",
          status,
          blocking: true,
          summary: status === "PASS" ? "测试前置条件已满足。" : "测试前置条件未满足。",
          nextActions: status === "PASS" ? [] : ["修复测试前置条件后重试。"],
          artifacts: [],
        }),
      ])),
      writeStdout: (message) => stdout.push(message),
      writeStderr: (message) => stderr.push(message),
    },
  };
}

describe("inspect CLI", () => {
  it("从 CLI 模块位置解析仓库根目录，不受 npm --prefix 改变 cwd 影响", () => {
    const repoRoot = resolve("fixture", "repo");
    const moduleUrl = pathToFileURL(join(repoRoot, "harness", "src", "cli", "inspect.ts")).href;

    expect(resolveInspectRepoRoot(moduleUrl)).toBe(repoRoot);
  });

  it("--help 使用中文说明命令、阶段、退出码和只读边界", async () => {
    const fixture = dependencies("PASS");

    const exitCode = await inspectCli(["--help"], fixture.dependency);
    const output = fixture.stdout.join("\n");

    expect(exitCode).toBe(0);
    expect(output).toBe(renderInspectHelp());
    expect(output).toContain("npm run harness:node:inspect -- --env real-pre");
    expect(output).toContain("只读");
    expect(output).toContain("阶段");
    expect(output).toContain("退出码");
  });

  it.each([
    ["PASS", 0],
    ["FAIL", 1],
    ["BLOCKED", 2],
  ] as const)("将 %s 映射为退出码 %i", async (status, expectedExitCode) => {
    const fixture = dependencies(status);

    const exitCode = await inspectCli(
      ["--env", "real-pre"],
      fixture.dependency,
    );

    expect(exitCode).toBe(expectedExitCode);
    expect(fixture.stdout.join("\n")).toContain("检查摘要");
    expect(fixture.stdout.join("\n")).toContain(status);
  });

  it("PARTIAL 映射退出码 2", async () => {
    const stdout: string[] = [];
    const dependency: InspectCliDependencies = {
      cwd: () => "D:/repo",
      runInspect: vi.fn(async () => createRunResult([
        createCheckResult({
          checkId: "inspect.fixture",
          title: "检查测试前置条件",
          status: "WARN",
          blocking: true,
          summary: "测试前置条件需要人工复核。",
          nextActions: ["人工复核后重试。"],
          artifacts: [],
        }),
      ])),
      writeStdout: (message) => stdout.push(message),
      writeStderr: () => undefined,
    };

    expect(await inspectCli(["--env", "test"], dependency)).toBe(2);
    expect(stdout.join("\n")).toContain("PARTIAL");
  });

  it.each([
    { args: [] },
    { args: ["--env"] },
    { args: ["--env", "production"] },
    { args: ["--unknown"] },
    { args: ["--env", "test", "extra"] },
  ])("非法参数返回 3 且输出中文修复指引：$args", async ({ args }) => {
    const fixture = dependencies("PASS");

    const exitCode = await inspectCli(args, fixture.dependency);

    expect(exitCode).toBe(3);
    expect(fixture.stderr.join("\n")).toContain("参数错误");
    expect(fixture.stderr.join("\n")).toContain("--help");
  });

  it("契约错误返回 3，普通执行错误返回 2，均不输出堆栈", async () => {
    const contractStdout: string[] = [];
    const contractStderr: string[] = [];
    const contractDependency: InspectCliDependencies = {
      cwd: () => "D:/repo",
      runInspect: vi.fn(async () => {
        const error = new Error("环境契约损坏");
        error.name = "EnvironmentContractError";
        throw error;
      }),
      writeStdout: (message) => contractStdout.push(message),
      writeStderr: (message) => contractStderr.push(message),
    };

    expect(await inspectCli(["--env", "real-pre"], contractDependency)).toBe(3);
    expect(contractStderr.join("\n")).toContain("契约错误");
    expect(contractStderr.join("\n")).not.toContain(" at ");

    const runtime = dependencies("PASS");
    runtime.dependency.runInspect = vi.fn(async () => {
      throw new Error("只读检查执行异常");
    });
    expect(await inspectCli(["--env", "real-pre"], runtime.dependency)).toBe(2);
    expect(runtime.stderr.join("\n")).toContain("执行受阻");
    expect(runtime.stderr.join("\n")).not.toContain(" at ");
  });
});
