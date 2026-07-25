# Skills

[V1 必做] Skill 是智能体可执行任务说明。每个技能说明文件必须包含触发场景、输入、步骤、输出和验证。

| Skill | 用途 |
| --- | --- |
| 需求对齐 | 判断需求是否属于 V1 |
| 领域审计 | 检查领域合同与边界 |
| 订单归因审计 | 检查 `pick_source`、订单事实和业绩边界 |
| 抖音接口审计 | 检查真实 SDK / Gateway / Token |
| E2E测试设计 | 设计 test/mock 和 real-pre E2E |
| real-pre验收 | 执行真实联调验收口径 |
| 文档重构 | 重排、裁剪、归档、索引文档 |
| 部署排障 | 排查 Docker Compose / profile / 端口 |
| saas-api-cli-skill | 接入 Apifox/OpenAPI CLI、Codex MCP 和接口测试资产索引 |

## 兼容入口

以下入口保留给 code-review-graph 工作流，不替代上面的中文业务入口：

| Skill | 用途 |
| --- | --- |
| `explore-codebase` | 图谱导航、模块和调用流探索 |
| `debug-issue` | 图谱辅助缺陷定位 |
| `refactor-safely` | 图谱辅助安全重构 |
| `review-changes` | 图谱辅助变更审查 |

兼容入口统一要求：先获取最小上下文；图谱不可用时标记 `BLOCKED`，不得伪造图谱结果；代码修改仍遵守项目构建、健康检查和 evidence 规则。
