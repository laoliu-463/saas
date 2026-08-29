type ProductCopySource = Record<string, any>
type ProductPromotionScene = 'PRODUCT_LIBRARY' | 'PRODUCT_DETAIL' | 'TALENT_SHARE' | 'SAMPLE_DESK'
export type ProductBriefCopyFormat = 'LEGACY' | 'DOUYIN_SHARE'

export type ProductCopyContent = {
  text: string
  imageUrl: string | null
}

type ProductCopyContentResult = {
  copied: boolean
  imageCopied: boolean
}

type CopyProductBriefOptions = {
  item: ProductCopySource
  activityId: string | number
  productId: string | number
  scene: ProductPromotionScene
  format?: ProductBriefCopyFormat
  convertLink: (
    activityId: string | number,
    productId: string | number,
    data: { scene: ProductPromotionScene }
  ) => Promise<unknown>
  writeText: (text: string) => Promise<boolean>
  writeContent?: (content: ProductCopyContent) => Promise<ProductCopyContentResult>
}

type CopyProductBriefResult = {
  text: string
  link: string | null
  copied: boolean
  converted: boolean
  linkGenerationFailed: boolean
  promotionLinkGenerated: boolean
  fallbackReason: string | null
  pickSource: string | null
  realPromotionWriteEnabled: boolean | null
  allowRealPromotionWrite: boolean | null
  responseData: ProductCopySource | null
  imageCopyAttempted: boolean
  imageCopied: boolean
  error: unknown
}

type ProductBriefCopyMessageInput = {
  clipboardWriteFailed: boolean
  linkGenerationFailed: boolean
  promotionLinkGenerated?: boolean
  imageCopyAttempted?: boolean
  imageCopied?: boolean
}

type ProductBriefCopyMessage = {
  type: 'success' | 'warning'
  content: string
}

const EMPTY_TEXT = '-'

const normalizeText = (value: unknown): string => {
  if (value === null || value === undefined) return ''
  const text = String(value).trim()
  if (!text || text === 'null' || text === 'undefined') return ''
  return text
}

const displayText = (value: unknown): string => normalizeText(value) || EMPTY_TEXT

const getAuditSupplement = (item: ProductCopySource) => item?.auditSupplement || {}

const getPromotionMaterialPack = (item: ProductCopySource) => item?.promotionMaterialPack || {}

const normalizeTextList = (value: unknown): string[] => {
  if (!Array.isArray(value)) return []
  return value.map((item) => normalizeText(item)).filter(Boolean)
}

const resolveSellingPoints = (item: ProductCopySource): string[] => {
  const supplementPoints = normalizeTextList(getAuditSupplement(item)?.sellingPoints)
  if (supplementPoints.length) return supplementPoints
  return normalizeTextList(getPromotionMaterialPack(item)?.sellingPoints)
}

const resolvePromotionScript = (item: ProductCopySource): string => {
  const supplement = getAuditSupplement(item)
  const pack = getPromotionMaterialPack(item)
  return normalizeText(supplement?.promotionScript) || normalizeText(pack?.outreachScript)
}

const resolveAuditSupplementForShare = (item: ProductCopySource): ProductCopySource => {
  const supplement = item?.auditSupplement
  if (supplement && typeof supplement === 'object' && Object.keys(supplement).length) {
    return supplement
  }
  return item?.auditSupplementSummary || {}
}

const firstDisplayValue = (...values: unknown[]): string =>
  values.map((value) => normalizeText(value)).find(Boolean) || EMPTY_TEXT

const resolveBasisPointRate = (value: unknown): string => {
  const text = normalizeText(value)
  if (!text) return ''
  if (text.includes('%')) return text
  const number = Number(text)
  if (!Number.isFinite(number) || number <= 0) return ''
  return `${(number / 100).toFixed(2).replace(/\.00$/, '')}%`
}

