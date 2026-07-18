import { realpathSync } from "node:fs";
import { isAbsolute, relative, resolve } from "node:path";

import type { EnvironmentName } from "../core/config.js";
import type {
  ProcessOptions,
  ProcessRunner,
} from "../core/process-runner.js";
import { runPlatformProcess } from "../core/platform-process-runner.js";
import { redactEvidenceText } from "../core/redact.js";
import { createCheckResult, type CheckResult } from "../core/result.js";
import {
  buildLogArtifactPath,
  writeBuildStepLog,
  type BuildLogWriter,
} from "./build-log.js";
import { normalizeProcessResult, safeCheckText } from "./process-result.js";

export interface BusinessCommandStep {
  readonly stepId: "business-validation";
  readonly command: string;
  readonly args: readonly string[];
  readonly timeoutMs: number;
  readonly custom: boolean;
}

export interface BuildBusinessCommandPlanOptions {
  readonly environment: EnvironmentName;
  readonly businessCommand?: string;
  readonly platform?: NodeJS.Platform;
  readonly repoRoot?: string;
}

export interface RunBusinessCheckOptions extends BuildBusinessCommandPlanOptions {
  readonly repoRoot: string;
  readonly rawDir: string;
  readonly processRunner?: ProcessRunner;
  readonly logWriter?: BuildLogWriter;
  readonly skipBusinessValidation?: boolean;
  readonly dryRun?: boolean;
  readonly secrets?: readonly string[];
}

const BUSINESS_TIMEOUT_MS = 30 * 60_000;
const FORBIDDEN_CUSTOM_TEXT =
  /\b(?:git|docker|ssh|scp|sftp|rsync|kubectl|node|npx|curl|wget|invoke-command|invoke-expression|iex|enter-pssession|new-pssession|start-process|deploy[-_]?remote|remote[-_]?deploy|jenkins)\b/iu;
const POWERSHELL_EXIT_GUARD =
  /^if\s*\(\s*\$LASTEXITCODE\s+-ne\s+0\s*\)\s*\{\s*exit\s+\$LASTEXITCODE\s*\}$/iu;
