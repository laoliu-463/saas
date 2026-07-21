# Prompt: real-pre debug

请按 real-pre 真实联调排障流程执行：

1. 读取 `docs/harness-maintenance/legacy-rules/skills/ddd/real-pre-debug.skill.md`。
2. 读取 `docs/10-部署运行总览.md` 与 `docs/验收/real-pre联调手册.md`。
3. 先执行安全检查：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\safety-check.ps1 -Env real-pre
```

4. 检查 Docker、健康接口、环境变量、日志、DB/API 事实。
5. 运行 real-pre preflight。
6. 区分代码问题、数据问题、三方接口问题、真实样本不足。
7. 输出 evidence report。

禁止：

- 清库。
- 删除 volume。
- mock 化。
- 无真实订单样本时声明渠道闭环通过。
