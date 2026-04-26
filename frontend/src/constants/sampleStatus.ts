export const sampleStatusTextMap: Record<string, string> = {
  PENDING_AUDIT: '待审核',
  PENDING_SHIP: '待发货',
  SHIPPED: '快递中',
  PENDING_TASK: '待交作业',
  FINISHED: '已完成',
  REJECTED: '已拒绝',
  CLOSED: '已关闭'
}

export const sampleStatusTypeMap: Record<string, 'default' | 'warning' | 'info' | 'success' | 'error'> = {
  PENDING_AUDIT: 'warning',
  PENDING_SHIP: 'info',
  SHIPPED: 'success',
  PENDING_TASK: 'info',
  FINISHED: 'success',
  REJECTED: 'error',
  CLOSED: 'default'
}

export function getSampleStatusText(status?: string | null) {
  return sampleStatusTextMap[String(status || '')] || String(status || '-')
}

export function getSampleStatusType(status?: string | null) {
  return sampleStatusTypeMap[String(status || '')] || 'default'
}
