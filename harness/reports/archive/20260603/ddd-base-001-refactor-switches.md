# DDD-BASE-001 Refactor Switches Report

## Task

- Task ID: DDD-BASE-001
- Goal: add passive DDD refactor safety switches, all disabled by default.
- Environment: local real-pre
- Conclusion: PENDING final verification

## Switches

| Key | Default | Purpose |
| --- | --- | --- |
| `ddd.refactor.enabled` | `false` | Root DDD refactor switch; passive in this task. |
| `ddd.refactor.user-facade.enabled` | `false` | Future user domain facade migration guard. |
| `ddd.refactor.config-facade.enabled` | `false` | Future config domain facade migration guard. |
| `ddd.refactor.product-facade.enabled` | `false` | Future product domain facade migration guard. |
| `ddd.refactor.talent-facade.enabled` | `false` | Future talent domain facade migration guard. |
| `ddd.refactor.sample-application.enabled` | `false` | Future sample application service migration guard. |
| `ddd.refactor.order-application.enabled` | `false` | Future order application service migration guard. |
| `ddd.refactor.order-attribution.enabled` | `false` | Future order attribution migration guard. |
| `ddd.refactor.order-amount-policy.enabled` | `false` | Future order amount policy migration guard. |
| `ddd.refactor.performance-calc.enabled` | `false` | Future performance calculation migration guard. |
| `ddd.refactor.performance-query.enabled` | `false` | Future performance query migration guard. |
| `ddd.refactor.analytics-shadow.enabled` | `false` | Future analytics shadow validation guard. |
| `ddd.refactor.outbox.enabled` | `false` | Future outbox migration guard. |

## Change Scope

- `DddRefactorProperties` now binds the required 13 switches.
- `application.yml`, `application-real-pre.yml`, and `application-test.yml` define all switches with `${ENV:false}` defaults.
- Tests cover all-default-false and explicit-true binding.
- The integration test uses a lightweight Spring configuration binding context and does not start Testcontainers.

No Controller, Service business path, Mapper, production migration, real-pre database, frontend, or API contract is modified by this task.

## Verification

| Check | Result | Evidence |
| --- | --- | --- |
| Targeted test | PENDING | `mvn "-Dtest=DddRefactorPropertiesTest,DddRefactorPropertiesIntegrationTest" test` |
| Backend full test | PENDING | Run from a clean verification worktree to avoid unrelated dirty config-domain changes in the main worktree. |
| Backend package | PENDING | `mvn -DskipTests package` |
| Business path guard | PENDING | No production business code should read the new getters in this task. |
| Runtime env guard | PENDING | No `DDD_REFACTOR_*` environment variables should be present/enabled in real-pre. |
| Health | PENDING | Backend health check after package/restart if code verification passes. |

## Rollback

1. Revert `DddRefactorProperties.java` to the previous switch structure.
2. Revert the `ddd.refactor` sections in the three Spring YAML files.
3. Revert the two DDD refactor property tests.
4. Re-run targeted tests and backend verification.

The rollback risk is low because this task only adds passive configuration binding and does not switch any implementation path.

## Out Of Scope

- Do not enable any `ddd.refactor.*` switch.
- Do not connect these switches to Controller or Service behavior.
- Do not add Facade, Port, Policy, or ApplicationService implementations.
- Do not modify production database schema or data.
