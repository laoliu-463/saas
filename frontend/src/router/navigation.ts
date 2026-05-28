/**
 * 导航路由工具模块
 *
 * 职责：
 * - 定义路由路径到侧边栏分组的映射关系（SECTION_MAP）
 * - 定义侧边栏分组父节点的默认落地路由（MENU_GROUP_DEFAULT_ROUTE）
 * - 提供路由路径匹配、活跃分区解析、菜单权限过滤等工具函数
 *
 * 设计说明：
 * - SECTION_MAP 按路径长度倒序匹配，确保更具体的路径优先命中
 * - MENU_GROUP_DEFAULT_ROUTE 用于分组节点点击时的默认跳转
 * - 菜单权限过滤基于角色代码，管理员角色自动绕过
 */
import { hasAccess } from '../constants/rbac'

/** 路由路径前缀到侧边栏分组 key 的映射条目类型 [前缀, 分组key] */
export type SectionMapEntry = readonly [string, string]

/**
 * 侧边栏分组父节点 -> 默认落地路由
 * 当用户点击分组父节点本身时，跳转到此处定义的路由
 * 注意：父节点本身不可直接 router.push，需要通过此映射找到子路由
 */
export const MENU_GROUP_DEFAULT_ROUTE: Record<string, string> = {
  'attribution-group': '/dashboard',      // 归因工作台 -> 归因概览
  'data-group': '/data',                  // 数据看板 -> 核心看板
  'product-manage-group': '/product/manage', // 商品管理 -> 活动列表
  'talent-group': '/talent?view=TEAM_PUBLIC', // 达人CRM -> 团队公海
  'system-group': '/system/users'         // 系统管理 -> 用户管理
}

/**
 * 侧边栏导航菜单项接口
 * 定义菜单项的基本结构，支持嵌套子菜单
 */
export interface NavigationMenuItem {
  /** 菜单显示名称 */
  label: string
  /** 菜单唯一标识 */
  key: string
  /** 对应的路由路径 */
  path?: string
  /** 允许访问的角色代码列表 */
  roles?: string[]
  /** 子菜单列表 */
  children?: NavigationMenuItem[]
  /** 所属侧边栏分组标识 */
  _section?: string
  /** 允许扩展其他字段 */
  [key: string]: unknown
}

/**
 * 路由路径前缀到侧边栏分组 key 的映射表
 *
 * 匹配规则：按路径长度倒序处理，确保更具体的路径优先命中
 * 例如 '/system/depts' 优先于 '/system' 匹配
 *
 * 分组标识说明：
 * - system: 系统管理
 * - product-manage: 商品管理
 * - product: 商品库
 * - data: 数据看板
 * - attribution: 归因工作台
 * - talent: 达人CRM
 * - sample: 合作管理
 * - dev: 开发调试
 */
export const SECTION_MAP: SectionMapEntry[] = [
  ['/system/depts', 'system'],
  ['/system/departments', 'system'],
  ['/system/operation-logs', 'system'],
  ['/product/manage/products', 'product-manage'],
  ['/product/activity', 'product-manage'],
  ['/product/review', 'product-manage'],
  ['/product/library', 'product'],
  ['/product/manage', 'product-manage'],
  ['/ops/shipping', 'sample'],
  ['/ops/exclusive', 'data'],
  ['/system/config', 'system'],
  ['/system/commission-rules', 'system'],
  ['/system/douyin', 'system'],
  ['/system/rule-center', 'system'],
  ['/system/roles', 'system'],
  ['/system/users', 'system'],
  ['/data/orders', 'data'],
  ['/dashboard', 'attribution'],
  ['/product', 'product'],
  ['/orders', 'attribution'],
  ['/talent', 'talent'],
  ['/sample', 'sample'],
  ['/data', 'data'],
  ['/dev', 'dev']
]

/**
 * 判断路由路径是否在指定前缀下
 * 用于匹配菜单高亮、权限判断等场景
 *
 * @param path - 待检查的路由路径
 * @param prefix - 前缀路径
 * @returns 路径是否等于前缀或以 前缀/ 开头
 */
