import {
  existsSync,
  readFileSync,
  readdirSync,
  statSync,
} from "node:fs";
import { extname, join, relative, resolve } from "node:path";

import {
  getEnvironmentContract,
  loadEnvironmentContracts,
  parseEnvText,
  type EnvironmentName,
} from "../core/config.js";
import { runPlatformProcess } from "../core/platform-process-runner.js";
import type {
  ProcessResult,
  ProcessRunner,
} from "../core/process-runner.js";
import {
  createCheckResult,
  createRunResult,
  type CheckResult,
  type CheckStatus,
  type RunResult,
} from "../core/result.js";

export type InspectProcessRunner = ProcessRunner;

export interface RunInspectOptions {
  readonly environment: EnvironmentName;
  readonly repoRoot: string;
  readonly processRunner?: InspectProcessRunner;
}

export interface InspectCommand {
  readonly command: string;
  readonly args: readonly string[];
}

export interface DangerousCommandReference {
  readonly path: string;
  readonly line: number;
}

const PROCESS_TIMEOUT_MS = 10_000;
const DANGEROUS_COMMAND = /docker\s+compose\b[^\r\n]*\bdown\b[^\r\n]*(?:-v\b|--volumes\b)|docker\s+volume\s+(?:rm|prune)\b|Remove-Item\b[^\r\n]*(?:postgres|redis)[^\r\n]*volume|DROP\s+DATABASE\b/iu;
const SKIPPED_SCAN_DIRECTORIES = new Set([".git", "node_modules"]);

function check(
  checkId: string,
  title: string,
  status: CheckStatus,
  blocking: boolean,
  summary: string,
  nextActions: readonly string[] = [],
  artifacts: readonly string[] = [],
): CheckResult {
  return createCheckResult({
    checkId,
    title,
    status,
    blocking,
    summary,
    nextActions,
    artifacts,
  });
}

export function buildInspectCommandPlan(): readonly InspectCommand[] {
  return [
    { command: "node", args: ["--version"] },
    { command: "npm", args: ["--version"] },
    { command: "java", args: ["-version"] },
    { command: "mvn", args: ["-version"] },
    { command: "docker", args: ["--version"] },
    { command: "docker", args: ["compose", "version"] },
  ];
}

function normalizedPath(repoRoot: string, path: string): string {
  return relative(repoRoot, path).replaceAll("\\", "/");
}

function walkPowerShellFiles(root: string): string[] {
  if (!existsSync(root)) return [];
  const files: string[] = [];
  const visit = (directory: string): void => {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      if (entry.isDirectory()) {
        if (!SKIPPED_SCAN_DIRECTORIES.has(entry.name)) visit(join(directory, entry.name));
      } else if (entry.isFile() && extname(entry.name).toLowerCase() === ".ps1") {
        files.push(join(directory, entry.name));
      }
    }
  };
  visit(root);
  return files;
}

export function findDangerousCommandReferences(repoRoot: string): readonly DangerousCommandReference[] {
  const matches: DangerousCommandReference[] = [];
  for (const scanRoot of ["scripts", "harness"]) {
    for (const path of walkPowerShellFiles(join(repoRoot, scanRoot))) {
      const relativePath = normalizedPath(repoRoot, path);
      if (isCanonicalSafetyCheckPath(repoRoot, path)) continue;
      const lines = readFileSync(path, "utf8").split(/\r?\n/gu);
      let logicalLine = "";
      let logicalStart = 1;
      lines.forEach((lineText, index) => {
        if (logicalLine.length === 0) logicalStart = index + 1;
        logicalLine = `${logicalLine} ${lineText.trim()}`.trim();
        if (/`\s*$/u.test(lineText)) {
          logicalLine = logicalLine.replace(/`\s*$/u, "").trimEnd();
          return;
        }
        if (DANGEROUS_COMMAND.test(logicalLine)) {
          matches.push({ path: relativePath, line: logicalStart });
        }
        logicalLine = "";
      });
      if (logicalLine.length > 0 && DANGEROUS_COMMAND.test(logicalLine)) {
        matches.push({ path: relativePath, line: logicalStart });
      }
    }
  }
  return matches.sort((left, right) =>
    left.path.localeCompare(right.path, "en") || left.line - right.line
  );
}

