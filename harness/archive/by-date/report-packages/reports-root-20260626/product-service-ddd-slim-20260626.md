# ProductService DDD Slim Evidence 2026-06-26

## Scope

- Env: local `real-pre`
- Main domain: 商品域
- Gate: backend + domain verification
- Target: 优先治理超过 5000 行的 `ProductService`

## Change

- Added `ActivityProductViewAssembler` under product application layer.
- Added `ProductAuditSupplementPayload` under product policy layer.
- `ProductService` now delegates activity product view assembly, audit supplement parsing, fee/rate/date display helpers, and shop score extraction.
- No changes to promotion conversion, `pick_source_mapping`, product status machine, attribution, commission, sample state, DB schema, or real-pre data.

## Evidence

- Pre-change evidence: code-review-graph found `ProductService.java` as the only 5000+ file.
- Post-change line count: `ProductService.java` = 4829 lines.
- Compile: `mvn -q -f backend/pom.xml -DskipTests compile` PASS.
- Targeted tests: `ProductServiceShopScoreTest,ProductServiceFilterTest,ProductServiceLibraryViewTest,DddSlimProduct001DisplayPolicyRoutingTest,ProductDisplayPolicyTest` PASS.
- code-review-graph post-check: 0 files >= 5000 lines.
- First `agent-do.ps1 -Env real-pre -Scope backend` attempt failed after isolating the pre-existing user-domain `SysMenuApplication` slice; evidence: `harness/reports/evidence-20260626-152238.md`.
- Final `agent-do.ps1 -Env real-pre -Scope backend -ContentMaintenance off` PASS; evidence: `harness/reports/evidence-20260626-152545.md`.
- Runtime gate covered backend package, backend Docker rebuild/restart, `/api/system/health`, and `npm run e2e:real-pre:p0:preflight`.
- Mixed-slice guard: `SysMenuServiceTest,SysMenuApplicationTest,SysMenuControllerTest,SysRoleControllerTest` PASS after `SysMenuService` / `SysMenuApplication` were included by the harness commit.

## Mixed Commit Note

- Pre-existing user-domain DDD files observed during this task:
  - `backend/src/main/java/com/colonel/saas/domain/user/application/SysMenuApplication.java`
  - `backend/src/test/java/com/colonel/saas/domain/user/application/SysMenuApplicationTest.java`
- `backend/src/main/java/com/colonel/saas/auth/service/SysMenuService.java` already depended on `SysMenuApplication` in the working tree.
- The final harness commit included this user-domain slice because the backend package gate required it. Review product-domain extraction and user-domain menu migration as separate logical changes inside the same commit.

## Conclusion

PASS with mixed-commit review risk noted above.
