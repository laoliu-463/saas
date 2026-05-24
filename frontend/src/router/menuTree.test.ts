import { describe, expect, it } from 'vitest'

import { ROLE_CODES } from '../constants/rbac'
import {
  buildAccessibleMenuTree,
  getLeftMenus,
  getTopMenus,
  isTopMenuActive,
  resolveActiveLeftKey,
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
    expect(resolveActiveLeftKey('/data/orders')).toBe('/data/orders')
  })

  it('navigates to first accessible child when clicking top menu', () => {
    expect(resolveTopMenuDefaultPath('product-manage', [ROLE_CODES.BIZ_STAFF])).toBe('/product/manage/products')
    expect(resolveTopMenuDefaultPath('data', [ROLE_CODES.BIZ_STAFF])).toBe('/data')
    expect(resolveTopMenuDefaultPath('sample', [ROLE_CODES.OPS_STAFF])).toBe('/ops/shipping')
  })

  it('marks top menu active from current route', () => {
    expect(isTopMenuActive('product', '/product/library')).toBe(true)
    expect(isTopMenuActive('data', '/product')).toBe(false)
    expect(isTopMenuActive('sample', '/sample')).toBe(true)
    expect(isTopMenuActive('sample', '/ops/shipping')).toBe(true)
  })
})
