# Safety Rules

> **主源说明**：本文件保留精简条款。完整安全 / 禁止口径以 `harness/FORBIDDEN_SCOPE.md` 为准；
> 本文件与 `FORBIDDEN_SCOPE.md` 内容重叠时一律以后者为准。如本文件与 `FORBIDDEN_SCOPE.md` 冲突，必须更新 `FORBIDDEN_SCOPE.md` 并在 `HARNESS_CHANGELOG.md` 记录，不得在本文件覆盖。

## real-pre 安全边界

- real-pre 是真实联调环境。
- `APP_TEST_ENABLED=false`。
- `DOUYIN_TEST_ENABLED=false`。
- `DOUYIN_REAL_UPSTREAM_MODE=live`。
- 禁止 mock 化。
- 禁止清库。
- 禁止 `docker compose down -v`。
- 禁止删除 PostgreSQL / Redis volume。

## Git 与密钥

- 禁止提交 `.env`、`.env.real-pre`、`.env.test`。
- 禁止提交 `*.pem`、`*.key`、credentials、secrets、私钥、证书。
- 禁止输出 Token、密码、OAuth code、数据库密码。
- `safety-check.ps1` 和 `git-push-safe.ps1` 只输出密钥是否存在，不输出密钥值。

## 操作安全

- 远端部署必须走 `deploy-remote.ps1`。
- 回滚必须先保护数据卷。
- 破坏性数据库 migration 必须人工确认。

