# Apifox Sync Guide

## 目标

将当前 Spring Boot 后端生成的 OpenAPI JSON 导入 Apifox，作为接口文档、接口用例和后续契约测试入口。

本任务只做文档生成和同步脚本，不改变接口路径、HTTP 方法、请求参数、响应结构、权限逻辑或数据库 schema。

## 依赖事实

- 项目已使用 `knife4j-openapi3-jakarta-spring-boot-starter`，无需重复引入 `springdoc-openapi-starter-webmvc-ui`。
- Spring Boot 版本：`3.2.5`。
- OpenAPI 分组：`apifox`，导出路径为 `/v3/api-docs/apifox`。
- Apifox 支持导入 OpenAPI 3.0、3.1 和 Swagger 2.0 JSON/YAML。
- Apifox 导入后的目录主要依赖接口 `tags`，未标注 `@Tag` 的接口需要后续补齐文档注解。

参考：
- https://springdoc.org/
- https://docs.apifox.com/import-openapi-swagger
- https://docs.apifox.com/cli-command-options
- https://docs.apifox.com/6597992m0

## 导出 OpenAPI

Windows / PowerShell：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\export-openapi.ps1 `
  -BaseUrl http://127.0.0.1:8080/api `
  -Group apifox `
  -Output docs/openapi/saas-openapi.json
```

Bash：

```bash
OPENAPI_BASE_URL=http://127.0.0.1:8080/api \
OPENAPI_GROUP=apifox \
OPENAPI_OUTPUT=docs/openapi/saas-openapi.json \
bash scripts/export-openapi.sh
```

real-pre 本地容器默认端口是 `8081`，且 `/v3/api-docs/**` 不匿名公开。如需从 real-pre 导出，必须使用有效 JWT：

```bash
OPENAPI_BASE_URL=http://127.0.0.1:8081/api \
OPENAPI_GROUP=apifox \
OPENAPI_BEARER_TOKEN="$JWT_TOKEN" \
bash scripts/export-openapi.sh
```

禁止把 JWT、Apifox Token 或 `.env.real-pre` 写入仓库。

## 同步 Apifox

环境变量：

| 变量 | 必填 | 说明 |
|---|---|---|
| `APIFOX_ACCESS_TOKEN` | 是 | Apifox 访问令牌，禁止入库 |
| `APIFOX_PROJECT_ID` | 是 | Apifox 项目 ID |
| `APIFOX_BRANCH` | 否 | 默认 `ddd-sync`，建议先导入 AI/dev 分支 |
| `APIFOX_OPENAPI_FILE` | 否 | 默认 `docs/openapi/saas-openapi.json` |

脚本会优先读取进程环境变量；未设置时，会从仓库根目录 `.env` 读取上述 `APIFOX_*` 键。`.env` 已被 Git 忽略，只能保存本机占位符或真实私密配置，禁止提交。

`.env` 占位符示例：

```bash
APIFOX_ACCESS_TOKEN=__FILL_ME_APIFOX_ACCESS_TOKEN__
APIFOX_PROJECT_ID=__FILL_ME_APIFOX_PROJECT_ID__
APIFOX_BRANCH=ddd-sync
APIFOX_OPENAPI_FILE=docs/openapi/saas-openapi.json
```

执行：

```bash
bash scripts/sync-apifox.sh
```

脚本执行逻辑：

1. 检查 `apifox` CLI 是否在 `PATH`。
2. 检查 Token、项目 ID 是否仍为占位符，并检查 OpenAPI 文件。
3. 执行 `apifox login --with-token`。
4. 执行 `apifox import --project <id> --format openapi --file <json> --branch <branch>`。

如果当前 CLI 版本不支持 `import --branch`，先用 `apifox import --help` 确认参数，再通过 Apifox UI 将导入目标设置到 AI/dev 分支。

## 验收

- `mvn -f backend/pom.xml -DskipTests compile` 通过。
- `/api/v3/api-docs/apifox` 能返回非空 OpenAPI JSON。
- `docs/openapi/saas-openapi.json` 存在且 `paths` 数量与 Controller 清单大体匹配。
- 缺少 Apifox Token 时，只能标记为同步跳过，不能写成 Apifox 导入成功。
- 导入 Apifox 后人工检查目录、鉴权、请求模型、响应模型和 legacy 接口标识。
