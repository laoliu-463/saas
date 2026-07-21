import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

import { redactEvidenceText } from "../core/redact.js";
import type { GitSnapshot } from "../core/git.js";
import { validateReportKey } from "../core/run-context.js";
import type { RunStatus } from "../core/result.js";
import {
  runNodeVerify,
  VerifyContractError,
  type NodeVerifyOptions,
  type NodeVerifyOutcome,
} from "../workflows/run-verify.js";

export interface VerifyCliDependencies {
  cwd: () => string;
  runVerify: (options: NodeVerifyOptions) => Promise<NodeVerifyOutcome>;
  writeStdout: (message: string) => void;
  writeStderr: (message: string) => void;
  readEnvironmentVariable: (name: string) => string | undefined;
  digestFile: (path: string) => string;
}

export interface ParsedVerifyArguments {
  readonly environment: "test" | "real-pre";
  readonly scope: "backend" | "frontend" | "full";
  readonly reportKey: string;
  readonly businessCommand?: string;
  readonly skipBusinessValidation: boolean;
  readonly dryRun: boolean;
}

const DEFAULT_DEPENDENCIES: VerifyCliDependencies = {
  cwd: () => resolveVerifyRepoRoot(),
  runVerify: runNodeVerify,
  writeStdout: (message) => process.stdout.write(`${message}\n`),
  writeStderr: (message) => process.stderr.write(`${message}\n`),
  readEnvironmentVariable: (name) => process.env[name],
  digestFile: (path) => `sha256:${createHash("sha256").update(readFileSync(path)).digest("hex")}`,
};

export const VERIFY_RECEIPT_PREFIX = "HARNESS_VERIFY_RECEIPT_V1:";
const SHA_PATTERN = /^(?:[a-f0-9]{40}|[a-f0-9]{64})$/u;
const FINGERPRINT_PATTERN = /^sha256:[a-f0-9]{64}$/u;
const INVOCATION_PATTERN = /^[a-f0-9]{32}$/u;

function record(value: unknown): Record<string, unknown> | undefined {
  return typeof value === "object" && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : undefined;
}

function relativePath(value: unknown): value is string {
  return typeof value === "string" && value.length > 0 &&
    !value.includes("\\") && !value.includes("\0") &&
    !value.startsWith("/") && !/^[A-Za-z]:/u.test(value) &&
    !value.split("/").includes("..");
}

export function parseInjectedGitSnapshot(raw: string | undefined): GitSnapshot | undefined {
  if (raw === undefined || raw.trim().length === 0) return undefined;
  let value: unknown;
  try {
    value = JSON.parse(raw);
  } catch {
    throw new VerifyContractError("agent-do 注入的 Git 快照不是合法 JSON。");
  }
  const snapshot = record(value);
  const identity = record(snapshot?.["identity"]);
  const changedFiles = snapshot?.["changedFiles"];
  if (
    snapshot === undefined || identity === undefined ||
    typeof snapshot["headSha"] !== "string" ||
    !SHA_PATTERN.test(snapshot["headSha"]) ||
    typeof snapshot["branch"] !== "string" || snapshot["branch"].length === 0 ||
    typeof snapshot["clean"] !== "boolean" ||
    !Array.isArray(changedFiles) || !changedFiles.every(relativePath) ||
    new Set(changedFiles).size !== changedFiles.length
  ) {
    throw new VerifyContractError("agent-do 注入的 Git 快照不符合结构契约。");
  }
  if (snapshot["clean"] === true) {
    if (
      changedFiles.length !== 0 || identity["kind"] !== "COMMIT" ||
      identity["commitSha"] !== snapshot["headSha"]
    ) {
      throw new VerifyContractError("干净 Git 快照必须使用与 HEAD 一致的 COMMIT 身份。");
    }
  } else if (
    changedFiles.length === 0 || identity["kind"] !== "WORKTREE" ||
    identity["headSha"] !== snapshot["headSha"] ||
    !Array.isArray(identity["changedFiles"]) ||
    JSON.stringify(identity["changedFiles"]) !== JSON.stringify(changedFiles) ||
    typeof identity["patchFingerprint"] !== "string" ||
    !FINGERPRINT_PATTERN.test(identity["patchFingerprint"])
  ) {
    throw new VerifyContractError("非干净 Git 快照必须使用一致的 WORKTREE 身份。");
  }
  return snapshot as unknown as GitSnapshot;
}

