# Eval: p0-regression

## 验收目标

验证 V1 P0 本地回归基线未被破坏。

## 前置条件

- `test` 环境可启动。
- `.env.test` 存在。
- 前端、后端依赖已安装或可构建。

## 执行步骤

1. 执行后端构建或 Maven 测试。
2. 执行前端构建。
3. 重启 test compose。
4. 检查后端 `/api/system/health`。
5. 检查前端健康页面。
6. 执行 `npm run e2e:v1-p0` 或相关 P0 子集。

## 通过标准

- 构建通过。
- Docker 服务健康。
- P0 脚本无失败。
- evidence report 记录命令和结果。

## 失败含义

- 构建失败：代码不可交付。
- 健康失败：容器内未生效或配置错误。
- E2E 失败：业务回归风险。

## 证据要求

- Maven / npm 输出。
- Docker `ps`。
- 健康检查响应。
- Playwright 报告。

