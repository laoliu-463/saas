# 数据库迁移

适用对象：数据库维护者、后端维护者和发布管理员。普通业务 PR 只需在 PR 模板勾选数据库影响并填写兼容性、迁移和回滚说明。

权威材料：

- [数据库变更规则](../../docs/harness-maintenance/legacy-rules/runbooks/database-change.md)
- [部署运行总览](../10-部署运行总览.md)
- [当前迁移入口](../../backend/src/main/resources/db/migrate-all.sql)

迁移必须先确认历史数据、前后版本兼容、执行顺序、备份和回滚边界。real-pre 禁止清库、删除 PostgreSQL / Redis volume，dry-run 不能冒充真实迁移成功。
