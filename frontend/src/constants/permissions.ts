export const PERMISSION_CODES = {
  DASHBOARD_ACCESS: 'dashboard:access',
  ORDER_ACCESS: 'order:access',
  PRODUCT_ACCESS: 'product:access',
  PRODUCT_MANAGE_ACCESS: 'colonel-activity:access',
  TALENT_ACCESS: 'talent:access',
  SAMPLE_ACCESS: 'sample:access',
  SHIPPING_ACCESS: 'admin-sample-logistics:access',
  DATA_ACCESS: 'data:access',
  EXCLUSIVE_ACCESS: 'data:get-exclusive-talent-status',
  SYS_USER_ACCESS: 'sys-user:access',
  SYS_ROLE_ACCESS: 'sys-role:access',
  SYS_DEPT_ACCESS: 'sys-dept:access',
  RULE_CENTER_ACCESS: 'rule-center:access',
  SYS_CONFIG_ACCESS: 'sys-config:access',
  COMMISSION_RULE_ACCESS: 'commission-rule:access',
  DOUYIN_ACCESS: 'douyin:access',
  OPERATION_LOG_ACCESS: 'operation-log:access'
} as const

export const normalizePermissionCodes = (permissionCodes: unknown): string[] => {
  if (!Array.isArray(permissionCodes)) return []
  return Array.from(new Set(permissionCodes.map(String).map((code) => code.trim()).filter(Boolean))).sort()
}

export const hasPermission = (
  permissionCodes: readonly string[] = [],
  requiredPermissions?: readonly string[]
): boolean => {
  if (!requiredPermissions?.length) return true
  const granted = new Set(permissionCodes)
  return requiredPermissions.some((permission) => granted.has(permission))
}
