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
  })

  it('returns only current top domain children for left menus', () => {
    const tree = buildAccessibleMenuTree([ROLE_CODES.BIZ_STAFF])
    const productMenus = getLeftMenus(tree, 'product')
    const dataMenus = getLeftMenus(tree, 'data')

    expect(productMenus.map((menu) => menu.key)).toEqual(['/product'])
    expect(dataMenus.map((menu) => menu.key)).toEqual(['/data', '/data/orders'])
  })

  it('resolves active top key from route path', () => {
    expect(resolveActiveTopKey('/product/library')).toBe('product')
    expect(resolveActiveTopKey('/data/orders')).toBe('data')
    expect(resolveActiveTopKey('/talent')).toBe('talent')
  })

  it('resolves active left key including talent query views', () => {
    expect(resolveActiveLeftKey('/talent', 'MY_TALENTS')).toBe('/talent?view=MY_TALENTS')
    expect(resolveActiveLeftKey('/data/orders')).toBe('/data/orders')
  })

  it('navigates to first accessible child when clicking top menu', () => {
    expect(resolveTopMenuDefaultPath('product-manage', [ROLE_CODES.BIZ_STAFF])).toBe('/product/manage/products')
    expect(resolveTopMenuDefaultPath('data', [ROLE_CODES.BIZ_STAFF])).toBe('/data')
  })

  it('marks top menu active from current route', () => {
    expect(isTopMenuActive('product', '/product/library')).toBe(true)
    expect(isTopMenuActive('data', '/product')).toBe(false)
  })
})
