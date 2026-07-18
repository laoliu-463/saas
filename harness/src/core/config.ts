import { readFileSync } from "node:fs";

export type EnvironmentName = "test" | "real-pre";

export interface SecretKeyContract {
  readonly key: string;
  readonly required: boolean;
}

export interface EnvironmentContract {
  readonly composeFile: string;
  readonly envFile: string;
  readonly envExampleFile: string;
  readonly projectName: string;
  readonly backendService: string;
  readonly frontendService: string;
  readonly backendPort: number;
  readonly frontendPort: number;
  readonly backendHealthPath: string;
  readonly frontendHealthPaths: readonly string[];
  readonly requiredConfigKeys: readonly string[];
  readonly hardSwitches: Readonly<Record<string, string>>;
  readonly secretKeys: readonly SecretKeyContract[];
}

export interface EnvironmentContracts {
  readonly schemaVersion: "1.0.0";
  readonly toolchain: {
    readonly nodeMajor: number;
    readonly javaMajor: number;
  };
  readonly environments: Readonly<Record<EnvironmentName, EnvironmentContract>>;
}

export class EnvironmentContractError extends Error {
  override readonly name = "EnvironmentContractError";
}

const CONTRACT_URL = new URL("../../contracts/environments.json", import.meta.url);
const ENVIRONMENT_NAMES = ["test", "real-pre"] as const;
const KEY_PATTERN = /^[A-Z][A-Z0-9_]*$/u;
const REQUIRED_TOOLCHAIN = { nodeMajor: 20, javaMajor: 17 } as const;
const ENVIRONMENT_IDENTITY = {
  test: {
    composeFile: "docker-compose.test.yml",
    envFile: ".env.test",
    envExampleFile: ".env.test.example",
    projectName: "saas-test",
  },
  "real-pre": {
    composeFile: "docker-compose.real-pre.yml",
    envFile: ".env.real-pre",
    envExampleFile: ".env.real-pre.example",
    projectName: "saas-active",
  },
} as const;
const REQUIRED_CONFIG_KEYS = {
  test: ["SPRING_PROFILES_ACTIVE", "DB_NAME", "DB_USER"],
  "real-pre": [
    "COMPOSE_PROJECT_NAME",
    "SPRING_PROFILES_ACTIVE",
    "DB_NAME",
    "DB_USER",
    "ADMIN_PASSWORD",
    "DOUYIN_APP_ID",
    "DOUYIN_CLIENT_KEY",
    "DOUYIN_OAUTH_REDIRECT_URI",
    "DOUYIN_OAUTH_FRONTEND_SUCCESS_URL",
    "DOUYIN_OAUTH_FRONTEND_FAILURE_URL",
  ],
} as const;
const REQUIRED_HARD_SWITCHES = {
  test: { APP_TEST_ENABLED: "true", DOUYIN_TEST_ENABLED: "true" },
  "real-pre": {
    APP_TEST_ENABLED: "false",
    DOUYIN_TEST_ENABLED: "false",
    DOUYIN_REAL_UPSTREAM_MODE: "live",
  },
} as const;
const REQUIRED_REAL_PRE_SECRETS = [
  "DB_PASSWORD",
  "REDIS_PASSWORD",
  "JWT_SECRET",
  "DOUYIN_CLIENT_SECRET",
  "LOGISTICS_KD100_KEY",
] as const;

