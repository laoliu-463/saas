import { hasPermission } from '../constants/permissions'
import { buildLoginRedirectTarget } from './redirect'

export type GuardDecision =
  | { type: 'allow' }
  | { type: 'redirect'; redirectTarget: string; reason: string }
  | { type: 'abort'; redirectTarget: string; reason: string }

export type GuardRedirectDecision = Exclude<GuardDecision, { type: 'allow' }>

export interface GuardDecisionInput {
  toPath: string
  toFullPath?: string
  fromPath: string
  isLoggedIn: boolean
  permissionCodes: readonly string[]
  requiredPermissions?: string[]
  resolveHomePath: () => string
}

export function resolveGuardDecision(input: GuardDecisionInput): GuardDecision {
  if (input.toPath !== '/login' && !input.isLoggedIn) {
    return createRedirectDecision(input, buildLoginRedirectTarget(input.toFullPath || input.toPath), 'anonymous', true)
  }

  if (input.toPath === '/login') {
    if (!input.isLoggedIn) {
      return { type: 'allow' }
    }
    return buildHomeRedirectDecision(input, 'logged-in-login')
  }

  if (input.toPath === '/') {
    return buildHomeRedirectDecision(input, 'root')
  }

  if (!hasPermission(input.permissionCodes, input.requiredPermissions)) {
    return buildHomeRedirectDecision(input, 'access-denied')
  }

  return { type: 'allow' }
}

export function createGuardWarningDeduper() {
  const emitted = new Set<string>()
  return (decision: GuardRedirectDecision, from: string, to: string, permissionCodes: readonly string[]): boolean => {
    if (isRoutineRedirectReason(decision.reason)) {
      return false
    }
    const key = JSON.stringify({
      from,
      to,
      permissionCodes: [...permissionCodes],
      redirectTarget: decision.redirectTarget,
      reason: decision.reason
    })
    if (emitted.has(key)) {
      return false
    }
    emitted.add(key)
    return true
  }
}

function isRoutineRedirectReason(reason: string): boolean {
  return reason === 'logged-in-login' || reason === 'root'
}

function buildHomeRedirectDecision(input: GuardDecisionInput, reason: string): GuardDecision {
  return buildRedirectDecision(input, input.resolveHomePath(), reason)
}

function buildRedirectDecision(input: GuardDecisionInput, redirectTarget: string, reason: string): GuardDecision {
  return createRedirectDecision(input, redirectTarget, reason, false)
}

function createRedirectDecision(
  input: GuardDecisionInput,
  redirectTarget: string,
  reason: string,
  preserveTargetQuery: boolean
): GuardDecision {
  const target = preserveTargetQuery ? normalizeInternalRedirect(redirectTarget || '/login') : normalizeRoutePath(redirectTarget || '/login')
  if (target === normalizeRoutePath(input.toPath)) {
    return {
      type: 'abort',
      redirectTarget: target,
      reason
    }
  }
  return {
    type: 'redirect',
    redirectTarget: target,
    reason
  }
}

function normalizeInternalRedirect(path: string): string {
  const value = String(path || '/login').trim()
  if (!value.startsWith('/') || value.startsWith('//')) {
    return '/login'
  }
  return value
}

function normalizeRoutePath(path: string): string {
  const clean = String(path || '/').split(/[?#]/)[0]?.trim() || '/'
  const withSlash = clean.startsWith('/') ? clean : `/${clean}`
  if (withSlash === '/') return withSlash
  return withSlash.replace(/\/+$/, '')
}
