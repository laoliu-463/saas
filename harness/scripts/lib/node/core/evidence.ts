import { randomUUID } from "node:crypto";
import {
  lstatSync,
  mkdirSync,
  readFileSync,
  realpathSync,
  renameSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { basename, dirname, isAbsolute, relative, resolve } from "node:path";

import { Ajv2020 } from "ajv/dist/2020.js";
import type { AnySchema, ErrorObject } from "ajv";

import { redactEvidenceText } from "./redact.js";
import {
  SCHEMA_VERSION,
  validateRunResult,
  type CheckResult,
  type RunResult,
  type ValidationResult,
} from "./result.js";
import {
  formatShanghaiTime,
  toRepositoryRelativePath,
  type HarnessEnvironment,
  type HarnessScope,
  type RunContext,
} from "./run-context.js";
import type { EvidenceGitSnapshot, GitSnapshot } from "./git.js";

export interface EvidencePaths {
  readonly rawJson: string;
  readonly stableJson: string;
  readonly stableMarkdown: string;
}

export interface EvidenceWriteOutcome extends EvidencePaths {
  readonly cleanupWarnings: readonly string[];
}

export interface EvidenceReport {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly runId: string;
  readonly reportKey: string;
  readonly environment: HarnessEnvironment;
  readonly scope: HarnessScope;
  readonly startedAt: string;
  readonly startedAtShanghai: string;
  readonly finishedAt: string;
  readonly finishedAtShanghai: string;
  readonly durationMs: number;
  readonly git: EvidenceGitSnapshot;
  readonly result: RunResult;
  readonly evidencePaths: EvidencePaths;
}

export interface CreateEvidenceOptions {
  readonly finishedAt?: Date;
  readonly secrets?: readonly string[];
}

type EvidencePathKind =
  | "MISSING"
  | "FILE"
  | "DIRECTORY"
  | "SYMLINK"
  | "OTHER";

interface EvidenceFileOps {
  readonly pathKind: (path: string) => EvidencePathKind;
  readonly canonicalPath: (path: string) => string;
  readonly ensureDirectory: (path: string) => void;
  readonly writeExclusive: (path: string, content: string) => void;
  readonly move: (source: string, target: string) => void;
  readonly remove: (path: string) => void;
}

export interface WriteEvidenceOptions {
  readonly repoRoot: string;
  readonly secrets?: readonly string[];
  readonly failpoints?: readonly EvidenceFailpoint[];
}

export const EVIDENCE_OPERATION_STAGES = [
  "ENSURE_DIRECTORY",
  "WRITE_TEMP",
  "MOVE_TO_BACKUP",
  "INSTALL_FINAL",
  "ROLLBACK_REMOVE_FINAL",
  "ROLLBACK_RESTORE_BACKUP",
  "ROLLBACK_REMOVE_TEMP",
  "CLEANUP_BACKUP",
] as const;

export type EvidenceOperationStage = typeof EVIDENCE_OPERATION_STAGES[number];

export interface EvidenceFailpoint {
  readonly stage: EvidenceOperationStage;
  readonly targetIndex: number;
}

export class EvidenceWriteError extends Error {
  readonly rootCause: string;
  readonly safeRetry: string;
  readonly stopCondition: string;
  readonly rollbackComplete: boolean;

  constructor(code: string, rollbackComplete = true) {
    const safeCode = /^[A-Z0-9_-]+$/u.test(code) ? code : "EVIDENCE_IO_ERROR";
    const rootCause = rollbackComplete
      ? `证据写入失败（${safeCode}），已尝试恢复写入前状态。`
      : `证据写入失败（${safeCode}），且回滚未完整。`;
    super(rootCause);
    this.name = "EvidenceWriteError";
    this.rootCause = rootCause;
    this.safeRetry = rollbackComplete
      ? "确认目录权限、剩余空间和文件占用状态后，可安全重试一次。"
      : "先人工核对三份最终证据与可识别备份，恢复一致后才可重试。";
    this.stopCondition = rollbackComplete
      ? "若同一错误再次出现，停止重试并修复存储或权限问题。"
      : "立即停止自动重试，必须人工检查并确认三份证据的一致性。";
    this.rollbackComplete = rollbackComplete;
  }
}

const NODE_FILE_OPS: EvidenceFileOps = {
  pathKind(path) {
    try {
      const stat = lstatSync(path);
      if (stat.isSymbolicLink()) return "SYMLINK";
      if (stat.isFile()) return "FILE";
      if (stat.isDirectory()) return "DIRECTORY";
      return "OTHER";
    } catch (error) {
      if (["ENOENT", "ENOTDIR"].includes((error as NodeJS.ErrnoException).code ?? "")) {
        return "MISSING";
      }
      throw error;
    }
  },
  canonicalPath: (path) => realpathSync.native(path),
  ensureDirectory: (path) => mkdirSync(path, { recursive: true }),
  writeExclusive: (path, content) =>
    writeFileSync(path, content, { encoding: "utf8", flag: "wx" }),
  move: (source, target) => renameSync(source, target),
  remove: (path) => rmSync(path, { force: true }),
};

const CONTRACT_ROOT = new URL("../../contracts/", import.meta.url);

function readSchema(name: string): AnySchema {
  return JSON.parse(readFileSync(new URL(name, CONTRACT_ROOT), "utf8")) as AnySchema;
}

const ajv = new Ajv2020({ allErrors: true, strict: true });
ajv.addSchema(readSchema("check-result.schema.json"));
ajv.addSchema(readSchema("run-result.schema.json"));
const validateSchema = ajv.compile<EvidenceReport>(
  readSchema("evidence-report.schema.json"),
);

function schemaErrors(errors: ErrorObject[] | null | undefined): string[] {
  if (!errors || errors.length === 0) return ["证据不符合 JSON Schema。"];
  return errors.map((error) => {
    const path = error.instancePath || "/";
    return `字段 ${path} 不符合证据契约（${error.keyword}）。`;
  });
}

function sortedUnique(values: readonly string[]): string[] {
  return [...new Set(values)].sort((left, right) => left.localeCompare(right, "en"));
}

interface PathSemantics {
  readonly relative: (from: string, to: string) => string;
  readonly isAbsolute: (path: string) => boolean;
}

const NATIVE_PATH_SEMANTICS: PathSemantics = { relative, isAbsolute };

export function isEvidencePathInside(
  root: string,
  candidate: string,
  semantics: PathSemantics = NATIVE_PATH_SEMANTICS,
): boolean {
  const relation = semantics.relative(root, candidate);
  return relation === "" || (
    !semantics.isAbsolute(relation) &&
    relation !== ".." &&
    !relation.startsWith("../") &&
    !relation.startsWith("..\\")
  );
}

function nearestExisting(path: string, fileOps: EvidenceFileOps): string {
  let cursor = path;
  for (;;) {
    if (fileOps.pathKind(cursor) !== "MISSING") return cursor;
    const parent = dirname(cursor);
    if (parent === cursor) {
      throw new Error("canonical 路径检查无法找到存在的父目录。");
    }
    cursor = parent;
  }
}

function assertCanonicalRepositoryPath(
  repoRoot: string,
  relativePath: string,
  fileOps: EvidenceFileOps,
  rejectFinalLink: boolean,
): string {
  const root = resolve(repoRoot);
  const absolute = resolve(root, ...relativePath.split("/"));
  if (!isEvidencePathInside(root, absolute)) {
    throw new Error("证据路径必须位于仓库内。");
  }

  const rootKind = fileOps.pathKind(root);
  if (rootKind !== "DIRECTORY" && rootKind !== "SYMLINK") {
    throw new Error("仓库根目录不存在或类型不受支持。");
  }
  const canonicalRoot = fileOps.canonicalPath(root);
  const finalKind = fileOps.pathKind(absolute);
  if (rejectFinalLink && finalKind === "SYMLINK") {
    throw new Error("证据最终文件不得是符号链接或 junction。");
  }
  if (rejectFinalLink && (finalKind === "DIRECTORY" || finalKind === "OTHER")) {
    throw new Error("证据最终路径必须是普通文件或尚未存在。");
  }
  const existing = nearestExisting(absolute, fileOps);
  const canonicalExisting = fileOps.canonicalPath(existing);
  if (!isEvidencePathInside(canonicalRoot, canonicalExisting)) {
    throw new Error("证据路径通过符号链接或 junction 解析到仓库外。");
  }
  return absolute;
}

function ensureCanonicalDirectory(
  repoRoot: string,
  absoluteDirectory: string,
  fileOps: EvidenceFileOps,
): void {
  const root = resolve(repoRoot);
  if (!isEvidencePathInside(root, absoluteDirectory)) {
    throw new Error("证据目录必须位于仓库内。");
  }
  const rootKind = fileOps.pathKind(root);
  if (rootKind !== "DIRECTORY" && rootKind !== "SYMLINK") {
    throw new Error("仓库根目录不存在或类型不受支持。");
  }
  const canonicalRoot = fileOps.canonicalPath(root);
  const segments = relative(root, absoluteDirectory)
    .split(/[\\/]+/u)
    .filter((segment) => segment.length > 0);
  let cursor = root;

  for (const segment of segments) {
    cursor = resolve(cursor, segment);
    let kind = fileOps.pathKind(cursor);
    if (kind === "MISSING") {
      fileOps.ensureDirectory(cursor);
      kind = fileOps.pathKind(cursor);
    }
    if (kind !== "DIRECTORY" && kind !== "SYMLINK") {
      throw new Error("证据目录路径包含非目录节点。");
    }
    const canonical = fileOps.canonicalPath(cursor);
    if (!isEvidencePathInside(canonicalRoot, canonical)) {
      throw new Error("证据目录通过符号链接或 junction 解析到仓库外。");
    }
  }
}

function sanitizePath(
  repoRoot: string,
  value: string,
  secrets: readonly string[],
  fileOps: EvidenceFileOps,
): string {
  const relativePath = toRepositoryRelativePath(
    repoRoot,
    redactEvidenceText(value, secrets),
  );
  assertCanonicalRepositoryPath(repoRoot, relativePath, fileOps, false);
  return relativePath;
}

function sanitizeCheck(
  repoRoot: string,
  check: CheckResult,
  secrets: readonly string[],
  fileOps: EvidenceFileOps,
): CheckResult {
  return {
    ...check,
    title: redactEvidenceText(check.title, secrets),
    summary: redactEvidenceText(check.summary, secrets),
    nextActions: check.nextActions.map((action) => redactEvidenceText(action, secrets)),
    artifacts: check.artifacts.map((artifact) =>
      sanitizePath(repoRoot, artifact, secrets, fileOps)),
  };
}

function sanitizeResult(
  repoRoot: string,
  result: RunResult,
  secrets: readonly string[],
  fileOps: EvidenceFileOps,
): RunResult {
  const validation = validateRunResult(result);
  if (!validation.valid) {
    throw new Error(`无法生成证据：${validation.errors.join("；")}`);
  }
  return {
    ...result,
    summary: redactEvidenceText(result.summary, secrets),
    checks: result.checks.map((check) =>
      sanitizeCheck(repoRoot, check, secrets, fileOps)),
  };
}

function sanitizeGit(
  repoRoot: string,
  git: EvidenceGitSnapshot,
  secrets: readonly string[],
  fileOps: EvidenceFileOps,
): EvidenceGitSnapshot {
  if (git.identity.kind === "UNAVAILABLE") return git;
  const collected = git as GitSnapshot;
  const changedFiles = sortedUnique(
    collected.changedFiles.map((file) => sanitizePath(repoRoot, file, secrets, fileOps)),
  );
  const identity = collected.identity.kind === "COMMIT"
    ? { ...collected.identity }
    : { ...collected.identity, changedFiles };
  return {
    ...collected,
    branch: redactEvidenceText(collected.branch, secrets),
    changedFiles,
    identity,
  };
}

function expectedPaths(runId: string, reportKey: string): EvidencePaths {
  return {
    rawJson: `runtime/qa/out/${runId}/run.json`,
    stableJson: `runtime/qa/out/latest-${reportKey}.json`,
    stableMarkdown: `runtime/qa/out/latest-${reportKey}.md`,
  };
}

export function validateEvidenceReport(value: unknown): ValidationResult {
  if (!validateSchema(value)) {
    return { valid: false, errors: schemaErrors(validateSchema.errors) };
  }
  const report = value as EvidenceReport;
  const errors: string[] = [];
  if (JSON.stringify(report.evidencePaths) !== JSON.stringify(
    expectedPaths(report.runId, report.reportKey),
  )) {
    errors.push("证据路径与 runId 或 ReportKey 不一致。");
  }
  if (report.git.identity.kind === "UNAVAILABLE") {
    if (report.result.status === "PASS") {
      errors.push("Git 身份未采集时运行结论不得为 PASS。");
    }
  } else if (report.git.identity.kind === "COMMIT") {
    if (report.git.identity.commitSha !== report.git.headSha) {
      errors.push("COMMIT 身份的 commitSha 必须与 HEAD 一致。");
    }
  } else {
    if (report.git.identity.headSha !== report.git.headSha) {
      errors.push("WORKTREE 身份的 headSha 必须与 HEAD 一致。");
    }
    if (JSON.stringify(report.git.identity.changedFiles) !== JSON.stringify(
      report.git.changedFiles,
    )) {
      errors.push("WORKTREE 身份的变更文件必须与 Git 快照一致。");
    }
  }
  const runValidation = validateRunResult(report.result);
  if (!runValidation.valid) errors.push(...runValidation.errors);
  return errors.length === 0 ? { valid: true } : { valid: false, errors };
}

function assertValid(report: EvidenceReport): void {
  const validation = validateEvidenceReport(report);
  if (!validation.valid) {
    throw new Error(`无法生成证据：${validation.errors.join("；")}`);
  }
}

export function createEvidenceReport(
  context: RunContext,
  result: RunResult,
  options: CreateEvidenceOptions = {},
): EvidenceReport {
  const finishedAt = options.finishedAt ?? new Date();
  const startedAt = new Date(context.startedAt);
  const durationMs = finishedAt.getTime() - startedAt.getTime();
  if (Number.isNaN(finishedAt.getTime()) || durationMs < 0) {
    throw new Error("证据完成时间无效或早于开始时间。");
  }
  const secrets = options.secrets ?? [];
  const report: EvidenceReport = {
    schemaVersion: SCHEMA_VERSION,
    runId: context.runId,
    reportKey: context.reportKey,
    environment: context.environment,
    scope: context.scope,
    startedAt: context.startedAt,
    startedAtShanghai: context.startedAtShanghai,
    finishedAt: finishedAt.toISOString(),
    finishedAtShanghai: formatShanghaiTime(finishedAt),
    durationMs,
    git: sanitizeGit(context.repoRoot, context.git, secrets, NODE_FILE_OPS),
    result: sanitizeResult(context.repoRoot, result, secrets, NODE_FILE_OPS),
    evidencePaths: expectedPaths(context.runId, context.reportKey),
  };
  assertValid(report);
  return report;
}

const MARKDOWN_TEXT_ENTITIES: Readonly<Record<string, string>> = {
  "&": "&amp;",
  "<": "&lt;",
  ">": "&gt;",
  "|": "&#124;",
  "\\": "&#92;",
  "`": "&#96;",
  "*": "&#42;",
  "_": "&#95;",
  "{": "&#123;",
  "}": "&#125;",
  "[": "&#91;",
  "]": "&#93;",
  "(": "&#40;",
  ")": "&#41;",
  "#": "&#35;",
  "+": "&#43;",
  "-": "&#45;",
  ".": "&#46;",
  "!": "&#33;",
  "\"": "&quot;",
  "'": "&#39;",
};

function escapeMarkdownPlainText(value: string, lineBreak: string): string {
  const normalized = value.replace(/[\r\n]+/gu, lineBreak);
  return [...normalized]
    .map((character) => MARKDOWN_TEXT_ENTITIES[character] ?? character)
    .join("");
}

export function escapeMarkdownTableText(value: string): string {
  return escapeMarkdownPlainText(value, " ");
}

export function escapeMarkdownParagraphText(value: string): string {
  return escapeMarkdownPlainText(value, " ↵ ");
}

export function renderMarkdownCodeSpan(value: string): string {
  const normalized = value
    .replace(/[\r\n]+/gu, " ↵ ")
    .replaceAll("|", "\\|");
  const longestRun = Math.max(
    0,
    ...(normalized.match(/`+/gu) ?? []).map((run) => run.length),
  );
  const delimiter = "`".repeat(longestRun + 1);
  const visible = /^\s+$/u.test(normalized)
    ? normalized.replaceAll(" ", "␠")
    : normalized;
  return `${delimiter} ${visible} ${delimiter}`;
}

export function renderEvidenceMarkdown(report: EvidenceReport): string {
  assertValid(report);
  const lines = [
    "# Harness 验证证据",
    "",
    `- 运行 ID：${renderMarkdownCodeSpan(report.runId)}`,
    `- 环境：${renderMarkdownCodeSpan(report.environment)}`,
    `- 范围：${renderMarkdownCodeSpan(report.scope)}`,
    `- 分支：${renderMarkdownCodeSpan(report.git.branch ?? "未采集")}`,
    `- HEAD：${renderMarkdownCodeSpan(report.git.headSha ?? "未采集")}`,
    `- 证据身份：${renderMarkdownCodeSpan(report.git.identity.kind)}`,
    `- 开始时间：${report.startedAtShanghai}（${renderMarkdownCodeSpan(report.startedAt)}）`,
    `- 完成时间：${report.finishedAtShanghai}（${renderMarkdownCodeSpan(report.finishedAt)}）`,
    `- 运行结论：${report.result.statusLabel}（${report.result.status}）`,
    "",
    "## 结果摘要",
    "",
    escapeMarkdownParagraphText(report.result.summary),
    "",
    "## 检查结果",
    "",
    "| 检查项 | 状态 | 阻断 | 摘要 |",
    "| --- | --- | --- | --- |",
    ...report.result.checks.map(
      (check) =>
        `| ${escapeMarkdownTableText(check.title)} | ${check.statusLabel}（${check.status}） | ${check.blocking ? "是" : "否"} | ${escapeMarkdownTableText(check.summary)} |`,
    ),
  ];
  const actionRows = report.result.checks.flatMap((check) =>
    check.nextActions.map((action) =>
      `| ${escapeMarkdownTableText(check.title)} | ${escapeMarkdownTableText(action)} |`),
  );
  if (actionRows.length > 0) {
    lines.push(
      "",
      "## 后续操作",
      "",
      "| 检查项 | 操作 |",
      "| --- | --- |",
      ...actionRows,
    );
  }
  if (report.git.identity.kind === "WORKTREE") {
    lines.push("", "## 工作区身份", "");
    lines.push(`- 补丁指纹：${renderMarkdownCodeSpan(report.git.identity.patchFingerprint)}`);
    lines.push(...report.git.changedFiles.map((file) =>
      `- ${renderMarkdownCodeSpan(file)}`));
  }
  lines.push("", "## 证据路径", "");
  lines.push(`- 原始 JSON：${renderMarkdownCodeSpan(report.evidencePaths.rawJson)}`);
  lines.push(`- 稳定 JSON：${renderMarkdownCodeSpan(report.evidencePaths.stableJson)}`);
  lines.push(
    `- 稳定 Markdown：${renderMarkdownCodeSpan(report.evidencePaths.stableMarkdown)}`,
    "",
  );
  return lines.join("\n");
}

function explicitSecretVariants(secrets: readonly string[]): string[] {
  const variants = new Set<string>();
  for (const secret of secrets) {
    if (secret.length === 0) continue;
    variants.add(secret);
    variants.add(JSON.stringify(secret).slice(1, -1));
  }
  return [...variants].filter((value) => value.length > 0);
}

function containsSecret(value: string, variants: readonly string[]): boolean {
  return variants.some((secret) => value.includes(secret));
}

function assertIdentitySecretFree(
  report: EvidenceReport,
  variants: readonly string[],
): void {
  const identityFields = [
    report.runId,
    report.reportKey,
    report.evidencePaths.rawJson,
    report.evidencePaths.stableJson,
    report.evidencePaths.stableMarkdown,
    ...report.result.checks.map((check) => check.checkId),
  ];
  if (identityFields.some((value) => containsSecret(value, variants))) {
    throw new Error("证据身份字段命中显式 secret，不允许静默替换或写入。");
  }
}

function assertNoSecret(value: unknown, variants: readonly string[]): void {
  const serialized = JSON.stringify(value);
  if (containsSecret(serialized, variants)) {
    throw new Error("证据写入前检测到未清洗的敏感信息，已停止所有文件操作。");
  }
}

function sanitizeForWrite(
  report: EvidenceReport,
  repoRoot: string,
  secrets: readonly string[],
  fileOps: EvidenceFileOps,
): EvidenceReport {
  const safe: EvidenceReport = {
    ...report,
    git: sanitizeGit(repoRoot, report.git, secrets, fileOps),
    result: sanitizeResult(repoRoot, report.result, secrets, fileOps),
    evidencePaths: { ...report.evidencePaths },
  };
  assertValid(safe);
  return safe;
}

interface TransactionTarget {
  readonly path: string;
  readonly content: string;
  readonly absolute: string;
  temporary: string;
  backup: string;
  backedUp: boolean;
  installed: boolean;
}

function operationCode(error: unknown, fallback: string): string {
  return (error as NodeJS.ErrnoException).code ?? fallback;
}

function attemptRollback(action: () => void, failures: string[]): void {
  try {
    action();
  } catch {
    failures.push("ROLLBACK_OPERATION_FAILED");
  }
}

function validateFailpoints(
  failpoints: readonly EvidenceFailpoint[] | undefined,
): readonly EvidenceFailpoint[] {
  const allowed = new Set<string>(EVIDENCE_OPERATION_STAGES);
  const result = failpoints ?? [];
  for (const failpoint of result) {
    if (
      !allowed.has(failpoint.stage) ||
      !Number.isInteger(failpoint.targetIndex) ||
      failpoint.targetIndex < 0 ||
      failpoint.targetIndex > 2
    ) {
      throw new Error("证据 failpoint 必须使用已知阶段和 0 至 2 的目标索引。");
    }
  }
  return result;
}

function triggerFailpoint(
  failpoints: readonly EvidenceFailpoint[],
  stage: EvidenceOperationStage,
  targetIndex: number,
): void {
  if (failpoints.some((item) => item.stage === stage && item.targetIndex === targetIndex)) {
    throw Object.assign(new Error(`evidence failpoint: ${stage}[${targetIndex}]`), {
      code: `FAILPOINT_${stage}_${targetIndex}`,
    });
  }
}

function rollbackTransaction(
  targets: readonly TransactionTarget[],
  fileOps: EvidenceFileOps,
  failpoints: readonly EvidenceFailpoint[],
): boolean {
  const failures: string[] = [];
  for (let index = targets.length - 1; index >= 0; index -= 1) {
    const target = targets[index]!;
    if (target.installed) {
      attemptRollback(() => {
        triggerFailpoint(failpoints, "ROLLBACK_REMOVE_FINAL", index);
        fileOps.remove(target.absolute);
      }, failures);
    }
  }
  for (let index = 0; index < targets.length; index += 1) {
    const target = targets[index]!;
    if (target.backedUp) {
      attemptRollback(() => {
        triggerFailpoint(failpoints, "ROLLBACK_RESTORE_BACKUP", index);
        fileOps.move(target.backup, target.absolute);
      }, failures);
    }
  }
  for (let index = 0; index < targets.length; index += 1) {
    const target = targets[index]!;
    if (target.temporary) {
      attemptRollback(() => {
        triggerFailpoint(failpoints, "ROLLBACK_REMOVE_TEMP", index);
        fileOps.remove(target.temporary);
      }, failures);
    }
  }
  return failures.length === 0;
}

function prepareTargets(
  report: EvidenceReport,
  repoRoot: string,
  fileOps: EvidenceFileOps,
): TransactionTarget[] {
  const json = `${JSON.stringify(report)}\n`;
  const markdown = renderEvidenceMarkdown(report);
  const targets = [
    { path: report.evidencePaths.rawJson, content: json },
    { path: report.evidencePaths.stableJson, content: json },
    { path: report.evidencePaths.stableMarkdown, content: markdown },
  ];
  return targets.map((target) => ({
    ...target,
    absolute: assertCanonicalRepositoryPath(repoRoot, target.path, fileOps, true),
    temporary: "",
    backup: "",
    backedUp: false,
    installed: false,
  }));
}

export function writeEvidence(
  report: EvidenceReport,
  options: WriteEvidenceOptions,
): EvidenceWriteOutcome {
  const secrets = options.secrets ?? [];
  const failpoints = validateFailpoints(options.failpoints);
  const variants = explicitSecretVariants(secrets);
  assertIdentitySecretFree(report, variants);
  const fileOps = NODE_FILE_OPS;
  const safeReport = sanitizeForWrite(report, options.repoRoot, secrets, fileOps);
  assertNoSecret(safeReport, variants);

  const targets = prepareTargets(safeReport, options.repoRoot, fileOps);
  assertNoSecret(
    [options.repoRoot, ...targets.map((target) => target.absolute)],
    variants,
  );
  try {
    for (let index = 0; index < targets.length; index += 1) {
      const target = targets[index]!;
      triggerFailpoint(failpoints, "ENSURE_DIRECTORY", index);
      ensureCanonicalDirectory(options.repoRoot, dirname(target.absolute), fileOps);
    }
    for (let index = 0; index < targets.length; index += 1) {
      const target = targets[index]!;
      assertCanonicalRepositoryPath(options.repoRoot, target.path, fileOps, true);
      target.temporary = resolve(
        dirname(target.absolute),
        `.${basename(target.absolute)}.tmp-${randomUUID()}`,
      );
      triggerFailpoint(failpoints, "WRITE_TEMP", index);
      fileOps.writeExclusive(target.temporary, target.content);
    }

    for (let index = 0; index < targets.length; index += 1) {
      const target = targets[index]!;
      const kind = fileOps.pathKind(target.absolute);
      if (kind === "FILE") {
        target.backup = resolve(
          dirname(target.absolute),
          `.${basename(target.absolute)}.bak-${randomUUID()}`,
        );
        triggerFailpoint(failpoints, "MOVE_TO_BACKUP", index);
        fileOps.move(target.absolute, target.backup);
        target.backedUp = true;
      } else if (kind !== "MISSING") {
        throw Object.assign(new Error("final path changed during transaction"), {
          code: "FINAL_PATH_CHANGED",
        });
      }
    }

    for (let index = 0; index < targets.length; index += 1) {
      const target = targets[index]!;
      triggerFailpoint(failpoints, "INSTALL_FINAL", index);
      fileOps.move(target.temporary, target.absolute);
      target.installed = true;
    }
  } catch (error) {
    const rollbackComplete = rollbackTransaction(targets, fileOps, failpoints);
    throw new EvidenceWriteError(
      operationCode(error, "EVIDENCE_IO_ERROR"),
      rollbackComplete,
    );
  }

  const cleanupWarnings: string[] = [];
  for (let index = 0; index < targets.length; index += 1) {
    const target = targets[index]!;
    if (!target.backedUp) continue;
    try {
      triggerFailpoint(failpoints, "CLEANUP_BACKUP", index);
      fileOps.remove(target.backup);
    } catch {
      cleanupWarnings.push(`新证据已提交，但备份清理未完成：${target.path}`);
    }
  }
  return {
    ...safeReport.evidencePaths,
    cleanupWarnings,
  };
}