export function isCanonicalSafetyCheckPath(
  repoRoot: string,
  candidate: string,
  platform: NodeJS.Platform = process.platform,
): boolean {
  const expected = resolve(repoRoot, "harness", "scripts", "commands", "safety-check.ps1");
  const actual = resolve(candidate);
  return platform === "win32"
    ? actual.toLowerCase() === expected.toLowerCase()
    : actual === expected;
}

function repositoryStructureCheck(repoRoot: string, composeFile: string, envExampleFile: string): CheckResult {
  const requiredDirectories = ["backend", "frontend", "harness", "scripts"];
  const requiredFiles = [
    "AGENTS.md",
    "backend/pom.xml",
    "frontend/package.json",
    composeFile,
    envExampleFile,
  ];
  const missing = [
    ...requiredDirectories.filter((path) =>
      !existsSync(join(repoRoot, path)) || !statSync(join(repoRoot, path)).isDirectory()
    ),
    ...requiredFiles.filter((path) =>
      !existsSync(join(repoRoot, path)) || !statSync(join(repoRoot, path)).isFile()
    ),
  ];
  return missing.length === 0
    ? check("repository.structure", "检查仓库结构", "PASS", true, "仓库结构与当前 Harness 入口完整。")
    : check(
      "repository.structure",
      "检查仓库结构",
      "FAIL",
      true,
      `仓库缺少必要路径：${missing.join("、")}。`,
      ["请在仓库根目录运行，并恢复缺失的受版本控制文件。"],
    );
}

function environmentFileChecks(
  repoRoot: string,
  environment: EnvironmentName,
): { readonly checks: readonly CheckResult[]; readonly values: Readonly<Record<string, string>> | null } {
  const contract = getEnvironmentContract(environment);
  const composePath = join(repoRoot, contract.composeFile);
  const examplePath = join(repoRoot, contract.envExampleFile);
  const envPath = join(repoRoot, contract.envFile);
  const missingStatic = [
    !existsSync(composePath) ? contract.composeFile : null,
    !existsSync(examplePath) ? contract.envExampleFile : null,
  ].filter((value): value is string => value !== null);
  if (missingStatic.length > 0) {
    return {
      values: null,
      checks: [check(
        "environment.files",
        "检查环境文件",
        "FAIL",
        true,
        `环境静态文件缺失：${missingStatic.join("、")}。`,
        ["从版本控制恢复 Compose 与环境示例文件后重试。"],
      )],
    };
  }
  if (!existsSync(envPath)) {
    return {
      values: null,
      checks: [check(
        "environment.files",
        "检查环境文件",
        "BLOCKED",
        true,
        `实际环境文件 ${contract.envFile} 缺失，不能用 example 文件冒充。`,
        [`复制 ${contract.envExampleFile} 为 ${contract.envFile}，仅在本地安全填写真实配置且不要提交。`],
      )],
    };
  }

  let values: Readonly<Record<string, string>>;
  try {
    values = parseEnvText(readFileSync(envPath, "utf8"));
  } catch {
    return {
      values: null,
      checks: [check(
        "environment.files",
        "检查环境文件",
        "BLOCKED",
        true,
        `实际环境文件 ${contract.envFile} 无法安全读取。`,
        ["检查文件存在性、编码和读取权限；不要把文件内容粘贴到日志。"],
      )],
    };
  }
  return {
    values,
    checks: [check(
      "environment.files",
      "检查环境文件",
      "PASS",
      true,
      `Compose、示例文件与实际 ${contract.envFile} 均存在。`,
      [],
      [contract.composeFile, contract.envExampleFile],
    )],
  };
}

function blockedEnvironmentChecks(): readonly CheckResult[] {
  return [
    check("environment.required-config", "检查必需配置", "BLOCKED", true, "实际环境文件不可用，未检查必需配置。", ["先解除环境文件阻塞。"]),
    check("environment.hard-switches", "检查环境硬开关", "BLOCKED", true, "实际环境文件不可用，未检查环境硬开关。", ["先解除环境文件阻塞。"]),
    check("environment.secrets", "检查密钥存在性", "BLOCKED", true, "实际环境文件不可用，未检查密钥存在性。", ["先解除环境文件阻塞。"]),
  ];
}

