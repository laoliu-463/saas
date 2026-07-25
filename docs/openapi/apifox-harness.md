# Apifox OpenAPI Harness

## 目标

Apifox / OpenAPI 同步不是一次性脚本，而是接口文档交付链路的一部分。每次接口相关修改后，都必须通过本地 harness；真实云端同步只能在凭证、开发入口和目标分支都验证后显式执行。

本 harness 不修改业务代码、API 路径、请求/响应字段、schema、权限、状态机、金额、佣金、提成或归因规则。

## 触发范围

修改以下内容后必须运行 Apifox harness：

- Controller、DTO、VO、Request、Response。
- SwaggerConfig、springdoc / Knife4j 配置、JWT bearerAuth、OpenAPI servers。
- OpenAPI 导出脚本、Apifox 同步脚本、API 路由、权限注解。
- `docs/openapi/saas-openapi.json` 和 `docs/openapi/` 文档。

## 验证分层

### 0. CLI / MCP / Skill Asset Guard

- 运行 `scripts/export-api-cli-skill-assets.ps1` 从 `docs/openapi/saas-openapi.json` 生成三份离线源：
  - `docs/openapi/openapi-full.json`
  - `docs/openapi/openapi-business.json`
  - `docs/openapi/openapi-sdk-debug.json`
- `.codex/config.toml` 和 `docs/.apifox-mcp-config.json` 必须指向上述可生成文件。
- `.claude/skills/saas-api-cli-skill/` 只保存 Skill 流程和索引，不复制接口 / 测试源文件正文，避免双写漂移。
- `references/project-assets-manifest.md` 只记录接口文档数量、测试资产根目录和计数；具体文件路径通过 `rg --files` 查询，避免提交重复的逐文件清单。

### A. Local OpenAPI Guard

- JSON 可解析，`openapi`、`paths`、`operations`、`components.schemas` 可统计。
- `servers` 必须存在；`components.securitySchemes` 必须包含 `bearerAuth`。
- 抽样 operation 必须有 method、path、responses。
- 配置真实 `APIFOX_DEV_BASE_URL` / `APIFOX_DEV_PORT` 时，OpenAPI `servers` 必须包含开发入口或端口。

### B. Apifox CLI Guard

- `apifox -v`、`apifox import --help` 可执行。
- `import --help` 必须支持 `--project`、`--format`、`--file`、`--branch`。
- `endpoint list/get --help` 必须支持 `--branch`，否则无法证明目标分支回读。
- `environment list/get --help` 不可用时记 WARN；真实 environment 校验只能在 CLI 支持时执行。

### C. Development Endpoint Guard

- 必须声明 `APIFOX_DEV_BASE_URL` 和 `APIFOX_DEV_PORT`。
- 云端导入时二者必须是真实值，且 base URL 必须包含端口。
- OpenAPI `servers` 必须匹配开发入口。
- 配置 `APIFOX_ENVIRONMENT_ID` 后，云端 environment Base URL 必须匹配开发入口。
- 没有开发入口，不允许云端导入。

### D. Branch Guard

- `branch create --from main` 只表示分支来源，不代表导入成功。
- 导入必须显式 target `APIFOX_BRANCH`；本项目默认 `ddd-sync`。
- 当前 Bash 同步入口使用 Apifox 官方 Open API，并在请求体中传入 `options.targetBranchId`。
- Evidence 必须记录 branch source、target branch、target branch id 和 import API。

### E. Secret Safety Guard

- `.env` 必须被 Git 忽略，且不允许 staged。
- Token、Authorization header、真实 Project ID 不允许进入文档、日志、evidence 或 commit message。
- Evidence 中 Project ID 只能写脱敏值；开发入口可按 configured/matched 记录。
- Staged diff secret scan 必须 PASS。

### F. Cloud Sync Guard

- 默认不执行真实云端导入。
- 只有真实凭证、真实 Project ID、真实开发入口、真实 OpenAPI 文件和用户明确要求同时满足时，才运行 `bash scripts/sync-apifox.sh`。
- 导入后必须验证 import counters、endpoint list 非空、至少 1-3 个 endpoint get 有 method/path/parameters 或 requestBody/responses。
- 没有 endpoint detail，不允许声明同步成功。

### G. Docs Site Guard

- `apifox import` 只代表接口定义导入。
- docs-site/shared-doc 是单独文档发布能力。
- 默认不创建、不更新、不发布 docs-site/shared-doc；发布必须单独 evidence。

## 红线

- 禁止只凭 branch created 判断成功。
- 禁止只凭 OpenAPI file exists 判断成功。
- 禁止没有开发端口配置就执行云端导入。
- 禁止没有 endpoint get 详情就声明同步成功。
- 禁止把真实 Token 写入任何 Git 文件。
- 禁止默认发布 docs-site/shared-doc。
- 禁止省略 `APIFOX_BRANCH` 后依赖 main 默认行为。
- 禁止删除 JWT bearerAuth 或 OpenAPI servers。

## 命令

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\export-api-cli-skill-assets.ps1
```

```bash
bash scripts/verify-openapi-apifox.sh
bash scripts/sync-apifox.sh
```

Windows harness 入口：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope apifox -Message "chore(harness): add Apifox OpenAPI verification gate"
```

如 PATH 中的 `bash` 指向 WSL 且 WSL 不可用，使用 Git Bash 绝对路径，例如：

```powershell
& "D:\DevTools\Git\Git\bin\bash.exe" scripts/verify-openapi-apifox.sh
```
