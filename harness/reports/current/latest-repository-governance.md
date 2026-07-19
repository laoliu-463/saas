# Evidence Report

## Metadata

- Time: 2026-07-19 17:40:20 +08:00
- Environment: real-pre
- Scope: docs
- Branch: codex/repository-governance-mainline-20260719
- Commit: fe50573b
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
.github/workflows/ci.yml
CONTRIBUTING.md
harness/rules/skills/git/git-change-control.md
harness/scripts/commands/agent-do.ps1
harness/scripts/commands/git-push-safe.ps1
harness/scripts/tests/github-collaboration.Tests.ps1
~~~

## Owned Git Status

~~~text
M .github/workflows/ci.yml
 M CONTRIBUTING.md
 M harness/rules/skills/git/git-change-control.md
 M harness/scripts/commands/agent-do.ps1
 M harness/scripts/commands/git-push-safe.ps1
 M harness/scripts/tests/github-collaboration.Tests.ps1
~~~

## Build Result

~~~text
Scope=docs: build skipped.
~~~

## Docker Status

~~~text
not collected
not collected
~~~

## Health Check Result

~~~text
Scope=docs: compose restart and HTTP health checks skipped by scoped local harness path.
~~~

## Business Validation Result

~~~text
Scope=docs: business validation not applicable; safety check executed.
~~~

## Content Maintenance Result

~~~text
Content maintenance skipped by -ContentMaintenance off.
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

本轮根因治理：旧默认分支与服务器运行线分叉且缺少平台保护；已建立受保护 main 与 release/real-pre，并以 CI/PR/串行合并合同阻止并行合并和部署。

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
