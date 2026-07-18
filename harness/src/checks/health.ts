import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import {
  getEnvironmentContract,
  parseEnvText,
  type EnvironmentName,
} from "../core/config.js";
import { createCheckResult, type CheckResult } from "../core/result.js";
import type { VerifyScope } from "../workflows/verify.js";
import { healthLogArtifactPath, writeHealthCheckLog } from "./health-log.js";
import { safeCheckText } from "./process-result.js";

export interface HealthHttpRequest {
  readonly url: string;
  readonly timeoutMs: number;
}

export interface HealthHttpResponse {
  readonly statusCode: number;
  readonly body: string;
}

export type HealthHttpClient = (request: HealthHttpRequest) => Promise<unknown>;
export type HealthSleep = (milliseconds: number) => Promise<void>;
export type HealthFetch = (
  input: string | URL,
  init?: RequestInit,
) => Promise<Response>;

export interface HealthAttemptEvidence {
  readonly attempt: number;
  readonly url: string;
  readonly statusCode: number | null;
  readonly success: boolean;
  readonly summary: string;
}

export interface HealthLogInput {
  readonly repoRoot: string;
  readonly rawDir: string;
  readonly probeId: "health-backend" | "health-frontend";
  readonly attempts: readonly HealthAttemptEvidence[];
}

export type HealthLogWriter = (input: HealthLogInput) => Promise<string>;

export interface RunHealthCheckOptions {
  readonly environment: EnvironmentName;
  readonly scope: VerifyScope;
  readonly repoRoot: string;
  readonly rawDir: string;
  readonly envValues?: Readonly<Record<string, string | undefined>>;
  readonly httpClient?: HealthHttpClient;
  readonly sleep?: HealthSleep;
  readonly logWriter?: HealthLogWriter;
  readonly maxAttempts?: number;
  readonly retryDelayMs?: number;
  readonly requestTimeoutMs?: number;
  readonly dryRun?: boolean;
  readonly secrets?: readonly string[];
}

interface HealthProbe {
  readonly probeId: HealthLogInput["probeId"];
  readonly subject: "后端" | "前端";
  readonly urls: readonly string[];
  readonly backend: boolean;
}

interface ProbeOutcome {
  readonly success: boolean;
  readonly attempts: readonly HealthAttemptEvidence[];
  readonly reason: string;
}

const DEFAULT_MAX_ATTEMPTS = 12;
const DEFAULT_RETRY_DELAY_MS = 10_000;
const DEFAULT_REQUEST_TIMEOUT_MS = 10_000;
const MAX_HTTP_BODY_LENGTH = 64 * 1_024;

async function readBoundedResponseBody(response: Response): Promise<string> {
  if (response.body === null) return "";
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let byteLength = 0;
  let body = "";
  try {
    while (true) {
      const chunk = await reader.read();
      if (chunk.done) break;
      byteLength += chunk.value.byteLength;
      if (byteLength > MAX_HTTP_BODY_LENGTH) {
        await reader.cancel();
        throw new Error("HTTP 响应体超过 64 KiB 上限");
      }
      body += decoder.decode(chunk.value, { stream: true });
    }
    body += decoder.decode();
    return body;
  } finally {
    reader.releaseLock();
  }
}

export function createFetchHealthHttpClient(
  fetchImpl: HealthFetch = globalThis.fetch,
): HealthHttpClient {
  return async (request) => {
    const response = await fetchImpl(request.url, {
      method: "GET",
      redirect: "manual",
      signal: AbortSignal.timeout(request.timeoutMs),
    });
    return {
      statusCode: response.status,
      body: await readBoundedResponseBody(response),
    } satisfies HealthHttpResponse;
  };
}

function positiveInteger(
  value: number,
  field: string,
  maximum: number,
  allowZero = false,
): number {
  if (!Number.isInteger(value) || value < (allowZero ? 0 : 1) || value > maximum) {
    throw new Error(`${field} 必须是${allowZero ? "非负" : "正"}整数且不超过 ${maximum}。`);
  }
  return value;
}

function resolvePort(value: string | undefined, fallback: number, key: string): number {
  if (value === undefined || value.trim().length === 0) return fallback;
  if (!/^\d+$/u.test(value.trim())) {
    throw new Error(`${key} 必须是 1 到 65535 的端口。`);
  }
  return positiveInteger(Number(value), key, 65_535);
}

