import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import CooperationActionColumn from './CooperationActionColumn.vue'

describe('CooperationActionColumn', () => {
  it('renders all seven actions vertically and keeps every action clickable', async () => {
    const wrapper = mount(CooperationActionColumn, {
      props: {
        availability: {
          APPROVE: { enabled: true, disabledReason: null },
          PROGRESS: { enabled: true, disabledReason: null }
        }
      }
    })

    const column = wrapper.get('.cooperation-actions')
    expect(column.findAll('n-tooltip')).toHaveLength(0)
    expect(column.findAll('button').map((button) => button.text())).toEqual([
      '通过', '拒绝', '修改订单', '查看进度', '复制链接', '复制订单', '备注'
    ])
    expect(column.attributes('style') || '').not.toContain('row')
    expect(column.findAll('button').every((button) => !button.attributes('disabled'))).toBe(true)
    expect(wrapper.text()).not.toContain('服务端未返回该操作能力')

    await wrapper.get('[data-testid="cooperation-action-APPROVE"]').trigger('click')
    await wrapper.get('[data-testid="cooperation-action-EDIT"]').trigger('click')
    expect(wrapper.emitted('select')).toEqual([['APPROVE'], ['EDIT']])
  })
})
