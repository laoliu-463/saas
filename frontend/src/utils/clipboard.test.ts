import { afterEach, describe, expect, it, vi } from 'vitest'

import { tryCopyText, tryCopyTextAndImage } from './clipboard'

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

describe('tryCopyTextAndImage', () => {
  afterEach(() => {
    setClipboard(originalClipboard)
    setExecCommand(originalExecCommand)
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('writes the image URL and formatted text as HTML without an independent image representation', async () => {
    const write = vi.fn().mockResolvedValue(undefined)
    setClipboard({ write, writeText: vi.fn() } as unknown as Clipboard)
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    class ClipboardItemMock {
      readonly items: Record<string, Blob>

      constructor(items: Record<string, Blob>) {
        this.items = items
      }
    }
    vi.stubGlobal('ClipboardItem', ClipboardItemMock)

    await expect(tryCopyTextAndImage('商品文案', 'https://img.example.com/product.png')).resolves.toEqual({
      copied: true,
      imageCopied: true
    })

    expect(write).toHaveBeenCalledOnce()
    const clipboardItems = (write.mock.calls[0][0][0] as ClipboardItemMock).items
    expect(Object.keys(clipboardItems)).toEqual(['text/html', 'text/plain'])
    await expect(clipboardItems['text/html'].text()).resolves.toContain(
      '<img src="https://img.example.com/product.png"'
    )
    await expect(clipboardItems['text/html'].text()).resolves.toContain('<div>商品文案</div>')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('escapes the image URL and formatted text before writing rich HTML', async () => {
    const write = vi.fn().mockResolvedValue(undefined)
    setClipboard({ write, writeText: vi.fn() } as unknown as Clipboard)
    class ClipboardItemMock {
      readonly items: Record<string, Blob>

      constructor(items: Record<string, Blob>) {
        this.items = items
      }
    }
    vi.stubGlobal('ClipboardItem', ClipboardItemMock)

    await expect(tryCopyTextAndImage('商品<文案>\n第二行', 'https://img.example.com/product.png?a=1&b=2')).resolves.toEqual({
      copied: true,
      imageCopied: true
    })

    const clipboardItems = (write.mock.calls[0][0][0] as ClipboardItemMock).items
    expect(Object.keys(clipboardItems)).toEqual(['text/html', 'text/plain'])
    await expect(clipboardItems['text/html'].text()).resolves.toContain(
      '<img src="https://img.example.com/product.png?a=1&amp;b=2"'
    )
    await expect(clipboardItems['text/html'].text()).resolves.toContain(
      '<div>商品&lt;文案&gt;<br>第二行</div>'
    )
  })

  it('falls back to text when the rich clipboard write is rejected', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    const write = vi.fn().mockRejectedValue(new Error('rich clipboard denied'))
    setClipboard({ write, writeText } as unknown as Clipboard)
    class ClipboardItemMock {
      readonly items: Record<string, Blob>

      constructor(items: Record<string, Blob>) {
        this.items = items
      }
    }
    vi.stubGlobal('ClipboardItem', ClipboardItemMock)

    await expect(tryCopyTextAndImage('商品文案', 'https://img.example.com/product.png')).resolves.toEqual({
      copied: true,
      imageCopied: false
    })

    expect(writeText).toHaveBeenCalledWith('商品文案')
  })
})
