import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProductManageFilters from './ProductManageFilters.vue'
import { DEFAULT_PRODUCT_FILTERS } from '../product-filters'

const stubs = {
  NSpace: { template: '<div><slot /></div>' },
  NInput: { template: '<div v-bind="$attrs"><slot /></div>' },
  NSelect: { template: '<div v-bind="$attrs"><slot /></div>' },
  NInputGroup: { template: '<div><slot /></div>' },
  NButton: { emits: ['click'], template: '<button v-bind="$attrs" @click="$emit(\'click\', $event)"><slot /></button>' }
}

const mountFilters = () => mount(ProductManageFilters, {
  props: {
    filters: DEFAULT_PRODUCT_FILTERS(),
    loading: false,
    assignedActivityOptions: [{ label: '春季招商', value: 'ACT001' }],
    assignedActivityOptionsLoading: false
  },
  global: { stubs }
})

describe('ProductManageFilters', () => {
  it('only renders the four required filters for the recruit team (商品ID/商品名称/活动信息/招商)', () => {
    const wrapper = mountFilters()
    expect(wrapper.find('[data-testid="filter-product-id"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="filter-product-name"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="filter-assigned-activity"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="filter-assignee"]').exists()).toBe(true)
    // 已移除的筛选项
    expect(wrapper.find('[data-testid="filter-cooperation-type"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="filter-shop-name"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="filter-merchant-status"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="filter-product-mechanism"]').exists()).toBe(false)
  })

  it('emits search and reset actions', async () => {
    const wrapper = mountFilters()
    await wrapper.find('[data-testid="filter-search"]').trigger('click')
    await wrapper.find('[data-testid="filter-reset"]').trigger('click')
    expect(wrapper.emitted('search-click')).toBeTruthy()
    expect(wrapper.emitted('reset')).toBeTruthy()
  })

  it('hides the assignee filter when showAssigneeFilter is false', () => {
    const wrapper = mount(ProductManageFilters, {
      props: {
        filters: DEFAULT_PRODUCT_FILTERS(),
        loading: false,
        showAssigneeFilter: false,
        assignedActivityOptions: [],
        assignedActivityOptionsLoading: false
      },
      global: { stubs }
    })
    expect(wrapper.find('[data-testid="filter-assignee"]').exists()).toBe(false)
  })
})
