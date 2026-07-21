# DDD-ORDER-005 Lock

## Scope

- Branch: feature/ddd/DDD-ORDER-005
- Owner: Codex
- Status: in_progress

## Allowed Area

- backend order event publisher and mapper
- backend order persistence event tests
- harness report, task handover, lock

## Excluded Area

- frontend
- database migrations
- performance calculation rules
- product copy-promotion behavior unless separately scoped

## Notes

- Do not create `harness/agent-locks` or `harness/handovers`; current Harness structure uses `rules/locks` and `tasks`.
