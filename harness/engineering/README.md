# Agent skills: 入口与降级路径

> **状态**：本目录是 Matt Pocock engineering skills 的工程配置入口（v2.0，2026-06-19）。
> **目标**：当 Agent 通过任何方式（直接调用 skill 名称 / 文件路径查找 / 模糊匹配）寻找配置时，**至少有一条路径可命中**。

## 权威配置位置（按优先级）

Agent 应按以下顺序查找配置（命中即用，不需遍历全部）：

| 优先级 | 路径 | 用途 |
| --- | --- | --- |
| 1 | `harness/engineering/issue-tracker.md` | Issue tracker 配置 |
| 2 | `harness/engineering/triage-labels.md` | 5 个 canonical 标签映射 |
| 3 | `harness/engineering/context.md` | Domain doc 消费规则 |
| 4 | `harness/engineering/issues-index.md` | GitHub Issues 镜像 |
| 5 | `AGENTS.md`（项目根）## 11. Agent skills section | skill 总览（指向 #1~#4） |
| 6 | `harness/INDEX.md` | harness 总入口（包含 engineering/ 引用） |

## 降级路径（当主路径找不到时）

如果上述路径都找不到配置（**异常情况**，正常 setup 后必有），降级到：

- **GitHub Issues 兜底**：直接 `gh issue list --state open --label ready-for-agent` 抓可执行 issue
- **`CONTEXT.md`（项目根）兜底**：包含项目领域词汇表
- **`harness/README.md` 兜底**：harness 顶层说明

## 自检命令

如果不确定 setup 是否正确，可运行以下命令自检：

```bash
# 1. 检查 4 个核心配置文件存在
ls -la harness/engineering/{issue-tracker,triage-labels,context,issues-index}.md

# 2. 检查 AGENTS.md 指向正确
grep -A 1 "## 11. Agent skills" AGENTS.md

# 3. 检查 harness/INDEX.md 包含 engineering
grep "engineering/" harness/INDEX.md

# 4. 检查 GitHub 标签已建立
gh label list | grep -E "needs-triage|needs-info|ready-for-agent|ready-for-human|wontfix"

# 5. 检查 gh CLI 可用
gh --version

# 6. 检查当前 open issues
gh issue list --state open --label ready-for-agent
```

如果任何一步失败，按 `harness/engineering/` 下对应文件修复。

## 文件清单（v2.0）

```text
harness/engineering/
├── README.md                ← 本文件（入口与降级路径）
├── issue-tracker.md         ← GitHub Issues 配置
├── triage-labels.md         ← 5 个 canonical 标签映射
├── context.md               ← Domain doc 消费规则
└── issues-index.md          ← GitHub Issues 本地镜像
```

## 已知调用模式

| 调用方式 | 是否支持 |
| --- | --- |
| 直接通过 skill 名称（如 `/setup-matt-pocock-skills`） | ✅ |
| 通过文件路径（如 `harness/engineering/issue-tracker.md`） | ✅ |
| 通过 AGENTS.md 中的 ## 11. Agent skills section | ✅ |
| 通过 harness/INDEX.md | ✅ |
| 通过 GitHub Issues（`gh issue list`） | ✅ |
| 通过 GitHub Labels | ✅ |

## 故障排查表

| 症状 | 排查路径 |
| --- | --- |
| "找不到 issue-tracker.md" | 1. 确认在正确仓库目录<br>2. 确认 `harness/engineering/` 目录存在<br>3. `find . -name "issue-tracker.md"` 全局搜索 |
| "GitHub CLI 不可用" | 1. `which gh` 检查<br>2. 安装：https://cli.github.com/<br>3. `gh auth login` 登录 |
| "标签 missing" | `gh label create <name> --description "..." --color <hex>` 重建 |
| "AGENTS.md 中 ## 11. Agent skills 不见了" | 重新跑 setup-matt-pocock-skills skill |
| "harness/engineering/ 目录不存在" | 重新跑 setup-matt-pocock-skills skill |

## 变更历史

- **v1.0**（2026-06-19）：初始化，建立入口与降级路径文档