# OpenAPI / Apifox Evidence

## 结论

PARTIAL

## 证据

- **文件**:
  - `backend/src/main/java/com/colonel/saas/config/SwaggerConfig.java`
  - `scripts/export-openapi.ps1`
  - `scripts/export-openapi.sh`
  - `scripts/sync-apifox.sh`
  - `docs/openapi/saas-openapi.json`
  - `docs/openapi/controller-inventory.md`
  - `docs/openapi/apifox-sync.md`
  - `docs/openapi/openapi-generation-report.md`
- **命令**:
  - `mvn -f backend/pom.xml "-DincludeGroupIds=org.springdoc,com.github.xiaoymin" dependency:tree`：PASS
  - `mvn -f backend/pom.xml -DskipTests compile`：PASS
  - `agent-do.ps1 -Env real-pre -Scope backend -Message "docs: add openapi apifox export"`：构建、重启、健康 PASS；业务 preflight BLOCKED
  - `scripts/export-openapi.ps1`：PASS，导出 `paths=221`
  - `bash -n scripts/export-openapi.sh; bash -n scripts/sync-apifox.sh`：PASS
  - `npm install -g apifox-cli@latest`：PASS，安装版本 `2.2.5`
  - `apifox import --help`：PASS，确认支持 `--project`、`--format openapi`、`--file`、`--branch`
  - `bash scripts/sync-apifox.sh`：按预期被 `.env` 占位符保护阻断，未发起云端导入
  - `.env` 填写真实 `APIFOX_ACCESS_TOKEN` / `APIFOX_PROJECT_ID` 后，`apifox login --with-token`：PASS
  - `apifox branch list --project <redacted> --type all`：PASS，初始仅存在 `main`
  - `apifox branch create --project <redacted> --type sprint --name ddd-sync`：PASS
  - `apifox import --project <redacted> --format openapi --file docs/openapi/saas-openapi.json --branch ddd-sync`：PASS，创建 252 个 API、43 个目录、339 个 schema，errorCount=0
  - `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\sync-apifox.ps1`：PASS
  - `apifox branch get ddd-sync --project <redacted> --type sprint`：PASS，分支状态 `apiCount=252`
- **接口**:
  - `GET /api/system/health`：`UP`
  - `GET /api/v3/api-docs/apifox`：无 JWT 返回 401；带 JWT 导出成功
  - `GET /api/doc.html`：无 JWT 返回 401
- **日志**:
  - backend 容器启动完成，active profile 为 `real-pre`
  - preflight 证据：`runtime/qa/out/real-pre-preflight-20260704-133108/report.md`
  - 自动 evidence 归档：`harness/archive/reports-20260704-openapi/evidence-20260704-133110.md`
  - 自动 evidence 摘要：`harness/reports/evidence-20260704-133639.md`

## 风险

- Apifox CLI 已安装，且 OpenAPI 已成功导入 Apifox `ddd-sync` 分支。
- 当前机器 Bash 入口受 Windows/WSL Node 路径影响，`bash scripts/sync-apifox.sh` 不适合作为 Windows 首选入口；已补充 `scripts/sync-apifox.ps1`。
- `apifox-cli@2.2.5` 安装时出现上游弃用依赖警告，当前未影响 CLI `import` 命令可用性。
- real-pre preflight 因抖音 Token 状态 `BLOCKED_AUTH` 未通过，不能声明业务闭环 PASS。
- `harness/reports` 初次检查超限，已归档 2 个旧/详细报告后复查 PASS。
- `docs` 根目录存在较多历史文件；本次新增内容放入 `docs/openapi/`，未继续增加 docs 根目录文件。
- 当前工作区已有大量非本任务 dirty，本次未回滚、未提交、未推送。

## 下一步

- Windows 本机后续执行 `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\sync-apifox.ps1`。
- 完成抖音授权后重跑 real-pre preflight。
- 本次未发现需要升级 Harness；仅记录既有 reports/docs 文件数超限风险。
