# Prompt: p0 fix

请按 P0 修复流程执行：

1. 先复现问题，记录页面 / 接口 / 命令 / 环境。
2. 收集证据：日志、Network、API、SQL、配置、依赖、最近 diff。
3. 构造最小复现。
4. 判断所属领域和 V1 边界。
5. 做最小修改，不扩大架构和业务范围。
6. 执行构建。
7. 重启对应 Docker 容器。
8. 执行健康检查。
9. 执行业务验证。
10. 生成 evidence report。
11. 执行安全 Git 提交与推送。
12. 在 evidence 内联 retro 结论；只有可执行改进才单独生成 retro。
13. 输出阶段性结论和剩余风险。

默认命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope full -ReportKey task-key -OwnedFiles 'path1;path2' -Message "fix: describe p0 fix"
```

禁止事项：

- 不用 try-catch 掩盖根因。
- 不用 mock 证明 real-pre。
- 未验证不得写 PASS。
