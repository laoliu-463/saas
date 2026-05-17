# Issue tracker: GitHub

本仓库的 issue tracker 默认是 GitHub Issues，对应 remote 为 `https://github.com/laoliu-463/saas.git`。涉及 issue、PRD、triage 的 skill 默认使用 `gh` CLI 在当前仓库上下文中执行。

## Conventions

- 创建 issue：`gh issue create --title "..." --body "..."`
- 查看 issue：`gh issue view <number> --comments`
- 列出 issue：`gh issue list --state open`
- 评论 issue：`gh issue comment <number> --body "..."`
- 增删标签：`gh issue edit <number> --add-label "..."` / `--remove-label "..."`
- 关闭 issue：`gh issue close <number> --comment "..."`

## 约束

- 在当前仓库内运行 `gh` 命令，默认让 CLI 从 git remote 推断仓库，不手写别的 repo。
- 若 skill 产出的是计划、PRD 或拆分结果，默认发布为 GitHub issue；除非用户明确要求只落本地文档。
- 若仓库后续改用 Jira、Linear 或本地 markdown issue，再更新本文件，而不是让 skill 自行猜测。

## When a skill says "publish to the issue tracker"

默认含义是：发布到当前仓库的 GitHub Issues。

## When a skill says "fetch the relevant ticket"

默认含义是：运行 `gh issue view <number> --comments` 读取对应 issue。
