# DDD 收口验收指南

## 1. 定位

DDD 1.0 收口表示当前阶段的领域边界、白名单、架构测试和证据矩阵达到可继续小步推进的基线，不表示永久 100% 完成。

本指南只约束验收方式，不改变业务规则、接口、schema、权限、状态机、金额、佣金、提成或归因口径。

## 2. 收口口径

- `DONE` 必须有代码证据、测试证据、执行命令和结果。
- `PARTIAL` 表示已有部分证据，但缺少完整链路或当前复跑证据。
- `TODO` 表示尚未形成可验证证据。
- `BLOCKED` 必须写明阻塞原因和解阻条件，不能伪装成通过。
- DDD 收口报告默认写入 `runtime/qa/out/latest-ddd-acceptance-report.md`。

## 3. 七层验收

1. 白名单：`cross-domain-mapper` 有效项必须为 0；`architecture-redline` 按 `-MaxRedlineDebt` 逐步降到 0。
2. 架构测试：必须覆盖 DDD redline、跨域 mapper、包结构、policy、guard、contract。
3. 领域边界：前端、Controller、领域、SDK、数据库职责不得越界。
4. 老 Service 薄壳化：旧 Service 逐步委派到 application/facade/port，不顺手重构业务规则。
5. 行为 parity：API 路径、请求响应、权限、状态机和金额口径保持一致。
6. 性能健壮性：避免同步长请求、重复依赖、旧产物残留和不可追踪隐式逻辑。
7. E2E / BLOCKED：真实样本不足时记录 BLOCKED，不用 mock 证明 real-pre 闭环。

## 4. 每轮 Codex 流程

1. 从 `docs/ddd-completion-evidence-matrix.md` 选择 1 到 3 张卡。
2. 先补最小测试或可执行证据，再做最小代码 / 文档修改。
3. 运行 DDD 验收脚本：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\check-ddd-acceptance.ps1 -MaxRedlineDebt 11
```

4. 更新矩阵状态和证据，不把未验证项写成 `DONE`。
5. 输出 evidence / retro，说明 dirty 文件、未验证项和下一步。

## 5. 常用命令

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\check-ddd-acceptance.ps1 -DocsOnly
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\check-ddd-acceptance.ps1 -RequireRedlineZero
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\check-ddd-acceptance.ps1 -FailOnUnexpectedDirty
```

配套基础门禁：

```powershell
git diff --check
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\check-harness-limits.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\safety-check.ps1 -Env real-pre -Scope docs -DryRun
```

## 6. 当前基线

- `cross-domain-mapper-legacy-whitelist.txt` 当前目标为 0。
- `architecture-redline-legacy-whitelist.txt` 当前按 `-MaxRedlineDebt 11` 兼容运行，后续逐步降到 0。
- 实际有效项数量以脚本读取 whitelist 的运行结果为准，不在脚本中写死。
- 当前 178 卡矩阵以 `docs/ddd-completion-evidence-matrix.md` 为主源。

## 7. 禁止事项

- 不因脚本通过就声明 real-pre 全业务闭环通过。
- 不把 `BLOCKED`、`PENDING`、`PARTIAL` 写成 `PASS`。
- 不处理与当前卡无关的业务 dirty。
- 不跳过失败测试或删除测试来制造通过。
