import { describe, expect, it } from "vitest";

import {
  createRunContext,
  toRepositoryRelativePath,
  validateReportKey,
} from "../src/core/run-context.js";
import type { GitSnapshot } from "../src/core/git.js";

const COMMIT_SNAPSHOT: GitSnapshot = {
  headSha: "a".repeat(40),
  branch: "codex/evidence",
  clean: true,
  changedFiles: [],
  identity: { kind: "COMMIT", commitSha: "a".repeat(40) },
};

describe("运行上下文", () => {
  it("使用可注入 runId 和时间生成确定性中文时区及证据路径", () => {
    const context = createRunContext({
      repoRoot: "C:\\repo with space\\SAAS",
      environment: "real-pre",
      scope: "full",
      reportKey: "task-5-evidence",
      startedAt: new Date("2026-07-18T04:05:06.789Z"),
      runId: "run-20260718-001",
      gitSnapshot: COMMIT_SNAPSHOT,
    });

    expect(context.startedAt).toBe("2026-07-18T04:05:06.789Z");
    expect(context.startedAtShanghai).toBe("2026-07-18 12:05:06 Asia/Shanghai");
    expect(context.rawDir).toBe("runtime/qa/out/run-20260718-001");
    expect(context.stableJsonPath).toBe(
      "harness/reports/current/latest-task-5-evidence.json",
    );
    expect(context.stableMarkdownPath).toBe(
      "harness/reports/current/latest-task-5-evidence.md",
    );
    expect(context.git).toEqual(COMMIT_SNAPSHOT);
  });

  it("默认 runId 在相同时间输入下仍保持唯一", () => {
    const input = {
      repoRoot: "C:\\repo",
      environment: "test" as const,
      scope: "backend" as const,
      reportKey: "unique-run",
      startedAt: new Date("2026-07-18T04:05:06.789Z"),
      gitSnapshot: COMMIT_SNAPSHOT,
    };

    expect(createRunContext(input).runId).not.toBe(createRunContext(input).runId);
  });

  it("未注入快照时如实标记 Git 未采集且不自行调用 Git", () => {
    const context = createRunContext({
      repoRoot: "C:\\repo",
      environment: "test",
      scope: "backend",
      reportKey: "no-git-boundary",
    });

    expect(context.git).toEqual({
      headSha: null,
      branch: null,
      clean: null,
      changedFiles: [],
      identity: { kind: "UNAVAILABLE", reason: "NODE_GIT_BOUNDARY" },
    });
  });

  it.each([
    "",
    "Task-5",
    "task_5",
    "../task",
    "task/5",
    "task\\5",
    "C:\\task",
    "/tmp/task",
    "task..five",
    "a".repeat(65),
  ])("在写文件前拒绝非稳定 ReportKey：%s", (reportKey) => {
    expect(() => validateReportKey(reportKey)).toThrowError(/报告键/);
  });

  it("支持 Windows 和 POSIX 绝对路径转换为仓库相对正斜杠路径", () => {
    expect(
      toRepositoryRelativePath(
        "C:\\Users\\cao\\repo",
        "C:\\Users\\cao\\repo\\runtime\\日志.txt",
      ),
    ).toBe("runtime/日志.txt");
    expect(
      toRepositoryRelativePath("/srv/repo", "/srv/repo/harness/out.json"),
    ).toBe("harness/out.json");
    expect(
      toRepositoryRelativePath("C:\\Users\\cao\\repo", "harness\\out.json"),
    ).toBe("harness/out.json");
  });

  it("拒绝仓库外绝对路径和路径穿越，不回显用户目录", () => {
    for (const candidate of [
      "C:\\Users\\cao\\outside\\secret.log",
      "/srv/outside/secret.log",
      "../outside.log",
    ]) {
      try {
        toRepositoryRelativePath(
          candidate.startsWith("/") ? "/srv/repo" : "C:\\Users\\cao\\repo",
          candidate,
        );
        throw new Error("未拒绝仓库外路径");
      } catch (error) {
        expect(String(error)).toContain("仓库内");
        expect(String(error)).not.toContain("C:\\Users\\cao");
      }
    }
  });

  it("仓库路径前缀相似时仍拒绝 repo2 逃逸", () => {
    expect(() =>
      toRepositoryRelativePath(
        "C:\\workspace\\repo",
        "C:\\workspace\\repo2\\outside.log",
      ),
    ).toThrowError(/仓库内/);
    expect(() =>
      toRepositoryRelativePath("/workspace/repo", "/workspace/repo2/outside.log"),
    ).toThrowError(/仓库内/);
  });
});
