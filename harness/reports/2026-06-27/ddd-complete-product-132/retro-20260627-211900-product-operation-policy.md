# Retro Summary - #132 Product Operation Policy

## What Happened

- Product operation log/status semantics were already partially committed by parallel agents before this verification pass.
- The verification path exposed a committed user-domain test compile blocker; it was fixed in 2e93a069 and pushed.
- Frontend build initially failed because local `node_modules` was incomplete, not because source failed typecheck or build.

## Verification Notes

- `agent-do.ps1` was not used for the final runtime proof because the main worktree had concurrent frontend dirty files.
- Instead, backend/frontend images were built from a clean detached worktree at 2e93a069, then real-pre containers were restarted with `--no-build`.
- Targeted backend, user-domain blocker test, frontend unit test, frontend build, Docker health and real-pre P0 preflight all passed.

## Harness Learning

- Standard full-scope execution is risky during parallel agent work because it can read or stage unrelated dirty files.
- Future harness improvement: add a clean-source or staged-only execution mode that builds images from a specified commit/worktree and records the source commit explicitly.

## Follow-up

- Continue product #133 for query/read-model收口.
- Keep #135 as PENDING until a real order generated from system promotion link returns and proves `pick_source` attribution.
