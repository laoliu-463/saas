import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import CooperationActionColumn from './CooperationActionColumn.vue'

describe('CooperationActionColumn', () => {
  it('renders all seven actions vertically and only emits enabled actions', async () => {
    const wrapper = mount(CooperationActionColumn, {
      props: {
        availability: {
          APPROVE: { enabled: true, disabledReason: null },
          PROGRESS: { enabled: true, disabledReason: null }
        }
      },
      global: {
        stubs: {
          NTooltip: { template: '<div class="tooltip"><slot name="trigger"/><span class="tooltip-text"><slot /></span></div>' },
          NButton: {
            props: ['disabled'],
            emits: ['click'],
            template: '<button :disabled="disabled" v-bind="$attrs" @click="$emit(\'click\')"><slot /></button>'
          }
        }
      }
    })

    const column = wrapper.get('.cooperation-actions')
    expect(column.findAll('button').map((button) => button.text())).toEqual([
      '通过', '拒绝', '修改订单', '查看进度', '复制链接', '复制订单', '备注'
    ])
    expect(column.attributes('style') || '').not.toContain('row')
    expect(wrapper.text()).toContain('服务端未返回该操作能力')

    await wrapper.get('[data-testid="cooperation-action-APPROVE"]').trigger('click')
    await wrapper.get('[data-testid="cooperation-action-EDIT"]').trigger('click')
    expect(wrapper.emitted('select')).toEqual([['APPROVE']])
  })
})
