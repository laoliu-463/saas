import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import QuickSampleModal from './QuickSampleModal.vue'

vi.mock('naive-ui', async (importOriginal) => {
  const actual = await importOriginal<typeof import('naive-ui')>()
  return {
    ...actual,
    useMessage: () => ({ error: vi.fn(), success: vi.fn(), warning: vi.fn() })
  }
})

describe('QuickSampleModal', () => {
  it('renders quick sample form fields', () => {
    const wrapper = mount(QuickSampleModal, {
      props: {
        show: true,
        product: { id: '11111111-1111-1111-1111-111111111111', title: '测试商品' }
      },
      global: {
        stubs: {
          NModal: { template: '<div data-testid="quick-sample-modal"><slot /><slot name="footer" /></div>', props: ['show'] },
          NForm: { template: '<form><slot /></form>' },
          NFormItem: { template: '<div><slot /></div>', props: ['label'] },
          NSelect: { template: '<select data-testid="quick-sample-talents" />' },
          ProductSpecSelector: { template: '<select data-testid="quick-sample-spec" />' },
          NInput: { template: '<input />' },
          NInputNumber: true,
          NButton: { template: '<button data-testid="quick-sample-submit"><slot /></button>' },
          NSpace: { template: '<div><slot /></div>' }
        }
      }
    })

    expect(wrapper.find('[data-testid="quick-sample-modal"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="quick-sample-talents"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="quick-sample-submit"]').exists()).toBe(true)
  })
})
