# Evidence Report - 2026-06-21 Server CD Feasibility

- Time: 2026-06-21 19:40:46 +08:00
- Env: local workspace and remote real-pre server
- Local branch: feature/ddd/DDD-VERIFY-001
- Local commit: 8426ea0bfc88a6141dc42b9b63a6562ea9a3d7bd
- Remote host: VM-0-12-ubuntu
- Remote user: deploy
- Remote deploy app: /opt/saas/app
- Remote deployed: no
- Remote restart: no
- DB write: no
- Conclusion: PARTIAL

## Verified Evidence

- GitHub Actions CI exists and is active, but it only runs CI.
- Jenkinsfile exists and includes CI, environment guard, package, controlled real-pre deploy,
  health check, optional E2E gates, and evidence archiving.
- Server app directory exists: /opt/saas/app.
- Server env file exists: /opt/saas/env/.env.real-pre.
- Required deploy scripts exist on server:
  - scripts/deploy-real-pre.sh
  - scripts/health-check.sh
  - scripts/run-real-pre-db-migrations.sh
  - scripts/rollback-real-pre.sh
- Deploy script syntax check passed on server.
- Docker is available on server.
- Docker Compose is available on server.
- deploy user belongs to docker group.
- docker compose config passed for real-pre.
- real-pre required env guard keys passed without printing secrets:
  COMPOSE_PROJECT_NAME, SPRING_PROFILES_ACTIVE, DB_NAME, APP_TEST_ENABLED,
  DOUYIN_TEST_ENABLED, DOUYIN_REAL_UPSTREAM_MODE, ORDER_SYNC_ENABLED,
  TALENT_COLLECT_MODE, TALENT_COLLECT_API_ENABLED,
  TALENT_PUBLIC_PAGE_CRAWL_ENABLED, LOGISTICS_PROVIDER,
  LOGISTICS_KD100_ENABLED, LOGISTICS_KD100_SUBSCRIBE_ENABLED,
  LOGISTICS_SYNC_ENABLED, EXCLUSIVE_ENABLED.
- Required secret-like values were present and not placeholders; values were not printed.
- Current real-pre containers are healthy:
  - backend-real-pre: healthy, localhost 8081 health returned 200 and {"status":"UP"}.
  - frontend-real-pre: healthy, localhost 3001 /healthz returned 200 and ok.
  - postgres-real-pre: healthy.
  - redis-real-pre: healthy.
- Health-check script logic passed when invoked with bash.

## Blocking Gaps

- Jenkins is not installed/enabled on the server:
  - systemctl is-active jenkins: inactive
  - systemctl is-enabled jenkins: not-found
  - no Jenkins process
  - no Jenkins container
- Server PATH for deploy user lacks Java and Maven.
  Jenkinsfile directly runs backend mvn clean test and mvn clean package.
- Backend Dockerfile only copies target/*.jar; it does not build the jar in Docker.
  A reliable CD run therefore needs a successful Maven package before docker compose build.
- Server backend/target exists but is owned by root and is not writable by deploy user.
  Direct server-side packaging from /opt/saas/app would fail or reuse stale artifacts.
- Shell scripts are tracked as mode 100644 and are not executable on server.
  Jenkins deploy stage runs chmod +x before deploy, but RUN_REAL_PRE_E2E-only health stage
  can hit permission denied if no deploy stage ran first.
- Current running image tag is 96700af8, while /opt/saas/app HEAD is e96fdb4 and remote
  gitee branch currently resolves to e14dcbb3. This is acceptable for a running service,
  but proves CD is not currently keeping the server automatically converged.

## Feasibility Conclusion

Server CD is feasible, but not currently ready as a fully automated server pipeline.

The deployment scripts, Compose configuration, env guard, Docker access, and real-pre
health checks are enough to support a controlled CD implementation. The missing pieces are
the Jenkins/runner runtime, Java/Maven packaging capability, script execution permissions,
and artifact ownership cleanup.

## Required Next Steps

1. Install and enable a CD runner on the server, or use an external runner with SSH access.
2. Install Java 17 and Maven for the Jenkins agent, or move backend packaging into a
   reproducible Docker build stage.
3. Make shell invocation independent of file execute bits by calling bash explicitly, or
   track executable bits and keep the existing chmod guard.
4. Fix backend/target ownership or avoid persistent target artifacts in /opt/saas/app.
5. Decide one source of truth for deployment refs: GitHub origin, Gitee, or Jenkins SCM.
6. Run a non-production deploy rehearsal with DEPLOY_REAL_PRE=false first, then a controlled
   real-pre deploy only after branch, artifact, and rollback evidence are confirmed.

## Retro Summary

- No production deploy was performed.
- No container restart was performed.
- No database write was performed.
- No Harness upgrade is required from this feasibility check.
