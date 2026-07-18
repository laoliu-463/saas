import {
  runProcess,
  type ProcessOptions,
  type ProcessResult,
  type ProcessRunner,
} from "./process-runner.js";
import { redactEvidenceText } from "./redact.js";

export interface CreatePlatformProcessRunnerOptions {
  readonly baseRunner?: ProcessRunner;
  readonly platform?: NodeJS.Platform;
  readonly commandInterpreter?: string;
}

const WINDOWS_COMMAND_TOOLS = new Set(["npm", "mvn"]);
const WINDOWS_CMD_META_CHARACTER = /[&|<>^()%!"\r\n\u0000]/u;

function logicalCommandDisplay(options: ProcessOptions): string {
  return [options.command, ...options.args]
    .map((part) => JSON.stringify(redactEvidenceText(part, options.secrets ?? [])))
    .join(" ");
}

export function createPlatformProcessRunner(
  options: CreatePlatformProcessRunnerOptions = {},
): ProcessRunner {
  const baseRunner = options.baseRunner ?? runProcess;
  const platform = options.platform ?? process.platform;
  const commandInterpreter = options.commandInterpreter ??
    process.env["ComSpec"] ?? "cmd.exe";

  return async (logicalOptions: ProcessOptions): Promise<ProcessResult> => {
    const logicalCommand = logicalOptions.command;
    const logicalArgs = Object.freeze([...logicalOptions.args]);
    const secretSnapshot = Object.freeze([...(logicalOptions.secrets ?? [])]);
    const executionOptions: ProcessOptions = {
      command: logicalCommand,
      args: [...logicalArgs],
      ...(logicalOptions.cwd === undefined ? {} : { cwd: logicalOptions.cwd }),
      ...(logicalOptions.timeoutMs === undefined ? {} : { timeoutMs: logicalOptions.timeoutMs }),
      ...(logicalOptions.env === undefined ? {} : { env: { ...logicalOptions.env } }),
      ...(logicalOptions.secrets === undefined ? {} : { secrets: [...secretSnapshot] }),
    };
    if (
      platform !== "win32" ||
      !WINDOWS_COMMAND_TOOLS.has(logicalCommand)
    ) {
      return baseRunner(executionOptions);
    }
    if (logicalArgs.some((argument) => WINDOWS_CMD_META_CHARACTER.test(argument))) {
      throw new Error(
        "固定 cmd.exe 适配只接受无 shell 元字符的独立参数，已拒绝不安全调用。",
      );
    }
    const { command, args, ...shared } = executionOptions;
    const result = await baseRunner({
      ...shared,
      command: commandInterpreter,
      args: ["/d", "/s", "/c", `${command}.cmd`, ...args],
    });
    return {
      ...result,
      commandDisplay: logicalCommandDisplay({
        command: logicalCommand,
        args: logicalArgs,
        secrets: secretSnapshot,
      }),
    };
  };
}

export const runPlatformProcess = createPlatformProcessRunner();
