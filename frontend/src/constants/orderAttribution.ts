export const attributionStatusTextMap: Record<string, string> = {
  ATTRIBUTED: '已确认业绩',
  UNATTRIBUTED: '待排查',
  PARTIAL: '部分归因',
  FAILED: '同步/归因失败'
}

export const attributionReasonTextMap: Record<string, string> = {
  ATTRIBUTED: '已确认业绩',
  NO_PICK_SOURCE: '订单未携带推广参数',
  MAPPING_NOT_FOUND: '未找到对应推广链接',
  PRODUCT_NOT_FOUND: '未匹配到本地商品库',
  ACTIVITY_NOT_FOUND: '商品未关联活动',
  CHANNEL_NOT_FOUND: '未匹配到渠道负责人',
  SYNC_FAILED: '订单同步失败',
  'pick_source 未匹配到有效归因映射': '未找到对应推广链接',
  '订单未携带推广参数': '订单未携带推广参数',
  '订单同步失败': '订单同步失败'
}

export const attributionReasonSuggestionMap: Record<string, string> = {
  NO_PICK_SOURCE: '检查订单是否来自有效推广链路',
  MAPPING_NOT_FOUND: '检查转链记录和 pick_source 映射是否写入成功',
  PRODUCT_NOT_FOUND: '检查商品主链路是否已同步入库',
  ACTIVITY_NOT_FOUND: '检查商品状态和活动绑定信息',
  CHANNEL_NOT_FOUND: '检查渠道负责人是否已完成分配',
  SYNC_FAILED: '查看同步日志和第三方回流结果',
  'pick_source 未匹配到有效归因映射': '检查转链记录和 pick_source 映射是否写入成功',
  '订单未携带推广参数': '检查订单是否来自有效推广链路',
  '订单同步失败': '查看同步日志和第三方回流结果'
}

export const attributionReasonOptions = Object.entries(attributionReasonTextMap)
  .filter(([code]) => code !== 'ATTRIBUTED')
  .map(([value, label]) => ({ label, value }))

export function getAttributionReasonText(reason?: string | null) {
  if (!reason) return '-'
  return attributionReasonTextMap[reason] || reason
}

export function getAttributionReasonSuggestion(reason?: string | null) {
  if (!reason) return '请检查该商品转链情况或达人渠道'
  return attributionReasonSuggestionMap[reason] || '请检查该商品转链情况或达人渠道'
}

export function getAttributionStatusText(status?: string | null) {
  if (!status) return '-'
  return attributionStatusTextMap[status] || status
}
