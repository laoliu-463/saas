# 回滚与故障处理

适用对象：值班人员和发布管理员。回滚前确认影响范围、当前版本、目标版本、数据库兼容性和审批记录。

权威材料：

- [回滚与故障排查](../deploy/06-回滚与故障排查.md)
- [Harness 回滚规则](../../harness/rules/runbooks/rollback.md)
- [real-pre 回滚脚本](../../scripts/rollback-real-pre.sh)

回滚完成后必须再次执行健康检查，记录实际 SHA、容器状态、数据库状态、用户影响和后续修复 PR。不得用“命令执行成功”代替服务验证。
