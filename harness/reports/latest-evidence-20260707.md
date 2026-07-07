# Evidence Report - 2026-07-07

## Metadata

- Time: 2026-07-07 14:50 +08:00
- Environment: local real-pre repository context
- Branch: codex/ddd-user-role-application
- HEAD: 3fda3d61
- Scope: DDD closure evidence: O-5 default attribution input contract
- Remote deploy: not requested, not executed
- Commit / push: not executed; active goal says no push
- Worktree: dirty; many historical non docs/harness changes remain outside this slice

## Selected Card

- Matrix source: `docs/ddd-completion-evidence-matrix.md`
- Completed card: O-5 default attribution input independent card-level verification
- Matrix count after update: DONE 73 / PARTIAL 73 / TODO 27 / BLOCKED 5
- Selection reason: O-5 was the earliest order-domain card that could be closed with local source, docs and contract-test evidence. O-4 remains PARTIAL because real `pick_source` hit samples are not available in this local runtime.

## Evidence Chain

- Added `DddOrderDefaultAttributionInputContractTest` as a source/docs/test-only contract guard.
- The guard pins `OrderAttributionInput.from` reading order facts: `productId`, `pickSource`, local `talentId`.
- The guard pins raw payload aliases: `colonel_activity_id` / `activity_id`, `pick_extra` / `pickExtra`, `author_id` / `authorId`, `talent_uid` / `talentUid`, `promotion_talent_uid` / `promotionTalentUid`.
- The guard pins fallback behavior: raw activity id overrides order activity id; missing raw talent UID falls back to order `talentName`.
- The guard pins `OrderDefaultAttributionResolver` consuming default inputs through `OrderPickSourceMappingAdapter`, `TalentDomainFacade` and `ProductDomainFacade`.
- The guard pins `OrderDefaultAttributionResult` to default-channel/default-recruiter fields only, with no commission, final ownership, reversal or exclusive override fields.
- Updated `docs/领域/订单域.md` to record the default attribution input boundary.
- Updated `docs/ddd-completion-evidence-matrix.md` and `harness/rules/state/snapshots/DOMAIN_STATUS.md`.
- No production business code was changed for O-5.

## Commands

| Command | Result | Evidence |
| --- | --- | --- |
| code-review-graph global review context | REVIEWED | 152 changed files, risk high, 265 impacted files; high risk reflects global dirty workspace |
| `mvn -q test "-Dtest=DddOrderDefaultAttributionInputContractTest"` | PASS | 4 tests / 0 failures / 0 errors / 0 skipped |
| `mvn -q test "-Dtest=DddOrderDefaultAttributionInputContractTest,OrderDefaultAttributionResolverTest,OrderDefaultAttributionPolicyTest,OrderAttributionRouterTest,OrderAttributionServiceTest,PickSourceMappingServiceTest"` | PASS | 46 tests / 0 failures / 0 errors / 0 skipped |
| `mvn -q -DskipTests compile` | PASS | exitCode=0 |
| `mvn -q test "-Dtest=DddArchitectureRedlineGuardTest"` | PASS | 4 tests / 0 failures / 0 errors / 0 skipped |
| `mvn -q test "-Dtest=*Architecture*Test,*Ddd*Test,*Guard*Test,*Contract*Test"` | PASS | 246 tests / 0 failures / 0 errors / 1 skipped |
| `mvn -q test "-Dtest=*Order*Test,*Performance*Test"` | PASS | 516 tests / 0 failures / 0 errors / 4 skipped; visible exception stacks are expected negative-path or fallback test logs |
| `git diff --check` | PASS | exitCode=0; CRLF/LF warning only for `docs/ddd-completion-evidence-matrix.md` |
| `check-ddd-acceptance.ps1 -RequireRedlineZero` | PASS | latest report 2026-07-07 14:50:05 +08:00; whitelist 0/0; matrix 73/73/27/5; compile PASS; redline guard PASS; wide architecture 246 tests / 0 failures / 0 errors / 1 skipped |
| `agent-do.ps1 -Env real-pre -Scope backend -Message "ddd: o5 default attribution input contract"` | RUNTIME FAIL | safety check PASS and backend package BUILD SUCCESS; Docker compose failed because `//./pipe/dockerDesktopLinuxEngine` is unavailable |

## Runtime

- No remote deploy requested.
- Backend package succeeded through the harness `agent-do` path.
- Docker compose restart failed because Docker Desktop Linux engine pipe is unavailable.
- Container restart, health check and business runtime validation are not claimed.

## Reports

- Latest DDD acceptance: `harness/reports/latest-ddd-acceptance-report.md`
- Latest evidence: `harness/reports/latest-evidence-20260707.md`
- Archived raw agent-do evidence: `harness/archive/by-date/report-packages/reports-20260707-o5-agent-do-runtime-fail.zip`
- Matrix: `docs/ddd-completion-evidence-matrix.md`
- Domain status: `harness/rules/state/snapshots/DOMAIN_STATUS.md`

## Remaining Risks

- Real-pre Docker runtime, health check and business validation are blocked locally by missing Docker Desktop Linux engine.
- O-5 only proves local default attribution input extraction, resolver consumption and result field contract.
- O-5 does not prove real upstream order sync, real `pick_source` hit, account-difference E2E, frontend pages or full E2E.
- O-4 remains PARTIAL until real `pick_source` hit samples and evidence are available.
- Existing worktree remains dirty with many unexpected non docs/harness paths in the acceptance report.

## Conclusion

O-5 has enough local source, docs and contract-test evidence to move to DONE. Runtime verification remains PARTIAL because Docker compose cannot start without the local Docker Desktop Linux engine.

## Retro

No harness script upgrade was needed. The reusable pattern is a narrow contract guard that pins default attribution input extraction, resolver consumption and output fields without changing attribution business rules.
