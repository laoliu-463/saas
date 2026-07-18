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

const mountModal = (show = true, sampleId = 'sample-1') => mount(PrivateNoteModal, {
  props: { show, sampleId },
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
      NButton: {
        props: ['disabled'],
        emits: ['click'],
        template: '<button :disabled="disabled" v-bind="$attrs" @click="$emit(\'click\')"><slot/></button>'
      }
    }
  }
})

const deferred = <T,>() => {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

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

  it('ignores a stale private note after switching samples', async () => {
    const first = deferred<AxiosResponse<{ content: string; version: number }>>()
    const second = deferred<AxiosResponse<{ content: string; version: number }>>()
    vi.mocked(getSamplePrivateNote)
      .mockReturnValueOnce(first.promise as any)
      .mockReturnValueOnce(second.promise as any)

    const wrapper = mountModal()
    await wrapper.setProps({ show: false })
    await wrapper.setProps({ sampleId: 'sample-2', show: true })
    second.resolve(response({ content: 'B的备注', version: 2 }))
    await flushPromises()
    expect((wrapper.get('[data-testid="private-note-content"]').element as HTMLTextAreaElement).value).toBe('B的备注')

    first.resolve(response({ content: 'A的备注', version: 1 }))
    await flushPromises()
    expect((wrapper.get('[data-testid="private-note-content"]').element as HTMLTextAreaElement).value).toBe('B的备注')

    await wrapper.get('[data-testid="private-note-content"]').setValue('只保存B')
    await wrapper.get('[data-testid="private-note-save"]').trigger('click')
    await flushPromises()
    expect(saveSamplePrivateNote).toHaveBeenCalledWith('sample-2', { content: '只保存B' })
  })

  it('does not save an empty note while the existing note is still loading', async () => {
    const pending = deferred<AxiosResponse<{ content: string; version: number }>>()
    vi.mocked(getSamplePrivateNote).mockReturnValueOnce(pending.promise as any)
    const wrapper = mountModal()

    expect(wrapper.get('[data-testid="private-note-save"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[data-testid="private-note-save"]').trigger('click')
    expect(saveSamplePrivateNote).not.toHaveBeenCalled()

    pending.resolve(response({ content: '不能被误删', version: 1 }))
    await flushPromises()
    expect(wrapper.get('[data-testid="private-note-save"]').attributes('disabled')).toBeUndefined()
    expect((wrapper.get('[data-testid="private-note-content"]').element as HTMLTextAreaElement).value).toBe('不能被误删')
  })

  it('keeps saving disabled when loading the existing note fails', async () => {
    vi.mocked(getSamplePrivateNote).mockRejectedValueOnce(new Error('load failed'))
    const wrapper = mountModal()
    await flushPromises()

    expect(messageApi.error).toHaveBeenCalled()
    expect(wrapper.get('[data-testid="private-note-save"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[data-testid="private-note-save"]').trigger('click')
    expect(saveSamplePrivateNote).not.toHaveBeenCalled()
  })
})
