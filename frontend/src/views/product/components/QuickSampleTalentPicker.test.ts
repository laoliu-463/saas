import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import QuickSampleTalentPicker, { type QuickSampleTalentRow } from './QuickSampleTalentPicker.vue'

const rows: QuickSampleTalentRow[] = [
  { value: 'A', nickname: '重庆小吴', douyinNo: '71067386245', fansCount: 25000 },
  { value: 'B', nickname: '妮妮又喝了', douyinNo: '33612596488', fansCount: 10000 },
  { value: 'C', nickname: '叮叮又喝了', douyinNo: '93810356967', fansCount: 3094 }
]

const drawerStubs = {
  NDrawer: {
    inheritAttrs: false,
    template: '<div v-bind="$attrs"><slot /></div>'
  },
  NDrawerContent: {
    template: '<div><header><slot name="header" /></header><slot /><footer><slot name="footer" /></footer></div>'
  }
}

describe('QuickSampleTalentPicker', () => {
  it('matches the selection layout and submits multiple selected talents', async () => {
    const wrapper = mount(QuickSampleTalentPicker, {
      props: { show: true, rows, selectedValues: [] },
      global: { stubs: drawerStubs }
    })

    expect(wrapper.get('[data-testid="quick-sample-talent-picker-drawer"]').attributes('width')).toBe('860')
    expect(wrapper.get('[data-testid="quick-sample-talent-picker-title"]').text()).toContain('选择合作达人(0/20)')
    expect(wrapper.get('[data-testid="quick-sample-talent-picker"]').text()).toContain('共3条达人数据')
    expect(wrapper.get('[data-testid="quick-sample-talent-row-A"]').text()).toContain('2.5W')

    await wrapper.get('[data-testid="quick-sample-talent-row-A"]').trigger('click')
    await wrapper.get('[data-testid="quick-sample-talent-row-B"]').trigger('click')

    expect(wrapper.get('[data-testid="quick-sample-talent-picker-title"]').text()).toContain('选择合作达人(2/20)')
    await wrapper.get('[data-testid="quick-sample-talent-picker-submit"]').trigger('click')

    expect(wrapper.emitted('update:selectedValues')).toEqual([[['A', 'B']]])
    expect(wrapper.emitted('update:show')).toEqual([[false]])
  })

  it('filters by nickname and douyin number before paging', async () => {
    const wrapper = mount(QuickSampleTalentPicker, {
      props: { show: true, rows, selectedValues: [] },
      global: { stubs: drawerStubs }
    })

    await wrapper.get('[data-testid="quick-sample-talent-nickname-search"]').setValue('叮叮')
    await wrapper.get('[data-testid="quick-sample-talent-search"]').trigger('click')

    expect(wrapper.find('[data-testid="quick-sample-talent-row-A"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="quick-sample-talent-row-C"]').exists()).toBe(true)
    expect(wrapper.get('.quick-sample-talent-picker__total').text()).toBe('共1条达人数据')
  })
})
