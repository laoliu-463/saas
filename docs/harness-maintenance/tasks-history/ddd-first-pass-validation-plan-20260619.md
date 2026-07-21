# DDD First Pass Validation Plan - 2026-06-19

## Scope

- Goal: first-pass DDD validation, progress completeness check, and gap-fill plan.
- Environment: local repository only.
- Production impact: none. No deploy, restart, DB write, or remote operation.
- Status: PARTIAL. This file is a plan, not proof that DDD refactoring is complete.

## Evidence Read

- `CLAUDE.md`
- `docs/README.md`
- `docs/03-领域架构总览.md`
- `docs/03-领域边界总表.md`
- `docs/领域/*.md`
- `harness/README.md`
- `harness/README.md`
- `docs/harness-maintenance/legacy-rules/runbooks/ddd/DDD_OPTIMIZATION_ROADMAP.md`
- `docs/harness-maintenance/legacy-rules/runbooks/ddd/DDD_DOMAIN_TASK_MATRIX*.md`
- `docs/harness-maintenance/legacy-rules/state/snapshots/DOMAIN_STATUS.md`
- code-review-graph minimal context, architecture overview, change detection, knowledge gaps

## First-Pass Findings

1. DDD is staged, not complete.
   - Matrix has 178 task cards.
   - `DOMAIN_STATUS.md` records next steps and risks for P0/P1 domains.
   - Roadmap explicitly says it does not prove code refactoring is complete.

2. Documentation source has drift.
   - `docs/README.md` and DDD roadmap reference `docs/01-V1交付范围与边界.md`.
   - Current tree does not contain that file.
   - Existing nearby files include `docs/01-V2交付范围与边界.md`, `docs/01-V1交付合同.md`, and `docs/00-V1范围冻结说明.md`.

3. Backend domain package structure exists.
   - Domains observed: analytics, config, event, order, performance, product, sample, shared, talent, user.
   - Layers observed include api, application, domain, event, facade, infrastructure, policy, query.
   - Many `domain` layers are still package shells or minimal objects, not full domain models.

4. Legacy service dependency remains the main migration risk.
   - Large legacy classes include `ProductService` 5217 lines, `TalentService` 1605, `TalentQueryService` 1521, `ProductActivityBackfillService` 1469, `OrderSyncService` 1335, `ProductDisplayRuleService` 1310, `DashboardService` 1088.
   - Several domain application/facade classes still delegate to legacy `service` or `mapper` classes.

5. Boundary checks need stronger automation.
   - Existing architecture tests exist under `backend/src/test/java/com/colonel/saas/architecture`.
   - code-review-graph still reports risk `0.85`, 9 test gaps, and untested hotspots.
   - High-coupling warnings include service-order -> service-should and frontend API/product communities.

6. Current dirty worktree affects DDD risk reading.
   - Uncommitted changes include `SysUserService`, frontend request/login files, and old report archive moves.
   - These must be classified before any DDD completion claim.

## Gap-Fill Order

1. `DDD-DOC-SOURCE-001`: fix V1 scope source drift.
   - Decide whether `01-V1交付范围与边界.md` should be restored or README/roadmap should point to current source.
   - Evidence: docs search, ADR if scope conflict remains.

2. `DDD-GIT-INTAKE-001`: classify current dirty worktree.
   - Separate user-auth/request changes from report archive moves.
   - Evidence: `git status`, changed-file list, ownership note.

3. `DDD-USER-003`: close user-domain P0 data-scope boundary.
   - Focus: CurrentUser, PermissionContext, DataScopeResolver, PermissionChecker, UserDomainFacade.
   - Evidence: targeted unit tests plus admin/group/self API/E2E.

4. `DDD-CONFIG-001`: config-domain inventory and boundary proof.
   - Focus: config only provides parameters and audit facts.
   - Evidence: config API, change log SQL/API, negative proof that config does not trigger business action.

5. `DDD-ORDER-PERF-001`: order/performance boundary cleanup.
   - Focus: order facts vs performance attribution, historical performance gap, replay/backfill safety.
   - Evidence: API/SQL anti-join, idempotency tests, no direct order-domain commission calculation.

6. `DDD-ANALYTICS-001`: analytics read-only proof.
   - Focus: dashboard reads summaries/facts and does not recalculate attribution.
   - Evidence: dashboard API vs SQL, admin/group/self differences.

7. `DDD-PRODUCT-001`: product-domain strangler pass.
   - Focus: reduce `ProductService` hotspot, isolate conversion/mapping through ports.
   - Evidence: product policy tests, sync/backfill dry-run evidence, mapping traceability.

8. `DDD-TALENT-SAMPLE-001`: talent/sample state and event proof.
   - Focus: talent facts, sample state machine, order-event homework completion.
   - Evidence: state machine tests, duplicate-event idempotency, real-pre BLOCKED/PENDING where upstream sample is missing.

9. `DDD-OUTBOX-001`: event and outbox contract pass.
   - Focus: event naming, payloads, idempotency keys, retry evidence.
   - Evidence: producer/consumer tests and outbox/consume-log SQL.

10. `DDD-FRONTEND-E2E-001`: frontend domain and E2E proof.
    - Focus: no frontend hardcoded core rules/state machines; channel, investment, management chains.
    - Evidence: Vitest, Playwright, screenshot/API evidence where applicable.

## Validation Rule

Each follow-up task must produce:

- Changed-file ownership note.
- Domain boundary statement.
- Build/test command result or docs-scope skip reason.
- API/SQL/E2E evidence where relevant.
- Updated evidence report.
- `DOMAIN_STATUS.md` update only for the domain actually changed.
