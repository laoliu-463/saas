/**
 * 菜单树数据源与查询工具
 *
 * 职责：
 * - 维护全局菜单树 MENU_TREE（两级结构：顶部业务域 -> 左侧子功能）
 * - 提供按角色过滤菜单树的工具函数
 * - 解析当前路由路径对应的活跃顶部 key / 左侧 key
 * - 计算点击顶部菜单时的默认跳转路径
 *
 * 菜单树结构约定：
 * - 一级节点（topKey === key）为顶部导航业务域
 * - 二级节点（children）为左侧边栏子菜单
 * - 路由路径统一在 navigation.ts 的 SECTION_MAP 中声明归属
 */
import { hasAccess, ROLE_CODES } from '../constants/rbac'
import {
  isRoutePathUnderPrefix,
  resolveActiveSection,
  resolveMenuNavigateTarget,
  type NavigationMenuItem
} from './navigation'

/**
 * 达人 CRM 左侧菜单 key 常量
 * 达人模块使用 query 参数 view 区分不同视图，此处统一管理
 */
export const TALENT_MENU_KEYS = {
  /** 团队公海视图 */
  teamPublic: '/talent?view=TEAM_PUBLIC',
  /** 我的达人视图 */
  myTalents: '/talent?view=MY_TALENTS',
  /** 自然出单达人视图 */
  naturalOrders: '/talent?view=NATURAL_ORDERS',
  /** 达人黑名单视图 */
  blacklist: '/talent?view=BLACKLIST'
} as const

/**
 * 菜单树节点接口
 * 扩展 NavigationMenuItem，增加菜单树特有的元数据字段
 */
export interface MenuTreeNode extends NavigationMenuItem {
  /** 菜单显示名称 */
  label: string
  /** 菜单唯一标识（一级为业务域标识如 'data'，二级为路由路径如 '/data/orders'） */
  key: string
  /** 顶部业务域 key，一级节点等于自身 key，二级节点指向所属一级节点 */
  topKey: string
  /** 允许访问该菜单的角色代码列表，为空表示不限制 */
  roles?: string[]
  /** E2E 测试用的 data-testid 标识 */
  testId?: string
  /** 是否在顶部导航栏展示，为 false 时只在特定条件下可见 */
  showInTop?: boolean
  /** 子菜单列表 */
  children?: MenuTreeNode[]
}

/** 角色代码别名，简化菜单定义中的引用 */
const ROLE = ROLE_CODES

/**
 * 统一菜单数据源
 *
 * 结构说明：
 * - 一级节点：顶部业务域（归因工作台、商品库、商品管理、达人CRM、合作管理、数据看板、系统管理）
 * - 二级节点：左侧边栏子功能菜单
 * - roles 字段控制可见性，未设置则不限制
 */
export const MENU_TREE: MenuTreeNode[] = [
  {
    label: '归因工作台',
    key: 'attribution',
    topKey: 'attribution',
    showInTop: false, // 不在顶部导航展示，通过其他入口访问
    roles: [ROLE.BIZ_LEADER, ROLE.CHANNEL_LEADER, ROLE.ADMIN],
    children: [
      { label: '归因概览', key: '/dashboard', topKey: 'attribution', path: '/dashboard' },
      { label: '订单工作台', key: '/orders', topKey: 'attribution', path: '/orders' }
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
    roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.ADMIN],
    children: [
      { label: '活动列表', key: '/product/manage', topKey: 'product-manage', path: '/product/manage', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF] },
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
    roles: [ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF, ROLE.ADMIN],
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
        // 发货台仅运维人员可见
        roles: [ROLE.OPS_STAFF],
        testId: 'nav-shipping'
      }
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
        // 独家状态仅业务主管、渠道主管和管理员可见
        roles: [ROLE.BIZ_LEADER, ROLE.CHANNEL_LEADER, ROLE.ADMIN]
      }
    ]
  },
  {
    label: '系统管理',
    key: 'system',
    topKey: 'system',
    testId: 'nav-system',
    showInTop: true,
    roles: [ROLE.ADMIN], // 系统管理仅管理员可见
    children: [
      { label: '用户管理', key: '/system/users', topKey: 'system', path: '/system/users' },
      { label: '角色管理', key: '/system/roles', topKey: 'system', path: '/system/roles' },
      { label: '部门管理', key: '/system/depts', topKey: 'system', path: '/system/depts', roles: [ROLE.ADMIN, ROLE.CHANNEL_LEADER] },
      { label: '规则中心', key: '/system/rule-center', topKey: 'system', path: '/system/rule-center' },
      { label: '高级配置', key: '/system/config', topKey: 'system', path: '/system/config' },
      { label: '提成规则', key: '/system/commission-rules', topKey: 'system', path: '/system/commission-rules' },
      { label: '抖店联调', key: '/system/douyin', topKey: 'system', path: '/system/douyin' },
      { label: '操作日志', key: '/system/operation-logs', topKey: 'system', path: '/system/operation-logs' }
    ]
  }
]

