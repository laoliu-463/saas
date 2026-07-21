# Policy

Policy 只回答“不能做什么、什么时候必须升级验证”。机器规则由 `scripts/` 执行，开发者流程见 [`../README.md`](../README.md)。

- [安全边界](safety.md)：敏感配置、real-pre、权限和直接部署禁区。
- [验证与完成标准](validation.md)：风险分级、自动检查和可声明完成的条件。
- [real-pre 约束](real-pre.md)：真实上游、数据和发布入口的特殊规则。
- [保留与证据](retention.md)：运行产物、CI artifact 和历史资料如何保存。
