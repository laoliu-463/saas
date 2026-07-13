# Harness Retro Summary

## 任务

- 主题：商品库服务费率与双佣字段展示
- 环境：real-pre（本地）
- 分支：`codex/ddd-user-role-application`
- 代码提交：`67c6a0fb`

## 做得好的地方

- 先用真实数据库只读样本确认 `service_ratio`、`ad_service_ratio` 和 `activity_ad_cos_ratio` 的字段职责，再补红测。
- 前端、后端分别固化了 `100%`、错误 `0%`、显式 `0` 和双佣字段分离的回归测试。
- 固定 agent-do 完成后端/前端构建、real-pre 容器重启和健康检查；结果均通过。
- 未执行远端部署，未修改 real-pre 数据库、Redis 或真实上游开关。

## 暴露的问题

- real-pre P0 preflight 因 admin 登录连续 HTTP 401 失败，token readiness 为 `BLOCKED_AUTH`；认证业务流无法执行。
- `git-push-safe.ps1` 会收集工作区全部变更；本次运行中发现一个并非本任务范围的商品寄样设置测试文件，已记录为待清理项，后续不得继续依赖全量自动暂存。
- GitHub origin 在 gitee 推送成功后出现非 fast-forward；已先 fetch 并普通 merge，禁止强推。

## 后续动作

- 提供有效 real-pre 管理员凭据后，重跑 preflight，并补商品库普通/双佣/显式 0% 的页面与 API 业务验收。
- 将 Harness Git Exit 改为基于任务 intake 白名单暂存，避免把并行任务或未知 dirty 文件带入提交。
- 继续保留 `PARTIAL/BLOCKED_AUTH` 口径，不把单测和健康检查替代真实业务闭环。
