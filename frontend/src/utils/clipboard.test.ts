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

  it('writes the formatted text and fetched product image as one clipboard payload', async () => {
    const write = vi.fn().mockResolvedValue(undefined)
    setClipboard({ write, writeText: vi.fn() } as unknown as Clipboard)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      blob: vi.fn().mockResolvedValue(new Blob(['image'], { type: 'image/png' }))
    }))
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
    expect(Object.keys((write.mock.calls[0][0][0] as ClipboardItemMock).items)).toEqual([
      'text/plain',
      'text/html',
      'image/png'
    ])
  })

  it('converts jpeg images to png before writing because browser clipboard write supports png', async () => {
    const write = vi.fn().mockResolvedValue(undefined)
    setClipboard({ write, writeText: vi.fn() } as unknown as Clipboard)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      blob: vi.fn().mockResolvedValue(new Blob(['jpeg'], { type: 'image/jpeg' }))
    }))
    vi.stubGlobal('createImageBitmap', vi.fn().mockResolvedValue({
      width: 100,
      height: 80,
      close: vi.fn()
    }))
    const drawImage = vi.fn()
    const toBlob = vi.fn((callback: BlobCallback) => callback(new Blob(['png'], { type: 'image/png' })))
    vi.spyOn(document, 'createElement').mockImplementation((tagName: string) => {
      if (tagName === 'canvas') {
        return {
          width: 0,
          height: 0,
          getContext: () => ({ drawImage }),
          toBlob
        } as unknown as HTMLCanvasElement
      }
      return document.createElementNS('http://www.w3.org/1999/xhtml', tagName)
    })
    class ClipboardItemMock {
      readonly items: Record<string, Blob>

      constructor(items: Record<string, Blob>) {
        this.items = items
      }
    }
    vi.stubGlobal('ClipboardItem', ClipboardItemMock)

    await expect(tryCopyTextAndImage('商品文案', 'https://img.example.com/product.jpg')).resolves.toEqual({
      copied: true,
      imageCopied: true
    })

    expect(drawImage).toHaveBeenCalledOnce()
    expect(toBlob).toHaveBeenCalledOnce()
    expect(Object.keys((write.mock.calls[0][0][0] as ClipboardItemMock).items)).toEqual([
      'text/plain',
      'text/html',
      'image/png'
    ])
  })

  it('falls back to text when the image cannot be fetched', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    setClipboard({ writeText } as unknown as Clipboard)
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('cors denied')))

    await expect(tryCopyTextAndImage('商品文案', 'https://img.example.com/product.png')).resolves.toEqual({
      copied: true,
      imageCopied: false
    })

    expect(writeText).toHaveBeenCalledWith('商品文案')
  })
})
