import axios from 'axios'
import { describe, expect, it, beforeEach } from 'vitest'

import {
  applyPermissionHint,
  extractTraceId,
  extractRequestErrorMessage,
  handleApiFailure,
  isPermissionDeniedError,
  isRequestErrorNotified,
  notifyClientPermission,
  resetPermissionHints,
  shouldShowPermissionHint
} from './requestError'
import { globalPermissionHint } from '../stores/permissionHint'

describe('request error helpers', () => {
  beforeEach(() => {
    resetPermissionHints()
    globalPermissionHint.value = ''
  })

  it('extracts trace id from response body fields', () => {
    expect(extractTraceId({ response: { data: { traceId: 'trace-body-001' } } })).toBe('trace-body-001')
    expect(extractTraceId({ data: { requestId: 'request-body-001' } })).toBe('request-body-001')
  })

  it('extracts trace id from response headers', () => {
    expect(extractTraceId({
      response: {
        headers: {
          'x-trace-id': 'trace-header-001'
        }
      }
    })).toBe('trace-header-001')
    expect(extractTraceId({
      headers: {
        'x-request-id': 'request-header-001'
      }
    })).toBe('request-header-001')
  })

  it('returns empty string when trace id is unavailable', () => {
    expect(extractTraceId({ response: { data: { msg: 'failed' }, headers: {} } })).toBe('')
  })

  it('detects permission denied errors from business and http payloads', () => {
    expect(isPermissionDeniedError({ code: 403, msg: '运营仅可查看待发货及后续物流寄样单' })).toBe(true)
    expect(isPermissionDeniedError({ __permissionDenied: true, msg: '无权访问' })).toBe(true)
    expect(isPermissionDeniedError({ response: { status: 403, data: { code: 403 } } })).toBe(true)
    expect(isPermissionDeniedError({ code: 500, msg: 'server error' })).toBe(false)
  })

  it('extracts server error messages with fallback', () => {
    expect(extractRequestErrorMessage({ msg: '无权访问该寄样单' })).toBe('无权访问该寄样单')
    expect(extractRequestErrorMessage({ response: { data: { msg: '仅招商角色可以审核寄样' } } }))
      .toBe('仅招商角色可以审核寄样')
    expect(extractRequestErrorMessage(null, 'fallback')).toBe('fallback')
  })

  it('shows permission hints only once per message', () => {
    expect(shouldShowPermissionHint('无权访问该寄样单')).toBe(true)
    expect(shouldShowPermissionHint('无权访问该寄样单')).toBe(false)
    expect(shouldShowPermissionHint('仅招商角色可以审核寄样')).toBe(true)
  })

  it('applies permission hints without repeating the same message', () => {
    const hints: string[] = []
    const error = { code: 403, msg: '无权访问该寄样单' }

    expect(applyPermissionHint(error, (message) => hints.push(message))).toBe(true)
    expect(applyPermissionHint(error, (message) => hints.push(message))).toBe(true)
    expect(hints).toEqual(['无权访问该寄样单'])
  })

  it('treats permission errors as already notified and skips duplicate toasts', () => {
    expect(isRequestErrorNotified({ code: 403, msg: '运营仅可查看待发货及后续物流寄样单' })).toBe(true)
    expect(isRequestErrorNotified({ response: { status: 403 } })).toBe(true)
    expect(isRequestErrorNotified(new axios.CanceledError('cancelled'))).toBe(true)
    expect(isRequestErrorNotified(null)).toBe(false)
    expect(isRequestErrorNotified(new Error('local failure'))).toBe(false)
  })

  it('routes permission failures to inline hint handlers only', () => {
    const permissionHints: string[] = []
    const fallbackMessages: string[] = []

    handleApiFailure(
      { code: 403, msg: '无权导出寄样数据' },
      {
        onPermissionHint: (message) => permissionHints.push(message),
        onFallback: (message) => fallbackMessages.push(message)
      }
    )

    expect(permissionHints).toEqual(['无权导出寄样数据'])
    expect(fallbackMessages).toEqual([])

    handleApiFailure(
      { code: 403, msg: '无权导出寄样数据' },
      {
        onPermissionHint: (message) => permissionHints.push(message),
        onFallback: (message) => fallbackMessages.push(message)
      }
    )

    expect(permissionHints).toEqual(['无权导出寄样数据'])
  })

  it('sets global permission hint once via notifyClientPermission', () => {
    globalPermissionHint.value = ''
    notifyClientPermission('当前角色无权导出寄样单')
    expect(globalPermissionHint.value).toBe('当前角色无权导出寄样单')
    notifyClientPermission('当前角色无权导出寄样单')
    expect(globalPermissionHint.value).toBe('当前角色无权导出寄样单')
  })
})
