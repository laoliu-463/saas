# PLAN-006 Harness 跨平台验证核心重构设计

## 1. 状态与目标

- 状态：设计已由用户逐段确认，等待实施计划与分批落地。
- 默认环境：本地 `real-pre`；不触碰远端部署，除非用户另行明确要求。
- 目标：建立 Node.js / TypeScript 确定性 Harness 核心，接管 `backend`、`frontend`、`full` 的本地验证。
- 兼容：保留 `agent-do.ps1` 参数入口；`docs`、`apifox` 暂留原 PowerShell 路径。
- 安全边界：直接运行 Node 验证不得提交、推送或部署。

本阶段不修改业务规则、数据库结构、远端部署、回滚、Codex 审查或 DDD 完成度算法。

## 2. 已确认的迁移策略

采用绞杀式迁移：Node 成为三个代码 Scope 的唯一验证事实来源，PowerShell 逐步收缩为兼容壳。禁止 Node 与旧 PowerShell 重复构建、重启后再比较结果。

### 2.1 目录

```text
harness/
├─ package.json
├─ package-lock.json
├─ tsconfig.json
├─ src/
│  ├─ cli/
│  ├─ core/
│  ├─ checks/
│  └─ workflows/
├─ contracts/
├─ tests/
└─ state/
```

- `src/`：CLI、执行器、检查和工作流。
- `contracts/`：JSON Schema 与环境策略。
- `tests/`：Harness 单元、工作流、Schema 和安全测试。
- `state/`：仅加入白名单；本阶段不创建占位文件。
- 原始运行产物继续写入 `runtime/qa/out/<runId>/`。

目录扩展由 `ADR-014` 决策，实施时同步修改 ADR-013、结构政策、检查器和项目入口。

## 3. 命令与职责

根目录提供：

```text
harness inspect
harness verify

开发者日常使用上层入口 `harness inspect` / `harness verify`；底层 Node 命令
`npm run harness:node:inspect` / `npm run harness:node:verify` 仅供 Harness
维护者和自动化调用。Windows 使用 `harness.cmd` 或 `harness.ps1`。
```

运行时基线为 Node.js 20（与现有前端 Docker 构建镜像一致）和 Java 17。`harness/package-lock.json` 固定 Node 侧依赖，不复用前端依赖树。

`inspect` 只读检查 Git、环境、配置和工具链。`verify` 负责安全预检、构建测试、按 Scope 重建容器、健康检查和业务验证。

`agent-do.ps1` 映射以下参数：`Env`、`Scope`、`ReportKey`、`BusinessCommand`、`SkipBusinessValidation`、`DryRun`。Node 失败或配置错误时禁止后续 Git 与部署；Node 阻塞或部分完成时可保存证据并执行现有受控 Git 收尾，但最终状态不得升级为通过。

## 4. 验证工作流

### 4.1 只读预检

- 仓库结构、完整 Commit SHA、工作区状态和补丁指纹。
- real-pre 安全开关、敏感文件、危险命令和必要配置。
- Java、Maven、Node、npm、Docker 与 Compose 可用性。
- `.env` 只读取必要键，日志只显示存在或缺失，不输出值。

### 4.2 构建与测试

- 后端：`mvn -f backend/pom.xml clean test`，通过后执行 `mvn -f backend/pom.xml package -DskipTests`。
- 前端：`npm --prefix frontend ci`、类型检查、Vitest 和生产构建。
- `full` 中前后端是独立分支；一方失败仍收集另一方证据。

### 4.3 Docker 与健康检查

- 目标构建与测试通过后，执行 `docker compose up -d --build <services>`。
- 禁止 `down -v`、删除卷或重建 PostgreSQL / Redis。
- 复用当前 test / real-pre 服务名、端口键和健康路径。
- 构建失败使 Docker 检查为阻塞；Docker 未成功使健康检查为阻塞。

### 4.4 业务验证

