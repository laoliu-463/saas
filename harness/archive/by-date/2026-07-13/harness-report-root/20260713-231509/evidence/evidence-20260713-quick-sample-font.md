# Evidence Report — 快速寄样字号调整

## Metadata

- Time: 2026-07-13
- Environment: real-pre
- Scope: frontend
- Branch: codex/ddd-user-role-application
- Base commit: bb531a0b
- Remote deploy: false

## Change

- 下调快速寄样右侧抽屉的标题、步骤、区块标题、表格、商品信息和按钮字号。
- 同步收紧步骤圆点、商品图片和表格行高，保持侧边栏布局，不改变接口和业务交互。

## Verification

- Product-related Vitest: PASS（3 files / 18 tests）
- Typecheck: PASS
- Production build: PASS
- Harness frontend build: PASS
- Docker restart: PASS
- Frontend health probe: PASS（HTTP 200）
- Harness limits check: PASS

## Business Validation

- Status: BLOCKED_AUTH
- Preflight: `runtime/qa/out/real-pre-preflight-20260713-161132/report.md`
- Admin login: FAIL（5 次 HTTP 401）
- 因缺少 admin token，未执行真实账号下的业务闭环验证。

## Conclusion

PARTIAL

## Residual Risk

- 需要有效 real-pre 管理员账号后，重新进行浏览器视觉确认。