const resolveShareCampaignCommission = (item: ProductCopySource): string => {
  const direct = firstDisplayValue(
    item?.campaignCommissionRateText,
    item?.deliveryCommissionRateText,
    item?.putCommissionRateText,
    item?.activityAdCosRatioText,
    item?.campaignCommissionRate
  )
  if (direct !== EMPTY_TEXT) return direct
  return resolveBasisPointRate(item?.activityAdCosRatio ?? item?.activity_ad_cos_ratio) || EMPTY_TEXT
}

export const extractProductImage = (source: ProductCopySource | null | undefined): string | null => {
  const pack = source?.promotionMaterialPack || {}
  return [
    source?.imageUrl,
    source?.cover,
    source?.mainImage,
    source?.productImage,
    pack?.cover,
    pack?.imageUrl
  ].map((value) => normalizeText(value)).find(Boolean) || null
}

/** 商品库专用：按渠道可直接转发的抖音商品信息模板生成纯文本。 */
export const buildDouyinShareCopy = (
  item: ProductCopySource,
  promotionLink?: string | null
): string => {
  const supplement = resolveAuditSupplementForShare(item)
  const link = normalizeText(promotionLink)
  return [
    `【抖音】${firstDisplayValue(item?.title, item?.productName, item?.name)}`,
    `【店铺名称】${firstDisplayValue(item?.shopName, item?.merchantShopName, item?.merchantName)}`,
    `【售价】${firstDisplayValue(item?.priceText, item?.livePriceText, item?.livePrice, item?.price)}`,
    `【佣金率】${firstDisplayValue(item?.activityCosRatioText, item?.commissionRateText, item?.commissionRate)}`,
    `【投放期佣金】${resolveShareCampaignCommission(item)}`,
    `【库存】${firstDisplayValue(item?.productStock, item?.product_stock, item?.stock)}`,
    `【奖励说明】${firstDisplayValue(supplement?.rewardRemark, item?.rewardRemark)}`,
    `【开始时间】${firstDisplayValue(item?.promotionStartTime, item?.activityStartTime, item?.startTime)}`,
    `【结束时间】${firstDisplayValue(item?.promotionEndTime, item?.activityEndTime, item?.endTime)}`,
    '【推广链接】',
    link || '未生成'
  ].join('\n')
}

export const extractPromotionLink = (source: ProductCopySource | null | undefined): string | null => {
  const candidates = [
    source?.promotion?.link,
    source?.promotion?.shortLink,
    source?.promotion?.promoteLink,
    source?.promotion?.promotionUrl,
    source?.promotionLink,
    source?.promoteLink,
    source?.promotionUrl,
    source?.shortLink
  ]
  return candidates.map((value) => normalizeText(value)).find(Boolean) || null
}

const readBoolean = (value: unknown): boolean | null => {
  if (typeof value === 'boolean') return value
  return null
}

export const buildProductBriefCopy = (item: ProductCopySource, promotionLink?: string | null): string => {
  const supplement = getAuditSupplement(item)
  const sellingPoints = resolveSellingPoints(item)
  const link = normalizeText(promotionLink)
  const lines = [
    `【商品】${displayText(item?.title || item?.productName || item?.name)}（${displayText(item?.shopName)}）`,
    `【售价】${displayText(item?.priceText)}  【佣金率】${displayText(item?.activityCosRatioText)}  【近30天】${displayText(item?.sales30d ?? item?.sales)}`,
    `【卖点】${sellingPoints.length ? sellingPoints.join('、') : EMPTY_TEXT}`,
    `【话术】${displayText(resolvePromotionScript(item))}`,
    `【寄样门槛】销售额≥${displayText(supplement?.sampleThresholdSales)} / 等级≥LV${displayText(supplement?.sampleThresholdLevel)}`,
    `【专属价说明】${displayText(supplement?.exclusivePriceRemark)}`
  ]

  if (link) {
    lines.push(`【链接】${link}`)
  }

  return lines.join('\n')
}

