import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { runBackendCheck } from "../checks/backend.js";
import { runBusinessCheck } from "../checks/business.js";
import { runDockerCheck } from "../checks/docker.js";
import { runFrontendCheck } from "../checks/frontend.js";
import { runHealthCheck } from "../checks/health.js";
import { runInspect } from "../checks/inspect.js";
import {
  getEnvironmentContract,
  parseEnvText,
  type EnvironmentName,
} from "../core/config.js";
import {
  createEvidenceReport,
  writeEvidence,
  type EvidenceReport,
  type EvidenceWriteOutcome,
} from "../core/evidence.js";
import { createRunContext, validateReportKey, type RunContext } from "../core/run-context.js";
import type { GitSnapshot } from "../core/git.js";
import {
  createCheckResult,
  createRunResult,
  type CheckResult,
  type RunResult,
} from "../core/result.js";
import type { WorkflowCheckExecutor } from "../core/workflow.js";
import {
  runVerifyWorkflow,
  type RunVerifyWorkflowOptions,
  type VerifyNodeId,
  type VerifyScope,
} from "./verify.js";

export interface NodeVerifyOptions {
  readonly repoRoot: string;
  readonly environment: EnvironmentName;
  readonly scope: VerifyScope;
  readonly reportKey: string;
  readonly businessCommand?: string;
  readonly skipBusinessValidation?: boolean;
  readonly dryRun?: boolean;
  readonly gitSnapshot?: GitSnapshot;
  readonly onStage?: (message: string) => void;
}

export interface LoadEnvironmentSecretsOptions {
  readonly repoRoot: string;
  readonly environment: EnvironmentName;
  readonly readFile?: (path: string) => string;
}

export interface NodeVerifyOutcome {
  readonly context: RunContext;
  readonly result: RunResult;
  readonly report: EvidenceReport;
  readonly evidence: EvidenceWriteOutcome;
}

export interface NodeVerifyDependencies {
  readonly runInspect: typeof runInspect;
  readonly runVerifyWorkflow: (options: RunVerifyWorkflowOptions) => Promise<RunResult>;
  readonly runBackendCheck: typeof runBackendCheck;
  readonly runFrontendCheck: typeof runFrontendCheck;
  readonly runDockerCheck: typeof runDockerCheck;
  readonly runHealthCheck: typeof runHealthCheck;
  readonly runBusinessCheck: typeof runBusinessCheck;
  readonly createRunContext: typeof createRunContext;
  readonly createEvidenceReport: typeof createEvidenceReport;
  readonly writeEvidence: typeof writeEvidence;
  readonly loadEnvironmentSecrets: typeof loadEnvironmentSecrets;
}

export class VerifyContractError extends Error {
  override readonly name = "VerifyContractError";
}

const DEFAULT_DEPENDENCIES: NodeVerifyDependencies = {
  runInspect,
  runVerifyWorkflow,
  runBackendCheck,
  runFrontendCheck,
  runDockerCheck,
  runHealthCheck,
  runBusinessCheck,
  createRunContext,
  createEvidenceReport,
  writeEvidence,
  loadEnvironmentSecrets,
};

function errorCode(error: unknown): string | undefined {
  if (typeof error !== "object" || error === null) return undefined;
  const code = Reflect.get(error, "code");
  return typeof code === "string" ? code : undefined;
}

export function loadEnvironmentSecrets(
  options: LoadEnvironmentSecretsOptions,
): readonly string[] {
  const contract = getEnvironmentContract(options.environment);
  const readFile = options.readFile ?? ((path: string) => readFileSync(path, "utf8"));
  let content: string;
  try {
    content = readFile(resolve(options.repoRoot, contract.envFile));
  } catch (error) {
    if (errorCode(error) === "ENOENT") return Object.freeze([]);
    throw new Error("实际环境文件无法安全读取，不能建立完整脱敏边界。");
  }
  const values = parseEnvText(content);
  return Object.freeze([
    ...new Set(contract.secretKeys
      .map(({ key }) => values[key]?.trim() ?? "")
      .filter((value) => value.length > 0)),
  ]);
}

function validateOptions(options: NodeVerifyOptions): void {
  if (options.environment !== "test" && options.environment !== "real-pre") {
    throw new VerifyContractError("环境只支持 test 或 real-pre。");
  }
  if (options.scope !== "backend" && options.scope !== "frontend" && options.scope !== "full") {
    throw new VerifyContractError("Scope 只支持 backend、frontend 或 full。");
  }
  try {
    validateReportKey(options.reportKey);
  } catch {
    throw new VerifyContractError("ReportKey 不符合稳定报告键契约。");
  }
}

interface VerifyExecutorOptions {
  readonly repoRoot: string;
  readonly rawDir: string;
  readonly environment: EnvironmentName;
  readonly scope: VerifyScope;
  readonly businessCommand?: string;
  readonly skipBusinessValidation: boolean;
  readonly secrets: readonly string[];
  readonly dependencies: NodeVerifyDependencies;
}

