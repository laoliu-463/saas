/**
 * request.ts 核心辅助函数单元测试
 * 测试 normalizeServerMessage、buildFriendlyErrorMessage、isValidToken、
 * isAuthLoginRequest、isAuthRefreshRequest、shouldTryRefresh、shouldSuppressErrorNotice
 */
import { describe, expect, it, vi, beforeEach } from 'vitest'
import axios from 'axios'
import {
  isValidToken,
  isAuthLoginRequest,
  isAuthRefreshRequest,
  shouldTryRefresh,
  shouldSuppressErrorNotice,
  normalizeServerMessage,
  buildFriendlyErrorMessage,
} from './request'

// ─── isValidToken ──────────────────────────────────────────────────────────────

describe('isValidToken', () => {
  it('有效 token 返回 true', () => {
    expect(isValidToken('eyJhbGciOiJIUzI1NiJ9')).toBe(true)
    expect(isValidToken('Bearer test')).toBe(true)
    expect(isValidToken('a')).toBe(true)
  })

  it('空/空白/undefined/null 返回 false', () => {
    expect(isValidToken('')).toBe(false)
    expect(isValidToken('   ')).toBe(false)
    expect(isValidToken('undefined')).toBe(false)
    expect(isValidToken('null')).toBe(false)
    expect(isValidToken(null)).toBe(false)
    expect(isValidToken(undefined)).toBe(false)
  })

  it('非字符串返回 false', () => {
    expect(isValidToken(123 as any)).toBe(false)
    expect(isValidToken({} as any)).toBe(false)
    expect(isValidToken([] as any)).toBe(false)
    expect(isValidToken(true as any)).toBe(false)
  })
})

// ─── isAuthLoginRequest ───────────────────────────────────────────────────────

describe('isAuthLoginRequest', () => {
  it('识别 /auth/login 路径', () => {
    expect(isAuthLoginRequest({ url: '/api/auth/login' })).toBe(true)
    expect(isAuthLoginRequest({ url: '/auth/login' })).toBe(true)
    expect(isAuthLoginRequest({ url: 'http://localhost/api/auth/login' })).toBe(true)
  })

  it('从 error.config 读取 url', () => {
    expect(isAuthLoginRequest({ config: { url: '/api/auth/login' } })).toBe(true)
  })

  it('非登录路径返回 false', () => {
    expect(isAuthLoginRequest({ url: '/api/auth/refresh' })).toBe(false)
    expect(isAuthLoginRequest({ url: '/api/depts' })).toBe(false)
    expect(isAuthLoginRequest({ url: '' })).toBe(false)
    expect(isAuthLoginRequest({})).toBe(false)
  })
})

// ─── isAuthRefreshRequest ─────────────────────────────────────────────────────

describe('isAuthRefreshRequest', () => {
  it('识别 /auth/refresh 路径', () => {
    expect(isAuthRefreshRequest({ url: '/api/auth/refresh' })).toBe(true)
    expect(isAuthRefreshRequest({ url: '/auth/refresh' })).toBe(true)
  })

  it('从 error.config 读取 url', () => {
    expect(isAuthRefreshRequest({ config: { url: '/api/auth/refresh' } })).toBe(true)
  })

  it('非刷新路径返回 false', () => {
    expect(isAuthRefreshRequest({ url: '/api/auth/login' })).toBe(false)
    expect(isAuthRefreshRequest({ url: '/api/users' })).toBe(false)
    expect(isAuthRefreshRequest({ url: '' })).toBe(false)
    expect(isAuthRefreshRequest({})).toBe(false)
  })
})

// ─── shouldTryRefresh ─────────────────────────────────────────────────────────

describe('shouldTryRefresh', () => {
  beforeEach(() => {
    localStorage.removeItem('refreshToken')
  })

  it('登录/刷新请求不应尝试刷新', () => {
    expect(shouldTryRefresh({ url: '/api/auth/login' })).toBe(false)
    expect(shouldTryRefresh({ url: '/api/auth/refresh' })).toBe(false)
  })

  it('无 refreshToken 时返回 false', () => {
    expect(shouldTryRefresh({ url: '/api/depts' })).toBe(false)
  })

  it('有 refreshToken 时对普通请求返回 true', () => {
    localStorage.setItem('refreshToken', 'valid-refresh')
    expect(shouldTryRefresh({ url: '/api/depts' })).toBe(true)
  })

  it('空字符串 refreshToken 返回 false', () => {
    localStorage.setItem('refreshToken', '')
    expect(shouldTryRefresh({ url: '/api/depts' })).toBe(false)
  })
})

// ─── shouldSuppressErrorNotice ─────────────────────────────────────────────────

describe('shouldSuppressErrorNotice', () => {
  it('suppressErrorNotice: true 时返回 true', () => {
    expect(shouldSuppressErrorNotice({ suppressErrorNotice: true })).toBe(true)
  })

  it('suppressErrorNotice: false / 缺失 / null / undefined 返回 false', () => {
    expect(shouldSuppressErrorNotice({ suppressErrorNotice: false })).toBe(false)
    expect(shouldSuppressErrorNotice({})).toBe(false)
    expect(shouldSuppressErrorNotice(null)).toBe(false)
    expect(shouldSuppressErrorNotice(undefined)).toBe(false)
  })
})

