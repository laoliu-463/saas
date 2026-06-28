/**
 * RBAC（基于角色的访问控制）常量模块
 *
 * 职责：
 * - 定义系统角色代码常量（ROLE_CODES）
 * - 提供前端路由、菜单和按钮可见性的角色适配工具
 *
 * 边界：
 * - 本模块只做 UI 可见性与历史 JWT 角色码兼容，不能替代后端鉴权。
 * - 核心权限、数据范围和业务写操作仍以后端用户域 / 业务域校验为准。
 *
 * 角色代码：
 * - admin：系统管理员，拥有全部权限（超级管理员绕过）
 * - biz_leader：业务主管
 * - biz_staff：业务专员
 * - channel_leader：渠道主管
 * - channel_staff：渠道专员
 * - ops_staff：运维专员
 */

/** 系统角色代码常量，使用 as const 确保字面量类型 */
export const ROLE_CODES = {
  ADMIN: 'admin',              // 系统管理员
  BIZ_LEADER: 'biz_leader',    // 业务主管
  BIZ_STAFF: 'biz_staff',      // 业务专员
  CHANNEL_LEADER: 'channel_leader', // 渠道主管
  CHANNEL_STAFF: 'channel_staff',   // 渠道专员
  OPS_STAFF: 'ops_staff'       // 运维专员
} as const;

/**
 * 角色代码 → 中文标签映射（供 UI 展示用）。
 * 自定义角色不在此映射中时，具体展示 fallback 由各页面处理。
 */
export const ROLE_NAME_MAP: Record<string, string> = {
  admin: '系统管理员',
  biz_leader: '业务主管',
  biz_staff: '业务专员',
  channel_leader: '渠道主管',
  channel_staff: '渠道专员',
  ops_staff: '运维专员'
};

/**
 * 历史角色码兼容映射。
 *
 * 后端当前权威角色码见 docs/领域/用户域.md；这里仅兼容未刷新 JWT 或旧 localStorage。
 */
export const LEGACY_ROLE_CODE_MAP: Record<string, string> = {
  zs_leader: ROLE_CODES.BIZ_LEADER,
  zs_staff: ROLE_CODES.BIZ_STAFF,
  // colonel_leader 已于 2026-05-30 合并至 biz_leader；保留到旧登录态自然过期。
  colonel_leader: ROLE_CODES.BIZ_LEADER,
  qd_leader: ROLE_CODES.CHANNEL_LEADER,
  qd_staff: ROLE_CODES.CHANNEL_STAFF
};

const normalizeRoleCode = (roleCode: unknown): string => {
  const code = String(roleCode ?? '').trim().toLowerCase();
  return code ? LEGACY_ROLE_CODE_MAP[code] || code : '';
};

export const normalizeRoleCodes = (roleCodes: unknown): string[] => {
  if (!Array.isArray(roleCodes)) return [];
  return Array.from(new Set(roleCodes.map(normalizeRoleCode).filter(Boolean)));
};

/**
 * 判断角色列表中是否包含管理员角色
 *
 * @param roles - 当前用户的角色代码列表
 * @returns 是否为管理员
 */
export const isAdminRole = (roles: readonly string[] = []): boolean =>
  normalizeRoleCodes(roles).includes(ROLE_CODES.ADMIN);

export const isLeaderRole = (roles: readonly string[] = []): boolean => {
  const normalized = normalizeRoleCodes(roles);
  return [ROLE_CODES.BIZ_LEADER, ROLE_CODES.CHANNEL_LEADER].some((role) => normalized.includes(role));
};

export const hasAnyRole = (roles: readonly string[] = [], allowedRoles: readonly string[] = []): boolean => {
  const normalizedRoles = normalizeRoleCodes(roles);
  const normalizedAllowedRoles = normalizeRoleCodes(allowedRoles);
  return normalizedAllowedRoles.some((role) => normalizedRoles.includes(role));
};

/**
 * 判断当前用户是否有权访问前端路由、菜单或按钮入口。
 *
 * @param roles - 当前用户的角色代码列表
 * @param requiredRoles - 资源要求的角色列表（可选，为空表示不限制）
 * @returns 是否有权限访问
 */
export const hasAccess = (roles: readonly string[] = [], requiredRoles?: readonly string[]): boolean => {
  const normalizedRequiredRoles = normalizeRoleCodes(requiredRoles || []);
  if (normalizedRequiredRoles.length === 0) return true;
  if (isAdminRole(roles)) return true;
  return hasAnyRole(roles, normalizedRequiredRoles);
};
