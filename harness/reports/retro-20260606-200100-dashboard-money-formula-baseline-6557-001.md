# Retro - dashboard-money-formula-baseline-6557-001

## What Worked

- TDD exposed the backend结算轨服务费支出 bug first, then verified the fix.
- Targeted frontend tests caught explicit zero expense being overwritten by fallback logic.
- Harness full path rebuilt and restarted real-pre containers, then local health and P0 preflight passed.
- SQL, API and browser evidence all showed the same key fact: user baseline is not stable against live real-pre data.

## What Failed Or Was Blocked

- `agent-do` failed at Git safe step because the worktree already had broad staged/dirty content and whitespace failures outside this task.
- The in-app Browser plugin was not exposed by `tool_search`; local Playwright was used instead.
- The baseline 6557/141508.04 cannot be certified without freezing time point, track and inclusion rules.

## Harness Follow-up

- Suggested: `git-push-safe.ps1` should support a path allowlist or fail early when unrelated staged files exist, before staging broad dirty state.
- Suggested: add a standard money-baseline snapshot command that records SQL/API/browser values under one timestamp, because live real-pre data changes during verification.
- Suggested: add a documented fallback when Browser plugin is unavailable, using bundled Playwright and recording screenshot paths.

## Harness Upgrade Status

- No harness code change was made in this task.
- This retro records the proposed harness improvements for future work.
