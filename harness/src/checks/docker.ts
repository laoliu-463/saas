import { existsSync } from "node:fs";
import { resolve } from "node:path";

import {
  getEnvironmentContract,
  type EnvironmentName,
} from "../core/config.js";
import {
  runProcess,
  type ProcessOptions,
  type ProcessRunner,
} from "../core/process-runner.js";
import { createCheckResult, type CheckResult } from "../core/result.js";
import type { VerifyScope } from "../workflows/verify.js";
import {
  buildLogArtifactPath,
  writeBuildStepLog,
  type BuildLogWriter,
} from "./build-log.js";
import { normalizeProcessResult, safeCheckText } from "./process-result.js";

export interface DockerCommandStep {
  readonly stepId: "docker-up" | "docker-ps";
  readonly command: "docker";
  readonly args: readonly string[];
  readonly timeoutMs: number;
}

export interface BuildDockerCommandPlanOptions {
  readonly environment: EnvironmentName;
  readonly scope: VerifyScope;
  readonly envFileExists: boolean;
}

export interface RunDockerCheckOptions {
  readonly environment: EnvironmentName;
  readonly scope: VerifyScope;
  readonly repoRoot: string;
  readonly rawDir: string;
  readonly fileExists?: (path: string) => boolean;
  readonly processRunner?: ProcessRunner;
  readonly logWriter?: BuildLogWriter;
  readonly dryRun?: boolean;
  readonly secrets?: readonly string[];
}

const SAFE_SERVICE_PATTERN = /^[a-z0-9][a-z0-9-]*$/u;
const FORBIDDEN_SERVICE_PATTERN = /(?:^|-)(?:postgres|redis)(?:-|$)/iu;

function selectedServices(
  scope: VerifyScope,
  backendService: string,
  frontendService: string,
): readonly string[] {
  const services = scope === "backend"
    ? [backendService]
    : scope === "frontend"
      ? [frontendService]
      : scope === "full"
        ? [backendService, frontendService]
        : [];
  if (services.length === 0) {
    throw new Error(`不支持的 Docker 验证范围：${String(scope)}。`);
  }
  if (services.some((service) =>
    !SAFE_SERVICE_PATTERN.test(service) || FORBIDDEN_SERVICE_PATTERN.test(service))) {
    throw new Error("Docker 服务名不符合应用服务安全约束，已拒绝生成命令。");
  }
  return services;
}

export function buildDockerCommandPlan(
  options: BuildDockerCommandPlanOptions,
): readonly DockerCommandStep[] {
  const contract = getEnvironmentContract(options.environment);
  const services = selectedServices(
    options.scope,
    contract.backendService,
    contract.frontendService,
  );
  const composeArgs = options.envFileExists
    ? ["compose", "--env-file", contract.envFile, "-f", contract.composeFile]
    : ["compose", "-f", contract.composeFile];
  return Object.freeze([
    Object.freeze({
      stepId: "docker-up" as const,
      command: "docker" as const,
      args: Object.freeze([
        ...composeArgs,
        "up",
        "-d",
        "--build",
        "--no-deps",
        ...services,
      ]),
      timeoutMs: 15 * 60_000,
    }),
    Object.freeze({
      stepId: "docker-ps" as const,
      command: "docker" as const,
      args: Object.freeze([...composeArgs, "ps"]),
      timeoutMs: 2 * 60_000,
    }),
  ]);
}

function dockerFailure(
  summary: string,
  nextActions: readonly string[],
  artifacts: readonly string[],
): CheckResult {
  return createCheckResult({
    checkId: "docker",
    title: "验证 Docker 运行环境",
    status: "FAIL",
    blocking: true,
    summary,
    nextActions,
    artifacts,
  });
}

