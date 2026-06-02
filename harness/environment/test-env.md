# Test Environment

## 用途

`test` 是本地 mock / seed 回归环境，可用于 P0 基线、权限回归、前后端构建和 E2E。

## 配置

- Compose：`docker-compose.test.yml`
- Env：`.env.test`
- 前端默认端口：`3000`
- 后端默认端口：`8080`
- 后端健康：`/api/system/health`

## 允许事项

- 允许使用 mock / seed 数据。
- 允许执行 P0 回归和浏览器 E2E。
- 允许在不影响 real-pre 的前提下重启 test 容器。

## 禁止事项

- test 结果不能证明 real-pre 真实闭环。
- test 里的 mock 订单不能证明真实渠道归因。

## 常用命令

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env test -Scope full -Message "fix: test change"
```

