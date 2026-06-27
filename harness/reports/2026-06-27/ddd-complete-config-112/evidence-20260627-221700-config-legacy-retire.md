# Evidence: DDD-COMPLETE-100-CONFIG-05 #112

## Scope

- Issue: #112 `[DDD-COMPLETE-100-CONFIG-05] 配置域 legacy retire 与迁移率目标达成`
- Date: 2026-06-27
- Branch: `feature/ddd/DDD-VERIFY-001`
- Commit: `bd00a3e1`
- Remote deploy: not requested

## Change Evidence

Commit `bd00a3e1` moved configuration legacy services into the config domain boundary:

- `backend/src/main/java/com/colonel/saas/service/SysConfigService.java` -> `backend/src/main/java/com/colonel/saas/domain/config/application/SysConfigService.java`
- `backend/src/main/java/com/colonel/saas/service/BusinessRuleConfigService.java` -> `backend/src/main/java/com/colonel/saas/domain/config/infrastructure/BusinessRuleConfigService.java`
- Controllers, facade, event consumers, listeners and business consumers now import the new config-domain package paths.

## Metrics

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\probes\ddd-migration-metrics.ps1 -RepoRoot . -Format Markdown
```

Result excerpt:

| Metric | Value |
| --- | ---: |
| Raw domain share | 22.9% |
| Business migration proxy | 30.2% |
| Config proxy | 68.1% |
| Config DDD LOC | 899 |
| Config legacy service LOC | 236 |
| Config legacy entry LOC | 422 |

## Verification

Command:

```powershell
mvn -f backend/pom.xml "-Dtest=SysConfigControllerTest,LegacyConfigDomainFacadeTest,AuthApplicationTest,SampleControllerTest" test
```

Result:

- Tests run: 132
- Failures: 0
- Errors: 0
- Skipped: 0
- Build result: SUCCESS

code-review-graph `detect_changes` on `bd00a3e1^..bd00a3e1`:

- Changed files analyzed: 46
- Affected flows: 45
- Test gaps: 0
- Overall risk score: 0.00

## Boundary

#112 is closed. Parent #92 remains open for the broader config-domain epic closeout and any remaining cross-domain configuration consumption checks.

## Conclusion

PASS for #112.
