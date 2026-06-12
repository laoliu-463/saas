# DOUYIN-ENV-SECRET-RUNTIME-RELOAD-001

## 1. Scope

- Time: 2026-06-06 20:19 +08:00
- Environment: local real-pre
- Branch: feature/auth-system
- Task: 排查三方联调上游失败是否�?`.env` 参数问题导致�?- Remote deploy: not requested / not executed

## 2. Phenomenon

上游联调再次失败，后端日志出现多接口共同的抖音开放平台认证失败：

- `alliance.colonelActivityProduct`
- `buyin.institutionInfo`
- `alliance.instituteColonelActivityList`
- `buyin.instituteOrderColonel`

共同错误特征为：

- `code=40003`
- `subCode=isv.signature-invalid`
- `msg=认证失败 / sign校验失败`

这不是单个接口字段现象，而是多个上游 API 共用签名链路同时失败�?
## 3. Evidence Collected

### 3.1 Safety and container state

- `safety-check.ps1 -Env real-pre -Scope full -DryRun`: PASS
- Initial Docker state: backend / frontend / postgres / redis all healthy
- No database cleanup executed
- No `docker compose down -v` executed
- No remote deployment executed

### 3.2 Secret presence check

Only presence was checked. Secret values were not printed.

- `DOUYIN_CLIENT_SECRET`: present
- `DOUYIN_CLIENT_KEY`: present
- `DOUYIN_APP_ID`: present
- `DOUYIN_TEST_ENABLED`: false
- `DOUYIN_REAL_UPSTREAM_MODE`: live

### 3.3 File env vs backend runtime env

Before backend restart:

- `.env.real-pre` and backend container env were equal for `DOUYIN_BASE_URL`, `DOUYIN_APP_ID`, `DOUYIN_CLIENT_KEY`, real-pre/test guards, and write guards.
- `.env.real-pre` and backend container env were different for `DOUYIN_CLIENT_SECRET`.

After backend restart via Harness restart script:

- `.env.real-pre` and backend container env were equal for all checked Douyin/runtime guard variables.
- `DOUYIN_CLIENT_SECRET`: file=SET, container=SET, compare=equal
- `DOUYIN_TEST_ENABLED=false`
- `DOUYIN_REAL_UPSTREAM_MODE=live`
- `APP_TEST_ENABLED=false`

No secret value was printed in either comparison.

### 3.4 Code path evidence

The backend signing path uses:

- `DouyinConfig.clientKey` / `DouyinConfig.appId` as app key input
- `DouyinConfig.clientSecret` as signing secret input
- `SignUtil.sign(appId, appSecret, methodName, timestamp, paramJson, "2")`

Therefore `DOUYIN_CLIENT_SECRET` mismatch between `.env.real-pre` and the running backend container is a direct signature-invalid cause candidate.

### 3.5 Action executed

Executed:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\restart-compose.ps1 -Env real-pre -Scope backend
```

Result:

- backend container recreated and started
- backend became Docker healthy
- startup profile confirmed: `real-pre`
- startup guards confirmed: `app.test.enabled=false`, `douyin.test.enabled=false`
- database confirmed: `saas_real_pre`

### 3.6 Post-restart validation

Post-restart probes:

- `/api/system/health`: `{"status":"UP"}`
- `npm run e2e:real-pre:p0:preflight`: PASS
- real-pre env guard: PASS
- Douyin token readiness: PASS
- admin login: PASS
- database schema readiness: PASS

Post-restart upstream checks:

- `/api/douyin/institution-info`: HTTP 200, business status success
- `/api/douyin/activities`: HTTP 200, business status success
- Scheduler logs show repeated upstream success for:
  - `alliance.colonelActivityProduct`
  - `buyin.instituteOrderColonel`

The later direct `/api/douyin/activity-product-list` 500 was not an upstream signature failure. Backend log shows:

- `MissingServletRequestParameterException`
- required request parameter `activityId` was not present

This is a local request-shape issue in the manual probe, not evidence that upstream signing remains broken.

## 4. Conclusion

PASS for the requested diagnosis and local runtime fix.

The failure was caused by `.env.real-pre` and the running backend container having different `DOUYIN_CLIENT_SECRET` values. The `.env.real-pre` file itself was not missing the secret; the issue was that the backend runtime had not reloaded the updated secret.

After restarting the backend through the Harness compose restart path, the runtime env matched `.env.real-pre`, preflight passed, and upstream Douyin calls resumed successfully.

## 5. Residual Risks

- This verifies local real-pre only; no remote real-pre deploy was requested or executed.
- Full V1 business closed-loop validation was not rerun in this task; this report only proves the credential/runtime signing path has recovered.
- The manual `activity-product-list` probe must include `activityId`; the previous no-parameter call is not a valid upstream product-list verification case.

## 6. Retro / Harness Upgrade

No Harness rule change is required for this task. The existing real-pre debug flow was sufficient:

- safety check
- runtime env comparison
- backend restart
- health check
- real-pre preflight
- upstream log validation
- evidence and retro generation
