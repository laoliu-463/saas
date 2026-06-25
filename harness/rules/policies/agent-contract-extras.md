# Agent Contract Extras — 补充规范

> 本文件是 `agent-contract.md` 的补充规范。原文件聚焦"必须遵守的核心契约"，本文件聚焦"补充口径 / 证据 / 维护 / 关系"。
> 任务 ID：HARNESS-DOC-GC-OPTIMIZE-003 Step 1
> 拆分时间：2026-06-12

## 1. 状态结论口径

| 状态 | 含义 |
| --- | --- |
| `PASS` | 验证已执行且证据完整 |
| `PARTIAL` | 部分验证通过，但仍有明确未验证项或阻塞项 |
| `BLOCKED` | 外部 Token、权限包、真实样本、远端权限等阻塞 |
| `PENDING` | 未执行或样本不足，不能写成通过 |
| `FAIL` | 已复现失败，需要继续修复或回滚 |

## 2. 证据优先级

1. 自动化测试报告
2. API 响应
3. SQL 查询结果
4. 容器日志 / 后端日志
5. 页面截图 / 浏览器 Network
6. 人工描述

**不得用人工描述替代可运行脚本、SQL/API 或日志证据。**

## 3. 旧内容维护

每次任务完成前必须判断是否产生旧内容、重复内容、临时产物或过时文档。

归档或删除必须使用 manifest，不允许凭自然语言直接删除：

- `Plan`：只生成候选报告，不移动、不删除。
- `Archive`：按 manifest 移动到 `harness/archive/retired-content/<timestamp>/`。
- `Delete`：按 manifest 删除；目录删除必须在 manifest 中写 `allowRecursive=true`。

源码、脚本、Docker 配置、数据库 migration、env、密钥和 Agent 入口文档默认受保护。确需处理源码类路径，必须显式传 `-AllowSourceCode` 并完成对应构建、重启、健康检查和业务验证。

## 4. 与现有内容的关系

- `docs/`：事实主源，保留。
- `.claude/`：Claude 工作台和历史 Agent 工作流，保留。
- `scripts/`：已有启动、QA 和部署辅助脚本，保留。
- `harness/`：新增统一执行系统，负责把规则沉淀为脚本、清单、模板和报告入口。

## 5. 关联

- [[agent-contract]]：核心契约（必读）
- [[../manifests/gc/harness-doc-gc-optimize-003-manifest]]：本拆分所属 manifest