/** 顶部菜单展示项类型，只保留渲染所需字段 */
export type TopMenuItem = Pick<MenuTreeNode, 'label' | 'key' | 'testId' | 'topKey'>

/**
 * 深拷贝菜单节点，去除元数据引用
 * 用于返回独立副本，避免修改原始 MENU_TREE 数据源
 *
 * @param node - 原始菜单节点
 * @returns 深拷贝后的菜单节点（保留业务字段，去除引用关系）
 */
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

/**
 * 按角色过滤菜单树（在生成阶段完成权限过滤）
 *
 * 过滤逻辑：
 * 1. 移除当前角色无权访问的节点
 * 2. 递归过滤子节点
 * 3. 移除过滤后子节点为空的分组节点（避免展示空分组）
 *
 * @param nodes - 待过滤的菜单树
 * @param roles - 当前用户的角色代码列表
 * @returns 过滤后的菜单树副本
 */
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
    // 移除过滤后无子节点的分组（避免展示空壳分组）
    .filter((node) => !Array.isArray(node.children) || node.children.length > 0)
}

/**
 * 构建当前角色可访问的菜单树
 *
 * @param roles - 当前用户的角色代码列表
 * @returns 按权限过滤后的完整菜单树
 */
export function buildAccessibleMenuTree(roles: readonly string[]): MenuTreeNode[] {
  return filterMenuTreeByRoles(MENU_TREE, roles)
}

/**
 * 获取顶部导航菜单列表
 *
 * @param roles - 当前用户的角色代码列表
 * @returns 可见的顶部菜单项数组（仅包含 showInTop 不为 false 的节点）
 */
export function getTopMenus(roles: readonly string[]): TopMenuItem[] {
  return buildAccessibleMenuTree(roles)
    .filter((node) => node.showInTop !== false)
    .map(({ label, key, testId, topKey }) => ({ label, key, testId, topKey }))
}

/**
 * 根据当前路由路径解析活跃的顶部业务域 key
 *
 * @param path - 当前路由路径
 * @returns 顶部业务域 key，未匹配返回 null
 */
export function resolveActiveTopKey(path: string): string | null {
  return resolveActiveSection(path)
}

/**
 * 在菜单树中查找指定 topKey 的一级节点
 *
 * @param tree - 菜单树
 * @param topKey - 要查找的顶部业务域 key
 * @returns 匹配的菜单节点，未找到返回 null
 */
export function findTopMenuNode(tree: readonly MenuTreeNode[], topKey: string | null | undefined): MenuTreeNode | null {
  if (!topKey) return null
  return tree.find((node) => node.key === topKey) || null
}

/**
 * 获取左侧子菜单列表
 * 左侧菜单 = 当前 activeTopKey 对应节点的 children（替换，非追加）
 *
 * @param tree - 已过滤的菜单树
 * @param topKey - 当前选中的顶部业务域 key
 * @returns 该业务域下的子菜单列表，未找到返回空数组
 */
export function getLeftMenus(tree: readonly MenuTreeNode[], topKey: string | null | undefined): MenuTreeNode[] {
  const topNode = findTopMenuNode(tree, topKey)
  return topNode?.children ? topNode.children.map(cloneWithoutMeta) : []
}

