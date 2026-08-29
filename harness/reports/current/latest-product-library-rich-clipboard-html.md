# Evidence Report

## Metadata

- Time: 2026-07-15 19:01:05 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/deploy-wechat-clipboard-20260715
- Application commit: 4a116585
- Evidence commit before this amendment: 50c13c09
- Release upstream: gitee/feature/auth-system
- Owned worktree: clean
- Deploy remote: true

## Owned Files

~~~text
docs/领域/商品域.md
docs/验收/验收证据索引.md
frontend/src/architecture/frontend-business-rule-boundary.test.ts
frontend/src/utils/clipboard.test.ts
frontend/src/utils/clipboard.ts
frontend/src/views/product/product-copy.test.ts
frontend/src/views/product/product-copy.ts
frontend/src/views/product/ProductLibrary.vue
harness/rules/state/snapshots/DOMAIN_STATUS.md
~~~

## Owned Git Status

~~~text
(clean)
~~~

## Build Result

~~~text
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
Frontend production typecheck: PASS
Frontend test typecheck: PASS
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED              STATUS                        PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    About a minute ago   Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   About a minute ago   Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   About a minute ago   Up About a minute (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      3 hours ago          Up 3 hours (healthy)          6379/tcp
NAMES                             STATUS                        PORTS
saas-active-frontend-real-pre-1   Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up About a minute (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up About a minute (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 3 hours (healthy)          6379/tcp
campus_frontend                   Up 27 hours                   0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 27 hours (healthy)         0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 27 hours (healthy)         0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 27 hours (unhealthy)       0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
Remote backend: PASS ({"status":"UP"})
Remote frontend: PASS (ok)
~~~

## Business Validation Result

~~~text
Targeted clipboard/copy contract: PASS (2 files, 19 tests)
Full frontend regression: PASS (94 files, 719 tests)
User manual WeChat verification: PASS
Feishu: accepted compatibility fallback; receives image link/rich content text but does not render the image
~~~

## Production Browser Clipboard Verification

~~~text
Target: local real-pre Docker production build (same application source as remote commit 4a116585)
Browser: headed Chromium through Playwright CLI, clean session
Clipboard write: PASS ({copied:true,imageCopied:true})
ClipboardItem count: 1
Clipboard MIME types: text/plain, text/html
Standalone image MIME: absent (expected)
Plain text: exact after CRLF/LF normalization
HTML: contains img and all 10 required introduction fields
Contenteditable paste: 1 image plus exact full introduction text
Browser console errors: 0
~~~

Required fields verified: 抖音标题、店铺名称、售价、佣金率、投放期佣金、库存、奖励说明、开始时间、结束时间、推广链接。

## Default P0 Preflight Boundary

~~~text
Result: BLOCKED_AUTH, not PASS
Evidence: runtime/qa/out/real-pre-preflight-20260715-185554
Cause observed: admin login returned HTTP 401
Handling: did not use SkipBusinessValidation; used the 19 clipboard/copy contract tests as the task-relevant business gate
~~~

The admin authentication failure is outside this clipboard change and remains a separate follow-up item. It is not presented as a successful P0 result.

## Content Maintenance Result

~~~text
Content maintenance skipped by -ContentMaintenance off.
~~~

## Remote Deploy Result

~~~text
Remote deploy: PASS
Remote server application revision: 4a116585
Remote release branch evidence revision: 50c13c09 before this amendment
Remote source verified: tryCopyTextAndImage + text/html + text/plain; no standalone image/png
~~~

## Retro Summary

远端服务器跟踪 Gitee feature/auth-system；远端部署必须同时校验服务器应用 revision。默认 P0 preflight 因 admin HTTP 401 为 BLOCKED_AUTH，本任务改用 19 项商品复制合同测试作为相关业务验证，不把认证链路标记为 PASS。微信为必过客户端，飞书图片链接加完整文字为已接受降级。

## Conclusion

PASS for the product-library WeChat rich clipboard scope. This conclusion does not include the independently blocked admin-auth P0 probe.

## Residual Risk

- Feishu still does not render the copied image. The user explicitly accepted its current link/text fallback, so it is not a blocker for this delivery.
- `imageCopied=true` proves that the browser wrote the rich HTML clipboard item; actual rendering remains controlled by each destination client's clipboard parser.
- The default P0 admin login remains `BLOCKED_AUTH` with HTTP 401 and should be handled separately if full-system P0 acceptance is required.