function inspectEnvironmentValues(
  environment: EnvironmentName,
  values: Readonly<Record<string, string>>,
): readonly CheckResult[] {
  const contract = getEnvironmentContract(environment);
  const missingConfig = contract.requiredConfigKeys.filter((key) =>
    values[key] === undefined || values[key]?.trim().length === 0
  );
  const wrongSwitches = Object.entries(contract.hardSwitches).filter(([key, expected]) =>
    values[key]?.toLowerCase() !== expected.toLowerCase()
  );
  const presentSecrets = contract.secretKeys.filter(({ key }) =>
    values[key] !== undefined && values[key]?.trim().length !== 0
  );
  const missingSecrets = contract.secretKeys.filter(({ key }) =>
    values[key] === undefined || values[key]?.trim().length === 0
  );
  const missingRequiredSecrets = missingSecrets.filter(({ required }) => required);

  const configCheck = missingConfig.length === 0
    ? check("environment.required-config", "检查必需配置", "PASS", true, "必需配置键均已填写；未输出任何配置值。")
    : check(
      "environment.required-config",
      "检查必需配置",
      "FAIL",
      true,
      `缺少必需配置：${missingConfig.join("、")}。`,
      ["按环境示例文件补齐键值，并保持真实配置不进入 Git。"],
    );
  const switchCheck = wrongSwitches.length === 0
    ? check("environment.hard-switches", "检查环境硬开关", "PASS", true, "环境硬开关符合当前环境基线。")
    : check(
      "environment.hard-switches",
      "检查环境硬开关",
      "FAIL",
      true,
      `环境硬开关不符合基线：${wrongSwitches.map(([key, expected]) => `${key} 应为 ${expected}`).join("；")}。`,
      [`将 ${wrongSwitches.map(([key, expected]) => `${key} 设置为 ${expected}`).join("，")}，再执行只读检查；real-pre 禁止 mock/test 模式。`],
    );
  const secretStatus: CheckStatus = missingRequiredSecrets.length > 0
    ? "FAIL"
    : missingSecrets.length > 0 ? "WARN" : "PASS";
  const secretSummary = [
    `存在：${presentSecrets.map(({ key }) => key).join("、") || "无"}`,
    `缺失：${missingSecrets.map(({ key }) => key).join("、") || "无"}`,
  ].join("；");
  const secretCheck = check(
    "environment.secrets",
    "检查密钥存在性",
    secretStatus,
    missingRequiredSecrets.length > 0,
    `密钥只报告存在性。${secretSummary}。`,
    missingRequiredSecrets.length > 0
      ? ["在受控环境文件中补齐必需密钥；不要在命令行、日志或报告中输出密钥值。"]
      : [],
  );
  return [configCheck, switchCheck, secretCheck];
}

function majorVersion(output: string, kind: "node" | "java"): number | null {
  const pattern = kind === "node" ? /\bv?(\d+)(?:\.\d+)+/u : /\bversion\s+"?(\d+)(?:\.\d+)+/iu;
  const match = pattern.exec(output);
  return match?.[1] === undefined ? null : Number.parseInt(match[1], 10);
}

function toolCheck(
  id: string,
  title: string,
  result: ProcessResult,
  expectedMajor?: number,
  kind?: "node" | "java",
): CheckResult {
  if (!result.success) {
    return check(
      `toolchain.${id}`,
      title,
      "BLOCKED",
      true,
      `${title}不可用，无法完成本地验证。`,
      [result.safeRetry ?? "安装或修复工具链后重试。", result.stopCondition ?? "工具仍不可用时停止执行后续验证。"],
    );
  }
  if (expectedMajor !== undefined && kind !== undefined) {
    const actualMajor = majorVersion(`${result.stdout}\n${result.stderr}`, kind);
    if (actualMajor !== expectedMajor) {
      return check(
        `toolchain.${id}`,
        title,
        "WARN",
        true,
        `${title}目标为 ${kind === "node" ? "Node.js" : "Java"} ${expectedMajor}，当前识别为 ${actualMajor ?? "未知版本"}。`,
        [`切换到 ${kind === "node" ? `Node.js ${expectedMajor}` : `Java ${expectedMajor}`} 后执行正式验证。`],
      );
    }
  }
  return check(`toolchain.${id}`, title, "PASS", true, `${title}只读版本探测通过。`);
}

