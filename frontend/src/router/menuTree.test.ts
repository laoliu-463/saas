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
  it('builds top menus from accessible tree only', () => {
    const topMenus = getTopMenus([ROLE_CODES.BIZ_STAFF])
    expect(topMenus.map((menu) => menu.key)).toEqual(['data', 'product', 'product-manage', 'sample'])
    expect(topMenus.find((menu) => menu.key === 'sample')?.label).toBe('合作管理')
  })

  it('returns only current top domain children for left menus', () => {
    const tree = buildAccessibleMenuTree([ROLE_CODES.BIZ_STAFF])
    const productMenus = getLeftMenus(tree, 'product')
    const dataMenus = getLeftMenus(tree, 'data')
    const cooperationMenus = getLeftMenus(tree, 'sample')

    expect(productMenus.map((menu) => menu.key)).toEqual(['/product'])
    expect(dataMenus.map((menu) => menu.key)).toEqual(['/data', '/data/orders'])
    expect(cooperationMenus.map((menu) => menu.label)).toEqual(['合作单'])
    expect(getLeftMenus(tree, null)).toEqual([])
    expect(getLeftMenus(tree, 'missing')).toEqual([])
  })

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

  it('shows both cooperation children for admin under sample', () => {
    const tree = buildAccessibleMenuTree([ROLE_CODES.ADMIN])
    expect(getLeftMenus(tree, 'sample').map((menu) => menu.key)).toEqual(['/sample', '/ops/shipping'])
  })

  it('resolves active top key from route path', () => {
    expect(resolveActiveTopKey('/product/library')).toBe('product')
    expect(resolveActiveTopKey('/data/orders')).toBe('data')
    expect(resolveActiveTopKey('/talent')).toBe('talent')
    expect(resolveActiveTopKey('/sample')).toBe('sample')
    expect(resolveActiveTopKey('/ops/shipping')).toBe('sample')
  })

  it('resolves active left key including talent query views', () => {
    expect(resolveActiveLeftKey('/talent', 'MY_TALENTS')).toBe('/talent?view=MY_TALENTS')
    expect(resolveActiveLeftKey('/talent', 'NATURAL_ORDERS')).toBe('/talent?view=NATURAL_ORDERS')
    expect(resolveActiveLeftKey('/talent', 'BLACKLIST')).toBe('/talent?view=BLACKLIST')
    expect(resolveActiveLeftKey('/talent', null)).toBe('/talent?view=TEAM_PUBLIC')
    expect(resolveActiveLeftKey('/data/orders')).toBe('/data/orders')
  })

  it('resolves active left key for all routed menu aliases', () => {
    expect(resolveActiveLeftKey('/ops/shipping/today')).toBe('/ops/shipping')
    expect(resolveActiveLeftKey('/ops/exclusive')).toBe('/ops/exclusive')
    expect(resolveActiveLeftKey('/system/depts')).toBe('/system/depts')
    expect(resolveActiveLeftKey('/system/rule-center')).toBe('/system/rule-center')
    expect(resolveActiveLeftKey('/system/config')).toBe('/system/config')
    expect(resolveActiveLeftKey('/system/douyin')).toBe('/system/douyin')
    expect(resolveActiveLeftKey('/system/operation-logs')).toBe('/system/operation-logs')
    expect(resolveActiveLeftKey('/system/commission-rules')).toBe('/system/commission-rules')
    expect(resolveActiveLeftKey('/system/roles')).toBe('/system/roles')
    expect(resolveActiveLeftKey('/system/users')).toBe('/system/users')
    expect(resolveActiveLeftKey('/product/manage/products')).toBe('/product/manage/products')
    expect(resolveActiveLeftKey('/product/manage')).toBe('/product/manage')
    expect(resolveActiveLeftKey('/product/manage/import')).toBe('/product/manage')
    expect(resolveActiveLeftKey('/product/activity/ACT-1')).toBe('/product/manage')
    expect(resolveActiveLeftKey('/product/review')).toBe('/product/manage/products')
    expect(resolveActiveLeftKey('/product/library')).toBe('/product')
    expect(resolveActiveLeftKey('/unknown')).toBe('/unknown')
  })

  it('navigates to first accessible child when clicking top menu', () => {
    expect(resolveTopMenuDefaultPath('product-manage', [ROLE_CODES.BIZ_STAFF])).toBe('/product/manage/products')
    expect(resolveTopMenuDefaultPath('data', [ROLE_CODES.BIZ_STAFF])).toBe('/data')
    expect(resolveTopMenuDefaultPath('sample', [ROLE_CODES.OPS_STAFF])).toBe('/ops/shipping')
    expect(resolveTopMenuDefaultPath('missing', [ROLE_CODES.ADMIN])).toBeNull()
  })

  it('resolves first accessible path for custom leaf and fallback group nodes', () => {
    expect(resolveFirstAccessiblePath(null, [ROLE_CODES.ADMIN])).toBeNull()
    expect(resolveFirstAccessiblePath({
      label: '不可访问',
      key: '/secret',
      topKey: 'custom',
      roles: [ROLE_CODES.ADMIN]
    }, [ROLE_CODES.BIZ_STAFF])).toBeNull()
    expect(resolveFirstAccessiblePath({
      label: '无 path 叶子',
      key: '/custom/fallback',
      topKey: 'custom'
    }, [])).toBe('/custom/fallback')
    expect(resolveFirstAccessiblePath({
      label: '空分组',
      key: 'data-group',
      topKey: 'custom',
      children: [
        { label: '不可访问子项', key: '/secret', topKey: 'custom', roles: [ROLE_CODES.ADMIN] }
      ]
    }, [ROLE_CODES.BIZ_STAFF])).toBe('/data')
  })

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

  it('marks top menu active from current route', () => {
    expect(isTopMenuActive('product', '/product/library')).toBe(true)
    expect(isTopMenuActive('data', '/product')).toBe(false)
    expect(isTopMenuActive('sample', '/sample')).toBe(true)
    expect(isTopMenuActive('sample', '/ops/shipping')).toBe(true)
  })
})
