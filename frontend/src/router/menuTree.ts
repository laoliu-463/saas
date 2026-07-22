/**
 * 菜单树数据源与查询工具
 *
 * 职责：
 * - 维护全局菜单树 MENU_TREE（两级结构：顶部业务域 -> 左侧子功能）
 * - 提供按权限过滤菜单树的工具函数
 * - 解析当前路由路径对应的活跃顶部 key / 左侧 key
 * - 计算点击顶部菜单时的默认跳转路径
 *
 * 菜单树结构约定：
 * - 一级节点（topKey === key）为顶部导航业务域
 * - 二级节点（children）为左侧边栏子菜单
 * - 路由路径统一在 navigation.ts 的 SECTION_MAP 中声明归属
 */
import { hasPermission, PERMISSION_CODES } from '../constants/permissions'
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
  /** 本组达人视图 */
  teamPrivate: '/talent?view=TEAM_PRIVATE',
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
  /** 允许访问该菜单的权限代码列表，为空表示不限制 */
  permissions?: string[]
  /** E2E 测试用的 data-testid 标识 */
  testId?: string
  /** 是否在顶部导航栏展示，为 false 时只在特定条件下可见 */
  showInTop?: boolean
  /** 子菜单列表 */
  children?: MenuTreeNode[]
}

const PERMISSION = PERMISSION_CODES

/**
 * 统一菜单数据源
 *
 * 结构说明：
 * - 一级节点：顶部业务域（归因工作台、商品库、商品管理、达人CRM、合作管理、数据看板、系统管理）
 * - 二级节点：左侧边栏子功能菜单
 * - permissions 字段控制可见性，未设置则不限制
 */
