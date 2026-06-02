# Document Priority

## 当前优先级

1. 当前源码、运行配置、测试结果。
2. `AGENTS.md`。
3. `CLAUDE.md`。
4. `docs/README.md`。
5. `harness/CURRENT_STATE.md`、`harness/TASK_ROUTING.md`、`harness/FORBIDDEN_SCOPE.md`、`harness/DOMAIN_MAP.md`。
6. `docs/00-项目总览.md` 到 `docs/10-部署运行总览.md`。
7. `docs/领域/`、`docs/流程/`、`docs/对接/`、`docs/验收/`、`docs/决策/`。
8. `.claude/` 工作台文档。
9. `docs/归档/` 与 `docs/archive/` 历史资料。

## 冲突处理

- V2.2 完整方案 vs 当前 V1：以 V1 为准。
- FastAPI / Celery vs 当前 Spring Boot：以 Spring Boot 源码为准。
- 独家达人 / 独家商家：V1 不启用。
- 毛利字段：V1 不做毛利。
- 寄样 30 天自动关闭：V1 不自动关闭。
- 个别品负责人覆盖：V1 不做。
- 物流 API 自动跟踪：V1 以手动物流和可证据接口为准。
- 老文档高级看板：V1 以基础看板和真实汇总为准。

发现新冲突时，追加到 `docs/决策/ADR-002-V1范围优先级.md`。