export const copyProductBriefWithLink = async ({
  item,
  activityId,
  productId,
  scene,
  format = 'LEGACY',
  convertLink,
  writeText,
  writeContent
}: CopyProductBriefOptions): Promise<CopyProductBriefResult> => {
  let link = extractPromotionLink(item)
  let converted = false
  let linkGenerationFailed = false
  let responseData: ProductCopySource | null = null
  let error: unknown = null
  let promotionLinkGenerated = Boolean(link)
  let fallbackReason: string | null = null
  let pickSource: string | null = null
  let realPromotionWriteEnabled: boolean | null = null
  let allowRealPromotionWrite: boolean | null = null
  let backendCopyText = ''

  if (!link) {
    try {
      converted = true
      const response = await convertLink(activityId, productId, { scene })
      responseData = ((response as { data?: ProductCopySource })?.data || response || null) as ProductCopySource | null
      link = extractPromotionLink(responseData)
      backendCopyText = normalizeText(responseData?.copyText)
      const backendGenerated = readBoolean(responseData?.promotionLinkGenerated)
      promotionLinkGenerated = backendGenerated ?? Boolean(link)
      fallbackReason = normalizeText(responseData?.fallbackReason) || null
      pickSource = normalizeText(responseData?.pickSource) || null
      realPromotionWriteEnabled = readBoolean(responseData?.realPromotionWriteEnabled)
      allowRealPromotionWrite = readBoolean(responseData?.allowRealPromotionWrite)
      linkGenerationFailed = !promotionLinkGenerated
    } catch (caughtError) {
      linkGenerationFailed = true
      promotionLinkGenerated = false
      error = caughtError
    }
  }

  const text = format === 'DOUYIN_SHARE'
    ? buildDouyinShareCopy(item, link)
    : backendCopyText || buildProductBriefCopy(item, link)
  const imageUrl = format === 'DOUYIN_SHARE' ? extractProductImage(item) : null
  const imageCopyAttempted = Boolean(format === 'DOUYIN_SHARE' && writeContent && imageUrl)
  let copied = false
  let imageCopied = false
  if (format === 'DOUYIN_SHARE' && writeContent) {
    try {
      const copyResult = await writeContent({ text, imageUrl })
      copied = copyResult.copied
      imageCopied = copyResult.imageCopied
    } catch {
      try {
        copied = await writeText(text)
      } catch {
        copied = false
      }
    }
  } else {
    try {
      copied = await writeText(text)
    } catch {
      copied = false
    }
  }

  return {
    text,
    link,
    copied,
    converted,
    linkGenerationFailed,
    promotionLinkGenerated,
    fallbackReason,
    pickSource,
    realPromotionWriteEnabled,
    allowRealPromotionWrite,
    responseData,
    imageCopyAttempted,
    imageCopied,
    error
  }
}

export const resolveProductBriefCopyMessage = ({
  clipboardWriteFailed,
  linkGenerationFailed,
  promotionLinkGenerated,
  imageCopyAttempted,
  imageCopied
}: ProductBriefCopyMessageInput): ProductBriefCopyMessage => {
  if (clipboardWriteFailed) {
    return { type: 'warning', content: '简介已生成，但浏览器未允许写入剪贴板，请手动复制' }
  }
  if (imageCopyAttempted === true) {
    return imageCopied
      ? { type: 'success', content: '商品图片和完整简介已按模板复制' }
      : { type: 'warning', content: '完整简介已复制，但商品图片受浏览器或图片源限制未能显示' }
  }
  if (imageCopyAttempted === false) {
    return { type: 'warning', content: '完整简介已复制；商品库未提供可复制的商品图片' }
  }
  if (promotionLinkGenerated === true) {
    return { type: 'success', content: '复制成功，已生成推广链接' }
  }
  if (promotionLinkGenerated === false || linkGenerationFailed) {
    return {
      type: 'warning',
      content: '已复制基础简介；真实推广链接未生成，因为真实转链开关未开启。'
    }
  }
  return { type: 'success', content: '已复制简介' }
}
