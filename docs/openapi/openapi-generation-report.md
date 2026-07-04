# OpenAPI Generation Report

## 结论

PARTIAL

OpenAPI 生成与本地 real-pre 导出已完成；Apifox 导入未执行，因为当前环境未检测到 `apifox` CLI，且没有提供 `APIFOX_ACCESS_TOKEN` / `APIFOX_PROJECT_ID`。

## 范围

- 环境：本地 `real-pre`
- Gate：Gate 1 Backend Change + 文档/脚本验证
- 不变更：API 路径、HTTP 方法、请求参数、响应结构、数据库 schema、权限逻辑、业务规则
- code-review-graph：当前会话未暴露可调用 MCP，已回退到 `rg`、Maven、Docker 和 HTTP 证据

## 依赖链

- Spring Boot：`3.2.5`
- 已有文档依赖：`com.github.xiaoymin:knife4j-openapi3-jakarta-spring-boot-starter:4.5.0`
- 间接 OpenAPI 依赖：`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0`
- 结论：无需新增 `springdoc` 依赖，避免重复依赖和版本冲突

## 生成结果

| 项 | 结果 |
|---|---:|
| Controller 类 | 45 |
| 源码 mapping 注解 | 295 |
| OpenAPI paths | 221 |
| OpenAPI operations | 252 |
| OpenAPI schemas | 345 |
| OpenAPI tags | 32 |
| Security scheme | `bearerAuth` |

路径：

- `docs/openapi/saas-openapi.json`
- `docs/openapi/controller-inventory.md`
- `docs/openapi/apifox-sync.md`

## 验证

| 检查项 | 结果 | 证据 |
|---|---|---|
| Maven compile | PASS | `mvn -f backend/pom.xml -DskipTests compile` |
| Harness backend package | PASS | `agent-do.ps1 -Env real-pre -Scope backend` 中 `mvn -DskipTests package` 成功 |
| Docker restart | PASS | `backend-real-pre` 已重建并重启 |
| Health | PASS | `GET http://127.0.0.1:8081/api/system/health` 返回 `UP` |
| OpenAPI export | PASS | `scripts/export-openapi.ps1` 导出 `paths=221` |
| Anonymous docs access | PASS | `/api/v3/api-docs/apifox` 与 `/api/doc.html` 无 JWT 均返回 401 |
| real-pre preflight | BLOCKED | `douyin token readiness`：`hasAccessToken=false`、`reauthorizeRequired=true` |
| Apifox import | SKIP | `apifox` CLI 未在 PATH，Token/Project ID 未提供 |

## 风险

- 部分 Controller 未标注 `@Tag`，Apifox 导入后目录可能不够精细。
- `operations=252` 小于源码 mapping 注解数 295，需要后续逐项补齐 hidden/legacy/多方法映射差异。
- real-pre 抖音授权缺失导致业务 preflight 阻塞，不能声明真实业务闭环通过。

## 后续

- 安装 Apifox CLI 后执行 `scripts/sync-apifox.sh`，先导入 `ai-sync` 分支。
- 按 `controller-inventory.md` 为未标注 `@Tag` / `@Operation` 的 Controller 补文档注解。
- 抖音重新授权后重跑 `npm run e2e:real-pre:p0:preflight`。
