# Runbook: frontend change

## 适用场景

Vue 页面、组件、路由、Pinia 状态、前端 API 封装、权限展示和交互流程修改。

## 前置检查

1. 读取 `docs/05-API契约总表.md`、对应流程文档和验收文档。
2. 确认前端只调用内部 `/api/**`，不直连抖音 / 抖店开放接口。
3. 确认核心业务规则、权限规则和状态机仍由后端负责。

## 操作步骤

1. 先定位页面入口、API 封装和状态来源。
2. 做最小 UI/状态修改，保持 Naive UI、Pinia、现有 CSS token 体系。
3. 对交互、错误态、权限态和空态补验证。
4. 执行固定入口：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope frontend -Message "fix: frontend change"
```

## 验证标准

- `npm --prefix frontend run build` 通过。
- frontend 容器已重启。
- real-pre 前端 `/healthz` 或 `/login` 可访问。
- 页面或 E2E 能证明目标场景。

## 常见失败原因

- 前端硬编码后端业务口径。
- API 字段名与 `docs/05` 或后端 DTO 不一致。
- 权限菜单只验证 admin，未验证 group/self。
- build 通过但页面流程未验证。

## 禁止事项

- 前端不得持有第三方密钥、Token 或 OAuth code。
- 不用 mock 页面状态证明 real-pre 真实闭环。
- 不用纯兜底提示替代根因修复。

## 产出物位置

- 页面/组件/API diff。
- build/E2E/截图或 Network 证据。
- `harness/reports/evidence-*.md`。
- `harness/reports/retro-*.md`。