export async function runDockerCheck(
  options: RunDockerCheckOptions,
): Promise<CheckResult> {
  const secrets = Object.freeze([...(options.secrets ?? [])]);
  let plan: readonly DockerCommandStep[];
  let expectedArtifacts: readonly string[];
  try {
    const contract = getEnvironmentContract(options.environment);
    const fileExists = options.fileExists ?? existsSync;
    plan = buildDockerCommandPlan({
      environment: options.environment,
      scope: options.scope,
      envFileExists: fileExists(resolve(options.repoRoot, contract.envFile)),
    });
    expectedArtifacts = plan.map((step) =>
      buildLogArtifactPath(options.rawDir, step.stepId)
    );
  } catch (error) {
    return dockerFailure(
      `Docker 检查配置无效：${safeCheckText(error, secrets)}。`,
      ["修复环境、Scope、rawDir 或固定 Compose 配置后重新运行验证。"],
      [],
    );
  }

  if (options.dryRun === true) {
    return createCheckResult({
      checkId: "docker",
      title: "验证 Docker 运行环境",
      status: "SKIPPED",
      blocking: true,
      summary: "dry-run 模式仅展示 Docker 命令计划，未重建或查询容器。",
      nextActions: [
        ...plan.map((step) => `计划命令：${[step.command, ...step.args].join(" ")}`),
        "移除 dry-run 参数后执行 Docker 验证。",
      ],
      artifacts: [],
    });
  }

  const runner = options.processRunner ?? runProcess;
  const logWriter = options.logWriter ?? writeBuildStepLog;
  const artifacts: string[] = [];

  for (const [index, step] of plan.entries()) {
    let rawResult: unknown;
    try {
      const processOptions: ProcessOptions = {
        command: step.command,
        args: [...step.args],
        cwd: options.repoRoot,
        timeoutMs: step.timeoutMs,
        secrets: [...secrets],
      };
      rawResult = await runner(processOptions);
    } catch (error) {
      return dockerFailure(
        `Docker 步骤 ${step.stepId} 执行器异常：${safeCheckText(error, secrets)}。`,
        [
          "安全重试：确认 Docker 工具链、工作目录和权限后，可重新运行一次。",
          "停止条件：异常原因未变化或容器状态无法确认时停止重试。",
        ],
        artifacts,
      );
    }

    let result;
    try {
      result = normalizeProcessResult(rawResult, secrets);
    } catch (error) {
      return dockerFailure(
        `Docker 步骤 ${step.stepId} 的结果不符合契约：${safeCheckText(error, secrets)}。`,
        [
          "安全重试：修复 ProcessRunner 结果契约后，可重新运行一次。",
          "停止条件：结果无法可信解析时停止验证，不得生成通过证据。",
        ],
        artifacts,
      );
    }

    try {
      const artifact = await logWriter({
        repoRoot: options.repoRoot,
        rawDir: options.rawDir,
        stepId: step.stepId,
        result,
      });
      if (artifact !== expectedArtifacts[index]) {
        throw new Error("artifact 路径不符合固定计划");
      }
      artifacts.push(artifact);
    } catch (error) {
      return dockerFailure(
        `Docker 步骤 ${step.stepId} 的日志写入失败：${safeCheckText(error, secrets)}。`,
        [
          "安全重试：确认 rawDir、磁盘空间和文件权限后，可重新运行一次。",
          "停止条件：日志仍无法独占写入时停止验证，不得把无证据操作标记为通过。",
        ],
        artifacts,
      );
    }

    if (!result.success) {
      return dockerFailure(
        `Docker 步骤 ${step.stepId} 失败。根因：${result.rootCause ?? "命令执行失败，未返回根因。"}`,
        [
          `安全重试：${result.safeRetry ?? "确认容器状态和幂等性后再重试。"}`,
          `停止条件：${result.stopCondition ?? "无法确认数据卷与容器安全时停止重试。"}`,
        ],
        artifacts,
      );
    }
  }

  return createCheckResult({
    checkId: "docker",
    title: "验证 Docker 运行环境",
    status: "PASS",
    blocking: true,
    summary: "目标应用服务已完成安全重建，Compose 状态查询成功。",
    nextActions: [],
    artifacts,
  });
}
