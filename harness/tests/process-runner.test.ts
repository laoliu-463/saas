import { spawnSync } from "node:child_process";
import { existsSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { afterEach, describe, expect, it } from "vitest";

import {
  BUSINESS_VALIDATION_PURPOSE,
  DEFAULT_PROCESS_TIMEOUT_MS,
  MAX_PROCESS_TIMEOUT_MS,
  ProcessConfigurationError,
  runBusinessValidationShell,
  runProcess,
  type ProcessOptions,
} from "../src/core/process-runner.js";

const temporaryDirectories: string[] = [];

function createTemporaryDirectory(): string {
  const directory = mkdtempSync(join(tmpdir(), "harness-process-runner-"));
  temporaryDirectories.push(directory);
  return directory;
}

function hasChineseText(value: string | null): boolean {
  return value !== null && /[\u3400-\u9fff]/u.test(value);
}

function expectFailureRecovery(result: Awaited<ReturnType<typeof runProcess>>): void {
  expect(result.success).toBe(false);
  expect(hasChineseText(result.rootCause)).toBe(true);
  expect(hasChineseText(result.safeRetry)).toBe(true);
  expect(hasChineseText(result.stopCondition)).toBe(true);
}

interface TestProcessTree {
  readonly parentPid: number;
  readonly descendantPid: number;
}

function wait(milliseconds: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

function readTestProcessTree(path: string): TestProcessTree | null {
  if (!existsSync(path)) return null;
  const value = JSON.parse(readFileSync(path, "utf8")) as Partial<TestProcessTree>;
  if (!Number.isInteger(value.parentPid) || !Number.isInteger(value.descendantPid)) {
    return null;
  }
  return value as TestProcessTree;
}

function isProcessAlive(pid: number): boolean {
  try {
    process.kill(pid, 0);
    return true;
  } catch (error) {
    return (error as NodeJS.ErrnoException).code !== "ESRCH";
  }
}

function cleanupTestProcessTree(tree: TestProcessTree | null): void {
  if (tree === null) return;
  if (process.platform === "win32") {
    spawnSync("taskkill", ["/PID", String(tree.descendantPid), "/T", "/F"], {
      shell: false,
      windowsHide: true,
      stdio: "ignore",
    });
    return;
  }
  try {
    process.kill(-tree.parentPid, "SIGKILL");
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code !== "ESRCH") throw error;
  }
  try {
    process.kill(tree.descendantPid, "SIGKILL");
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code !== "ESRCH") throw error;
  }
}

afterEach(() => {
  for (const directory of temporaryDirectories.splice(0)) {
    rmSync(directory, { recursive: true, force: true });
  }
});

