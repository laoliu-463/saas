import { hasAccess } from '../constants/rbac'

export type GuardDecision =
  | { type: 'allow' }
  | { type: 'redirect'; redirectTarget: string; reason: string }
  | { type: 'abort'; redirectTarget: string; reason: string }

export type GuardRedirectDecision = Exclude<GuardDecision, { type: 'allow' }>

export interface GuardDecisionInput {
  toPath: string
  fromPath: string
  isLoggedIn: boolean
  roleCodes: readonly string[]
  requiredRoles?: string[]
  resolveHomePath: () => string
}

export function resolveGuardDecision(input: GuardDecisionInput): GuardDecision {
  const roleCodes = [...input.roleCodes]

  if (input.toPath !== '/login' && !input.isLoggedIn) {
    return buildRedirectDecision(input, '/login', 'anonymous')
  }

  if (input.toPath === '/login') {
    if (!input.isLoggedIn || roleCodes.length === 0) {
      return { type: 'allow' }
    }
    return buildHomeRedirectDecision(input, 'logged-in-login')
  }

  if (input.isLoggedIn && roleCodes.length === 0) {
    return buildRedirectDecision(input, '/login', 'missing-role-codes')
  }

  if (input.toPath === '/') {
    return buildHomeRedirectDecision(input, 'root')
  }

  if (!hasAccess(roleCodes, input.requiredRoles)) {
    return buildHomeRedirectDecision(input, 'access-denied')
  }

  return { type: 'allow' }
}

export function createGuardWarningDeduper() {
  const emitted = new Set<string>()
  return (decision: GuardRedirectDecision, from: string, to: string, roleCodes: readonly string[]): boolean => {
    if (isRoutineRedirectReason(decision.reason)) {
      return false
    }
    const key = JSON.stringify({
      from,
      to,
      roleCodes: [...roleCodes],
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
  const target = normalizeRoutePath(redirectTarget || '/login')
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

function normalizeRoutePath(path: string): string {
  const clean = String(path || '/').split(/[?#]/)[0]?.trim() || '/'
  const withSlash = clean.startsWith('/') ? clean : `/${clean}`
  if (withSlash === '/') return withSlash
  return withSlash.replace(/\/+$/, '')
}
