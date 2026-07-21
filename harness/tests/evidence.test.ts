import {
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  readdirSync,
  rmSync,
  symlinkSync,
  writeFileSync,
} from "node:fs";
import { dirname, join, win32 } from "node:path";
import { tmpdir } from "node:os";

import { Ajv2020 } from "ajv/dist/2020.js";
import { afterEach, describe, expect, it } from "vitest";

import {
  EvidenceWriteError,
  createEvidenceReport,
  escapeMarkdownTableText,
  renderMarkdownCodeSpan,
  validateEvidenceReport,
  writeEvidence,
} from "../src/core/evidence.js";
import { createRunContext } from "../src/core/run-context.js";
import { createCheckResult, createRunResult } from "../src/core/result.js";
import type { GitSnapshot } from "../src/core/git.js";

const roots: string[] = [];
const SECRET = "s3cr3t-token-value";

function worktreeSnapshot(): GitSnapshot {
  return {
    headSha: "b".repeat(40),
    branch: `codex/${SECRET}`,
    clean: false,
    changedFiles: [`src/${SECRET}.ts`],
    identity: {
      kind: "WORKTREE",
      headSha: "b".repeat(40),
      changedFiles: [`src/${SECRET}.ts`],
      patchFingerprint: `sha256:${"c".repeat(64)}`,
    },
  };
}

function makeRoot(): string {
  const root = mkdtempSync(join(tmpdir(), "harness-evidence-"));
  roots.push(root);
  return root;
}

function makeRunResult(root: string) {
  return createRunResult([
    createCheckResult({
      checkId: "evidence.write",
      title: "写入验证证据",
      status: "PASS",
      blocking: true,
      summary: `验证通过，token=${SECRET}。`,
      nextActions: [`无需操作，password=${SECRET}。`],
      artifacts: [join(root, "runtime", "qa", "out", SECRET, "check.log")],
    }),
  ]);
}

function makeContext(root: string) {
  return createRunContext({
    repoRoot: root,
    environment: "test",
    scope: "backend",
    reportKey: "task-5-evidence",
    startedAt: new Date("2026-07-18T04:05:06.000Z"),
    runId: "run-task-5-001",
    gitSnapshot: worktreeSnapshot(),
  });
}

function allFiles(root: string): string[] {
  const result: string[] = [];
  function visit(directory: string): void {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const absolute = join(directory, entry.name);
      if (entry.isDirectory()) visit(absolute);
      else result.push(absolute);
    }
  }
  visit(root);
  return result;
}

function readEvidenceFiles(root: string, report: ReturnType<typeof createEvidenceReport>) {
  return {
    raw: readFileSync(join(root, ...report.evidencePaths.rawJson.split("/")), "utf8"),
    json: readFileSync(join(root, ...report.evidencePaths.stableJson.split("/")), "utf8"),
    markdown: readFileSync(
      join(root, ...report.evidencePaths.stableMarkdown.split("/")),
      "utf8",
    ),
  };
}

function reportAt(root: string, finishedAt: string) {
  return createEvidenceReport(makeContext(root), makeRunResult(root), {
    finishedAt: new Date(finishedAt),
    secrets: [SECRET],
  });
}

function createDirectoryLink(target: string, link: string): boolean {
  try {
    symlinkSync(target, link, process.platform === "win32" ? "junction" : "dir");
    return true;
  } catch (error) {
    if (["EPERM", "EACCES", "UNKNOWN"].includes((error as NodeJS.ErrnoException).code ?? "")) {
      return false;
    }
    throw error;
  }
}

function emptyDirectory(path: string): boolean {
  return !existsSync(path) || readdirSync(path).length === 0;
}

function identityReport(
  root: string,
  field: "runId" | "reportKey" | "checkId",
  secret: string,
) {
  const snapshot: GitSnapshot = {
    headSha: "d".repeat(40),
    branch: "main",
    clean: true,
    changedFiles: [],
    identity: { kind: "COMMIT", commitSha: "d".repeat(40) },
  };
  const result = createRunResult([
    createCheckResult({
      checkId: field === "checkId" ? secret : "evidence.identity",
      title: "验证身份字段",
      status: "PASS",
      blocking: true,
      summary: "身份字段检查通过。",
      nextActions: [],
      artifacts: [],
    }),
  ]);
  const context = createRunContext({
    repoRoot: root,
    environment: "test",
    scope: "backend",
    reportKey: field === "reportKey" ? secret : "identity-report",
    runId: field === "runId" ? secret : "identity-run-001",
    startedAt: new Date("2026-07-18T04:05:06.000Z"),
    gitSnapshot: snapshot,
  });
  return createEvidenceReport(context, result, {
    finishedAt: new Date("2026-07-18T04:05:08.000Z"),
  });
}