/**
 * 递归解析菜单节点下第一个角色有权访问的叶子路径
 *
 * 逻辑：
 * 1. 如果是分组节点（有 children），递归查找第一个可访问子节点
 * 2. 如果所有子节点均不可访问，回退到 MENU_GROUP_DEFAULT_ROUTE
 * 3. 如果是叶子节点，检查角色权限后返回 path 或 key
 *
 * @param node - 菜单节点
 * @param roles - 当前用户的角色代码列表
 * @returns 第一个可访问的路由路径，无权访问返回 null
 */
export function resolveFirstAccessiblePath(
  node: MenuTreeNode | null | undefined,
  roles: readonly string[]
): string | null {
  if (!node) return null
  const roleList = [...roles]

  // 分组节点：递归查找第一个可访问子节点
  if (node.children?.length) {
    for (const child of node.children) {
      const target = resolveFirstAccessiblePath(child, roleList)
      if (target) return target
    }
    // 所有子节点均不可访问，回退到分组默认路由
    return resolveMenuNavigateTarget(node.key)
  }

  // 叶子节点：检查角色权限
  if (!hasAccess(roleList, node.roles)) return null
  const path = typeof node.path === 'string' ? node.path : undefined
  if (path) return path
  return resolveMenuNavigateTarget(node.key)
}

/**
 * 解析点击顶部菜单时的默认跳转路径
 *
 * @param topKey - 被点击的顶部业务域 key
 * @param roles - 当前用户的角色代码列表
 * @returns 默认跳转路径，无法解析返回 null
 */
export function resolveTopMenuDefaultPath(topKey: string, roles: readonly string[]): string | null {
  const tree = buildAccessibleMenuTree(roles)
  const topNode = findTopMenuNode(tree, topKey)
  return resolveFirstAccessiblePath(topNode, roles)
}

/**
 * 根据当前路由路径和查询参数，解析对应的左侧菜单 key
 *
 * 特殊处理：
 * - 达人 CRM 根据 view query 参数区分四个子视图
 * - 商品管理/商品审核等子路由归并到对应父菜单
 * - 系统管理各子页面精确匹配
 *
 * @param path - 当前路由路径
 * @param viewQuery - 可选的 view 查询参数（用于达人 CRM 视图切换）
 * @returns 左侧菜单 key
 */
export function resolveActiveLeftKey(path: string, viewQuery?: string | null): string {
  // 系统管理子页面精确匹配（按路径长度倒序，确保更长的路径优先匹配）
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
  // 数据看板子页面
  if (path === '/data/orders') return '/data/orders'
  // 商品管理子页面（精确路径优先，前缀匹配兜底）
  if (path === '/product/manage/products') return '/product/manage/products'
  if (path === '/product/manage') return '/product/manage'
  if (isRoutePathUnderPrefix(path, '/product/manage')) return '/product/manage'
  if (isRoutePathUnderPrefix(path, '/product/activity')) return '/product/manage'
  if (isRoutePathUnderPrefix(path, '/product/review')) return '/product/manage/products'
  if (isRoutePathUnderPrefix(path, '/product/library')) return '/product'
  // 达人 CRM：根据 view query 参数解析具体子视图
  if (isRoutePathUnderPrefix(path, '/talent')) {
    const view = viewQuery || 'TEAM_PUBLIC'
    if (view === 'MY_TALENTS') return TALENT_MENU_KEYS.myTalents
    if (view === 'NATURAL_ORDERS') return TALENT_MENU_KEYS.naturalOrders
    if (view === 'BLACKLIST') return TALENT_MENU_KEYS.blacklist
    return TALENT_MENU_KEYS.teamPublic
  }
  return path
}

/**
 * 判断指定顶部菜单是否处于激活状态
 *
 * @param topKey - 要判断的顶部业务域 key
 * @param path - 当前路由路径
 * @returns 是否激活
 */
export function isTopMenuActive(topKey: string, path: string): boolean {
  const activeTopKey = resolveActiveTopKey(path)
  return activeTopKey === topKey
}
