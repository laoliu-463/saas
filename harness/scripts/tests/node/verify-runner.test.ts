import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { afterEach, describe, expect, it, vi } from "vitest";

import { createCheckResult, createRunResult, type CheckStatus } from "../../lib/node/core/result.js";
import { createRunContext } from "../../lib/node/core/run-context.js";
import {
  loadEnvironmentSecrets,
  runNodeVerify,
  type NodeVerifyDependencies,
} from "../../lib/node/workflows/run-verify.js";

const roots: string[] = [];

function makeRoot(): string {
  const root = mkdtempSync(join(tmpdir(), "harness-verify-runner-"));
  roots.push(root);
  return root;
}

function result(checkId: string, status: CheckStatus = "PASS") {
  return createCheckResult({
    checkId,
    title: `验证${checkId}检查`,
    status,
    blocking: true,
    summary: `${checkId} 返回 ${status}。`,
    nextActions: status === "PASS" ? [] : ["修复后重试。"],
    artifacts: [],
  });
}

function runResult(checkId: string, status: CheckStatus = "PASS") {
  return createRunResult([result(checkId, status)]);
}

function dependencies(
  inspectStatus: CheckStatus = "PASS",
): { dependencies: Partial<NodeVerifyDependencies>; calls: string[] } {
  const calls: string[] = [];
  const evidencePaths = {
    rawJson: "runtime/qa/out/run-task10/run.json",
    stableJson: "runtime/qa/out/latest-task10.json",
    stableMarkdown: "runtime/qa/out/latest-task10.md",
  };
  return {
    calls,
    dependencies: {
      runInspect: vi.fn(async () => runResult("inspect.fixture", inspectStatus)),
      createRunContext: vi.fn((input) => ({
        ...input,
        runId: "run-task10",
        startedAt: "2026-07-18T00:00:00.000Z",
        startedAtShanghai: "2026-07-18 08:00:00 Asia/Shanghai",
        rawDir: "runtime/qa/out/run-task10",
        stableJsonPath: evidencePaths.stableJson,
        stableMarkdownPath: evidencePaths.stableMarkdown,
        git: {
          branch: "codex/task10",
          headSha: "a".repeat(40),
          clean: true,
          changedFiles: [],
          patchFingerprint: null,
          identity: { kind: "COMMIT", commitSha: "a".repeat(40) },
        },
      })),
      loadEnvironmentSecrets: vi.fn(() => ["fixture-secret"]),
      runBackendCheck: vi.fn(async () => {
        calls.push("backend");
        return result("backend");
      }),
      runFrontendCheck: vi.fn(async () => {
        calls.push("frontend");
        return result("frontend");
      }),
      runDockerCheck: vi.fn(async () => {
        calls.push("docker");
        return result("docker");
      }),
      runHealthCheck: vi.fn(async () => {
        calls.push("health");
        return result("health");
      }),
      runBusinessCheck: vi.fn(async (options) => {
        calls.push("business");
        return result("business", options.skipBusinessValidation === true ? "SKIPPED" : "PASS");
      }),
      createEvidenceReport: vi.fn((_context, run) => ({
        result: run,
        evidencePaths,
      }) as never),
      writeEvidence: vi.fn(() => ({ ...evidencePaths, cleanupWarnings: [] })),
    },
  };
}

afterEach(() => {
  vi.restoreAllMocks();
  while (roots.length > 0) rmSync(roots.pop()!, { recursive: true, force: true });
});

