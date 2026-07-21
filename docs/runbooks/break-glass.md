# Break-glass 紧急恢复

适用对象：经过授权的值班人员。Break-glass 只用于阻断性事故恢复，不能成为日常发布捷径；完成后必须通过正常 PR 路径补录和收敛。

权威材料：

- [服务器部署总览](../deploy/00-服务器部署总览.md)
- [Docker 手动部署 real-pre](../deploy/02-Docker手动部署real-pre.md)
- [real-pre 全过程命令清单](../deploy/08-real-pre全过程命令清单.md)
- [远端部署规则](../../docs/harness-maintenance/legacy-rules/runbooks/remote-deploy.md)

执行记录至少包含审批人、原因、目标完整 SHA、备份、主机级锁、健康检查、回滚结果和补录 PR。
