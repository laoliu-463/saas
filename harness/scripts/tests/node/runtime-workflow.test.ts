import { describe, expect, it, vi } from "vitest";

import { runBusinessCheck } from "../../lib/node/checks/business.js";
import { createCheckResult, type CheckResult, type CheckStatus } from "../../lib/node/core/result.js";
import type { WorkflowCheckExecutor } from "../../lib/node/core/workflow.js";
import { runVerifyWorkflow } from "../../lib/node/workflows/verify.js";

function result(checkId: string, status: CheckStatus): CheckResult {
  return createCheckResult({
    checkId,
    title: `验证节点${checkId}`,
    status,
    blocking: true,
    summary: `节点 ${checkId} 返回 ${status}。`,
    nextActions: [],
    artifacts: [],
  });
}

describe("运行时检查工作流", () => {
  it("构建通过后严格按 docker、health、business 依赖顺序执行", async () => {
    const calls: string[] = [];
    const executor: WorkflowCheckExecutor = vi.fn(async ({ node }) => {
      calls.push(node.id);
      return result(node.id, "PASS");
    });

    const run = await runVerifyWorkflow({ scope: "full", executor });

    expect(run.status).toBe("PASS");
    expect(calls).toEqual(["backend", "frontend", "docker", "health", "business"]);
  });

  it("Docker FAIL 时 health 与 business 保留 BLOCKED 且不调用执行器", async () => {
    const calls: string[] = [];
    const executor: WorkflowCheckExecutor = vi.fn(async ({ node }) => {
      calls.push(node.id);
      return result(node.id, node.id === "docker" ? "FAIL" : "PASS");
    });

    const run = await runVerifyWorkflow({ scope: "backend", executor });

    expect(run.status).toBe("FAIL");
    expect(calls).toEqual(["backend", "docker"]);
    expect(run.checks.find(({ checkId }) => checkId === "health")?.status).toBe("BLOCKED");
    expect(run.checks.find(({ checkId }) => checkId === "business")?.status).toBe("BLOCKED");
  });

  it("健康 FAIL 时业务保留 BLOCKED", async () => {
    const calls: string[] = [];
    const executor: WorkflowCheckExecutor = vi.fn(async ({ node }) => {
      calls.push(node.id);
      return result(node.id, node.id === "health" ? "FAIL" : "PASS");
    });

    const run = await runVerifyWorkflow({ scope: "frontend", executor });

    expect(run.status).toBe("FAIL");
    expect(calls).toEqual(["frontend", "docker", "health"]);
    expect(run.checks.find(({ checkId }) => checkId === "business")?.status).toBe("BLOCKED");
  });

  it("业务显式跳过使整次运行只能 PARTIAL", async () => {
    const executor: WorkflowCheckExecutor = async ({ node }) => node.id === "business"
      ? runBusinessCheck({
        environment: "real-pre",
        repoRoot: "D:/repo",
        rawDir: "runtime/qa/out/run-task9-workflow-skip",
        skipBusinessValidation: true,
      })
      : result(node.id, "PASS");

    const run = await runVerifyWorkflow({ scope: "backend", executor });

    expect(run.status).toBe("PARTIAL");
    expect(run.checks.find(({ checkId }) => checkId === "business"))
      .toMatchObject({ status: "SKIPPED", blocking: true });
  });
});
