# Evidence Report - 1603 缁撶畻鍙ｅ緞鍒囨崲

## Metadata

- Time: 2026-06-12 12:34 +08:00
- Environment: local real-pre
- Branch: feature/ddd/DDD-SAMPLE-005-FIX-sample-agent
- Commit: f3846415
- Worktree: dirty
- Git commit/push: not performed due to broad pre-existing dirty worktree
- Remote deploy: false
- Conclusion: PARTIAL

## Scope

- 灏嗚鍗曠粨绠椾富璇诲彇鍙ｅ緞鍒囨崲涓?`buyin.instituteOrderColonel`銆?- 灏?`buyin.colonelMultiSettlementOrders` 闄嶇骇涓?fallback / probe / contrast銆?- 涓嶄慨鏀瑰綊鍥犺鍒欍€佸墠绔笟鍔¤鍒欍€佷笟缁╁叕寮忋€?- 涓嶅仛 pay -> settle 鎴?estimate -> effective 鐨勭‖鍏滃簳銆?
## Build And Test

- PASS: `mvn '-Dtest=*1603*,*Settlement*,*PerformanceCalculationEffectiveTrack*' test`
  - 11 tests, 0 failures, 0 errors.
- FAIL: `mvn test`
  - Full suite compiled and ran, but existing non-1603 tests failed.
  - Examples: `SysConfigServiceEventTest`, `SysConfigServiceTest`, `SysDeptServiceTest`, talent provider/schema tests, `ProductDisplayRuleServiceTest`.
  - One repeated failure source: Mockito inline mock cannot redefine `ProductBizStatusService` on current JVM.
- PASS: `mvn -Dmaven.test.skip=true package`
- PASS: `npm test -- --run src/api/douyin.test.ts`
  - 39 tests, 0 failures.
- PASS: `npm run build`
- PASS: Harness safety check.
- PASS: Harness backend build.
- PASS: Harness frontend build.

## Runtime Verification

- FAIL/BLOCKED: Harness Docker restart.
- Blocking evidence: Docker Desktop Linux engine pipe missing:
  `open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified`.
- NOT COLLECTED: Docker status.
- NOT COLLECTED: health check.
- NOT COLLECTED: real-pre 1603 raw probe.
- NOT COLLECTED: real-pre 1603 dry-run endpoint call.
- NOT COLLECTED: small time-window write verification.

## Evidence Artifacts

- Archived raw Harness reports: `harness/archive/evidence-raw-20260612.zip`
- Interface audit: `harness/reports/order-1603-settlement-interface-audit.md`
- Harness limits: `harness/reports/latest-harness-limits-check.md`

## Residual Risk

- 褰撳墠鍙兘璇佹槑浠ｇ爜璺緞銆佹槧灏勭瓥鐣ュ拰鏋勫缓/鐩爣娴嬭瘯閫氳繃锛屼笉鑳借瘉鏄庣湡瀹炴姈闊?1603 涓婃父瀛楁瀹屾暣銆?- Docker 鏈惎鍔ㄥ鑷村鍣ㄩ噸鍚€佸仴搴锋鏌ュ拰涓氬姟鑱旇皟鏈畬鎴愩€?- real-pre 灏忕獥鍙ｅ啓鍏ラ獙璇佸繀椤诲湪 Docker engine 鍙敤鍚庤ˉ璺戙€?- npm audit 鎶ュ憡 2 涓?critical 渚濊禆椋庨櫓锛屾湰杞湭澶勭悊銆?
## Retro

- 鏈鏃犻渶 Harness 瑙勫垯鍗囩骇銆?- 杩愯闃诲鏉ヨ嚜鏈満 Docker engine 涓嶅彲鐢紝涓嶆槸 Harness 娴佺▼缂洪」銆?