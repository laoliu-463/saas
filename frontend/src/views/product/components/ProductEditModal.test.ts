import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ProductEditModal from './ProductEditModal.vue'

vi.mock('../../../api/productManage', () => ({
  updateProduct: vi.fn()
}))

vi.mock('naive-ui', async (importOriginal) => {
  const actual = await importOriginal<typeof import('naive-ui')>()
  return {
    ...actual,
    useMessage: () => ({
      success: vi.fn(),
      warning: vi.fn()
    })
  }
})

describe('ProductEditModal', () => {
  it('renders as a right-side drawer instead of a centered modal', () => {
    const wrapper = mount(ProductEditModal, {
      props: {
        show: true,
        row: null
      },
      global: {
        stubs: {
          NDrawer: {
            props: ['show', 'width', 'placement'],
            template: '<aside data-testid="drawer" :data-width="width" :data-placement="placement"><slot /></aside>'
          },
          NDrawerContent: {
            template: '<section><slot name="header" /><slot /><slot name="footer" /></section>'
          },
          NForm: { template: '<form><slot /></form>' },
          NFormItem: { template: '<div><slot /></div>' },
          NSelect: { template: '<div />' },
          NInput: { template: '<div />' },
          NCheckbox: { template: '<div />' },
          NSpace: { template: '<div><slot /></div>' },
          NButton: { template: '<button><slot /></button>' }
        }
      }
    })

    const drawer = wrapper.get('[data-testid="product-edit-drawer"]')
    expect(drawer.attributes('data-placement')).toBe('right')
    expect(drawer.attributes('data-width')).toBe('640')
    expect(wrapper.text()).toContain('编辑商品')
    expect(wrapper.findComponent({ name: 'NModal' }).exists()).toBe(false)
  })
})
