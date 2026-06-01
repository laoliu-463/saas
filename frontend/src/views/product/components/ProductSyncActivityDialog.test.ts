import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProductSyncActivityDialog from './ProductSyncActivityDialog.vue'

const stubs = {
  NModal: {
    props: ['show'],
    emits: ['update:show'],
    template: '<div v-if="show"><slot /><slot name="action" /></div>'
  },
  NForm: { template: '<form><slot /></form>' },
  NFormItem: { template: '<label><slot /></label>' },
  NSpace: { template: '<div><slot /></div>' },
  NSelect: {
    props: ['value', 'options'],
    emits: ['update:value'],
    template: `
      <select data-testid="sync-activity-select" :value="value" @change="$emit('update:value', $event.target.value)">
        <option value="">请选择活动</option>
        <option v-for="option in options" :key="option.value" :value="option.value">{{ option.label }}</option>
      </select>
    `
  },
  NButton: {
    props: ['disabled', 'loading'],
    emits: ['click'],
    template: '<button :disabled="disabled || loading" @click="$emit(\'click\', $event)"><slot /></button>'
  }
}

const activityOptions = [
  { label: '春季招商 (ACT001)', value: 'ACT001' },
  { label: '夏季招商 (ACT002)', value: 'ACT002' }
]

const mountDialog = (props = {}) => mount(ProductSyncActivityDialog, {
  props: {
    show: true,
    activityOptions,
    ...props
  },
  global: { stubs }
})

describe('ProductSyncActivityDialog', () => {
  it('emits selected activity id before syncing products', async () => {
    const wrapper = mountDialog()

    await wrapper.get('[data-testid="sync-activity-select"]').setValue('ACT002')
    await wrapper.findAll('button').find((button) => button.text() === '开始同步')?.trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([['ACT002']])
  })

  it('uses the default activity when the dialog opens', async () => {
    const wrapper = mountDialog({ defaultActivityId: 'ACT001' })

    await wrapper.findAll('button').find((button) => button.text() === '开始同步')?.trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([['ACT001']])
  })
})