afterEach(() => {
  while (roots.length > 0) rmSync(roots.pop()!, { recursive: true, force: true });
});

describe("结构化证据", () => {
  it("使用同一 RunContext 和 RunResult 生成 Schema 合法的中文报告", () => {
    const root = makeRoot();
    const report = createEvidenceReport(makeContext(root), makeRunResult(root), {
      finishedAt: new Date("2026-07-18T04:05:08.250Z"),
      secrets: [SECRET],
    });

    expect(report.durationMs).toBe(2250);
    expect(report.finishedAtShanghai).toBe("2026-07-18 12:05:08 Asia/Shanghai");
    expect(report.result.status).toBe("PASS");
    expect(report.result.statusLabel).toBe("通过");
    expect(report.git.identity.kind).toBe("WORKTREE");
    expect(validateEvidenceReport(report)).toEqual({ valid: true });
    expect(JSON.stringify(report)).not.toContain(SECRET);
    expect(JSON.stringify(report)).not.toContain(root);
  });

  it("严格拒绝缺字段、非法 identity 分支、非法路径和非法状态", () => {
    const root = makeRoot();
    const report = createEvidenceReport(makeContext(root), makeRunResult(root), {
      finishedAt: new Date("2026-07-18T04:05:08.250Z"),
      secrets: [SECRET],
    });

    expect(validateEvidenceReport({ ...report, runId: undefined }).valid).toBe(false);
    expect(
      validateEvidenceReport({
        ...report,
        git: {
          ...report.git,
          clean: true,
          identity: report.git.identity,
        },
      }).valid,
    ).toBe(false);
    expect(
      validateEvidenceReport({
        ...report,
        evidencePaths: { ...report.evidencePaths, rawJson: "../run.json" },
      }).valid,
    ).toBe(false);
    expect(
      validateEvidenceReport({
        ...report,
        result: { ...report.result, status: "UNKNOWN" },
      }).valid,
    ).toBe(false);
  });

  it("Git 未采集身份只能形成非 PASS 证据", () => {
    const root = makeRoot();
    const context = createRunContext({
      repoRoot: root,
      environment: "test",
      scope: "backend",
      reportKey: "no-git-evidence",
      runId: "run-no-git-001",
      startedAt: new Date("2026-07-18T04:05:06.000Z"),
    });
    const partial = createRunResult([createCheckResult({
      checkId: "evidence.git-identity",
      title: "检查 Git 证据身份",
      status: "WARN",
      blocking: true,
      summary: "Node 不调用 Git，本次身份未采集。",
      nextActions: ["通过 agent-do 注入快照。"],
      artifacts: [],
    })]);
    const report = createEvidenceReport(context, partial, {
      finishedAt: new Date("2026-07-18T04:05:08.000Z"),
    });

    expect(report.git.identity.kind).toBe("UNAVAILABLE");
    expect(report.result.status).toBe("PARTIAL");
    expect(validateEvidenceReport(report)).toEqual({ valid: true });
    expect(validateEvidenceReport({
      ...report,
      result: createRunResult([createCheckResult({
        checkId: "evidence.invalid-pass",
        title: "拒绝伪造通过",
        status: "PASS",
        blocking: true,
        summary: "未采集身份却伪造通过。",
        nextActions: [],
        artifacts: [],
      })]),
    }).valid).toBe(false);
  });

  it("evidence Schema 直接引用 RunResult 而不重复状态定义", () => {
    const evidenceSchema = JSON.parse(
      readFileSync(
        new URL("../contracts/evidence-report.schema.json", import.meta.url),
        "utf8",
      ),
    ) as { properties: { result: { $ref: string } } };
    const runSchema = JSON.parse(
      readFileSync(new URL("../contracts/run-result.schema.json", import.meta.url), "utf8"),
    ) as { $id: string };
    const checkSchema = JSON.parse(
      readFileSync(
        new URL("../contracts/check-result.schema.json", import.meta.url),
        "utf8",
      ),
    ) as { $id: string };
    const ajv = new Ajv2020({ allErrors: true, strict: true });
    ajv.addSchema(checkSchema);
    ajv.addSchema(runSchema);
    expect(() => ajv.compile(evidenceSchema)).not.toThrow();
    expect(evidenceSchema.properties.result.$ref).toBe(runSchema.$id);
  });

  it("原始 JSON、稳定 JSON 与 Markdown 由同一对象生成且不落盘密钥或绝对路径", () => {
    const root = makeRoot();
    const context = makeContext(root);
    const report = createEvidenceReport(context, makeRunResult(root), {
      finishedAt: new Date("2026-07-18T04:05:08.250Z"),
      secrets: [SECRET],
    });

    const written = writeEvidence(report, {
      repoRoot: root,
      secrets: [SECRET],
    });
    const rawJson = readFileSync(join(root, ...written.rawJson.split("/")), "utf8");
    const stableJson = readFileSync(
      join(root, ...written.stableJson.split("/")),
      "utf8",
    );
    const markdown = readFileSync(
      join(root, ...written.stableMarkdown.split("/")),
      "utf8",
    );
    const parsed = JSON.parse(stableJson) as typeof report;

    expect(rawJson).toBe(stableJson);
    expect(stableJson.trimEnd().split(/\r?\n/u)).toHaveLength(1);
    expect(parsed.runId).toBe(report.runId);
    expect(parsed.git.headSha).toBe(report.git.headSha);
    expect(parsed.environment).toBe(report.environment);
    expect(parsed.scope).toBe(report.scope);
    expect(parsed.result.status).toBe(report.result.status);
    for (const content of [rawJson, stableJson, markdown]) {
      expect(content).not.toContain(SECRET);
      expect(content).not.toContain(root);
      expect(content).not.toMatch(/[A-Z]:\\Users\\/u);
    }
    expect(markdown).toContain("运行结论：通过（PASS）");
    expect(markdown).toContain(report.runId);
    expect(markdown).toContain(report.git.headSha);
    expect(markdown).toContain(`\` ${report.environment} \``);
    expect(markdown).toContain(`\` ${report.scope} \``);
    expect(stableJson).not.toContain("diff");
    expect(stableJson).not.toContain("patch\"");
  });

  it("JSON 保留结构化文本而 Markdown 安全展示恶意字段", () => {
    const root = makeRoot();
    const branch = " branch `` | <b>分支</b> [link](url)\n下一行 ";
    const changedFile = "src/changed``[link](url).ts";
    const title = "检查`` | <img src=x onerror=alert(1)> [link](url)\n标题";
    const summary = "摘要`` | <script>alert(1)</script> [link](url)\n下一行。";
    const action = "动作`` | <a href=url>link</a> [link](url)\n下一行。";
    const baseSnapshot = worktreeSnapshot();
    if (baseSnapshot.identity.kind !== "WORKTREE") {
      throw new Error("测试夹具必须是 WORKTREE 身份");
    }
    const snapshot: GitSnapshot = {
      ...baseSnapshot,
      branch,
      changedFiles: [changedFile],
      identity: {
        ...baseSnapshot.identity,
        changedFiles: [changedFile],
      },
    };
    const context = createRunContext({
      repoRoot: root,
      environment: "test",
      scope: "backend",
      reportKey: "markdown-safety",
      runId: "run-markdown-safety-001",
      startedAt: new Date("2026-07-18T04:05:06.000Z"),
      gitSnapshot: snapshot,
    });
    const result = createRunResult([
      createCheckResult({
        checkId: "evidence.markdown-safety",
        title,
        status: "PASS",
        blocking: true,
        summary,
        nextActions: [action],
        artifacts: [],
      }),
    ]);
    const report = createEvidenceReport(context, result, {
      finishedAt: new Date("2026-07-18T04:05:08.250Z"),
    });
    const outcome = writeEvidence(report, { repoRoot: root });
    const persisted = JSON.parse(
      readFileSync(join(root, ...outcome.stableJson.split("/")), "utf8"),
    ) as typeof report;
    const markdown = readFileSync(
      join(root, ...outcome.stableMarkdown.split("/")),
      "utf8",
    );

    expect(persisted.git.branch).toBe(branch);
    expect(persisted.git.changedFiles).toEqual([changedFile]);
    expect(persisted.result.checks[0]).toMatchObject({
      title,
      summary,
      nextActions: [action],
    });
    expect(markdown).toContain(renderMarkdownCodeSpan(branch));
    expect(markdown).toContain(renderMarkdownCodeSpan(changedFile));
    const checkRow = markdown.split("\n")
      .find((line) => line.startsWith("|") && line.includes("onerror"));
    const actionRow = markdown.split("\n")
      .find((line) => line.startsWith("|") && line.includes("href"));
    expect(checkRow).toBeDefined();
    expect(actionRow).toBeDefined();
    for (const row of [checkRow!, actionRow!]) {
      expect(row).not.toMatch(/<\/?(?:img|script|a|b)\b/iu);
      expect(row).not.toContain("[link](url)");
      expect(row).not.toContain("\n");
    }
    expect(checkRow!.split("|")).toHaveLength(6);
    expect(actionRow!.split("|")).toHaveLength(4);
    expect(markdown).toContain("## 后续操作");
  });

  it("顶层运行摘要保持 JSON 原值但在 Markdown 中按安全段落展示", async () => {
    const root = makeRoot();
    const summary = "运行摘要 <script>alert(1)</script> [link](url) ``\n# 下一行。";
    const baseResult = makeRunResult(root);
    const report = createEvidenceReport(
      makeContext(root),
      { ...baseResult, summary },
      { finishedAt: new Date("2026-07-18T04:05:08.250Z"), secrets: [SECRET] },
    );
    const outcome = writeEvidence(report, { repoRoot: root, secrets: [SECRET] });
    const parsed = JSON.parse(
      readFileSync(join(root, ...outcome.stableJson.split("/")), "utf8"),
    ) as typeof report;
    const markdown = readFileSync(
      join(root, ...outcome.stableMarkdown.split("/")),
      "utf8",
    );
    const summarySection = markdown
      .split("## 结果摘要\n\n")[1]!
      .split("\n\n## 检查结果")[0]!;

    expect(parsed.result.summary).toBe(summary);
    expect(summarySection).toContain("运行摘要");
    expect(summarySection).not.toMatch(/<\/?script\b/iu);
    expect(summarySection).not.toContain("[link](url)");
    expect(summarySection).not.toContain("\n# 下一行");
    const module = await import("../src/core/evidence.js");
    expect(typeof (module as unknown as { escapeMarkdownParagraphText?: unknown })
      .escapeMarkdownParagraphText).toBe("function");
  });

  it("纯文本表格转义与动态 code span 处理反引号、边界空格和管道", () => {
    expect(escapeMarkdownTableText("<b>|[link](url)\n`_*!"))
      .toBe("&lt;b&gt;&#124;&#91;link&#93;&#40;url&#41; &#96;&#95;&#42;&#33;");
    expect(renderMarkdownCodeSpan(" edge `` ticks|tail "))
      .toBe("```  edge `` ticks\\|tail  ```");
    expect(renderMarkdownCodeSpan("a```b")).toBe("```` a```b ````");
  });

  it("在构造报告时拒绝仓库外 artifact 绝对路径", () => {
    const root = makeRoot();
    const result = createRunResult([
      createCheckResult({
        checkId: "evidence.outside",
        title: "拒绝外部证据",
        status: "FAIL",
        blocking: true,
        summary: "证据路径越界。",
        nextActions: ["将证据放回仓库运行目录。"],
        artifacts: [join(root, "..", "outside.log")],
      }),
    ]);

    expect(() =>
      createEvidenceReport(makeContext(root), result, {
        finishedAt: new Date("2026-07-18T04:05:08.250Z"),
      }),
    ).toThrowError(/仓库内/);
  });

  it("写入失败时返回中文恢复边界且清理临时文件", () => {
    const root = makeRoot();
    writeFileSync(join(root, "runtime"), "block-directory", "utf8");
    const report = createEvidenceReport(makeContext(root), makeRunResult(root), {
      finishedAt: new Date("2026-07-18T04:05:08.250Z"),
      secrets: [SECRET],
    });

    try {
      writeEvidence(report, { repoRoot: root, secrets: [SECRET] });
      throw new Error("写入本应失败");
    } catch (error) {
      expect(error).toBeInstanceOf(EvidenceWriteError);
      const evidenceError = error as EvidenceWriteError;
      expect(evidenceError.rootCause).toMatch(/证据写入失败/);
      expect(evidenceError.safeRetry).toMatch(/确认/);
      expect(evidenceError.stopCondition).toMatch(/停止/);
    }
    expect(allFiles(root).filter((file) => file.includes(".tmp-"))).toEqual([]);
  });

  it("窄 failpoint 只能在指定内部阶段和目标索引使真实写入失败", () => {
    const root = makeRoot();
    const report = reportAt(root, "2026-07-18T04:05:08.250Z");

    expect(() =>
      writeEvidence(report, {
        repoRoot: root,
        secrets: [SECRET],
        failpoints: [{ stage: "WRITE_TEMP", targetIndex: 0 }],
      }),
    ).toThrowError(EvidenceWriteError);
    expect(allFiles(root)).toEqual([]);
  });

  describe("三文件事务", () => {
    it.each([2, 3])("第 %i 次安装最终文件失败时完整恢复三份旧证据", (failureAt) => {
      const root = makeRoot();
      const oldReport = reportAt(root, "2026-07-18T04:05:08.250Z");
      writeEvidence(oldReport, { repoRoot: root, secrets: [SECRET] });
      const oldFiles = readEvidenceFiles(root, oldReport);
      expect(() =>
        writeEvidence(reportAt(root, "2026-07-18T04:05:10.250Z"), {
          repoRoot: root,
          secrets: [SECRET],
          failpoints: [{ stage: "INSTALL_FINAL", targetIndex: failureAt - 1 }],
        }),
      ).toThrowError(EvidenceWriteError);
      expect(readEvidenceFiles(root, oldReport)).toEqual(oldFiles);
      expect(
        allFiles(root).filter((file) => file.includes(".tmp-") || file.includes(".bak-")),
      ).toEqual([]);
    });

    it("备份清理失败时保留三份一致的新证据并返回清理警告", () => {
      const root = makeRoot();
      const oldReport = reportAt(root, "2026-07-18T04:05:08.250Z");
      writeEvidence(oldReport, { repoRoot: root, secrets: [SECRET] });
      const newReport = reportAt(root, "2026-07-18T04:05:10.250Z");
      const outcome = writeEvidence(newReport, {
        repoRoot: root,
        secrets: [SECRET],
        failpoints: [{ stage: "CLEANUP_BACKUP", targetIndex: 0 }],
      });
      const files = readEvidenceFiles(root, newReport);
      expect(files.raw).toBe(files.json);
      expect(files.json).toContain(newReport.finishedAt);
      expect(files.markdown).toContain(newReport.finishedAt);
      expect(outcome.cleanupWarnings).toHaveLength(1);
      expect(allFiles(root).some((file) => file.includes(".bak-"))).toBe(true);
      expect(allFiles(root).some((file) => file.includes(".tmp-"))).toBe(false);
    });

    it("回滚单点失败不会中断其余恢复，并要求人工检查", () => {
      const root = makeRoot();
      const oldReport = reportAt(root, "2026-07-18T04:05:08.250Z");
      writeEvidence(oldReport, { repoRoot: root, secrets: [SECRET] });
      try {
        writeEvidence(reportAt(root, "2026-07-18T04:05:10.250Z"), {
          repoRoot: root,
          secrets: [SECRET],
          failpoints: [
            { stage: "INSTALL_FINAL", targetIndex: 1 },
            { stage: "ROLLBACK_RESTORE_BACKUP", targetIndex: 0 },
          ],
        });
        throw new Error("写入本应失败");
      } catch (error) {
        expect(error).toBeInstanceOf(EvidenceWriteError);
        expect((error as EvidenceWriteError).stopCondition).toContain("人工检查");
      }
      const leftovers = allFiles(root);
      expect(leftovers.filter((file) => file.includes(".bak-"))).toHaveLength(1);
      expect(allFiles(root).some((file) => file.includes(".tmp-"))).toBe(false);
    });
  });

  describe("canonical 路径边界", () => {
    it("按 path.win32 语义拒绝跨盘和相邻前缀路径", async () => {
      const module = await import("../src/core/evidence.js");
      const containment = (module as unknown as {
        isEvidencePathInside?: (
          root: string,
          candidate: string,
          semantics: Pick<typeof win32, "relative" | "isAbsolute">,
        ) => boolean;
      }).isEvidencePathInside;

      expect(typeof containment).toBe("function");
      if (!containment) return;
      expect(containment("D:\\repo", "C:\\outside", win32)).toBe(false);
      expect(containment("D:\\repo", "D:\\repo2\\outside", win32)).toBe(false);
      expect(containment("D:\\repo", "D:\\repo\\inside", win32)).toBe(true);
      expect(containment("D:\\Repo", "d:\\repo\\inside", win32)).toBe(true);
    });

    it("调用者伪造 fileOps 时仍固定使用真实安全检查和真实 I/O", () => {
      const root = makeRoot();
      const report = reportAt(root, "2026-07-18T04:05:08.250Z");
      let accessed = false;
      const options = {
        repoRoot: root,
        secrets: [SECRET],
        get fileOps() {
          accessed = true;
          throw new Error("调用者不应控制证据 I/O");
        },
      } as unknown as Parameters<typeof writeEvidence>[1];

      const outcome = writeEvidence(report, options);
      expect(accessed).toBe(false);
      expect(readFileSync(join(root, ...outcome.rawJson.split("/")), "utf8"))
        .toContain(report.finishedAt);
    });

    for (const target of ["runtime", "reports"] as const) {
      it(
        `${target} 证据父目录链接到仓库外时写前拒绝且外部目录无文件`,
        (context) => {
          const root = makeRoot();
          const external = makeRoot();
          const link = target === "runtime"
            ? join(root, "runtime")
            : join(root, "harness", "reports", "current");
          mkdirSync(dirname(link), { recursive: true });
          if (!createDirectoryLink(external, link)) context.skip();

          expect(() =>
            writeEvidence(reportAt(root, "2026-07-18T04:05:08.250Z"), {
              repoRoot: root,
              secrets: [SECRET],
            }),
          ).toThrowError(/链接|仓库外|canonical/);
          expect(emptyDirectory(external)).toBe(true);
        },
      );
    }

    it("最终文件本身是链接时拒绝替换", (context) => {
      const root = makeRoot();
      const external = makeRoot();
      const report = reportAt(root, "2026-07-18T04:05:08.250Z");
      const finalPath = join(root, ...report.evidencePaths.stableJson.split("/"));
      mkdirSync(dirname(finalPath), { recursive: true });
      if (!createDirectoryLink(external, finalPath)) context.skip();

      expect(() => writeEvidence(report, { repoRoot: root, secrets: [SECRET] }))
        .toThrowError(/链接|仓库外|canonical/);
      expect(emptyDirectory(external)).toBe(true);
    });

    it("artifact 的现存父目录链接到仓库外时在构造报告阶段拒绝", (context) => {
      const root = makeRoot();
      const external = makeRoot();
      const artifactParent = join(root, "artifact-link");
      if (!createDirectoryLink(external, artifactParent)) context.skip();
      const result = createRunResult([
        createCheckResult({
          checkId: "evidence.linked-artifact",
          title: "拒绝链接证据路径",
          status: "FAIL",
          blocking: true,
          summary: "证据父目录链接到仓库外。",
          nextActions: ["改用仓库内真实目录。"],
          artifacts: [join(artifactParent, "outside.log")],
        }),
      ]);

      expect(() =>
        createEvidenceReport(makeContext(root), result, {
          finishedAt: new Date("2026-07-18T04:05:08.250Z"),
        }),
      ).toThrowError(/链接|仓库外|canonical/);
      expect(emptyDirectory(external)).toBe(true);
    });
  });

  describe("metadata secret 零落盘", () => {
    it.each([
      ["runId", "identity-secret-001"],
      ["reportKey", "identity-secret"],
      ["checkId", "identity.secret"],
    ] as const)("%s 命中显式 secret 时在任何文件操作前拒绝", (field, secret) => {
      const root = makeRoot();
      const report = identityReport(root, field, secret);

      expect(() => writeEvidence(report, { repoRoot: root, secrets: [secret] }))
        .toThrowError(/身份字段|敏感信息|secret/);
      expect(readdirSync(root)).toEqual([]);
    });

    it("自由文本中含引号和反斜杠的 secret 仍脱敏且不保存转义等价形式", () => {
      const root = makeRoot();
      const secret = "quote\"slash\\token";
      const result = createRunResult([
        createCheckResult({
          checkId: "evidence.escaped-secret",
          title: "验证复杂密钥脱敏",
          status: "PASS",
          blocking: true,
          summary: `验证通过，token=\"${secret}\"。`,
          nextActions: [`不要输出 ${secret}。`],
          artifacts: [],
        }),
      ]);
      const report = createEvidenceReport(makeContext(root), result, {
        finishedAt: new Date("2026-07-18T04:05:08.250Z"),
        secrets: [secret],
      });

      writeEvidence(report, { repoRoot: root, secrets: [secret] });
      const persisted = Object.values(readEvidenceFiles(root, report)).join("\n");
      expect(persisted).not.toContain(secret);
      expect(persisted).not.toContain(JSON.stringify(secret).slice(1, -1));
    });
  });
});
