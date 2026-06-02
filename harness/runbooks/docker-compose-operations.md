# Runbook: docker-compose operations

## 适用场景

本地 test / real-pre Docker Compose 操作。

默认操作目标为本地 `real-pre`。`test` 仅在用户明确要求或专项测试需要时显式指定。

## 安全检查

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\safety-check.ps1 -Env real-pre
```

## 重启后端

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\restart-compose.ps1 -Env real-pre -Scope backend
```

## 重启前端

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\restart-compose.ps1 -Env real-pre -Scope frontend
```

## 重启全栈

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\restart-compose.ps1 -Env real-pre -Scope full
```

## 健康检查

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\verify-local.ps1 -Env real-pre -Scope full
```

## 禁止事项

- 不执行 `docker compose down -v`。
- 不删除 volume。
- 不混起第二个 `3001` Vite 或额外 `8080` 后端。
