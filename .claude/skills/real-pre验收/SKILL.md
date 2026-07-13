# real-pre验收

description: 执行或审计 real-pre 真实联调验收。

## 步骤

[V1 必做] 先读 `harness/rules/governance/forbidden-scope.md` 和 `harness/rules/environment/envs/real-pre-env.md`。

[V1 必做] 跑预检：`npm run e2e:real-pre:p0:preflight`。

[V1 必做] 统一验收：`npm run e2e:real-pre:p0`。

[V1 必做] 检查 `runtime/qa/out/real-pre-p0-*/summary.json` 和 `report.md`。

[V1 必做] 不能用细分脚本报告替代统一验收报告。
