import { hasAccess } from '../constants/rbac'

export type SectionMapEntry = readonly [string, string]

/** 侧边栏分组父节点 -> 默认落地路由（父节点本身不可直接 router.push） */
export const MENU_GROUP_DEFAULT_ROUTE: Record<string, string> = {
  'attribution-group': '/dashboard',
  'data-group': '/data',
  'product-manage-group': '/product/manage',
  'talent-group': '/talent?view=TEAM_PUBLIC',
  'system-group': '/system/users'
}

export interface NavigationMenuItem {
  label: string
  key: string
  path?: string
  roles?: string[]
  children?: NavigationMenuItem[]
  _section?: string
  [key: string]: unknown
}

/** 路由前缀 -> 侧边栏分组 key 的映射。匹配时会按路径长度倒序处理。 */
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

export function isRoutePathUnderPrefix(path: string, prefix: string): boolean {
  const normalizedPath = normalizeRoutePath(path)
  const normalizedPrefix = normalizeRoutePath(prefix)
  return normalizedPath === normalizedPrefix || normalizedPath.startsWith(`${normalizedPrefix}/`)
}

export function resolveActiveSection(path: string, sectionMap: readonly SectionMapEntry[] = SECTION_MAP): string | null {
  const matched = [...sectionMap]
    .sort(([left], [right]) => right.length - left.length)
    .find(([prefix]) => isRoutePathUnderPrefix(path, prefix))
  return matched?.[1] || null
}

export function resolveMenuNavigateTarget(key: string): string | null {
  const mapped = MENU_GROUP_DEFAULT_ROUTE[key]
  if (mapped) return mapped
  if (key.startsWith('/')) return key
  return null
}

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
      return {
        ...menu,
        children: filterAccessibleMenus(menu.children, roleList, activeSection, false)
      }
    })
    .filter((menu) => !menu.children?.length || menu.children.length > 0)
}

function normalizeRoutePath(path: string): string {
  const clean = String(path || '/').split(/[?#]/)[0]?.trim() || '/'
  const withSlash = clean.startsWith('/') ? clean : `/${clean}`
  if (withSlash === '/') return withSlash
  return withSlash.replace(/\/+$/, '')
}
