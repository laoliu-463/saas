import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import SampleSettingModal from './SampleSettingModal.vue'
import { updateSampleSetting } from '../../../api/productManage'

const messageApi = {
  success: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
  info: vi.fn()
}

vi.mock('../../../api/productManage', () => ({
  updateSampleSetting: vi.fn().mockResolvedValue({ data: { success: true } })
}))

vi.mock('naive-ui', () => ({
  useMessage: () => messageApi
}))

describe('SampleSettingModal', () => {
  it('renders the requested sample setting fields and submits their defaults', async () => {
    const wrapper = mount(SampleSettingModal, {
      props: {
        show: true,
        row: { relationId: '11111111-1111-1111-1111-111111111111' }
      },
      global: {
        stubs: {
          NModal: { template: '<div data-testid="sample-setting-modal"><slot /><slot name="action" /></div>', props: ['show'] },
          NForm: { template: '<form><slot /></form>' },
          NFormItem: { template: '<div><label>{{ label }}</label><slot /></div>', props: ['label'] },
          NRadioGroup: { template: '<div><slot /></div>' },
          NRadio: { template: '<span><slot /></span>' },
          NInputNumber: { template: '<input />' },
          NButton: { emits: ['click'], template: '<button v-bind="$attrs" @click="$emit(\'click\')"><slot /></button>' },
          NSpace: { template: '<div><slot /></div>' }
        }
      }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('是否支持免费寄样')
    expect(wrapper.text()).toContain('是否有寄样门槛')
    expect(wrapper.text()).toContain('近30天橱窗销量')
    expect(wrapper.text()).toContain('近30天销售额')
    expect(wrapper.text()).toContain('粉丝数')
    expect(wrapper.text()).toContain('达人带货等级')
    expect(wrapper.text()).toContain('样品盒数')
    expect(wrapper.text()).toContain('4 盒')
    expect(wrapper.text()).toContain('1 份')

    await wrapper.findAll('button')[1].trigger('click')
    await flushPromises()

    expect(updateSampleSetting).toHaveBeenCalledWith(
      '11111111-1111-1111-1111-111111111111',
      expect.objectContaining({
        supportFreeSample: true,
        hasSampleThreshold: true,
        minSales30d: 50000,
        sampleBoxCount: 4,
        sampleQuantity: 1,
        sampleType: 'FREE'
      })
    )
  })
})
