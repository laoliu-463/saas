---
name: explore-codebase
description: 使用 code-review-graph 理解项目结构、模块关系、调用流和复杂度的兼容入口。
---

# 探索代码库（兼容入口）

> [历史兼容] 项目上下文优先读取 `AGENTS.md`、`CLAUDE.md`、`docs/README.md` 和 `.claude/mcp/README.md`；本入口只负责图谱导航。

## 触发场景

- 需要在修改前理解模块、函数、类、调用链、流程或高复杂度代码。

## 输入与步骤

- 输入：目标领域、文件/接口、要回答的问题和已知约束。
- 必须先调用 `get_minimal_context(task="...")`；图谱不可用时标记 `BLOCKED`，再用文件检索补充并注明限制。
- 按需调用 `list_graph_stats`、`get_architecture_overview`、`list_communities`、`get_community`、`semantic_search_nodes`、`query_graph`、`list_flows`、`get_flow`、`find_large_functions`。
- 先宽后窄：结构 → 社区/模块 → 目标节点 → callers/callees/imports → 流程和测试。

## 输出与验证

输出结构、关键节点、依赖方向、影响范围、未确认项和建议入口；不把目录名当作架构事实。

- 结论必须带图谱节点/文件证据，图谱缺失时标注数据边界。
- 修改代码时仍需执行项目测试、构建和 evidence；本入口本身不执行部署。
