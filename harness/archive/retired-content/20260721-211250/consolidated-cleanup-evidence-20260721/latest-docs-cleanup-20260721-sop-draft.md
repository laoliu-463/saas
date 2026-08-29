# Content Retirement Report

## Metadata

- Time: 2026-07-21 21:01:00 +08:00
- Action: Archive
- DryRun: False
- Reason: cleanup: archive stale unreferenced SOP draft
- Manifest: .\harness\manifests\docs-cleanup-20260721-sop-draft.json
- ArchiveRoot: harness/archive/retired-content
- AllowSourceCode: False

## Auto Candidates

~~~text
(none)
~~~

## Planned / Applied Operations

~~~text
ARCHIVE harness\reports\current\latest-sop-refactor-design.md -> harness\archive\retired-content\20260721-210100\stale-sop-draft-20260721\latest-sop-refactor-design.md
~~~

## Safety Rules

- Archive/Delete requires an explicit manifest.
- Source-like paths require -AllowSourceCode.
- Protected paths such as env files, git metadata, compose files, and database migration resources are blocked.
- Directory delete requires allowRecursive=true in the manifest.
- All targets are resolved and checked inside the repository before move/delete.
