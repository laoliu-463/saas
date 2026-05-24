import { hasAccess, ROLE_CODES } from '../constants/rbac'
import {
  isRoutePathUnderPrefix,
  resolveActiveSection,
  resolveMenuNavigateTarget,
  type NavigationMenuItem
} from './navigation'

export const TALENT_MENU_KEYS = {
  teamPublic: '/talent?view=TEAM_PUBLIC',
  myTalents: '/talent?view=MY_TALENTS',
  naturalOrders: '/talent?view=NATURAL_ORDERS',
  blacklist: '/talent?view=BLACKLIST'
} as const

export interface MenuTreeNode extends NavigationMenuItem {
  label: string
  key: string
  /** 顶部业务域 key，一级节点等于自身 key */
  topKey: string
  roles?: string[]
  testId?: string
  /** 是否在顶部导航展示 */
  showInTop?: boolean
  children?: MenuTreeNode[]
}

const ROLE = ROLE_CODES

/** 统一菜单数据源：一级为顶部业务域，children 为左侧子功能 */
export const MENU_TREE: MenuTreeNode[] = [
  {
    label: '归因工作台',
    key: 'attribution',
    topKey: 'attribution',
    showInTop: false,
    roles: [ROLE.BIZ_LEADER, ROLE.CHANNEL_LEADER, ROLE.ADMIN],
    children: [
      { label: '归因概览', key: '/dashboard', topKey: 'attribution', path: '/dashboard' },
      { label: '订单工作台', key: '/orders', topKey: 'attribution', path: '/orders' }
    ]
  },
  {
    label: '数据看板',
    key: 'data',
    topKey: 'data',
    testId: 'nav-dashboard',
    showInTop: true,
    roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF],
    children: [
      { label: '核心看板', key: '/data', topKey: 'data', path: '/data' },
      { label: '订单明细', key: '/data/orders', topKey: 'data', path: '/data/orders' },
      {
        label: '独家状态',
        key: '/ops/exclusive',
        topKey: 'data',
        path: '/ops/exclusive',
        roles: [ROLE.BIZ_LEADER, ROLE.CHANNEL_LEADER, ROLE.ADMIN]
      }
    ]
  },
  {
    label: '商品库',
    key: 'product',
    topKey: 'product',
    testId: 'nav-product',
    showInTop: true,
    roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF],
    children: [{ label: '商品库', key: '/product', topKey: 'product', path: '/product' }]
  },
  {
    label: '商品管理',
    key: 'product-manage',
    topKey: 'product-manage',
    testId: 'nav-activity-product',
    showInTop: true,
    roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF],
    children: [
      { label: '活动列表', key: '/product/manage', topKey: 'product-manage', path: '/product/manage', roles: [ROLE.BIZ_LEADER] },
      {
        label: '商品列表',
        key: '/product/manage/products',
        topKey: 'product-manage',
        path: '/product/manage/products',
        roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF]
      }
    ]
  },
  {
    label: '达人 CRM',
    key: 'talent',
    topKey: 'talent',
    testId: 'nav-talent',
    showInTop: true,
    roles: [ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF],
    children: [
      { label: '团队公海', key: TALENT_MENU_KEYS.teamPublic, topKey: 'talent', path: TALENT_MENU_KEYS.teamPublic },
      { label: '我的达人', key: TALENT_MENU_KEYS.myTalents, topKey: 'talent', path: TALENT_MENU_KEYS.myTalents },
      { label: '自然出单达人', key: TALENT_MENU_KEYS.naturalOrders, topKey: 'talent', path: TALENT_MENU_KEYS.naturalOrders },
      { label: '达人黑名单', key: TALENT_MENU_KEYS.blacklist, topKey: 'talent', path: TALENT_MENU_KEYS.blacklist }
    ]
  },
  {
    label: '合作管理',
    key: 'sample',
    topKey: 'sample',
    testId: 'nav-sample',
    showInTop: true,
    roles: [
      ROLE.BIZ_LEADER,
      ROLE.BIZ_STAFF,
      ROLE.CHANNEL_LEADER,
      ROLE.CHANNEL_STAFF,
      ROLE.OPS_STAFF
    ],
    children: [
      {
        label: '合作单',
        key: '/sample',
        topKey: 'sample',
        path: '/sample',
        roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF]
      },
      {
        label: '发货台',
        key: '/ops/shipping',
        topKey: 'sample',
        path: '/ops/shipping',
        roles: [ROLE.OPS_STAFF],
        testId: 'nav-shipping'
      }
    ]
  },
  {
    label: '系统管理',
    key: 'system',
    topKey: 'system',
    testId: 'nav-system',
    showInTop: true,
    roles: [ROLE.ADMIN],
    children: [
      { label: '用户管理', key: '/system/users', topKey: 'system', path: '/system/users' },
      { label: '角色管理', key: '/system/roles', topKey: 'system', path: '/system/roles' },
      { label: '部门管理', key: '/system/depts', topKey: 'system', path: '/system/depts' },
      { label: '规则中心', key: '/system/rule-center', topKey: 'system', path: '/system/rule-center' },
      { label: '高级配置', key: '/system/config', topKey: 'system', path: '/system/config' },
      { label: '提成规则', key: '/system/commission-rules', topKey: 'system', path: '/system/commission-rules' },
      { label: '抖店联调', key: '/system/douyin', topKey: 'system', path: '/system/douyin' },
      { label: '操作日志', key: '/system/operation-logs', topKey: 'system', path: '/system/operation-logs' }
    ]
  }
]