function invocationId(raw: string | undefined): string | undefined {
  if (raw === undefined || raw.length === 0) return undefined;
  if (!INVOCATION_PATTERN.test(raw)) {
    throw new VerifyContractError("agent-do 调用标识不符合契约。");
  }
  return raw;
}

function renderReceipt(
  outcome: NodeVerifyOutcome,
  options: ParsedVerifyArguments,
  invocation: string,
  digestFile: (path: string) => string,
): string {
  const absoluteEvidencePath = (path: string) =>
    resolve(outcome.context.repoRoot, ...path.split("/"));
  const payload = {
    schemaVersion: "1.0.0",
    invocationId: invocation,
    runId: outcome.context.runId,
    reportKey: options.reportKey,
    environment: options.environment,
    scope: options.scope,
    status: outcome.result.status,
    evidencePaths: {
      rawJson: outcome.evidence.rawJson,
      stableJson: outcome.evidence.stableJson,
      stableMarkdown: outcome.evidence.stableMarkdown,
    },
    evidenceDigests: {
      rawJson: digestFile(absoluteEvidencePath(outcome.evidence.rawJson)),
      stableJson: digestFile(absoluteEvidencePath(outcome.evidence.stableJson)),
      stableMarkdown: digestFile(absoluteEvidencePath(outcome.evidence.stableMarkdown)),
    },
  };
  return `${VERIFY_RECEIPT_PREFIX}${Buffer.from(JSON.stringify(payload), "utf8").toString("base64url")}`;
}

export function resolveVerifyRepoRoot(moduleUrl: string = import.meta.url): string {
  return resolve(dirname(fileURLToPath(moduleUrl)), "../../..");
}

export function renderVerifyHelp(): string {
  return [
    "Harness 本地验证",
    "",
    "用法：",
    "  npm run harness:node:verify -- --env test --scope backend --report-key task-key",
    "  npm run harness:node:verify -- --env real-pre --scope full --report-key task-key [--business-command \"npm run e2e:real-pre:p0:preflight\"]",
    "",
    "参数：",
    "  --env test|real-pre                     必填，验证环境",
    "  --scope backend|frontend|full           必填，验证范围",
    "  --report-key key                        必填，稳定报告键",
    "  --business-command raw                  可选，受白名单约束的本地业务验证命令",
    "  --skip-business-validation              可选，显式跳过业务验证，结论只能为 PARTIAL",
    "  --dry-run                               可选，只执行 inspect 并生成 SKIPPED 计划与证据",
    "",
    "阶段：inspect → Scope 验证/计划 → 同源 JSON 与 Markdown 证据。",
    "安全边界：直接运行不会提交、推送、SSH 或部署；远端发布必须进入 release/real-pre 与 Jenkins 唯一发布队列。",
    "",
    "退出码：",
    "  0  PASS（通过）",
    "  1  FAIL（确定性失败）",
    "  2  BLOCKED / PARTIAL / 证据写入阻塞",
    "  3  参数、Schema 或环境契约错误",
  ].join("\n");
}

function requiredValue(args: readonly string[], index: number, flag: string): string {
  const value = args[index + 1];
  if (value === undefined || value.length === 0 || value.startsWith("--")) {
    throw new Error(`${flag} 缺少参数值。`);
  }
  return value;
}

export function parseVerifyArguments(args: readonly string[]): ParsedVerifyArguments {
  let environment: ParsedVerifyArguments["environment"] | undefined;
  let scope: ParsedVerifyArguments["scope"] | undefined;
  let reportKey: string | undefined;
  let businessCommand: string | undefined;
  let skipBusinessValidation = false;
  let dryRun = false;
  const seen = new Set<string>();

  for (let index = 0; index < args.length; index += 1) {
    const flag = args[index];
    if (flag === undefined || !flag.startsWith("--")) {
      throw new Error(`不支持的位置参数：${flag ?? "（空）"}。`);
    }
    if (seen.has(flag)) throw new Error(`参数 ${flag} 不允许重复。`);
    seen.add(flag);
    if (flag === "--skip-business-validation") {
      skipBusinessValidation = true;
      continue;
    }
    if (flag === "--dry-run") {
      dryRun = true;
      continue;
    }
    const value = requiredValue(args, index, flag);
    index += 1;
    if (flag === "--env") {
      if (value !== "test" && value !== "real-pre") {
        throw new Error("--env 只允许 test 或 real-pre。");
      }
      environment = value;
    } else if (flag === "--scope") {
      if (value !== "backend" && value !== "frontend" && value !== "full") {
        throw new Error("--scope 只允许 backend、frontend 或 full。");
      }
      scope = value;
    } else if (flag === "--report-key") {
      validateReportKey(value);
      reportKey = value;
    } else if (flag === "--business-command") {
      businessCommand = value;
    } else {
      throw new Error(`未知参数：${flag}。`);
    }
  }
  if (environment === undefined || scope === undefined || reportKey === undefined) {
    throw new Error("必须同时提供 --env、--scope 与 --report-key。");
  }
  return {
    environment,
    scope,
    reportKey,
    skipBusinessValidation,
    dryRun,
    ...(businessCommand === undefined ? {} : { businessCommand }),
  };
}