describe("标准进程执行入口", () => {
  it("使用 command 与只读参数数组捕获退出码、中文输出和环境覆盖", async () => {
    const args = [
      "-e",
      "console.log(process.argv[1]); console.error(process.env.HARNESS_TEST_VALUE)",
      "参数 中文",
    ] as const;

    const result = await runProcess({
      command: process.execPath,
      args,
      env: { HARNESS_TEST_VALUE: "环境 中文" },
    });

    expect(result).toMatchObject({
      exitCode: 0,
      signal: null,
      timedOut: false,
      stdout: "参数 中文",
      stderr: "环境 中文",
      success: true,
      rootCause: null,
      safeRetry: null,
      stopCondition: null,
    });
    expect(result.durationMs).toBeGreaterThanOrEqual(0);
    expect(result.commandDisplay).toContain("参数 中文");
    expect(result).not.toHaveProperty("env");
  });

  it("默认继承 cwd，并公开明确的超时默认值与上限", async () => {
    expect(DEFAULT_PROCESS_TIMEOUT_MS).toBe(60_000);
    expect(MAX_PROCESS_TIMEOUT_MS).toBe(30 * 60_000);

    const result = await runProcess({
      command: process.execPath,
      args: ["-e", "console.log(process.cwd())"],
    });

    expect(result.success).toBe(true);
    expect(result.stdout.toLocaleLowerCase()).toBe(process.cwd().toLocaleLowerCase());
  });

  it("标准入口把 Shell 元字符作为普通参数且不会执行附带动作", async () => {
    const directory = createTemporaryDirectory();
    const marker = join(directory, "不应创建.txt");
    const dangerousArgument =
      process.platform === "win32"
        ? `& echo injected > "${marker}" & rem | ; $()`
        : `; printf injected > '${marker.replaceAll("'", "'\\''")}'; : '& | ; $()'`;

    const result = await runProcess({
      command: process.execPath,
      args: ["-e", "console.log(process.argv[1])", dangerousArgument],
    });

    expect(result.success).toBe(true);
    expect(result.stdout).toBe(dangerousArgument);
    expect(existsSync(marker)).toBe(false);
  });

  it("规范化 CRLF、CR、尾随空白和末尾空行", async () => {
    const result = await runProcess({
      command: process.execPath,
      args: ["-e", 'process.stdout.write("甲  \\r\\n\\r乙\\t \\n\\n")'],
    });

    expect(result.success).toBe(true);
    expect(result.stdout).toBe("甲\n\n乙");
  });

  it("非零退出转为带中文恢复建议的结构化失败", async () => {
    const result = await runProcess({
      command: process.execPath,
      args: ["-e", "process.exit(7)"],
    });

    expect(result.exitCode).toBe(7);
    expect(result.timedOut).toBe(false);
    expect(result.rootCause).toContain("退出码 7");
    expectFailureRecovery(result);
  });

  it("失败恢复字段统一清理调用方显式密钥", async () => {
    const secrets = ["退出码 7", "标准错误", "重复执行"] as const;
    const result = await runProcess({
      command: process.execPath,
      args: ["-e", "process.exit(7)"],
      secrets,
    });

    expect(result.exitCode).toBe(7);
    expectFailureRecovery(result);
    const evidenceText = JSON.stringify(result);
    for (const secret of secrets) {
      expect(evidenceText).not.toContain(secret);
    }
  });

  it("命令不存在时不抛异常并返回无法启动的结构化失败", async () => {
    const result = await runProcess({
      command: `harness-command-not-found-${Date.now()}`,
      args: [],
    });

    expect(result.exitCode).toBeNull();
    expect(result.signal).toBeNull();
    expect(result.timedOut).toBe(false);
    expect(result.rootCause).toContain("无法启动");
    expect(result.stderr).not.toContain("at ");
    expectFailureRecovery(result);
  });

  it("超时会终止直接子进程并返回 timedOut", async () => {
    const startedAt = Date.now();
    const result = await runProcess({
      command: process.execPath,
      args: ["-e", "setTimeout(() => {}, 10_000)"],
      timeoutMs: 80,
    });

    expect(Date.now() - startedAt).toBeLessThan(3_000);
    expect(result.timedOut).toBe(true);
    expect(result.rootCause).toContain("超时");
    expectFailureRecovery(result);
  });

  it("后代继承输出管道时最终 watchdog 仍在有界时间内返回", async () => {
    const directory = createTemporaryDirectory();
    const pidFile = join(directory, "descendant.pid");
    const descendantLifetimeMs = 5_000;
    const parentScript = [
      'import { spawn } from "node:child_process";',
      'import { writeFileSync } from "node:fs";',
      `const child = spawn(${JSON.stringify(process.execPath)}, ["-e", ${JSON.stringify(`const timer = setInterval(() => { process.stdout.write("held\\n"); process.stderr.write("held\\n"); }, 100); setTimeout(() => { clearInterval(timer); }, ${descendantLifetimeMs})`)}], {`,
      '  stdio: ["ignore", "inherit", "inherit"], windowsHide: true, detached: true,',
      "});",
      `writeFileSync(${JSON.stringify(pidFile)}, String(child.pid), "utf8");`,
      "child.unref();",
    ].join("\n");
    const startedAt = Date.now();

    try {
      const result = await runProcess({
        command: process.execPath,
        args: ["--input-type=module", "-e", parentScript],
        timeoutMs: 2_000,
      });

      expect(existsSync(pidFile)).toBe(true);
      expect(result.timedOut).toBe(true);
      expect(result.success).toBe(false);
      expect(Date.now() - startedAt).toBeLessThan(3_500);
      expect(result.durationMs).toBeLessThan(3_500);
      expectFailureRecovery(result);
      expect(result.rootCause).toContain("进程树终止未确认");
      expect(result.rootCause).not.toContain("命令无法启动");
    } finally {
      if (existsSync(pidFile)) {
        const descendantPid = Number.parseInt(readFileSync(pidFile, "utf8"), 10);
        if (Number.isInteger(descendantPid) && descendantPid > 0) {
          try {
            process.kill(descendantPid, "SIGKILL");
          } catch (error) {
            if ((error as NodeJS.ErrnoException).code !== "ESRCH") throw error;
          }
        }
      }
    }
  });

  it("超时会终止进程树并阻止后代延迟副作用", async () => {
    const directory = createTemporaryDirectory();
    const pidFile = join(directory, "process-tree.json");
    const sideEffectFile = join(directory, "不应出现的后代副作用.txt");
    const descendantScript = [
      'import { writeFileSync } from "node:fs";',
      `setTimeout(() => writeFileSync(${JSON.stringify(sideEffectFile)}, "orphan", "utf8"), 6_000);`,
      "setTimeout(() => {}, 9_000);",
    ].join("\n");
    const parentScript = [
      'import { spawn } from "node:child_process";',
      'import { writeFileSync } from "node:fs";',
      `const child = spawn(${JSON.stringify(process.execPath)}, ["--input-type=module", "-e", ${JSON.stringify(descendantScript)}], {`,
      `  stdio: ["ignore", "ignore", "ignore"], windowsHide: true, detached: ${process.platform === "win32"},`,
      "});",
      `writeFileSync(${JSON.stringify(pidFile)}, JSON.stringify({ parentPid: process.pid, descendantPid: child.pid }), "utf8");`,
      "setTimeout(() => {}, 10_000);",
    ].join("\n");
    let tree: TestProcessTree | null = null;

    try {
      const result = await runProcess({
        command: process.execPath,
        args: ["--input-type=module", "-e", parentScript],
        timeoutMs: 2_500,
      });
      tree = readTestProcessTree(pidFile);
      expect(tree).not.toBeNull();

      await wait(3_000);

      expect(existsSync(sideEffectFile)).toBe(false);
      expect(tree === null ? true : isProcessAlive(tree.descendantPid)).toBe(false);
      expect(result.timedOut).toBe(true);
      expect(result.rootCause).toContain("进程树终止已确认");
      expectFailureRecovery(result);
    } finally {
      tree ??= readTestProcessTree(pidFile);
      cleanupTestProcessTree(tree);
      await wait(100);
    }
  }, 15_000);

  it.skipIf(process.platform === "win32")("信号退出转为结构化失败", async () => {
    const result = await runProcess({
      command: process.execPath,
      args: ["-e", 'process.kill(process.pid, "SIGTERM")'],
    });

    expect(result.success).toBe(false);
    expect(result.exitCode).toBeNull();
    expect(result.signal).not.toBeNull();
    expect(result.rootCause).toContain("信号");
    expectFailureRecovery(result);
  });

  it("commandDisplay、stdout、stderr 和恢复文本不会泄漏显式密钥", async () => {
    const shortSecret = "task4-short-secret";
    const longSecret = `${shortSecret}-extended`;
    const result = await runProcess({
      command: process.execPath,
      args: [
        "-e",
        "console.log(process.argv[1]); console.error(process.argv[2]); process.exit(9)",
        `token=${longSecret}`,
        shortSecret,
      ],
      secrets: [shortSecret, longSecret],
    });

    const evidenceText = JSON.stringify(result);
    expect(evidenceText).not.toContain(shortSecret);
    expect(evidenceText).not.toContain(longSecret);
    expect(result.commandDisplay).toContain("[REDACTED]");
    expect(result.stdout).toContain("[REDACTED]");
    expect(result.stderr).toContain("[REDACTED]");
    expectFailureRecovery(result);
  });

  it("嵌套 JSON 参数中的转义密钥不会泄漏到 commandDisplay 或结果", async () => {
    const tokenSecret = String.raw`token-head"token-tail-731\token-end-731`;
    const passwordSecret = String.raw`password-head"password-tail-842\password-end-842`;
    const nestedArgument = JSON.stringify({
      credentials: { token: tokenSecret, password: passwordSecret },
      label: "中文参数保留",
    });
    const result = await runProcess({
      command: process.execPath,
      args: ["-e", "process.exit(0)", nestedArgument],
      secrets: [tokenSecret, passwordSecret],
    });

    expect(result.success).toBe(true);
    expect(result.commandDisplay).toContain("中文参数保留");
    expect(result.commandDisplay.match(/\[REDACTED\]/gu) ?? []).toHaveLength(2);
    const evidenceText = JSON.stringify(result);
    const forbiddenFragments = [
      tokenSecret,
      passwordSecret,
      JSON.stringify(tokenSecret).slice(1, -1),
      JSON.stringify(passwordSecret).slice(1, -1),
      "token-tail-731",
      "token-end-731",
      "password-tail-842",
      "password-end-842",
    ];
    for (const fragment of forbiddenFragments) {
      expect(result.commandDisplay).not.toContain(fragment);
      expect(evidenceText).not.toContain(fragment);
    }
  });
});

