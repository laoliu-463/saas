# Skill: real-pre-debug

## 使用场景

用于 real-pre 环境排障、真实 SDK 联调、Token / 权限 / 限流 / 空数据 / 上游响应异常定位。

## 必读文件

- `docs/08-第三方对接总览.md`
- `docs/10-部署运行总览.md`
- `docs/验收/real-pre联调手册.md`
- `.claude/hooks/real-pre环境守卫.md`
- `harness/FORBIDDEN_SCOPE.md`
- 当前对接项对应的 `docs/对接/*.md`

## 禁止事项

- 禁止清库、删除 volume、执行 `down -v`。
- 禁止把 real-pre mock 化。
- 禁止用 test 环境证明 real-pre 真实闭环。
- 禁止无 Token / 无真实样本时声明 PASS。
- 禁止输出密钥、Token、密码。

## 标准流程

1. 执行 `harness/commands/safety-check.ps1 -Env real-pre`。
2. 检查 Docker 服务状态：PostgreSQL、Redis、backend、frontend。
3. 检查后端健康：`http://127.0.0.1:8081/api/system/health`。
4. 检查前端健康：`http://127.0.0.1:3001/healthz` 或 `/login`。
5. 检查环境变量是否为真实模式：`APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`、`DOUYIN_REAL_UPSTREAM_MODE=live`。
6. 检查后端日志、同步日志、上游请求 / 响应。
7. 查询数据库事实，确认是代码问题、数据问题、三方接口问题还是真实样本不足。
8. 运行 `npm run e2e:real-pre:p0:preflight`；必要时再运行 P0 / roles。
9. 生成 evidence report，结论只能是 `PASS`、`PARTIAL`、`BLOCKED`、`PENDING` 或 `FAIL`。

## 验证方式

- Docker `ps` 输出。
- 健康检查响应。
- real-pre env guard。
- 后端日志。
- DB/API 事实。
- Playwright real-pre 报告。

## 输出格式

```md
现象：
环境事实：
日志证据：
DB/API 证据：
上游证据：
排除项：
阶段性结论：
下一步：
```

