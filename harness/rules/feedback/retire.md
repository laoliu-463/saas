# 旧内容生命周期规则

## 目标

防止文档和代码在多轮 AI 修改后积累重复、过时、冲突或临时内容。每次任务结束前都要判断是否需要整理、归档或删除旧内容。

## 默认流程

```text
任务完成
-> 生成旧内容维护计划
-> 判断 keep / update / archive / delete
-> 需要归档或删除时编写 manifest
-> 执行 Archive 或 Delete
-> 覆盖稳定 content-retire 报告
-> evidence 记录结果并内联 retro 结论
```

默认只执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\retire-content.ps1 -Action Plan
```

## 分类

| 分类 | 处理方式 | 例子 |
| --- | --- | --- |
| 当前事实主源 | keep | `docs/01-V1交付范围与边界.md`、`docs/领域/*.md` |
| 兼容入口 | keep / update | `docs/01-V1交付合同.md` 等只引用主源的文件 |
| 历史背景 | archive | 旧 V2.2、旧 FastAPI / Celery 方案 |
| 重复文档 | archive 或 delete | 被新主源完全替代且引用已迁移的文档 |
| 临时产物 | delete | 顶层 `snap*.txt`、临时 `.log`、临时 json |
| 验收证据 | keep / retention 后 archive | `runtime/qa/out/`、`harness/reports/` |
| 源码旧实现 | delete only after full validation | 已被新实现替代且测试证明无引用 |

## 归档规则

- 归档必须使用 manifest。
- 默认归档到按日期/主题分桶的 `harness/archive/` 子目录；同一分桶直接文件和子目录均不得超过 50。
- 归档前必须确认新主源已存在，并且引用入口已更新。
- 归档报告覆盖 `harness/reports/current/latest-content-retire.md`。
- 同批多类文件用单段 `archiveGroup` 分组，禁止 `..`、绝对路径和多级 group。

## 删除规则

删除必须满足：

- 有 manifest。
- 有删除理由。
- 不是 env、密钥、compose、数据库 migration、Git 元数据或 Agent 入口文档。
- 删除目录时 manifest 必须写 `allowRecursive=true`。
- 删除源码类路径时必须显式 `-AllowSourceCode`，并完成构建、容器重启、健康检查和业务验证。

## manifest 示例

```json
{
  "items": [
    {
      "path": "doc/计划.md",
      "action": "archive",
      "archiveGroup": "historical-plan",
      "category": "historical-plan",
      "reason": "旧 FastAPI/Celery/V2 口径，仅作历史背景"
    },
    {
      "path": "snap1.txt",
      "action": "delete",
      "category": "transient-root-file",
      "reason": "一次性调试快照，已无引用"
    },
    {
      "path": "tmp",
      "action": "delete",
      "category": "generated-output",
      "reason": "临时输出目录，已完成证据迁移",
      "allowRecursive": true
    }
  ]
}
```

## 禁止事项

- 禁止无 manifest 直接归档或删除。
- 禁止用删除替代冲突分析。
- 禁止删除真实验收证据后再声明验证通过。
- 禁止删除 `.env*`、密钥、证书、数据库 migration、Docker Compose、Git 元数据。
- 禁止删除源码后不构建、不重启、不验证。