// ─── normalizeServerMessage ───────────────────────────────────────────────────

describe('normalizeServerMessage', () => {
  it('空消息返回通用失败提示', () => {
    expect(normalizeServerMessage('')).toBe('请求失败，请稍后重试')
    expect(normalizeServerMessage('   ')).toBe('请求失败，请稍后重试')
    expect(normalizeServerMessage(null as any)).toBe('请求失败，请稍后重试')
  })

  it('Java LocalDateTime / DateTimeParseException 映射为时间格式提示', () => {
    expect(normalizeServerMessage('java.time.LocalDateTime cannot be parsed'))
      .toBe('跟进时间格式不正确，请重新选择时间')
    expect(normalizeServerMessage('DateTimeParseException'))
      .toBe('跟进时间格式不正确，请重新选择时间')
    expect(normalizeServerMessage('nextfollowtime is invalid'))
      .toBe('跟进时间格式不正确，请重新选择时间')
  })

  it('Java UUID 反序列化错误映射为负责人 ID 提示', () => {
    expect(normalizeServerMessage('AssignRequest["assigneeId"] is invalid'))
      .toBe('负责人ID格式不正确，请输入系统中的标准负责人ID')
    expect(normalizeServerMessage('cannot deserialize value of type java.util.UUID'))
      .toBe('负责人ID格式不正确，请输入系统中的标准负责人ID')
    expect(normalizeServerMessage('uuid has to be represented by standard 36-char representation'))
      .toBe('负责人ID格式不正确，请输入36位标准格式的负责人ID')
  })

  it('JSON parse error 映射为格式提示', () => {
    expect(normalizeServerMessage('JSON parse error at position 0'))
      .toBe('提交内容格式不正确，请检查后重试')
  })

  it('普通消息原样返回', () => {
    expect(normalizeServerMessage('服务器内部错误')).toBe('服务器内部错误')
    expect(normalizeServerMessage('权限不足')).toBe('权限不足')
  })
})

// ─── buildFriendlyErrorMessage ────────────────────────────────────────────────

describe('buildFriendlyErrorMessage', () => {
  it('有 server msg 时优先用 normalizeServerMessage 归一化', () => {
    const err = { response: { data: { msg: 'java.time.LocalDateTime error' }, status: 400 } }
    expect(buildFriendlyErrorMessage(err))
      .toBe('跟进时间格式不正确，请重新选择时间')
  })

  it('ECONNABORTED / timeout 关键字返回超时提示', () => {
    expect(buildFriendlyErrorMessage({ message: 'timeout', code: 'ECONNABORTED' }))
      .toBe('请求超时，数据量可能较大，请缩小查询范围后重试')
    expect(buildFriendlyErrorMessage({ message: 'Request timeout exceeded' }))
      .toBe('请求超时，数据量可能较大，请缩小查询范围后重试')
  })

  it('socket hang up / network error 返回网络提示', () => {
    expect(buildFriendlyErrorMessage({ message: 'socket hang up' }))
      .toBe('网络连接异常，请确认服务已启动后重试')
    expect(buildFriendlyErrorMessage({ message: 'Network Error' }))
      .toBe('网络连接异常，请确认服务已启动后重试')
  })

  it('status 401 返回登录失效提示', () => {
    expect(buildFriendlyErrorMessage({ response: { status: 401 } }))
      .toBe('登录已失效，请重新登录')
  })

  it('status 403 返回权限不足提示', () => {
    expect(buildFriendlyErrorMessage({ response: { status: 403 } }))
      .toBe('权限不足，无法执行当前操作')
  })

  it('status 404 返回接口不存在提示', () => {
    expect(buildFriendlyErrorMessage({ response: { status: 404 } }))
      .toBe('接口不存在，请联系管理员确认接口地址')
  })

  it('status >= 500 返回服务不可用提示', () => {
    expect(buildFriendlyErrorMessage({ response: { status: 500 } }))
      .toBe('服务暂时不可用，请稍后重试')
    expect(buildFriendlyErrorMessage({ response: { status: 502 } }))
      .toBe('服务暂时不可用，请稍后重试')
    expect(buildFriendlyErrorMessage({ response: { status: 503 } }))
      .toBe('服务暂时不可用，请稍后重试')
  })

  it('无 status 码时返回发送失败提示', () => {
    expect(buildFriendlyErrorMessage({ message: 'cors error' }))
      .toBe('请求发送失败，请检查网络或服务状态')
  })

  it('无 status 且无 serverMsg 时返回发送失败提示', () => {
    // 没有 response.status 时，!status 为 true，走"请求发送失败"分支
    expect(buildFriendlyErrorMessage({ message: 'unknown error' }))
      .toBe('请求发送失败，请检查网络或服务状态')
  })

  it('无 response 字段时回退到 message/status 判断', () => {
    expect(buildFriendlyErrorMessage({ message: 'timeout' }))
      .toBe('请求超时，数据量可能较大，请缩小查询范围后重试')
  })
})
