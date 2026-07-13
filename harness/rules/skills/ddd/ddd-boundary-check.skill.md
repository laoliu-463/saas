# Skill: ddd-boundary-check

## 使用场景

用于任何 DDD、领域边界、跨域调用、业务规则下沉、Controller 逻辑治理或 Repository 穿透检查任务。

## 必读文件

- `harness/rules/governance/forbidden-scope.md`
- `harness/DOMAIN_MAP.md`
- `docs/03-领域架构总览.md`
- 当前领域 `docs/领域/*.md`
- 当前领域 `harness/rules/instructions/domain/*.md`

## 检查清单

- 主责领域是否明确。
- 上游输入和下游输出是否明确。
- 是否跨领域直接访问 Repository。
- 是否把业务规则写入 Controller、前端或数据库隐式逻辑。
- 是否让订单域计算提成或写业绩。
- 是否让分析模块重算归因。
- 是否让配置域执行业务规则。
- 是否引入 V1 禁止项。

## 输出格式

```md
主责领域：
关联领域：
允许调用：
禁止调用：
发现的越界风险：
需要补充的证据：
阶段性结论：
```
