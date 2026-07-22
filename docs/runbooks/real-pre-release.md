# real-pre 发布

适用对象：发布管理员、值班人员和 Jenkins 维护者。

发布必须遵循：`main` 合并 → `release/real-pre` 提升 PR → Jenkins `saas-real-pre-cd` 唯一队列。普通 Agent 不直接部署远端 real-pre。

权威材料：

- [部署运行总览](../10-部署运行总览.md)
- [Jenkins 自动化部署规划](../deploy/07-Jenkins自动化部署规划.md)
- [部署后验收门禁](../deploy/05-real-pre部署后验收门禁.md)
- [发布审查索引](../release/README.md)
- [Jenkinsfile](../../Jenkinsfile)

发布记录必须包含目标完整 SHA、制品、迁移计划、锁、健康检查、验收结果和回滚目标。
