export const attributionStatusTextMap: Record<string, string> = {
  ATTRIBUTED: '已确认业绩',
  UNATTRIBUTED: '待排查订单',
  PARTIAL: '部分归因',
  FAILED: '同步失败'
}

export const attributionReasonTextMap: Record<string, string> = {
  ATTRIBUTED: '已确认业绩',
  NO_PICK_SOURCE: '订单未携带推广参数',
  MAPPING_NOT_FOUND: '未找到对应推广链接',
  COLONEL_MAPPING_NOT_FOUND: '原生团长订单未找到归因映射',
  COLONEL_MAPPING_AMBIGUOUS: '原生团长订单命中多条归因映射',
  TALENT_CLAIM_OWNER_CONFLICT: '归因负责人和达人认领人不一致',
  PRODUCT_NOT_FOUND: '未匹配到本地商品',
  ACTIVITY_NOT_FOUND: '商品未关联活动',
  CHANNEL_NOT_FOUND: '未匹配到渠道负责人',
  SYNC_FAILED: '订单同步失败',
  'pick_source 未匹配到有效归因映射': '未找到对应推广链接',
  '订单未携带推广参数': '订单未携带推广参数',
  '订单同步失败': '订单同步失败'
}

export const attributionReasonSuggestionMap: Record<string, string> = {
  NO_PICK_SOURCE: '请确认达人是否使用系统生成的推广链接。',
  MAPPING_NOT_FOUND: '请检查该 pick_source 是否由系统转链生成，或是否未成功落库。',
  COLONEL_MAPPING_NOT_FOUND: '请检查原生团长字段对应的活动、商品和负责人映射是否已落库。',
  COLONEL_MAPPING_AMBIGUOUS: '请清理重复的原生团长映射，确保同一活动商品只命中一个负责人。',
  TALENT_CLAIM_OWNER_CONFLICT: '请核对该达人当前有效认领记录和推广映射负责人，必要时重新认领或重建转链映射。',
  PRODUCT_NOT_FOUND: '请检查商品主链路是否已经同步入库。',
  ACTIVITY_NOT_FOUND: '请检查商品活动绑定关系是否完整。',
  CHANNEL_NOT_FOUND: '请检查渠道负责人是否已完成分配。',
  SYNC_FAILED: '请查看订单同步日志和第三方回流结果。',
  'pick_source 未匹配到有效归因映射': '请检查该 pick_source 是否由系统转链生成，或是否未成功落库。',
  '订单未携带推广参数': '请确认达人是否使用系统生成的推广链接。',
  '订单同步失败': '请查看订单同步日志和第三方回流结果。'
}

export const attributionReasonOptions = Object.entries(attributionReasonTextMap)
  .filter(([code]) => code !== 'ATTRIBUTED')
  .map(([value, label]) => ({ label, value }))

export function getAttributionReasonText(reason?: string | null) {
  if (!reason) return '-'
  return attributionReasonTextMap[reason] || reason
}

export function getAttributionReasonSuggestion(reason?: string | null) {
  if (!reason) return '请检查商品转链、达人渠道和订单回流情况。'
  return attributionReasonSuggestionMap[reason] || '请检查商品转链、达人渠道和订单回流情况。'
}

export function getAttributionStatusText(status?: string | null) {
  if (!status) return '-'
  return attributionStatusTextMap[status] || status
}
