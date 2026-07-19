# Evidence Report

## Metadata

- Time: 2026-07-19 15:05:34 +08:00
- Environment: real-pre
- Scope: docs
- Branch: codex/166-git-governance-phase1
- Commit: 52976a74
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
.github/workflows/ci.yml
harness/rules/changelog.md
harness/scripts/modules/HarnessFileGovernance.psm1
harness/scripts/tests/check-harness-limits.Tests.ps1
harness/scripts/tests/github-collaboration.Tests.ps1
~~~

## Owned Git Status

~~~text
M .github/workflows/ci.yml
 M harness/rules/changelog.md
 M harness/scripts/modules/HarnessFileGovernance.psm1
 M harness/scripts/tests/check-harness-limits.Tests.ps1
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

首轮 PR CI 暴露 Linux 路径分隔符和测试 Compose 迁移耦合；已改为跨平台路径处理与无仓库迁移的原生 service containers，并补充 29 项治理回归。平台规则仍待后端基线修复后启用。

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
