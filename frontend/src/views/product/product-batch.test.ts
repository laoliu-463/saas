/**
 * 商品批量操作工具 - 单元测试
 *
 * 测试场景：
 * 1. 批量商品 ID 规范化：去重和截断到 100 上限
 * 2. 批量结果消息格式化：成功和失败场景的消息格式
 */
import { describe, expect, it } from 'vitest'

import {
  formatBatchResultMessage,
  MAX_BATCH_PRODUCT_IDS,
  normalizeBatchProductIds
} from './product-batch'

describe('商品批量操作工具（product-batch helpers）', () => {
  it('批量 ID 规范化应去重并截断到 100 上限', () => {
    /* 构造超过上限的重复 ID 列表，验证去重和截断 */
    const ids = Array.from({ length: MAX_BATCH_PRODUCT_IDS + 5 }, (_, index) => String(index % 3))

    expect(normalizeBatchProductIds(ids)).toEqual(['0', '1', '2'])
    /* 验证去重：重复的 9001 只保留一个，空字符串被过滤 */
    expect(normalizeBatchProductIds(['9001', '9001', '9002', ''])).toEqual(['9001', '9002'])
  })

  it('格式化批量结果消息：全成功时显示 X/Y 成功，有失败时附带原因示例', () => {
    /* 全部成功的场景 */
    expect(formatBatchResultMessage({ total: 3, succeeded: 3, failed: 0 }, '批量置顶')).toBe('批量置顶完成：3/3 成功')
    /* 部分失败的场景，应显示第一个失败原因 */
    expect(formatBatchResultMessage({
      total: 2,
      succeeded: 1,
      failed: 1,
      failures: [{ productId: '9002', reason: '状态不允许' }]
    }, '批量加入商品库')).toBe('批量加入商品库完成：成功 1，失败 1（示例：状态不允许）')
  })
})