export function isRoutePathUnderPrefix(path: string, prefix: string): boolean {
  const normalizedPath = normalizeRoutePath(path)
  const normalizedPrefix = normalizeRoutePath(prefix)
  return normalizedPath === normalizedPrefix || normalizedPath.startsWith(`${normalizedPrefix}/`)
}

/**
 * 根据路由路径解析所属的侧边栏分组
 * 按路径长度倒序匹配 SECTION_MAP，确保最长前缀优先命中
 *
 * @param path - 当前路由路径
 * @param sectionMap - 可选的自定义映射表，默认使用 SECTION_MAP
 * @returns 分组标识字符串，未匹配返回 null
 */
export function resolveActiveSection(path: string, sectionMap: readonly SectionMapEntry[] = SECTION_MAP): string | null {
  const matched = [...sectionMap]
    .sort(([left], [right]) => right.length - left.length) // 按路径长度倒序，确保最长前缀优先
    .find(([prefix]) => isRoutePathUnderPrefix(path, prefix))
  return matched?.[1] || null
}

/**
 * 解析侧边栏分组节点的导航目标路由
 *
 * @param key - 菜单节点 key（分组标识如 'system-group'，或路由路径如 '/system/depts'）
 * @returns 目标路由路径，未找到返回 null
 */
export function resolveMenuNavigateTarget(key: string): string | null {
  const mapped = MENU_GROUP_DEFAULT_ROUTE[key]
  if (mapped) return mapped
  // 如果 key 本身就是路由路径（以 / 开头），直接返回
  if (key.startsWith('/')) return key
  return null
}

/**
 * 判断菜单项是否应在当前分区中展示
 *
 * @param menu - 菜单项
 * @param activeSection - 当前活跃的侧边栏分区标识
 * @param _roles - 角色代码列表（预留参数，当前未使用）
 * @returns 是否展示该菜单
 */
export function shouldShowMenuInSection(
  menu: NavigationMenuItem,
  activeSection: string | null | undefined,
  _roles: readonly string[]
): boolean {
  if (!activeSection) {
    return false
  }
  if (!menu._section) {
    return false
  }
  return menu._section === activeSection
}

/**
 * 按角色和分区过滤菜单列表
 *
 * 过滤逻辑：
 * 1. 移除当前角色无权访问的菜单项
 * 2. 如果启用分区过滤，只保留属于当前分区的菜单
 * 3. 递归过滤子菜单（子菜单不做分区过滤，只做角色过滤）
 * 4. 移除过滤后子节点为空的分组节点
 *
 * @param menus - 待过滤的菜单列表
 * @param roles - 当前用户角色代码列表
 * @param activeSection - 当前活跃分区标识
 * @param applySectionFilter - 是否应用分区过滤（递归时设为 false）
 * @returns 过滤后的菜单列表
 */
export function filterAccessibleMenus<T extends NavigationMenuItem>(
  menus: readonly T[],
  roles: readonly string[],
  activeSection?: string | null,
  applySectionFilter = true
): T[] {
  const roleList = [...roles]
  return menus
    .filter((menu) => hasAccess(roleList, menu.roles))
    .filter((menu) => !applySectionFilter || shouldShowMenuInSection(menu, activeSection, roleList))
    .map((menu) => {
      if (!menu.children?.length) return menu
      // 递归过滤子菜单，子菜单不做分区过滤
      return {
        ...menu,
        children: filterAccessibleMenus(menu.children, roleList, activeSection, false)
      }
    })
    // 移除过滤后无子节点的分组
    .filter((menu) => !menu.children?.length || menu.children.length > 0)
}

/**
 * 规范化路由路径
 * 去除查询参数和 hash，确保以 / 开头，去除末尾斜杠
 *
 * @param path - 原始路径
 * @returns 规范化后的路径
 */
function normalizeRoutePath(path: string): string {
  const clean = String(path || '/').split(/[?#]/)[0]?.trim() || '/'
  const withSlash = clean.startsWith('/') ? clean : `/${clean}`
  if (withSlash === '/') return withSlash
  return withSlash.replace(/\/+$/, '')
}
