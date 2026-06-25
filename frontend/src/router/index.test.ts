import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ROLE_CODES } from '../constants/rbac'

vi.mock('../views/Login.vue', () => ({ default: {} }))
vi.mock('../views/layout/index.vue', () => ({ default: {} }))
vi.mock('../views/product/ProductLibrary.vue', () => ({ default: {} }))
vi.mock('../views/product/ActivityList.vue', () => ({ default: {} }))
vi.mock('../views/product/index.vue', () => ({ default: {} }))
vi.mock('../views/talent/index.vue', () => ({ default: {} }))
vi.mock('../views/sample/index.vue', () => ({ default: {} }))
vi.mock('../views/sample/Apply.vue', () => ({ default: {} }))
vi.mock('../views/sample/SampleDetail.vue', () => ({ default: {} }))
vi.mock('../views/ops/ExclusiveStatus.vue', () => ({ default: {} }))
vi.mock('../views/ops/Shipping.vue', () => ({ default: {} }))
vi.mock('../views/data/index.vue', () => ({ default: {} }))
vi.mock('../views/data/OrderList.vue', () => ({ default: {} }))
vi.mock('../views/system/UserList.vue', () => ({ default: {} }))
vi.mock('../views/system/RoleList.vue', () => ({ default: {} }))
vi.mock('../views/system/DeptList.vue', () => ({ default: {} }))
vi.mock('../views/system/ConfigList.vue', () => ({ default: {} }))
vi.mock('../views/ops/DouyinIntegration.vue', () => ({ default: {} }))
vi.mock('../views/system/OperationLogList.vue', () => ({ default: {} }))
vi.mock('../views/orders/index.vue', () => ({ default: {} }))
vi.mock('../views/dashboard/index.vue', () => ({ default: {} }))

type GuardHook = (to: RouteStub, from: RouteStub) => unknown
type AfterHook = (to: RouteStub, from: RouteStub, failure?: { type: number }) => unknown

interface RouteStub {
  path: string
  fullPath: string
  meta?: Record<string, unknown>
}

interface AuthState {
  isLoggedIn: boolean
  roleCodes: string[]
  hydrateFromStorage: ReturnType<typeof vi.fn>
}

let beforeEachHook: GuardHook | undefined
let afterEachHook: AfterHook | undefined
let routesConfig: unknown[] = []
let authState: AuthState
const resolveCalls: string[] = []
const timingCalls: unknown[] = []

vi.mock('vue-router', () => ({
  createWebHistory: vi.fn(() => ({ mode: 'history' })),
  createRouter: vi.fn((config: { routes: unknown[] }) => {
    routesConfig = config.routes
    return {
      beforeEach: vi.fn((hook: GuardHook) => {
        beforeEachHook = hook
      }),
      afterEach: vi.fn((hook: AfterHook) => {
        afterEachHook = hook
      }),
      resolve: vi.fn((path: string) => {
        resolveCalls.push(path)
        const rolesByPath: Record<string, string[] | undefined> = {
          '/dashboard': [ROLE_CODES.BIZ_LEADER, ROLE_CODES.CHANNEL_LEADER, ROLE_CODES.ADMIN],
          '/orders': [ROLE_CODES.BIZ_LEADER, ROLE_CODES.CHANNEL_LEADER, ROLE_CODES.ADMIN],
          '/data': [ROLE_CODES.BIZ_LEADER, ROLE_CODES.BIZ_STAFF, ROLE_CODES.CHANNEL_LEADER, ROLE_CODES.CHANNEL_STAFF],
          '/product': [ROLE_CODES.BIZ_LEADER, ROLE_CODES.BIZ_STAFF, ROLE_CODES.CHANNEL_LEADER, ROLE_CODES.CHANNEL_STAFF],
          '/product/manage': [ROLE_CODES.BIZ_LEADER],
          '/product/manage/products': [ROLE_CODES.BIZ_LEADER, ROLE_CODES.BIZ_STAFF],
          '/talent': [ROLE_CODES.CHANNEL_LEADER, ROLE_CODES.CHANNEL_STAFF],
          '/sample': [ROLE_CODES.BIZ_LEADER, ROLE_CODES.BIZ_STAFF, ROLE_CODES.CHANNEL_LEADER, ROLE_CODES.CHANNEL_STAFF],
          '/ops/shipping': [ROLE_CODES.OPS_STAFF],
          '/system/users': [ROLE_CODES.ADMIN]
        }
        return { matched: [{ meta: { roles: rolesByPath[path] } }] }
      })
    }
  })
}))

