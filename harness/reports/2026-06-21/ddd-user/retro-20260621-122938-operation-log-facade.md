# Retro 2026-06-21 12:29 - DDD User Facade Operation Log

## What Changed

- `OperationLogService` now consumes `UserDomainFacade.getUsername` for audit username.
- The service no longer loads `UserOptionResponse` just to write `operation_log.username`.
- User terminology now distinguishes **操作人账号** from真实姓名 and full user DTO.

## Evidence Learned

- Initial design tried `getUserName`, but regression evidence showed it returns real name.
- `CurrentUserPasswordAuditIntegrationTest` proved the audit column must keep login account.
- The final design preserves behavior and narrows the cross-domain contract.

## Harness Upgrade

- No Harness script change needed.
- Existing report scripts over-collect dirty worktree details in broad dirty sessions; manual compliant evidence was created under date/domain directory.
