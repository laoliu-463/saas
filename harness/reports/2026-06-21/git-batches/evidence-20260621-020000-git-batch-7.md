# GIT-BATCH-7 Evidence - Current User And Master Data

- Time: 2026-06-21 02:00 CST
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Base commit: 274b5ff0fb1aa2d0303b2d6f0b36144f8903528e
- Candidate scope: 20 staged files
- Remote deploy: not requested, not executed

## Scope

- Routed `CurrentUserController` through `CurrentUserApplicationService`.
- Routed `UserMasterDataController` through `UserMasterDataApplicationService`.
- Extracted current-user permission and credential rules from
  `UserDomainService` into user-domain policies.
- Moved `LegacyUserDomainFacade` user/department reads behind explicit ports:
  `UserBasicLookup` and `DepartmentOptionLookup`.
- Added adapter, controller, application, policy, integration, and facade
  boundary tests for this slice.

## Evidence Chain

- code-review-graph first:
  - `semantic_search_nodes` for the target symbols returned no useful matches;
    source import tracing was used for dependency closure.
- Dependency closure:
  - Created temp worktree from base commit and applied only staged candidate
    patch.
  - Temp worktree: `C:\Users\CAOJIA~1\AppData\Local\Temp\saas-gitbatch7-20260621015121`
  - Constructor call search found only focused tests and Spring injection.

## Tests

- Initial `clean test` command exceeded 240s. This was not counted as PASS.
- Surefire reports from that run showed 45 tests passed, then the command was
  rerun without `clean` to obtain Maven exit code 0.
- Passing command:
  `mvn -f backend/pom.xml test -Dtest=CurrentUserControllerTest,UserMasterDataControllerTest,UserDomainServiceTest,CurrentUserApplicationServiceTest,CurrentUserPasswordAuditIntegrationTest,LegacyUserDomainFacadeBoundaryTest,LegacyUserDomainFacadeTest,CurrentUserPermissionPolicyTest,UserCredentialPolicyTest`
- Location: temp candidate worktree
- Result: PASS
- Test result: 45 run, 0 failures, 0 errors, 0 skipped
- Business coverage:
  - current-user response, password change, data scope, permission check
  - user master-data channel/recruiter/group-member endpoints
  - password persistence and operation-log audit integration
  - legacy user-domain facade department and user read ports
  - current-user permission and credential policy parity

## Build And Restart

- Package command: `mvn -f backend/pom.xml -DskipTests package`
- Package result: PASS
- Safety check: `safety-check.ps1 -Env real-pre -Scope backend -DryRun` PASS
- Docker build:
  `docker build -t colonel-saas/backend:real-pre <temp>\backend`
- Image digest: `sha256:dba681ddf3dcbd3bf7e060ccdc480b3e1383172ef07bd0130cc8da2b01d8f2a1`
- Restart command:
  `docker compose --env-file D:\Projects\SAAS\.env.real-pre -f D:\Projects\SAAS\docker-compose.real-pre.yml -p saas-active up -d --no-build backend-real-pre`
- Restart result: backend recreated; postgres and redis stayed running.
- Post-push runtime recheck found the local backend tag had drifted to an older
  image. A clean HEAD worktree at commit `883a6631` was rebuilt and redeployed.
- Final package command:
  `mvn -f backend/pom.xml -DskipTests -Djacoco.skip=true package`
- Final package result: PASS
- Final image digest:
  `sha256:f776f159b0a871974b0ea5288b05f2d96c8a7a0f719e647174ba480289caec5a`

## Health And Runtime

- Harness health: `verify-local.ps1 -Env real-pre -Scope backend` PASS
- Backend health response: `{"status":"UP"}`
- Running container image ID matches final clean HEAD image digest.
- Container started at `2026-06-21T02:47:34Z`, status running, health healthy.

## Residual Risks

- Authenticated browser E2E was not run; no stable test account/token was used.
- Test profile shutdown emitted a scheduler Redis STOPPING stack trace during
  the first long run; tests still passed on rerun.
- real-pre backend logs show an unrelated scheduled task error:
  `column "colonel_name" does not exist`. Health stayed UP; this is not caused
  by the current-user/master-data slice but should be tracked separately.

## Harness

- `harness/scripts/check-harness-limits.ps1`: PASS after report creation.
- `harness/reports/2026-06-21/git-batches` reaches 10 files after this batch;
  archive before adding another report here.

## Conclusion

PASS for candidate dependency closure, focused tests, package, local backend
image rebuild, container restart, and health check.

Remaining validation gap: authenticated real-pre UI/API E2E.
