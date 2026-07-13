# Harness 结构政策

> 本文件是 `harness/` 结构限制的唯一主源。保留周期见 `file-retention-policy.md`，报告格式见 `report-style-policy.md`。

## 一级目录白名单

只允许以下 9 个一级目录：

- `rules/`：长期规则、执行规范、质量门禁。
- `tasks/`：当前任务卡。
- `probes/`：只读探针和验证说明。
- `reports/`：当前有效报告。
- `scripts/`：自动化脚本。
- `manifests/`：归档和删除清单。
- `archive/`：历史归档。
- `templates/`：任务、报告和提示词模板。
- `engineering/`：工程 Skill 项目配置。

`README.md` 和 `INDEX.md` 是一级文件，不计入目录白名单。

## 硬限制

- 任意目录的直接文件数不超过 50。
- 任意目录的直接子目录数不超过 50。
- 脚本以外的文本文件不超过 200 行。
- `README.md` 建议不超过 120 行。
- 禁止用空目录或占位 README 制造表面合规。

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
powershell -ExecutionPolicy Bypass -File harness/scripts/check-harness-limits.ps1
```

检查失败时不得把 Harness 结构结论写成 PASS。
