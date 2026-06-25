# Retro: DDD-USER-PRODUCT-SERVICE-FACADE

## What Changed

- `ProductService` no longer reads full user DTOs for display, assignment, promotion-link channel code, or cross-department binding checks.
- User-domain language now distinguishes **渠道编码** from username, display label, and full user profile.
- The new architecture test guards against reintroducing `UserOptionResponse` into `ProductService`.

## Evidence

- RED boundary test failed before the production change, proving the guard catches the old dependency.
- Focused, product-level, and U-7 expanded regressions passed after the facade split.
- Build, backend container restart, local health check, graph update, and old-call scan all passed.

## Boundary Notes

- 商品域仍决定商品状态机、活动分配、转链和 `pick_source` 映射语义。
- 用户域只提供显示标签、负责人归属引用、渠道编码和登录名等稳定出口。
- `UserOptionResponse` can remain inside user-domain master-data / compatibility surfaces, but should not cross into business services for partial facts.

## Follow-up

- Split `UserDomainFacade` compatibility DTO surfaces from business scalar exports more explicitly.
- Continue U-7 with `DataScopeResolver` and `PermissionChecker` consumption unification.
- Keep the old-call scan in the exit checklist until the compatibility DTO boundary is fully documented.
