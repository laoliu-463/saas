# PLAN-007 Harness 跨平台验证核心实施计划

> 关联设计：`PLAN-006-Harness跨平台验证核心重构设计.md`；关联 Issue：GitHub #165。

## 1. 执行原则

- 分支：`codex/harness-node-verify-phase1`，只在隔离 worktree 实施。
- 方法：每项行为先写失败测试并确认失败原因，再写最小实现。
- 入口：Node 接管 `backend/frontend/full`；`docs/apifox` 保持现有路径。
- 安全：Node 不提交、不推送、不部署；远端部署不在本计划内。
- 提交：每项任务一个独立提交，任何提交都必须保持既有 Harness 可用。
- 证据：原始日志进入 `runtime/qa/out/<runId>/`，稳定摘要进入 `reports/current/`。

## 2. Task 1：修复旧 Harness 收尾基线

**文件**：`harness/scripts/tests/report-lifecycle.Tests.ps1`、`harness/scripts/commands/git-push-safe.ps1`、`harness/scripts/commands/collect-evidence.ps1`。

1. RED：新增本地 bare remote 场景，证明无 upstream 新分支首次推送当前失败；新增 docs Scope 不得采集 Docker 表格测试。
2. 运行定向 Pester，确认分别因 upstream stderr 和 runtime collection 失败。
3. GREEN：使 upstream 探测返回普通状态；docs/apifox 自动跳过运行时采集；外部输出统一清理行尾空白。
4. 运行定向及全部 Pester，执行 `git diff --check`。
5. 提交：`fix(harness): harden docs evidence and first push`。

## 3. Task 2：扩展目录治理

**文件**：结构门禁 Pester、治理模块、`AGENTS.md`、ADR-013、Harness 结构政策、README、INDEX、changelog。

1. RED：新增测试，要求 `src/contracts/state/tests` 合法，未知第 14 个目录仍失败。
2. 运行结构门禁测试，确认新目录被旧白名单阻断。
3. GREEN：把白名单扩展为 13 个；保留 40/50/200、报告生命周期和基线语义。
4. 更新 ADR-013 为被 ADR-014 扩展，禁止创建空占位目录。
5. 运行全部 Pester和结构门禁。
6. 提交：`feat(harness): allow typed core directories`。

## 4. Task 3：建立 Node 包与结果契约

**文件**：根 `package.json`，`harness/package.json`、lock、tsconfig，`contracts/*.schema.json`，`src/core/result.ts`，`tests/result.test.ts`。

1. 添加独立 Node 20 包、TypeScript、Vitest、Ajv 配置和结果测试；不先写实现。
2. RED：运行结果测试，确认因结果模块缺失而失败。
3. GREEN：实现单项/运行状态、中文标签、聚合规则和 JSON Schema 校验。
4. 覆盖非法状态、阻断、跳过、未采集和警告场景。
5. 运行类型检查、结果测试和 Schema 测试。
6. 提交：`feat(harness): define typed result contracts`。

## 5. Task 4：建立安全进程执行器

**文件**：`src/core/process-runner.ts`、`src/core/redact.ts` 及对应测试。

1. RED：测试参数数组执行、退出码、超时、stdout/stderr、UTF-8、行尾清理和敏感值脱敏。
2. 确认测试因执行器缺失而失败。
3. GREEN：标准命令使用 `shell=false`；仅显式自定义业务命令允许平台 shell。
4. 错误结果必须包含根因提示、安全重试和停止条件。
5. 运行定向测试、类型检查。
6. 提交：`feat(harness): add safe process runner`。

## 6. Task 5：建立运行上下文与证据

**文件**：`src/core/run-context.ts`、`src/core/evidence.ts`、`src/core/git.ts` 及对应测试。

1. RED：测试 runId、完整 SHA、Asia/Shanghai 时间、ReportKey 路径校验、补丁指纹和 COMMIT/WORKTREE 身份。
2. RED：测试 JSON 与 Markdown 的 runId、SHA、环境、Scope、结论一致。
3. GREEN：实现运行上下文、原始目录、稳定 JSON/中文 Markdown 生成。
4. 绝对路径转换为仓库相对路径，任何密钥不得进入证据。
5. 运行定向测试、Schema、类型检查。
6. 提交：`feat(harness): write structured evidence`。

## 7. Task 6：实现只读 inspect

