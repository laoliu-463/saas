import { readFileSync } from "node:fs";

import { Ajv2020 } from "ajv/dist/2020.js";
import { describe, expect, it } from "vitest";

import {
  CHECK_STATUS_LABELS,
  RUN_STATUS_LABELS,
  SCHEMA_VERSION,
  aggregateRunStatus,
  createCheckResult,
  createRunResult,
  validateCheckResult,
  validateRunResult,
  type CheckResult,
  type CheckResultInput,
  type CheckStatus,
} from "../../lib/node/core/result.js";

interface StatusContractSchema {
  readonly $id: string;
  readonly properties: {
    readonly status: { readonly enum: readonly string[] };
    readonly statusLabel: { readonly enum: readonly string[] };
  };
  readonly oneOf?: readonly {
    readonly properties: {
      readonly status: { readonly const: string };
      readonly statusLabel: { readonly const: string };
    };
  }[];
}

const CHECK_RESULT_SCHEMA = JSON.parse(
  readFileSync(
    new URL("../../lib/contracts/check-result.schema.json", import.meta.url),
    "utf8",
  ),
) as StatusContractSchema;
const RUN_RESULT_SCHEMA = JSON.parse(
  readFileSync(new URL("../../lib/contracts/run-result.schema.json", import.meta.url), "utf8"),
) as StatusContractSchema;
const CHECK_STATUSES = Object.keys(CHECK_STATUS_LABELS);
const RUN_STATUSES = Object.keys(RUN_STATUS_LABELS);

function schemaStatusLabels(schema: StatusContractSchema): Record<string, string> {
  return Object.fromEntries(
    (schema.oneOf ?? []).map((entry) => [
      entry.properties.status.const,
      entry.properties.statusLabel.const,
    ]),
  );
}

function compileSchemasDirectly() {
  const ajv = new Ajv2020({ allErrors: true, strict: true });
  ajv.addSchema(CHECK_RESULT_SCHEMA);
  const validateCheck = ajv.getSchema(CHECK_RESULT_SCHEMA.$id);
  if (!validateCheck) {
    throw new Error("无法从 Ajv 取得单项检查结果 Schema。");
  }
  const validateRun = ajv.compile(RUN_RESULT_SCHEMA);
  return { validateCheck, validateRun };
}

const BASE_INPUT: CheckResultInput = {
  checkId: "toolchain.node",
  title: "检查 Node.js 工具链",
  status: "PASS",
  blocking: true,
  summary: "Node.js 版本满足要求。",
  nextActions: [],
  artifacts: [],
};

function check(
  status: CheckStatus,
  blocking = true,
  overrides: Partial<CheckResultInput> = {},
): CheckResult {
  return createCheckResult({ ...BASE_INPUT, status, blocking, ...overrides });
}

describe("结果状态契约", () => {
  it("固定 Schema 版本和中文状态标签", () => {
    expect(SCHEMA_VERSION).toBe("1.0.0");
    expect(CHECK_STATUS_LABELS).toEqual({
      PASS: "通过",
      FAIL: "失败",
      BLOCKED: "阻塞",
      WARN: "警告",
      SKIPPED: "已跳过",
      NOT_COLLECTED: "未采集",
    });
    expect(RUN_STATUS_LABELS).toEqual({
      PASS: "通过",
      FAIL: "失败",
      BLOCKED: "阻塞",
      PARTIAL: "部分完成",
    });
  });

  it("从窄输入确定性补全版本和中文标签", () => {
    expect(check("PASS")).toEqual({
      schemaVersion: "1.0.0",
      ...BASE_INPUT,
      statusLabel: "通过",
    });
  });
});

describe("Node 包契约", () => {
  it("固定在满足依赖要求的 Node 20 区间", () => {
    const packageJson = JSON.parse(
      readFileSync(new URL("../../../package.json", import.meta.url), "utf8"),
    ) as { engines: { node: string } };

    expect(packageJson.engines.node).toBe(">=20.19.0 <21");
  });

  it("保留 npm 标准格式化 lockfile", () => {
    const lockfile = readFileSync(
      new URL("../../../package-lock.json", import.meta.url),
      "utf8",
    );

    expect(lockfile.split(/\r?\n/u).length).toBeGreaterThan(200);
    expect(lockfile.endsWith("\n")).toBe(true);
  });
});

describe("运行状态聚合", () => {
  it("阻断失败优先于阻塞", () => {
    expect(aggregateRunStatus([check("BLOCKED"), check("FAIL")])).toBe("FAIL");
  });

  it("没有阻断失败但存在阻塞时返回 BLOCKED", () => {
    expect(aggregateRunStatus([check("PASS"), check("BLOCKED", false)])).toBe(
      "BLOCKED",
    );
  });

  it.each(["SKIPPED", "NOT_COLLECTED"] as const)(
    "阻断检查为 %s 时返回 PARTIAL",
    (status) => {
      expect(aggregateRunStatus([check("PASS"), check(status)])).toBe("PARTIAL");
    },
  );

  it.each(["SKIPPED", "NOT_COLLECTED", "WARN"] as const)(
    "非阻断检查为 %s 且阻断检查通过时仍可 PASS",
    (status) => {
      expect(aggregateRunStatus([check("PASS"), check(status, false)])).toBe(
        "PASS",
      );
    },
  );

  it("阻断警告和非阻断失败不能被升级为 PASS", () => {
    expect(aggregateRunStatus([check("PASS"), check("WARN")])).toBe("PARTIAL");
    expect(aggregateRunStatus([check("PASS"), check("FAIL", false)])).toBe(
      "PARTIAL",
    );
  });

  it("没有任何检查时返回 PARTIAL", () => {
    expect(aggregateRunStatus([])).toBe("PARTIAL");
  });

  it("只有非阻断检查时不能形成通过证据", () => {
    expect(aggregateRunStatus([check("PASS", false)])).toBe("PARTIAL");
  });
});