function fail(message: string): never {
  throw new EnvironmentContractError(`环境契约错误：${message}`);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function requireString(record: Record<string, unknown>, key: string): string {
  const value = record[key];
  if (typeof value !== "string" || value.trim().length === 0) {
    fail(`字段 ${key} 必须是非空字符串。`);
  }
  return value;
}

function requirePositiveInteger(record: Record<string, unknown>, key: string): number {
  const value = record[key];
  if (!Number.isInteger(value) || Number(value) <= 0) {
    fail(`字段 ${key} 必须是正整数。`);
  }
  return Number(value);
}

function requireStringArray(
  record: Record<string, unknown>,
  key: string,
  itemPattern?: RegExp,
): string[] {
  const value = record[key];
  if (!Array.isArray(value) || value.length === 0 || value.some((item) =>
    typeof item !== "string" || item.length === 0 ||
    (itemPattern !== undefined && !itemPattern.test(item)))) {
    fail(`字段 ${key} 必须是非空字符串数组。`);
  }
  const items = [...value] as string[];
  if (new Set(items).size !== items.length) fail(`字段 ${key} 不允许重复项。`);
  return items;
}

function parseSwitches(value: unknown): Readonly<Record<string, string>> {
  if (!isRecord(value)) fail("hardSwitches 必须是对象。");
  const entries = Object.entries(value);
  if (entries.length === 0 || entries.some(([key, item]) =>
    !KEY_PATTERN.test(key) || typeof item !== "string" || item.length === 0)) {
    fail("hardSwitches 包含非法键或非字符串值。");
  }
  return Object.fromEntries(entries) as Readonly<Record<string, string>>;
}

function parseSecrets(value: unknown): readonly SecretKeyContract[] {
  if (!Array.isArray(value) || value.length === 0) fail("secretKeys 必须是非空数组。");
  const secrets = value.map((item) => {
    if (!isRecord(item) || typeof item["key"] !== "string" ||
      !KEY_PATTERN.test(item["key"]) || typeof item["required"] !== "boolean") {
      fail("secretKeys 包含非法项目。");
    }
    return { key: item["key"], required: item["required"] };
  });
  if (new Set(secrets.map(({ key }) => key)).size !== secrets.length) {
    fail("secretKeys 不允许重复键。");
  }
  return secrets;
}

function parseEnvironment(value: unknown, name: EnvironmentName): EnvironmentContract {
  if (!isRecord(value)) fail(`环境 ${name} 必须是对象。`);
  return {
    composeFile: requireString(value, "composeFile"),
    envFile: requireString(value, "envFile"),
    envExampleFile: requireString(value, "envExampleFile"),
    projectName: requireString(value, "projectName"),
    backendService: requireString(value, "backendService"),
    frontendService: requireString(value, "frontendService"),
    backendPort: requirePositiveInteger(value, "backendPort"),
    frontendPort: requirePositiveInteger(value, "frontendPort"),
    backendHealthPath: requireString(value, "backendHealthPath"),
    frontendHealthPaths: requireStringArray(value, "frontendHealthPaths"),
    requiredConfigKeys: requireStringArray(value, "requiredConfigKeys", KEY_PATTERN),
    hardSwitches: parseSwitches(value["hardSwitches"]),
    secretKeys: parseSecrets(value["secretKeys"]),
  };
}

function requireEntries(
  name: EnvironmentName,
  field: string,
  actual: readonly string[],
  expected: readonly string[],
): void {
  const missing = expected.filter((item) => !actual.includes(item));
  if (missing.length > 0) fail(`环境 ${name} 的 ${field} 缺少安全必需项：${missing.join("、")}。`);
}

function assertEnvironmentBaseline(name: EnvironmentName, contract: EnvironmentContract): void {
  const identity = ENVIRONMENT_IDENTITY[name];
  for (const [field, expected] of Object.entries(identity)) {
    if (contract[field as keyof typeof identity] !== expected) {
      fail(`环境 ${name} 的 ${field} 必须固定为 ${expected}。`);
    }
  }
  requireEntries(name, "requiredConfigKeys", contract.requiredConfigKeys, REQUIRED_CONFIG_KEYS[name]);
  for (const [key, expected] of Object.entries(REQUIRED_HARD_SWITCHES[name])) {
    if (contract.hardSwitches[key] !== expected) {
      fail(`环境 ${name} 的硬开关 ${key} 必须固定为 ${expected}。`);
    }
  }
  if (name === "real-pre") {
    const requiredSecrets = contract.secretKeys
      .filter(({ required }) => required)
      .map(({ key }) => key);
    requireEntries(name, "required secretKeys", requiredSecrets, REQUIRED_REAL_PRE_SECRETS);
  }
}

export function parseEnvironmentContracts(parsed: unknown): EnvironmentContracts {
  if (!isRecord(parsed) || parsed["schemaVersion"] !== "1.0.0" ||
    !isRecord(parsed["toolchain"]) || !isRecord(parsed["environments"])) {
    fail("根结构、schemaVersion、toolchain 或 environments 无效。");
  }
  const toolchain = parsed["toolchain"];
  const environments = parsed["environments"];
  const environmentNames = Object.keys(environments).sort((left, right) => left.localeCompare(right, "en"));
  if (environmentNames.join(",") !== [...ENVIRONMENT_NAMES].sort().join(",")) {
    fail("environments 必须且只能包含 test 与 real-pre。");
  }
  const nodeMajor = requirePositiveInteger(toolchain, "nodeMajor");
  const javaMajor = requirePositiveInteger(toolchain, "javaMajor");
  if (nodeMajor !== REQUIRED_TOOLCHAIN.nodeMajor || javaMajor !== REQUIRED_TOOLCHAIN.javaMajor) {
    fail("工具链基线必须固定为 Node.js 20 与 Java 17。");
  }
  const test = parseEnvironment(environments["test"], "test");
  const realPre = parseEnvironment(environments["real-pre"], "real-pre");
  assertEnvironmentBaseline("test", test);
  assertEnvironmentBaseline("real-pre", realPre);
  return {
    schemaVersion: "1.0.0",
    toolchain: { nodeMajor, javaMajor },
    environments: { test, "real-pre": realPre },
  };
}

export function loadEnvironmentContracts(): EnvironmentContracts {
  let parsed: unknown;
  try {
    parsed = JSON.parse(readFileSync(CONTRACT_URL, "utf8"));
  } catch (error) {
    const reason = error instanceof Error ? error.message : "未知读取错误";
    fail(`无法读取 environments.json：${reason}`);
  }
  return parseEnvironmentContracts(parsed);
}

export function getEnvironmentContract(
  name: EnvironmentName,
  contracts: EnvironmentContracts = loadEnvironmentContracts(),
): EnvironmentContract {
  if (!ENVIRONMENT_NAMES.includes(name)) {
    fail(`不支持环境“${String(name)}”，仅允许 test 或 real-pre。`);
  }
  return contracts.environments[name];
}

export function parseEnvText(content: string): Readonly<Record<string, string>> {
  const values: Record<string, string> = {};
  for (const line of content.split(/\r?\n/gu)) {
    const trimmed = line.trim();
    if (trimmed.length === 0 || trimmed.startsWith("#")) continue;
    const separator = trimmed.indexOf("=");
    if (separator <= 0) continue;
    const key = trimmed.slice(0, separator).trim();
    if (!KEY_PATTERN.test(key)) continue;
    let value = trimmed.slice(separator + 1).trim();
    if (value.length >= 2 && ((value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'")))) {
      value = value.slice(1, -1);
    }
    values[key] = value;
  }
  return values;
}
