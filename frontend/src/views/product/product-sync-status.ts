export type ProductSyncNoticeType = 'success' | 'info' | 'warning'

export interface ProductSyncNotice {
  type: ProductSyncNoticeType
  message: string
}

export function resolveProductSyncNotice(payload: Record<string, unknown> = {}): ProductSyncNotice {
  const syncStatus = typeof payload.syncStatus === 'string' ? payload.syncStatus : ''
  const customMessage = typeof payload.message === 'string' ? payload.message : ''

  if (syncStatus === 'RUNNING') {
    return {
      type: 'info',
      message: '商品同步进行中，列表将自动刷新'
    }
  }
  if (syncStatus === 'BUSY') {
    return {
      type: 'warning',
      message: customMessage || '商品同步队列繁忙，请稍后重试'
    }
  }
  return {
    type: 'success',
    message: '商品同步已开始，列表将自动刷新'
  }
}
