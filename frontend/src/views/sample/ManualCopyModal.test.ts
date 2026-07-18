import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import ManualCopyModal from './ManualCopyModal.vue'

vi.mock('../../utils/clipboard', () => ({ tryCopyText: vi.fn().mockResolvedValue(false) }))
vi.mock('naive-ui', async (importOriginal) => ({
  ...(await importOriginal<typeof import('naive-ui')>()),
  useMessage: () => ({ success: vi.fn(), warning: vi.fn() })
}))

describe('ManualCopyModal', () => {
  it('keeps the complete copy text visible for manual selection', () => {
    const text = '第一行\n第二行\n第三行'
    const wrapper = mount(ManualCopyModal, {
      props: { show: true, content: text },
      global: {
        stubs: {
          NModal: { props: ['show'], template: '<div><slot/><slot name="footer"/></div>' },
          NAlert: { template: '<div><slot/></div>' },
          NInput: { props: ['value'], template: '<textarea :value="value" />' },
          NSpace: { template: '<div><slot/></div>' },
          NButton: { template: '<button><slot/></button>' }
        }
      }
    })

    expect((wrapper.get('[data-testid="manual-copy-content"]').element as HTMLTextAreaElement).value).toBe(text)
  })
})
