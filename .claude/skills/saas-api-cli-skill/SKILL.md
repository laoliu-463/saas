---
name: saas-api-cli-skill
description: 当前 SAAS 项目的 Apifox/OpenAPI CLI、Codex MCP 和接口测试资产接入 Skill。用于查询或同步接口文档、生成 CLI 离线 OpenAPI、定位测试用例、执行 Apifox 本地校验或云端导入证据。
---

# SAAS API CLI 与接口测试接入

## 触发场景

- 用户要求接入 Apifox、API CLI、OpenAPI、MCP 或接口文档。
- 用户要求查找某个接口对应的文档、Controller、E2E、JUnit、Vitest 或 QA 脚本。
- 用户要求把当前项目接口资产同步到 Skill 或校验 Apifox 导入结果。

## 输入

- 默认 OpenAPI：`docs/openapi/saas-openapi.json`。
- CLI/MCP 派生 OpenAPI：`docs/openapi/openapi-full.json`、`docs/openapi/openapi-business.json`、`docs/openapi/openapi-sdk-debug.json`。
- 资产清单：`references/project-assets-manifest.md`。

## 步骤

1. 先读 `docs/openapi/apifox-harness.md` 和 `docs/openapi/apifox-sync.md`。
2. 需要刷新本地 CLI/MCP 与 Skill 资产时，运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\export-api-cli-skill-assets.ps1
```

3. 需要本地验证 Apifox/OpenAPI 时，运行：

```bash
bash scripts/verify-openapi-apifox.sh
```

4. 只有用户明确要求、且本地 `.env` 有真实 `APIFOX_*` 凭证和开发入口时，才运行：

```bash
bash scripts/sync-apifox.sh
```

5. 查测试覆盖时，先读 `references/test-assets-index.md`，再按 `references/project-assets-manifest.md` 定位具体文件。

## 输出

- OpenAPI full/business/sdk-debug 离线文件。
- Skill 资产清单，覆盖接口文档与测试资产路径。
- Apifox 本地验证或云端导入 evidence。

## 验证

- `docs/openapi/openapi-full.json`、`openapi-business.json`、`openapi-sdk-debug.json` 均可解析且 paths 非空。
- `references/project-assets-manifest.md` 已记录接口文档数量和测试资产数量。
- 缺少 Apifox Token、Project ID、开发入口或 endpoint 回读时，只能写 `BLOCKED` / `PARTIAL`，不能写云端同步 `PASS`。
