import {
  executeWorkflow,
  type WorkflowCheckExecutor,
  type WorkflowNode,
} from "../core/workflow.js";
import type { RunResult } from "../core/result.js";

export type VerifyScope = "backend" | "frontend" | "full";
export type VerifyNodeId =
  | "backend"
  | "frontend"
  | "docker"
  | "health"
  | "business";

export interface RunVerifyWorkflowOptions {
  readonly scope: VerifyScope;
  readonly executor: WorkflowCheckExecutor;
  readonly dryRun?: boolean;
}

const NODE_TITLES: Record<VerifyNodeId, string> = {
  backend: "验证后端构建",
  frontend: "验证前端构建",
  docker: "验证 Docker 运行环境",
  health: "验证应用健康状态",
  business: "验证业务链路",
};

function verifyNode(
  id: VerifyNodeId,
  dependencies: readonly VerifyNodeId[],
): WorkflowNode {
  return {
    id,
    title: NODE_TITLES[id],
    blocking: true,
    dependencies,
  };
}

export function createVerifyWorkflow(scope: VerifyScope): readonly WorkflowNode[] {
  const buildNodes: WorkflowNode[] = [];
  const buildDependencies: VerifyNodeId[] = [];

  if (scope === "backend" || scope === "full") {
    buildNodes.push(verifyNode("backend", []));
    buildDependencies.push("backend");
  }
  if (scope === "frontend" || scope === "full") {
    buildNodes.push(verifyNode("frontend", []));
    buildDependencies.push("frontend");
  }
  if (buildNodes.length === 0) {
    throw new Error(`不支持的验证范围：${String(scope)}。`);
  }

  return [
    ...buildNodes,
    verifyNode("docker", buildDependencies),
    verifyNode("health", ["docker"]),
    verifyNode("business", ["health"]),
  ];
}

export function runVerifyWorkflow(
  options: RunVerifyWorkflowOptions,
): Promise<RunResult> {
  return executeWorkflow(
    createVerifyWorkflow(options.scope),
    options.executor,
    { dryRun: options.dryRun ?? false },
  );
}
