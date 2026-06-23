import { describe, expect, it } from 'vitest'

import { resolveProductSyncNotice } from './product-sync-status'

describe('resolveProductSyncNotice', () => {
  it('returns info notice for already running sync', () => {
    expect(resolveProductSyncNotice({ syncStatus: 'RUNNING' })).toEqual({
      type: 'info',
      message: '商品同步进行中，列表将自动刷新'
    })
  })

  it('returns warning notice for busy sync queue', () => {
    expect(resolveProductSyncNotice({ syncStatus: 'BUSY' })).toEqual({
      type: 'warning',
      message: '商品同步队列繁忙，请稍后重试'
    })
  })

  it('returns success notice for accepted sync', () => {
    expect(resolveProductSyncNotice({ syncStatus: 'ACCEPTED' })).toEqual({
      type: 'success',
      message: '商品同步已开始，列表将自动刷新'
    })
  })

  it('uses backend message when present', () => {
    expect(resolveProductSyncNotice({
      syncStatus: 'BUSY',
      message: '队列已满'
    })).toEqual({
      type: 'warning',
      message: '队列已满'
    })
  })
})