const SAFE_NPM_STATEMENT =
  /^npm(?:\.cmd)?(?:\s+--prefix\s+(?:"[^"]+"|'[^']+'|[^\s]+))?\s+run\s+([a-z0-9][a-z0-9:_-]*)(?:\s+(.*))?$/iu;
const SAFE_NPM_SCRIPT =
  /^(?:(?:test|e2e|qa|harness|lint)(?::[a-z0-9:_-]+)*|typecheck|build)$/iu;
const SAFE_MAVEN_GOALS = new Set(["test", "verify", "validate", "compile", "package"]);
const SAFE_MAVEN_FLAGS = new Set(["-q", "--quiet", "-b", "--batch-mode", "-ntp", "--no-transfer-progress"]);

function stripOuterQuotes(value: string): string {
  return value.replace(/^['"]|['"]$/gu, "");
}

function isInside(canonicalRoot: string, canonicalPath: string): boolean {
  const relativePath = relative(canonicalRoot, canonicalPath);
  return relativePath === "" || (!isAbsolute(relativePath) &&
    relativePath !== ".." && !/^\.\.(?:[\\/]|$)/u.test(relativePath));
}

function assertSafeNpmTail(
  tail: string | undefined,
  prefix: string | undefined,
  script: string,
  repoRoot: string | undefined,
): void {
  if (tail === undefined) return;
  if (!/^test(?::|$)/iu.test(script) || !tail.startsWith("-- ")) {
    throw new Error("自定义 npm 尾随参数只允许测试文件列表，已拒绝执行。");
  }
  const testPaths = tail.slice(3).trim().split(/\s+/u).map(stripOuterQuotes);
  if (repoRoot === undefined || testPaths.length === 0 ||
    testPaths.some((path) => path.length === 0 || path.startsWith("-"))) {
    throw new Error("自定义 npm 测试文件参数无效，已拒绝执行。");
  }
  const packageRoot = resolve(repoRoot, prefix ?? ".");
  let canonicalPackageRoot: string;
  try {
    canonicalPackageRoot = realpathSync(packageRoot);
  } catch {
    throw new Error("自定义 npm 包目录无法可信解析，已拒绝执行。");
  }
  for (const testPath of testPaths) {
    const normalized = testPath.replaceAll("\\", "/");
    const expectedPrefix = prefix === "frontend" ? "src/" : "tests/";
    if (isAbsolute(testPath) || !normalized.startsWith(expectedPrefix) ||
      normalized.split("/").includes("..") ||
      !/\.(?:test|spec)\.[cm]?[jt]sx?$/iu.test(normalized)) {
      throw new Error("自定义 npm 测试文件必须位于允许的仓库测试目录，已拒绝执行。");
    }
    let canonicalTestPath: string;
    try {
      canonicalTestPath = realpathSync(resolve(packageRoot, testPath));
    } catch {
      throw new Error("自定义 npm 测试文件不存在或无法可信解析，已拒绝执行。");
    }
    if (!isInside(canonicalPackageRoot, canonicalTestPath)) {
      throw new Error("自定义 npm 测试文件越出允许的仓库包目录，已拒绝执行。");
    }
  }
}

function assertSafeCustomStatement(statement: string, repoRoot: string | undefined): void {
  if (POWERSHELL_EXIT_GUARD.test(statement)) return;
  if (statement.includes("$")) {
    throw new Error("自定义业务命令不允许运行时变量展开，已拒绝执行。");
  }
  if (FORBIDDEN_CUSTOM_TEXT.test(statement)) {
    throw new Error("自定义业务命令引用高风险工具或远端执行能力，已禁止执行。");
  }
  if (/^mvn(?:\.cmd)?(?:\s|$)/iu.test(statement)) {
    const tokens = statement.split(/\s+/u).map(stripOuterQuotes);
    if (tokens.some((token) =>
      (/^-f.+/iu.test(token) && token.toLowerCase() !== "-f") ||
      /^--file=.+/iu.test(token))) {
      throw new Error("自定义 Maven 命令不允许附着式 POM 路径参数，已拒绝执行。");
    }
    const fileFlags = tokens
      .map((token, index) => ({ token: token.toLowerCase(), index }))
      .filter(({ token }) => token === "-f" || token === "--file");
    const pomPath = fileFlags.length === 1
      ? tokens[fileFlags[0]?.index === undefined ? -1 : fileFlags[0].index + 1]
      : undefined;
    const normalizedPomPath = pomPath
      ?.replace(/^['"]|['"]$/gu, "")
      .replaceAll("\\", "/");
    if (normalizedPomPath !== "backend/pom.xml") {
      throw new Error("自定义 Maven 命令只允许 backend/pom.xml，已拒绝执行。");
    }
    const consumedIndexes = new Set([0, fileFlags[0]?.index, (fileFlags[0]?.index ?? -2) + 1]);
    let goalCount = 0;
    for (const [index, token] of tokens.entries()) {
      if (consumedIndexes.has(index)) continue;
      const normalized = token.toLowerCase();
      if (SAFE_MAVEN_GOALS.has(normalized)) {
        goalCount += 1;
        continue;
      }
      if (SAFE_MAVEN_FLAGS.has(normalized) ||
        /^-dtest=[a-z0-9_.,#-]+$/iu.test(token) ||
        /^-dfailifnotests=(?:true|false)$/iu.test(token)) {
        continue;
      }
      throw new Error("自定义 Maven 命令包含未授权目标或参数，已拒绝执行。");
    }
    if (goalCount === 0) {
      throw new Error("自定义 Maven 命令缺少本地验证目标，已拒绝执行。");
    }
    return;
  }
  const npm = SAFE_NPM_STATEMENT.exec(statement);
  const prefix = /\s--prefix\s+(?:"([^"]+)"|'([^']+)'|([^\s]+))/iu.exec(statement);
  const normalizedPrefix = (prefix?.[1] ?? prefix?.[2] ?? prefix?.[3])
    ?.replaceAll("\\", "/");
  if (/\s--prefix(?:\s|=)/iu.test(statement) && normalizedPrefix !== "frontend") {
    throw new Error("自定义 npm 命令的 --prefix 只允许 frontend，已拒绝执行。");
  }
  if (npm !== null && npm[1] !== undefined && SAFE_NPM_SCRIPT.test(npm[1]) &&
    !/(?:deploy|publish|release|start|stop)/iu.test(npm[1])) {
    assertSafeNpmTail(npm[2], normalizedPrefix, npm[1], repoRoot);
    return;
  }
  throw new Error("自定义业务命令只允许本地 Maven 验证或 npm 测试脚本，已拒绝执行。");
}

function assertSafeCustomCommand(
  command: string,
  repoRoot: string | undefined,
): void {
  if (command.length > 8_192) {
    throw new Error("自定义业务命令超过 8192 字符上限，已拒绝执行。");
  }
  if (/[\u0000-\u001f\u007f]/u.test(command)) {
    throw new Error("自定义业务命令包含非法控制字符，已拒绝执行。");
  }
  if (/[&|`<>]|\$\(/u.test(command)) {
    throw new Error("自定义业务命令包含动态执行、管道或重定向语法，已拒绝执行。");
  }
  const statements = command.split(";").map((statement) => statement.trim());
  if (statements.some((statement) => statement.length === 0)) {
    throw new Error("自定义业务命令包含空语句，已拒绝执行。");
  }
  for (const statement of statements) {
    assertSafeCustomStatement(statement, repoRoot);
  }
}

export function buildBusinessCommandPlan(
  options: BuildBusinessCommandPlanOptions,
): BusinessCommandStep {
  if (options.environment !== "test" && options.environment !== "real-pre") {
    throw new Error(`不支持的业务验证环境：${String(options.environment)}。`);
  }
  const customCommand = options.businessCommand?.trim() ?? "";
  if (customCommand.length === 0) {
    const script = options.environment === "real-pre"
      ? "e2e:real-pre:p0:preflight"
      : "e2e:v1-p0";
    return Object.freeze({
      stepId: "business-validation",
      command: "npm",
      args: Object.freeze(["run", script]),
      timeoutMs: BUSINESS_TIMEOUT_MS,
      custom: false,
    });
  }

  assertSafeCustomCommand(customCommand, options.repoRoot);
  const windows = (options.platform ?? process.platform) === "win32";
  return Object.freeze({
    stepId: "business-validation",
    command: windows ? "powershell" : "sh",
    args: Object.freeze(windows
      ? ["-NoProfile", "-NonInteractive", "-Command", customCommand]
      : ["-lc", customCommand]),
    timeoutMs: BUSINESS_TIMEOUT_MS,
    custom: true,
  });
}

function businessFailure(
  summary: string,
  nextActions: readonly string[],
  artifacts: readonly string[],
): CheckResult {
  return createCheckResult({
    checkId: "business",
    title: "验证业务链路",
    status: "FAIL",
    blocking: true,
    summary,
    nextActions,
    artifacts,
  });
}

function skippedBusinessResult(
  summary: string,
  nextActions: readonly string[],
): CheckResult {
  return createCheckResult({
    checkId: "business",
    title: "验证业务链路",
    status: "SKIPPED",
    blocking: true,
    summary,
    nextActions,
    artifacts: [],
  });
}

export async function runBusinessCheck(
  options: RunBusinessCheckOptions,
): Promise<CheckResult> {
  const secrets = Object.freeze([...(options.secrets ?? [])]);
  let step: BusinessCommandStep;
  let expectedArtifact: string;
  try {
    step = buildBusinessCommandPlan(options);
    expectedArtifact = buildLogArtifactPath(options.rawDir, step.stepId);
  } catch (error) {
    return businessFailure(
      `业务验证配置无效：${safeCheckText(error, secrets)}。`,
      ["修复环境、BusinessCommand 或 rawDir 后重新运行验证。"],
      [],
    );
  }

  if (options.skipBusinessValidation === true) {
    return skippedBusinessResult(
      "业务验证被显式跳过，本次运行不得标记为完整通过。",
      ["移除 SkipBusinessValidation 后执行真实业务验证。"],
    );
  }
  const display = redactEvidenceText([step.command, ...step.args].join(" "), secrets);
  if (options.dryRun === true) {
    return skippedBusinessResult(
      "dry-run 模式仅展示业务命令计划，未执行业务验证。",
      [`计划命令：${display}`, "移除 dry-run 参数后执行业务验证。"],
    );
  }

  const runner = options.processRunner ?? runPlatformProcess;
  let rawResult: unknown;
  try {
    const processOptions: ProcessOptions = {
      command: step.command,
      args: [...step.args],
      cwd: options.repoRoot,
      timeoutMs: step.timeoutMs,
      secrets: [...secrets],
    };
    rawResult = await runner(processOptions);
  } catch (error) {
    return businessFailure(
      `业务验证执行器异常：${safeCheckText(error, secrets)}。`,
      [
        "安全重试：确认测试工具链、工作目录和命令边界后，可重新运行一次。",
        "停止条件：异常原因未变化或命令副作用无法确认时停止重试。",
      ],
      [],
    );
  }

  let result;
  try {
    result = normalizeProcessResult(rawResult, secrets);
  } catch (error) {
    return businessFailure(
      `业务验证结果不符合契约：${safeCheckText(error, secrets)}。`,
      [
        "安全重试：修复 ProcessRunner 结果契约后，可重新运行一次。",
        "停止条件：结果无法可信解析时停止验证，不得生成通过证据。",
      ],
      [],
    );
  }

  let artifact: string;
  try {
    const logWriter = options.logWriter ?? writeBuildStepLog;
    artifact = await logWriter({
      repoRoot: options.repoRoot,
      rawDir: options.rawDir,
      stepId: step.stepId,
      result,
    });
    if (artifact !== expectedArtifact) {
      throw new Error("artifact 路径不符合固定计划");
    }
  } catch (error) {
    return businessFailure(
      `业务验证日志写入失败：${safeCheckText(error, secrets)}。`,
      [
        "安全重试：确认 rawDir、磁盘空间和文件权限后，可重新运行一次。",
        "停止条件：日志仍无法独占写入时停止验证，不得把无证据业务命令标记为通过。",
      ],
      [],
    );
  }

  if (!result.success) {
    return businessFailure(
      `业务验证失败。根因：${result.rootCause ?? "命令执行失败，未返回根因。"}`,
      [
        `安全重试：${result.safeRetry ?? "确认失败原因和幂等性后再重试。"}`,
        `停止条件：${result.stopCondition ?? "无法确认安全边界时停止重试。"}`,
      ],
      [artifact],
    );
  }

  return createCheckResult({
    checkId: "business",
    title: "验证业务链路",
    status: "PASS",
    blocking: true,
    summary: `业务验证命令已通过（${step.custom ? "显式自定义命令" : "环境默认命令"}）。`,
    nextActions: [],
    artifacts: [artifact],
  });
}
