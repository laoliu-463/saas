# 文档总览

更新时间：2026-05-03

## 当前一句话状态

当前项目最准确的阶段表述是：

> `本地 Mock 核心业务闭环已完成，认证安全体系已补齐，真实 SDK 联调准备中`

说明：

- 当前已完成本地 Mock 主链路联调与问题收口
- 当前已完成 real-pre 浏览器全路径回归（2026-05-03，45/45 通过）
- 当前已完成 `3000/8080` 本地 Mock 可见浏览器分批验收，并修复停用登录提示与物流发货数据可见性问题
- 当前已完成认证安全 5 项补齐：Token 双令牌刷新、登出 / Token 吊销（Redis 黑名单）、SecurityConfig 白名单收紧、DataScope 数据权限过滤、权限 / 菜单管理端点
- 当前不默认接真实抖店 / 抖音 / SDK
- 下一阶段重点是收口剩余问题，并为真实 SDK 联调做准备

## 当前基线

### 代码与验证基线

- 前端构建：`frontend npm run build` 通过
- 后端测试：`backend mvn test` 作为当前代码基线为通过（`418 tests, 0 failures, 0 errors`）
- 认证安全相关补测（AuthService、JwtTokenProvider、JwtAuthInterceptor 等）已通过
- 本地 Mock 主链路联调记录已形成文档
- real-pre 全路径回归报告已落盘：`runtime/qa/out/e2e-20260503-1353/report.md`
- local-mock 补充验收报告已落盘：`runtime/qa/out/local-mock-supplement-20260503-1430/report.md`
- QA 脚本入口已固定为：`runtime/qa/full-browser-e2e.cjs`、`runtime/qa/local-mock-supplement.cjs`
- QA 一键命令已固定为：`scripts/run-real-pre-e2e.ps1`、`scripts/run-local-mock-supplement.ps1`、`scripts/run-qa-all.ps1`
- 本地 Mock 可见浏览器验收截图与结果已落盘：`out/e2e-*`
- 本地 Mock 最终可见浏览器烟雾验收已落盘：`out/e2e-final-smoke-20260503114958/results.json`

### 当前本地运行事实

- 前端：`http://localhost:3000`
- 后端：`http://localhost:8080/api`
- real-pre 前端：`http://localhost:3001`
- real-pre 后端：`http://localhost:8081/api`
- real-pre 当前是独立端口/容器的回归环境，但后端 profile 仍是 `local-mock`
- 当前 `.env.real-pre` 仍启用 `APP_TEST_ENABLED=true`、`DOUYIN_TEST_ENABLED=true`
- PostgreSQL：`5432`
- Redis：`6379`
- 登录账号：`admin / admin123`

### 当前口径说明

- 本地人工联调默认口径已经统一为 `local-mock`
- `test` 继续用于自动化测试 / 隔离测试栈
- `real-pre` 当前用于浏览器回归、权限验收、部署形态验证
- 真实 SDK 联调环境仍待从当前 `real-pre` 进一步演进

## 主干文档（10 个）

当前 `docs/` 根目录只保留以下 10 个主干文档：

1. [README](./README.md)
2. [00-项目总览](./00-项目总览.md)
3. [01-业务闭环](./01-业务闭环.md)
4. [02-架构设计](./02-架构设计.md)
5. [03-Test与Real网关契约](./03-Test与Real网关契约.md)
6. [04-开发进度](./04-开发进度.md)
7. [05-接口与数据模型](./05-接口与数据模型.md)
8. [06-部署与对接计划](./06-部署与对接计划.md)
9. [09-真实SDK联调准备清单](./09-真实SDK联调准备清单.md)
10. [10-V2.2场景覆盖矩阵](./10-V2.2场景覆盖矩阵.md)

其余阶段性记录、专项验收、联调实录、整改单统一迁入 [archive/README](./archive/README.md)。

## 当前已打通的本地 Mock 主链路

