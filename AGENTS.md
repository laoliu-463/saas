# AGENTS.md — 抖音团长 SaaS V1 Harness Engineering 开发地图

**版本**：V1 Harness Engineering 维护版
**最后更新**：2026-05-26（事实口径与 `CLAUDE.md`、`docs/README.md` 对齐）
**适用对象**：AI 智能体 / 开发者

---

## Agent skills

### Issue tracker

本仓库默认使用 GitHub Issues 作为 issue tracker，相关 skill 统一按当前仓库 remote 使用 `gh` CLI。见 `docs/agents/issue-tracker.md`。

### Triage labels

当前仓库未声明自定义 triage 标签词汇，先使用 `needs-triage`、`needs-info`、`ready-for-agent`、`ready-for-human`、`wontfix` 作为默认映射。见 `docs/agents/triage-labels.md`。

### Domain docs

当前仓库按单上下文布局接入：根目录 `CONTEXT.md` 提供术语表，主业务与执行口径以 `AGENTS.md`、`CLAUDE.md` 和 `docs/*.md` 为准。见 `docs/agents/domain.md`。

### Codex Harness 工作台

本项目已经建立 Harness Engineering 工作台：

- `CLAUDE.md`：只做地图，告诉智能体先读什么、怎么找证据、如何进入任务。
- `.claude/`：存放智能体工作台文档，包括 hooks、skills、plugins、LSP、MCP、subagents、commands、memory、qa、templates。
- `docs/`：存放事实、领域合同、流程、接口、事件、数据模型、验收、部署和 ADR。

Codex 在本项目执行任务时默认按以下顺序进入：

1. 先读 `CLAUDE.md`，确认当前任务入口和禁止越界项。
2. 再读 `docs/README.md`，进入对应主干文档或专题目录。
3. 如果用户要求审计、验收、排障、文档重构，优先读取 `.claude/commands/` 中同名命令文档。
4. 如果任务需要复用方法，读取 `.claude/skills/` 下对应 `SKILL.md`。
5. 如果需要分领域检查，按 `.claude/subagents/` 中的代理职责拆分证据，不自行扩展业务规则。

注意：`.claude/hooks/*.md`、`.claude/commands/*.md`、`.claude/subagents/*.md` 在 Codex 中是可读工作流文档，不是自动执行机制。用户说“按 CLAUDE.md 执行”或“按某个 command/skill 执行”时，Codex 必须显式读取并遵循。

---

## 1. 当前执行口径

本项目采用 Harness Engineering：
- 地图入口：`CLAUDE.md`
- 智能体工作台：`.claude/**/*.md`
- 文档主源：`docs/*.md`
- 代码事实来源：`backend/src/**` + `frontend/src/**`

后续开发统一遵循一句验收准则：

> **开发角度看功能是否闭环，用户角度看业务是否顺手。**

执行时不要只看“接口通了、页面有了、按钮能点”，而要看用户是否能完成真实业务动作，是否减少重复记录、重复沟通和线下补表。

文档优先级：
1. `CLAUDE.md`
2. `docs/README.md`
3. `docs/00-项目总览.md` ~ `docs/10-部署运行总览.md`
4. `docs/领域/`、`docs/流程/`、`docs/对接/`、`docs/验收/`、`docs/决策/`
5. `.claude/commands/`、`.claude/skills/`、`.claude/hooks/`、`.claude/subagents/`、`.claude/mcp/`
6. `docs/归档/` 与 `docs/archive/` 中的历史资料
7. 当前代码和测试结果

补充要求：
- 进入真实 SDK 联调、P0 验收、乱码治理等专项任务时，必须同时阅读 `CLAUDE.md`、`docs/README.md` 和对应专题目录，不能只按单个旧文档执行。
- 旧 V2.2 完整方案、FastAPI、Celery、Python 爬虫等历史口径只能作为归档背景，不得写成当前事实。
- 当前打开中的任务文档、当前里程碑引用的补充文档，默认视为本次任务约束的一部分。

---

## 2. 当前阶段（以代码实况为准）

