export interface SampleSelectOption {
  label: string
  value: string
}

export interface SampleContextResult {
  query: Record<string, string>
  option?: SampleSelectOption
}

export interface TalentSampleContextResult {
  query: Record<string, string>
  talentRow?: Record<string, unknown>
}

export interface SampleRemarkInput {
  reason?: string | null
  receiverName?: string | null
  receiverPhone?: string | null
  receiverAddress?: string | null
  extraRemark?: string | null
}

export const INTERNAL_QUICK_SAMPLE_SOURCE = 'INTERNAL_QUICK_SAMPLE'
export const MANUAL_SAMPLE_APPLY_SOURCE = 'MANUAL'

const cleanText = (value: unknown) => {
  if (value === null || value === undefined) return ''
  const text = String(value).trim()
  return text === 'null' || text === 'undefined' ? '' : text
}

const firstText = (...values: unknown[]) => {
  for (const value of values) {
    const text = cleanText(value)
    if (text) return text
  }
  return ''
}

const setQuery = (query: Record<string, string>, key: string, value: unknown) => {
  const text = cleanText(value)
  if (text) query[key] = text
}

export const isMainlandMobile = (value?: string | null) => /^1\d{10}$/.test(cleanText(value))

export const normalizeSampleApplySource = (value?: string | null) => {
  const normalized = cleanText(value).toUpperCase()
  return normalized === INTERNAL_QUICK_SAMPLE_SOURCE ? INTERNAL_QUICK_SAMPLE_SOURCE : MANUAL_SAMPLE_APPLY_SOURCE
}

export function buildProductSampleContext(product: any): SampleContextResult {
  const productId = firstText(
    product?.id,
    product?.productUuid,
    product?.productRecordId,
    product?.productInternalId,
    product?.productId
  )
  const label = firstText(product?.title, product?.productName, product?.name, product?.productId, productId)
  const query: Record<string, string> = {}

  setQuery(query, 'productId', productId)
  setQuery(query, 'productLabel', label)
  setQuery(query, 'productExternalId', product?.productId)
  setQuery(query, 'shopName', product?.shopName)
  setQuery(query, 'applySource', INTERNAL_QUICK_SAMPLE_SOURCE)

  return {
    query,
    option: productId ? { label: label || productId, value: productId } : undefined
  }
}

export function buildTalentSampleContext(detail: any): TalentSampleContextResult {
  const talent = detail?.talent ?? detail ?? {}
  const claim = detail?.claim ?? {}
  const talentId = firstText(talent?.douyinUid, talent?.douyinNo, talent?.uid, talent?.id)
  const nickname = firstText(talent?.nickname, talentId)
  const query: Record<string, string> = {}

  setQuery(query, 'talentId', talentId)
  setQuery(query, 'talentUuid', talent?.id)
  setQuery(query, 'talentNickname', nickname)
  setQuery(query, 'talentFansCount', talent?.fansCount)
  setQuery(query, 'talentCreditScore', talent?.creditScore)
  setQuery(query, 'talentMainCategory', talent?.mainCategory)
  setQuery(query, 'receiverName', claim?.recipientName ?? talent?.shippingRecipientName ?? talent?.recipientName)
  setQuery(query, 'receiverPhone', claim?.recipientPhone ?? talent?.shippingRecipientPhone ?? talent?.recipientPhone)
  setQuery(query, 'receiverAddress', claim?.recipientAddress ?? talent?.shippingRecipientAddress ?? talent?.recipientAddress)

  return {
    query,
    talentRow: talentId
      ? {
          id: talent?.id,
          talentId,
          nickname,
          fansCount: talent?.fansCount ?? null,
          creditScore: talent?.creditScore ?? null,
          region: talent?.ipLocation ?? talent?.region ?? '',
          mainCategory: talent?.mainCategory ?? '',
          avatarUrl: talent?.avatarUrl ?? ''
        }
      : undefined
  }
}

export function mergeLockedOption(options: SampleSelectOption[], lockedOption?: SampleSelectOption | null) {
  if (!lockedOption?.value) return options
  return [
    lockedOption,
    ...options.filter(option => option.value !== lockedOption.value)
  ]
}

export function buildSampleRemark(input: SampleRemarkInput) {
  const lines = [
    ['申请理由', input.reason],
    ['收货人', input.receiverName],
    ['手机号', input.receiverPhone],
    ['地址', input.receiverAddress],
    ['补充说明', input.extraRemark]
  ]

  return lines
    .map(([label, value]) => {
      const text = cleanText(value)
      return text ? `${label}：${text}` : ''
    })
    .filter(Boolean)
    .join('\n')
}