describe("JSON Schema 与语义校验", () => {
  it("Schema 直接通过 Ajv 校验时拒绝状态与中文标签错配", () => {
    const { validateCheck, validateRun } = compileSchemasDirectly();
    const validRun = createRunResult([check("PASS")]);

    expect(validateCheck({ ...check("PASS"), statusLabel: "失败" })).toBe(false);
    expect(validateRun({ ...validRun, statusLabel: "失败" })).toBe(false);
  });

  it("Schema 状态集合和中文标签映射与 TypeScript 定义完全一致", () => {
    expect(CHECK_RESULT_SCHEMA.properties.status.enum).toEqual(CHECK_STATUSES);
    expect(CHECK_RESULT_SCHEMA.properties.statusLabel.enum).toEqual(
      Object.values(CHECK_STATUS_LABELS),
    );
    expect(schemaStatusLabels(CHECK_RESULT_SCHEMA)).toEqual(CHECK_STATUS_LABELS);

    expect(RUN_RESULT_SCHEMA.properties.status.enum).toEqual(RUN_STATUSES);
    expect(RUN_RESULT_SCHEMA.properties.statusLabel.enum).toEqual(
      Object.values(RUN_STATUS_LABELS),
    );
    expect(schemaStatusLabels(RUN_RESULT_SCHEMA)).toEqual(RUN_STATUS_LABELS);
  });

  it("接受完整且标签一致的单项结果", () => {
    expect(validateCheckResult(check("PASS"))).toEqual({ valid: true });
  });

  it("拒绝非法状态、缺失必填字段和错误 Schema 版本", () => {
    const common = {
      schemaVersion: SCHEMA_VERSION,
      checkId: "toolchain.node",
      title: "检查 Node.js 工具链",
      statusLabel: "通过",
      blocking: true,
      summary: "Node.js 版本满足要求。",
      nextActions: [],
      artifacts: [],
    };

    expect(validateCheckResult({ ...common, status: "UNKNOWN" }).valid).toBe(false);
    expect(validateCheckResult({ ...common, status: "PASS", title: undefined }).valid).toBe(
      false,
    );
    expect(
      validateCheckResult({ ...common, schemaVersion: "2.0.0", status: "PASS" }).valid,
    ).toBe(false);
  });

  it("拒绝英文标题及状态与中文标签不一致", () => {
    expect(
      validateCheckResult({ ...check("PASS"), title: "Check Node.js" }).valid,
    ).toBe(false);

    const result = validateCheckResult({ ...check("PASS"), statusLabel: "失败" });
    expect(result.valid).toBe(false);
    if (!result.valid) {
      expect(result.errors.join(" ")).toContain("/statusLabel");
    }
  });

  it("拒绝纯英文摘要但允许中文说明包含英文技术标识", () => {
    expect(
      validateCheckResult({ ...check("PASS"), summary: "Node.js is ready." }).valid,
    ).toBe(false);
    expect(
      validateCheckResult({
        ...check("PASS"),
        summary: "Node.js 20 运行环境已就绪。",
      }).valid,
    ).toBe(true);
    expect(() =>
      check("PASS", true, { summary: "Node.js is ready." }),
    ).toThrowError(/无法创建检查结果/);
  });

  it("创建可通过 Schema 校验且聚合一致的运行结果", () => {
    const run = createRunResult([check("PASS"), check("WARN", false)]);

    expect(run.status).toBe("PASS");
    expect(run.statusLabel).toBe("通过");
    expect(validateRunResult(run)).toEqual({ valid: true });
  });

  it("拒绝运行状态标签不一致和空检查集合", () => {
    const run = createRunResult([check("PASS")]);
    const mismatch = validateRunResult({ ...run, statusLabel: "失败" });

    expect(mismatch.valid).toBe(false);
    if (!mismatch.valid) {
      expect(mismatch.errors.join(" ")).toContain("/statusLabel");
    }
    expect(() => createRunResult([])).toThrowError(/至少包含一个检查结果/);
  });

  it("拒绝与检查聚合结果不一致的运行状态", () => {
    const run = createRunResult([check("PASS")]);
    const result = validateRunResult({
      ...run,
      status: "PARTIAL",
      statusLabel: "部分完成",
    });

    expect(result.valid).toBe(false);
    if (!result.valid) {
      expect(result.errors.join(" ")).toContain("聚合结果");
    }
  });
});
