import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import ProductLibraryFilterPanel from './ProductLibraryFilterPanel.vue'
import { DEFAULT_PRODUCT_FILTERS } from '../product-filters'

describe('ProductLibraryFilterPanel', () => {
  const categoryOptions = [
    { label: '食品饮料', value: '食品饮料' },
    { label: '美妆', value: '美妆' }
  ]

  const globalStubs = {
    NInput: {
      props: ['value'],
      template: '<input :value="value" v-bind="$attrs" @input="$emit(\'update:value\', $event.target.value)" />'
    },
    NButton: {
      props: ['loading'],
      template: '<button v-bind="$attrs"><slot /></button>'
    }
  }

  it('renders library filter sections and basic search fields', () => {
    const wrapper = mount(ProductLibraryFilterPanel, {
      props: {
        filters: DEFAULT_PRODUCT_FILTERS(),
        libraryStatus: null,
        loading: false,
        categoryOptions
      },
      global: { stubs: globalStubs }
    })

    expect(wrapper.find('[data-testid="product-category-filter"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="goods-tag-filter"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="product-tag-filter"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="product-demand-filter"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="product-other-filter"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="product-selected-filters"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="product-basic-search-bar"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="library-basic-product-id"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="library-basic-shop-name"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="library-basic-colonel-name"]').exists()).toBe(true)
  })

  it('updates selected filters immediately when chips are clicked', async () => {
    const wrapper = mount(ProductLibraryFilterPanel, {
      props: {
        filters: DEFAULT_PRODUCT_FILTERS(),
        libraryStatus: null,
        loading: false,
        categoryOptions
      },
      global: { stubs: globalStubs }
    })

    await wrapper.findAll('[data-testid="category-filter-option"]')[0].trigger('click')

    const emitted = wrapper.emitted('update:filters')?.[0]?.[0]
    expect(emitted).toMatchObject({ categories: ['食品饮料'] })
    expect(wrapper.emitted('search-click')).toBeFalsy()
  })

  it('emits search and reset from the basic search bar', async () => {
    const wrapper = mount(ProductLibraryFilterPanel, {
      props: {
        filters: DEFAULT_PRODUCT_FILTERS(),
        libraryStatus: null,
        loading: false,
        categoryOptions
      },
      global: { stubs: globalStubs }
    })

    await wrapper.find('[data-testid="library-basic-search"]').trigger('click')
    await wrapper.find('[data-testid="library-basic-reset"]').trigger('click')

    expect(wrapper.emitted('search-click')).toHaveLength(1)
    expect(wrapper.emitted('reset')).toHaveLength(1)
  })
})
