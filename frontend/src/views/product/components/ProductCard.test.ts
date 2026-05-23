import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ProductCard from './ProductCard.vue'

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
    expect(button.text()).toContain('内部寄样')

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
})
