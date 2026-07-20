import { describe, expect, it } from 'vitest'

import { PERMISSION_CODES } from '../constants/permissions'
import { createGuardWarningDeduper, resolveGuardDecision } from './guard'

describe('resolveGuardDecision', () => {
  it('redirects anonymous users to login for protected routes', () => {
    expect(
      resolveGuardDecision({
        toPath: '/orders',
        toFullPath: '/orders',
        fromPath: '/login',
        isLoggedIn: false,
        permissionCodes: [],
        requiredPermissions: [PERMISSION_CODES.SYS_USER_ACCESS],
        resolveHomePath: () => '/dashboard'
      })
    ).toEqual({ type: 'redirect', redirectTarget: '/login?redirect=%2Forders', reason: 'anonymous' })
  })

  it('allows anonymous users to visit login', () => {
    expect(
      resolveGuardDecision({
        toPath: '/login',
        toFullPath: '/login',
        fromPath: '/',
        isLoggedIn: false,
        permissionCodes: [],
        resolveHomePath: () => '/dashboard'
      })
    ).toEqual({ type: 'allow' })
  })

  it('redirects logged-in users away from login', () => {
    expect(
      resolveGuardDecision({
        toPath: '/login',
        toFullPath: '/login',
        fromPath: '/orders',
        isLoggedIn: true,
        permissionCodes: [PERMISSION_CODES.SYS_USER_ACCESS],
        resolveHomePath: () => '/system/users'
      })
    ).toEqual({ type: 'redirect', redirectTarget: '/system/users', reason: 'logged-in-login' })
  })

  it('aborts when the redirect target is already the current route', () => {
    expect(
      resolveGuardDecision({
        toPath: '/',
        toFullPath: '/',
        fromPath: '/orders',
        isLoggedIn: true,
        permissionCodes: [PERMISSION_CODES.SYS_USER_ACCESS],
        resolveHomePath: () => '/'
      })
    ).toEqual({ type: 'abort', redirectTarget: '/', reason: 'root' })
  })

  it('keeps logged-in users with no grants authenticated and redirects denied routes to profile', () => {
    expect(
      resolveGuardDecision({
        toPath: '/orders',
        toFullPath: '/orders',
        fromPath: '/',
        isLoggedIn: true,
        permissionCodes: [],
        requiredPermissions: [PERMISSION_CODES.ORDER_ACCESS],
        resolveHomePath: () => '/profile'
      })
    ).toEqual({ type: 'redirect', redirectTarget: '/profile', reason: 'access-denied' })
  })

  it('redirects root to the resolved home path', () => {
    expect(
      resolveGuardDecision({
        toPath: '/',
        toFullPath: '/',
        fromPath: '/login',
        isLoggedIn: true,
        permissionCodes: [PERMISSION_CODES.PRODUCT_ACCESS],
        resolveHomePath: () => '/product'
      })
    ).toEqual({ type: 'redirect', redirectTarget: '/product', reason: 'root' })
  })

  it('redirects denied routes to the resolved home path', () => {
    expect(
      resolveGuardDecision({
        toPath: '/system/users',
        toFullPath: '/system/users',
        fromPath: '/product',
        isLoggedIn: true,
        permissionCodes: [PERMISSION_CODES.PRODUCT_ACCESS],
        requiredPermissions: [PERMISSION_CODES.SYS_USER_ACCESS],
        resolveHomePath: () => '/product'
      })
    ).toEqual({ type: 'redirect', redirectTarget: '/product', reason: 'access-denied' })
  })

  it('allows logged-in users with matching permissions', () => {
    expect(
      resolveGuardDecision({
        toPath: '/orders',
        toFullPath: '/orders',
        fromPath: '/dashboard',
        isLoggedIn: true,
        permissionCodes: [PERMISSION_CODES.SYS_USER_ACCESS],
        requiredPermissions: [PERMISSION_CODES.SYS_USER_ACCESS],
        resolveHomePath: () => '/dashboard'
      })
    ).toEqual({ type: 'allow' })
  })

  it('defaults blank redirect targets to login', () => {
    expect(
      resolveGuardDecision({
        toPath: '/',
        toFullPath: '/',
        fromPath: '/orders',
        isLoggedIn: true,
        permissionCodes: [PERMISSION_CODES.SYS_USER_ACCESS],
        resolveHomePath: () => ''
      })
    ).toEqual({ type: 'redirect', redirectTarget: '/login', reason: 'root' })
  })

  it('normalizes redirect targets without slashes and route paths without values', () => {
    expect(
      resolveGuardDecision({
        toPath: '/login',
        toFullPath: '/login',
        fromPath: '/',
        isLoggedIn: true,
        permissionCodes: [PERMISSION_CODES.SYS_USER_ACCESS],
        resolveHomePath: () => 'dashboard/?tab=summary#top'
      })
    ).toEqual({ type: 'redirect', redirectTarget: '/dashboard', reason: 'logged-in-login' })

    expect(
      resolveGuardDecision({
        toPath: '',
        toFullPath: '',
        fromPath: '/',
        isLoggedIn: false,
        permissionCodes: [],
        resolveHomePath: () => '/dashboard'
      })
    ).toEqual({ type: 'redirect', redirectTarget: '/login', reason: 'anonymous' })

    expect(
      resolveGuardDecision({
        toPath: '/login',
        toFullPath: '/login',
        fromPath: '/',
        isLoggedIn: true,
        permissionCodes: [PERMISSION_CODES.SYS_USER_ACCESS],
        resolveHomePath: () => '?tab=summary'
      })
    ).toEqual({ type: 'redirect', redirectTarget: '/', reason: 'logged-in-login' })
  })

  it('preserves protected oauth callback target when anonymous users must login first', () => {
    expect(
      resolveGuardDecision({
        toPath: '/system/douyin',
        toFullPath: '/system/douyin?oauth=success',
        fromPath: '/',
        isLoggedIn: false,
        permissionCodes: [],
        requiredPermissions: [PERMISSION_CODES.SYS_USER_ACCESS],
        resolveHomePath: () => '/dashboard'
      })
    ).toEqual({
      type: 'redirect',
      redirectTarget: '/login?redirect=%2Fsystem%2Fdouyin%3Foauth%3Dsuccess',
      reason: 'anonymous'
    })
  })
})

describe('createGuardWarningDeduper', () => {
  it('suppresses routine redirects', () => {
    const shouldWarn = createGuardWarningDeduper()

    expect(
      shouldWarn({ type: 'redirect', redirectTarget: '/dashboard', reason: 'root' }, '/', '/', [PERMISSION_CODES.SYS_USER_ACCESS])
    ).toBe(false)
    expect(
      shouldWarn({ type: 'redirect', redirectTarget: '/dashboard', reason: 'logged-in-login' }, '/', '/login', [
        PERMISSION_CODES.SYS_USER_ACCESS
      ])
    ).toBe(false)
  })

  it('emits a non-routine redirect only once for the same inputs', () => {
    const shouldWarn = createGuardWarningDeduper()
    const decision = { type: 'redirect' as const, redirectTarget: '/product', reason: 'access-denied' }

    expect(shouldWarn(decision, '/system/users', '/product', [PERMISSION_CODES.PRODUCT_ACCESS])).toBe(true)
    expect(shouldWarn(decision, '/system/users', '/product', [PERMISSION_CODES.PRODUCT_ACCESS])).toBe(false)
    expect(shouldWarn(decision, '/system/roles', '/product', [PERMISSION_CODES.PRODUCT_ACCESS])).toBe(true)
  })
})
