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
      message: customMessage || '商品同步已在后台执行，请稍后刷新列表'
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
    message: customMessage || '商品同步已转入后台执行，请稍后刷新列表查看结果'
  }
}
