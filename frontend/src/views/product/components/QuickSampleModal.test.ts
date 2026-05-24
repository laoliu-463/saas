import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { applyQuickSample } from '../../../api/product'
import QuickSampleModal from './QuickSampleModal.vue'

vi.mock('../../../api/product', () => ({
  applyQuickSample: vi.fn().mockResolvedValue({ data: { successCount: 1, failureCount: 0, items: [] } })
}))

vi.mock('../../../api/activityProduct', () => ({
  getActivityProductSkus: vi.fn().mockResolvedValue({
    data: [
      { skuId: 'SKU-1', skuName: '红色/L', priceText: '¥99.00' },
      { skuId: 'SKU-2', skuName: '蓝色/M', priceText: '¥89.00' }
    ]
  })
}))

vi.mock('../../../api/talent', () => ({
  getTalentPrivate: vi.fn().mockResolvedValue({ data: [] }),
  parsePrivateTalentPoolResponse: vi.fn(() => [{ id: 'TALENT-1', nickname: '达人一' }]),
  toPrivateTalentSelectOption: vi.fn((item: any) => ({ label: item.nickname, value: item.id }))
}))

vi.mock('naive-ui', async (importOriginal) => {
  const actual = await importOriginal<typeof import('naive-ui')>()
  return {
    ...actual,
    useMessage: () => ({ error: vi.fn(), success: vi.fn(), warning: vi.fn(), info: vi.fn() })
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
          NAlert: { template: '<div><slot /></div>' },
          NSelect: { props: ['options', 'value', 'loading'], template: '<select data-testid="quick-sample-talents" />' },
          ProductSpecSelector: { template: '<select data-testid="quick-sample-spec" />' },
          NInput: { template: '<input />' },
          NInputNumber: true,
          NButton: { emits: ['click'], template: '<button v-bind="$attrs" @click="$emit(\'click\')"><slot /></button>' },
          NSpace: { template: '<div><slot /></div>' }
        }
      }
    })

    expect(wrapper.find('[data-testid="quick-sample-modal"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="quick-sample-talents"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="quick-sample-submit"]').exists()).toBe(true)
  })

  it('submits the selected skuId with the sku specification name', async () => {
    const wrapper = mount(QuickSampleModal, {
      props: {
        show: true,
        product: {
          id: '11111111-1111-1111-1111-111111111111',
          activityId: 'ACT-1',
          productId: 'PROD-1',
          title: '测试商品'
        }
      },
      global: {
        stubs: {
          NModal: { template: '<div data-testid="quick-sample-modal"><slot /><slot name="footer" /></div>', props: ['show'] },
          NForm: { template: '<form><slot /></form>' },
          NFormItem: { template: '<div><slot /></div>', props: ['label'] },
          NAlert: { template: '<div><slot /></div>' },
          NSelect: {
            props: ['options', 'value', 'loading'],
            emits: ['update:value'],
            template: '<button data-testid="quick-sample-talents" @click="$emit(\'update:value\', [\'TALENT-1\'])">talent</button>'
          },
          ProductSpecSelector: {
            emits: ['update:modelValue', 'select'],
            template:
              '<button data-testid="quick-sample-spec" @click="$emit(\'update:modelValue\', \'SKU-2\'); $emit(\'select\', { skuId: \'SKU-2\', skuName: \'蓝色/M\', priceText: \'¥89.00\' })">spec</button>'
          },
          NInput: { template: '<input />' },
          NInputNumber: true,
          NButton: { emits: ['click'], template: '<button v-bind="$attrs" @click="$emit(\'click\')"><slot /></button>' },
          NSpace: { template: '<div><slot /></div>' }
        }
      }
    })
    await flushPromises()

    await wrapper.get('[data-testid="quick-sample-talents"]').trigger('click')
    await wrapper.get('[data-testid="quick-sample-spec"]').trigger('click')
    await wrapper.get('[data-testid="quick-sample-submit"]').trigger('click')
    await flushPromises()

    expect(applyQuickSample).toHaveBeenCalledWith('11111111-1111-1111-1111-111111111111', expect.objectContaining({
      talentIds: ['TALENT-1'],
      skuId: 'SKU-2',
      specification: '蓝色/M'
    }))
  })
})
