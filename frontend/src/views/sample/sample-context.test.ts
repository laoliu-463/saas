import { describe, expect, it } from 'vitest'

import {
  buildProductSampleContext,
  buildSampleRemark,
  buildTalentSampleContext,
  isMainlandMobile,
  mergeLockedOption
} from './sample-context'

describe('sample context helpers', () => {
  it('builds a locked product context with the internal product id first', () => {
    const context = buildProductSampleContext({
      id: 'product-record-001',
      productId: 'douyin-product-888',
      title: '夏季爆款水杯',
      shopName: '清风小店'
    })

    expect(context.query).toMatchObject({
      productId: 'product-record-001',
      productLabel: '夏季爆款水杯'
    })
    expect(context.option).toEqual({
      label: '夏季爆款水杯',
      value: 'product-record-001'
    })
  })

  it('falls back to external product id when no internal id is available', () => {
    const context = buildProductSampleContext({
      productId: 'douyin-product-888',
      name: '候选商品'
    })

    expect(context.query.productId).toBe('douyin-product-888')
    expect(context.option?.label).toBe('候选商品')
  })

  it('builds talent context from detail claim shipping fields', () => {
    const context = buildTalentSampleContext({
      talent: {
        id: 'talent-record-001',
        douyinUid: 'dy-uid-001',
        nickname: '测品达人',
        fansCount: 52000,
        creditScore: 4.8,
        mainCategory: '家居'
      },
      claim: {
        recipientName: '张三',
        recipientPhone: '13800138000',
        recipientAddress: '上海市浦东新区测试路 1 号'
      }
    })

    expect(context.query).toMatchObject({
      talentId: 'dy-uid-001',
      talentNickname: '测品达人',
      talentFansCount: '52000',
      talentCreditScore: '4.8',
      talentMainCategory: '家居',
      receiverName: '张三',
      receiverPhone: '13800138000',
      receiverAddress: '上海市浦东新区测试路 1 号'
    })
    expect(context.talentRow).toMatchObject({
      talentId: 'dy-uid-001',
      nickname: '测品达人',
      fansCount: 52000
    })
  })

  it('keeps the locked product option visible even when remote results do not include it', () => {
    const options = mergeLockedOption(
      [{ label: '远程商品', value: 'remote-001' }],
      { label: '锁定商品', value: 'locked-001' }
    )

    expect(options).toEqual([
      { label: '锁定商品', value: 'locked-001' },
      { label: '远程商品', value: 'remote-001' }
    ])
  })

  it('validates mainland mobile numbers and formats receiver remark lines', () => {
    expect(isMainlandMobile('13800138000')).toBe(true)
    expect(isMainlandMobile('23800138000')).toBe(false)

    expect(buildSampleRemark({
      reason: '短视频测品',
      receiverName: '张三',
      receiverPhone: '13800138000',
      receiverAddress: '上海市浦东新区测试路 1 号',
      extraRemark: '请优先顺丰'
    })).toBe([
      '申请理由：短视频测品',
      '收货人：张三',
      '手机号：13800138000',
      '地址：上海市浦东新区测试路 1 号',
      '补充说明：请优先顺丰'
    ].join('\n'))
  })
})
