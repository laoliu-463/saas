import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import ManualCopyDialog from './ManualCopyDialog.vue'

const stubs = {
  NModal: {
    props: ['show', 'title'],
    template: '<section v-if="show"><h2>{{ title }}</h2><slot /></section>'
  },
  NAlert: {
    props: ['type', 'title'],
    template: '<div data-testid="manual-copy-alert"><strong>{{ title }}</strong><slot /></div>'
  },
  NSpace: { template: '<div><slot /></div>' },
  NButton: {
    emits: ['click'],
    template: '<button v-bind="$attrs" @click="$emit(\'click\', $event)"><slot /></button>'
  }
}

const mountDialog = (props = {}) => mount(ManualCopyDialog, {
  props: {
    show: true,
    content: '【商品】测试商品\n【链接】https://v.douyin.com/abc/?pick_source=ps_001',
    promotionLink: 'https://v.douyin.com/abc/?pick_source=ps_001',
    pickSource: 'ps_001',
    ...props
  },
  global: { stubs }
})

describe('ManualCopyDialog', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('renders generated content, promotion link and pick source', () => {
    const wrapper = mountDialog()

    expect(wrapper.text()).toContain('复制受限，请手动复制')
    expect(wrapper.get('[data-testid="manual-copy-content"]').element).toHaveProperty(
      'value',
      '【商品】测试商品\n【链接】https://v.douyin.com/abc/?pick_source=ps_001'
    )
    expect(wrapper.text()).toContain('https://v.douyin.com/abc/?pick_source=ps_001')
    expect(wrapper.text()).toContain('ps_001')
  })

  it('renders warning and baiying entry when provided', () => {
    const wrapper = mountDialog({
      pickSource: null,
      pickSourceWarning: '无法确认归因：链接缺少 pick_source',
      baiyingUrl: 'https://buyin.example/manual'
    })

    expect(wrapper.text()).toContain('无法确认归因：链接缺少 pick_source')
    const link = wrapper.get('[data-testid="manual-copy-baiying"]')
    expect(link.attributes('href')).toBe('https://buyin.example/manual')
  })

  it('selects content and emits retry and close events', async () => {
    const select = vi.spyOn(HTMLTextAreaElement.prototype, 'select').mockImplementation(() => {})
    const wrapper = mountDialog()

    await wrapper.get('[data-testid="manual-copy-select"]').trigger('click')
    await wrapper.get('[data-testid="manual-copy-retry"]').trigger('click')
    await wrapper.get('[data-testid="manual-copy-close"]').trigger('click')

    expect(select).toHaveBeenCalledOnce()
    expect(wrapper.emitted('retry')).toHaveLength(1)
    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
