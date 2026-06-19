# Evidence Report - 2026-06-19 CI-Only Activation And Read-Only Checks

- Time: 2026-06-19 12:07:00 +08:00
- Env: GitHub Actions, local workspace, remote real-pre server
- Branch: feature/ddd/DDD-VERIFY-001
- Latest CI-covered commit: 118ecadb `ci: skip report-only workflow runs`
- Worktree clean: no
- Worktree note: pre-existing local changes remain in auth/login/request files and harness report archive moves; they were not staged in the CI commits.
- Remote deployed: no
- Remote write/restart: no
- Conclusion: PARTIAL

## Scope

- Added GitHub Actions CI-only workflow at `.github/workflows/ci.yml`.
- Verified CI activation across multiple push runs.
- Added `paths-ignore: harness/reports/**` so report-only commits do not create an infinite evidence-report CI loop.
- Performed read-only remote health checks.
- Performed DDD structure/status read-only checks.
- Did not deploy, restart containers, update remote env, or write production data.

## CI/CD Evidence

- GitHub Actions permissions: enabled, `allowed_actions=all`.
- Workflow registered: `CI`, active, workflow id `298639342`.
- Current workflow run id: `27804592079`.
- Current run URL: `https://github.com/laoliu-463/saas/actions/runs/27804592079`.
- Event: push.
- Head branch: `feature/ddd/DDD-VERIFY-001`.
- Head sha: `118ecadb14d8644aed2551c3d4234a5ee47b706b`.
- Run conclusion: success.

Current run jobs:

- `Frontend tests and build`: success.
  - Checkout: success.
  - Set up Node.js 24: success.
  - Enable pnpm: success.
  - `pnpm install --frozen-lockfile`: success.
  - `pnpm test`: success.
  - `pnpm typecheck`: success.
  - `pnpm build`: success.
- `Backend tests`: success.
  - Checkout: success.
  - Set up Java 17: success.
  - `mvn -B test`: success.

Additional CI activation evidence:

- Run `27803688436` on commit `dfa0954b`: success.
- Run `27804324203` on commit `0a46c0b6`: success.
- Commit statuses endpoint can remain `total_count=0`; GitHub Actions reports through check-runs/runs, not legacy statuses.

## Remote Server Evidence

- Public frontend health: `http://1.14.108.159/healthz` -> `ok`.
- Public backend health: `http://1.14.108.159/api/system/health` -> `{"status":"UP"}`.
- Remote app directory commit: `e96fdb4`.
- Remote deployment was intentionally not updated to CI commits.
- Containers:
  - `saas-active-frontend-real-pre-1`: healthy.
  - `saas-active-backend-real-pre-1`: healthy.
  - `saas-active-postgres-real-pre-1`: healthy.
  - `saas-active-redis-real-pre-1`: healthy.

## DDD Evidence

- Code-review-graph minimal context: 12204 nodes, 140888 edges, 1522 files.
- Graph risk score: high, `0.85`, from pre-existing changed files.
- Architecture overview: 19 communities, 9 community pairs, 6 high-coupling warnings.
- DDD package evidence exists under `backend/src/main/java/com/colonel/saas/domain`.
- Observed layers include `api`, `application`, `domain`, `event`, `facade`, `infrastructure`, `policy`, `query`.
- DDD status source: `harness/rules/state/snapshots/DOMAIN_STATUS.md`.
- Roadmap source: `harness/rules/runbooks/ddd/DDD_OPTIMIZATION_ROADMAP.md`.
- Roadmap explicitly states it is not proof that corresponding code refactoring is complete.
- Current DDD state remains staged: multiple P0/P1 domains still list next-step tasks and risks.

## Safety

- CI workflow has `permissions: contents: read`.
- Workflow does not use secrets.
- Workflow does not use SSH.
- Workflow does not deploy.
- Workflow does not call Docker on the remote server.
- Workflow does not touch `.env.real-pre`.
- No production service restart was executed.

## Remaining Risks

- CI-only is now effective; CD deployment remains Jenkins/manual-gated and was not activated.
- Pure evidence-report commits under `harness/reports/**` are intentionally ignored by CI after `118ecadb`.
- Remote server is available over public HTTP; production-domain HTTPS readiness was not re-proven in this task.
- DDD is not a full PASS. The current evidence supports a structural/status check only.
- Current local worktree remains dirty with pre-existing unrelated changes.

## Conclusion

PARTIAL.

The CI-only pipeline is effective and verified by successful GitHub Actions runs through commit `118ecadb`. The remote real-pre server remained available and healthy after the CI changes. DDD structure and status were checked, but DDD remains a staged architecture effort, not a completed refactor.
