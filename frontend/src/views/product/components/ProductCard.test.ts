import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import ProductCard from './ProductCard.vue'

vi.mock('../../../api/activityProduct', () => ({
  getActivityProductDetail: vi.fn().mockResolvedValue({ data: {} })
}))

const naiveStubs = {
  NButton: {
    template: '<button v-bind="$attrs" @click="$emit(\'click\', $event)"><slot /></button>'
  },
  NTag: {
    template: '<span v-bind="$attrs"><slot /></span>'
  },
  NSpace: {
    template: '<div><slot /></div>'
  },
  NGrid: {
    template: '<div><slot /></div>'
  },
  NGi: {
    template: '<div><slot /></div>'
  },
  NSpin: {
    template: '<div><slot /></div>'
  },
  NModal: {
    template: '<div v-if="$attrs.show"><slot /></div>'
  }
}

const baseProps = {
  product: {
    productId: 'PROD-1',
    activityId: 'ACT-1',
    title: '测试商品',
    shopName: '测试店铺',
    bizStatus: 'APPROVED',
    selectedToLibrary: true,
    libraryVisible: true,
    displayStatus: 'DISPLAYING',
    priceText: '¥99.00',
    activityCosRatioText: '20%',
    estimatedServiceFee: '19.80'
  },
  expanded: true,
  canAudit: false,
  canAssign: false,
  canAssignAuditOwner: false,
  pickMode: false,
  libraryMode: true,
  canPutIntoLibrary: false,
  canCopyLink: true,
  canApplySample: true,
  canPin: true
}

describe('ProductCard pin action', () => {
  it('labels product sample action as an internal sample request', async () => {
    const wrapper = mount(ProductCard, {
      props: baseProps,
      global: { stubs: naiveStubs }
    })

    const button = wrapper.get('[data-testid="product-quick-sample"]')
    expect(button.text()).toContain('快速寄样')

    await button.trigger('click')

    expect(wrapper.emitted('applySample')?.[0]).toEqual([baseProps.product])
  })

  it('emits pin when an unpinned product is pinned from the quick view', async () => {
    const wrapper = mount(ProductCard, {
      props: baseProps,
      global: { stubs: naiveStubs }
    })

    await wrapper.get('[data-testid="product-pin-button"]').trigger('click')

    expect(wrapper.emitted('pin')?.[0]).toEqual([baseProps.product])
  })

  it('emits unpin when a pinned product is unpinned from the quick view', async () => {
    const product = { ...baseProps.product, pinned: true }
    const wrapper = mount(ProductCard, {
      props: { ...baseProps, product },
      global: { stubs: naiveStubs }
    })

    await wrapper.get('[data-testid="product-unpin-button"]').trigger('click')

    expect(wrapper.emitted('unpin')?.[0]).toEqual([product])
  })

  it('shows ready-after-entry tag for promoting products in manage mode', () => {
    const product = {
      ...baseProps.product,
      selectedToLibrary: false,
      status: 1,
      statusText: '推广中',
      promotionEndTime: '2026-12-31'
    }
    const wrapper = mount(ProductCard, {
      props: { ...baseProps, product, libraryMode: false },
      global: { stubs: naiveStubs }
    })

    expect(wrapper.text()).toContain('审核入库后可展示')
  })

  it('shows hidden-from-library warning tag when product is stored but not list-visible', () => {
    const product = {
      ...baseProps.product,
      selectedToLibrary: true,
      libraryVisible: false,
      displayStatus: 'HIDDEN',
      hiddenReason: 'NOT_ELIGIBLE',
      statusText: '申请未通过'
    }
    const wrapper = mount(ProductCard, {
      props: { ...baseProps, product },
      global: { stubs: naiveStubs }
    })

    expect(wrapper.text()).toContain('已入库·列表不可见')
  })

  it('marks a double commission product that does not support ads', () => {
    const product = {
      ...baseProps.product,
      cosType: 1,
      supportsAds: false
    }
    const wrapper = mount(ProductCard, {
      props: { ...baseProps, product },
      global: { stubs: naiveStubs }
    })

    expect(wrapper.get('[data-testid="product-fake-double-commission"]').text()).toBe('不支持投流')
  })

  it('opens ads rule modal when the ads tag is clicked', async () => {
    const product = {
      ...baseProps.product,
      supportsAds: true,
      adsRule: '投流比例1:0.5，保量10万曝光'
    }
    const wrapper = mount(ProductCard, {
      props: { ...baseProps, product },
      global: {
        stubs: {
          ...naiveStubs,
          AdsRuleDetailModal: {
            props: ['show', 'ruleText'],
            template: '<div v-if="show" data-testid="ads-rule-detail-modal">{{ ruleText }}</div>'
          }
        }
      }
    })

    await wrapper.get('[data-testid="product-ads-rule-tag"]').trigger('click')

    expect(wrapper.find('[data-testid="ads-rule-detail-modal"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="ads-rule-detail-modal"]').text()).toContain('投流比例1:0.5')
  })

  it('shows empty rule fallback when adsRule is missing', async () => {
    const product = { ...baseProps.product, supportsAds: true }
    const wrapper = mount(ProductCard, {
      props: { ...baseProps, product },
      global: {
        stubs: {
          ...naiveStubs,
          AdsRuleDetailModal: {
            props: ['show', 'ruleText', 'loading'],
            template: '<section v-if="show" data-testid="ads-rule-detail-modal"><span data-testid="ads-rule-empty" v-if="!ruleText">暂无投流规则</span><span v-else>{{ ruleText }}</span></section>'
          }
        }
      }
    })

    await wrapper.get('[data-testid="product-ads-rule-tag"]').trigger('click')
    await wrapper.vm.$nextTick()

    expect(wrapper.find('[data-testid="ads-rule-empty"]').text()).toBe('暂无投流规则')
  })
})
