# Retro Summary - DOUYIN-UPSTREAM-RECONNECT-001

- Time: 2026-06-05 15:53:51 CST
- Scope: local real-pre upstream reconnection
- Conclusion: no Harness code upgrade performed

## What Worked

- The previous audit correctly narrowed the failure to platform-side credential state.
- Non-secret env comparison caught the concrete runtime issue: `.env.real-pre` had the reset secret, but the backend container still had the old value.
- Restarting only backend through the Harness compose script restored signing.
- Manual probes and scheduler logs both verified recovery, so the result does not depend on a single API call.

## What Was Missing

- There is still no first-class Harness command for "secret rotated, reload backend and run upstream probes".
- `restart-compose.ps1 -Scope backend` also recreated Redis because of Compose dependency behavior; this was safe here but should be documented for operator expectations.
- Performance coverage remains a separate downstream problem; the reconnect task should not claim that order-to-performance is fixed.

## Suggested Harness Improvements

1. Add a `douyin-upstream-reconnect` runbook section:
   - compare env file vs container secret presence/equality without printing secrets
   - restart backend
   - run preflight
   - run 6468/2704/activity-product readonly probes
   - check scheduler logs for new signature failures
2. Add a small safe probe script that emits only endpoint, status, code, subCode, log_id and count hints.
3. Document that remote real-pre must be handled separately through `/opt/saas/env/.env.real-pre` and controlled restart.

## Non-Actions

- Did not update source code.
- Did not update Harness scripts.
- Did not expose secrets.
- Did not deploy remote real-pre.
