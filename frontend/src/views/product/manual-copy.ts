import { extractPickSourceFromUrl } from '../../utils/extractPickSource'

export type ManualCopyReason =
  | 'CLIPBOARD_WRITE_FAILED'
  | 'PROMOTION_LINK_MISSING_PICK_SOURCE'
  | 'PROMOTION_LINK_FAILED'

type ManualCopyResultSource = {
  text: string
  link: string | null
  copied: boolean
  linkGenerationFailed: boolean
  pickSource: string | null
}

export type ManualCopyDialogState = {
  show: boolean
  content: string
  promotionLink: string | null
  pickSource: string | null
  pickSourceWarning: string | null
  reason: ManualCopyReason | ''
  baiyingUrl: string | null
}

export const createEmptyManualCopyDialogState = (): ManualCopyDialogState => ({
  show: false,
  content: '',
  promotionLink: null,
  pickSource: null,
  pickSourceWarning: null,
  reason: '',
  baiyingUrl: null
})

const normalizeText = (value: unknown): string => {
  if (value === null || value === undefined) return ''
  const text = String(value).trim()
  if (!text || text === 'null' || text === 'undefined') return ''
  return text
}

const resolveBaiyingUrl = (source: Record<string, any> | null | undefined): string | null => {
  // 百应后台链接兜底必须只在"百应"语义内部封闭：baiyingUrl / baiyingLink / buyinUrl。
  // 禁止把 productUrl / detailUrl / 任何转链字段兜底为百应 — 否则会出现"商品原链 / 转链"被
  // 当作百应链接展示的回归。详见 docs/决策/ADR-003-活动列表与商品库入口路由统一.md
  const candidates = [
    source?.baiyingUrl,
    source?.baiyingLink,
    source?.buyinUrl,
    source?.card?.baiyingUrl
  ]
  return candidates.map(normalizeText).find(Boolean) || null
}

export function resolveManualCopyDialogState(
  result: ManualCopyResultSource,
  source: Record<string, any> | null | undefined
): ManualCopyDialogState | null {
  const promotionLink = normalizeText(result.link) || null
  const pickSource = normalizeText(result.pickSource) || extractPickSourceFromUrl(promotionLink)
  const promotionLinkFailed = result.linkGenerationFailed || !promotionLink
  const pickSourceWarning = promotionLink && !pickSource ? '无法确认归因：链接缺少 pick_source' : null

  let reason: ManualCopyReason | null = null
  if (promotionLinkFailed) {
    reason = 'PROMOTION_LINK_FAILED'
  } else if (!result.copied) {
    reason = 'CLIPBOARD_WRITE_FAILED'
  } else if (pickSourceWarning) {
    reason = 'PROMOTION_LINK_MISSING_PICK_SOURCE'
  }

  if (!reason) return null

  return {
    show: true,
    content: result.text,
    promotionLink,
    pickSource,
    pickSourceWarning,
    reason,
    baiyingUrl: promotionLinkFailed ? resolveBaiyingUrl(source) : null
  }
}
