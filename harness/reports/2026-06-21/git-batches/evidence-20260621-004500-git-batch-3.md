# Evidence Report - GIT-BATCH-3 Engineering Config Migration

- Time: 2026-06-21 00:45:00 +08:00
- Env: local real-pre workspace
- Branch: feature/ddd/DDD-VERIFY-001
- Base commit: 6fb238fa
- Scope: docs/harness-engineering
- Remote deploy: no
- Docker restart: not applicable, docs/harness config only
- Business data write: no
- Conclusion: PASS for pre-commit engineering-config validation

## Scope

- Move Matt Pocock engineering skill config source from `docs/agents/` into `harness/engineering/`.
- Add `harness/engineering/README.md`, `context.md`, `issue-tracker.md`, `triage-labels.md`, and `issues-index.md`.
- Update `harness/INDEX.md` to expose `engineering/`.
- Update harness structure policy and `check-harness-limits.ps1` to allow `engineering/` as a first-level harness directory.
- Add `harness/manifests/2026-06-19-engineering-merge.md` as the migration manifest.

## Explicitly Excluded

- `CONTEXT.md`, `UBIQUITOUS_LANGUAGE.md`, DDD ADR/PRD/status docs, and `DOMAIN_STATUS.md`.
- `harness/engineering/PHASE-1-DDD-USER-DATASCOPE.md`, because it is a historical handoff and needs separate status correction.
- Backend and frontend implementation changes.

## Evidence Collected

- `harness/engineering` direct file count: 6 currently present; only 5 core config files are staged for this batch.
- Core engineering file line counts:
  - `README.md`: 63
  - `context.md`: 82
  - `issue-tracker.md`: 34
  - `triage-labels.md`: 51
  - `issues-index.md`: 96
- Migration manifest line count after correction: under 200.
- `harness/manifests` direct file count: 10.
- `harness/reports/2026-06-21`: 10 files and this `git-batches/` subdir.

## Closeout Verification

- Staged files: 14 paths.
- Staged forbidden backend/frontend/env/key paths: 0.
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\check-harness-limits.ps1`: PASS.
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\safety-check.ps1 -Env real-pre -Scope docs -DryRun`: PASS.
- `git diff --cached --check`: PASS after removing trailing blank EOF lines in this evidence/retro pair.
