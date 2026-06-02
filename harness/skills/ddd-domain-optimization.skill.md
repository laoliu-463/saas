# Skill: ddd-domain-optimization

## 使用场景

用于执行 DDD 领域优化任务卡，例如 `U-1`、`O-3`、`S-12`。目标是确保每次只推进一个领域任务，并保留可验证证据。

## 必读文件

- `harness/AGENT_CONTRACT.md`
- `harness/TASK_ROUTING.md`
- `harness/FORBIDDEN_SCOPE.md`
- `harness/plans/DDD_OPTIMIZATION_ROADMAP.md`
- `harness/plans/DDD_DOMAIN_TASK_MATRIX.md`
- 当前领域对应的 `harness/instructions/*.md`
- 当前领域对应的 `docs/领域/*.md`

## 禁止事项

- 禁止一次任务跨多个领域顺手重构。
- 禁止跳过边界检查直接改代码。
- 禁止把未验证任务写成完成。
- 禁止扩大 V1 禁止范围。

## 标准流程

1. 明确任务卡编号、主责领域和预期产物。
2. 读取对应领域 instruction 和领域合同。
3. 使用 `ddd-boundary-check.skill.md` 做边界检查。
4. 执行最小变更。
5. 按 Scope 运行固定验证。
6. 使用 `ddd-post-task-sync.skill.md` 同步 state、feedback 和 evidence。

## 验证方式

- 能指出任务卡编号、修改文件、未触碰范围和验证证据。
- `DOMAIN_STATUS.md` 已更新，evidence report 已生成。