async function executeCommandPlan(
  repoRoot: string,
  processRunner: InspectProcessRunner,
): Promise<readonly ProcessResult[]> {
  const results: ProcessResult[] = [];
  for (const command of buildInspectCommandPlan()) {
    try {
      results.push(await processRunner({
        ...command,
        cwd: repoRoot,
        timeoutMs: PROCESS_TIMEOUT_MS,
      }));
    } catch {
      results.push({
        commandDisplay: "只读命令执行异常",
        exitCode: null,
        signal: null,
        timedOut: false,
        durationMs: 0,
        stdout: "",
        stderr: "",
        success: false,
        rootCause: "只读工具探测发生未分类异常。",
        safeRetry: "确认工具路径与仓库权限后重试一次。",
        stopCondition: "再次异常时停止并检查本地工具链。",
      });
    }
  }
  return results;
}

export async function runInspect(options: RunInspectOptions): Promise<RunResult> {
  const contracts = loadEnvironmentContracts();
  const contract = getEnvironmentContract(options.environment, contracts);
  const runner = options.processRunner ?? runPlatformProcess;
  const checks: CheckResult[] = [
    repositoryStructureCheck(options.repoRoot, contract.composeFile, contract.envExampleFile),
  ];

  const fileResult = environmentFileChecks(options.repoRoot, options.environment);
  checks.push(...fileResult.checks);
  checks.push(...(fileResult.values === null
    ? blockedEnvironmentChecks()
    : inspectEnvironmentValues(options.environment, fileResult.values)));

  try {
    const dangerous = findDangerousCommandReferences(options.repoRoot);
    checks.push(dangerous.length === 0
      ? check("safety.dangerous-commands", "扫描破坏性命令", "PASS", true, "scripts 与 harness 中未发现被禁止的破坏性命令引用。")
      : check(
        "safety.dangerous-commands",
        "扫描破坏性命令",
        "FAIL",
        true,
        `发现破坏性命令引用：${dangerous.map(({ path, line }) => `${path}:${line}`).join("、")}。`,
        ["人工审查并移除破坏性命令；real-pre 禁止删库、删卷和 DROP DATABASE。"],
      ));
  } catch {
    checks.push(check("safety.dangerous-commands", "扫描破坏性命令", "BLOCKED", true, "破坏性命令扫描无法完成。", ["检查 scripts/harness 读取权限后重试。"]));
  }

  const processResults = await executeCommandPlan(options.repoRoot, runner);
  const [nodeVersion, npmVersion, javaVersion, mavenVersion, dockerVersion, composeVersion] = processResults;
  if (!nodeVersion || !npmVersion || !javaVersion ||
    !mavenVersion || !dockerVersion || !composeVersion) {
    throw new Error("只读命令计划结果数量不完整。");
  }
  checks.push(check(
    "source-control.boundary",
    "检查版本控制边界",
    "PASS",
    true,
    "Node inspect 未调用 Git；敏感变更与候选提交由 agent-do 的 PowerShell 门禁负责。",
  ));
  checks.push(
    toolCheck("node", "检查 Node.js", nodeVersion, contracts.toolchain.nodeMajor, "node"),
    toolCheck("npm", "检查 npm", npmVersion),
    toolCheck("java", "检查 Java", javaVersion, contracts.toolchain.javaMajor, "java"),
    toolCheck("maven", "检查 Maven", mavenVersion),
    toolCheck("docker", "检查 Docker", dockerVersion),
    toolCheck("docker-compose", "检查 Docker Compose", composeVersion),
  );
  return createRunResult(checks);
}
