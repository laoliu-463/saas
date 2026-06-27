# Retro - DDD100 User Assign Issue #35

## What Changed

- Added `UserGroupMembershipStore` and `SysUserGroupMembershipStoreAdapter`.
- Moved `SysUserGroupMembershipApplication` away from direct `SysUserMapper` / `SysUser` imports.
- Added explicit `SysUserMapper.updateDeptById` SQL for assign/remove group membership.

## What Did Not Change

- No API contract, database schema, default real-pre config, or frontend behavior changed.
- No live real-pre user/group write E2E was executed.

## Harness Upgrade

No Harness script change. This is a backend boundary/evidence slice.

## Next

- #36 should continue role/menu and permission-policy boundaries.
