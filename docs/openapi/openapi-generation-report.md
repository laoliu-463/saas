# OpenAPI Generation Report

## 结论

PARTIAL

OpenAPI 生成、本地 real-pre 导出与 Apifox `ddd-sync` 分支云端导入已完成；业务闭环仍因 real-pre 抖音授权阻塞，不能声明 PASS。

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
| Apifox import | PASS | `bash scripts/sync-apifox.sh` 导入目标 `ddd-sync`，`endpointFailed=0`、`schemaFailed=0`、`endpointIgnored=252`、`schemaIgnored=345` |

## 风险

- 部分 Controller 未标注 `@Tag`，Apifox 导入后目录可能不够精细。
- Apifox `branch create --from main` 只表示分支来源；云端导入证据必须以 `apifox import --branch ddd-sync` 与 counters 为准。
- `operations=252` 小于源码 mapping 注解数 295，需要后续逐项补齐 hidden/legacy/多方法映射差异。
- real-pre 抖音授权缺失导致业务 preflight 阻塞，不能声明真实业务闭环通过。

## 后续

- 在 Apifox UI 切换到 `ddd-sync` 分支后，人工复核接口目录、鉴权、请求模型、响应模型和 legacy 标识。
- 按 `controller-inventory.md` 为未标注 `@Tag` / `@Operation` 的 Controller 补文档注解。
- 抖音重新授权后重跑 `npm run e2e:real-pre:p0:preflight`。