describe("配置边界", () => {
  it.each([
    [{ command: "", args: [] }, /命令不能为空/u],
    [{ command: process.execPath, args: [], timeoutMs: 0 }, /超时时间必须大于 0/u],
    [{ command: process.execPath, args: [], timeoutMs: -1 }, /超时时间必须大于 0/u],
    [
      { command: process.execPath, args: [], timeoutMs: MAX_PROCESS_TIMEOUT_MS + 1 },
      /不能超过/u,
    ],
  ] as const)("拒绝非法标准执行配置 %#", async (options, expectedMessage) => {
    expect(() => runProcess(options)).toThrow(ProcessConfigurationError);
    expect(() => runProcess(options)).toThrow(expectedMessage);
  });

  it("运行时拒绝普通调用者注入 shell=true", async () => {
    const unsafeOptions = {
      command: process.execPath,
      args: [],
      shell: true,
    } as unknown as ProcessOptions;

    expect(() => runProcess(unsafeOptions)).toThrow(/标准入口禁止 Shell/u);
  });
});

describe("显式业务验证 Shell 入口", () => {
  it("只有固定 purpose 才通过当前平台 Shell 执行", async () => {
    const directory = createTemporaryDirectory();
    const marker = join(directory, "业务验证.txt");
    const command =
      process.platform === "win32"
        ? `echo shell-ok>"${marker}"`
        : `printf shell-ok > '${marker.replaceAll("'", "'\\''")}'`;

    const result = await runBusinessValidationShell({
      purpose: BUSINESS_VALIDATION_PURPOSE,
      command,
    });

    expect(result.success).toBe(true);
    expect(readFileSync(marker, "utf8").trim()).toBe("shell-ok");
  });

  it("运行时拒绝伪造的业务 purpose", () => {
    expect(() =>
      runBusinessValidationShell({
        purpose: "GENERAL" as typeof BUSINESS_VALIDATION_PURPOSE,
        command: "echo unsafe",
      }),
    ).toThrow(/仅允许 BUSINESS_VALIDATION/u);
  });
});
