/**
 * 订单详情页字段来源标注（DDD-FRONT-001）。
 *
 * 仅前端展示用途，不修改 API 契约；来源口径对齐 OrderQueryService 装配逻辑。
 */
export type OrderDetailSectionKey =
  | 'basic'
  | 'amount'
  | 'attribution'
  | 'promotion'
  | 'product'
  | 'talent'
  | 'sample'

export const ORDER_DETAIL_SECTION_SOURCES: Record<OrderDetailSectionKey, string> = {
  basic: 'colonelsettlement_order（order_id / order_status / create_time / settle_time / update_time）',
  amount:
    'colonelsettlement_order 双轨金额字段（order_amount / settle_amount / estimate_* / effective_*）',
  attribution:
    'colonelsettlement_order.attribution_status + AttributionService 未归因原因翻译',
  promotion:
    'pick_source_mapping + promotion 映射（pick_source / promotion_url / mapping_id）',
  product:
    'colonelsettlement_order + product_snapshot / activity 名称解析',
  talent:
    'extra_data.talent_uid + pick_source_mapping + crawler_talent 昵称回填',
  sample: 'sample_request 关联查询（达人 UID / author_id + 商品 ID）'
}

export function getOrderDetailSectionSource(section: OrderDetailSectionKey): string {
  return ORDER_DETAIL_SECTION_SOURCES[section]
}
