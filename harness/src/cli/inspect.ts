import { dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

import {
  runInspect as executeInspect,
  type RunInspectOptions,
} from "../checks/inspect.js";
import type { EnvironmentName } from "../core/config.js";
import type { RunResult, RunStatus } from "../core/result.js";

export interface InspectCliDependencies {
  cwd: () => string;
  runInspect: (options: RunInspectOptions) => Promise<RunResult>;
  writeStdout: (message: string) => void;
  writeStderr: (message: string) => void;
}

const DEFAULT_DEPENDENCIES: InspectCliDependencies = {
  cwd: () => resolveInspectRepoRoot(),
  runInspect: executeInspect,
  writeStdout: (message) => process.stdout.write(`${message}\n`),
  writeStderr: (message) => process.stderr.write(`${message}\n`),
};

export function resolveInspectRepoRoot(moduleUrl: string = import.meta.url): string {
  return resolve(dirname(fileURLToPath(moduleUrl)), "../../..");
}

export function renderInspectHelp(): string {
  return [
    "Harness 只读环境检查",
    "",
    "用法：",
    "  npm run harness:node:inspect -- --env real-pre",
    "  npm run harness:node:inspect -- --env test",
    "",
    "阶段：",
    "  1. 检查仓库、Compose 与环境文件",
    "  2. 检查环境硬开关、必需配置与密钥存在性",
    "  3. 扫描敏感变更与破坏性命令引用",
    "  4. 只读探测 Git、Node.js、npm、Java、Maven、Docker 与 Compose 版本",
    "",
    "只读边界：不会提交或推送 Git，不会构建、启动、重启或删除容器，不会执行网络、SSH 或远端命令。",
    "",
    "退出码：",
    "  0  PASS（通过）",
    "  1  FAIL（失败）",
    "  2  BLOCKED / PARTIAL（阻塞或部分完成）",
    "  3  参数或环境契约错误",
  ].join("\n");
}

function parseEnvironment(args: readonly string[]): EnvironmentName {
  if (args.length !== 2 || args[0] !== "--env" ||
    (args[1] !== "test" && args[1] !== "real-pre")) {
    throw new Error("必须且只能使用 --env test 或 --env real-pre。");
  }
  return args[1];
}

function exitCodeFor(status: RunStatus): number {
  if (status === "PASS") return 0;
  if (status === "FAIL") return 1;
  return 2;
}

function renderResult(result: RunResult): string {
  const lines = [
    "",
    "=== 检查摘要 ===",
    `结论：${result.status}（${result.statusLabel}）`,
    result.summary,
  ];
  for (const item of result.checks) {
    lines.push(`- [${item.status}] ${item.title}：${item.summary}`);
    for (const action of item.nextActions) lines.push(`  修复指引：${action}`);
  }
  return lines.join("\n");
}

export async function inspectCli(
  args: readonly string[] = [],
  dependencies: InspectCliDependencies = DEFAULT_DEPENDENCIES,
): Promise<number> {
  if (args.length === 1 && (args[0] === "--help" || args[0] === "-h")) {
    dependencies.writeStdout(renderInspectHelp());
    return 0;
  }

  let environment: EnvironmentName;
  try {
    environment = parseEnvironment(args);
  } catch (error) {
    const reason = error instanceof Error ? error.message : "未知参数错误。";
    dependencies.writeStderr(`参数错误：${reason} 使用 --help 查看中文帮助。`);
    return 3;
  }

  dependencies.writeStdout(`=== 阶段 1/4：读取 ${environment} 环境事实 ===`);
  dependencies.writeStdout("=== 阶段 2/4：检查配置与安全门禁 ===");
  dependencies.writeStdout("=== 阶段 3/4：执行固定只读工具探测 ===");
  try {
    const result = await dependencies.runInspect({
      environment,
      repoRoot: dependencies.cwd(),
    });
    dependencies.writeStdout("=== 阶段 4/4：汇总结构化结果 ===");
    dependencies.writeStdout(renderResult(result));
    return exitCodeFor(result.status);
  } catch (error) {
    const name = error instanceof Error ? error.name : "Error";
    const reason = error instanceof Error ? error.message : "未知错误";
    if (name === "EnvironmentContractError") {
      dependencies.writeStderr(`契约错误：${reason}。修复 environments.json 后重试；未输出堆栈。`);
      return 3;
    }
    dependencies.writeStderr(`执行受阻：${reason}。确认仓库和本地工具链后安全重试一次；未输出堆栈。`);
    return 2;
  }
}

function isMainModule(): boolean {
  const entry = process.argv[1];
  return entry !== undefined && pathToFileURL(resolve(entry)).href === import.meta.url;
}

if (isMainModule()) {
  process.exitCode = await inspectCli(process.argv.slice(2));
}
