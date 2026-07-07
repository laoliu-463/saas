# OpenAPI Reports Archive Manifest

## 结论

PASS

## 动作

为满足 `harness/reports` 每目录不超过 50 个直接文件的限制，将本次自动 evidence 和过期 latest evidence 归档。

## 归档文件

| Source | Destination |
|---|---|
| `harness/reports/evidence-20260704-133110.md` | `harness/archive/reports-20260704-openapi/evidence-20260704-133110.md` |
| `harness/reports/latest-evidence-20260630.md` | `harness/archive/reports-20260704-openapi/latest-evidence-20260630.md` |

## 保留文件

- `harness/reports/latest-evidence-20260704.md`
- `harness/reports/latest-harness-limits-check.md`

## 风险

- 归档不改变原 evidence 内容，只移动路径。
