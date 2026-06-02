# Task Routing

## 总规则

任务开始后先判断所属类型和领域，再读取对应文档。不能只凭文件名或报错内容直接下结论。

## 任务类型分流

| 任务类型 | 必读文件 | 默认 Scope | 默认验证 |
| --- | --- | --- | --- |
| 后端修复 | `CLAUDE.md`、`docs/README.md`、对应领域合同、`docs/05`、`docs/06`、`docs/09` | `backend` | Maven 构建、后端健康、相关 API/SQL |
| 前端修复 | `CLAUDE.md`、`docs/README.md`、对应流程、`docs/05`、`docs/09` | `frontend` | 前端构建、前端健康、页面或 E2E |
| 全链路修复 | 领域合同、流程、接口、数据、权限、验收和部署文档 | `full` | 后端 + 前端构建、容器重启、健康、业务验证 |
| real-pre 排障 | `docs/08`、`docs/10`、`docs/验收/real-pre联调手册.md`、`harness/skills/real-pre-debug.skill.md` | `full` | preflight、日志、环境变量、DB/API 事实 |
| 订单归因 | `docs/流程/订单归因链路.md`、`docs/对接/转链与pick_source归因.md`、`harness/skills/order-attribution.skill.md` | `backend` | pick_source、mapping、default_channel、performance、sample |
| 寄样生命周期 | `docs/领域/寄样域.md`、`docs/流程/寄样交作业链路.md`、`harness/skills/sample-lifecycle.skill.md` | `full` | 申请、审批、发货、订单事件、状态流转 |
| 商品库 / 推广中入库 | `docs/领域/商品域.md`、`docs/05` 商品 API 补充、`harness/skills/product-library.skill.md` | `full` | 同步 / repair、商品库展示、转链映射 |
| 看板 / 业绩 | `docs/领域/业绩域.md`、`docs/领域/分析模块.md`、`harness/skills/performance-dashboard.skill.md` | `full` | 业绩明细、汇总、dashboard API |
| 权限 / 数据范围 | `docs/07-权限与数据范围.md`、用户域、对应业务域 | `full` | admin/group/self 多账号对比 |
| 文档 / Harness 调整 | `AGENTS.md`、`CLAUDE.md`、`docs/README.md`、`harness/*.md` | `docs` | safety-check、结构检查、旧内容维护计划、证据报告 |
| 远端部署 | `harness/environment/remote-real-pre-env.md`、`harness/runbooks/remote-deploy.md` | `full` | 远端 docker ps、后端健康、前端健康 |

## 领域判断

- 商品、活动、转链、`pick_source_mapping`：商品域。
- 达人资料、标签、地址、跟进：达人域。
- 寄样申请、审批、发货、交作业：寄样域。
- 订单事实、退款事实、同步日志：订单域。
- 最终归属、提成、冲正、汇总：业绩域。
- 用户、角色、菜单、组织、数据范围：用户域。
- 规则参数、配置变更、配置审计：配置域。
- dashboard、报表、导出、汇总展示：分析模块。

## 执行入口选择

```powershell
# 文档 / Harness 变更
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env test -Scope docs -Message "docs: update harness"

# 后端变更
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env test -Scope backend -Message "fix: update backend logic"

# 前端变更
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env test -Scope frontend -Message "fix: update frontend flow"

# real-pre 全链路验证
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env real-pre -Scope full -Message "fix: update real-pre flow"

# 远端部署（用户明确要求时）
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env real-pre -Scope full -DeployRemote true -Message "deploy: real-pre update"
```

远端部署只有用户明确要求时才加：

```powershell
-DeployRemote true
```

## 直接子命令

```powershell
# 安全检查
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\safety-check.ps1 -Env real-pre -Scope docs -DryRun

# docs-only 验证
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env real-pre -Scope docs -DeployRemote false -Message "docs: initialize harness engineering system" -DryRun

# 旧内容维护候选计划
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\retire-content.ps1 -Action Plan -DryRun

# 远端部署子命令
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\deploy-remote.ps1 -Env real-pre -RemoteHost saas -RemoteDir /opt/saas/app -DryRun
```
