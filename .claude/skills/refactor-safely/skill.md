---
name: refactor-safely
description: 使用依赖图谱规划可回滚的代码重构，先预览影响再应用变更。
---

# 安全重构（兼容入口）

## 触发场景

- 用户要求重命名、拆分、删除死代码、调整模块边界或降低复杂度。

## 输入

- 输入：重构目标、约束、允许修改范围、测试基线和发布风险。

## 步骤
- 必须先调用 `get_minimal_context(task="...")`，再用 `get_impact_radius`/`get_affected_flows` 确认影响；图谱不可用时标记 `BLOCKED`，不得凭感觉大改。
- 用 `refactor_tool` 的 `suggest` 或 `dead_code` 形成候选；重命名先用 `rename` 预览完整编辑清单。
- 只有用户授权且预览无遗漏时，才用 `apply_refactor_tool` 应用；完成后用 `detect_changes` 复核。
- 对高复杂度目标可用 `find_large_functions`，但不能把“复杂”直接等同于“应拆分”。

## 安全边界

- 不删除未确认的公共 API、迁移、事件、权限规则或真实数据；不直接修改 main、release 或生产。
- 代码修改遵守最小变更、单一职责和项目模块边界；数据库迁移必须另行说明兼容与回滚。

## 输出

输出影响半径、候选方案、实际改动、测试覆盖、回滚方式和剩余风险。

## 验证

- 代码修改后必须按项目 Definition of Done 构建、重启、健康检查、业务验证并生成 evidence。
- 只做计划或审计时明确未执行项；没有验证不得写 `PASS`。