function exitCodeFor(status: RunStatus): number {
  if (status === "PASS") return 0;
  if (status === "FAIL") return 1;
  return 2;
}

function safeError(error: unknown): string {
  let message = "未知错误";
  try {
    message = error instanceof Error ? error.message : String(error);
  } catch {
    message = "异常详情无法安全读取";
  }
  return redactEvidenceText(message.slice(0, 2_000))
    .replace(/[\u0000-\u001f\u007f-\u009f]/gu, " ")
    .replace(/\s+/gu, " ")
    .trim()
    .slice(0, 500) || "未知错误";
}

function contractError(error: unknown): boolean {
  const name = error instanceof Error ? error.name : "";
  return name === "VerifyContractError" || name === "EnvironmentContractError";
}

function renderSummary(outcome: NodeVerifyOutcome): string {
  const lines = [
    "",
    "=== 验证摘要 ===",
    `结论：${outcome.result.status}（${outcome.result.statusLabel}）`,
    outcome.result.summary,
    `稳定 JSON：${outcome.evidence.stableJson}`,
    `稳定 Markdown：${outcome.evidence.stableMarkdown}`,
  ];
  for (const check of outcome.result.checks) {
    lines.push(`- [${check.status}] ${check.title}：${check.summary}`);
  }
  for (const warning of outcome.evidence.cleanupWarnings) {
    lines.push(`- 证据清理警告：${warning}`);
  }
  return lines.join("\n");
}

export async function verifyCli(
  args: readonly string[] = [],
  dependencies: VerifyCliDependencies = DEFAULT_DEPENDENCIES,
): Promise<number> {
  if (args.length === 1 && (args[0] === "--help" || args[0] === "-h")) {
    dependencies.writeStdout(renderVerifyHelp());
    return 0;
  }

  let parsed: ParsedVerifyArguments;
  try {
    parsed = parseVerifyArguments(args);
  } catch (error) {
    dependencies.writeStderr(`参数错误：${safeError(error)} 使用 --help 查看中文帮助。`);
    return 3;
  }

  try {
    const injectedSnapshot = parseInjectedGitSnapshot(
      dependencies.readEnvironmentVariable("HARNESS_VERIFY_GIT_SNAPSHOT_JSON"),
    );
    const invocation = invocationId(
      dependencies.readEnvironmentVariable("HARNESS_VERIFY_INVOCATION_ID"),
    );
    const outcome = await dependencies.runVerify({
      repoRoot: dependencies.cwd(),
      ...parsed,
      ...(injectedSnapshot === undefined ? {} : { gitSnapshot: injectedSnapshot }),
      onStage: (message) => dependencies.writeStdout(`=== ${message} ===`),
    });
    dependencies.writeStdout(renderSummary(outcome));
    if (invocation !== undefined) {
      dependencies.writeStdout(renderReceipt(outcome, parsed, invocation, dependencies.digestFile));
    }
    return exitCodeFor(outcome.result.status);
  } catch (error) {
    if (contractError(error)) {
      dependencies.writeStderr(`契约错误：${safeError(error)} 修复参数或 Harness 契约后重试；未输出堆栈。`);
      return 3;
    }
    dependencies.writeStderr(`执行受阻：${safeError(error)} 确认证据目录、权限和本地工具链后安全重试一次；未输出堆栈。`);
    return 2;
  }
}

function isMainModule(): boolean {
  const entry = process.argv[1];
  return entry !== undefined && pathToFileURL(resolve(entry)).href === import.meta.url;
}

if (isMainModule()) {
  process.exitCode = await verifyCli(process.argv.slice(2));
}
