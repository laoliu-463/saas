import { test, expect } from '@playwright/test'

import viteConfig from '../../frontend/vite.config'
import { ROLE_CODES } from '../../frontend/src/constants/rbac'
import {
  filterAccessibleMenus,
  resolveActiveSection,
  isRoutePathUnderPrefix
} from '../../frontend/src/router/navigation'
import { createGuardWarningDeduper, resolveGuardDecision } from '../../frontend/src/router/guard'

test('Vite dev server only enables polling when explicitly requested', () => {
  const originalForcePolling = process.env.VITE_FORCE_POLLING
  const originalChokidarPolling = process.env.CHOKIDAR_USEPOLLING
  const originalChokidarInterval = process.env.CHOKIDAR_INTERVAL

  try {
    delete process.env.VITE_FORCE_POLLING
    delete process.env.CHOKIDAR_USEPOLLING
    delete process.env.CHOKIDAR_INTERVAL

    const defaultConfig = (viteConfig as any)({ command: 'serve', mode: 'development' })
    expect(defaultConfig.server.strictPort).toBe(false)
    expect(defaultConfig.server.watch).toBeUndefined()

    process.env.VITE_FORCE_POLLING = 'true'
    process.env.CHOKIDAR_INTERVAL = '750'

    const pollingConfig = (viteConfig as any)({ command: 'serve', mode: 'development' })
    expect(pollingConfig.server.watch.usePolling).toBe(true)
    expect(pollingConfig.server.watch.interval).toBe(750)
  } finally {
    restoreEnv('VITE_FORCE_POLLING', originalForcePolling)
    restoreEnv('CHOKIDAR_USEPOLLING', originalChokidarPolling)
    restoreEnv('CHOKIDAR_INTERVAL', originalChokidarInterval)
  }
})

test('route section matching prefers exact child paths and avoids false prefixes', () => {
  expect(resolveActiveSection('/product/library')).toBe('product')
  expect(resolveActiveSection('/product/library/sku-1')).toBe('product')
  expect(resolveActiveSection('/product/activity')).toBe('product-manage')
  expect(resolveActiveSection('/product/activity/3916506')).toBe('product-manage')
  expect(resolveActiveSection('/product/review')).toBe('product-manage')
  expect(resolveActiveSection('/product/review/pending')).toBe('product-manage')
  expect(resolveActiveSection('/ops/exclusive')).toBe('data')
  expect(resolveActiveSection('/productxxx')).toBeNull()
  expect(isRoutePathUnderPrefix('/productxxx', '/product')).toBe(false)

  expect(
    resolveActiveSection('/product/library/detail', [
      ['/product', 'generic'],
      ['/product/library', 'specific']
    ])
  ).toBe('specific')
})

test('sidebar menu filtering keeps only current section menus when section is known', () => {
  const menus = filterAccessibleMenus(
    [
      { label: '数据平台', key: 'data-group', _section: 'data', roles: [ROLE_CODES.BIZ_STAFF] },
      { label: '商品管理', key: 'product-manage-group', _section: 'product-manage', roles: [ROLE_CODES.BIZ_STAFF] },
      { label: '寄样审核', key: '/sample', _section: 'sample', roles: [ROLE_CODES.BIZ_STAFF] },
      { label: '系统管理', key: 'system', _section: 'system', roles: [ROLE_CODES.ADMIN] }
    ],
    [ROLE_CODES.BIZ_STAFF],
    'data'
  )

  expect(menus.map((menu) => menu.key)).toEqual(['data-group'])
})

test('sidebar menu filtering falls back to permitted menus when section is unknown', () => {
  const menus = filterAccessibleMenus(
    [
      { label: '数据平台', key: 'data-group', _section: 'data', roles: [ROLE_CODES.BIZ_STAFF] },
      { label: '商品管理', key: 'product-manage-group', _section: 'product-manage', roles: [ROLE_CODES.BIZ_STAFF] },
      { label: '寄样审核', key: '/sample', _section: 'sample', roles: [ROLE_CODES.BIZ_STAFF] },
      { label: '系统管理', key: 'system', _section: 'system', roles: [ROLE_CODES.ADMIN] }
    ],
    [ROLE_CODES.BIZ_STAFF],
    null
  )

  expect(menus.map((menu) => menu.key)).toEqual(['data-group', 'product-manage-group', '/sample'])
})

test('router guard waits for usable roles before home redirects', () => {
  const homeCalls: string[] = []
  const resolveHomePath = () => {
    homeCalls.push('called')
    return '/data'
  }

  const missingRolesDecision = resolveGuardDecision({
    toPath: '/data',
    fromPath: '/dashboard',
    isLoggedIn: true,
    roleCodes: [],
    requiredRoles: [ROLE_CODES.BIZ_STAFF],
    resolveHomePath
  })

  expect(missingRolesDecision).toEqual({
    type: 'redirect',
    redirectTarget: '/login',
    reason: 'missing-role-codes'
  })
  expect(homeCalls).toHaveLength(0)

  const loginDecision = resolveGuardDecision({
    toPath: '/login',
    fromPath: '/data',
    isLoggedIn: true,
    roleCodes: [],
    resolveHomePath
  })

  expect(loginDecision).toEqual({ type: 'allow' })
  expect(homeCalls).toHaveLength(0)

  const repeatTargetDecision = resolveGuardDecision({
    toPath: '/orders',
    fromPath: '/dashboard',
    isLoggedIn: true,
    roleCodes: [ROLE_CODES.CHANNEL_STAFF],
    requiredRoles: [ROLE_CODES.BIZ_LEADER],
    resolveHomePath: () => '/orders'
  })

  expect(repeatTargetDecision).toEqual({
    type: 'abort',
    redirectTarget: '/orders',
    reason: 'access-denied'
  })
})

test('router guard warning helper suppresses normal and repeated redirect logs', () => {
  const shouldEmitWarning = createGuardWarningDeduper()
  const loginRedirect = {
    type: 'redirect',
    redirectTarget: '/dashboard',
    reason: 'logged-in-login'
  } as const
  const accessDenied = {
    type: 'redirect',
    redirectTarget: '/data',
    reason: 'access-denied'
  } as const

  expect(shouldEmitWarning(loginRedirect, '/', '/login', [ROLE_CODES.ADMIN])).toBe(false)
  expect(shouldEmitWarning(accessDenied, '/orders', '/sample', [ROLE_CODES.CHANNEL_STAFF])).toBe(true)
  expect(shouldEmitWarning(accessDenied, '/orders', '/sample', [ROLE_CODES.CHANNEL_STAFF])).toBe(false)
  expect(shouldEmitWarning(accessDenied, '/data', '/sample', [ROLE_CODES.CHANNEL_STAFF])).toBe(true)
})

function restoreEnv(name: string, value: string | undefined) {
  if (value === undefined) {
    delete process.env[name]
    return
  }
  process.env[name] = value
}
