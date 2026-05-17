# AGENTS.md — 抖音团长 SaaS V2.2 开发地图

**版本**：V2.2 维护版  
**最后更新**：2026-05-09（事实口径与 `docs/README.md`、`docs/04-开发进度.md` 对齐）  
**适用对象**：AI 智能体 / 开发者

---

## Agent skills

### Issue tracker

本仓库默认使用 GitHub Issues 作为 issue tracker，相关 skill 统一按当前仓库 remote 使用 `gh` CLI。见 `docs/agents/issue-tracker.md`。

### Triage labels

当前仓库未声明自定义 triage 标签词汇，先使用 `needs-triage`、`needs-info`、`ready-for-agent`、`ready-for-human`、`wontfix` 作为默认映射。见 `docs/agents/triage-labels.md`。

### Domain docs

当前仓库按单上下文布局接入：根目录 `CONTEXT.md` 提供术语表，主业务与执行口径仍以 `AGENTS.md` 和 `docs/*.md` 为准。见 `docs/agents/domain.md`。

---

## 1. 当前执行口径

本项目采用 Harness Engineering：
- 文档主源：`docs/*.md`
- 代码事实来源：`backend/src/**` + `frontend/src/**`

后续开发统一遵循一句验收准则：

> **开发角度看功能是否闭环，用户角度看业务是否顺手。**

执行时不要只看“接口通了、页面有了、按钮能点”，而要看用户是否能完成真实业务动作，是否减少重复记录、重复沟通和线下补表。

文档优先级：
1. `docs/README.md`
2. `docs/00-项目总览.md` ~ `docs/06-部署与对接计划.md`
3. 当前阶段直接相关的主干文档：`docs/09-真实SDK联调准备清单.md`、`docs/10-V2.2场景覆盖矩阵.md`
4. 归档与专项记录：`docs/archive/*.md`
5. 当前代码可验证事实

补充要求：
- 进入真实 SDK 联调、P0 验收、乱码治理等专项任务时，必须同时阅读对应主干文档或 `docs/archive/` 中对应专项记录，不能只按 `00~06` 执行。
- 当前打开中的任务文档、当前里程碑引用的补充文档，默认视为本次任务约束的一部分。

---

## 2. 当前阶段（以代码实况为准）

- 已完成：V0.5（M0.1~M0.8）
- 已完成：V1.0 到 M1.5（SDK 封装、订单同步、爬虫、寄样真实数据接入、寄样自动闭环）
- 已完成：P0/P1 本地 Mock 收口（环境口径统一、数据基线固化、日志降噪、SOP 文档化）
- 已完成：real-pre 环境浏览器 E2E 全路径自动化联调（2026-05-02 首轮 10/10 PASS；2026-05-03 全量 45/45 PASS）
- 进行中：P2 工程治理（权限注解口径统一）；real-pre 订单归因与看板口径收口（详见 `docs/04-开发进度.md`）
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
├── backend/                    # Spring Boot 后端
├── frontend/                   # Vue3 前端
├── docs/                       # 项目主文档（根目录收敛为 10 个）
│   ├── README.md
│   ├── 00-项目总览.md
│   ├── 01-业务闭环.md
│   ├── 02-架构设计.md
│   ├── 03-Test与Real网关契约.md
│   ├── 04-开发进度.md
│   ├── 05-接口与数据模型.md
│   ├── 06-部署与对接计划.md
│   ├── 09-真实SDK联调准备清单.md
│   ├── 10-V2.2场景覆盖矩阵.md
│   └── archive/
├── scripts/
└── docker-compose.test.yml
```

---

## 4. 文档阅读入口

### 开发新功能
1. 先读 `docs/04-开发进度.md` 确认当前阶段和里程碑
2. 再读 `docs/01-业务闭环.md`、`docs/02-架构设计.md`
3. 如涉及接口、环境、联调，再补读 `docs/03`、`docs/05`、`docs/06`
4. 如涉及专项任务，再补读 `docs/09`、`docs/10` 或 `docs/archive/` 中对应专项文档
5. 对照当前代码实现落地
6. 增加 / 更新测试并完成最小验证

### 修复 Bug
1. 定位模块
2. 查对应主文档和专项文档
3. 修复 + 回归测试
4. 更新 `docs/04-开发进度.md`（如影响阶段结论）

### 文档维护
1. 改实现后必须同步对应 `docs/*.md`
2. 涉及 SDK / Gateway 时同步 `docs/03-Test与Real网关契约.md`、`docs/06-部署与对接计划.md`
3. 涉及真实联调时同步 `docs/09-真实SDK联调准备清单.md`
4. 涉及场景覆盖与验收口径时同步 `docs/10-V2.2场景覆盖矩阵.md`，必要时补记 `docs/archive/runbooks/11-P0测试数据收口清单.md`
5. 涉及乱码、编码、文档可读性治理时同步 `docs/archive/audits/12-文档编码乱码问题分析报告.md`
6. 重大里程碑完成后更新 `docs/README.md` 和 `docs/04-开发进度.md`

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

1. Test / Real 必须共用 Gateway 契约：`docs/03-Test与Real网关契约.md`
2. 前端只调用内部 API：`docs/05-接口与数据模型.md`
3. 商品主链路按统一闭环推进：`docs/01-业务闭环.md`
4. 部署和联调按环境切换路径执行：`docs/06-部署与对接计划.md`
5. 真实 SDK 联调前先过准备清单：`docs/09-真实SDK联调准备清单.md`
6. P0 / 场景验收以覆盖矩阵和收口清单为准：`docs/10-V2.2场景覆盖矩阵.md`、`docs/archive/runbooks/11-P0测试数据收口清单.md`
7. 文档修改需避免乱码回归，必要时对照：`docs/archive/audits/12-文档编码乱码问题分析报告.md`

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
