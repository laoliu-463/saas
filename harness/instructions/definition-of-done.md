# Definition of Done

## 完成条件

任务只有同时满足以下条件，才允许声明完成：

- 代码或文档已修改。
- 构建通过，或明确说明 docs-only 不需要构建。
- 对应容器已重启，或明确说明 docs-only 不需要重启。
- 健康检查通过，或明确说明阻塞原因。
- 相关业务验证通过，或明确说明阻塞原因。
- Git commit 已生成。
- Git push 已完成。
- 如果用户要求远端部署，远端部署已完成。
- evidence report 已生成。
- retro summary 已生成，或明确说明本次无需 Harness 升级。
- 剩余风险已列出。

## docs-only 例外

`Scope=docs` 可以跳过构建、容器重启和业务 E2E，但必须：

- 执行 `safety-check.ps1`。
- 执行 `collect-evidence.ps1`。
- 执行 `new-retro.ps1`。
- 在报告中写明构建 / 重启 / 健康检查未执行。

## 禁止结论

- 未验证不得写 PASS。
- `BLOCKED` / `PENDING` / `PARTIAL` 不能写成 PASS。
- 脚本失败后不能声明成功。

