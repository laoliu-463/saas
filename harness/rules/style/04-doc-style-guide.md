# Harness 文档风格指南

## 1. 行数限制

- 当前可执行文档：不超过 200 行
- README.md：不超过 120 行
- 索引文件：不超过 200 行
- archive 下的历史文件：不限制

## 2. 单一职责

每个文档只解决一个问题。不要在同一个文件里既写规则又写示例又写报告。

## 3. 超长文档处理

| 情况 | 处理 |
|---|---|
| 规则文档 > 200 行 | 拆分为主文件 + 子文件 |
| 历史报告 > 200 行 | 移到 `reports/archive/YYYYMMDD/` |
| 长日志 / 终端输出 | 不入文档，放 archive 或 reports |
| 重复内容 | 删除重复，保留一份 |

## 4. 拆分规则

```text
原文件：harness/core/task-lifecycle.md
拆分为：
  harness/core/task-lifecycle.md          (主文件: 目标+流程+链接)
  harness/core/task-lifecycle.checklist.md (子文件: 检查清单)
  harness/core/task-lifecycle.examples.md  (子文件: 示例)
```

## 5. 报告规范

每份报告必须包含：
1. 结论（PASS / PARTIAL / FAIL）
2. 证据（命令输出、截图、SQL 结果）
3. 风险
4. 后续动作

## 6. 删除规范

删除前必须生成 Manifest（`harness/manifests/gc/`），记录：
- 删除路径
- 删除原因
- 替代文件

## 7. 禁止

- 不把一次性任务沉淀成永久规则
- 不重复记录已在领域文档中存在的业务规则
- 不在当前执行入口堆积历史背景
- 不让 README 变成巨型说明书
