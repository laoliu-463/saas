# Skill: evidence-report

## 使用场景

用于每次修复、排障、部署、验收或文档治理后的证据报告生成与审查。

## 必读文件

- `harness/rules/policies/agent-contract.md`
- `docs/09-测试验收总览.md`
- `docs/验收/验收证据索引.md`

## 禁止事项

- 禁止无证据宣称完成。
- 禁止把未采集项伪造成已采集。
- 禁止把 `BLOCKED` / `PENDING` 写成 `PASS`。
- 禁止输出密钥、Token、密码。

## 标准流程

1. 记录时间、环境、分支、commit。
2. 记录工作区状态。
3. 记录构建命令和结果。
4. 记录 Docker 状态。
5. 记录健康检查。
6. 记录业务验证。
7. 若部署远端，记录远端步骤和健康检查。
8. 给出 `PASS` / `PARTIAL` / `FAIL` 结论。
9. 列出未采集项和剩余风险。

## 验证方式

- `harness/scripts/commands/collect-evidence.ps1` 生成 `harness/reports/evidence-*.md`。
- 报告中未采集项必须写“未采集 / 阻塞原因”。

## 输出格式

```md
报告路径：
结论：
关键证据：
未采集项：
剩余风险：
```
