# 数据库变更

## 什么时候用

新增或修改 migration、表结构、索引、数据回填或数据库兼容逻辑时使用。

## 执行命令

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\run.ps1 verify
```

## 成功标准

迁移顺序、重复执行、回滚或兼容窗口经过验证；PR 明确历史数据影响、锁风险和回滚策略。

## 失败回滚

停止应用发布和迁移重试，先确认当前 migration 与数据库状态；按值班审批执行可逆迁移或恢复备份，禁止清库和删除 volume。
