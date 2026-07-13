# Prompt: full review

请按以下流程执行全量审查：

1. 读取 `AGENTS.md`。
2. 读取 `harness/rules/state/snapshots/01-当前项目状态.md`。
3. 读取 `docs/01-V1交付范围与边界.md`、`docs/02-业务闭环总览.md`、`docs/03-领域架构总览.md`。
4. 按用户域、配置域、商品域、达人域、寄样域、订单域、业绩域、分析模块逐一对照代码和验收证据。
5. 使用 code-review-graph 先获取结构上下文，再按需读取源码。
6. 输出 P0 / P1 / P2 问题，必须给证据路径、影响范围、验证方式。
7. 不要把旧 V2.2、FastAPI、Celery 或 mock 口径写成当前事实。

输出格式：

```md
# 全量审查报告

## P0
## P1
## P2
## 证据路径
## 未验证项
## 下一步
```
