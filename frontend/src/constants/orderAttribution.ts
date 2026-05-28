/**
 * 订单归因常量模块
 *
 * 职责：
 * - 定义订单归因状态的中文文案映射（已确认业绩、待排查、部分归因等）
 * - 定义归因失败原因的中文文案映射（未携带推广参数、映射未找到等）
 * - 定义归因失败原因的操作建议文案（供排查指引使用）
 * - 提供归因状态/原因的查询工具函数
 *
 * 业务背景：
 * 订单归因是将抖音订单与推广链接、达人、商品、活动等关联的过程。
 * 归因失败时需要明确原因和操作建议，帮助运营人员快速定位问题。
 */

/** 归因状态文案映射：归因状态码 -> 中文显示文案 */
export const attributionStatusTextMap: Record<string, string> = {
  ATTRIBUTED: '已确认业绩',       // 归因成功，已确认业绩归属
  UNATTRIBUTED: '待排查订单',     // 未归因，需要排查原因
  PARTIAL: '部分归因',           // 部分字段归因成功，部分缺失
  FAILED: '同步失败'            // 订单同步阶段失败
}

/**
 * 归因失败原因文案映射：原因码 -> 中文显示文案
 * 包含标准原因码和后端可能返回的中文原始描述（兼容处理）
 */
export const attributionReasonTextMap: Record<string, string> = {
  ATTRIBUTED: '已确认业绩',
  NO_PICK_SOURCE: '订单未携带推广参数',           // 订单中缺少 pick_source 推广参数
  MAPPING_NOT_FOUND: '未找到对应推广链接',         // pick_source 在映射表中未找到
  COLONEL_MAPPING_NOT_FOUND: '原生团长订单未找到归因映射', // 原生团长字段无对应映射
  COLONEL_MAPPING_AMBIGUOUS: '原生团长订单命中多条归因映射', // 原生团长映射存在冲突
  TALENT_CLAIM_OWNER_CONFLICT: '归因负责人和达人认领人不一致', // 达人认领关系与归因负责人冲突
  PRODUCT_NOT_FOUND: '未匹配到本地商品',           // 订单商品在本地商品库中不存在
  ACTIVITY_NOT_FOUND: '商品未关联活动',            // 商品未绑定到任何活动
  CHANNEL_NOT_FOUND: '未匹配到渠道负责人',          // 渠道负责人未分配
  SYNC_FAILED: '订单同步失败',                    // 同步接口调用失败
  // 兼容后端可能直接返回的中文原始描述
  'pick_source 未匹配到有效归因映射': '未找到对应推广链接',
  '订单未携带推广参数': '订单未携带推广参数',
  '订单同步失败': '订单同步失败'
}

/**
 * 归因失败原因的操作建议映射：原因码 -> 排查指引文案
 * 帮助运营人员根据失败原因快速定位和解决问题
 */
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
  // 兼容后端可能直接返回的中文原始描述
  'pick_source 未匹配到有效归因映射': '请检查该 pick_source 是否由系统转链生成，或是否未成功落库。',
  '订单未携带推广参数': '请确认达人是否使用系统生成的推广链接。',
  '订单同步失败': '请查看订单同步日志和第三方回流结果。'
}

/**
 * 归因原因下拉选项列表
 * 从 attributionReasonTextMap 生成，排除 ATTRIBUTED（已确认业绩不需要作为筛选选项）
 * 格式：[{ label: '中文文案', value: '原因码' }, ...]
 */
export const attributionReasonOptions = Object.entries(attributionReasonTextMap)
  .filter(([code]) => code !== 'ATTRIBUTED')
  .map(([value, label]) => ({ label, value }))

/**
 * 获取归因失败原因的中文文案
 *
 * @param reason - 原因码（可能为 null/undefined）
 * @returns 中文文案，未知原因码返回原样显示，空值返回 '-'
 */
export function getAttributionReasonText(reason?: string | null) {
  if (!reason) return '-'
  return attributionReasonTextMap[reason] || reason
}

/**
 * 获取归因失败原因的操作建议
 *
 * @param reason - 原因码（可能为 null/undefined）
 * @returns 排查建议文案，未知原因码返回通用建议
 */
export function getAttributionReasonSuggestion(reason?: string | null) {
  if (!reason) return '请检查商品转链、达人渠道和订单回流情况。'
  return attributionReasonSuggestionMap[reason] || '请检查商品转链、达人渠道和订单回流情况。'
}

/**
 * 获取归因状态的中文文案
 *
 * @param status - 状态码（可能为 null/undefined）
 * @returns 中文文案，未知状态码返回原样显示，空值返回 '-'
 */
export function getAttributionStatusText(status?: string | null) {
  if (!status) return '-'
  return attributionStatusTextMap[status] || status
}
