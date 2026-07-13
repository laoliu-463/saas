# Runbook: backend change

## 适用场景

后端 Controller、Service、Gateway、权限、领域逻辑或数据库读取写入行为修改。

## 前置检查

1. 读取 `AGENTS.md`、`CLAUDE.md`、`docs/README.md`、`harness/rules/governance/task-routing.md`。
2. 按领域读取 `docs/领域/*.md`、相关流程、`docs/05-API契约总表.md`、`docs/06-数据模型总表.md`。
3. 修改前使用 code-review-graph 查影响半径；图谱不足时再用 `rg`。

## 操作步骤

1. 确认业务规则来源和模块边界。
2. 做最小代码修改，不把前端展示逻辑写进后端业务层。
3. 按风险补充后端测试或 API/SQL 验证。
4. 执行固定入口：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope backend -Message "fix: backend change"
```

## 验证标准

- Maven 构建通过。
- backend 容器已重启。
- `/api/system/health` 返回 `UP`。
- 对应 API/SQL/日志能证明业务结果。
- evidence report 和 retro summary 已生成。

## 常见失败原因

- 未读取领域合同导致职责边界错放。
- 只看 HTTP 200，未验证业务字段。
- test 与 real-pre profile 混用。
- migration 或历史数据影响未说明。

## 禁止事项

- 订单域不得计算提成或最终归属。
- 配置域不得执行具体业务规则。
- 不用 try-catch 吞掉根因。
- 不直接裸 SQL 批量修 real-pre 数据。

## 产出物位置

- 代码 diff。
- 测试/日志/API/SQL 证据。
- `harness/reports/evidence-*.md`。
- `harness/reports/retro-*.md`。
