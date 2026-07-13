# Evidence Report — 商品库快速寄样右侧抽屉

## Metadata

- Time: 2026-07-13
- Environment: real-pre
- Scope: frontend
- Branch: codex/ddd-user-role-application
- Base commit before this task: 4a7c305a
- Worktree: dirty（存在其他并行修改，未纳入本任务）
- Remote deploy: false

## Change Scope

- `frontend/src/views/product/components/QuickSampleModal.vue`
- `frontend/src/views/product/components/QuickSampleModal.test.ts`
- 右侧抽屉最大宽度为 `920px`，小屏按视口自适应，不再铺满整个屏幕。
- 补齐截图对应的批量申样标题、两步提示、合作达人入口、商品规格表、数量和备注编辑。
- 保留管理员按媒介查询达人、非管理员使用当前私海的既有权限边界。
- 移除截图未展示的静态 fallback 提示，不在前端擅自截断达人列表。

## Verification

- Targeted Vitest: PASS（3 files / 18 tests）
- Full Vitest: PASS（93 files / 696 tests）
- Typecheck: PASS（`npm --prefix frontend run typecheck`）
- Production build: PASS（`npm --prefix frontend run build`）
- Harness frontend build: PASS
- Docker restart: PASS（frontend/backend/postgres/redis healthy）
- Local frontend health: PASS（`http://127.0.0.1:3001/healthz` = 200）
- Browser visual acceptance: 未采集

## Business Validation

- Status: BLOCKED_AUTH
- Preflight: `runtime/qa/out/real-pre-preflight-20260713-155920/report.md`
- Admin login: FAIL，连续 5 次 HTTP 401
- Real-pre env guard / Douyin token readiness: BLOCKED，缺少 admin token
- 因认证前置失败，未将真实寄样业务链路标记为 PASS。

## Conclusion

PARTIAL

## Residual Risk

- 需要可用的 real-pre 管理员账号重新执行 preflight，再做真实商品库打开抽屉、选择媒介、选择达人、选规格并提交的浏览器验收。
- 当前单元测试验证了组件结构和接口调用参数，未替代真实账号下的视觉与业务闭环验证。
