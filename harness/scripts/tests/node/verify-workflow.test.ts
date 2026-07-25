import { describe, expect, it, vi } from "vitest";

import { createCheckResult, type CheckStatus } from "../../lib/node/core/result.js";
import type { WorkflowCheckExecutor } from "../../lib/node/core/workflow.js";
import {
  createVerifyWorkflow,
  runVerifyWorkflow,
  type VerifyNodeId,
  type VerifyScope,
} from "../../lib/node/workflows/verify.js";

function executorWith(
  statuses: Partial<Record<VerifyNodeId, CheckStatus>> = {},
): WorkflowCheckExecutor {
  return vi.fn(async ({ node }) =>
    createCheckResult({
      checkId: node.id,
      title: node.title,
      status: statuses[node.id as VerifyNodeId] ?? "PASS",
      blocking: true,
      summary: `节点${node.id}执行完成。`,
      nextActions: [],
      artifacts: [],
    }),
  );
}

describe("verify 工作流选择", () => {
  it.each([
    ["backend", ["backend"]],
    ["frontend", ["frontend"]],
    ["full", ["backend", "frontend", "docker", "health", "business"]],
  ] as const)("scope=%s 只选择确定节点", (scope, expectedIds) => {
    expect(createVerifyWorkflow(scope).map((node) => node.id)).toEqual(expectedIds);
  });

  it.each(["backend", "frontend", "full"] as const)(
    "scope=%s 运行时每个已选节点最多执行一次",
    async (scope: VerifyScope) => {
      const executor = executorWith();
      const run = await runVerifyWorkflow({ scope, executor });
      const calledIds = vi.mocked(executor).mock.calls.map(([context]) => context.node.id);

      expect(calledIds).toEqual(createVerifyWorkflow(scope).map((node) => node.id));
      expect(new Set(calledIds).size).toBe(calledIds.length);
      expect(run.status).toBe("PASS");
    },
  );
});

describe("verify 工作流依赖与聚合", () => {
  it.each(["backend", "frontend"] as const)(
    "full 中 %s 失败不阻止另一构建分支，且阻塞 docker 及下游",
    async (failedNode) => {
      const siblingNode = failedNode === "backend" ? "frontend" : "backend";
      const executor = executorWith({ [failedNode]: "FAIL" });

      const run = await runVerifyWorkflow({ scope: "full", executor });
      const calledIds = vi.mocked(executor).mock.calls.map(([context]) => context.node.id);

      expect(calledIds).toEqual(["backend", "frontend"]);
      expect(run.checks.find(({ checkId }) => checkId === failedNode)?.status).toBe("FAIL");
      expect(run.checks.find(({ checkId }) => checkId === siblingNode)?.status).toBe("PASS");
      expect(run.checks
        .filter(({ checkId }) => ["docker", "health", "business"].includes(checkId))
        .map(({ checkId, status }) => [checkId, status])).toEqual([
          ["docker", "BLOCKED"],
          ["health", "BLOCKED"],
          ["business", "BLOCKED"],
        ]);
      expect(run.checks.find(({ checkId }) => checkId === "docker")?.summary)
        .toContain(failedNode);
      expect(run.status).toBe("FAIL");
    },
  );

  it("docker 依赖 full scope 的全部 build 节点", () => {
    expect(createVerifyWorkflow("full").find((node) => node.id === "docker"))
      .toMatchObject({ dependencies: ["backend", "frontend"] });
  });

  it("isolated scope 不包含 Docker、health 或 business 节点", () => {
    expect(createVerifyWorkflow("backend").map((node) => node.id))
      .toEqual(["backend"]);
    expect(createVerifyWorkflow("frontend").map((node) => node.id))
      .toEqual(["frontend"]);
  });

  it("health 依赖 docker，business 依赖 health", () => {
    const workflow = createVerifyWorkflow("full");

    expect(workflow.find((node) => node.id === "health"))
      .toMatchObject({ dependencies: ["docker"] });
    expect(workflow.find((node) => node.id === "business"))
      .toMatchObject({ dependencies: ["health"] });
  });

  it("BLOCKED 聚合优先于上游的 PARTIAL 语义", async () => {
    const run = await runVerifyWorkflow({
      scope: "full",
      executor: executorWith({ backend: "WARN" }),
    });

    expect(run.checks.find((check) => check.checkId === "backend")?.status).toBe(
      "WARN",
    );
    expect(run.checks.find((check) => check.checkId === "docker")?.status).toBe(
      "BLOCKED",
    );
    expect(run.status).toBe("BLOCKED");
  });

  it("dry-run 不触发任何注入执行器并返回 PARTIAL", async () => {
    const executor = executorWith();

    const run = await runVerifyWorkflow({
      scope: "full",
      executor,
      dryRun: true,
    });

    expect(executor).not.toHaveBeenCalled();
    expect(run.checks).toHaveLength(5);
    expect(run.checks.every((check) => check.status === "SKIPPED")).toBe(true);
    expect(run.status).toBe("PARTIAL");
  });
});
