# Content Retirement Report

## Metadata

- Time: 2026-07-18 23:27:43 +08:00
- Action: Archive
- DryRun: False
- Reason: post-task content maintenance
- Manifest: harness/manifests/single-channel-cd-current-report-retirement-extra-20260718.json
- ArchiveRoot: harness/archive/retired-content
- AllowSourceCode: False

## Auto Candidates

~~~text
(none)
~~~

## Planned / Applied Operations

~~~text
ARCHIVE harness\reports\current\latest-evidence-20260713-harness-layered-file-governance-design.md -> harness\archive\retired-content\20260718-232743\superseded-harness-design\latest-evidence-20260713-harness-layered-file-governance-design.md
ARCHIVE harness\reports\current\latest-evidence-20260713.md -> harness\archive\retired-content\20260718-232743\superseded-harness-design\latest-evidence-20260713.md
~~~

## Safety Rules

- Archive/Delete requires an explicit manifest.
- Source-like paths require -AllowSourceCode.
- Protected paths such as env files, git metadata, compose files, and database migration resources are blocked.
- Directory delete requires allowRecursive=true in the manifest.
- All targets are resolved and checked inside the repository before move/delete.
