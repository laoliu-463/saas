import { flushPromises, mount } from '@vue/test-utils'
import type { AxiosResponse } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getSamplePrivateNote, saveSamplePrivateNote } from '../../api/sample'
import PrivateNoteModal from './PrivateNoteModal.vue'

const messageApi = vi.hoisted(() => ({ error: vi.fn(), success: vi.fn() }))
vi.mock('../../api/sample', () => ({
  getSamplePrivateNote: vi.fn(),
  saveSamplePrivateNote: vi.fn()
}))
vi.mock('naive-ui', async (importOriginal) => ({
  ...(await importOriginal<typeof import('naive-ui')>()),
  useMessage: () => messageApi
}))

const response = <T,>(data: T) => ({ data } as AxiosResponse<T>)

const mountModal = (show = true) => mount(PrivateNoteModal, {
  props: { show, sampleId: 'sample-1' },
  global: {
    stubs: {
      NModal: { props: ['show'], template: '<div><slot/><slot name="footer"/></div>' },
      NInput: {
        props: ['value'],
        emits: ['update:value'],
        template: '<textarea :value="value" v-bind="$attrs" @input="$emit(\'update:value\', $event.target.value)" />'
      },
      NAlert: { template: '<div><slot/></div>' },
      NSpace: { template: '<div><slot/></div>' },
      NButton: { emits: ['click'], template: '<button v-bind="$attrs" @click="$emit(\'click\')"><slot/></button>' }
    }
  }
})

describe('PrivateNoteModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getSamplePrivateNote).mockResolvedValue(response({ content: '已有备注', version: 1 }) as any)
    vi.mocked(saveSamplePrivateNote).mockResolvedValue(response({ content: '新的私有备注', version: 2 }) as any)
  })

  it('loads the current user private note on every open and saves at most 200 chars', async () => {
    const wrapper = mountModal()
    await flushPromises()
    expect(wrapper.text()).toContain('仅自己可见')
    expect((wrapper.get('[data-testid="private-note-content"]').element as HTMLTextAreaElement).value).toBe('已有备注')

    await wrapper.get('[data-testid="private-note-content"]').setValue('新的私有备注')
    await wrapper.get('[data-testid="private-note-save"]').trigger('click')
    await flushPromises()
    expect(saveSamplePrivateNote).toHaveBeenCalledWith('sample-1', { content: '新的私有备注' })

    await wrapper.setProps({ show: false })
    await wrapper.setProps({ show: true })
    await flushPromises()
    expect(getSamplePrivateNote).toHaveBeenCalledTimes(2)
  })
})
