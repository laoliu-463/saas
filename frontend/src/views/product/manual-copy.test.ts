import { describe, expect, it } from 'vitest'

import { resolveManualCopyDialogState } from './manual-copy'

describe('resolveManualCopyDialogState', () => {
  const baseResult = {
    text: '【商品】测试商品\n【链接】https://v.douyin.com/abc/?pick_source=ps_001',
    link: 'https://v.douyin.com/abc/?pick_source=ps_001',
    copied: true,
    linkGenerationFailed: false,
    pickSource: null
  }

  it('returns null when promotion link and clipboard copy are both valid', () => {
    expect(resolveManualCopyDialogState(baseResult, {})).toBeNull()
  })

  it('opens manual copy dialog when clipboard copy fails', () => {
    const state = resolveManualCopyDialogState(
      { ...baseResult, copied: false },
      { baiyingUrl: 'https://buyin.example/manual' }
    )

    expect(state).toMatchObject({
      show: true,
      content: baseResult.text,
      promotionLink: baseResult.link,
      pickSource: 'ps_001',
      reason: 'CLIPBOARD_WRITE_FAILED'
    })
  })

  it('opens baiying fallback when promotion link generation fails', () => {
    const state = resolveManualCopyDialogState(
      {
        ...baseResult,
        text: '【商品】测试商品',
        link: null,
        copied: true,
        linkGenerationFailed: true
      },
      { baiyingUrl: 'https://buyin.example/manual' }
    )

    expect(state).toMatchObject({
      show: true,
      content: '【商品】测试商品',
      promotionLink: null,
      pickSource: null,
      reason: 'PROMOTION_LINK_FAILED',
      baiyingUrl: 'https://buyin.example/manual'
    })
  })

  it('warns when promotion link has no pick_source', () => {
    const state = resolveManualCopyDialogState(
      {
        ...baseResult,
        text: '【商品】测试商品\n【链接】https://v.douyin.com/abc/',
        link: 'https://v.douyin.com/abc/',
        copied: true
      },
      {}
    )

    expect(state).toMatchObject({
      show: true,
      promotionLink: 'https://v.douyin.com/abc/',
      pickSource: null,
      pickSourceWarning: '无法确认归因：链接缺少 pick_source',
      reason: 'PROMOTION_LINK_MISSING_PICK_SOURCE'
    })
  })

  /**
   * ADR-003：百应兜底链必须只在百应语义内部封闭。
   * 禁止把 productUrl / detailUrl / 转链字段 当作百应链接展示 —
   * 否则会出现"商品原链 / 转链"被冒充百应后路的回归。
   */
  it('转链失败时不会用 productUrl 当作百应链接', () => {
    const state = resolveManualCopyDialogState(
      {
        ...baseResult,
        text: '【商品】测试商品',
        link: null,
        copied: true,
        linkGenerationFailed: true
      },
      {
        // 注意：只传 productUrl，不传 baiyingUrl/baiyingLink/buyinUrl
        productUrl: 'https://item.jd.com/origin.html'
      }
    )

    expect(state?.reason).toBe('PROMOTION_LINK_FAILED')
    // productUrl 不得被当作百应链接兜底
    expect(state?.baiyingUrl).toBeNull()
  })

  it('转链失败时，baiyingLink / buyinUrl 仍可作为百应兜底', () => {
    const fromBaiyingLink = resolveManualCopyDialogState(
      {
        ...baseResult,
        text: '【商品】测试商品',
        link: null,
        copied: true,
        linkGenerationFailed: true
      },
      { baiyingLink: 'https://buyin.example/manage-via-baiyingLink' }
    )
    expect(fromBaiyingLink?.baiyingUrl).toBe('https://buyin.example/manage-via-baiyingLink')

    const fromBuyinUrl = resolveManualCopyDialogState(
      {
        ...baseResult,
        text: '【商品】测试商品',
        link: null,
        copied: true,
        linkGenerationFailed: true
      },
      { buyinUrl: 'https://buyin.example/manage-via-buyinUrl' }
    )
    expect(fromBuyinUrl?.baiyingUrl).toBe('https://buyin.example/manage-via-buyinUrl')
  })
})