1. 登录与核心页面访问
2. Token 双令牌刷新（Access Token 2h + Refresh Token 7d）
3. 登出与 Token 吊销（Redis 黑名单）
4. DataScope 数据权限过滤（本人 / 本组 / 全部）
5. 系统用户与系统角色 CRUD
6. 商品库、活动商品、商品审核、分配、Mock 转链
7. Mock 订单回流、归因、未归因展示与详情排查
8. 寄样申请、审核、发货、签收、待交作业、自动完成
9. 达人列表、达人详情、达人与商品/订单/寄样关联

详细过程与证据见 [archive/records/16-local-mock业务联调记录](./archive/records/16-local-mock业务联调记录.md)。
本轮最终可见浏览器收口记录见 [archive/records/20-20260503-本地Mock可见浏览器验收记录](./archive/records/20-20260503-本地Mock可见浏览器验收记录.md)。

## 建议阅读路径

### 1. 第一次接手项目

1. [00-项目总览](./00-项目总览.md)
2. [01-业务闭环](./01-业务闭环.md)
3. [02-架构设计](./02-架构设计.md)
4. [03-Test与Real网关契约](./03-Test与Real网关契约.md)
5. [05-接口与数据模型](./05-接口与数据模型.md)
6. 如需直接跑浏览器验收，可先看 `runtime/qa/full-browser-e2e.cjs`

### 2. 做本地 Mock 联调

1. [06-部署与对接计划](./06-部署与对接计划.md)
2. [archive/runbooks/07-Test全链路验收](./archive/runbooks/07-Test全链路验收.md)
3. [archive/runbooks/08-test-演示脚本](./archive/runbooks/08-test-演示脚本.md)
4. [archive/records/16-local-mock业务联调记录](./archive/records/16-local-mock业务联调记录.md)
5. [archive/records/17-项目剩余事项看板](./archive/records/17-项目剩余事项看板.md)
6. [runtime/qa/local-mock-supplement.cjs](../runtime/qa/local-mock-supplement.cjs)

### 3. 做真实 SDK 联调准备

1. [09-真实SDK联调准备清单](./09-真实SDK联调准备清单.md)
2. [archive/records/14-抖店SDK全量梳理与逐接口联调规划](./archive/records/14-抖店SDK全量梳理与逐接口联调规划.md)
3. [archive/records/15-real-pre最小联调落地方案](./archive/records/15-real-pre最小联调落地方案.md)
4. [runtime/qa/full-browser-e2e.cjs](../runtime/qa/full-browser-e2e.cjs)

### 4. 做专项收口

1. [10-V2.2场景覆盖矩阵](./10-V2.2场景覆盖矩阵.md)
2. [archive/runbooks/11-P0测试数据收口清单](./archive/runbooks/11-P0测试数据收口清单.md)
3. [archive/audits/12-文档编码乱码问题分析报告](./archive/audits/12-文档编码乱码问题分析报告.md)
4. [archive/audits/13-接口导入APIFOX整改任务单](./archive/audits/13-接口导入APIFOX整改任务单.md)
5. [archive/audits/接口整改-现状vs目标](./archive/audits/接口整改-现状vs目标.md)
6. [archive/audits/接口整改-决策备忘录](./archive/audits/接口整改-决策备忘录.md)

## 当前最重要的执行原则

- 前端只调用内部 API，不直连第三方
- Service 层只依赖 Gateway 契约
- 本地 Mock 与 Real 只允许在 Gateway 实现与配置层切换
- 本地联调必须先看业务，再看页面，再看接口，再查数据库，再看日志
- 浏览器验收默认执行规范：每个测试用例单独新开一个可见浏览器，逐页操作并截图归档到对应批次目录
- 未经明确要求，不接真实抖店 / 抖音 / SDK

## 当前优先级

1. 推进真实 SDK Token 建立与真实接口样本获取
2. 完成 M1.6 数据看板真实化
3. 完成 M1.7 部署验证
4. 继续按 Gateway 逐项推进真实 SDK 联调
