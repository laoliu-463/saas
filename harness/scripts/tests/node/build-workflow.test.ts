import { describe, expect, it } from "vitest";

import {
  runBackendCheck,
  type BuildLogInput,
} from "../../lib/node/checks/backend.js";
import { runFrontendCheck } from "../../lib/node/checks/frontend.js";
import type { ProcessOptions, ProcessResult } from "../../lib/node/core/process-runner.js";
import { createCheckResult } from "../../lib/node/core/result.js";
import type { WorkflowCheckExecutor } from "../../lib/node/core/workflow.js";
import {
  runVerifyWorkflow,
  type VerifyScope,
} from "../../lib/node/workflows/verify.js";

function successfulProcessResult(options: ProcessOptions): ProcessResult {
  return {
    commandDisplay: [options.command, ...options.args].join(" "),
    exitCode: 0,
    signal: null,
    timedOut: false,
    durationMs: 1,
    stdout: "",
    stderr: "",
    success: true,
    rootCause: null,
    safeRetry: null,
    stopCondition: null,
  };
}

describe("verify 工作流组合构建检查", () => {
  it.each([
    [
      "backend",
      [
        "mvn -f backend/pom.xml test",
        "mvn -f backend/pom.xml -DskipTests package",
      ],
    ],
    [
      "frontend",
      [
        "npm --prefix frontend ci",
        "npm --prefix frontend run typecheck",
        "npm --prefix frontend run test",
        "npm --prefix frontend run build",
      ],
    ],
    [
      "full",
      [
        "mvn -f backend/pom.xml test",
        "mvn -f backend/pom.xml -DskipTests package",
        "npm --prefix frontend ci",
        "npm --prefix frontend run typecheck",
        "npm --prefix frontend run test",
        "npm --prefix frontend run build",
      ],
    ],
  ] as const)("scope=%s 只调用所选 build 检查", async (scope, expectedCommands) => {
    const executed: string[] = [];
    const nonBuildNodes: string[] = [];
    const processRunner = async (options: ProcessOptions): Promise<ProcessResult> => {
      executed.push([options.command, ...options.args].join(" "));
      return successfulProcessResult(options);
    };
    const logWriter = async (input: BuildLogInput): Promise<string> =>
      `${input.rawDir}/${input.stepId}.log`;
    const executor: WorkflowCheckExecutor = async ({ node }) => {
      if (node.id === "backend") {
        return runBackendCheck({
          repoRoot: "D:/repo",
          rawDir: `runtime/qa/out/run-${scope}`,
          processRunner,
          logWriter,
        });
      }
      if (node.id === "frontend") {
        return runFrontendCheck({
          repoRoot: "D:/repo",
          rawDir: `runtime/qa/out/run-${scope}`,
          processRunner,
          logWriter,
        });
      }
      nonBuildNodes.push(node.id);
      return createCheckResult({
        checkId: node.id,
        title: node.title,
        status: "PASS",
        blocking: node.blocking,
        summary: `fixture 节点 ${node.id} 已通过。`,
        nextActions: [],
        artifacts: [],
      });
    };

    const result = await runVerifyWorkflow({
      scope: scope as VerifyScope,
      executor,
    });

    expect(result.status).toBe("PASS");
    expect(executed).toEqual(expectedCommands);
    expect(nonBuildNodes).toEqual(["docker", "health", "business"]);
  });
});
