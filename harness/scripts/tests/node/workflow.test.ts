import { describe, expect, it, vi } from "vitest";

import {
  executeWorkflow,
  sortWorkflowNodes,
  type WorkflowNode,
} from "../../lib/node/core/workflow.js";
import {
  createCheckResult,
  type CheckResult,
  type CheckStatus,
} from "../../lib/node/core/result.js";

function node(
  id: string,
  dependencies: readonly string[] = [],
): WorkflowNode {
  return {
    id,
    title: `检查节点${id}`,
    blocking: true,
    dependencies,
  };
}

function result(id: string, status: CheckStatus = "PASS"): CheckResult {
  return createCheckResult({
    checkId: id,
    title: `执行器返回${id}`,
    status,
    blocking: false,
    summary: `节点${id}返回${status}结果。`,
    nextActions: [],
    artifacts: [`runtime/${id}.log`],
  });
}

describe("验证依赖图", () => {
  it("使用确定拓扑顺序执行每个节点且最多一次", async () => {
    const nodes = [
      node("finish", ["frontend", "backend"]),
      node("frontend"),
      node("backend"),
    ];
    const calls: string[] = [];

    const run = await executeWorkflow(nodes, async ({ node: current }) => {
      calls.push(current.id);
      return result(current.id);
    });

    expect(sortWorkflowNodes(nodes).map((item) => item.id)).toEqual([
      "backend",
      "frontend",
      "finish",
    ]);
    expect(calls).toEqual(["backend", "frontend", "finish"]);
    expect(new Set(calls).size).toBe(calls.length);
    expect(run.checks.map((check) => check.checkId)).toEqual(calls);
    expect(run.status).toBe("PASS");
  });

  it("拒绝重复 id、未知依赖和依赖环", () => {
    expect(() => sortWorkflowNodes([])).toThrowError(/至少需要一个节点/u);
    expect(() => sortWorkflowNodes([node("duplicate"), node("duplicate")])).toThrowError(
      /重复/u,
    );
    expect(() => sortWorkflowNodes([
      node("root"),
      node("duplicate-dependency", ["root", "root"]),
    ])).toThrowError(/重复依赖/u);
    expect(() => sortWorkflowNodes([node("downstream", ["missing"])]))
      .toThrowError(/未知依赖.*missing/u);
    expect(() =>
      sortWorkflowNodes([node("a", ["b"]), node("b", ["a"])]),
    ).toThrowError(/环/u);
  });

  it("在执行前拒绝不符合结果契约的机器 id", () => {
    expect(() => sortWorkflowNodes([node("中文-id")])).toThrowError(
      /机器 id.*小写字母/u,
    );
  });

  it("在执行前拒绝英文标题、非布尔 blocking 与非法依赖结构", () => {
    expect(() => sortWorkflowNodes([{
      id: "english-title",
      title: "English title",
      blocking: true,
      dependencies: [],
    }])).toThrowError(/必须包含中文/u);
    expect(() => sortWorkflowNodes([{
      ...node("bad-blocking"),
      blocking: "true",
    } as unknown as WorkflowNode])).toThrowError(/blocking.*布尔值/u);
    expect(() => sortWorkflowNodes([{
      ...node("bad-dependencies"),
      dependencies: "root",
    } as unknown as WorkflowNode])).toThrowError(/依赖.*数组/u);
  });

  it("根节点失败不阻止独立兄弟执行，并为下游保留 BLOCKED 证据", async () => {
    const executor = vi.fn(async ({ node: current }: { node: WorkflowNode }) =>
      result(current.id, current.id === "backend" ? "FAIL" : "PASS"),
    );

    const run = await executeWorkflow(
      [node("docker", ["backend", "frontend"]), node("frontend"), node("backend")],
      executor,
    );

    expect(executor.mock.calls.map(([context]) => context.node.id)).toEqual([
      "backend",
      "frontend",
    ]);
    expect(run.checks).toHaveLength(3);
    expect(run.checks.find((check) => check.checkId === "frontend")?.status).toBe(
      "PASS",
    );
    const blocked = run.checks.find((check) => check.checkId === "docker");
    expect(blocked).toMatchObject({ status: "BLOCKED", statusLabel: "阻塞" });
    expect(blocked?.summary).toContain("backend");
    expect(blocked?.summary).toMatch(/依赖.*未通过/u);
    expect(run.status).toBe("FAIL");
  });

  it.each(["FAIL", "BLOCKED", "WARN", "SKIPPED", "NOT_COLLECTED"] as const)(
    "依赖返回 %s 时不调用下游执行器",
    async (status) => {
      const executor = vi.fn(
        async ({ node: current }: { node: WorkflowNode }) =>
          result(current.id, current.id === "upstream" ? status : "PASS"),
      );

      const run = await executeWorkflow(
        [node("downstream", ["upstream"]), node("upstream")],
        executor,
      );

      expect(executor).toHaveBeenCalledTimes(1);
      expect(run.checks.find((check) => check.checkId === "downstream")?.status).toBe(
        "BLOCKED",
      );
    },
  );

  it("dry-run 不调用执行器，所有计划节点生成 SKIPPED 且结论不是 PASS", async () => {
    const executor = vi.fn(async ({ node: current }: { node: WorkflowNode }) =>
      result(current.id),
    );

    const run = await executeWorkflow(
      [node("downstream", ["upstream"]), node("upstream")],
      executor,
      { dryRun: true },
    );

    expect(executor).not.toHaveBeenCalled();
    expect(run.checks.map((check) => check.status)).toEqual([
      "SKIPPED",
      "SKIPPED",
    ]);
    expect(run.status).toBe("PARTIAL");
  });

  it("执行器异常转成中文 FAIL 证据，图仍继续执行其他根节点", async () => {
    const executor = vi.fn(async ({ node: current }: { node: WorkflowNode }) => {
      if (current.id === "backend") throw new Error("编译进程异常退出");
      return result(current.id);
    });

    const run = await executeWorkflow(
      [node("frontend"), node("backend")],
      executor,
    );

    const failure = run.checks.find((check) => check.checkId === "backend");
    expect(failure).toMatchObject({ status: "FAIL", blocking: true });
    expect(failure?.summary).toContain("根因");
    expect(failure?.summary).toContain("编译进程异常退出");
    expect(failure?.nextActions.join(" ")).toMatch(/[\u3400-\u9fff]/u);
    expect(run.checks.find((check) => check.checkId === "frontend")?.status).toBe(
      "PASS",
    );
    expect(run.status).toBe("FAIL");
  });

  it("执行器只能看到深度冻结快照，恶意修改不会破坏内部节点与依赖证据", async () => {
    const run = await executeWorkflow(
      [node("downstream", ["root"]), node("root")],
      async ({ node: current, dependencyResults }) => {
        expect(Object.isFrozen(current)).toBe(true);
        expect(Object.isFrozen(current.dependencies)).toBe(true);
        try {
          (current as { id: string }).id = "hijacked";
        } catch {
          // 严格模式下冻结对象会拒绝赋值。
        }

        const dependency = dependencyResults.get("root");
        if (dependency !== undefined) {
          expect(Object.isFrozen(dependency)).toBe(true);
          expect(Object.isFrozen(dependency.nextActions)).toBe(true);
          try {
            (dependency as { status: CheckStatus }).status = "FAIL";
          } catch {
            // 冻结依赖快照会拒绝赋值。
          }
          expect(() =>
            (dependencyResults as unknown as Map<string, CheckResult>)
              .set("injected", result("injected")),
          ).toThrow();
        }
        return result(current.id);
      },
    );

    expect(run.status).toBe("PASS");
    expect(run.checks.map(({ checkId, status }) => [checkId, status])).toEqual([
      ["root", "PASS"],
      ["downstream", "PASS"],
    ]);
  });

  it("异常摘要永不二次抛错，并脱敏、移除控制字符和限制长度", async () => {
    const unsafe = await executeWorkflow([node("unsafe-error")], async () => {
      throw new Error(`password=workflow-secret\u001b[31m${"x".repeat(3_000)}`);
    });
    const hostile = await executeWorkflow([node("hostile-error")], async () => {
      throw { toString: () => { throw new Error("二次异常"); } };
    });

    const unsafeSummary = unsafe.checks[0]?.summary ?? "";
    expect(unsafe.status).toBe("FAIL");
    expect(unsafeSummary).toContain("[REDACTED]");
    expect(unsafeSummary).not.toContain("workflow-secret");
    expect(unsafeSummary).not.toMatch(/[\u0000-\u001f\u007f-\u009f]/u);
    expect(unsafeSummary.length).toBeLessThan(600);
    expect(hostile.status).toBe("FAIL");
    expect(hostile.checks[0]?.summary).toContain("异常详情无法安全读取");
  });

  it("执行器返回非法对象时转成 FAIL，而不是让整个工作流拒绝", async () => {
    const run = await executeWorkflow([node("invalid-result")], async () =>
      undefined as unknown as CheckResult
    );

    expect(run.status).toBe("FAIL");
    expect(run.checks[0]).toMatchObject({ checkId: "invalid-result", status: "FAIL" });
  });

  it("执行器返回字符串数组字段等畸形结构时转成 FAIL", async () => {
    const malformed = {
      ...result("malformed-result"),
      nextActions: "不是数组",
      artifacts: "runtime/fake.log",
    } as unknown as CheckResult;
    const run = await executeWorkflow([node("malformed-result")], async () => malformed);

    expect(run.status).toBe("FAIL");
    expect(run.checks[0]).toMatchObject({ checkId: "malformed-result", status: "FAIL" });
    expect(run.checks[0]?.summary).toContain("不符合契约");
  });

  it("依赖只读门面的原型被冻结，篡改尝试不影响后续节点", async () => {
    const run = await executeWorkflow(
      [node("tail", ["middle"]), node("middle", ["root"]), node("root")],
      async ({ node: current, dependencyResults }) => {
        if (current.id === "middle") {
          const prototype = Object.getPrototypeOf(dependencyResults) as object;
          expect(Object.isFrozen(prototype)).toBe(true);
          expect(() => Object.defineProperty(prototype, "values", {
            value: () => { throw new Error("原型已被篡改"); },
          })).toThrow();
        }
        return result(current.id);
      },
    );

    expect(run.status).toBe("PASS");
    expect(run.checks.map(({ checkId }) => checkId)).toEqual(["root", "middle", "tail"]);
  });

  it("工作流节点定义覆盖执行器返回的 id、标题和阻断属性", async () => {
    const run = await executeWorkflow([node("trusted")], async () =>
      result("wrong-id", "PASS"),
    );

    expect(run.checks[0]).toMatchObject({
      checkId: "trusted",
      title: "检查节点trusted",
      blocking: true,
      artifacts: ["runtime/wrong-id.log"],
    });
  });
});