- 已完成：V0.5（M0.1~M0.8）
- 已完成：V1.0 到 M1.5（SDK 封装、订单同步、爬虫、寄样真实数据接入、寄样自动闭环）
- 已完成：P0/P1 本地 Mock 收口（环境口径统一、数据基线固化、日志降噪、SOP 文档化）
- 已完成：real-pre 环境浏览器 E2E 全路径自动化联调（2026-05-02 首轮 10/10 PASS；2026-05-03 全量 45/45 PASS）
- 进行中：P2 工程治理（权限注解口径统一）；real-pre 订单归因与看板口径收口（当前入口见 `docs/验收/real-pre联调手册.md`、`docs/决策/ADR-002-V1范围优先级.md`）
- 待完成：M1.6 数据看板真实化**剩余项**、M1.7 部署验证；联盟侧能力中仍依赖外部 Token / 权限包 / 真实样本的分支（见 `docs/09`）

关键说明：
- 当前 `mvn test` 全绿（以 `docs/04` 最近一次记录为准：`652 tests, 0 failures, 0 errors`；重大变更后请先本地跑 `mvn clean test` 再更新数字）
- real-pre：`docker-compose.real-pre` 后端 **`real` profile**，典型 `.env.real-pre` 为 **`APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`**，可命中真实抖店上游；浏览器全路径回归 **45/45**（见 `docs/README.md`）。旧文档中「real-pre = local-mock + APP_TEST_ENABLED=true」为历史口径，以主干文档为准。
- 根目录 Playwright：`README-e2e.md`，日常 `npm run e2e`；抖店联调专项 `npm run e2e:real-pre`（等价 `runtime/qa/real-pre-douyin-frontend-e2e.cjs` → `tests/e2e/08-real-pre-douyin-integration.spec.ts`）。
- 第三方 SDK：主链路已具备大量 real-pre 取证；限流 / 429、部分权限包阻塞分支仍以清单跟踪（`docs/09`、`docs/04` 未完成项）。
- 商品页已实现抖音 Token 缺失时降级本地商品库；Dashboard 已兼容后端实际 Summary 字段格式
- 当前本机标准启动格局已固定为：`3000/8080` 一组、`3001/8081` 一组；执行时不得混起第二个 `3001` 本机 Vite 或额外 `8080` 手工后端

---

## 3. 目录导航

```text
SAAS/
├── CLAUDE.md                   # Codex / 智能体地图入口
├── .claude/                    # 智能体工作台文档
│   ├── commands/               # 审计、E2E、real-pre、文档重构等命令说明
│   ├── skills/                 # 需求对齐、领域审计、real-pre 验收等技能说明
│   ├── hooks/                  # 变更前、测试前、提交前守卫说明
│   ├── subagents/              # 分领域代理职责说明
│   ├── mcp/                    # MCP 使用和安全边界
│   └── qa/                     # P0、E2E、覆盖率、证据映射
├── backend/                    # Spring Boot 后端
├── frontend/                   # Vue3 前端
├── docs/                       # 项目主文档与事实层
│   ├── README.md
│   ├── 00-项目总览.md
│   ├── 01-V1交付范围与边界.md
│   ├── 02-业务闭环总览.md
│   ├── 03-领域架构总览.md
│   ├── 04-事件契约总表.md
│   ├── 05-API契约总表.md
│   ├── 06-数据模型总表.md
│   ├── 07-权限与数据范围.md
│   ├── 08-第三方对接总览.md
│   ├── 09-测试验收总览.md
│   ├── 10-部署运行总览.md
│   ├── 领域/
│   ├── 流程/
│   ├── 对接/
│   ├── 验收/
│   ├── 决策/
│   ├── 归档/
│   └── archive/
├── scripts/
└── docker-compose.test.yml
```

---

## 4. 文档阅读入口

### 开发新功能
1. 先读 `CLAUDE.md` 和 `docs/README.md`。
2. 再读 `docs/01-V1交付范围与边界.md`、`docs/02-业务闭环总览.md`、`docs/03-领域架构总览.md`。
3. 涉及领域职责时读 `docs/领域/` 对应领域合同。
4. 涉及业务链路时读 `docs/流程/` 对应流程。
5. 涉及接口、事件、数据、权限时读 `docs/04-事件契约总表.md`、`docs/05-API契约总表.md`、`docs/06-数据模型总表.md`、`docs/07-权限与数据范围.md`。
6. 涉及第三方或 real-pre 时读 `docs/08-第三方对接总览.md`、`docs/对接/` 和 `docs/验收/real-pre联调手册.md`。
7. 对照当前代码实现落地，增加 / 更新测试并完成最小验证。

### 修复 Bug
1. 定位模块
2. 查 `CLAUDE.md`、`docs/README.md`、对应领域合同、流程文档和验收文档
3. 修复 + 回归测试
4. 如影响接口、事件、数据、验收或 ADR，必须同步对应 `docs/*.md`

