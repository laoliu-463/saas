import { afterEach, describe, expect, it, vi } from 'vitest'

import { tryCopyText } from './clipboard'

const originalClipboard = navigator.clipboard
const originalExecCommand = document.execCommand

const setClipboard = (clipboard: Clipboard | undefined) => {
  Object.defineProperty(navigator, 'clipboard', {
    configurable: true,
    value: clipboard
  })
}

const setExecCommand = (impl: typeof document.execCommand | undefined) => {
  Object.defineProperty(document, 'execCommand', {
    configurable: true,
    value: impl
  })
}

describe('tryCopyText', () => {
  afterEach(() => {
    setClipboard(originalClipboard)
    setExecCommand(originalExecCommand)
    document.body.innerHTML = ''
    vi.restoreAllMocks()
  })

  it('returns true when navigator clipboard write succeeds', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    setClipboard({ writeText } as unknown as Clipboard)
    const execCommand = vi.fn()
    setExecCommand(execCommand as unknown as typeof document.execCommand)

    await expect(tryCopyText('可复制内容')).resolves.toBe(true)

    expect(writeText).toHaveBeenCalledWith('可复制内容')
    expect(execCommand).not.toHaveBeenCalled()
  })

  it('falls back to execCommand when navigator clipboard rejects', async () => {
    const writeText = vi.fn().mockRejectedValue(new Error('denied'))
    const execCommand = vi.fn().mockReturnValue(true)
    setClipboard({ writeText } as unknown as Clipboard)
    setExecCommand(execCommand as unknown as typeof document.execCommand)

    await expect(tryCopyText('execCommand 兜底')).resolves.toBe(true)

    expect(writeText).toHaveBeenCalledOnce()
    expect(execCommand).toHaveBeenCalledWith('copy')
    expect(document.querySelector('textarea')).toBeNull()
  })

  it('returns false when both clipboard paths fail', async () => {
    const writeText = vi.fn().mockRejectedValue(new Error('denied'))
    const execCommand = vi.fn().mockReturnValue(false)
    setClipboard({ writeText } as unknown as Clipboard)
    setExecCommand(execCommand as unknown as typeof document.execCommand)

    await expect(tryCopyText('需要手动复制')).resolves.toBe(false)

    expect(writeText).toHaveBeenCalledOnce()
    expect(execCommand).toHaveBeenCalledWith('copy')
  })
})
