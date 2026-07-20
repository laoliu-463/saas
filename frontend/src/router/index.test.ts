import { beforeEach, describe, expect, it, vi } from 'vitest'

import { PERMISSION_CODES } from '../constants/permissions'

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
  permissionCodes: string[]
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
        const permissionsByPath: Record<string, string[] | undefined> = {
          '/dashboard': [PERMISSION_CODES.DASHBOARD_ACCESS],
          '/orders': [PERMISSION_CODES.ORDER_ACCESS],
          '/data': [PERMISSION_CODES.DATA_ACCESS],
          '/product': [PERMISSION_CODES.PRODUCT_ACCESS],
          '/product/manage': [PERMISSION_CODES.PRODUCT_MANAGE_ACCESS],
          '/product/manage/products': [PERMISSION_CODES.PRODUCT_MANAGE_ACCESS],
          '/talent': [PERMISSION_CODES.TALENT_ACCESS],
          '/sample': [PERMISSION_CODES.SAMPLE_ACCESS],
          '/ops/shipping': [PERMISSION_CODES.SHIPPING_ACCESS],
          '/system/users': [PERMISSION_CODES.SYS_USER_ACCESS]
        }
        return { matched: [{ meta: { permissions: permissionsByPath[path] } }] }
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
    permissionCodes: Object.values(PERMISSION_CODES),
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
  // Lazy component imports can exceed one minute on a loaded Windows checkout;
  // the test still fails if module resolution hangs beyond two minutes.
  }, 120000)
})

describe('router guards', () => {
  it('redirects anonymous users to login with the original target and records beforeEach timing', () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    authState.isLoggedIn = false
    authState.permissionCodes = []

    const result = beforeEachHook?.(
      route('/orders', { permissions: [PERMISSION_CODES.SYS_USER_ACCESS] }),
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
    authState.permissionCodes = []

    const result = beforeEachHook?.(
      route('/system/douyin', { permissions: [PERMISSION_CODES.SYS_USER_ACCESS] }, '/system/douyin?oauth=success'),
      route('/login')
    )

    expect(result).toBe('/login?redirect=%2Fsystem%2Fdouyin%3Foauth%3Dsuccess')
    warnSpy.mockRestore()
  })

  it('sends a product reader to the first granted home route', () => {
    authState.permissionCodes = [PERMISSION_CODES.PRODUCT_ACCESS]

    const result = beforeEachHook?.(route('/', {}), route('/login'))

    expect(result).toBe('/product')
    expect(resolveCalls).toEqual(['/data', '/orders', '/product'])
  })

  it('sends a shipping operator to the shipping home route', () => {
    authState.permissionCodes = [PERMISSION_CODES.SHIPPING_ACCESS]

    const result = beforeEachHook?.(route('/', {}), route('/login'))

    expect(result).toBe('/ops/shipping')
    expect(resolveCalls).toEqual([
      '/data', '/orders', '/product', '/product/manage', '/product/manage/products', '/talent', '/ops/shipping'
    ])
  })

  it('does not collapse a composite business account into the ops-only home route', () => {
    authState.permissionCodes = [
      PERMISSION_CODES.PRODUCT_MANAGE_ACCESS,
      PERMISSION_CODES.ORDER_ACCESS,
      PERMISSION_CODES.PRODUCT_ACCESS,
      PERMISSION_CODES.DATA_ACCESS,
      PERMISSION_CODES.SHIPPING_ACCESS
    ]

    const result = beforeEachHook?.(route('/', {}), route('/login'))

    expect(result).toBe('/data')
    expect(resolveCalls).toEqual(['/data'])
  })

  it('sends product management users to the first granted management route', () => {
    authState.permissionCodes = [PERMISSION_CODES.PRODUCT_MANAGE_ACCESS]

    const result = beforeEachHook?.(route('/', {}), route('/login'))

    expect(result).toBe('/product/manage')
    expect(resolveCalls).toEqual(['/data', '/orders', '/product', '/product/manage'])
  })

  it('registers the talent CRM permission', () => {
    const allRoutes = flattenRoutes(routesConfig)
    const talentRoute = allRoutes.find((route) => route.path === 'talent') as {
      meta?: { permissions?: string[] }
    } | undefined

    expect(talentRoute?.meta?.permissions).toEqual(
      expect.arrayContaining([PERMISSION_CODES.TALENT_ACCESS])
    )
  })

  it('sends admin users through the default home candidates', () => {
    authState.permissionCodes = Object.values(PERMISSION_CODES)

    const result = beforeEachHook?.(route('/', {}), route('/login'))

    expect(result).toBe('/data')
    expect(resolveCalls).toEqual(['/data'])
  })

  it('falls back to profile when no home candidates are accessible', () => {
    authState.permissionCodes = ['unknown:permission']

    const result = beforeEachHook?.(route('/', {}), route('/login'))

    expect(result).toBe('/profile')
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
    authState.permissionCodes = [PERMISSION_CODES.PRODUCT_ACCESS]

    const first = beforeEachHook?.(
      route('/product', { permissions: [PERMISSION_CODES.SYS_USER_ACCESS] }),
      route('/system/users')
    )
    const duplicate = beforeEachHook?.(
      route('/product', { permissions: [PERMISSION_CODES.SYS_USER_ACCESS] }),
      route('/system/users')
    )

    expect(first).toBe(false)
    expect(duplicate).toBe(false)
    expect(warnSpy).toHaveBeenCalledOnce()

    warnSpy.mockRestore()
  })

  it('allows matching permissions', () => {
    expect(beforeEachHook?.(route('/system/users', { permissions: [PERMISSION_CODES.SYS_USER_ACCESS] }), route('/dashboard', {}, ''))).toBe(true)
    expect(timingCalls[0]).toMatchObject({
      payload: { from: '(start)' }
    })
  })

  it('records afterEach timing and failure status', () => {
    beforeEachHook?.(route('/dashboard', { permissions: [PERMISSION_CODES.SYS_USER_ACCESS] }), route('/login'))

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
