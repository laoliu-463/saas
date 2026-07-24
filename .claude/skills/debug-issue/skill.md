---
name: debug-issue
description: 使用 code-review-graph 定位代码问题的兼容入口；业务部署问题优先使用项目中文技能。
---

# 调试问题（兼容入口）

> [历史兼容] 部署问题优先使用 `.claude/skills/部署排障/SKILL.md`，领域问题优先使用 `.claude/skills/领域审计/SKILL.md`。本入口只保留图谱调试能力。

## 触发场景

- 用户提供可复现缺陷，需要追踪调用链、近期变更和影响范围。

## 输入

- 输入：现象、最小复现、目标文件/接口、环境和最近变更。

## 步骤
- 图谱不可用时先标记 `BLOCKED`，不得伪造图谱结果；可在记录原因后用文本检索补充。
- 必须先调用 `get_minimal_context(task="...")`，再按需使用 `semantic_search_nodes`、`query_graph`、`get_flow`、`detect_changes`、`get_impact_radius`。
- 先找入口，再查 callers/callees、事件/数据路径和近期变更，最后验证最小复现与测试覆盖。

## 输出

输出根因、代码位置、证据链、影响范围、最小修复和验证方式；区分事实与推论。

## 验证

- 不把静态线索写成已修复，不把图谱结果写成运行验证。
- 涉及代码修改时遵守项目构建、容器、健康检查和 evidence 要求；只诊断时明确未执行项。
- 不输出密钥；不直接部署或修改生产环境。
