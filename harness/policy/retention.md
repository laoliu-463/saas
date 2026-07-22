# 保留与证据

## 运行产物

原始日志、长输出和当前 evidence 统一写入 `runtime/qa/out/`，由 Git 忽略；CI 将需要审查的目录作为 artifact 保存。

## 仓库内容

`policy/`、`runbooks/`、`checks/`、`scripts/` 和三个模板是当前主源。历史规则、任务和工程配置移到 `docs/harness-maintenance/`；不在 `harness/` 再维护旧目录兼容副本。

## 删除或归档

删除前必须确认替代主源和引用已经更新，并在 PR 或决策文档记录范围、原因和恢复方式。报告和归档不再作为 Harness 源文件提交，历史提交仍可追溯。
