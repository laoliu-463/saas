import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { afterEach, describe, expect, it, vi } from "vitest";

import {
  createFetchHealthHttpClient,
  runHealthCheck,
} from "../../lib/node/checks/health.js";

const temporaryDirectories: string[] = [];

function temporaryRepository(): string {
  const root = mkdtempSync(join(tmpdir(), "harness-health-check-"));
  temporaryDirectories.push(root);
  return root;
}

afterEach(() => {
  for (const directory of temporaryDirectories.splice(0)) {
    rmSync(directory, { recursive: true, force: true });
  }
});

describe("健康检查", () => {
  it("backend scope 使用端口覆盖并要求 2xx JSON status=UP", async () => {
    const httpClient = vi.fn(async () => ({
      statusCode: 200,
      body: '{"status":"UP"}',
    }));
    const sleep = vi.fn(async () => undefined);
    const logWriter = vi.fn(async (input: { rawDir: string; probeId: string }) =>
      `${input.rawDir}/${input.probeId}.log`
    );

    const result = await runHealthCheck({
      environment: "real-pre",
      scope: "backend",
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task9-health",
      envValues: { BACKEND_HOST_PORT: "18081" },
      httpClient,
      sleep,
      logWriter,
      maxAttempts: 2,
      retryDelayMs: 1,
      requestTimeoutMs: 250,
    });

    expect(httpClient).toHaveBeenCalledWith({
      url: "http://127.0.0.1:18081/api/system/health",
      timeoutMs: 250,
    });
    expect(httpClient).toHaveBeenCalledTimes(1);
    expect(sleep).not.toHaveBeenCalled();
    expect(result).toMatchObject({
      status: "PASS",
      blocking: true,
      artifacts: ["runtime/qa/out/run-task9-health/health-backend.log"],
    });
  });

  it("默认健康日志写入器在固定 rawDir 独占写入脱敏尝试记录", async () => {
    const root = temporaryRepository();
    const secret = "health-secret-639";

    const result = await runHealthCheck({
      environment: "test",
      scope: "backend",
      repoRoot: root,
      rawDir: "runtime/qa/out/run-task9-health-default",
      httpClient: vi.fn(async () => ({
        statusCode: 200,
        body: `{"status":"UP","token":"${secret}"}`,
      })),
      sleep: vi.fn(async () => undefined),
      maxAttempts: 1,
      retryDelayMs: 0,
      requestTimeoutMs: 250,
      secrets: [secret],
    });

    expect(result.status).toBe("PASS");
    expect(result.artifacts).toEqual([
      "runtime/qa/out/run-task9-health-default/health-backend.log",
    ]);
    const content = readFileSync(
      join(root, "runtime", "qa", "out", "run-task9-health-default", "health-backend.log"),
      "utf8",
    );
    expect(content).not.toContain(secret);
  });

  it("默认 HTTP 适配器设置单请求超时、禁止自动跳转并限制响应体", async () => {
    const fetchImpl = vi.fn(async (_url: string | URL, _init?: RequestInit) =>
      new Response('{"status":"UP"}', { status: 200 })
    );
    const client = createFetchHealthHttpClient(fetchImpl);

    await expect(client({
      url: "http://127.0.0.1:8081/api/system/health",
      timeoutMs: 321,
    })).resolves.toEqual({ statusCode: 200, body: '{"status":"UP"}' });
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    const init = fetchImpl.mock.calls[0]?.[1];
    expect(init?.redirect).toBe("manual");
    expect(init?.signal).toBeInstanceOf(AbortSignal);

    const oversizedClient = createFetchHealthHttpClient(vi.fn(async () =>
      new Response("x".repeat(64 * 1_024 + 1), { status: 200 })
    ));
    await expect(oversizedClient({ url: "http://127.0.0.1:8081/", timeoutMs: 100 }))
      .rejects.toThrow(/64 KiB/u);
  });

  it("frontend scope 按合同候选路径探测并在首个 2xx/3xx 响应停止", async () => {
    const httpClient = vi.fn()
      .mockResolvedValueOnce({ statusCode: 404, body: "missing" })
      .mockResolvedValueOnce({ statusCode: 302, body: "" });
    const logWriter = vi.fn(async (input: { rawDir: string; probeId: string }) =>
      `${input.rawDir}/${input.probeId}.log`
    );

    const result = await runHealthCheck({
      environment: "real-pre",
      scope: "frontend",
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task9-health-frontend",
      envValues: { FRONTEND_HOST_PORT: "13001" },
      httpClient,
      sleep: vi.fn(async () => undefined),
      logWriter,
      maxAttempts: 1,
      retryDelayMs: 0,
      requestTimeoutMs: 333,
    });

    expect(result.status).toBe("PASS");
    expect(httpClient.mock.calls.map(([request]) => request)).toEqual([
      { url: "http://127.0.0.1:13001/healthz", timeoutMs: 333 },
      { url: "http://127.0.0.1:13001/login", timeoutMs: 333 },
    ]);
    expect(result.artifacts).toEqual([
      "runtime/qa/out/run-task9-health-frontend/health-frontend.log",
    ]);
  });

  it("未注入 envValues 时从环境文件读取两个端口键", async () => {
    const root = temporaryRepository();
    writeFileSync(join(root, ".env.real-pre"), [
      "BACKEND_HOST_PORT=28081",
      "FRONTEND_HOST_PORT='23001'",
      "DB_PASSWORD=不得出现在证据中",
    ].join("\n"), "utf8");
    const httpClient = vi.fn(async ({ url }: { url: string }) => url.includes("28081")
      ? { statusCode: 200, body: '{"status":"UP"}' }
      : { statusCode: 200, body: "ok" });
    const logWriter = vi.fn(async (input: { rawDir: string; probeId: string }) =>
      `${input.rawDir}/${input.probeId}.log`
    );

    const result = await runHealthCheck({
      environment: "real-pre",
      scope: "full",
      repoRoot: root,
      rawDir: "runtime/qa/out/run-task9-health-env-file",
      httpClient,
      sleep: vi.fn(async () => undefined),
      logWriter,
      maxAttempts: 1,
      retryDelayMs: 0,
      requestTimeoutMs: 100,
    });

    expect(result.status).toBe("PASS");
    expect(httpClient.mock.calls.map(([request]) => request.url)).toEqual([
      "http://127.0.0.1:28081/api/system/health",
      "http://127.0.0.1:23001/healthz",
    ]);
    expect(JSON.stringify(logWriter.mock.calls)).not.toContain("不得出现在证据中");
  });

  it("健康失败按上限重试并把单请求超时传给每次调用", async () => {
    const secret = "health-retry-secret-741";
    const httpClient = vi.fn()
      .mockResolvedValueOnce({ statusCode: 200, body: '{"status":"DOWN"}' })
      .mockRejectedValueOnce(new Error(`连接失败 ${secret}`))
      .mockResolvedValueOnce({ statusCode: 200, body: '{"status":"UP"}' });
    const sleep = vi.fn(async () => undefined);
    let serializedAttempts = "";

    const result = await runHealthCheck({
      environment: "test",
      scope: "backend",
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task9-health-retry",
      httpClient,
      sleep,
      logWriter: vi.fn(async (input) => {
        serializedAttempts = JSON.stringify(input.attempts);
        return `${input.rawDir}/${input.probeId}.log`;
      }),
      maxAttempts: 3,
      retryDelayMs: 7,
      requestTimeoutMs: 444,
      secrets: [secret],
    });

    expect(result.status).toBe("PASS");
    expect(httpClient).toHaveBeenCalledTimes(3);
    expect(httpClient.mock.calls.every(([request]) => request.timeoutMs === 444)).toBe(true);
    expect(sleep).toHaveBeenCalledTimes(2);
    expect(sleep).toHaveBeenNthCalledWith(1, 7);
    expect(serializedAttempts).toContain("[REDACTED]");
    expect(serializedAttempts).not.toContain(secret);
  });

  it("full scope 即使后端失败也继续收集前端证据", async () => {
    const httpClient = vi.fn(async ({ url }: { url: string }) => url.includes(":8081/")
      ? { statusCode: 503, body: '{"status":"DOWN"}' }
      : { statusCode: 204, body: "" });
    const logWriter = vi.fn(async (input: { rawDir: string; probeId: string }) =>
      `${input.rawDir}/${input.probeId}.log`
    );

    const result = await runHealthCheck({
      environment: "real-pre",
      scope: "full",
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task9-health-full",
      envValues: {},
      httpClient,
      sleep: vi.fn(async () => undefined),
      logWriter,
      maxAttempts: 2,
      retryDelayMs: 0,
      requestTimeoutMs: 100,
    });

    expect(result.status).toBe("FAIL");
    expect(result.summary).toContain("后端健康失败");
    expect(httpClient.mock.calls.some(([request]) => request.url.includes(":3001/healthz")))
      .toBe(true);
    expect(result.artifacts).toEqual([
      "runtime/qa/out/run-task9-health-full/health-backend.log",
      "runtime/qa/out/run-task9-health-full/health-frontend.log",
    ]);
  });

  it("dry-run 只返回探测计划，不请求、等待或写日志", async () => {
    const httpClient = vi.fn(async () => ({ statusCode: 200, body: "ok" }));
    const sleep = vi.fn(async () => undefined);
    const logWriter = vi.fn(async () => "不应写入.log");

    const result = await runHealthCheck({
      environment: "test",
      scope: "full",
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task9-health-dry",
      envValues: {},
      httpClient,
      sleep,
      logWriter,
      dryRun: true,
    });

    expect(result).toMatchObject({ status: "SKIPPED", blocking: true, artifacts: [] });
    expect(result.nextActions.join("\n")).toContain("http://127.0.0.1:8080/api/system/health");
    expect(result.nextActions.join("\n")).toContain("http://127.0.0.1:3000/healthz");
    expect(httpClient).not.toHaveBeenCalled();
    expect(sleep).not.toHaveBeenCalled();
    expect(logWriter).not.toHaveBeenCalled();
  });

  it("畸形 HTTP 响应与伪造日志路径都不能制造 PASS", async () => {
    const malformed = await runHealthCheck({
      environment: "test",
      scope: "backend",
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task9-health-malformed",
      envValues: {},
      httpClient: vi.fn(async () => ({ statusCode: 200, body: 42 })),
      sleep: vi.fn(async () => undefined),
      logWriter: vi.fn(async (input) => `${input.rawDir}/${input.probeId}.log`),
      maxAttempts: 1,
      retryDelayMs: 0,
      requestTimeoutMs: 100,
    });
    const forged = await runHealthCheck({
      environment: "test",
      scope: "frontend",
      repoRoot: "D:/repo",
      rawDir: "runtime/qa/out/run-task9-health-forged",
      envValues: {},
      httpClient: vi.fn(async () => ({ statusCode: 200, body: "ok" })),
      sleep: vi.fn(async () => undefined),
      logWriter: vi.fn(async () => "../../outside.log"),
      maxAttempts: 1,
      retryDelayMs: 0,
      requestTimeoutMs: 100,
    });

    expect(malformed.status).toBe("FAIL");
    expect(forged.status).toBe("FAIL");
    expect(forged.artifacts).toEqual([]);
  });
});
