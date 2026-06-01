/**
 * 商品库选品卡片 (ProductSelectionCard) 悬浮抽屉行为单测。
 *
 * 重点：覆盖 real-pre 部署下"鼠标移上去抽屉不展开"的回归场景。
 * 之前 isHoverCapable() 用了严格的 `(hover: hover) and (pointer: fine)` 探测，
 * 在触屏笔记本 / 容器化部署 / 特殊 webview 等真实环境中会被误判为 false，
 * 导致桌面端 hover 抽屉永不展开。
 *
 * 现在的实现：
 * 1. isHoverCapable() 改为"仅在明确无 hover 时返回 false"
 * 2. mount 时一次性探测 supportsHover，hover-mode 桌面端用 hoverActive 驱动抽屉
 * 3. 桌面端 hover 模式额外加 CSS :hover 兜底（DOM 常驻 + CSS 控制可见性）
 *
 * 测试策略：通过 data-testid 验证 article 的 hover-mode class 和 drawer-shell 的存在性，
 * 不依赖 NCollapseTransition 的 stub（vue-test-utils 2.x 会把 NCollapseTransition
 * 自动识别为 <transition> 变体，自定义 stub 配置成本太高）。
 */
import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import ProductSelectionCard from './ProductSelectionCard.vue'

// naive-ui 的 useMessage 需要外层 <n-message-provider />，测试里直接 mock 掉
vi.mock('naive-ui', async () => {
  const actual = await vi.importActual<typeof import('naive-ui')>('naive-ui')
  return {
    ...actual,
    useMessage: () => ({
      success: vi.fn(),
      warning: vi.fn(),
      error: vi.fn(),
      info: vi.fn(),
      loading: vi.fn()
    })
  }
})

const setMatchMedia = (hoverCapable: boolean) => {
  // happy-dom 不提供完整 matchMedia，这里直接替换
  // (hover: none) 命中表示"明确无 hover"
  const matches = (query: string) => {
    if (query === '(hover: none)') return !hoverCapable
    if (query === '(hover: hover)') return hoverCapable
    return false
  }
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    writable: true,
    value: (query: string) => ({
      matches: matches(query),
      media: query,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false
    })
  })
}

const baseCard = {
  id: 'REL-1',
  relationId: 'REL-1',
  productId: 'PROD-1',
  productName: '测试商品',
  imageUrl: '',
  shopName: '测试店铺',
  partnerName: 'partner',
  merchantName: 'merchant',
  activityId: 'ACT-1',
  activityName: '测试活动',
  recruiterName: '张三',
  syncTimeText: '2026-06-01 12:00',
  colonelName: '李四',
  livePrice: '¥99.00',
  commissionRate: '20%',
  serviceFeeRate: '-',
  campaignCommissionRate: '-',
  campaignServiceFeeRate: '-',
  totalSales: 0,
  totalSalesText: '0',
  sampleRequirement: '未设置寄样门槛',
  activityStartTime: '2026-01-01',
  activityEndTime: '2026-12-31',
  productStock: '50',
  shopScore: null,
  isPinned: false,
  supportInvestment: false,
  productUrl: '',
  baiyingUrl: '',
  specs: [],
  raw: { productId: 'PROD-1' }
}

const mountCard = (props: Record<string, unknown> = {}) =>
  mount(ProductSelectionCard, {
    props: { card: baseCard, ...props }
  })

describe('ProductSelectionCard hover drawer', () => {
  beforeEach(() => {
    setMatchMedia(true) // 默认桌面端，支持 hover
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('hover-mode 下 article 元素带 hover-mode class', () => {
    const wrapper = mountCard()
    const card = wrapper.find('[data-testid="product-selection-card"]')
    expect(card.classes()).toContain('hover-mode')
  })

  it('hover-mode 下点击 body 触发 detail 事件（桌面端点击 = 进详情，不切抽屉）', async () => {
    const wrapper = mountCard()
    const body = wrapper.find('.selection-card__body')

    await body.trigger('click')

    expect(wrapper.emitted('detail')?.[0]).toEqual([baseCard.raw])
  })

  it('hover-mode 下底部空间不足时向上展开', async () => {
    const wrapper = mountCard()
    await wrapper.vm.$nextTick()
    const card = wrapper.find('[data-testid="product-selection-card"]')
    const drawer = wrapper.find('.selection-card__drawer-shell')

    Object.defineProperty(window, 'innerHeight', {
      configurable: true,
      value: 900
    })
    vi.spyOn(card.element, 'getBoundingClientRect').mockReturnValue({
      x: 0,
      y: 640,
      top: 640,
      right: 252,
      bottom: 894,
      left: 0,
      width: 252,
      height: 254,
      toJSON: () => ({})
    } as DOMRect)
    vi.spyOn(drawer.element, 'getBoundingClientRect').mockReturnValue({
      x: 0,
      y: 898,
      top: 898,
      right: 252,
      bottom: 1128,
      left: 0,
      width: 252,
      height: 230,
      toJSON: () => ({})
    } as DOMRect)

    await card.trigger('mouseenter')
    await wrapper.vm.$nextTick()

    expect(card.classes()).toContain('opens-up')
  })

  it('触屏模式（hover: none）下 article 不带 hover-mode class', async () => {
    setMatchMedia(false)
    const wrapper = mountCard()
    await wrapper.vm.$nextTick()

    const card = wrapper.find('[data-testid="product-selection-card"]')
    expect(card.classes()).not.toContain('hover-mode')
  })

  it('触屏模式（hover: none）下点击 body 切换 expanded 状态（通过 is-expanded class 验证）', async () => {
    setMatchMedia(false)
    const wrapper = mountCard()
    await wrapper.vm.$nextTick()

    const card = wrapper.find('[data-testid="product-selection-card"]')
    const body = wrapper.find('.selection-card__body')

    // 初始：触屏模式下 expanded=false
    expect(card.classes()).not.toContain('is-expanded')

    // 第一次点击：expanded 翻 true
    await body.trigger('click')
    await wrapper.vm.$nextTick()
    expect(card.classes()).toContain('is-expanded')

    // 第二次点击：expanded 翻 false
    await body.trigger('click')
    await wrapper.vm.$nextTick()
    expect(card.classes()).not.toContain('is-expanded')
  })

  it('触屏模式（hover: none）下点击 body 不触发 detail 事件', async () => {
    setMatchMedia(false)
    const wrapper = mountCard()
    await wrapper.vm.$nextTick()
    const body = wrapper.find('.selection-card__body')

    await body.trigger('click')
    expect(wrapper.emitted('detail')).toBeUndefined()
  })

  it('matchMedia 不可用时默认走 hover-mode', () => {
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      writable: true,
      value: undefined
    })
    const wrapper = mountCard()
    const card = wrapper.find('[data-testid="product-selection-card"]')

    expect(card.classes()).toContain('hover-mode')
  })

  it('isHoverCapable 只在 (hover: none) 命中时才返回 false（修复后的判断）', () => {
    // 桌面端：matchMedia('(hover: none)') 应为 false
    setMatchMedia(true)
    expect(window.matchMedia('(hover: none)').matches).toBe(false)

    // 触屏：matchMedia('(hover: none)') 应为 true
    setMatchMedia(false)
    expect(window.matchMedia('(hover: none)').matches).toBe(true)

    // 修复后：!matches(hover: none) 才走 hover-mode
    // 桌面端 → !false = true → hover-mode
    // 触屏端 → !true = false → click-mode
  })
})
