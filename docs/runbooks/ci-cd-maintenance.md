# CI/CD 维护

适用对象：GitHub Actions、Harness 和 Jenkins 维护者。这里维护入口关系，不把内部脚本合同复制到开发者文档。

权威材料：

- [GitHub Actions](../../.github/workflows/ci.yml)
- [Jenkinsfile](../../Jenkinsfile)
- [Harness 总览](../../harness/README.md)
- [CI/CD real-pre 规则](../../harness/rules/cicd-real-pre-policy.md)
- [部署规划](../deploy/07-Jenkins自动化部署规划.md)

修改 CI/CD、Harness、部署脚本、认证、迁移或发布基础设施时，按高风险 PR 规则补充兼容性、回滚和故障处理，并运行对应契约测试。
