import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import ProductFilters from './ProductFilters.vue'
import { DEFAULT_PRODUCT_FILTERS } from '../product-filters'

describe('ProductFilters library mode', () => {
  const baseProps = {
    filters: DEFAULT_PRODUCT_FILTERS(),
    selectedProduct: null,
    status: null,
    libraryStatus: null,
    productOptions: [],
    productOptionsLoading: false,
    loading: false,
    mode: 'library' as const,
    libraryCategoryOptions: [
      { label: '食品饮料', value: '食品饮料' },
      { label: '美妆', value: '美妆' }
    ],
    recruiterOptions: [{ label: '招商A', value: 'uuid-a' }]
  }

  const globalStubs = {
    NSpace: { template: '<div><slot /></div>' },
    NSelect: { template: '<select />' },
    NInput: { template: '<input />' },
    NButton: { template: '<button><slot /></button>' },
    NCheckbox: { template: '<input type="checkbox" />' }
  }

  it('renders dynamic category and P1 filter controls', () => {
    const wrapper = mount(ProductFilters, {
      props: baseProps,
      global: { stubs: globalStubs }
    })

    expect(wrapper.find('[data-testid="filter-library-categories"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="filter-colonel-name"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="filter-published"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="filter-listed"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="filter-free-sample"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="filter-material-download"]').exists()).toBe(true)
  })

  it('emits reset when reset button clicked with empty category options', async () => {
    const wrapper = mount(ProductFilters, {
      props: { ...baseProps, libraryCategoryOptions: [] },
      global: {
        stubs: {
          ...globalStubs,
          NButton: { template: '<button @click="$emit(\'click\')"><slot /></button>' }
        }
      }
    })

    const buttons = wrapper.findAll('button')
    await buttons[buttons.length - 1]?.trigger('click')
    expect(wrapper.emitted('reset')).toBeTruthy()
  })
})
