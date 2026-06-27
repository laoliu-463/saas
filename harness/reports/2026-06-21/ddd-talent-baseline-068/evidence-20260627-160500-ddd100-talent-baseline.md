# DDD100 #68 Talent Baseline Evidence

## Scope
- Issue: #68 `[DDD100-TALENT-BASELINE] TalentService 认领/保护期基线`
- Environment: local `real-pre`
- Code change: none
- Remote deploy: no

## What Was Verified
- Talent claim / release / protection-period behavior remains covered by targeted backend tests.
- Talent list/detail, tags, shipping address and follow-record services remain covered by targeted backend tests.
- Talent frontend filter/security behavior remains covered by focused frontend tests.
- Protection-period conflict prompt is covered by Playwright E2E against local real-pre frontend with mocked talent API responses.
- real-pre database and HTTP read-only probes confirmed current talent table shape and list API availability.

## Commands
```powershell
mvn -q -f backend/pom.xml "-Dtest=TalentServiceTest,TalentClaimPolicyTest,TalentQueryServiceTest,TalentControllerTest,TalentFollowServiceTest,TalentTagServiceTest,TalentClaimReleaseJobTest,DddTalent003TalentRoutingTest,TalentAddressPolicyTest,TalentTagPolicyTest" test
npm --prefix frontend run test -- --run src/views/talent/constants.test.ts src/views/talent/composables/useTalentFilters.test.ts src/views/talent/components/TalentDetailModal.security.test.ts
$env:E2E_BASE_URL='http://127.0.0.1:3001'; $env:E2E_BACKEND_URL='http://127.0.0.1:8081'; $env:E2E_ENV_FILE='.env.real-pre'; npx playwright test --project=chromium tests/e2e/29-talent-claim-protection.spec.ts
docker exec saas-active-postgres-real-pre-1 psql -U saas -d saas_real_pre -c "<read-only talent aggregate SQL>"
Invoke-RestMethod GET http://127.0.0.1:8081/api/talents?page=1&size=5&view=TEAM_PUBLIC
Invoke-RestMethod GET http://127.0.0.1:8081/api/talents?page=1&size=5&view=MY_TALENTS
```

## Results
- Backend targeted tests: PASS.
- Frontend targeted tests: PASS, 3 files / 25 tests.
- Playwright E2E: PASS, setup + `29-talent-claim-protection.spec.ts`.
- Initial E2E attempt with `--no-deps` failed on login page because existing storageState did not match the current real-pre frontend origin; rerun with setup and `E2E_BACKEND_URL=http://127.0.0.1:8081` passed.
- Docker state before E2E: backend and frontend real-pre containers were healthy.

## real-pre Read-only Facts
- Talent tables present: `talent`, `talent_claim`, `talent_follow_record`, `talent_tag`, `talent_tag_relation` and related sync/auth tables.
- Aggregate counts: talents 37, claims 36, active claims 36, active protected claims 25, claims with address 2, follow records 0, tags 0, tag relations 0.
- `GET /api/talents?page=1&size=5&view=TEAM_PUBLIC`: HTTP business code 200, total 37, records 5, first record id and nickname present.
- `GET /api/talents?page=1&size=5&view=MY_TALENTS`: HTTP business code 200, total 0, records 0 for the channel-leader account used by E2E.

## Boundary Notes
- #68 proves the current baseline behavior and regression coverage; it does not claim third-party talent gateway evidence, which remains assigned to #71.
- Current real-pre has no follow/tag positive rows, so tag/follow behavior is verified by targeted tests rather than real-pre positive samples.
- `gender` filtering remains an explicitly documented unsupported gap and belongs to follow-up talent tasks.

## Conclusion
PASS for #68 baseline verification. No source code, API, DB schema, Docker config, or real-pre data was changed.
