# SAAS API CLI / Skill Asset Manifest

- Source OpenAPI: docs/openapi/saas-openapi.json
- Full OpenAPI: docs/openapi/openapi-full.json
- Business OpenAPI: docs/openapi/openapi-business.json
- SDK debug OpenAPI: docs/openapi/openapi-sdk-debug.json
- Interface docs indexed: 14
- Test assets indexed: 892

## Contents

- OpenAPI outputs
- Interface docs
- Test assets

## OpenAPI Outputs

| Asset | Path | Paths | Operations | Schemas | Tags |
| --- | --- | ---: | ---: | ---: | ---: |
| Full | docs/openapi/openapi-full.json | 221 | 252 | 345 | 32 |
| Business | docs/openapi/openapi-business.json | 199 | 227 | 345 | 31 |
| SDK debug | docs/openapi/openapi-sdk-debug.json | 22 | 25 | 345 | 2 |

## Asset Roots

The manifest records counts and stable source roots only. Detailed file lists are intentionally not committed; use `rg --files` in the roots below to locate the current asset.

| Asset type | Root | Count |
| --- | --- | ---: |
| Interface docs | `docs/` | 14 |
| Browser / shared tests | `tests/` | 91 |
| Backend tests | `backend/src/test/` | 538 |
| Frontend tests and fixtures | `frontend/src/` | 190 |
| QA / real-pre scripts | `runtime/qa/` | 72 |
| QA helpers | `scripts/qa/` | 1 |

## Lookup

- Interface contract: `docs/` and the OpenAPI files listed above.
- Test paths: `rg --files tests backend/src/test frontend/src runtime/qa scripts/qa`.
- Refresh this summary with `scripts/export-api-cli-skill-assets.ps1`.
- This file is an index, not evidence that any test or cloud sync passed.