describe("Node verify 唯一执行器", () => {
  it.each([
    ["backend", ["backend", "docker", "health", "business"]],
    ["frontend", ["frontend", "docker", "health", "business"]],
    ["full", ["backend", "frontend", "docker", "health", "business"]],
  ] as const)("scope=%s 只执行对应检查链", async (scope, expectedCalls) => {
    const fixture = dependencies();

    const outcome = await runNodeVerify({
      repoRoot: makeRoot(),
      environment: "test",
      scope,
      reportKey: "task10",
    }, fixture.dependencies);

    expect(fixture.calls).toEqual(expectedCalls);
    expect(outcome.result.status).toBe("PASS");
    expect(outcome.result.checks[0]?.checkId).toBe("inspect.fixture");
  });

  it.each(["FAIL", "BLOCKED", "WARN"] as const)(
    "inspect=%s 时不调用任何后续检查但仍写可信证据",
    async (status) => {
      const fixture = dependencies(status);

      const outcome = await runNodeVerify({
        repoRoot: makeRoot(),
        environment: "real-pre",
        scope: "full",
        reportKey: "task10",
      }, fixture.dependencies);

      expect(fixture.calls).toEqual([]);
      expect(outcome.result.status).toBe(status === "FAIL" ? "FAIL" : status === "BLOCKED" ? "BLOCKED" : "PARTIAL");
      expect(fixture.dependencies.createEvidenceReport).toHaveBeenCalledOnce();
      expect(fixture.dependencies.writeEvidence).toHaveBeenCalledOnce();
    },
  );

  it("dry-run 只执行 inspect，五个检查均为计划性 SKIPPED 且无检查日志副作用", async () => {
    const fixture = dependencies();

    const outcome = await runNodeVerify({
      repoRoot: makeRoot(),
      environment: "real-pre",
      scope: "full",
      reportKey: "task10",
      dryRun: true,
    }, fixture.dependencies);

    expect(fixture.calls).toEqual([]);
    expect(outcome.result.status).toBe("PARTIAL");
    expect(outcome.result.checks.slice(1).map(({ checkId, status }) => [checkId, status]))
      .toEqual([
        ["backend", "SKIPPED"],
        ["frontend", "SKIPPED"],
        ["docker", "SKIPPED"],
        ["health", "SKIPPED"],
        ["business", "SKIPPED"],
        ["evidence.git-identity", "PASS"],
      ]);
  });

  it("显式跳过业务验证使最终状态保持 PARTIAL", async () => {
    const fixture = dependencies();

    const outcome = await runNodeVerify({
      repoRoot: makeRoot(),
      environment: "test",
      scope: "backend",
      reportKey: "task10",
      skipBusinessValidation: true,
    }, fixture.dependencies);

    expect(outcome.result.status).toBe("PARTIAL");
    expect(outcome.result.checks.find(({ checkId }) => checkId === "business")?.status)
      .toBe("SKIPPED");
  });

  it("直接 Node 不采集 Git，执行检查后只能形成 PARTIAL", async () => {
    const fixture = dependencies();
    const withoutInjectedGit = {
      ...fixture.dependencies,
      createRunContext,
    };

    const outcome = await runNodeVerify({
      repoRoot: makeRoot(),
      environment: "test",
      scope: "backend",
      reportKey: "task10",
    }, withoutInjectedGit);

    expect(fixture.calls).toEqual(["backend", "docker", "health", "business"]);
    expect(outcome.context.git.identity.kind).toBe("UNAVAILABLE");
    expect(outcome.result.status).toBe("PARTIAL");
    expect(outcome.result.checks.find(({ checkId }) => checkId === "evidence.git-identity"))
      .toMatchObject({ status: "WARN", blocking: true });
  });

  it("同一个 RunResult 同时进入 EvidenceReport、稳定 JSON 与 Markdown 写入", async () => {
    const fixture = dependencies();

    const outcome = await runNodeVerify({
      repoRoot: makeRoot(),
      environment: "test",
      scope: "backend",
      reportKey: "task10",
    }, fixture.dependencies);

    const reportCall = vi.mocked(fixture.dependencies.createEvidenceReport!).mock.calls[0];
    const writeCall = vi.mocked(fixture.dependencies.writeEvidence!).mock.calls[0];
    expect(reportCall?.[1]).toBe(outcome.result);
    expect((writeCall?.[0] as { result: unknown }).result).toBe(outcome.result);
    expect(outcome.evidence.stableJson).toMatch(/latest-task10\.json$/u);
    expect(outcome.evidence.stableMarkdown).toMatch(/latest-task10\.md$/u);
  });

  it("证据写入失败直接抛出且不返回伪 PASS", async () => {
    const fixture = dependencies();
    const failingDependencies = {
      ...fixture.dependencies,
      writeEvidence: vi.fn(() => {
        throw new Error("证据目录不可写");
      }),
    };

    await expect(runNodeVerify({
      repoRoot: makeRoot(),
      environment: "test",
      scope: "backend",
      reportKey: "task10",
    }, failingDependencies)).rejects.toThrow(/证据目录不可写/u);
  });

  it("只提取环境合同 secretKeys 对应的非空值", () => {
    const content = [
      "DB_PASSWORD=db-secret",
      "JWT_SECRET=jwt-secret",
      "ADMIN_PASSWORD=not-a-contract-secret",
      "DOUYIN_CLIENT_SECRET=",
      "UNRELATED=value",
    ].join("\n");

    const secrets = loadEnvironmentSecrets({
      repoRoot: makeRoot(),
      environment: "real-pre",
      readFile: vi.fn(() => content),
    });

    expect(secrets).toContain("db-secret");
    expect(secrets).toContain("jwt-secret");
    expect(secrets).not.toContain("not-a-contract-secret");
    expect(secrets).not.toContain("value");
    expect(secrets).not.toContain("");
  });
});
