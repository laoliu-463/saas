export const ROLE_CODES = {
  ADMIN: 'admin',
  BIZ_LEADER: 'biz_leader',
  BIZ_STAFF: 'biz_staff',
  CHANNEL_LEADER: 'channel_leader',
  CHANNEL_STAFF: 'channel_staff',
  OPS_STAFF: 'ops_staff'
} as const;

export const isAdminRole = (roles: string[] = []): boolean => roles.includes(ROLE_CODES.ADMIN);

export const hasAccess = (roles: string[] = [], requiredRoles?: string[]): boolean => {
  if (!requiredRoles || requiredRoles.length === 0) return true;
  if (isAdminRole(roles)) return true;
  return requiredRoles.some((role) => roles.includes(role));
};

