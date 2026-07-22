# API 文档索引

## 主源

- `docs/05-API契约总表.md`：内部 API 总入口。
- `docs/openapi/saas-openapi.json`：Apifox 导入源。
- `docs/openapi/openapi-full.json`：CLI/MCP 全量离线源。
- `docs/openapi/openapi-business.json`：业务接口离线源。
- `docs/openapi/openapi-sdk-debug.json`：抖音 / 上游联调辅助接口离线源。

## 领域接口梳理

- `docs/05-API契约总表.md`：领域接口的统一入口。
- `docs/接口/`：需要业务语义说明时，按领域读取接口契约。
- `docs/验收/`：接口验证、真实环境和证据要求。

## Apifox 与 CLI

- `docs/openapi/apifox-harness.md`
- `docs/openapi/apifox-sync.md`
- `docs/.apifox-mcp-config.json`
- `.codex/config.toml`

## 使用规则

- OpenAPI 是接口事实，不等于业务验收通过。
- `/douyin/**`、webhook、OAuth、probe 类路径只作为 SDK / 上游联调辅助，不直接写成主业务闭环通过。
- Apifox 云端导入必须有 import counters 与 endpoint list/get 回读证据。
