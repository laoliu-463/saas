import {
  createCheckResult,
  createRunResult,
  validateCheckResult,
  type CheckResult,
  type RunResult,
} from "./result.js";
import { redactEvidenceText } from "./redact.js";

export interface WorkflowNode {
  readonly id: string;
  readonly title: string;
  readonly blocking: boolean;
  readonly dependencies: readonly string[];
}

export interface WorkflowExecutionContext {
  readonly node: WorkflowNode;
  readonly dependencyResults: ReadonlyMap<string, CheckResult>;
}

export type WorkflowCheckExecutor = (
  context: WorkflowExecutionContext,
) => CheckResult | Promise<CheckResult>;

export interface WorkflowExecutionOptions {
  readonly dryRun?: boolean;
}

const WORKFLOW_ID_PATTERN = /^[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*$/u;
const CHINESE_TEXT_PATTERN = /[\u3400-\u9fff]/u;
const MAX_ERROR_INPUT_LENGTH = 2_000;
const MAX_ERROR_SUMMARY_LENGTH = 500;

function compareNodeIds(left: WorkflowNode, right: WorkflowNode): number {
  if (left.id === right.id) return 0;
  return left.id < right.id ? -1 : 1;
}

function validateNode(node: WorkflowNode): void {
  if (typeof node !== "object" || node === null || Array.isArray(node)) {
    throw new Error("工作流节点必须是对象。");
  }
  if (typeof node.id !== "string" || !WORKFLOW_ID_PATTERN.test(node.id)) {
    throw new Error(
      `工作流机器 id ${node.id || "（空）"} 非法，必须以小写字母开头并仅包含小写字母、数字、点或连字符。`,
    );
  }
  if (typeof node.title !== "string" || node.title.trim().length === 0 ||
    !CHINESE_TEXT_PATTERN.test(node.title)) {
    throw new Error(`工作流节点 ${node.id} 的标题不能为空且必须包含中文。`);
  }
  if (typeof node.blocking !== "boolean") {
    throw new Error(`工作流节点 ${node.id} 的 blocking 必须是布尔值。`);
  }
  if (!Array.isArray(node.dependencies) || node.dependencies.some((dependency) =>
    typeof dependency !== "string" || !WORKFLOW_ID_PATTERN.test(dependency))) {
    throw new Error(`工作流节点 ${node.id} 的依赖必须是合法机器 id 数组。`);
  }
  const uniqueDependencies = new Set(node.dependencies);
  if (uniqueDependencies.size !== node.dependencies.length) {
    throw new Error(`工作流节点 ${node.id} 包含重复依赖。`);
  }
}

function freezeNode(node: WorkflowNode): WorkflowNode {
  validateNode(node);
  return Object.freeze({
    id: node.id,
    title: node.title,
    blocking: node.blocking,
    dependencies: Object.freeze([...node.dependencies]),
  });
}

export function sortWorkflowNodes(
  nodes: readonly WorkflowNode[],
): readonly WorkflowNode[] {
  if (nodes.length === 0) {
    throw new Error("工作流至少需要一个节点。");
  }

  const normalizedNodes = nodes.map(freezeNode);
  const nodesById = new Map<string, WorkflowNode>();
  for (const node of normalizedNodes) {
    if (nodesById.has(node.id)) {
      throw new Error(`工作流包含重复节点 id：${node.id}。`);
    }
    nodesById.set(node.id, node);
  }

  const remainingDependencyCounts = new Map<string, number>();
  const dependents = new Map<string, string[]>();
  for (const node of normalizedNodes) {
    remainingDependencyCounts.set(node.id, node.dependencies.length);
    for (const dependencyId of node.dependencies) {
      if (!nodesById.has(dependencyId)) {
        throw new Error(`工作流节点 ${node.id} 引用了未知依赖：${dependencyId}。`);
      }
      const currentDependents = dependents.get(dependencyId) ?? [];
      currentDependents.push(node.id);
      dependents.set(dependencyId, currentDependents);
    }
  }

  const ready = normalizedNodes
    .filter((node) => node.dependencies.length === 0)
    .sort(compareNodeIds);
  const ordered: WorkflowNode[] = [];

  while (ready.length > 0) {
    const current = ready.shift();
    if (current === undefined) break;
    ordered.push(current);

    for (const dependentId of dependents.get(current.id) ?? []) {
      const previousCount = remainingDependencyCounts.get(dependentId);
      if (previousCount === undefined) {
        throw new Error(`工作流内部错误：节点 ${dependentId} 缺少依赖计数。`);
      }
      const nextCount = previousCount - 1;
      remainingDependencyCounts.set(dependentId, nextCount);
      if (nextCount === 0) {
        const dependent = nodesById.get(dependentId);
        if (dependent === undefined) {
          throw new Error(`工作流内部错误：找不到节点 ${dependentId}。`);
        }
        ready.push(dependent);
        ready.sort(compareNodeIds);
      }
    }
  }

  if (ordered.length !== normalizedNodes.length) {
    const orderedIds = new Set(ordered.map((node) => node.id));
    const unresolvedIds = normalizedNodes
      .filter((node) => !orderedIds.has(node.id))
      .map((node) => node.id)
      .sort();
    throw new Error(
      `工作流可能存在依赖环，无法完成拓扑排序的节点：${unresolvedIds.join("、")}。`,
    );
  }

  return ordered;
}

function createDryRunResult(node: WorkflowNode): CheckResult {
  return createCheckResult({
    checkId: node.id,
    title: node.title,
    status: "SKIPPED",
    blocking: node.blocking,
    summary: `dry-run 模式仅展示计划，节点 ${node.id} 未实际执行。`,
    nextActions: ["移除 dry-run 参数后重新执行验证。"],
    artifacts: [],
  });
}

function createBlockedResult(
  node: WorkflowNode,
  blockedDependencies: readonly CheckResult[],
): CheckResult {
  const dependencySummary = blockedDependencies
    .map((dependency) => `${dependency.checkId}（${dependency.status}）`)
    .join("、");
  return createCheckResult({
    checkId: node.id,
    title: node.title,
    status: "BLOCKED",
    blocking: node.blocking,
    summary: `依赖 ${dependencySummary} 未通过，节点 ${node.id} 未执行。`,
    nextActions: [
      `先修复依赖节点：${blockedDependencies.map((item) => item.checkId).join("、")}。`,
    ],
    artifacts: [],
  });
}

function safeThrownMessage(error: unknown): string {
  let message: string;
  try {
    message = error instanceof Error ? error.message : String(error);
  } catch {
    message = "异常详情无法安全读取";
  }
  const bounded = message.slice(0, MAX_ERROR_INPUT_LENGTH);
  const redacted = redactEvidenceText(bounded)
    .replace(/[\u0000-\u001f\u007f-\u009f]/gu, " ")
    .replace(/\s+/gu, " ")
    .trim()
    .slice(0, MAX_ERROR_SUMMARY_LENGTH);
  return redacted || "未知异常";
}

function createFailureResult(node: WorkflowNode, error: unknown): CheckResult {
  const message = safeThrownMessage(error);
  return createCheckResult({
    checkId: node.id,
    title: node.title,
    status: "FAIL",
    blocking: node.blocking,
    summary: `根因：节点 ${node.id} 的执行器抛出异常：${message}。`,
    nextActions: ["检查该节点执行器及其输入，修复异常后重新运行验证。"],
    artifacts: [],
  });
}

function freezeCheckResult(result: CheckResult): CheckResult {
  return Object.freeze({
    ...result,
    nextActions: Object.freeze([...result.nextActions]),
    artifacts: Object.freeze([...result.artifacts]),
  });
}

class DependencyResultSnapshot implements ReadonlyMap<string, CheckResult> {
  readonly #values: Map<string, CheckResult>;

  constructor(entries: Iterable<readonly [string, CheckResult]>) {
    this.#values = new Map(
      [...entries].map(([key, value]) => [key, freezeCheckResult(value)] as const),
    );
    Object.freeze(this);
  }

  get size(): number {
    return this.#values.size;
  }

  get(key: string): CheckResult | undefined {
    return this.#values.get(key);
  }

  has(key: string): boolean {
    return this.#values.has(key);
  }

  entries(): MapIterator<[string, CheckResult]> {
    return this.#values.entries();
  }

  keys(): MapIterator<string> {
    return this.#values.keys();
  }

  values(): MapIterator<CheckResult> {
    return this.#values.values();
  }

  forEach(
    callbackfn: (value: CheckResult, key: string, map: ReadonlyMap<string, CheckResult>) => void,
    thisArg?: unknown,
  ): void {
    this.#values.forEach((value, key) => callbackfn.call(thisArg, value, key, this));
  }

  [Symbol.iterator](): MapIterator<[string, CheckResult]> {
    return this.entries();
  }
}

