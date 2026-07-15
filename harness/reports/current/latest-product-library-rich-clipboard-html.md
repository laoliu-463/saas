# Evidence Report

## Metadata

- Time: 2026-07-15 18:07:16 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/ddd-user-role-application
- Pre-commit baseline: 3016ae7c
- Implementation commit: 1bfaa6d8
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
docs/领域/商品域.md
docs/验收/验收证据索引.md
frontend/src/utils/clipboard.test.ts
frontend/src/utils/clipboard.ts
harness/reports/current/latest-content-retire.md
harness/rules/state/snapshots/DOMAIN_STATUS.md
~~~

## Owned Git Status

~~~text
M docs/领域/商品域.md
 M docs/验收/验收证据索引.md
 M frontend/src/utils/clipboard.test.ts
 M frontend/src/utils/clipboard.ts
 M harness/reports/current/latest-content-retire.md
 M harness/rules/state/snapshots/DOMAIN_STATUS.md
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    45 seconds ago   Up 41 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   43 seconds ago   Up 25 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   58 minutes ago   Up 58 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      2 hours ago      Up 2 hours (healthy)      6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 25 seconds (healthy)   127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 41 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 58 minutes (healthy)   5432/tcp
saas-active-redis-real-pre-1      Up 2 hours (healthy)      6379/tcp
campus_frontend                   Up 26 hours               0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 26 hours (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 26 hours (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 26 hours (unhealthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS (npm run e2e:real-pre:p0:preflight)
Frontend typecheck: PASS
Frontend test typecheck: PASS
Frontend full regression: PASS (94 files / 721 tests)
Production clipboard module in headed Chromium: PASS
- Browser clipboard types: text/plain, text/html
- HTML contains product image URL and complete formatted introduction: PASS
- Plain text matches after normalizing Windows CRLF to LF: PASS
- Windows native formats contain HTML/text and no PNG/Bitmap/DIB image candidate: PASS
- Paste into a standard contenteditable rich editor keeps the image node, title, shop and promotion link: PASS
WeChat client paste: PENDING (target client evidence not collected)
Feishu client paste: PENDING (target client evidence not collected)
~~~

## Content Maintenance Result

~~~text
Content maintenance: Plan. Manifest=. DryRun=False.
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

The generic real-pre preflight cannot prove target-client clipboard compatibility. Future clipboard changes must record browser/Windows clipboard formats and separate WeChat/Feishu paste acceptance; no standalone retro is required.

## Conclusion

PARTIAL

## Residual Risk

- 微信与飞书会自行选择、过滤剪贴板格式；两端真实粘贴未采集，不能声明跨平台 PASS。
- 当前方案依赖目标端允许富文本中的商品 CDN 图片 URL；若目标端过滤外链图片，只会保留简介文本。
- 本轮未部署远端，仅应用到本地 `real-pre` 容器。
