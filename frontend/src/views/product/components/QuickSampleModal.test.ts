import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { applyQuickSample } from '../../../api/product'
import QuickSampleModal from './QuickSampleModal.vue'

const messageApi = vi.hoisted(() => ({
  error: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
  info: vi.fn()
}))

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
  getTalentByChannel: vi.fn().mockResolvedValue([]),
  parsePrivateTalentPoolResponse: vi.fn(() => [{ id: 'TALENT-1', nickname: '达人一', douyinUid: 'TALENT-1' }]),
  toPrivateTalentSelectOption: vi.fn((item: any) => ({
    label: item.nickname,
    value: item.douyinUid || item.id
  }))
}))

vi.mock('../../sample/sample-user-filter-options', () => ({
  loadSampleChannelOptions: vi.fn().mockResolvedValue([
    {
      label: '渠道甲 (channel_a)',
      value: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
    }
  ])
}))

vi.mock('../../../stores/auth', () => ({
  useAuthStore: () => ({ isAdmin: false, roleCodes: ['channel_staff'] })
}))

vi.mock('naive-ui', async (importOriginal) => {
  const actual = await importOriginal<typeof import('naive-ui')>()
  return {
    ...actual,
    useMessage: () => messageApi
  }
})

describe('QuickSampleModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(applyQuickSample).mockResolvedValue({ data: { successCount: 1, failureCount: 0, items: [] } })
  })

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

  it('shows item failure reasons instead of a generic apply failure', async () => {
    vi.mocked(applyQuickSample).mockResolvedValueOnce({
      data: {
        successCount: 0,
        failureCount: 1,
        items: [
          {
            talentId: 'TALENT-1',
            success: false,
            message: '商品快照不存在或商品 ID 缺失，请刷新商品后重试'
          }
        ]
      }
    })

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
          ProductSpecSelector: { template: '<select data-testid="quick-sample-spec" />' },
          NInput: { template: '<input />' },
          NInputNumber: true,
          NButton: { emits: ['click'], template: '<button v-bind="$attrs" @click="$emit(\'click\')"><slot /></button>' },
          NSpace: { template: '<div><slot /></div>' }
        }
      }
    })
    await flushPromises()

    await wrapper.get('[data-testid="quick-sample-talents"]').trigger('click')
    await wrapper.get('[data-testid="quick-sample-submit"]').trigger('click')
    await flushPromises()

    expect(messageApi.error).toHaveBeenCalledWith(expect.stringContaining('商品快照不存在或商品 ID 缺失，请刷新商品后重试'))
    expect(messageApi.success).not.toHaveBeenCalled()
  })
})