function safeHealthPath(path: string): string {
  if (!path.startsWith("/") || path.startsWith("//") || /[\u0000-\u001f\u007f]/u.test(path)) {
    throw new Error("健康路径必须是站内绝对路径，且不得包含控制字符。");
  }
  return path;
}

function loadEnvironmentValues(
  options: RunHealthCheckOptions,
  envFile: string,
): Readonly<Record<string, string | undefined>> {
  if (options.envValues !== undefined) return options.envValues;
  try {
    return parseEnvText(readFileSync(resolve(options.repoRoot, envFile), "utf8"));
  } catch (error) {
    if (
      typeof error === "object" &&
      error !== null &&
      Reflect.get(error, "code") === "ENOENT"
    ) {
      return {};
    }
    throw error;
  }
}

function selectedProbes(options: RunHealthCheckOptions): readonly HealthProbe[] {
  const contract = getEnvironmentContract(options.environment);
  const envValues = loadEnvironmentValues(options, contract.envFile);
  const backendPort = resolvePort(
    envValues["BACKEND_HOST_PORT"],
    contract.backendPort,
    "BACKEND_HOST_PORT",
  );
  const frontendPort = resolvePort(
    envValues["FRONTEND_HOST_PORT"],
    contract.frontendPort,
    "FRONTEND_HOST_PORT",
  );
  const probes: HealthProbe[] = [];
  if (options.scope === "backend" || options.scope === "full") {
    probes.push({
      probeId: "health-backend",
      subject: "后端",
      urls: [`http://127.0.0.1:${backendPort}${safeHealthPath(contract.backendHealthPath)}`],
      backend: true,
    });
  }
  if (options.scope === "frontend" || options.scope === "full") {
    probes.push({
      probeId: "health-frontend",
      subject: "前端",
      urls: contract.frontendHealthPaths.map((path) =>
        `http://127.0.0.1:${frontendPort}${safeHealthPath(path)}`
      ),
      backend: false,
    });
  }
  if (probes.length === 0) {
    throw new Error(`不支持的健康验证范围：${String(options.scope)}。`);
  }
  return probes;
}

function normalizeHttpResponse(value: unknown): HealthHttpResponse {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new Error("HTTP 响应必须是对象");
  }
  const candidate = value as Record<string, unknown>;
  const statusCode = candidate["statusCode"];
  const body = candidate["body"];
  if (!Number.isInteger(statusCode) || Number(statusCode) < 100 || Number(statusCode) > 599) {
    throw new Error("HTTP statusCode 必须是 100 到 599 的整数");
  }
  if (typeof body !== "string") throw new Error("HTTP body 必须是字符串");
  if (body.length > MAX_HTTP_BODY_LENGTH) throw new Error("HTTP 响应体超过 64 KiB 上限");
  return { statusCode: Number(statusCode), body };
}

function responseIsHealthy(response: HealthHttpResponse, backend: boolean): boolean {
  if (backend) {
    if (response.statusCode < 200 || response.statusCode >= 300) return false;
    try {
      const body = JSON.parse(response.body) as unknown;
      return typeof body === "object" && body !== null && !Array.isArray(body) &&
        (body as Record<string, unknown>)["status"] === "UP";
    } catch {
      return false;
    }
  }
  return response.statusCode >= 200 && response.statusCode < 400;
}

async function runProbe(
  probe: HealthProbe,
  httpClient: HealthHttpClient,
  sleep: HealthSleep,
  maxAttempts: number,
  retryDelayMs: number,
  requestTimeoutMs: number,
  secrets: readonly string[],
): Promise<ProbeOutcome> {
  const attempts: HealthAttemptEvidence[] = [];
  let lastReason = "未收到健康响应";
  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    for (const url of probe.urls) {
      let statusCode: number | null = null;
      let success = false;
      try {
        const response = normalizeHttpResponse(await httpClient({ url, timeoutMs: requestTimeoutMs }));
        statusCode = response.statusCode;
        success = responseIsHealthy(response, probe.backend);
        lastReason = success
          ? `${probe.subject}健康条件满足。`
          : probe.backend
            ? `后端响应未同时满足 2xx 与 JSON status=UP（HTTP ${statusCode}）。`
            : `前端响应不是 2xx/3xx（HTTP ${statusCode}）。`;
      } catch (error) {
        lastReason = `${probe.subject}请求失败：${safeCheckText(error, secrets)}。`;
      }
      attempts.push(Object.freeze({
        attempt,
        url,
        statusCode,
        success,
        summary: safeCheckText(lastReason, secrets),
      }));
      if (success) return { success: true, attempts, reason: lastReason };
    }
    if (attempt < maxAttempts) {
      try {
        await sleep(retryDelayMs);
      } catch (error) {
        lastReason = `健康重试等待失败：${safeCheckText(error, secrets)}。`;
        break;
      }
    }
  }
  return { success: false, attempts, reason: safeCheckText(lastReason, secrets) };
}