function unknownNode(nodeId: string): CheckResult {
  throw new VerifyContractError(`验证工作流出现未知节点 ${nodeId}。`);
}

function createCheckExecutor(options: VerifyExecutorOptions): WorkflowCheckExecutor {
  return async ({ node }) => {
    const common = {
      repoRoot: options.repoRoot,
      rawDir: options.rawDir,
      secrets: [...options.secrets],
    };
    const nodeId = node.id as VerifyNodeId;
    if (nodeId === "backend") return options.dependencies.runBackendCheck(common);
    if (nodeId === "frontend") return options.dependencies.runFrontendCheck(common);
    if (nodeId === "docker") {
      return options.dependencies.runDockerCheck({
        ...common,
        environment: options.environment,
        scope: options.scope,
      });
    }
    if (nodeId === "health") {
      return options.dependencies.runHealthCheck({
        ...common,
        environment: options.environment,
        scope: options.scope,
      });
    }
    if (nodeId === "business") {
      return options.dependencies.runBusinessCheck({
        ...common,
        environment: options.environment,
        skipBusinessValidation: options.skipBusinessValidation,
        ...(options.businessCommand === undefined
          ? {}
          : { businessCommand: options.businessCommand }),
      });
    }
    return unknownNode(node.id);
  };
}

function stage(options: NodeVerifyOptions, message: string): void {
  options.onStage?.(message);
}

export async function runNodeVerify(
  options: NodeVerifyOptions,
  overrides: Partial<NodeVerifyDependencies> = {},
): Promise<NodeVerifyOutcome> {
  validateOptions(options);
  const dependencies: NodeVerifyDependencies = {
    ...DEFAULT_DEPENDENCIES,
    ...overrides,
  };

  stage(options, `阶段 1/3：执行 ${options.environment} 只读 inspect。`);
  const inspectResult = await dependencies.runInspect({
    environment: options.environment,
    repoRoot: options.repoRoot,
  });
  const context = dependencies.createRunContext({
    repoRoot: options.repoRoot,
    environment: options.environment,
    scope: options.scope,
    reportKey: options.reportKey,
    ...(options.gitSnapshot === undefined ? {} : { gitSnapshot: options.gitSnapshot }),
  });
  const secrets = dependencies.loadEnvironmentSecrets({
    repoRoot: options.repoRoot,
    environment: options.environment,
  });

  const sourceControlCheck = context.git.identity.kind === "UNAVAILABLE"
    ? createCheckResult({
      checkId: "evidence.git-identity",
      title: "检查 Git 证据身份",
      status: "WARN",
      blocking: true,
      summary: "直接 Node 验证不调用 Git，本次未采集 Git 身份，最终结论不得为 PASS。",
      nextActions: ["需要提交候选时通过 agent-do 注入受控 Git 快照后重跑。"],
      artifacts: [],
    })
    : createCheckResult({
      checkId: "evidence.git-identity",
      title: "检查 Git 证据身份",
      status: "PASS",
      blocking: true,
      summary: "已消费 agent-do 注入的 Git 快照，Node 未执行 Git 命令。",
      nextActions: [],
      artifacts: [],
    });

  let result: RunResult;
  if (inspectResult.status !== "PASS") {
    stage(options, `阶段 2/3：inspect 结论为 ${inspectResult.status}，后续检查已阻断。`);
    result = createRunResult([...inspectResult.checks, sourceControlCheck]);
  } else {
    stage(options, options.dryRun === true
      ? "阶段 2/3：生成验证计划，所有写操作与 HTTP 检查记为 SKIPPED。"
      : `阶段 2/3：执行 ${options.scope} 本地验证工作流。`);
    const verification = await dependencies.runVerifyWorkflow({
      scope: options.scope,
      executor: createCheckExecutor({
        repoRoot: options.repoRoot,
        rawDir: context.rawDir,
        environment: options.environment,
        scope: options.scope,
        skipBusinessValidation: options.skipBusinessValidation === true,
        secrets,
        dependencies,
        ...(options.businessCommand === undefined
          ? {}
          : { businessCommand: options.businessCommand }),
      }),
      dryRun: options.dryRun === true,
    });
    result = createRunResult([
      ...inspectResult.checks,
      ...verification.checks,
      sourceControlCheck,
    ]);
  }

  stage(options, "阶段 3/3：由同一 RunResult 写入原始与稳定证据。 ");
  let report: EvidenceReport;
  try {
    report = dependencies.createEvidenceReport(context, result, { secrets });
  } catch {
    throw new VerifyContractError("RunResult 或 EvidenceReport 不符合证据契约。");
  }
  const evidence = dependencies.writeEvidence(report, {
    repoRoot: options.repoRoot,
    secrets,
  });
  return { context, result, report, evidence };
}
