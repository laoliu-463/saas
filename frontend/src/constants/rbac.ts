/**
 * RBAC（基于角色的访问控制）常量模块
 *
 * 职责：
 * - 定义系统角色代码常量（ROLE_CODES）
 * - 提供角色判断工具函数（isAdminRole、hasAccess）
 *
 * 角色层级：
 * - admin：系统管理员，拥有全部权限（超级管理员绕过）
 * - biz_leader：业务主管
 * - biz_staff：业务专员
 * - channel_leader：渠道主管
 * - channel_staff：渠道专员
 * - ops_staff：运维专员
 *
 * 权限规则：
 * - 未设置 requiredRoles 的路由/菜单默认允许所有角色访问
 * - admin 角色自动绕过所有角色限制（超级管理员特权）
 * - 非 admin 角色需要匹配 requiredRoles 中的至少一个
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
 * 自定义角色不在此映射中时，sanitizeRoleName 会 fallback 到 "自定义角色"，
 * 永远不展示原始 roleCode。
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
 * 判断角色列表中是否包含管理员角色
 *
 * @param roles - 当前用户的角色代码列表
 * @returns 是否为管理员
 */
export const isAdminRole = (roles: string[] = []): boolean => roles.includes(ROLE_CODES.ADMIN);

/**
 * 判断当前用户是否有权访问指定资源
 *
 * @param roles - 当前用户的角色代码列表
 * @param requiredRoles - 资源要求的角色列表（可选，为空表示不限制）
 * @returns 是否有权限访问
 */
export const hasAccess = (roles: string[] = [], requiredRoles?: string[]): boolean => {
  // 未设置角色要求时，所有角色均可访问
  if (!requiredRoles || requiredRoles.length === 0) return true;
  // 管理员角色自动绕过所有限制
  if (isAdminRole(roles)) return true;
  // 非管理员需要匹配 requiredRoles 中的至少一个角色
  return requiredRoles.some((role) => roles.includes(role));
};
