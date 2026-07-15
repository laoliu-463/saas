const runExecCommandCopy = (text: string): boolean => {
  if (typeof document === 'undefined' || typeof document.execCommand !== 'function') {
    return false
  }

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', 'readonly')
  textarea.style.position = 'fixed'
  textarea.style.left = '-9999px'
  textarea.style.top = '0'
  textarea.style.opacity = '0'

  document.body.appendChild(textarea)
  textarea.focus()
  textarea.select()

  try {
    return document.execCommand('copy')
  } catch {
    return false
  } finally {
    textarea.remove()
  }
}

export async function tryCopyText(text: string): Promise<boolean> {
  if (!text) return false

  if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      // Fall through to execCommand for HTTP or restricted browser contexts.
    }
  }

  return runExecCommandCopy(text)
}

export type ClipboardContentResult = {
  copied: boolean
  imageCopied: boolean
}

const isSupportedClipboardImageType = (value: string): boolean =>
  /^(image\/(png|jpeg|gif|webp))$/i.test(value)

const escapeHtml = (value: string): string => value.replace(/[&<>"']/g, (character) => {
  const entities: Record<string, string> = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  }
  return entities[character]
})

const readBlobAsDataUrl = (blob: Blob): Promise<string> => new Promise((resolve, reject) => {
  if (typeof FileReader === 'undefined') {
    reject(new Error('FileReader unavailable'))
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    if (typeof reader.result === 'string') resolve(reader.result)
    else reject(new Error('image data URL unavailable'))
  }
  reader.onerror = () => reject(reader.error || new Error('image read failed'))
  reader.readAsDataURL(blob)
})

const convertImageToClipboardPng = async (blob: Blob): Promise<Blob> => {
  const imageType = String(blob.type || '').toLowerCase()
  if (imageType === 'image/png') return blob
  if (typeof createImageBitmap !== 'function' || typeof document === 'undefined') {
    throw new Error('image conversion unavailable')
  }

  const bitmap = await createImageBitmap(blob)
  try {
    const canvas = document.createElement('canvas')
    canvas.width = bitmap.width
    canvas.height = bitmap.height
    const context = canvas.getContext('2d')
    if (!context) throw new Error('canvas context unavailable')
    context.drawImage(bitmap, 0, 0)

    return await new Promise<Blob>((resolve, reject) => {
      canvas.toBlob((pngBlob) => {
        if (pngBlob) resolve(pngBlob)
        else reject(new Error('png conversion failed'))
      }, 'image/png')
    })
  } finally {
    bitmap.close()
  }
}

/**
 * 尝试把商品图片和文本写成同一个富文本剪贴板表示。
 *
 * 不再同时暴露独立 image/png 表示：Windows 与粘贴目标会从多个格式中选择一个，
 * 微信会优先选择独立图片并丢弃同一 ClipboardItem 中的文字。富文本放在首位，
 * 纯文本仅作为不支持 HTML 的目标端降级格式。
 * 图片读取失败（常见于 CDN 未开放 CORS）时只降级复制文本，不伪造图片已复制。
 */
export async function tryCopyTextAndImage(
  text: string,
  imageUrl?: string | null
): Promise<ClipboardContentResult> {
  const normalizedImageUrl = String(imageUrl || '').trim()
  if (
    text &&
    normalizedImageUrl &&
    typeof fetch === 'function' &&
    typeof ClipboardItem !== 'undefined' &&
    typeof navigator !== 'undefined' &&
    typeof navigator.clipboard?.write === 'function'
  ) {
    try {
      const response = await fetch(normalizedImageUrl, {
        mode: 'cors',
        credentials: 'omit'
      })
      if (!response.ok) throw new Error(`image request failed: ${response.status}`)

      const sourceBlob = await response.blob()
      const imageType = String(sourceBlob.type || '').toLowerCase()
      if (!isSupportedClipboardImageType(imageType)) {
        throw new Error(`unsupported clipboard image type: ${imageType || 'unknown'}`)
      }

      const clipboardImage = await convertImageToClipboardPng(sourceBlob)
      const imageDataUrl = await readBlobAsDataUrl(clipboardImage)
      const html = `<img src="${imageDataUrl}" alt="商品图片"><div>${escapeHtml(text).replace(/\r?\n/g, '<br>')}</div>`
      const clipboardItem = new ClipboardItem({
        'text/html': new Blob([html], { type: 'text/html' }),
        'text/plain': new Blob([text], { type: 'text/plain' })
      })
      await navigator.clipboard.write([clipboardItem])
      return { copied: true, imageCopied: true }
    } catch {
      // 图片跨域或浏览器能力受限时，继续走已有纯文本降级路径。
    }
  }

  return {
    copied: await tryCopyText(text),
    imageCopied: false
  }
}
