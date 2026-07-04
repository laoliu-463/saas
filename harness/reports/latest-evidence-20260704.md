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

- Apifox CLI 未安装，且未提供 `APIFOX_ACCESS_TOKEN` / `APIFOX_PROJECT_ID`，所以未执行真实导入。
- real-pre preflight 因抖音 Token 状态 `BLOCKED_AUTH` 未通过，不能声明业务闭环 PASS。
- `harness/reports` 初次检查超限，已归档 2 个旧/详细报告后复查 PASS。
- `docs` 根目录存在较多历史文件；本次新增内容放入 `docs/openapi/`，未继续增加 docs 根目录文件。
- 当前工作区已有大量非本任务 dirty，本次未回滚、未提交、未推送。

## 下一步

- 安装 Apifox CLI 并配置 Token / Project ID 后执行 `bash scripts/sync-apifox.sh`。
- 完成抖音授权后重跑 real-pre preflight。
- 本次未发现需要升级 Harness；仅记录既有 reports/docs 文件数超限风险。
