import { describe, expect, it } from 'vitest'
import { getProductActions } from './product-actions'
import type { ProductManageRow } from '../../types/productManage'

const keys = (row: ProductManageRow) =>
  getProductActions(row, { roles: ['biz_leader', 'biz_staff'], isAdmin: false }).map((item) => item.key)

describe('product action rules', () => {
  it('shows only approve reject and detail for pending review rows', () => {
    expect(keys({ officialStatus: 'PENDING_REVIEW' })).toEqual(['approve', 'reject', 'detail'])
  })

  it('shows promoting primary actions and more menu actions', () => {
    expect(keys({
      officialStatus: 'PROMOTING',
      publishStatus: 'PUBLISHED',
      hasHandCard: true,
      hasOrders: true,
      baiyingUrl: 'https://buyin.example.test'
    })).toEqual([
      'pause',
      'edit',
      'cooperationSetting',
      'sampleSetting',
      'copyScript',
      'downloadHandCard',
      'detail',
      'copyLink',
      'assign',
      'extendPromotion',
      'viewOrders',
      'openBaiying'
    ])
  })

  it('does not treat local approved rows as promoting when upstream is still pending review', () => {
    expect(keys({
      officialStatus: 'PENDING_REVIEW',
      reviewStatus: 'APPROVED',
      publishStatus: 'PUBLISHED',
      displayStatus: 'DISPLAYING',
      hasHandCard: true,
      hasOrders: true,
      baiyingUrl: 'https://buyin.example.test'
    })).toEqual(['detail'])
  })

  it('uses upstream promoting status instead of local pending review for promotion maintenance', () => {
    expect(keys({
      officialStatus: 'PROMOTING',
      reviewStatus: 'PENDING',
      publishStatus: 'PUBLISHED',
      hasHandCard: true,
      hasOrders: true,
      baiyingUrl: 'https://buyin.example.test'
    })).toEqual([
      'pause',
      'edit',
      'cooperationSetting',
      'sampleSetting',
      'copyScript',
      'downloadHandCard',
      'detail',
      'copyLink',
      'assign',
      'extendPromotion',
      'viewOrders',
      'openBaiying'
    ])

    expect(keys({
      officialStatus: 'PROMOTING',
      reviewStatus: 'APPROVED',
      publishStatus: 'UNPUBLISHED',
      hasHandCard: true,
      hasOrders: true,
      baiyingUrl: 'https://buyin.example.test'
    })).toEqual(['detail'])
  })

  it('shows resume flow for paused publish status', () => {
    expect(keys({ officialStatus: 'PROMOTING', publishStatus: 'PAUSED' })).toEqual([
      'resume',
      'edit',
      'cooperationSetting',
      'sampleSetting',
      'detail'
    ])
  })

  it('hides link and sample actions for rejected rows', () => {
    expect(keys({ officialStatus: 'REJECTED' })).toEqual(['detail'])
    expect(keys({ officialStatus: 'PROMOTING', reviewStatus: 'REJECTED', publishStatus: 'PUBLISHED' })).toEqual(['detail'])
  })

  it('keeps terminated rows read only except orders when available', () => {
    expect(keys({ officialStatus: 'TERMINATED', hasOrders: true })).toEqual(['detail', 'viewOrders'])
  })

  it('keeps expired rows eligible for extension but not link or sample actions', () => {
    expect(keys({ officialStatus: 'EXPIRED', hasOrders: true })).toEqual(['detail', 'extendPromotion', 'viewOrders'])
  })
})
