import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import SampleSettingModal from './SampleSettingModal.vue'
import { fetchSampleSetting, updateSampleSetting } from '../../../api/productManage'

const messageApi = {
  success: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
  info: vi.fn()
}

vi.mock('../../../api/productManage', () => ({
  fetchSampleSetting: vi.fn().mockResolvedValue({ data: { sampleThresholdLevel: 1, sampleThresholdSales: 50000 } }),
  updateSampleSetting: vi.fn().mockResolvedValue({ data: { success: true } })
}))

vi.mock('naive-ui', () => ({
  useMessage: () => messageApi
}))

describe('SampleSettingModal', () => {
  const global = {
    stubs: {
      NDrawer: { template: '<div data-testid="sample-setting-drawer"><slot /></div>', props: ['show'] },
      NDrawerContent: { template: '<div><slot name="header" /><slot /><slot name="footer" /></div>' },
      NForm: { template: '<form><slot /></form>' },
      NFormItem: { template: '<div><label>{{ label }}</label><slot /></div>', props: ['label'] },
      NRadioGroup: { template: '<div><slot /></div>' },
      NRadio: { template: '<span><slot /></span>' },
      NInputNumber: { template: '<input />' },
      NSelect: {
        props: ['value', 'options'],
        template: '<select><option v-for="option in options" :key="option.value">{{ option.label }}</option></select>'
      },
      NInput: { template: '<input :placeholder="placeholder" />', props: ['placeholder'] },
      NButton: { emits: ['click'], template: '<button v-bind="$attrs" @click="$emit(\'click\')"><slot /></button>' },
      NSpace: { template: '<div><slot /></div>' }
    }
  }

  it('renders the requested sample setting fields and submits their defaults', async () => {
    const wrapper = mount(SampleSettingModal, {
      props: {
        show: true,
        row: { relationId: '11111111-1111-1111-1111-111111111111' }
      },
      global
    })
    await flushPromises()

    expect(wrapper.text()).toContain('是否支持免费寄样')
    expect(wrapper.text()).toContain('是否有寄样门槛')
    expect(wrapper.text()).toContain('近30天橱窗销量')
    expect(wrapper.text()).toContain('近30天销售额')
    expect(wrapper.text()).toContain('粉丝数')
    expect(wrapper.text()).toContain('达人带货等级')
    expect(wrapper.text()).toContain('LV1')
    expect(wrapper.text()).toContain('快速申样不进行该标准的判断')
    expect(wrapper.text()).not.toContain('样品盒数')
    expect(wrapper.text()).not.toContain('每次寄样数量')
    expect(wrapper.find('[data-testid="sample-setting-drawer"]').exists()).toBe(true)
    expect(fetchSampleSetting).toHaveBeenCalledWith('11111111-1111-1111-1111-111111111111')
    const editablePlaceholders = wrapper.findAll('input[placeholder="请输入"]')
    expect(editablePlaceholders).toHaveLength(2)
    expect(editablePlaceholders.every((input) => !input.attributes('disabled'))).toBe(true)

    await wrapper.get('[data-testid="sample-setting-confirm"]').trigger('click')
    await flushPromises()

    expect(updateSampleSetting).toHaveBeenCalledWith(
      '11111111-1111-1111-1111-111111111111',
      expect.objectContaining({
        supportFreeSample: true,
        hasSampleThreshold: true,
        minSales30d: 50000,
        minTalentLevel: 1,
        sampleType: 'FREE'
      })
    )
  })

  it('shows a read failure while keeping the cached row settings', async () => {
    vi.mocked(fetchSampleSetting).mockRejectedValueOnce(new Error('network down'))
    const wrapper = mount(SampleSettingModal, {
      props: {
        show: true,
        row: {
          relationId: '22222222-2222-2222-2222-222222222222',
          auditSupplement: { minSales30d: 30000 }
        }
      },
      global
    })

    await flushPromises()

    expect(wrapper.text()).toContain('寄样设置读取失败')
    expect(wrapper.text()).toContain('近30天销售额')
  })
})
