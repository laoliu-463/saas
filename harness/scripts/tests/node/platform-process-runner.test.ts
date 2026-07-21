import { describe, expect, it, vi } from "vitest";

import type { ProcessOptions, ProcessResult } from "../../lib/node/core/process-runner.js";
import { createPlatformProcessRunner } from "../../lib/node/core/platform-process-runner.js";

function processResult(): ProcessResult {
  return {
    commandDisplay: "底层适配命令",
    exitCode: 0,
    signal: null,
    timedOut: false,
    durationMs: 1,
    stdout: "",
    stderr: "",
    success: true,
    rootCause: null,
    safeRetry: null,
    stopCondition: null,
  };
}

describe("平台进程适配", () => {
  it.each(["npm", "mvn"] as const)(
    "Windows 将 %s 适配为固定 cmd.exe 调用并保留逻辑命令展示",
    async (command) => {
      const baseRunner = vi.fn(async (_options: ProcessOptions) => processResult());
      const runner = createPlatformProcessRunner({
        baseRunner,
        platform: "win32",
        commandInterpreter: "C:/Windows/System32/cmd.exe",
      });

      const result = await runner({
        command,
        args: ["--version"],
        cwd: "D:/repo",
      });

      expect(baseRunner).toHaveBeenCalledWith({
        command: "C:/Windows/System32/cmd.exe",
        args: ["/d", "/s", "/c", `${command}.cmd`, "--version"],
        cwd: "D:/repo",
      });
      expect(result.commandDisplay).toBe(`"${command}" "--version"`);
      expect(JSON.stringify(vi.mocked(baseRunner).mock.calls)).not.toContain("shell");
    },
  );

  it.each(["linux", "darwin"] as const)(
    "%s 直接使用逻辑命令与参数",
    async (platform) => {
      const baseRunner = vi.fn(async (_options: ProcessOptions) => processResult());
      const runner = createPlatformProcessRunner({ baseRunner, platform });
      const logical = { command: "npm", args: ["--version"], cwd: "/repo" };

      const result = await runner(logical);

      expect(baseRunner).toHaveBeenCalledWith(logical);
      expect(result.commandDisplay).toBe("底层适配命令");
    },
  );

  it("Windows 不适配白名单外命令", async () => {
    const baseRunner = vi.fn(async (_options: ProcessOptions) => processResult());
    const runner = createPlatformProcessRunner({
      baseRunner,
      platform: "win32",
      commandInterpreter: "cmd.exe",
    });
    const logical = { command: "java", args: ["-version"] };

    await runner(logical);

    expect(baseRunner).toHaveBeenCalledWith(logical);
  });

  it("Windows 固定适配器拒绝 cmd 元字符参数", async () => {
    const baseRunner = vi.fn(async (_options: ProcessOptions) => processResult());
    const runner = createPlatformProcessRunner({
      baseRunner,
      platform: "win32",
      commandInterpreter: "cmd.exe",
    });

    await expect(runner({ command: "npm", args: ["run", "test&whoami"] }))
      .rejects.toThrow(/固定 cmd\.exe 适配/u);
    expect(baseRunner).not.toHaveBeenCalled();
  });

  it("底层执行器不能篡改逻辑参数与脱敏快照", async () => {
    const secret = "platform-runner-secret-427";
    const logicalArgs = ["run", "test", secret];
    const logicalSecrets = [secret];
    const baseRunner = vi.fn(async (options: ProcessOptions) => {
      (options.args as string[]).splice(0, options.args.length, "mutated");
      (options.secrets as string[]).splice(0);
      return processResult();
    });
    const runner = createPlatformProcessRunner({
      baseRunner,
      platform: "win32",
      commandInterpreter: "cmd.exe",
    });

    const result = await runner({
      command: "npm",
      args: logicalArgs,
      secrets: logicalSecrets,
    });

    expect(logicalArgs).toEqual(["run", "test", secret]);
    expect(logicalSecrets).toEqual([secret]);
    expect(result.commandDisplay).toContain("[REDACTED]");
    expect(result.commandDisplay).not.toContain(secret);
    expect(result.commandDisplay).not.toContain("mutated");
  });
});
