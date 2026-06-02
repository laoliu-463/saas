# Runbook: database change

## 适用场景

新增或修改表、字段、索引、约束、迁移脚本、repair/backfill 逻辑。

## 前置检查

1. 读取 `docs/06-数据模型总表.md` 和对应领域合同。
2. 确认是否影响历史数据、real-pre 数据、远端迁移和回滚。
3. 检查 `backend/src/main/resources/db/` 与 `db/migrate/` 的现有迁移方式。

## 操作步骤

1. 先做只读 SQL/API 取证，明确问题数据和影响范围。
2. 设计幂等 migration 或受控 repair；不确定时标记待确认。
3. 对 real-pre 写入类操作先 dry-run 或做最小样本验证。
4. 后端相关修改走：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env real-pre -Scope backend -Message "fix: database change"
```

## 验证标准

- migration 不重复污染历史数据。
- 关键表结构、索引和约束可查询证明。
- 后端构建和健康检查通过。
- 业务 API 能读取/写入新结构。

## 常见失败原因

- `migrate-all.sql` 中历史非幂等 DML 被无条件重复执行。
- 只改 SQL 不改数据模型/API 文档。
- 只验证新增数据，不验证历史数据兼容。

## 禁止事项

- 禁止清库。
- 禁止删除 volume。
- 禁止未备份/未说明回滚就批量改 real-pre 数据。
- 禁止用数据库隐式逻辑承载不可追踪业务流程。

## 产出物位置

- migration / repair diff。
- SQL 取证摘要。
- evidence report。
- 必要时更新 `harness/state/KNOWN_ISSUES.md` 或 `docs/决策/*.md`。