export const MENU_TREE: MenuTreeNode[] = [
  {
    label: '归因工作台',
    key: 'attribution',
    topKey: 'attribution',
    showInTop: false, // 不在顶部导航展示，通过其他入口访问
    permissions: [PERMISSION.DASHBOARD_ACCESS, PERMISSION.ORDER_ACCESS],
    children: [
      { label: '归因概览', key: '/dashboard', topKey: 'attribution', path: '/dashboard', permissions: [PERMISSION.DASHBOARD_ACCESS] },
      { label: '订单工作台', key: '/orders', topKey: 'attribution', path: '/orders', permissions: [PERMISSION.ORDER_ACCESS] }
    ]
  },
  {
    label: '商品库',
    key: 'product',
    topKey: 'product',
    testId: 'nav-product',
    showInTop: true,
    permissions: [PERMISSION.PRODUCT_ACCESS],
    children: [{ label: '商品库', key: '/product', topKey: 'product', path: '/product', permissions: [PERMISSION.PRODUCT_ACCESS] }]
  },
  {
    label: '商品管理',
    key: 'product-manage',
    topKey: 'product-manage',
    testId: 'nav-activity-product',
    showInTop: true,
    permissions: [PERMISSION.PRODUCT_MANAGE_ACCESS],
    children: [
      {
        label: '商品列表',
        key: '/product/manage/products',
        topKey: 'product-manage',
        path: '/product/manage/products',
        permissions: [PERMISSION.PRODUCT_MANAGE_ACCESS]
      },
      { label: '活动列表', key: '/product/manage', topKey: 'product-manage', path: '/product/manage', permissions: [PERMISSION.PRODUCT_MANAGE_ACCESS] }
    ]
  },
  {
    label: '达人 CRM',
    key: 'talent',
    topKey: 'talent',
    testId: 'nav-talent',
    showInTop: true,
    permissions: [PERMISSION.TALENT_ACCESS],
    children: [
      { label: '团队公海', key: TALENT_MENU_KEYS.teamPublic, topKey: 'talent', path: TALENT_MENU_KEYS.teamPublic },
      { label: '我的达人', key: TALENT_MENU_KEYS.myTalents, topKey: 'talent', path: TALENT_MENU_KEYS.myTalents },
      { label: '本组达人', key: TALENT_MENU_KEYS.teamPrivate, topKey: 'talent', path: TALENT_MENU_KEYS.teamPrivate },
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
    permissions: [PERMISSION.SAMPLE_ACCESS, PERMISSION.SHIPPING_ACCESS],
    children: [
      {
        label: '合作单',
        key: '/sample',
        topKey: 'sample',
        path: '/sample',
        permissions: [PERMISSION.SAMPLE_ACCESS]
      },
      {
        label: '发货台',
        key: '/ops/shipping',
        topKey: 'sample',
        path: '/ops/shipping',
        permissions: [PERMISSION.SHIPPING_ACCESS],
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
    permissions: [PERMISSION.DATA_ACCESS],
    children: [
      { label: '核心看板', key: '/data', topKey: 'data', path: '/data', permissions: [PERMISSION.DATA_ACCESS] },
      { label: '订单明细', key: '/data/orders', topKey: 'data', path: '/data/orders', permissions: [PERMISSION.DATA_ACCESS] },
      {
        label: '独家状态',
        key: '/ops/exclusive',
        topKey: 'data',
        path: '/ops/exclusive',
        permissions: [PERMISSION.EXCLUSIVE_ACCESS]
      }
    ]
  },
  {
    label: '系统管理',
    key: 'system',
    topKey: 'system',
    testId: 'nav-system',
    showInTop: true,
    permissions: [
      PERMISSION.SYS_USER_ACCESS,
      PERMISSION.SYS_ROLE_ACCESS,
      PERMISSION.SYS_DEPT_ACCESS,
      PERMISSION.RULE_CENTER_ACCESS,
      PERMISSION.SYS_CONFIG_ACCESS,
      PERMISSION.COMMISSION_RULE_ACCESS,
      PERMISSION.DOUYIN_ACCESS,
      PERMISSION.OPERATION_LOG_ACCESS
    ],
    children: [
      { label: '用户管理', key: '/system/users', topKey: 'system', path: '/system/users', permissions: [PERMISSION.SYS_USER_ACCESS] },
      { label: '角色管理', key: '/system/roles', topKey: 'system', path: '/system/roles', permissions: [PERMISSION.SYS_ROLE_ACCESS] },
      { label: '部门管理', key: '/system/depts', topKey: 'system', path: '/system/depts', permissions: [PERMISSION.SYS_DEPT_ACCESS] },
      { label: '规则中心', key: '/system/rule-center', topKey: 'system', path: '/system/rule-center', permissions: [PERMISSION.RULE_CENTER_ACCESS] },
      { label: '高级配置', key: '/system/config', topKey: 'system', path: '/system/config', permissions: [PERMISSION.SYS_CONFIG_ACCESS] },
      { label: '提成规则', key: '/system/commission-rules', topKey: 'system', path: '/system/commission-rules', permissions: [PERMISSION.COMMISSION_RULE_ACCESS] },
      { label: '抖店联调', key: '/system/douyin', topKey: 'system', path: '/system/douyin', permissions: [PERMISSION.DOUYIN_ACCESS] },
      { label: '操作日志', key: '/system/operation-logs', topKey: 'system', path: '/system/operation-logs', permissions: [PERMISSION.OPERATION_LOG_ACCESS] }
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
    permissions: node.permissions,
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
export function filterMenuTreeByPermissions(
  nodes: readonly MenuTreeNode[],
  permissionCodes: readonly string[]
): MenuTreeNode[] {
  const permissions = [...permissionCodes]
  return nodes
    .filter((node) => hasPermission(permissions, node.permissions))
    .map((node) => {
      if (!node.children?.length) return cloneWithoutMeta(node)
      const children = filterMenuTreeByPermissions(node.children, permissions)
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
export function buildAccessibleMenuTree(permissionCodes: readonly string[]): MenuTreeNode[] {
  return filterMenuTreeByPermissions(MENU_TREE, permissionCodes)
}

/**
 * 获取顶部导航菜单列表
 *
 * @param roles - 当前用户的角色代码列表
 * @returns 可见的顶部菜单项数组（仅包含 showInTop 不为 false 的节点）
 */
export function getTopMenus(permissionCodes: readonly string[]): TopMenuItem[] {
  return buildAccessibleMenuTree(permissionCodes)
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
 * 解析侧边栏所属的业务分区。
 *
 * 个人中心等全局页面不属于任何业务分区；此时优先保留用户进入全局页面前
 * 所在且仍可访问的分区，避免把可用的侧边导航误显示为“没有可见菜单”。
 * 若没有历史分区（例如直接打开个人中心），则回退到当前角色的第一个可访问分区。
 */
export function resolveSidebarTopKey(
  path: string,
  permissionCodes: readonly string[],
  previousTopKey?: string | null
): string | null {
  const activeTopKey = resolveActiveTopKey(path)
  if (activeTopKey) return activeTopKey

  const accessibleTree = buildAccessibleMenuTree(permissionCodes)
  if (previousTopKey && findTopMenuNode(accessibleTree, previousTopKey)) {
    return previousTopKey
  }

  return getTopMenus(permissionCodes)[0]?.key || null
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
  permissionCodes: readonly string[]
): string | null {
  if (!node) return null
  const permissions = [...permissionCodes]

  // 分组节点：递归查找第一个可访问子节点
  if (node.children?.length) {
    for (const child of node.children) {
      const target = resolveFirstAccessiblePath(child, permissions)
      if (target) return target
    }
    // 所有子节点均不可访问，回退到分组默认路由
    return resolveMenuNavigateTarget(node.key)
  }

  // 叶子节点：检查角色权限
  if (!hasPermission(permissions, node.permissions)) return null
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
export function resolveTopMenuDefaultPath(topKey: string, permissionCodes: readonly string[]): string | null {
  const tree = buildAccessibleMenuTree(permissionCodes)
  const topNode = findTopMenuNode(tree, topKey)
  return resolveFirstAccessiblePath(topNode, permissionCodes)
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
