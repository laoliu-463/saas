# real-pre 发布清单

`release/real-pre.json` 只存在于进入 `release/real-pre` 的发布提升 PR 中，不在普通业务分支上长期维护。

发布清单必须同时固定：

- `sourceMainSha`：已经通过 `main` CI Gate 的完整 SHA。
- 后端、前端镜像仓库和 `sha256` 内容摘要。
- 数据库迁移版本和迁移输入指纹。
- `previous`：可立即回滚的上一份不可变发布；首次部署才允许 `bootstrap=true` 且 `previous=null`。

验证命令：

```bash
python3 scripts/verify-real-pre-release.py release/real-pre.json
```

Jenkins 只消费这个清单，拉取 `repository@digest` 并部署；不会在服务器上执行源码构建，也不会从服务器 Git 工作区重建镜像。

镜像摘要由 `main` 合并后的 GitHub Actions 产物 `image-release.json` 提供。发布人补齐迁移版本和上一版本回滚信息后，才可以发起 `release/real-pre` 提升 PR。
