# Content Retirement Report

## Metadata

- Time: 2026-07-16 23:00:40 +08:00
- Action: Plan
- DryRun: False
- Reason: fix: query performance activity column
- Manifest: (none)
- ArchiveRoot: harness/archive/retired-content
- AllowSourceCode: False

## Auto Candidates

~~~text
- docs/06-技术架构与数据模型.md [document-debt] -> review/resolve-from-debt-register; evidence=listed in harness/rules/state/snapshots/05-*.md debt register
- docs/deploy/02-jenkins-later.md [document-debt] -> review/resolve-from-debt-register; evidence=listed in harness/rules/state/snapshots/05-*.md debt register
- docs/deploy/07-Jenkins自动化部署规划.md [document-debt] -> review/resolve-from-debt-register; evidence=listed in harness/rules/state/snapshots/05-*.md debt register
- docs/归档/旧版V2.2完整方案.md [document-debt] -> review/resolve-from-debt-register; evidence=listed in harness/rules/state/snapshots/05-*.md debt register
- docs/领域/业绩域.md [document-debt] -> review/resolve-from-debt-register; evidence=listed in harness/rules/state/snapshots/05-*.md debt register
- hs_err_pid68956.log [root-transient-file] -> review/delete-if-no-longer-needed; evidence=top-level generated/transient filename pattern: *.log
- hs_err_pid84964.log [root-transient-file] -> review/delete-if-no-longer-needed; evidence=top-level generated/transient filename pattern: *.log
~~~

## Planned / Applied Operations

~~~text
(none)
~~~

## Safety Rules

- Archive/Delete requires an explicit manifest.
- Source-like paths require -AllowSourceCode.
- Protected paths such as env files, git metadata, compose files, and database migration resources are blocked.
- Directory delete requires allowRecursive=true in the manifest.
- All targets are resolved and checked inside the repository before move/delete.
