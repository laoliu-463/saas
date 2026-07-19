# Remote real-pre 环境

## 当前事实

| 项 | 固定值 |
| --- | --- |
| 唯一发布控制器 | Jenkins |
| 可部署分支 | `release/real-pre` |
| 环境文件 | `/opt/saas/env/.env.real-pre`，只读管理 |
| 发布目录 | `/opt/saas/releases/<完整SHA>/` |
| 当前指针 | `/opt/saas/releases/current.json` |
| 上一版本 | `/opt/saas/releases/previous.json` |
| 后端版本探针 | `http://127.0.0.1:8081/api/system/health` |
| 前端版本探针 | `http://127.0.0.1:3001/version.json` |

服务器应用发布不再依赖 `/opt/saas/app` 共享可变工作树。PostgreSQL、Redis 和固定 real-pre 配置继续由 Docker Compose 与服务器运维管理。

## 权限边界

- 普通 Codex 任务：本地验证、推送分支、PR、候选证据。
- Merge Queue：串行决定合并顺序。
- CI：构建并推送完整 SHA 镜像，产出 digest。
- Jenkins：持有镜像仓库和服务器权限，串行迁移、部署、验证和记录。

用户要求部署时，普通任务应报告“候选 SHA 已进入/等待发布队列”，不能改用 SSH 手工完成。
