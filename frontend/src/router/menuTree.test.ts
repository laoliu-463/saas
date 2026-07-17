/**
 * 菜单树模块单元测试
 *
 * 测试覆盖范围：
 * - 顶部菜单生成：按角色过滤后的可见顶层业务域
 * - 左侧菜单生成：当前选中业务域的子功能菜单
 * - 角色权限过滤：不同角色只能看到授权菜单
 * - 路径解析：从当前路由路径解析活跃的顶部/左侧菜单 key
 * - 默认路径解析：点击顶部菜单时跳转到第一个可访问子菜单
 */
import { describe, expect, it } from 'vitest'

import { ROLE_CODES } from '../constants/rbac'
import {
  buildAccessibleMenuTree,
  filterMenuTreeByRoles,
  getLeftMenus,
  getTopMenus,
  isTopMenuActive,
  resolveActiveLeftKey,
  resolveFirstAccessiblePath,
  resolveActiveTopKey,
  resolveTopMenuDefaultPath
} from './menuTree'

describe('menuTree', () => {
  // 验证：按角色过滤后，顶部菜单只包含该角色有权访问且 showInTop 不为 false 的业务域
  it('builds top menus from accessible tree only', () => {
    const topMenus = getTopMenus([ROLE_CODES.BIZ_STAFF])
    expect(topMenus.map((menu) => menu.key)).toEqual(['product', 'product-manage', 'talent', 'sample', 'data'])
    expect(topMenus.find((menu) => menu.key === 'sample')?.label).toBe('合作管理')
  })

  // 验证：左侧菜单只返回当前选中 topKey 对应节点的 children，未匹配则返回空数组
  it('returns only current top domain children for left menus', () => {
    const tree = buildAccessibleMenuTree([ROLE_CODES.BIZ_STAFF])
    const productMenus = getLeftMenus(tree, 'product')
    const dataMenus = getLeftMenus(tree, 'data')
    const cooperationMenus = getLeftMenus(tree, 'sample')

    expect(productMenus.map((menu) => menu.key)).toEqual(['/product'])
    expect(dataMenus.map((menu) => menu.key)).toEqual(['/data', '/data/orders'])
    expect(cooperationMenus.map((menu) => menu.label)).toEqual(['合作单'])
    expect(getLeftMenus(tree, 'talent').map((menu) => menu.label)).toEqual([
      '团队公海', '我的达人', '本组达人', '自然出单达人', '达人黑名单'
    ])
    // topKey 为 null 或不存在时返回空数组
    expect(getLeftMenus(tree, null)).toEqual([])
    expect(getLeftMenus(tree, 'missing')).toEqual([])
  })

  // 验证：运维人员只能看到合作管理下的「发货台」子菜单
  it('merges ops shipping into cooperation management top menu', () => {
    const topMenus = getTopMenus([ROLE_CODES.OPS_STAFF])
    const tree = buildAccessibleMenuTree([ROLE_CODES.OPS_STAFF])

    expect(topMenus.map((menu) => ({ key: menu.key, label: menu.label }))).toEqual([
      { key: 'sample', label: '合作管理' }
    ])
    expect(getLeftMenus(tree, 'sample').map((menu) => ({ key: menu.key, label: menu.label }))).toEqual([
      { key: '/ops/shipping', label: '发货台' }
    ])
  })

  // 验证：管理员可以看到合作管理下的全部子菜单（合作单 + 发货台）
  it('shows both cooperation children for admin under sample', () => {
    const tree = buildAccessibleMenuTree([ROLE_CODES.ADMIN])
    expect(getLeftMenus(tree, 'sample').map((menu) => menu.key)).toEqual(['/sample', '/ops/shipping'])
  })

  // 验证：根据当前路由路径正确解析所属的顶部业务域 key
  it('resolves active top key from route path', () => {
    expect(resolveActiveTopKey('/product/library')).toBe('product')
    expect(resolveActiveTopKey('/data/orders')).toBe('data')
    expect(resolveActiveTopKey('/talent')).toBe('talent')
    expect(resolveActiveTopKey('/sample')).toBe('sample')
    // /ops/shipping 隶属于「合作管理」而非独立域
    expect(resolveActiveTopKey('/ops/shipping')).toBe('sample')
  })

  // 验证：左侧菜单 key 解析，包括达人 CRM 的 query 参数视图路由
  it('resolves active left key including talent query views', () => {
    expect(resolveActiveLeftKey('/talent', 'MY_TALENTS')).toBe('/talent?view=MY_TALENTS')
    expect(resolveActiveLeftKey('/talent', 'NATURAL_ORDERS')).toBe('/talent?view=NATURAL_ORDERS')
    expect(resolveActiveLeftKey('/talent', 'BLACKLIST')).toBe('/talent?view=BLACKLIST')
    // 默认无 view 参数时映射为团队公海
    expect(resolveActiveLeftKey('/talent', null)).toBe('/talent?view=TEAM_PUBLIC')
    expect(resolveActiveLeftKey('/data/orders')).toBe('/data/orders')
  })

  // 验证：所有已注册的路由别名都能正确映射到对应的左侧菜单 key
  it('resolves active left key for all routed menu aliases', () => {
    // 运维发货台（含子路径）
    expect(resolveActiveLeftKey('/ops/shipping/today')).toBe('/ops/shipping')
    // 独家状态
    expect(resolveActiveLeftKey('/ops/exclusive')).toBe('/ops/exclusive')
    // 系统管理各子页面
    expect(resolveActiveLeftKey('/system/depts')).toBe('/system/depts')
    expect(resolveActiveLeftKey('/system/rule-center')).toBe('/system/rule-center')
    expect(resolveActiveLeftKey('/system/config')).toBe('/system/config')
    expect(resolveActiveLeftKey('/system/douyin')).toBe('/system/douyin')
    expect(resolveActiveLeftKey('/system/operation-logs')).toBe('/system/operation-logs')
    expect(resolveActiveLeftKey('/system/commission-rules')).toBe('/system/commission-rules')
    expect(resolveActiveLeftKey('/system/roles')).toBe('/system/roles')
    expect(resolveActiveLeftKey('/system/users')).toBe('/system/users')
    // 商品管理子页面
    expect(resolveActiveLeftKey('/product/manage/products')).toBe('/product/manage/products')
    expect(resolveActiveLeftKey('/product/manage')).toBe('/product/manage')
    expect(resolveActiveLeftKey('/product/manage/import')).toBe('/product/manage')
    expect(resolveActiveLeftKey('/product/activity/ACT-1')).toBe('/product/manage')
    expect(resolveActiveLeftKey('/product/review')).toBe('/product/manage/products')
    // 商品库
    expect(resolveActiveLeftKey('/product/library')).toBe('/product')
    // 未注册路径原样返回
    expect(resolveActiveLeftKey('/unknown')).toBe('/unknown')
  })

  // 验证：点击顶部菜单时，自动导航到该域下第一个角色有权访问的子菜单路径
  it('navigates to first accessible child when clicking top menu', () => {
    expect(resolveTopMenuDefaultPath('product-manage', [ROLE_CODES.BIZ_STAFF])).toBe('/product/manage/products')
    expect(resolveTopMenuDefaultPath('data', [ROLE_CODES.BIZ_STAFF])).toBe('/data')
    expect(resolveTopMenuDefaultPath('sample', [ROLE_CODES.OPS_STAFF])).toBe('/ops/shipping')
    // 不存在的 topKey 返回 null
    expect(resolveTopMenuDefaultPath('missing', [ROLE_CODES.ADMIN])).toBeNull()
  })

  // 验证：各种边界情况下的首次可访问路径解析（null 节点、无权限、无 path、空分组）
  it('resolves first accessible path for custom leaf and fallback group nodes', () => {
    // null 节点
    expect(resolveFirstAccessiblePath(null, [ROLE_CODES.ADMIN])).toBeNull()
    // 角色无权访问
    expect(resolveFirstAccessiblePath({
      label: '不可访问',
      key: '/secret',
      topKey: 'custom',
      roles: [ROLE_CODES.ADMIN]
    }, [ROLE_CODES.BIZ_STAFF])).toBeNull()
    // 无 path 的叶子节点，回退到 key
    expect(resolveFirstAccessiblePath({
      label: '无 path 叶子',
      key: '/custom/fallback',
      topKey: 'custom'
    }, [])).toBe('/custom/fallback')
    // 空分组（所有子节点均无权限），回退到 MENU_GROUP_DEFAULT_ROUTE
    expect(resolveFirstAccessiblePath({
      label: '空分组',
      key: 'data-group',
      topKey: 'custom',
      children: [
        { label: '不可访问子项', key: '/secret', topKey: 'custom', roles: [ROLE_CODES.ADMIN] }
      ]
    }, [ROLE_CODES.BIZ_STAFF])).toBe('/data')
  })

  // 验证：权限过滤会移除无子节点可访问的分组，保留无 children 的公开叶子节点
  it('filters custom menu nodes without children and drops empty denied groups', () => {
    const tree = filterMenuTreeByRoles([
      { label: '公开叶子', key: '/open', topKey: 'custom' },
      {
        label: '空分组',
        key: 'empty-group',
        topKey: 'custom',
        children: [{ label: '管理员子项', key: '/admin-only', topKey: 'custom', roles: [ROLE_CODES.ADMIN] }]
      }
    ], [ROLE_CODES.BIZ_STAFF])

    expect(tree).toHaveLength(1)
    expect(tree[0]).toMatchObject({ label: '公开叶子', key: '/open', topKey: 'custom' })
    expect(tree[0].children).toBeUndefined()
  })

  // 验证：根据当前路由路径判断某个顶部菜单是否处于激活状态
  it('marks top menu active from current route', () => {
    expect(isTopMenuActive('product', '/product/library')).toBe(true)
    expect(isTopMenuActive('data', '/product')).toBe(false)
    expect(isTopMenuActive('sample', '/sample')).toBe(true)
    // /ops/shipping 也属于合作管理域
    expect(isTopMenuActive('sample', '/ops/shipping')).toBe(true)
  })
})
