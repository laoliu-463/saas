# Evidence Report - 2026-06-19 CI-Only Activation And Read-Only Checks

- Time: 2026-06-19 11:37:00 +08:00
- Env: GitHub Actions, local workspace, remote real-pre server
- Branch: feature/ddd/DDD-VERIFY-001
- Commit: dfa0954b `ci: add GitHub Actions validation workflow`
- Worktree clean: no
- Worktree note: pre-existing local changes remain in auth/login/request files and harness report archive moves; they were not staged in the CI commit.
- Remote deployed: no
- Remote write/restart: no
- Conclusion: PARTIAL

## Scope

- Added GitHub Actions CI-only workflow at `.github/workflows/ci.yml`.
- Triggered workflow by pushing commit `dfa0954b` to GitHub.
- Performed read-only remote health checks.
- Performed DDD structure/status read-only checks.
- Did not deploy, restart containers, update remote env, or write production data.

## CI/CD Evidence

- GitHub Actions permissions: enabled, `allowed_actions=all`.
- Workflow registered: `CI`, active, workflow id `298639342`.
- Run id: `27803688436`.
- Run URL: `https://github.com/laoliu-463/saas/actions/runs/27803688436`.
- Event: push.
- Head branch: `feature/ddd/DDD-VERIFY-001`.
- Head sha: `dfa0954b6d2ad0be11f97341430ade139d8f1f5c`.
- Run conclusion: success.

Jobs:

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

Commit check-runs:

- `total_count=2`.
- Both check-runs completed with `conclusion=success`.
- Commit statuses endpoint still has `total_count=0`; this is expected because GitHub Actions reports through check-runs, not legacy statuses.

## Remote Server Evidence

- Public frontend health: `http://1.14.108.159/healthz` -> `ok`.
- Public backend health: `http://1.14.108.159/api/system/health` -> `{"status":"UP"}`.
- Remote app directory commit: `e96fdb4`.
- Remote deployment was intentionally not updated to `dfa0954b`.
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

- New workflow has `permissions: contents: read`.
- Workflow does not use secrets.
- Workflow does not use SSH.
- Workflow does not deploy.
- Workflow does not call Docker on the remote server.
- Workflow does not touch `.env.real-pre`.
- No production service restart was executed.

## Remaining Risks

- CI-only is now effective; CD deployment remains Jenkins/manual-gated and was not activated.
- Remote server is available over public HTTP; production-domain HTTPS readiness was not re-proven in this task.
- DDD is not a full PASS. The current evidence supports a structural/status check only.
- Current local worktree remains dirty with pre-existing unrelated changes.

## Conclusion

PARTIAL.

The CI-only pipeline is now effective and verified by a successful GitHub Actions run on commit `dfa0954b`. The remote real-pre server remains available and healthy after the push. DDD structure and status were checked, but DDD should remain a staged architecture effort, not a completed refactor.