vi.mock('../stores/auth', () => ({
  useAuthStore: () => authState
}))

vi.mock('../utils/performanceTiming', () => ({
  nowMs: vi.fn(() => 1000),
  recordFrontendTiming: vi.fn((scope: string, payload: unknown, options?: unknown) => {
    timingCalls.push({ scope, payload, options })
  })
}))

beforeEach(async () => {
  vi.resetModules()
  beforeEachHook = undefined
  afterEachHook = undefined
  routesConfig = []
  resolveCalls.length = 0
  timingCalls.length = 0
  authState = {
    isLoggedIn: true,
    roleCodes: [ROLE_CODES.ADMIN],
    hydrateFromStorage: vi.fn()
  }
  await import('./index')
})

describe('router configuration', () => {
  it('registers the product, system, dashboard and fallback routes', () => {
    expect(routesConfig).toHaveLength(3)
    const layout = routesConfig[1] as { children: Array<{ path: string; redirect?: unknown; meta?: { title?: string } }> }

    expect(layout.children.map((route) => route.path)).toEqual(
      expect.arrayContaining([
        'product',
        'product/manage',
        'sample/apply',
        'ops/shipping',
        'system/depts',
        'system/departments',
        'system/douyin',
        'profile',
        'orders',
        'dashboard',
        ''
      ])
    )
    expect(layout.children.find((route) => route.path === 'system/departments')?.redirect).toBe('/system/depts')
    expect(layout.children.find((route) => route.path === 'system/douyin')?.meta?.title).toBe('抖店联调')
    expect(layout.children.find((route) => route.path === 'sample')?.meta?.title).toBe('合作单')
    expect(layout.children.find((route) => route.path === 'ops/shipping')?.meta?.title).toBe('发货台')
    expect((routesConfig[2] as { redirect: string }).redirect).toBe('/data')
  })

  it('keeps lazy component loaders and dynamic redirects executable', async () => {
    const allRoutes = flattenRoutes(routesConfig)
    const componentLoaders = allRoutes
      .map((route) => route.component)
      .filter((component): component is () => Promise<unknown> => typeof component === 'function')

    expect(componentLoaders).toHaveLength(25)
    await Promise.all(componentLoaders.map((loadComponent) => loadComponent()))

    const activityRedirect = allRoutes.find((route) => route.path === 'product/activity/:activityId')?.redirect
    expect(typeof activityRedirect).toBe('function')
      // 历史 /product/manage/:activityId 与 /product/activity/:activityId
      // 都统一重定向到活动商品列表，并用 query 保留 activityId。
      expect(
        (activityRedirect as (to: { params: { activityId: string } }) => { path: string; query: { activityId: string } })(
          { params: { activityId: 'A-100' } }
        )
      ).toEqual({ path: '/product/manage/products', query: { activityId: 'A-100' } })
  }, 60000)
})

