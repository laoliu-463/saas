/**
 * RBAC 常量模块单元测试
 *
 * 测试覆盖范围：
 * - hasAccess：无角色要求时默认放行、管理员绕过、角色匹配/不匹配
 * - isAdminRole：管理员角色识别
 */
import { describe, expect, it } from 'vitest'

import { ROLE_CODES, hasAccess, isAdminRole } from './rbac'

describe('rbac helpers', () => {
  // 验证：无角色要求的路由默认允许所有角色访问
  it('allows routes without required roles', () => {
    // 无参数时返回 true
    expect(hasAccess()).toBe(true)
    // requiredRoles 为空数组时返回 true
    expect(hasAccess([ROLE_CODES.BIZ_STAFF], []))
    expect(hasAccess([ROLE_CODES.BIZ_STAFF], [])).toBe(true)
  })

  // 验证：管理员拥有全部权限，普通角色只在匹配时通过
  it('allows admin and matching roles while rejecting unrelated roles', () => {
    // 管理员角色识别
    expect(isAdminRole([ROLE_CODES.ADMIN])).toBe(true)
    // 管理员可访问任意角色要求的资源
    expect(hasAccess([ROLE_CODES.ADMIN], [ROLE_CODES.BIZ_LEADER])).toBe(true)
    // 角色匹配时通过
    expect(hasAccess([ROLE_CODES.BIZ_STAFF], [ROLE_CODES.BIZ_STAFF])).toBe(true)
    // 角色不匹配时拒绝
    expect(hasAccess([ROLE_CODES.CHANNEL_STAFF], [ROLE_CODES.BIZ_STAFF])).toBe(false)
  })
})
