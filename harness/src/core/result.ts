import { readFileSync } from "node:fs";

import { Ajv2020 } from "ajv/dist/2020.js";
import type { AnySchema, ErrorObject } from "ajv";

export const SCHEMA_VERSION = "1.0.0" as const;

export const CHECK_STATUS_LABELS = {
  PASS: "通过",
  FAIL: "失败",
  BLOCKED: "阻塞",
  WARN: "警告",
  SKIPPED: "已跳过",
  NOT_COLLECTED: "未采集",
} as const;

export const RUN_STATUS_LABELS = {
  PASS: "通过",
  FAIL: "失败",
  BLOCKED: "阻塞",
  PARTIAL: "部分完成",
} as const;

export type CheckStatus = keyof typeof CHECK_STATUS_LABELS;
export type RunStatus = keyof typeof RUN_STATUS_LABELS;
export type CheckStatusLabel = (typeof CHECK_STATUS_LABELS)[CheckStatus];
export type RunStatusLabel = (typeof RUN_STATUS_LABELS)[RunStatus];

export interface CheckResultInput {
  readonly checkId: string;
  readonly title: string;
  readonly status: CheckStatus;
  readonly blocking: boolean;
  readonly summary: string;
  readonly nextActions: readonly string[];
  readonly artifacts: readonly string[];
}

export interface CheckResult extends CheckResultInput {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly statusLabel: CheckStatusLabel;
}

export interface RunResult {
  readonly schemaVersion: typeof SCHEMA_VERSION;
  readonly status: RunStatus;
  readonly statusLabel: RunStatusLabel;
  readonly summary: string;
  readonly checks: readonly CheckResult[];
}

export type ValidationResult =
  | { readonly valid: true }
  | { readonly valid: false; readonly errors: readonly string[] };

const RUN_STATUS_SUMMARIES: Record<RunStatus, string> = {
  PASS: "全部阻断检查均已通过。",
  FAIL: "存在阻断检查失败。",
  BLOCKED: "验证存在阻塞项，请先解除阻塞。",
  PARTIAL: "验证仅部分完成，存在未通过、跳过或未采集项。",
};

const CHECK_SCHEMA_URL = new URL(
  "../../contracts/check-result.schema.json",
  import.meta.url,
);
const RUN_SCHEMA_URL = new URL(
  "../../contracts/run-result.schema.json",
  import.meta.url,
);

function readSchema(url: URL): AnySchema {
  const parsed: unknown = JSON.parse(readFileSync(url, "utf8"));
  if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
    throw new Error(`结果契约不是有效的 JSON 对象：${url.pathname}`);
  }
  return parsed as AnySchema;
}

const ajv = new Ajv2020({ allErrors: true, strict: true });
const validateCheckSchema = ajv.compile<CheckResult>(readSchema(CHECK_SCHEMA_URL));
const validateRunSchema = ajv.compile<RunResult>(readSchema(RUN_SCHEMA_URL));

function isCheckResult(value: unknown): value is CheckResult {
  return validateCheckSchema(value) === true;
}

function isRunResult(value: unknown): value is RunResult {
  return validateRunSchema(value) === true;
}

function formatSchemaErrors(errors: ErrorObject[] | null | undefined): string[] {
  if (!errors || errors.length === 0) {
    return ["结果不符合 JSON Schema，但校验器未返回详细原因。"];
  }

  return errors.map((error) => {
    const path = error.instancePath || "/";
    if (error.keyword === "required") {
      return `字段 ${path} 缺少必填项 ${String(error.params["missingProperty"])}。`;
    }
    if (error.keyword === "additionalProperties") {
      return `字段 ${path} 包含未允许的属性 ${String(error.params["additionalProperty"])}。`;
    }
    return `字段 ${path} 不符合结果契约（${error.keyword}）。`;
  });
}

export function validateCheckResult(value: unknown): ValidationResult {
  if (!isCheckResult(value)) {
    return { valid: false, errors: formatSchemaErrors(validateCheckSchema.errors) };
  }

  const expectedLabel = CHECK_STATUS_LABELS[value.status];
  if (value.statusLabel !== expectedLabel) {
    return {
      valid: false,
      errors: [`状态标签不一致：${value.status} 必须使用“${expectedLabel}”。`],
    };
  }
  return { valid: true };
}

export function aggregateRunStatus(checks: readonly CheckResult[]): RunStatus {
  const blockingChecks = checks.filter((check) => check.blocking);
  if (blockingChecks.some((check) => check.status === "FAIL")) return "FAIL";
  if (checks.some((check) => check.status === "BLOCKED")) return "BLOCKED";
  if (checks.length === 0 || blockingChecks.length === 0) return "PARTIAL";
  if (
    blockingChecks.some(
      (check) => check.status === "SKIPPED" || check.status === "NOT_COLLECTED",
    )
  ) {
    return "PARTIAL";
  }
  if (blockingChecks.some((check) => check.status !== "PASS")) return "PARTIAL";
  if (checks.some((check) => !check.blocking && check.status === "FAIL")) {
    return "PARTIAL";
  }
  return "PASS";
}

export function validateRunResult(value: unknown): ValidationResult {
  if (!isRunResult(value)) {
    return { valid: false, errors: formatSchemaErrors(validateRunSchema.errors) };
  }

  const errors: string[] = [];
  const expectedLabel = RUN_STATUS_LABELS[value.status];
  if (value.statusLabel !== expectedLabel) {
    errors.push(`运行状态标签不一致：${value.status} 必须使用“${expectedLabel}”。`);
  }
  value.checks.forEach((check, index) => {
    const result = validateCheckResult(check);
    if (!result.valid) {
      errors.push(...result.errors.map((error) => `检查项 ${index + 1}：${error}`));
    }
  });
  const aggregatedStatus = aggregateRunStatus(value.checks);
  if (value.status !== aggregatedStatus) {
    errors.push(`运行状态与检查聚合结果不一致，应为 ${aggregatedStatus}。`);
  }
  return errors.length === 0 ? { valid: true } : { valid: false, errors };
}

function assertValid(result: ValidationResult, context: string): void {
  if (!result.valid) {
    throw new Error(`${context}：${result.errors.join("；")}`);
  }
}

export function createCheckResult(input: CheckResultInput): CheckResult {
  const result: CheckResult = {
    schemaVersion: SCHEMA_VERSION,
    ...input,
    statusLabel: CHECK_STATUS_LABELS[input.status],
    nextActions: [...input.nextActions],
    artifacts: [...input.artifacts],
  };
  assertValid(validateCheckResult(result), "无法创建检查结果");
  return result;
}

export function createRunResult(checks: readonly CheckResult[]): RunResult {
  if (checks.length === 0) {
    throw new Error("运行结果至少包含一个检查结果。");
  }
  const status = aggregateRunStatus(checks);
  const result: RunResult = {
    schemaVersion: SCHEMA_VERSION,
    status,
    statusLabel: RUN_STATUS_LABELS[status],
    summary: RUN_STATUS_SUMMARIES[status],
    checks: [...checks],
  };
  assertValid(validateRunResult(result), "无法创建运行结果");
  return result;
}
