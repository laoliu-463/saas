# Domain Docs

本仓库当前按单上下文模式接入 Matt Pocock engineering skills。工程类 skill 在探索代码前，应按下面顺序消费领域文档。

## Before exploring, read these

1. 根目录 [AGENTS.md](/D:/Projects/SAAS/AGENTS.md)
2. 根目录 [CONTEXT.md](/D:/Projects/SAAS/CONTEXT.md)
3. 主干文档 [docs/README.md](/D:/Projects/SAAS/docs/README.md)
4. 当前任务最相关的主文档，至少优先看：
   - [docs/04-开发进度.md](/D:/Projects/SAAS/docs/04-开发进度.md)
   - [docs/01-业务闭环.md](/D:/Projects/SAAS/docs/01-业务闭环.md)
   - [docs/02-架构设计.md](/D:/Projects/SAAS/docs/02-架构设计.md)
5. 若任务涉及接口、环境、联调，再读：
   - [docs/03-Test与Real网关契约.md](/D:/Projects/SAAS/docs/03-Test与Real网关契约.md)
   - [docs/05-接口与数据模型.md](/D:/Projects/SAAS/docs/05-接口与数据模型.md)
   - [docs/06-部署与对接计划.md](/D:/Projects/SAAS/docs/06-部署与对接计划.md)
6. 若任务涉及专项，继续遵守 `AGENTS.md` 中的专项阅读要求

如果某类补充文档不存在或当前任务不涉及，对应 skill 应静默跳过，不需要为了“文档齐全”而先造文档。

## File structure

当前仓库是 single-context repo：

```text
/
├── AGENTS.md
├── CONTEXT.md
├── docs/
│   ├── README.md
│   ├── 00-项目总览.md
│   ├── 01-业务闭环.md
│   ├── 02-架构设计.md
│   ├── 03-Test与Real网关契约.md
│   ├── 04-开发进度.md
│   ├── 05-接口与数据模型.md
│   ├── 06-部署与对接计划.md
│   ├── 09-真实SDK联调准备清单.md
│   ├── 10-V2.2场景覆盖矩阵.md
│   └── archive/
└── backend/ frontend/
```

当前没有强制启用 `docs/adr/`。如果未来确实出现“难以回滚、没有上下文就会令人困惑、且基于真实权衡”的决策，再由相应 skill 或人工按需创建。

## Use the glossary's vocabulary

- 输出中涉及领域术语时，优先使用 `CONTEXT.md` 中已收口的叫法。
- 若 `CONTEXT.md` 与 `docs/*.md` 用词冲突，以更新日期更近且与当前阶段一致的主干文档为准，并在修改时同步收口。
- 不要把通用工程词硬塞进 `CONTEXT.md`；它只负责本项目特有的业务和环境术语。

## Project-specific rule

对本仓库来说，Matt Pocock skills 是执行方法，不是业务总指挥。凡是与项目口径冲突时，遵循以下优先级：

1. 用户当前直接要求
2. `AGENTS.md`
3. 当前阶段相关 `docs/*.md`
4. `CONTEXT.md`
5. skill 自身默认流程

## Flag ADR conflicts

如果未来仓库引入 `docs/adr/`，skill 在输出与 ADR 冲突的方案时应显式指出冲突，而不是静默覆盖。
