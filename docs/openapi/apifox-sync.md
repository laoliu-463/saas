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
- Apifox CLI `apifox import` 与 Apifox UI 的 endpoint 数据面可能不一致；本脚本使用官方 Open API `POST /v1/projects/{projectId}/import-openapi`，通过 `targetBranchId` 明确导入目标分支。

参考：
- https://springdoc.org/
- https://docs.apifox.com/import-openapi-swagger
- https://docs.apifox.com/cli-command-options
- https://apifox-openapi.apifox.cn/api-173409873
- https://docs.apifox.com/6597992m0

## 导出 OpenAPI

刷新 Codex MCP / API CLI / Skill 离线资产：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\export-api-cli-skill-assets.ps1
```

该命令会从 `docs/openapi/saas-openapi.json` 生成：

- `docs/openapi/openapi-full.json`
- `docs/openapi/openapi-business.json`
- `docs/openapi/openapi-sdk-debug.json`
- `.claude/skills/saas-api-cli-skill/references/project-assets-manifest.md`

这一步只生成本地 CLI / Skill 资产，不等于 Apifox 云端导入成功。

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
| `APIFOX_BRANCH_SOURCE` | 否 | 默认 `main`，仅用于目标分支不存在时创建分支 |
| `APIFOX_MODULE_ID` | 否 | 可选；指定 Apifox 目标模块，不填则使用项目默认模块 |
| `APIFOX_OPENAPI_FILE` | 否 | 默认 `docs/openapi/saas-openapi.json` |
| `APIFOX_DEV_BASE_URL` | 是 | Apifox 开发环境对应的接口 Base URL，只能在本地 `.env` 写真实值 |
| `APIFOX_DEV_PORT` | 是 | 开发端口，必须与 `APIFOX_DEV_BASE_URL` 和 OpenAPI `servers` 匹配 |
| `APIFOX_ENVIRONMENT_ID` | 否 | Apifox 开发环境 ID；配置后云端同步会回读 environment Base URL |
| `APIFOX_IMPORT_OUTPUT` | 否 | 默认 `harness/reports/apifox/import-latest.log` |

脚本会优先读取进程环境变量；未设置时，会从仓库根目录 `.env` 读取上述 `APIFOX_*` 键。`.env` 已被 Git 忽略，只能保存本机占位符或真实私密配置，禁止提交。

`.env` 占位符示例：

```bash
APIFOX_ACCESS_TOKEN=__FILL_ME_APIFOX_ACCESS_TOKEN__
APIFOX_PROJECT_ID=__FILL_ME_APIFOX_PROJECT_ID__
APIFOX_BRANCH=ddd-sync
APIFOX_BRANCH_SOURCE=main
APIFOX_OPENAPI_FILE=docs/openapi/saas-openapi.json
APIFOX_DEV_BASE_URL=__FILL_ME_APIFOX_DEV_BASE_URL__
APIFOX_DEV_PORT=__FILL_ME_APIFOX_DEV_PORT__
APIFOX_ENVIRONMENT_ID=__FILL_ME_APIFOX_ENVIRONMENT_ID__
# APIFOX_MODULE_ID=__FILL_ME_APIFOX_MODULE_ID__
```

本地只验证、不导入云端：

```bash
bash scripts/verify-openapi-apifox.sh
```

Bash / WSL：

```bash
bash scripts/sync-apifox.sh
```

Windows / PowerShell 备用入口：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\sync-apifox.ps1
```

脚本执行逻辑：

1. 检查 `apifox` CLI 是否在 `PATH`。
2. 检查 `node`、`curl`、Token、项目 ID 是否可用，并检查 OpenAPI 文件。
3. 执行 `apifox login --with-token`。
4. 查询目标分支并取得 branch id；目标分支不存在时创建 `sprint` 分支。
5. 校验 `APIFOX_DEV_BASE_URL`、`APIFOX_DEV_PORT` 和 OpenAPI `servers` 均指向开发入口。
6. 调用官方 Open API `POST /v1/projects/{projectId}/import-openapi`。
7. 在请求体 `options.targetBranchId` 中显式传入目标分支 id。
8. 保存 import 输出到 `harness/reports/apifox/import-latest.log` 并解析 `data.counters`。
9. 导入后执行 `endpoint list/get` 回读，必要时执行 `environment get` 校验 Base URL。

红线：

- 创建分支成功不等于导入成功。
- `branch create --from main` 只表示 `ddd-sync` 从 `main` 创建。
- CLI `apifox import --branch` 返回的 `apiCollection` 计数不能单独证明 UI 接口管理已有内容。
- OpenAPI 云端导入必须显式指定 `APIFOX_BRANCH` 对应的 `targetBranchId`。
- 没有 Apifox 开发端口 / 开发入口配置，不允许云端导入。
- OpenAPI `servers` 不包含开发入口或开发端口，不允许云端导入。
- Evidence 必须记录 branch source、import target branch、target branch id、import API 和 import counters。
- Evidence 必须记录 endpoint list/get 回读；没有 endpoint detail 不允许声明同步成功。
- `docs-site/shared-doc` 是独立发布能力，默认不创建、不更新、不发布。

导入 counters 必须满足：

- `endpointFailed=0`
- `schemaFailed=0`
- `endpointCreated + endpointUpdated + endpointIgnored > 0`

判断云端导入是否存在接口内容时，优先使用：

```bash
apifox endpoint list --project "$APIFOX_PROJECT_ID" --branch "$APIFOX_BRANCH" --page 1 --page-size 5
apifox endpoint get <endpoint_id> --project "$APIFOX_PROJECT_ID" --branch "$APIFOX_BRANCH"
```

`apifox project get` 的项目级 `statistics.endpointCount` 不能替代分支级 endpoint 验证。

如果 UI 仍看不到接口，先确认已经切换到 `APIFOX_BRANCH` 分支，再检查当前模块筛选、目录筛选和浏览器缓存。

## 已知问题与修复口径

Observed issue:
Apifox branch was created, but endpoint documentation and endpoint details were not visible.

Root cause:
The sync did not use the Apifox development port / development endpoint.

Fix:
Apifox harness now requires development endpoint configuration before cloud import. The sync script validates `APIFOX_DEV_BASE_URL` / `APIFOX_DEV_PORT`, OpenAPI `servers`, explicit `APIFOX_BRANCH`, endpoint list/get readback, and optional environment Base URL readback.

## 验收

- `mvn -f backend/pom.xml -DskipTests compile` 通过。
- `/api/v3/api-docs/apifox` 能返回非空 OpenAPI JSON。
- `docs/openapi/saas-openapi.json` 存在且 `paths` 数量与 Controller 清单大体匹配。
- `bash scripts/verify-openapi-apifox.sh` 本地校验通过。
- 缺少 Apifox Token 时，只能标记为同步跳过，不能写成 Apifox 导入成功。
- 同步日志必须显示 `Branch source` 与 `Import target branch`，不能把分支来源当成导入目标。
- import counters 通过后，才允许声明 Apifox 云端导入 PASS。
- `apifox endpoint list/get --branch <branch>` 能查到接口详情后，才允许声明 UI 接口数据面已写入。
- `APIFOX_ENVIRONMENT_ID` 已配置时，必须校验云端 environment Base URL 匹配开发入口。
- 导入 Apifox 后人工检查目录、鉴权、请求模型、响应模型和 legacy 接口标识。