export type TopMenuItem = Pick<MenuTreeNode, 'label' | 'key' | 'testId' | 'topKey'>

function cloneWithoutMeta(node: MenuTreeNode): MenuTreeNode {
  const cloned: MenuTreeNode = {
    label: node.label,
    key: node.key,
    topKey: node.topKey,
    roles: node.roles,
    testId: node.testId,
    showInTop: node.showInTop,
    path: node.path
  }
  if (node.children?.length) {
    cloned.children = node.children.map(cloneWithoutMeta)
  }
  return cloned
}

/** 按角色过滤菜单树（在生成阶段完成权限过滤） */
export function filterMenuTreeByRoles(
  nodes: readonly MenuTreeNode[],
  roles: readonly string[]
): MenuTreeNode[] {
  const roleList = [...roles]
  return nodes
    .filter((node) => hasAccess(roleList, node.roles))
    .map((node) => {
      if (!node.children?.length) return cloneWithoutMeta(node)
      const children = filterMenuTreeByRoles(node.children, roleList)
      return { ...cloneWithoutMeta(node), children }
    })
    .filter((node) => !Array.isArray(node.children) || node.children.length > 0)
}

export function buildAccessibleMenuTree(roles: readonly string[]): MenuTreeNode[] {
  return filterMenuTreeByRoles(MENU_TREE, roles)
}

export function getTopMenus(roles: readonly string[]): TopMenuItem[] {
  return buildAccessibleMenuTree(roles)
    .filter((node) => node.showInTop !== false)
    .map(({ label, key, testId, topKey }) => ({ label, key, testId, topKey }))
}

export function resolveActiveTopKey(path: string): string | null {
  return resolveActiveSection(path)
}

export function findTopMenuNode(tree: readonly MenuTreeNode[], topKey: string | null | undefined): MenuTreeNode | null {
  if (!topKey) return null
  return tree.find((node) => node.key === topKey) || null
}

/** 左侧菜单 = 当前 activeTopKey 对应节点的 children（替换，非追加） */
export function getLeftMenus(tree: readonly MenuTreeNode[], topKey: string | null | undefined): MenuTreeNode[] {
  const topNode = findTopMenuNode(tree, topKey)
  return topNode?.children ? topNode.children.map(cloneWithoutMeta) : []
}

export function resolveFirstAccessiblePath(
  node: MenuTreeNode | null | undefined,
  roles: readonly string[]
): string | null {
  if (!node) return null
  const roleList = [...roles]

  if (node.children?.length) {
    for (const child of node.children) {
      const target = resolveFirstAccessiblePath(child, roleList)
      if (target) return target
    }
    return resolveMenuNavigateTarget(node.key)
  }

  if (!hasAccess(roleList, node.roles)) return null
  const path = typeof node.path === 'string' ? node.path : undefined
  if (path) return path
  return resolveMenuNavigateTarget(node.key)
}

export function resolveTopMenuDefaultPath(topKey: string, roles: readonly string[]): string | null {
  const tree = buildAccessibleMenuTree(roles)
  const topNode = findTopMenuNode(tree, topKey)
  return resolveFirstAccessiblePath(topNode, roles)
}

export function resolveActiveLeftKey(path: string, viewQuery?: string | null): string {
  if (isRoutePathUnderPrefix(path, '/ops/shipping')) return '/ops/shipping'
  if (isRoutePathUnderPrefix(path, '/ops/exclusive')) return '/ops/exclusive'
  if (isRoutePathUnderPrefix(path, '/system/depts')) return '/system/depts'
  if (isRoutePathUnderPrefix(path, '/system/rule-center')) return '/system/rule-center'
  if (isRoutePathUnderPrefix(path, '/system/config')) return '/system/config'
  if (isRoutePathUnderPrefix(path, '/system/douyin')) return '/system/douyin'
  if (isRoutePathUnderPrefix(path, '/system/operation-logs')) return '/system/operation-logs'
  if (isRoutePathUnderPrefix(path, '/system/commission-rules')) return '/system/commission-rules'
  if (isRoutePathUnderPrefix(path, '/system/roles')) return '/system/roles'
  if (isRoutePathUnderPrefix(path, '/system/users')) return '/system/users'
  if (path === '/data/orders') return '/data/orders'
  if (path === '/product/manage/products') return '/product/manage/products'
  if (path === '/product/manage') return '/product/manage'
  if (isRoutePathUnderPrefix(path, '/product/manage')) return '/product/manage'
  if (isRoutePathUnderPrefix(path, '/product/activity')) return '/product/manage'
  if (isRoutePathUnderPrefix(path, '/product/review')) return '/product/manage/products'
  if (isRoutePathUnderPrefix(path, '/product/library')) return '/product'
  if (isRoutePathUnderPrefix(path, '/talent')) {
    const view = viewQuery || 'TEAM_PUBLIC'
    if (view === 'MY_TALENTS') return TALENT_MENU_KEYS.myTalents
    if (view === 'NATURAL_ORDERS') return TALENT_MENU_KEYS.naturalOrders
    if (view === 'BLACKLIST') return TALENT_MENU_KEYS.blacklist
    return TALENT_MENU_KEYS.teamPublic
  }
  return path
}

export function isTopMenuActive(topKey: string, path: string): boolean {
  const activeTopKey = resolveActiveTopKey(path)
  return activeTopKey === topKey
}
