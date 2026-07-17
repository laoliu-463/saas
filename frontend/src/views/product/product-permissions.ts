import { ROLE_CODES, hasAccess } from '../../constants/rbac'

/** 快速寄样是寄样申请动作，招商和渠道都可发起；管理员由全局权限绕过。 */
export const QUICK_SAMPLE_APPLICANT_ROLES = [
  ROLE_CODES.BIZ_LEADER,
  ROLE_CODES.BIZ_STAFF,
  ROLE_CODES.CHANNEL_LEADER,
  ROLE_CODES.CHANNEL_STAFF
]

export const canApplyQuickSampleByRole = (roles: string[] = [], isAdmin = false) =>
  isAdmin || hasAccess(roles, QUICK_SAMPLE_APPLICANT_ROLES)
