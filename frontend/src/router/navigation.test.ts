/**
 * 导航路由工具模块单元测试
 *
 * 测试覆盖范围：
 * - resolveMenuNavigateTarget：分组 key 到默认路由的映射
 * - shouldShowMenuInSection：菜单项分区可见性判断
 * - filterAccessibleMenus：按角色和分区过滤菜单
 * - resolveActiveSection：路由路径到侧边栏分区的解析
 * - isRoutePathUnderPrefix：路径前缀匹配（含边界情况）
 * - MENU_GROUP_DEFAULT_ROUTE：分组默认路由常量验证
 */
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
  // 验证：侧边栏分组 key 能正确映射到默认落地路由
  it('maps sidebar group keys to default routes', () => {
    expect(resolveMenuNavigateTarget('system-group')).toBe('/system/users')
    expect(resolveMenuNavigateTarget('data-group')).toBe('/data')
  })

  // 验证：以 / 开头的绝对路径直接透传
  it('passes through absolute paths', () => {
    expect(resolveMenuNavigateTarget('/system/depts')).toBe('/system/depts')
  })

  // 验证：未知的分组 key 返回 null
  it('returns null for unknown keys', () => {
    expect(resolveMenuNavigateTarget('unknown-group')).toBeNull()
  })
})

describe('shouldShowMenuInSection', () => {
  // 验证：缺少活跃分区或菜单无分区标识时不展示
  it('hides menus when active section is missing', () => {
    // activeSection 为 null 时隐藏
    expect(
      shouldShowMenuInSection(
        { label: '数据平台', key: 'data-group', _section: 'data' },
        null,
        [ROLE_CODES.BIZ_STAFF]
      )
    ).toBe(false)
    // 菜单无 _section 字段时隐藏
    expect(
      shouldShowMenuInSection(
        { label: '普通菜单', key: '/orders' },
        'data',
        [ROLE_CODES.BIZ_STAFF]
      )
    ).toBe(false)
  })

  // 验证：只有 _section 匹配当前分区的菜单才展示
  it('shows only menus in the active section', () => {
    expect(
      shouldShowMenuInSection(
        { label: '数据平台', key: 'data-group', _section: 'data' },
        'data',
        [ROLE_CODES.BIZ_STAFF]
      )
    ).toBe(true)
    // 分区不匹配时不展示
    expect(
      shouldShowMenuInSection(
        { label: '系统管理', key: 'system-group', _section: 'system' },
        'data',
        [ROLE_CODES.ADMIN]
      )
    ).toBe(false)
  })
})

describe('filterAccessibleMenus', () => {
  // 验证：管理员在数据分区下只看到数据分区的菜单，系统管理菜单被分区过滤
  it('includes only current section menus for admin on data section', () => {
    const menus = filterAccessibleMenus(
      [
        { label: '数据平台', key: 'data-group', _section: 'data', roles: [ROLE_CODES.ADMIN] },
        { label: '系统管理', key: 'system-group', _section: 'system', roles: [ROLE_CODES.ADMIN] }
      ],
      [ROLE_CODES.ADMIN],
      'data'
    )
    expect(menus.map((menu) => menu.key)).toEqual(['data-group'])
  })

  // 验证：递归过滤子菜单时，保留有权限的父节点并过滤无权限的子节点
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
  // 验证：系统部门路由正确识别为 system 分区
  it('recognizes system dept routes', () => {
    expect(resolveActiveSection('/system/depts')).toBe('system')
  })

  // 验证：能处理缺少前导斜杠、有尾部斜杠、带查询参数和 hash 的路径
  it('normalizes query strings, missing slash and trailing slash', () => {
    expect(resolveActiveSection('system/depts/?tab=members#top')).toBe('system')
  })

  // 验证：未匹配的路径返回 null
  it('returns null for unmatched paths', () => {
    expect(resolveActiveSection('/unknown')).toBeNull()
  })
})

describe('isRoutePathUnderPrefix', () => {
  // 验证：能正确处理空白路径、仅查询参数路径和尾部斜杠
  it('normalizes blank paths, query-only paths and trailing slashes', () => {
    expect(isRoutePathUnderPrefix('', '/')).toBe(true)
    expect(isRoutePathUnderPrefix('?tab=1', '/')).toBe(true)
    expect(isRoutePathUnderPrefix('product/library/', '/product')).toBe(true)
  })
})

describe('MENU_GROUP_DEFAULT_ROUTE', () => {
  // 验证：系统管理分组的落地页为用户管理
  it('defines system group landing page', () => {
    expect(MENU_GROUP_DEFAULT_ROUTE['system-group']).toBe('/system/users')
  })
})
