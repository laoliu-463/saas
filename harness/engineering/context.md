# Domain Docs & Engineering Context

> **状态**：本目录是 Matt Pocock engineering skills 的配置入口。旧版位于 `docs/agents/domain.md`，已**合并重构到本目录**（DDD-MIGRATION-100 Phase 0）。

本仓库当前按**单上下文模式**接入 Matt Pocock engineering skills。工程类 skill（`/grill-with-docs` / `/diagnosing-bugs` / `/tdd` / `/implement` / `/to-issues` / `/to-prd`）在探索代码前，应按下面顺序消费领域文档。

## Before exploring, read these

1. 根目录 [`AGENTS.md`](../../AGENTS.md) —— **第一必读**，包含项目协议 + Agent skills 引用
2. 根目录 [`CONTEXT.md`](../../CONTEXT.md) —— 项目领域词汇表
3. 根目录 [`CLAUDE.md`](../../CLAUDE.md) —— Java 全栈工程规范（补充）
4. 主干文档 [`docs/README.md`](../../docs/README.md)（如果存在）
5. 当前任务最相关的主文档，至少优先看：
   - [`docs/04-开发进度.md`](../../docs/04-开发进度.md)
   - [`docs/01-业务闭环.md`](../../docs/01-业务闭环.md)
   - [`docs/02-架构设计.md`](../../docs/02-架构设计.md)
6. 若任务涉及接口、环境、联调，再读：
   - [`docs/03-Test与Real网关契约.md`](../../docs/03-Test与Real网关契约.md)
   - [`docs/05-接口与数据模型.md`](../../docs/05-接口与数据模型.md)
   - [`docs/06-部署与对接计划.md`](../../docs/06-部署与对接计划.md)
7. 若任务涉及专项，继续遵守 `AGENTS.md` 中的专项阅读要求

如果某类补充文档不存在或当前任务不涉及，对应 skill 应静默跳过，不需要为了"文档齐全"而先造文档。

## 工程 Skill 配置（本目录）

| 文件 | 用途 |
| --- | --- |
| [`issue-tracker.md`](./issue-tracker.md) | Issue tracker 配置（GitHub + harness 索引） |
| [`triage-labels.md`](./triage-labels.md) | 5 个 canonical triage 标签 |
| [`issues-index.md`](./issues-index.md) | GitHub Issues 本地镜像（待建立） |

## File structure

当前仓库是 **single-context repo**：

```text
/
├── AGENTS.md                       ← 主入口（项目协议 + Agent skills 引用）
├── CLAUDE.md                       ← Java 全栈工程规范
├── CONTEXT.md                      ← 项目领域词汇表
├── docs/
│   ├── README.md
│   ├── 00-项目总览.md
│   ├── 01-业务闭环.md
│   ├── 02-架构设计.md
│   ├── 03-Test与Real网关契约.md
│   ├── 04-开发进度.md
│   ├── 05-接口与数据模型.md
│   ├── 06-部署与对接计划.md
│   ├── 决策/                        ← ADR 收口（已有 ADR-001~010）
│   └── ...
├── harness/                        ← 核心执行引擎 + 工程 Skill 配置
│   ├── README.md
│   ├── INDEX.md
│   ├── rules/                       ← 长期规则、执行规范、质量门禁
│   ├── tasks/                       ← 可执行任务卡
│   ├── probes/                      ← 只读探针
│   ├── reports/                     ← 最新报告
│   ├── scripts/                     ← 自动化脚本
│   ├── manifests/                   ← 清理/删除证据
│   ├── archive/                     ← 历史归档
│   ├── templates/                   ← 任务/报告模板
│   └── engineering/                 ← Matt Pocock engineering skill 配置 ⭐
│       ├── issue-tracker.md
│       ├── triage-labels.md
│       ├── context.md
│       └── issues-index.md
└── backend/ frontend/
```

当前是 single-context repo，**ADR 统一收口在 `docs/决策/`**（已有 ADR-001~010）。`docs/adr/` 不再启用，避免与 `docs/决策/` 双向漂移。

## Use the glossary's vocabulary

- 输出中涉及领域术语时，优先使用 `CONTEXT.md` 中已收口的叫法。
- 若 `CONTEXT.md` 与 `docs/*.md` 用词冲突，以更新日期更近且与当前阶段一致的主干文档为准，并在修改时同步收口。
- 不要把通用工程词硬塞进 `CONTEXT.md`；它只负责本项目特有的业务和环境术语。

## Project-specific rule

对本仓库来说，Matt Pocock skills 是执行方法，**不是业务总指挥**。凡是与项目口径冲突时，遵循以下优先级：

1. **用户当前直接要求**
2. **`AGENTS.md`**
3. **当前阶段相关 `docs/*.md`**
4. **`CONTEXT.md`**
5. **`harness/engineering/`**
6. **skill 自身默认流程**

## Flag ADR conflicts

skill 在输出与 `docs/决策/ADR-*.md` 冲突的方案时必须显式指出冲突，而不是静默覆盖。若发现旧文档与新事实冲突，写入 `docs/决策/ADR-002-V1范围优先级.md`，不要自行拍板。

## 相关文件

- [`harness/engineering/issue-tracker.md`](./issue-tracker.md) —— Issue tracker 配置
- [`harness/engineering/triage-labels.md`](./triage-labels.md) —— 标签映射
- [`harness/INDEX.md`](../INDEX.md) —— Harness 总入口

## 变更历史

- **v1.0**（初始）：位于 `docs/agents/domain.md`，single-context 模式
- **v2.0**（2026-06-19）：迁移到 `harness/engineering/context.md`，与 harness 完全融合