describe('router guards', () => {
  it('redirects anonymous users to login with the original target and records beforeEach timing', () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    authState.isLoggedIn = false
    authState.roleCodes = []

    const result = beforeEachHook?.(
      route('/orders', { roles: [ROLE_CODES.ADMIN] }),
      route('/login')
    )

    expect(result).toBe('/login?redirect=%2Forders')
    expect(authState.hydrateFromStorage).toHaveBeenCalledOnce()
    expect(timingCalls[0]).toMatchObject({
      scope: 'router',
      payload: { phase: 'beforeEach', from: '/login', to: '/orders' }
    })
    warnSpy.mockRestore()
  })

  it('preserves douyin oauth callback query for anonymous users', () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    authState.isLoggedIn = false
    authState.roleCodes = []

    const result = beforeEachHook?.(
      route('/system/douyin', { roles: [ROLE_CODES.ADMIN] }, '/system/douyin?oauth=success'),
      route('/login')
    )

    expect(result).toBe('/login?redirect=%2Fsystem%2Fdouyin%3Foauth%3Dsuccess')
    warnSpy.mockRestore()
  })

  it('sends channel staff to the first channel-accessible home route', () => {
    authState.roleCodes = [ROLE_CODES.CHANNEL_STAFF]

    const result = beforeEachHook?.(route('/', {}), route('/login'))

    expect(result).toBe('/product')
    expect(resolveCalls).toEqual(['/product'])
  })

  it('sends ops staff to the shipping home route', () => {
    authState.roleCodes = [ROLE_CODES.OPS_STAFF]

    const result = beforeEachHook?.(route('/', {}), route('/login'))

    expect(result).toBe('/ops/shipping')
    expect(resolveCalls).toEqual(['/ops/shipping'])
  })

  it('sends biz staff to product management when it is the first accessible home route', () => {
    authState.roleCodes = [ROLE_CODES.BIZ_STAFF]

    const result = beforeEachHook?.(route('/', {}), route('/login'))

    expect(result).toBe('/product/manage/products')
    expect(resolveCalls).toEqual(['/product/manage/products'])
  })

  it('sends admin users through the default home candidates', () => {
    authState.roleCodes = [ROLE_CODES.ADMIN]

    const result = beforeEachHook?.(route('/', {}), route('/login'))

    expect(result).toBe('/data')
    expect(resolveCalls).toEqual(['/data'])
  })

  it('falls back to login when no home candidates are accessible', () => {
    authState.roleCodes = ['unknown-role']

    const result = beforeEachHook?.(route('/', {}), route('/login'))

    expect(result).toBe('/login')
    expect(resolveCalls).toEqual([
      '/data',
      '/orders',
      '/product',
      '/product/manage',
      '/product/manage/products',
      '/talent',
      '/ops/shipping',
      '/sample',
      '/system/users'
    ])
  })

  it('aborts loops and warns once for denied routes', () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    authState.roleCodes = [ROLE_CODES.CHANNEL_STAFF]

    const first = beforeEachHook?.(
      route('/product', { roles: [ROLE_CODES.ADMIN] }),
      route('/system/users')
    )
    const duplicate = beforeEachHook?.(
      route('/product', { roles: [ROLE_CODES.ADMIN] }),
      route('/system/users')
    )

    expect(first).toBe(false)
    expect(duplicate).toBe(false)
    expect(warnSpy).toHaveBeenCalledOnce()

    warnSpy.mockRestore()
  })

  it('allows matching roles', () => {
    expect(beforeEachHook?.(route('/system/users', { roles: [ROLE_CODES.ADMIN] }), route('/dashboard', {}, ''))).toBe(true)
    expect(timingCalls[0]).toMatchObject({
      payload: { from: '(start)' }
    })
  })

  it('records afterEach timing and failure status', () => {
    beforeEachHook?.(route('/dashboard', { roles: [ROLE_CODES.ADMIN] }), route('/login'))

    afterEachHook?.(route('/dashboard'), route('/login'), { type: 4 })

    expect(timingCalls.at(-1)).toMatchObject({
      scope: 'router',
      payload: { phase: 'afterEach', from: '/login', to: '/dashboard', durationMs: 0, failure: 4 },
      options: { failed: true }
    })
  })

  it('records afterEach timing without prior start or failure', () => {
    afterEachHook?.(route('/dashboard'), route('/login', {}, ''))

    expect(timingCalls.at(-1)).toMatchObject({
      scope: 'router',
      payload: { phase: 'afterEach', from: '(start)', to: '/dashboard', durationMs: 0, failure: undefined },
      options: { failed: false }
    })
  })
})

function route(path: string, meta: Record<string, unknown> = {}, fullPath = path): RouteStub {
  return { path, fullPath, meta }
}

function flattenRoutes(routes: unknown[]): Array<{ path?: string; redirect?: unknown; component?: unknown; children?: unknown[] }> {
  return routes.flatMap((route) => {
    const typed = route as { children?: unknown[] }
    return [route as { path?: string; redirect?: unknown; component?: unknown; children?: unknown[] }, ...flattenRoutes(typed.children || [])]
  })
}