Object.freeze(DependencyResultSnapshot.prototype);

function normalizeExecutorResult(
  node: WorkflowNode,
  result: unknown,
): CheckResult {
  const validation = validateCheckResult(result);
  if (!validation.valid) {
    throw new Error(`执行器返回结果不符合契约：${validation.errors.join("；")}`);
  }
  const trustedResult = result as CheckResult;
  return createCheckResult({
    checkId: node.id,
    title: node.title,
    status: trustedResult.status,
    blocking: node.blocking,
    summary: trustedResult.summary,
    nextActions: trustedResult.nextActions,
    artifacts: trustedResult.artifacts,
  });
}

export async function executeWorkflow(
  nodes: readonly WorkflowNode[],
  executor: WorkflowCheckExecutor,
  options: WorkflowExecutionOptions = {},
): Promise<RunResult> {
  const orderedNodes = sortWorkflowNodes(nodes);
  const resultsById = new Map<string, CheckResult>();
  const checks: CheckResult[] = [];

  for (const node of orderedNodes) {
    let result: CheckResult;
    if (options.dryRun === true) {
      result = createDryRunResult(node);
    } else {
      const dependencyEntries: Array<readonly [string, CheckResult]> = [];
      for (const dependencyId of node.dependencies) {
        const dependencyResult = resultsById.get(dependencyId);
        if (dependencyResult === undefined) {
          throw new Error(`工作流内部错误：依赖 ${dependencyId} 尚无执行结果。`);
        }
        dependencyEntries.push([dependencyId, dependencyResult]);
      }
      const dependencyResults = new DependencyResultSnapshot(dependencyEntries);
      const blockedDependencies = [...dependencyResults.values()].filter(
        (dependency) => dependency.status !== "PASS",
      );

      if (blockedDependencies.length > 0) {
        result = createBlockedResult(node, blockedDependencies);
      } else {
        try {
          result = normalizeExecutorResult(
            node,
            await executor(Object.freeze({ node, dependencyResults })),
          );
        } catch (error) {
          result = createFailureResult(node, error);
        }
      }
    }

    resultsById.set(node.id, result);
    checks.push(result);
  }

  return createRunResult(checks);
}
