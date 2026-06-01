import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProductManageToolbar from './ProductManageToolbar.vue'

const stubs = {
  NSpace: { template: '<div><slot /></div>' },
  NButton: { emits: ['click'], template: '<button v-bind="$attrs" @click="$emit(\'click\', $event)"><slot /></button>' }
}

const mountToolbar = () => mount(ProductManageToolbar, {
  global: { stubs }
})

describe('ProductManageToolbar', () => {
  it('only renders sync activity and sync product actions', () => {
    const wrapper = mountToolbar()

    expect(wrapper.text()).toContain('同步最新活动')
    expect(wrapper.text()).toContain('一键同步商品')
    expect(wrapper.text()).not.toContain('合作中申请提醒')
    expect(wrapper.text()).not.toContain('批量补录商品信息')
    expect(wrapper.text()).not.toContain('导出')
    expect(wrapper.text()).not.toContain('刷新商品')
  })

  it('emits only sync events from visible buttons', async () => {
    const wrapper = mountToolbar()
    const buttons = wrapper.findAll('button')

    expect(buttons).toHaveLength(2)

    await buttons[0].trigger('click')
    await buttons[1].trigger('click')

    expect(wrapper.emitted('syncActivities')).toHaveLength(1)
    expect(wrapper.emitted('syncProducts')).toHaveLength(1)
    expect(wrapper.emitted('openPendingApplications')).toBeFalsy()
    expect(wrapper.emitted('openBatchSupplement')).toBeFalsy()
    expect(wrapper.emitted('export')).toBeFalsy()
    expect(wrapper.emitted('refresh')).toBeFalsy()
  })
})
