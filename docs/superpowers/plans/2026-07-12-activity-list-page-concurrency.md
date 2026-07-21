# Activity List Page Concurrency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Use bounded page-level concurrency for the asynchronous activity-list sync so upstream pagination completes faster without blocking the HTTP request or creating unbounded work.

**Architecture:** Keep one background activity-sync job as the global orchestration boundary. Fetch pages in bounded windows on a dedicated fixed-size executor, then apply activity upserts in page order on the worker thread. Stop at a short/empty page or the configured max-pages guard; any page error closes the job as partial/failed according to existing semantics.

**Tech Stack:** Spring Boot / Java 17 / JUnit 5 / Mockito / Docker Compose real-pre.

---

### Task 1: Add a RED concurrency regression test

**Files:**
- Modify: `backend/src/test/java/com/colonel/saas/service/ColonelActivityListSyncServiceTest.java`

- [x] Add a test with `pageSize=2`, `pageParallelism=2`, page 1 as the seed page, and subsequent pages blocked on a `CountDownLatch`. Assert that upstream page calls overlap, stay within the bound, and all pages are eventually applied.
- [x] Run `mvn -f backend/pom.xml "-Dtest=ColonelActivityListSyncServiceTest" test`.
- [x] Confirm RED first on the sequential implementation, then GREEN after the bounded executor implementation.

### Task 2: Implement bounded page fetch concurrency

**Files:**
- Modify: `backend/src/main/java/com/colonel/saas/service/ColonelActivityListSyncService.java`

- [x] Add `colonel.activity.list-sync.page-parallelism` with default `4`, normalize it to `1..8`, and create a dedicated fixed-size page executor.
- [x] Fetch page 1 first, then submit only a bounded window of subsequent page requests. Collect futures in page order and call `activityService.syncFromGatewayItem` in page order so database writes remain deterministic.
- [x] Keep the existing max-pages guard, lock ownership, status update, and final lock release. Do not pass upstream or page parameters from the frontend.
- [x] Add executor shutdown on bean destruction and cancel remaining page futures when the job is interrupted.
- [x] Persist the fetched activity total into the sync job log so the polling API reports truthful progress.

### Task 3: Verify the change and the real-pre runtime

**Files:**
- No production files beyond Task 2.
- Generate: `runtime/qa/out/latest-evidence-YYYYMMDD-activity-page-concurrency.md`
- Generate: `runtime/qa/out/retro-YYYYMMDD-activity-page-concurrency.md`

- [x] Run the focused test, backend build, frontend tests/build, and the backend full suite while excluding `AllActivitiesSyncAndInspectTest`.
- [x] Run `restart-compose.ps1 -Env real-pre -Scope full`, `verify-local.ps1`, and `npm run e2e:real-pre:p0:preflight`.
- [x] Trigger real activity-list syncs, record immediate trigger return, duration, final status, page count, and database counts; no long synchronous product backfill was run.
- [x] Run `check-harness-limits.ps1`; fresh result was `PASS`.
