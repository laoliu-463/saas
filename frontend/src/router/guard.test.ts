import { describe, expect, it } from 'vitest'

import { ROLE_CODES } from '../constants/rbac'
import { createGuardWarningDeduper, resolveGuardDecision } from './guard'

describe('resolveGuardDecision', () => {
  it('redirects anonymous users to login for protected routes', () => {
    expect(
      resolveGuardDecision({
        toPath: '/orders',
        toFullPath: '/orders',
        fromPath: '/login',
        isLoggedIn: false,
        roleCodes: [],
        requiredRoles: [ROLE_CODES.ADMIN],
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
        roleCodes: [],
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
        roleCodes: [ROLE_CODES.ADMIN],
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
        roleCodes: [ROLE_CODES.ADMIN],
        resolveHomePath: () => '/'
      })
    ).toEqual({ type: 'abort', redirectTarget: '/', reason: 'root' })
  })

  it('redirects logged-in users with no roles back to login', () => {
    expect(
      resolveGuardDecision({
        toPath: '/orders',
        toFullPath: '/orders',
        fromPath: '/',
        isLoggedIn: true,
        roleCodes: [],
        resolveHomePath: () => '/dashboard'
      })
    ).toEqual({ type: 'redirect', redirectTarget: '/login', reason: 'missing-role-codes' })
  })

  it('redirects root to the resolved home path', () => {
    expect(
      resolveGuardDecision({
        toPath: '/',
        toFullPath: '/',
        fromPath: '/login',
        isLoggedIn: true,
        roleCodes: [ROLE_CODES.CHANNEL_STAFF],
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
        roleCodes: [ROLE_CODES.CHANNEL_STAFF],
        requiredRoles: [ROLE_CODES.ADMIN],
        resolveHomePath: () => '/product'
      })
    ).toEqual({ type: 'redirect', redirectTarget: '/product', reason: 'access-denied' })
  })

  it('allows logged-in users with matching roles', () => {
    expect(
      resolveGuardDecision({
        toPath: '/orders',
        toFullPath: '/orders',
        fromPath: '/dashboard',
        isLoggedIn: true,
        roleCodes: [ROLE_CODES.ADMIN],
        requiredRoles: [ROLE_CODES.ADMIN],
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
        roleCodes: [ROLE_CODES.ADMIN],
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
        roleCodes: [ROLE_CODES.ADMIN],
        resolveHomePath: () => 'dashboard/?tab=summary#top'
      })
    ).toEqual({ type: 'redirect', redirectTarget: '/dashboard', reason: 'logged-in-login' })

    expect(
      resolveGuardDecision({
        toPath: '',
        toFullPath: '',
        fromPath: '/',
        isLoggedIn: false,
        roleCodes: [],
        resolveHomePath: () => '/dashboard'
      })
    ).toEqual({ type: 'redirect', redirectTarget: '/login', reason: 'anonymous' })

    expect(
      resolveGuardDecision({
        toPath: '/login',
        toFullPath: '/login',
        fromPath: '/',
        isLoggedIn: true,
        roleCodes: [ROLE_CODES.ADMIN],
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
        roleCodes: [],
        requiredRoles: [ROLE_CODES.ADMIN],
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
      shouldWarn({ type: 'redirect', redirectTarget: '/dashboard', reason: 'root' }, '/', '/', [ROLE_CODES.ADMIN])
    ).toBe(false)
    expect(
      shouldWarn({ type: 'redirect', redirectTarget: '/dashboard', reason: 'logged-in-login' }, '/', '/login', [
        ROLE_CODES.ADMIN
      ])
    ).toBe(false)
  })

  it('emits a non-routine redirect only once for the same inputs', () => {
    const shouldWarn = createGuardWarningDeduper()
    const decision = { type: 'redirect' as const, redirectTarget: '/product', reason: 'access-denied' }

    expect(shouldWarn(decision, '/system/users', '/product', [ROLE_CODES.CHANNEL_STAFF])).toBe(true)
    expect(shouldWarn(decision, '/system/users', '/product', [ROLE_CODES.CHANNEL_STAFF])).toBe(false)
    expect(shouldWarn(decision, '/system/roles', '/product', [ROLE_CODES.CHANNEL_STAFF])).toBe(true)
  })
})
