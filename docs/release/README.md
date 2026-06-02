# release 审查报告索引

本目录用于归档 real-pre 上线前总审查报告。

当前口径：

- real-pre 受控部署不等于正式生产全量上线。
- `PENDING` / `BLOCKED` 不得写成 `PASS`。
- 真实推广写开关在 real-pre 默认开启：`DOUYIN_REAL_PROMOTION_WRITE_ENABLED=true`、`ALLOW_REAL_PROMOTION_WRITE=true`；如临时关闭，必须记录原因、影响范围和恢复计划。
- 真实 Token、订单样本、归因、寄样和业绩证据不足时，只能记录为 `PENDING` 或 `BLOCKED`。

每次审查应同时生成：

- `docs/release/real-pre上线总审查报告-YYYYMMDD-HHMMSS.md`
- `runtime/qa/out/release-check-YYYYMMDD-HHMMSS/summary.json`
- `runtime/qa/out/release-check-YYYYMMDD-HHMMSS/report.md`

## 最近审查

- [real-pre上线总审查报告-20260528-171543.md](real-pre上线总审查报告-20260528-171543.md)：结论为 `可受控部署`，仍保留真实凭据、真实订单样本和服务器证据相关 `PENDING` / `BLOCKED`。
