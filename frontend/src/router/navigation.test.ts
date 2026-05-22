import { describe, expect, it } from 'vitest'

import { ROLE_CODES } from '../constants/rbac'
import {
  MENU_GROUP_DEFAULT_ROUTE,
  filterAccessibleMenus,
  isRoutePathUnderPrefix,
  resolveActiveSection,
  resolveMenuNavigateTarget,
  shouldShowMenuInSection
} from './navigation'

describe('resolveMenuNavigateTarget', () => {
  it('maps sidebar group keys to default routes', () => {
    expect(resolveMenuNavigateTarget('system-group')).toBe('/system/users')
    expect(resolveMenuNavigateTarget('data-group')).toBe('/data')
  })

  it('passes through absolute paths', () => {
    expect(resolveMenuNavigateTarget('/system/depts')).toBe('/system/depts')
  })

  it('returns null for unknown keys', () => {
    expect(resolveMenuNavigateTarget('unknown-group')).toBeNull()
  })
})

describe('shouldShowMenuInSection', () => {
  it('shows menus when active section or menu section is missing', () => {
    expect(
      shouldShowMenuInSection(
        { label: '数据平台', key: 'data-group', _section: 'data' },
        null,
        [ROLE_CODES.BIZ_STAFF]
      )
    ).toBe(true)
    expect(
      shouldShowMenuInSection(
        { label: '普通菜单', key: '/orders' },
        'data',
        [ROLE_CODES.BIZ_STAFF]
      )
    ).toBe(true)
  })

  it('keeps system menu visible for admin on non-system pages', () => {
    expect(
      shouldShowMenuInSection(
        { label: '系统管理', key: 'system-group', _section: 'system' },
        'data',
        [ROLE_CODES.ADMIN]
      )
    ).toBe(true)
  })

  it('hides system menu for non-admin on other sections', () => {
    expect(
      shouldShowMenuInSection(
        { label: '系统管理', key: 'system-group', _section: 'system' },
        'data',
        [ROLE_CODES.BIZ_STAFF]
      )
    ).toBe(false)
  })
})

describe('filterAccessibleMenus', () => {
  it('includes admin system menu while viewing data section', () => {
    const menus = filterAccessibleMenus(
      [
        { label: '数据平台', key: 'data-group', _section: 'data', roles: [ROLE_CODES.ADMIN] },
        { label: '系统管理', key: 'system-group', _section: 'system', roles: [ROLE_CODES.ADMIN] }
      ],
      [ROLE_CODES.ADMIN],
      'data'
    )
    expect(menus.map((menu) => menu.key)).toEqual(['data-group', 'system-group'])
  })

  it('filters nested children by role while preserving accessible parents', () => {
    const menus = filterAccessibleMenus(
      [
        {
          label: '商品管理',
          key: 'product-manage-group',
          _section: 'product-manage',
          roles: [ROLE_CODES.BIZ_STAFF],
          children: [
            { label: '用户管理', key: '/system/users', roles: [ROLE_CODES.ADMIN] },
            { label: '商品列表', key: '/product/manage/products', roles: [ROLE_CODES.BIZ_STAFF] }
          ]
        }
      ],
      [ROLE_CODES.BIZ_STAFF],
      'product-manage'
    )

    expect(menus).toHaveLength(1)
    expect(menus[0].children?.map((menu) => menu.key)).toEqual(['/product/manage/products'])
  })
})

describe('resolveActiveSection', () => {
  it('recognizes system dept routes', () => {
    expect(resolveActiveSection('/system/depts')).toBe('system')
  })

  it('normalizes query strings, missing slash and trailing slash', () => {
    expect(resolveActiveSection('system/depts/?tab=members#top')).toBe('system')
  })

  it('returns null for unmatched paths', () => {
    expect(resolveActiveSection('/unknown')).toBeNull()
  })
})

describe('isRoutePathUnderPrefix', () => {
  it('normalizes blank paths, query-only paths and trailing slashes', () => {
    expect(isRoutePathUnderPrefix('', '/')).toBe(true)
    expect(isRoutePathUnderPrefix('?tab=1', '/')).toBe(true)
    expect(isRoutePathUnderPrefix('product/library/', '/product')).toBe(true)
  })
})

describe('MENU_GROUP_DEFAULT_ROUTE', () => {
  it('defines system group landing page', () => {
    expect(MENU_GROUP_DEFAULT_ROUTE['system-group']).toBe('/system/users')
  })
})
