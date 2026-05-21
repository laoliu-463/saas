import { describe, expect, it } from 'vitest'

import { extractTraceId } from './requestError'

describe('request error helpers', () => {
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
})
