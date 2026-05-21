import { describe, expect, it } from 'vitest'

import { ROLE_CODES } from '../constants/rbac'
import {
  MENU_GROUP_DEFAULT_ROUTE,
  filterAccessibleMenus,
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
})

describe('resolveActiveSection', () => {
  it('recognizes system dept routes', () => {
    expect(resolveActiveSection('/system/depts')).toBe('system')
  })
})

describe('MENU_GROUP_DEFAULT_ROUTE', () => {
  it('defines system group landing page', () => {
    expect(MENU_GROUP_DEFAULT_ROUTE['system-group']).toBe('/system/users')
  })
})
