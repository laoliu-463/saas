import {
  mkdirSync,
  mkdtempSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { afterEach, describe, expect, it } from "vitest";

import type { ProcessOptions, ProcessResult } from "../../lib/node/core/process-runner.js";
import {
  buildInspectCommandPlan,
  findDangerousCommandReferences,
  isCanonicalSafetyCheckPath,
  runInspect,
  type InspectProcessRunner,
} from "../../lib/node/checks/inspect.js";

const temporaryDirectories: string[] = [];

function temporaryRepository(): string {
  const root = mkdtempSync(join(tmpdir(), "harness-inspect-"));
  temporaryDirectories.push(root);
  for (const directory of ["backend", "frontend", "harness", "scripts"]) {
    mkdirSync(join(root, directory), { recursive: true });
  }
  for (const [path, content] of Object.entries({
    "AGENTS.md": "# 测试仓库\n",
    "backend/pom.xml": "<project />\n",
    "frontend/package.json": "{}\n",
    "docker-compose.real-pre.yml": "name: saas-active\n",
    ".env.real-pre.example": "APP_TEST_ENABLED=false\n",
  })) {
    writeFileSync(join(root, path), content, "utf8");
  }
  return root;
}

function validRealPreEnv(secretSuffix = "fixture"): string {
  return [
    "COMPOSE_PROJECT_NAME=saas-active",
    "SPRING_PROFILES_ACTIVE=real-pre",
    "DB_NAME=saas_real_pre",
    "DB_USER=saas",
    "ADMIN_PASSWORD=admin-fixture",
    `DB_PASSWORD=db-${secretSuffix}`,
    `REDIS_PASSWORD=redis-${secretSuffix}`,
    `JWT_SECRET=jwt-${secretSuffix}`,
    "DOUYIN_APP_ID=app-id",
    "DOUYIN_CLIENT_KEY=client-key",
    `DOUYIN_CLIENT_SECRET=douyin-${secretSuffix}`,
    "DOUYIN_OAUTH_REDIRECT_URI=https://example.test/callback",
    "DOUYIN_OAUTH_FRONTEND_SUCCESS_URL=https://example.test/success",
    "DOUYIN_OAUTH_FRONTEND_FAILURE_URL=https://example.test/failure",
    `LOGISTICS_KD100_KEY=logistics-${secretSuffix}`,
    "TALENT_PROFILE_HTTP_TOKEN=",
    "TALENT_PROFILE_HTTP_AUTHORIZATION=",
    "APP_TEST_ENABLED=false",
    "DOUYIN_TEST_ENABLED=false",
    "DOUYIN_REAL_UPSTREAM_MODE=live",
  ].join("\n");
}

function processResult(stdout: string, success = true): ProcessResult {
  return {
    commandDisplay: "已注入的只读命令",
    exitCode: success ? 0 : 1,
    signal: null,
    timedOut: false,
    durationMs: 1,
    stdout,
    stderr: success ? "" : "工具不可用",
    success,
    rootCause: success ? null : "命令无法启动。",
    safeRetry: success ? null : "安装工具后重试一次。",
    stopCondition: success ? null : "仍失败时停止重试。",
  };
}

function fakeRunner(
  overrides: Readonly<Record<string, ProcessResult>> = {},
  captured: ProcessOptions[] = [],
): InspectProcessRunner {
  const outputs: Readonly<Record<string, ProcessResult>> = {
    "node --version": processResult("v20.19.4"),
    "npm --version": processResult("10.8.2"),
    "java -version": processResult("openjdk version \"17.0.12\""),
    "mvn -version": processResult("Apache Maven 3.9.9"),
    "docker --version": processResult("Docker version 28.0.0"),
    "docker compose version": processResult("Docker Compose version v2.35.0"),
    ...overrides,
  };
  return async (options) => {
    captured.push(options);
    const key = [options.command, ...options.args].join(" ");
    const result = outputs[key];
    if (!result) throw new Error(`测试未配置命令：${key}`);
    return result;
  };
}

afterEach(() => {
  for (const directory of temporaryDirectories.splice(0)) {
    rmSync(directory, { recursive: true, force: true });
  }
});

describe("inspect 只读边界", () => {
  it("命令计划只有固定本地工具探测，完全不调用 Git 或远端命令", () => {
    const plan = buildInspectCommandPlan();
    const displays = plan.map(({ command, args }) => [command, ...args].join(" "));
    const joined = displays.join("\n").toLowerCase();

    expect(displays).toEqual([
      "node --version",
      "npm --version",
      "java -version",
      "mvn -version",
      "docker --version",
      "docker compose version",
    ]);
    for (const forbidden of [
      "git",
      "docker up",
      "docker down",
      "docker build",
      "docker restart",
      "curl",
      "ssh",
      "scp",
    ]) {
      expect(joined).not.toContain(forbidden);
    }
  });

  it("真实执行器注入点只收到计划内命令且不接收 env 文件值", async () => {
    const root = temporaryRepository();
    const secret = "inspect-never-forward-this-secret";
    writeFileSync(join(root, ".env.real-pre"), validRealPreEnv(secret), "utf8");
    const captured: ProcessOptions[] = [];

    const result = await runInspect({
      environment: "real-pre",
      repoRoot: root,
      processRunner: fakeRunner({}, captured),
    });

    expect(result.status).toBe("PASS");
    expect(captured).toEqual(buildInspectCommandPlan().map((item) => ({
      ...item,
      cwd: root,
      timeoutMs: 10_000,
    })));
    expect(JSON.stringify(captured)).not.toContain(secret);
    expect(JSON.stringify(result)).not.toContain(secret);
  });
});

describe("环境、工具链和安全检查", () => {
  it("有效 real-pre 配置与目标工具链形成 PASS", async () => {
    const root = temporaryRepository();
    writeFileSync(join(root, ".env.real-pre"), validRealPreEnv(), "utf8");

    const result = await runInspect({
      environment: "real-pre",
      repoRoot: root,
      processRunner: fakeRunner(),
    });

    expect(result.status).toBe("PASS");
    expect(result.checks.every(({ status }) => status === "PASS" || status === "WARN"))
      .toBe(true);
  });

  it("实际 env 文件缺失时明确 BLOCKED，不能用 example 冒充", async () => {
    const root = temporaryRepository();

    const result = await runInspect({
      environment: "real-pre",
      repoRoot: root,
      processRunner: fakeRunner(),
    });
    const envCheck = result.checks.find(({ checkId }) => checkId === "environment.files");

    expect(result.status).toBe("BLOCKED");
    expect(envCheck?.status).toBe("BLOCKED");
    expect(envCheck?.summary).toContain(".env.real-pre");
    expect(envCheck?.nextActions.join(" ")).toContain("复制");
  });

  it("real-pre 三个硬开关任一不符均失败并给出中文修复指引", async () => {
    const root = temporaryRepository();
    writeFileSync(
      join(root, ".env.real-pre"),
      validRealPreEnv().replace("APP_TEST_ENABLED=false", "APP_TEST_ENABLED=true"),
      "utf8",
    );

    const result = await runInspect({
      environment: "real-pre",
      repoRoot: root,
      processRunner: fakeRunner(),
    });
    const check = result.checks.find(({ checkId }) => checkId === "environment.hard-switches");

    expect(result.status).toBe("FAIL");
    expect(check?.status).toBe("FAIL");
    expect(check?.summary).toContain("APP_TEST_ENABLED");
    expect(check?.nextActions.join(" ")).toContain("false");
  });

  it("密钥只报告存在或缺失，结果中绝不出现值", async () => {
    const root = temporaryRepository();
    const secret = "ultra-distinct-secret-908173";
    writeFileSync(join(root, ".env.real-pre"), validRealPreEnv(secret), "utf8");

    const result = await runInspect({
      environment: "real-pre",
      repoRoot: root,
      processRunner: fakeRunner(),
    });
    const serialized = JSON.stringify(result);
    const check = result.checks.find(({ checkId }) => checkId === "environment.secrets");

    expect(check?.summary).toContain("存在");
    expect(check?.summary).toContain("缺失");
    expect(serialized).not.toContain(secret);
    expect(serialized).not.toContain(`db-${secret}`);
  });

  it("缺少必需配置或必需密钥时失败", async () => {
    const root = temporaryRepository();
    writeFileSync(
      join(root, ".env.real-pre"),
      validRealPreEnv()
        .replace("DB_NAME=saas_real_pre\n", "")
        .replace("JWT_SECRET=jwt-fixture", "JWT_SECRET="),
      "utf8",
    );

    const result = await runInspect({
      environment: "real-pre",
      repoRoot: root,
      processRunner: fakeRunner(),
    });

    expect(result.status).toBe("FAIL");
    expect(result.checks.find(({ checkId }) => checkId === "environment.required-config")?.summary)
      .toContain("DB_NAME");
    expect(result.checks.find(({ checkId }) => checkId === "environment.secrets")?.summary)
      .toContain("JWT_SECRET");
  });

  it("real-pre 缺少 Compose 无默认值的 ADMIN_PASSWORD 时失败且不输出值", async () => {
    const root = temporaryRepository();
    writeFileSync(
      join(root, ".env.real-pre"),
      validRealPreEnv().replace("ADMIN_PASSWORD=admin-fixture\n", ""),
      "utf8",
    );

    const result = await runInspect({
      environment: "real-pre",
      repoRoot: root,
      processRunner: fakeRunner(),
    });

    expect(result.status).toBe("FAIL");
    expect(result.checks.find(({ checkId }) => checkId === "environment.required-config")?.summary)
      .toContain("ADMIN_PASSWORD");
    expect(JSON.stringify(result)).not.toContain("admin-fixture");
  });

  it("Node 非 20 与 Java 非 17 明确告警并形成 PARTIAL", async () => {
    const root = temporaryRepository();
    writeFileSync(join(root, ".env.real-pre"), validRealPreEnv(), "utf8");

    const result = await runInspect({
      environment: "real-pre",
      repoRoot: root,
      processRunner: fakeRunner({
        "node --version": processResult("v24.4.1"),
        "java -version": processResult("openjdk version \"21.0.1\""),
      }),
    });

    expect(result.status).toBe("PARTIAL");
    expect(result.checks.find(({ checkId }) => checkId === "toolchain.node")?.status)
      .toBe("WARN");
    expect(result.checks.find(({ checkId }) => checkId === "toolchain.java")?.status)
      .toBe("WARN");
    expect(result.checks.find(({ checkId }) => checkId === "toolchain.node")?.summary)
      .toContain("Node.js 20");
  });

  it("工具缺失时返回 BLOCKED 并保留结构化恢复建议", async () => {
    const root = temporaryRepository();
    writeFileSync(join(root, ".env.real-pre"), validRealPreEnv(), "utf8");

    const result = await runInspect({
      environment: "real-pre",
      repoRoot: root,
      processRunner: fakeRunner({
        "docker --version": processResult("", false),
      }),
    });

    expect(result.status).toBe("BLOCKED");
    expect(result.checks.find(({ checkId }) => checkId === "toolchain.docker")?.status)
      .toBe("BLOCKED");
  });

  it("敏感变更检查明确委托给 agent-do，Node inspect 不执行 Git", async () => {
    const root = temporaryRepository();
    writeFileSync(join(root, ".env.real-pre"), validRealPreEnv(), "utf8");
    const captured: ProcessOptions[] = [];

    const result = await runInspect({
      environment: "real-pre",
      repoRoot: root,
      processRunner: fakeRunner({}, captured),
    });

    expect(captured.some(({ command }) => command.toLowerCase() === "git")).toBe(false);
    expect(result.checks.find(({ checkId }) => checkId === "source-control.boundary"))
      .toMatchObject({ status: "PASS", blocking: true });
  });
});

describe("危险命令引用扫描", () => {
  it.each([
    "docker compose down -v",
    "docker compose down --volumes",
    "docker volume rm saas-active_postgres_real_pre_data",
    "docker volume prune -f",
    "Remove-Item postgres_real_pre_volume -Recurse",
    "Remove-Item redis_data_volume -Force",
    "DROP DATABASE saas_real_pre",
  ])("识别既有 safety-check 规则：%s", (command) => {
    const root = temporaryRepository();
    writeFileSync(join(root, "scripts", "danger.ps1"), command, "utf8");

    expect(findDangerousCommandReferences(root)).toEqual([
      expect.objectContaining({ path: "scripts/danger.ps1", line: 1 }),
    ]);
  });

  it("排除 safety-check.ps1 自身，避免规则定义自命中", () => {
    const root = temporaryRepository();
    mkdirSync(join(root, "harness", "scripts", "commands"), { recursive: true });
    writeFileSync(
      join(root, "harness", "scripts", "commands", "safety-check.ps1"),
      "docker compose down -v\nDROP DATABASE demo",
      "utf8",
    );

    expect(findDangerousCommandReferences(root)).toEqual([]);
  });

  it("不允许用其他目录下的同名 safety-check.ps1 绕过扫描", () => {
    const root = temporaryRepository();
    writeFileSync(join(root, "scripts", "safety-check.ps1"), "docker compose down -v", "utf8");

    expect(findDangerousCommandReferences(root)).toEqual([
      expect.objectContaining({ path: "scripts/safety-check.ps1", line: 1 }),
    ]);
  });

  it("Linux 仅排除大小写完全一致的规范 safety-check 路径", () => {
    const root = temporaryRepository();
    const canonical = join(root, "harness", "scripts", "commands", "safety-check.ps1");
    const caseVariant = join(root, "harness", "scripts", "commands", "SAFETY-CHECK.ps1");

    expect(isCanonicalSafetyCheckPath(root, canonical, "linux")).toBe(true);
    expect(isCanonicalSafetyCheckPath(root, caseVariant, "linux")).toBe(false);
    expect(isCanonicalSafetyCheckPath(root, caseVariant, "win32")).toBe(true);
  });

  it("识别 PowerShell 反引号续行拆分的破坏性命令", () => {
    const root = temporaryRepository();
    writeFileSync(
      join(root, "scripts", "continued.ps1"),
      "docker compose `\n  down `\n  --volumes\n",
      "utf8",
    );

    expect(findDangerousCommandReferences(root)).toEqual([
      expect.objectContaining({ path: "scripts/continued.ps1", line: 1 }),
    ]);
  });
});
