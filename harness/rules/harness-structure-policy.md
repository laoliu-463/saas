# Harness 结构政策

> 本文件是 `harness/` 结构限制的唯一主源。保留周期见 `file-retention-policy.md`，报告格式见 `report-style-policy.md`。

## 一级目录白名单

依据 ADR-014 对 ADR-013 的扩展，只允许以下 13 个一级目录：

- `rules/`：长期规则、执行规范、质量门禁。
- `tasks/`：当前任务卡。
- `probes/`：只读探针和验证说明。
- `reports/`：当前有效报告。
- `scripts/`：自动化脚本。
- `manifests/`：归档和删除清单。
- `archive/`：历史归档。
- `templates/`：任务、报告和提示词模板。
- `engineering/`：工程 Skill 项目配置。
- `src/`：Node / TypeScript Harness 核心源码。
- `contracts/`：JSON Schema 与机器可读策略。
- `state/`：稳定发布、架构或验证基线；按需创建，禁止放入运行时产物。
- `tests/`：Harness 自身测试。

`README.md` 和 `INDEX.md` 是一级文件，不计入目录白名单。

## 分层预算与硬限制

- 活跃目录的直接文件/子目录 40 预警、50 硬上限。
- 非脚本文本 160 行预警、200 行硬上限；脚本改由测试、语法检查和职责边界约束。
- `reports/` 根目标直接文件数不超过 20；当前摘要写入 `reports/current/latest-<topic>.md`。
- `README.md` 建议不超过 120 行。
- 禁止用空目录或占位 README 制造表面合规；新增目录只在有真实职责和内容时创建。
- 原始日志、临时数据和逐步命令输出继续进入 `runtime/qa/out/<run-id>/`，禁止进入 `state/`。

本地以 `HEAD` 为基线。历史超限保持或下降只影响 `REPOSITORY_HEALTH`，不阻断当前任务；新增/恶化硬违规使 `TASK_GATE=FAIL`。归档分桶继续遵守 50/50，历史不可变证据原样迁移不追溯 200 行限制。

## 收敛动作

| 动作 | 使用条件 |
| --- | --- |
| KEEP | 当前有效且内容唯一 |
| MERGE | 同主题内容重复，合并到主源 |
| ARCHIVE | 有追溯价值但不再是当前入口 |
| DELETE | 临时文件、空占位或已被主源完全替代 |
| RENAME | 命名或路径不符合当前结构 |

删除或归档前必须有 manifest，并确认替代主源和引用已经更新。

## 禁止事项

- 禁止在 `reports/` 根持续平铺带时间戳的 evidence、retro、content-retire。
- 禁止在 Harness 中重复维护 `docs/` 已有的业务事实。
- 禁止把历史报告、任务流水和当前规则混在同一文件。
- 禁止删除源码、配置、环境文件、数据库 migration 或当前生效脚本。

## 校验

任务结束前运行：

```powershell
powershell -ExecutionPolicy Bypass -File harness/scripts/check-harness-limits.ps1 -BaselineRef HEAD
```

检查失败时不得把 Harness 结构结论写成 PASS。
