import { readFileSync } from "node:fs";

import { describe, expect, it } from "vitest";

import {
  EnvironmentContractError,
  getEnvironmentContract,
  loadEnvironmentContracts,
  parseEnvironmentContracts,
  parseEnvText,
} from "../src/core/config.js";

describe("环境事实契约", () => {
  it("固定 test 与 real-pre 的 Compose、env 和服务事实", () => {
    const contract = loadEnvironmentContracts();

    expect(contract.schemaVersion).toBe("1.0.0");
    expect(Object.keys(contract.environments).sort()).toEqual(["real-pre", "test"]);
    expect(getEnvironmentContract("test", contract)).toMatchObject({
      composeFile: "docker-compose.test.yml",
      envFile: ".env.test",
      envExampleFile: ".env.test.example",
      projectName: "saas-test",
      backendService: "backend",
      frontendService: "frontend",
      backendPort: 8080,
      frontendPort: 3000,
    });
    expect(getEnvironmentContract("real-pre", contract)).toMatchObject({
      composeFile: "docker-compose.real-pre.yml",
      envFile: ".env.real-pre",
      envExampleFile: ".env.real-pre.example",
      projectName: "saas-active",
      backendService: "backend-real-pre",
      frontendService: "frontend-real-pre",
      backendPort: 8081,
      frontendPort: 3001,
      hardSwitches: {
        APP_TEST_ENABLED: "false",
        DOUYIN_TEST_ENABLED: "false",
        DOUYIN_REAL_UPSTREAM_MODE: "live",
      },
    });
  });

  it("密钥清单保持 PowerShell safety-check 的既有事实并标注是否必需", () => {
    const realPre = getEnvironmentContract("real-pre", loadEnvironmentContracts());

    expect(realPre.secretKeys.map(({ key }) => key)).toEqual([
      "DB_PASSWORD",
      "REDIS_PASSWORD",
      "JWT_SECRET",
      "DOUYIN_CLIENT_SECRET",
      "LOGISTICS_KD100_KEY",
      "TALENT_PROFILE_HTTP_TOKEN",
      "TALENT_PROFILE_HTTP_AUTHORIZATION",
    ]);
    expect(realPre.secretKeys.filter(({ required }) => required).map(({ key }) => key))
      .toEqual([
        "DB_PASSWORD",
        "REDIS_PASSWORD",
        "JWT_SECRET",
        "DOUYIN_CLIENT_SECRET",
        "LOGISTICS_KD100_KEY",
      ]);
  });

  it("环境契约是有效 JSON 且不包含任何密钥值", () => {
    const raw = readFileSync(
      new URL("../contracts/environments.json", import.meta.url),
      "utf8",
    );

    expect(() => JSON.parse(raw)).not.toThrow();
    expect(raw).not.toContain("MUST_CHANGE_DB_PASSWORD");
    expect(raw).not.toContain("saas123");
  });

  it("拒绝未知环境而不是静默回退", () => {
    expect(() =>
      getEnvironmentContract(
        "production" as "test",
        loadEnvironmentContracts(),
      ),
    ).toThrow(EnvironmentContractError);
  });

  it("拒绝用空数组或空对象关闭 real-pre 安全门禁", () => {
    const contract = loadEnvironmentContracts();
    for (const realPreOverride of [
      { requiredConfigKeys: [] },
      { hardSwitches: {} },
      { secretKeys: [] },
    ]) {
      const invalid = {
        ...contract,
        environments: {
          ...contract.environments,
          "real-pre": { ...contract.environments["real-pre"], ...realPreOverride },
        },
      };
      expect(() => parseEnvironmentContracts(invalid)).toThrow(EnvironmentContractError);
    }
  });

  it("拒绝重复配置键、缺失安全必需项及漂移的工具链基线", () => {
    const contract = loadEnvironmentContracts();
    const duplicateConfig = {
      ...contract,
      environments: {
        ...contract.environments,
        "real-pre": {
          ...contract.environments["real-pre"],
          requiredConfigKeys: ["DB_NAME", "DB_NAME"],
        },
      },
    };
    const missingHardSwitch = {
      ...contract,
      environments: {
        ...contract.environments,
        "real-pre": {
          ...contract.environments["real-pre"],
          hardSwitches: { APP_TEST_ENABLED: "false" },
        },
      },
    };
    const missingOAuthConfig = {
      ...contract,
      environments: {
        ...contract.environments,
        "real-pre": {
          ...contract.environments["real-pre"],
          requiredConfigKeys: contract.environments["real-pre"].requiredConfigKeys
            .filter((key) => key !== "DOUYIN_OAUTH_REDIRECT_URI"),
        },
      },
    };
    const wrongToolchain = {
      ...contract,
      toolchain: { ...contract.toolchain, nodeMajor: 24 },
    };

    expect(() => parseEnvironmentContracts(duplicateConfig)).toThrow(EnvironmentContractError);
    expect(() => parseEnvironmentContracts(missingHardSwitch)).toThrow(EnvironmentContractError);
    expect(() => parseEnvironmentContracts(missingOAuthConfig)).toThrow(EnvironmentContractError);
    expect(() => parseEnvironmentContracts(wrongToolchain)).toThrow(EnvironmentContractError);
  });
});

describe("env 文件解析", () => {
  it("支持空行、注释、KEY=VALUE 与单双引号，且不执行插值", () => {
    const parsed = parseEnvText([
      "",
      "  # 整行注释",
      "PLAIN = value with spaces ",
      'DOUBLE="double value"',
      "SINGLE='single value'",
      "INTERPOLATION=${SHOULD_NOT_EXPAND}",
      "HASH=value#kept",
      "EMPTY=",
    ].join("\n"));

    expect(parsed).toEqual({
      PLAIN: "value with spaces",
      DOUBLE: "double value",
      SINGLE: "single value",
      INTERPOLATION: "${SHOULD_NOT_EXPAND}",
      HASH: "value#kept",
      EMPTY: "",
    });
  });

  it("忽略无等号与空键行，重复键以最后一项为准", () => {
    expect(parseEnvText("BROKEN\n=value\nA=first\nA=second\n")).toEqual({
      A: "second",
    });
  });
});
