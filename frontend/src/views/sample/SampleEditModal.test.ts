import { flushPromises, mount } from '@vue/test-utils'
import type { AxiosResponse } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getSampleEditContext, updateSampleCooperationDetails } from '../../api/sample'
import SampleEditModal from './SampleEditModal.vue'

const messageApi = vi.hoisted(() => ({ error: vi.fn(), success: vi.fn() }))
const context = {
  sampleId: 'sample-1',
  talentNickname: '是小鱼吖',
  talentDouyinNo: 'xiaoyu06180102',
  talentFansCount: 68000,
  talentWindowSales30d: 14705,
  productId: 'product-1',
  productExternalId: '3820194249627009436',
  productName: '鲜飘飘生椰牛乳',
  shopName: '鲜飘飘饮料',
  productSpecification: '1L*2',
  quantity: 1,
  sampleThreshold: { minFans: 10000 },
  activityId: 'activity-1',
  activityName: '星链达客-yy',
  remark: '原备注',
  addressAvailable: true,
  recipientName: '薄荷',
  recipientPhone: '15093177715',
  recipientAddress: '河南省郑州市巩义市回郭镇东庙村',
  version: 5
}

vi.mock('../../api/sample', () => ({
  getSampleEditContext: vi.fn(),
  updateSampleCooperationDetails: vi.fn()
}))
vi.mock('naive-ui', async (importOriginal) => ({
  ...(await importOriginal<typeof import('naive-ui')>()),
  useMessage: () => messageApi
}))

const response = <T,>(data: T) => ({ data } as AxiosResponse<T>)

const mountModal = () => mount(SampleEditModal, {
  props: { show: true, sampleId: 'sample-1' },
  global: {
    stubs: {
      NModal: { props: ['show'], template: '<div><slot/><slot name="footer"/></div>' },
      NSpin: { template: '<div><slot/></div>' },
      NDescriptions: { template: '<div><slot/></div>' },
      NDescriptionsItem: { props: ['label'], template: '<div>{{ label }}：<slot/></div>' },
      NForm: { template: '<form><slot/></form>' },
      NFormItem: { props: ['label'], template: '<label>{{ label }}<slot/></label>' },
      NInput: {
        props: ['value', 'disabled'],
        emits: ['update:value'],
        template: '<input :value="value" :disabled="disabled" v-bind="$attrs" @input="$emit(\'update:value\', $event.target.value)" />'
      },
      NAlert: { template: '<div><slot/></div>' },
      NSpace: { template: '<div><slot/></div>' },
      NButton: { emits: ['click'], template: '<button v-bind="$attrs" @click.prevent="$emit(\'click\')"><slot/></button>' }
    }
  }
})

describe('SampleEditModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getSampleEditContext).mockResolvedValue(response(context) as any)
    vi.mocked(updateSampleCooperationDetails).mockResolvedValue(response(context) as any)
  })

  it('loads real facts and saves only editable fields with the version', async () => {
    const wrapper = mountModal()
    await flushPromises()

    expect(wrapper.text()).toContain('是小鱼吖')
    expect(wrapper.text()).toContain('星链达客-yy')
    await wrapper.get('[data-testid="sample-edit-remark"]').setValue('新备注')
    await wrapper.get('[data-testid="sample-edit-save"]').trigger('click')
    await flushPromises()

    expect(updateSampleCooperationDetails).toHaveBeenCalledWith('sample-1', {
      version: 5,
      remark: '新备注',
      recipientName: '薄荷',
      recipientPhone: '15093177715',
      recipientAddress: '河南省郑州市巩义市回郭镇东庙村'
    })
  })

  it('shows the fixed refresh message for an optimistic lock conflict', async () => {
    vi.mocked(updateSampleCooperationDetails).mockRejectedValueOnce({ response: { status: 409 } })
    const wrapper = mountModal()
    await flushPromises()
    await wrapper.get('[data-testid="sample-edit-save"]').trigger('click')
    await flushPromises()

    expect(messageApi.error).toHaveBeenCalledWith('数据已更新，请刷新后重试')
  })
})
