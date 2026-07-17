# Retro: xiangyun biz_staff remote deployment

- 时间：2026-07-17
- 责任人：工程维护
- 问题：本地开发分支已推送，但远端部署脚本固定拉取 `gitee/feature/auth-system`，首次部署实际重建了旧 commit。
- 改进动作：远端部署前校验远端 checkout commit 与目标部署 commit；不一致时先执行受控 fast-forward，再运行部署脚本。
- 本次验证：远端从 `fde84e32` fast-forward 到 `0f5f5a3a` 后重新构建；远端健康检查、biz_staff 新增达人、私海可见性和时间维度接口均通过。
