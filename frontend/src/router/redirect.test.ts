/**
 * 路由重定向安全工具单元测试
 *
 * 测试覆盖范围：
 * - buildLoginRedirectTarget：登录重定向 URL 编码
 * - resolveSafePostLoginRedirect：开放重定向防护（外部 URL、协议相对 URL、登录循环）
 */
import { describe, expect, it } from 'vitest'

import { buildLoginRedirectTarget, resolveSafePostLoginRedirect } from './redirect'

describe('router redirect helpers', () => {
  // 验证：受保护路径会被正确 URL 编码后拼入登录页 redirect 参数
  it('encodes the protected target in login redirect query', () => {
    expect(buildLoginRedirectTarget('/system/douyin?oauth=success')).toBe(
      '/login?redirect=%2Fsystem%2Fdouyin%3Foauth%3Dsuccess'
    )
  })

  // 验证：各种恶意重定向场景被拦截，回退到安全的默认路径
  it('keeps login redirects inside the SaaS frontend', () => {
    // 合法的内部路径正常通过
    expect(resolveSafePostLoginRedirect('/system/douyin?oauth=success', '/data')).toBe('/system/douyin?oauth=success')
    // 外部域名 URL 被拦截
    expect(resolveSafePostLoginRedirect('https://evil.example/phish', '/data')).toBe('/data')
    // 协议相对 URL（//evil.example）被拦截
    expect(resolveSafePostLoginRedirect('//evil.example/phish', '/data')).toBe('/data')
    // 重定向到 /login 本身被拦截（防止登录循环）
    expect(resolveSafePostLoginRedirect('/login?redirect=/login', '/data')).toBe('/data')
  })
})