function healthFailure(
  summary: string,
  nextActions: readonly string[],
  artifacts: readonly string[],
): CheckResult {
  return createCheckResult({
    checkId: "health",
    title: "验证应用健康状态",
    status: "FAIL",
    blocking: true,
    summary,
    nextActions,
    artifacts,
  });
}

export async function runHealthCheck(
  options: RunHealthCheckOptions,
): Promise<CheckResult> {
  const secrets = Object.freeze([...(options.secrets ?? [])]);
  let probes: readonly HealthProbe[];
  let maxAttempts: number;
  let retryDelayMs: number;
  let requestTimeoutMs: number;
  try {
    probes = selectedProbes(options);
    maxAttempts = positiveInteger(
      options.maxAttempts ?? DEFAULT_MAX_ATTEMPTS,
      "maxAttempts",
      DEFAULT_MAX_ATTEMPTS,
    );
    retryDelayMs = positiveInteger(
      options.retryDelayMs ?? DEFAULT_RETRY_DELAY_MS,
      "retryDelayMs",
      60_000,
      true,
    );
    requestTimeoutMs = positiveInteger(
      options.requestTimeoutMs ?? DEFAULT_REQUEST_TIMEOUT_MS,
      "requestTimeoutMs",
      30_000,
    );
    for (const probe of probes) healthLogArtifactPath(options.rawDir, probe.probeId);
  } catch (error) {
    return healthFailure(
      `健康检查配置无效：${safeCheckText(error, secrets)}。`,
      ["修复环境、Scope、端口、健康路径或重试参数后重新运行验证。"],
      [],
    );
  }

  if (options.dryRun === true) {
    return createCheckResult({
      checkId: "health",
      title: "验证应用健康状态",
      status: "SKIPPED",
      blocking: true,
      summary: "dry-run 模式仅展示健康探测计划，未发起 HTTP 请求。",
      nextActions: [
        ...probes.flatMap((probe) => probe.urls.map((url) => `计划探测：${url}`)),
        "移除 dry-run 参数后执行健康验证。",
      ],
      artifacts: [],
    });
  }

  const httpClient = options.httpClient ?? createFetchHealthHttpClient();
  const sleep = options.sleep ?? ((milliseconds) =>
    new Promise((resolve) => setTimeout(resolve, milliseconds)));
  const artifacts: string[] = [];
  const failures: string[] = [];
  const logWriter = options.logWriter ?? writeHealthCheckLog;

  for (const probe of probes) {
    const outcome = await runProbe(
      probe,
      httpClient,
      sleep,
      maxAttempts,
      retryDelayMs,
      requestTimeoutMs,
      secrets,
    );
    try {
      const expectedArtifact = healthLogArtifactPath(options.rawDir, probe.probeId);
      const artifact = await logWriter({
        repoRoot: options.repoRoot,
        rawDir: options.rawDir,
        probeId: probe.probeId,
        attempts: Object.freeze([...outcome.attempts]),
      });
      if (artifact !== expectedArtifact) throw new Error("artifact 路径不符合固定计划");
      artifacts.push(artifact);
    } catch (error) {
      failures.push(`${probe.subject}健康日志失败：${safeCheckText(error, secrets)}`);
      continue;
    }
    if (!outcome.success) failures.push(`${probe.subject}健康失败：${outcome.reason}`);
  }

  if (failures.length > 0) {
    return healthFailure(
      `健康检查失败。根因：${failures.join("；")}。`,
      [
        "安全重试：确认目标服务已启动、端口正确且响应稳定后，可重新运行一次。",
        `停止条件：最多 ${maxAttempts} 次探测后仍失败，或日志无法独占写入时停止重试。`,
      ],
      artifacts,
    );
  }
  return createCheckResult({
    checkId: "health",
    title: "验证应用健康状态",
    status: "PASS",
    blocking: true,
    summary: "所选应用健康探测均已通过。",
    nextActions: [],
    artifacts,
  });
}
