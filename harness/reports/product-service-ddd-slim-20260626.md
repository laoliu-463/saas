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

## Dirty Worktree Note

- Unrelated untracked files observed during this task:
  - `backend/src/main/java/com/colonel/saas/domain/user/application/SysMenuApplication.java`
  - `backend/src/test/java/com/colonel/saas/domain/user/application/SysMenuApplicationTest.java`
- They are not part of this商品域切片 and must not be staged in this commit.

## Conclusion

PARTIAL until `agent-do.ps1 -Env real-pre -Scope backend` completes build, restart, health and business validation.