### 文档维护
1. 改实现后必须同步对应 `docs/*.md`。
2. 涉及 SDK / Gateway 时同步 `docs/08-第三方对接总览.md` 和 `docs/对接/`。
3. 涉及真实联调时同步 `docs/验收/real-pre联调手册.md` 与 `docs/验收/验收证据索引.md`。
4. 涉及 P0 / E2E 验收时同步 `docs/09-测试验收总览.md`、`docs/验收/` 与 `.claude/qa/`。
5. 涉及领域边界时同步 `docs/领域/` 和 `docs/决策/`。
6. 涉及乱码、编码、文档可读性治理时对照 `docs/archive/audits/12-文档编码乱码问题分析报告.md`。

---

## 5. 当前重点风险

1. 第三方 SDK 真实联调未完成（高优先级，依赖外部 Token 配置）
2. 权限注解口径不统一（`@RequiresRole` / `@DataScope` 覆盖未完整审计）
3. 商品主链路状态机和操作日志仍待完全统一
4. 达人跟进与真实数据回流尚未完全接入主链路
5. `CrawlerScheduler` 的 Java 变更已编译，需容器重启后完全生效（`spring.devtools.restart.enabled=false`）
6. 部分补充文档曾出现编码 / 乱码问题，修改文档时需确认文件编码一致且可读

---

## 6. 强制规则速查

1. Codex 任务先读 `CLAUDE.md`，再读 `docs/README.md`。
2. V1 范围以 `docs/01-V1交付范围与边界.md` 为准，旧 V2.2 只能作为归档背景。
3. 前端只调用内部 API：`docs/05-API契约总表.md`。
4. 事件契约以 `docs/04-事件契约总表.md` 为入口。
5. 数据模型以 `docs/06-数据模型总表.md` 为入口。
6. 权限与数据范围以 `docs/07-权限与数据范围.md` 为入口。
7. 第三方对接以 `docs/08-第三方对接总览.md` 与 `docs/对接/` 为入口。
8. P0 / E2E / real-pre 验收以 `docs/09-测试验收总览.md` 与 `docs/验收/` 为准。
9. 部署和联调按 `docs/10-部署运行总览.md` 执行。
10. 发现 V1/V2 或当前事实冲突时，写入 `docs/决策/ADR-002-V1范围优先级.md`。

---

## 7. 常用命令

```bash
cd backend
mvn test

cd frontend
npm run dev
npm run build

# 仓库根目录（Playwright，见 README-e2e.md）
cd ..
npm install
npm run e2e
```

---

## 8. Superpowers 使用准则

本项目允许在 Codex 中使用 `superpowers`，但使用方式必须遵循：

> **项目文档决定业务口径，superpowers 只负责执行方法。**

也就是说：

- `AGENTS.md` 与 `docs/*.md` 决定做什么、做到什么算通过
- `superpowers` skill 决定如何拆解、排查、验证、回归
- 当前代码与测试结果决定最终事实

### 8.1 使用优先级

执行优先级固定为：

1. 用户当前直接要求
2. 本项目 `AGENTS.md`
3. 本项目相关文档（尤其是当前阶段对应专项文档）
4. `superpowers` skills
5. 默认自由发挥

如果 `superpowers` 的建议与本项目文档冲突，以本项目文档为准。

### 8.2 本项目推荐使用的 skills

#### 1. `writing-plans`

适用场景：

- 拆解里程碑任务
- 拆解跨后端 / 前端 / 文档的阶段任务
- 拆解真实 SDK 联调计划

本项目典型用法：

- `用 writing-plans 拆一下 M1.6 数据看板真实化，先按 docs/04、docs/10 执行`
- `用 writing-plans 拆一下真实 SDK 首轮联调任务，先读 docs/03、docs/06、docs/09`

#### 2. `systematic-debugging`

适用场景：

- 真实 SDK 联调失败
- token 获取 / 刷新异常
- 限流、空数据、权限错误排查
- webhook 收到但业务未消费

本项目典型用法：

- `用 systematic-debugging 排查 RealDouyinAuthGateway token 获取失败，先按 docs/03、docs/09`
- `用 systematic-debugging 排查订单真实回流未入库，先读 docs/03、docs/archive/records/14`

#### 3. `verification-before-completion`

适用场景：

- 改完代码后做验收
- 做 P0 收口前检查
- 检查是否破坏 test / real 契约