- real-pre 默认执行 `npm run e2e:real-pre:p0:preflight`。
- test 默认执行 `npm run e2e:v1-p0`。
- 显式跳过记为已跳过，整次运行只能是部分完成。

`--dry-run` 执行只读预检并输出计划；写操作记为已跳过，不得输出通过。

## 5. 状态与退出码

单项检查状态固定为：

```text
PASS / FAIL / BLOCKED / WARN / SKIPPED / NOT_COLLECTED
```

整次运行状态固定为：

```text
PASS / FAIL / BLOCKED / PARTIAL
```

聚合规则：阻断检查失败则运行失败；无失败但有阻塞则运行阻塞；存在阻断性跳过或未采集则部分完成；全部阻断检查通过时才通过。业务台账可继续使用 `PENDING`，但它不是 Harness 检查状态。

退出码：`0` 通过，`1` 确定性失败，`2` 阻塞或部分完成，`3` 参数、Schema 或 Harness 配置错误。

## 6. 中文交互

- CLI 帮助、阶段、摘要、错误和修复建议均使用中文。
- 机器字段和枚举保留英文，并提供 `statusLabel` 等中文可读字段。
- 中文摘要不堆原始异常栈；完整日志通过相对路径引用。
- 命令、文件路径、类名保持原始技术标识。
- Windows 与 Linux 统一 UTF-8；同时记录 ISO 8601 和 Asia/Shanghai 可读时间。

## 7. 证据模型

原始目录：

```text
runtime/qa/out/<runId>/
├─ run.json
├─ execution-plan.json
└─ <check-id>.log
```

稳定摘要：

```text
harness/reports/current/latest-<reportKey>.json
harness/reports/current/latest-<reportKey>.md
```

每项结果包含 Schema 版本、检查 ID、中文标题、状态、阻断性、环境、Scope、完整 SHA、时间、耗时、命令、退出码、证据路径、失败原因和中文修复建议。Markdown 与 JSON 必须由同一运行结果生成。

证据身份分为：

- `COMMIT`：工作区干净，绑定完整 SHA，可供 CI 或发布门禁使用。
- `WORKTREE`：记录 HEAD、变更文件和补丁指纹，只证明本地工作区验证，不冒充已提交发布证据。

## 8. 测试与验收

- 单元测试：状态聚合、中文标签、脱敏、环境解析、命令构造、报告键和补丁指纹。
- 工作流测试：后端失败、前端失败、Docker 阻塞、健康超时、业务跳过。
- Schema 测试：非法状态、缺字段或不一致报告必须失败。
- 兼容测试：Pester 验证参数转发且旧逻辑不重复执行。
- 安全测试：危险 Docker、敏感文件和 real-pre 错误开关必须被阻断。
- 交付验证：依赖安装、类型检查、测试、三个 Scope dry-run、Pester、结构门禁和安全检查。

验收要求：Node 不执行 Git 或远端部署；JSON 与 Markdown 的 runId、SHA、环境、Scope 和结论一致；未采集项不得成为通过；当前其他脏文件不得被修改、暂存或提交。

## 9. 实施批次与回滚

1. ADR 与 Node 包骨架。
2. 结果类型、Schema、中文状态和证据写入。
3. inspect、安全检查和执行计划。
4. 后端、前端、Docker、健康与业务工作流。
5. agent-do 兼容适配。
6. 测试、文档、变更日志、债务状态与 evidence。

每批独立测试、提交和回退。首阶段回滚只需恢复 `agent-do.ps1` 旧调用并移除新增 Node 入口，不涉及数据库或线上数据回滚。

## 10. 后续阶段

- CI 分 Job 与 Codex 只读结构化审查。
- 数据库契约、旧库升级与 Mapper SQL 冒烟。
- 远端 release / rollback 与镜像 Digest。
- DDD 能力清单、真实路由和 Legacy 退役审计。

这些后续项必须分别设计和验收，不得借本阶段顺手实现。
