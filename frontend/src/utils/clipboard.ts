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

/**
 * 尝试把商品图片引用和文本写成同一个富文本剪贴板表示。
 *
 * 不暴露独立图片格式，避免目标端只消费图片候选而忽略文字。HTML 同时包含
 * 商品 CDN 图片 URL 和完整简介，纯文本作为不支持 HTML 的目标端降级格式。
 * 是否保留 HTML 图片由粘贴目标决定，必须在对应客户端单独验收。
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
      const html = `<img src="${escapeHtml(normalizedImageUrl)}" alt="商品图片"><div>${escapeHtml(text).replace(/\r?\n/g, '<br>')}</div>`
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