本项目典型用法：

- `用 verification-before-completion 检查这次 Gateway 改动是否满足 docs/03 契约`
- `用 verification-before-completion 检查这次改动能否进入 P0 验收，重点对照 docs/10、docs/archive/runbooks/11`

#### 4. `test-driven-development`

适用场景：

- 后端 service / gateway / controller 的小范围功能开发
- bug 修复后的回归测试补齐

限制：

- 不要求把所有联调任务都机械改造成 TDD
- 真实 SDK 首轮探索阶段，以联调和契约验证优先，不强行追求完整 TDD 节奏

#### 5. `requesting-code-review` / `receiving-code-review`

适用场景：

- 合并前做风险复查
- 检查是否把第三方字段、Token、日志口径污染进主链路
- 检查是否破坏现有 Mock / Test 闭环

### 8.3 本项目不建议重度使用的 skills

#### 1. `subagent-driven-development`

当前项目处于联调收口阶段，口径集中比并行速度更重要。除非任务边界非常清晰，否则不建议默认拆成多个代理并行执行。

#### 2. `using-git-worktrees`

当前阶段重点是联调与收口，不是多分支并发试验。没有明确需要时，不额外引入 worktree 流程复杂度。

### 8.4 使用前必做动作

在本项目中调用任何 skill 之前，先判断任务类型，并完成对应阅读：

- 新功能 / 新阶段任务：至少先读 `docs/04`、`docs/01`、`docs/02`
- 接口 / 环境 / 联调：补读 `docs/03`、`docs/05`、`docs/06`
- 真实 SDK 联调：必须补读 `docs/09`
- P0 / 场景验收：必须补读 `docs/10`、`docs/archive/runbooks/11`
- 文档编码 / 乱码治理：必须补读 `docs/archive/audits/12`

### 8.5 推荐提问模板

为了让 Codex 更稳定地把 `superpowers` 用对，优先使用下面的话术：

1. 拆任务
   - `用 writing-plans 拆一下这个任务，先按 AGENTS.md 和 docs/04 执行`
2. 查问题
   - `用 systematic-debugging 排查这个问题，先按 docs/03、docs/09 走`
3. 改完验收
   - `用 verification-before-completion 检查这次改动能不能收口`
4. 补测试
   - `用 test-driven-development 给这个模块补测试，但不要破坏现有 test 闭环`
5. 做代码复查
   - `用 requesting-code-review 检查这次改动的风险，重点看 real/test 契约和日志泄漏`

### 8.6 一句话原则

> **superpowers 不是本项目的总指挥，它是执行手册；本项目的总指挥仍然是 AGENTS.md 与 docs/*.md。**

---

本文件用于“按当前代码推进任务”，不是历史需求归档。

<!-- code-review-graph MCP tools -->
## MCP Tools: code-review-graph

**IMPORTANT: This project has a knowledge graph. ALWAYS use the
code-review-graph MCP tools BEFORE using Grep/Glob/Read to explore
the codebase.** The graph is faster, cheaper (fewer tokens), and gives
you structural context (callers, dependents, test coverage) that file
scanning cannot.

### When to use graph tools FIRST

- **Exploring code**: `semantic_search_nodes` or `query_graph` instead of Grep
- **Understanding impact**: `get_impact_radius` instead of manually tracing imports
- **Code review**: `detect_changes` + `get_review_context` instead of reading entire files
- **Finding relationships**: `query_graph` with callers_of/callees_of/imports_of/tests_for
- **Architecture questions**: `get_architecture_overview` + `list_communities`

Fall back to Grep/Glob/Read **only** when the graph doesn't cover what you need.

### Key Tools

| Tool | Use when |
| ------ | ---------- |
| `detect_changes` | Reviewing code changes — gives risk-scored analysis |
| `get_review_context` | Need source snippets for review — token-efficient |
| `get_impact_radius` | Understanding blast radius of a change |
| `get_affected_flows` | Finding which execution paths are impacted |
| `query_graph` | Tracing callers, callees, imports, tests, dependencies |
| `semantic_search_nodes` | Finding functions/classes by name or keyword |
| `get_architecture_overview` | Understanding high-level codebase structure |
| `refactor_tool` | Planning renames, finding dead code |

### Workflow

1. The graph auto-updates on file changes (via hooks).
2. Use `detect_changes` for code review.
3. Use `get_affected_flows` to understand impact.
4. Use `query_graph` pattern="tests_for" to check coverage.
