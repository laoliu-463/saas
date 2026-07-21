# Checks

Checks 只回答“怎么证明做对了”。`harness verify` 会根据变更自动读取 [`impact-map.json`](impact-map.json) 并选择适用检查。

## 场景

- [P0 回归](scenarios/p0-regression.evals.md)
- [RBAC 与数据范围](scenarios/rbac-scope.evals.md)
- [订单归因](scenarios/order-attribution.evals.md)
- [商品库](scenarios/product-library.evals.md)
- [寄样自动完成](scenarios/sample-auto-complete.evals.md)
- [业务闭环](scenarios/business-closure.evals.md)

场景文件描述验收，不负责提交、推送或生成长期报告；执行结果进入 `runtime/qa/out/` 或 CI artifact。
