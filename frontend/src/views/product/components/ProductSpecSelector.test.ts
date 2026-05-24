import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ProductSpecSelector from './ProductSpecSelector.vue'

describe('ProductSpecSelector', () => {
  it('maps sku rows to select options', () => {
    const wrapper = mount(ProductSpecSelector, {
      props: {
        modelValue: '',
        skus: [
          { skuId: '1', skuName: '红色/L', priceText: '¥99.00' },
          { skuId: '2', skuName: '蓝色/M', priceText: '¥89.00' }
        ]
      },
      global: {
        stubs: {
          NSelect: {
            props: ['options', 'value'],
            template: '<select data-testid="product-spec-selector"><option v-for="opt in options" :key="opt.value">{{ opt.label }}</option></select>'
          }
        }
      }
    })

    const select = wrapper.get('[data-testid="product-spec-selector"]')
    expect(select.text()).toContain('红色/L（¥99.00）')
    expect(select.text()).toContain('蓝色/M（¥89.00）')
  })
})
