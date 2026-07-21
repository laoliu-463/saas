# Runbook: rollback

## 适用场景

本地或远端部署后出现构建、健康检查、业务验证失败。

## Git 回滚

优先使用非破坏方式：

```powershell
git revert <bad_commit>
```

不得在未获用户明确要求时执行：

```text
git reset --hard
git checkout -- <file>
```

## Docker 镜像回滚

1. 确认上一个可用镜像 tag 或 commit。
2. 修改 `IMAGE_TAG` 或切回上一个 commit。
3. 执行 `restart-compose.ps1`。
4. 执行 `verify-local.ps1`。

## 配置回滚

- `.env.real-pre` 不进 Git。
- 远端配置回滚必须先备份当前文件。
- 不输出配置值，只记录存在性和变更项。

## 数据库 migration 回滚注意事项

- real-pre 禁止清库。
- 不删除 volume。
- 不直接手写破坏性 SQL。
- 若 migration 已产生数据影响，先形成影响评估和回滚 SQL 审查，再执行。

## 验证

回滚后必须生成新的 evidence report，结论不能沿用回滚前报告。