**文件**：`contracts/environments.json`、`src/core/config.ts`、`src/checks/inspect.ts`、`src/cli/inspect.ts` 及测试。

1. RED：测试仓库结构、工具链、real-pre 开关、敏感变更、危险命令和缺失配置。
2. GREEN：实现 test/real-pre 配置解析；只报告密钥存在性。
3. CLI 帮助、阶段、摘要和修复建议使用中文。
4. 验证 inspect 不调用 Git 写操作、Docker 写操作或远端命令。
5. 运行 inspect 测试、类型检查和真实只读 inspect。
6. 提交：`feat(harness): add read-only inspect command`。

## 8. Task 7：实现验证依赖图

**文件**：`src/core/workflow.ts`、`src/workflows/verify.ts` 及测试。

1. RED：测试前后端独立分支、构建失败后的 Docker 阻塞、健康阻塞、业务阻塞及 dry-run 跳过。
2. GREEN：实现可注入检查执行器和确定性依赖图，不在首个失败后丢失独立证据。
3. 验证 FAIL 优先于 BLOCKED，BLOCKED 优先于 PARTIAL。
4. 运行工作流测试、结果测试和类型检查。
5. 提交：`feat(harness): orchestrate verification graph`。

## 9. Task 8：接入后端与前端检查

**文件**：`src/checks/backend.ts`、`src/checks/frontend.ts`、命令计划测试。

1. RED：按 Scope 测试 Maven test/package 与 npm ci/typecheck/test/build 的命令及顺序。
2. GREEN：通过安全执行器运行命令，日志写入独立检查文件。
3. backend Scope 不运行前端；frontend Scope 不运行后端；full 两者都运行。
4. dry-run 只写计划并标记跳过。
5. 运行定向测试、类型检查和三个 Scope dry-run。
6. 提交：`feat(harness): verify backend and frontend`。

## 10. Task 9：接入 Docker、健康与业务检查

**文件**：`src/checks/docker.ts`、`src/checks/health.ts`、`src/checks/business.ts` 及测试。

1. RED：测试服务选择、Compose 参数、禁止破坏性命令、健康重试/超时和业务默认命令。
2. RED：测试 `SkipBusinessValidation` 产生 SKIPPED/PARTIAL，不产生 PASS。
3. GREEN：只执行 `compose up -d --build <services>` 和 `compose ps`；不触碰数据库/Redis 服务。
4. GREEN：复用现有端口键和健康路径；自定义业务命令显式进入 shell 适配。
5. 运行定向测试、全部 Node 测试和 dry-run。
6. 提交：`feat(harness): verify runtime and business checks`。

## 11. Task 10：接入 agent-do 兼容入口

**文件**：`harness/scripts/tests/report-lifecycle.Tests.ps1`、`agent-do.ps1`、必要的 PowerShell 适配函数。

1. RED：Pester 锁定六个兼容参数、三个 Node Scope 仅调用一次 verify、docs/apifox 不变。
2. RED：验证 Node 退出码 1/3 禁止 Git 与部署，退出码 2 保留部分/阻塞结论。
3. GREEN：替换旧构建、重启、健康和业务段，消费 Node 稳定报告。
4. 禁止双跑；`DeployRemote` 仍需显式 true 且本地确定性检查通过。
5. 运行全部 Pester、Node 测试和 agent-do dry-run。
6. 提交：`refactor(harness): delegate verification to node`。

## 12. Task 11：文档、状态与最终证据

**文件**：Harness README/INDEX、命令矩阵、结构政策、changelog、债务表、稳定 evidence。

1. 更新中文命令、状态、退出码、证据目录、回滚和兼容边界。
2. 登记并关闭本轮已修 Harness 债务；未验证项保持 PARTIAL/BLOCKED。
3. 运行 `npm ci --prefix harness`、类型检查、全部 Node 测试、全部 Pester。
4. 运行 inspect 和三个 Scope dry-run；运行安全检查、结构门禁、`git diff --check`。
5. 生成稳定 evidence；不执行业务容器重启和远端部署，原因如实记录。
6. 提交并推送；执行最终代码审查。
7. 提交：`docs(harness): record node verify phase one evidence`。

## 13. 完成判定

只有全部任务测试通过、兼容入口无双跑、证据一致、任务门禁通过且分支已推送，第一阶段才可标记完成。仓库历史报告债务未清零时，仓库整体健康度继续为 PARTIAL。
