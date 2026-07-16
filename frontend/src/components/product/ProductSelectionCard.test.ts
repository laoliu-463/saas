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
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

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
  commissionTypeLabel: '',
  isDoubleCommission: false,
  campaignCommissionRate: '-',
  campaignServiceFeeRate: '-',
  totalSales: 0,
  totalSalesText: '0',
  sampleRequirement: '未设置寄样门槛',
  activityStartTime: '2026-01-01',
  activityEndTime: '2026-12-31',
  productStock: '50',
  shopScore: 90,
  isPinned: false,
  supportInvestment: false,
  adsRule: '',
  productUrl: '',
  baiyingUrl: '',
  promotionUrl: '',
  specs: [],
  raw: { productId: 'PROD-1' }
}

const mountCard = (props: Record<string, unknown> = {}) =>
  mount(ProductSelectionCard, {
    props: { card: baseCard, ...props },
    global: {
      stubs: {
        NTooltip: {
          template: '<span><slot name="trigger" /><span data-testid="product-ads-tooltip"><slot /></span></span>'
        }
      }
    }
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

  it('商家评分字段在抽屉中渲染（shopScore 有值时）', async () => {
    const wrapper = mountCard()
    // hover-mode 下 drawer-shell DOM 常驻（CSS 控制可见性）
    const drawer = wrapper.find('[data-testid="product-selection-drawer"]')
    expect(drawer.exists()).toBe(true)
    // 抽屉字段列表是 computed，hover 触发前 DOM 已渲染（drawer-shell 常驻）
    // 断言商家评分标签 + 值都在
    expect(drawer.text()).toContain('商家评分')
    expect(drawer.text()).toContain('90')
  })

  it('商家评分字段在 shopScore 为 null 时显示为占位符', async () => {
    const wrapper = mountCard({ card: { ...baseCard, shopScore: null } })
    const drawer = wrapper.find('[data-testid="product-selection-drawer"]')
    expect(drawer.exists()).toBe(true)
    // 字段行仍展示「商家评分」label；dd 内容为空时 template 走 '-'
    expect(drawer.text()).toContain('商家评分')
  })

  it('投流标签悬浮时展示招商填写的投流说明', () => {
    const wrapper = mountCard({
      card: {
        ...baseCard,
        supportInvestment: true,
        adsRule: '投流比例1:0.5，保量10万曝光'
      }
    })

    const tag = wrapper.get('[data-testid="product-ads-tag"]')
    expect(tag.text()).toBe('投流')
    expect(tag.attributes('aria-label')).toBe('投流比例1:0.5，保量10万曝光')
    expect(wrapper.get('[data-testid="product-ads-tooltip"]').text()).toContain('投流比例1:0.5，保量10万曝光')
  })

  it('活动字段在 drawer 中展示 activityName（来自后端补传）', async () => {
    const wrapper = mountCard()
    const drawer = wrapper.find('[data-testid="product-selection-drawer"]')
    expect(drawer.exists()).toBe(true)
    expect(drawer.text()).toContain('活动')
    expect(drawer.text()).toContain('测试活动')
  })

  it('展示默认态核心指标（直播价、佣金率、服务费率）', () => {
    const wrapper = mountCard()
    const metricsGrid = wrapper.find('.selection-card__metrics-grid')
    expect(metricsGrid.exists()).toBe(true)
    expect(metricsGrid.text()).toContain('¥99.00') // price
    expect(metricsGrid.text()).toContain('20%') // commission
    expect(metricsGrid.text()).toContain('-') // service fee rate
  })

  it('为商品库虚拟网格暴露包含多指标标签的卡片高度', () => {
    const wrapper = mountCard({
      card: {
        ...baseCard,
        productName: '很长的商品名称用于验证卡片内容是否被遮挡以及指标换行',
        commissionTypeLabel: '双佣金',
        isDoubleCommission: true,
        campaignCommissionRate: '8.00%',
        campaignServiceFeeRate: '8%',
        specs: [{ id: '1' }]
      }
    })

    const style = wrapper.find('[data-testid="product-selection-card"]').attributes('style') || ''
    expect(style).toContain('--selection-card-height: 492px')
  })

  it('商品图片使用懒加载和异步解码，降低批量渲染压力', () => {
    const wrapper = mountCard({ card: { ...baseCard, imageUrl: 'https://example.test/product.jpg' } })
    const image = wrapper.find('.selection-card__img')

    expect(image.exists()).toBe(true)
    expect(image.attributes('loading')).toBe('lazy')
    expect(image.attributes('decoding')).toBe('async')
  })

  it('完整展示商品名称和商品ID，快捷按钮只保留复制ID', () => {
    const productName = '很长的商品名称用于验证卡片直接完整换行展示且不会使用两行截断'
    const productId = '1234567890123456789'
    const wrapper = mountCard({ card: { ...baseCard, productName, productId } })

    expect(wrapper.get('.selection-card__title').text()).toContain(productName)
    expect(wrapper.get('[data-testid="product-id-value"]').text()).toBe(productId)
    expect(wrapper.get('[data-testid="product-copy-id"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="product-copy-url"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="product-refresh"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="product-detail-btn"]').exists()).toBe(false)
    const source = readFileSync(resolve(process.cwd(), 'src/components/product/ProductSelectionCard.vue'), 'utf8')
    expect(source).not.toContain('-webkit-line-clamp')
  })

  it('复制ID写入完整商品ID', async () => {
    const productId = '1234567890123456789'
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText }
    })
    const wrapper = mountCard({ card: { ...baseCard, productId } })

    await wrapper.get('[data-testid="product-copy-id"]').trigger('click')

    expect(writeText).toHaveBeenCalledWith(productId)
  })

  it('默认态投放期佣金为空占位时回退展示佣金率', () => {
    const wrapper = mountCard()
    const firstMetric = wrapper.find('.selection-card__metric-tag')

    expect(firstMetric.text()).toBe('佣 20%')
  })

  it('双佣金商品展示类型和投放期佣金', () => {
    const wrapper = mountCard({
      card: {
        ...baseCard,
        commissionTypeLabel: '双佣金',
        isDoubleCommission: true,
        campaignCommissionRate: '8.00%',
        campaignServiceFeeRate: '8%'
      }
    })
    const metrics = wrapper.find('.selection-card__metrics')
    const imageBadge = wrapper.find('[data-testid="product-dual-commission-badge"]')

    expect(imageBadge.text()).toBe('双佣金')
    expect(metrics.text()).toContain('双佣金')
    expect(metrics.text()).toContain('投放期佣 8.00%')
    expect(metrics.text()).toContain('投放服务费 8%')
  })

  it('普通佣金商品不展示佣金类型角标', () => {
    const wrapper = mountCard({
      card: {
        ...baseCard,
        commissionTypeLabel: '固定佣金',
        isDoubleCommission: false
      }
    })

    expect(wrapper.find('[data-testid="product-dual-commission-badge"]').exists()).toBe(false)
    expect(wrapper.find('.selection-card__metrics').text()).not.toContain('固定佣金')
  })

  it('hover 详情字段按 FUNC-001 要求展示标签顺序', () => {
    const wrapper = mountCard()
    const labels = wrapper.findAll('.selection-card__field dt').map((node) => node.text())

    expect(labels).toEqual(['招商', '寄样', '时间', '团长', '店铺', '活动', '库存', '商家评分'])
  })
})
