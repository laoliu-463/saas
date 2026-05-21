type ProductCopySource = Record<string, any>
type ProductPromotionScene = 'PRODUCT_LIBRARY' | 'PRODUCT_DETAIL' | 'TALENT_SHARE' | 'SAMPLE_DESK'

type CopyProductBriefOptions = {
  item: ProductCopySource
  activityId: string | number
  productId: string | number
  scene: ProductPromotionScene
  convertLink: (
    activityId: string | number,
    productId: string | number,
    data: { scene: ProductPromotionScene }
  ) => Promise<unknown>
  writeText: (text: string) => Promise<void>
}

type CopyProductBriefResult = {
  text: string
  link: string | null
  converted: boolean
  linkGenerationFailed: boolean
  responseData: ProductCopySource | null
  error: unknown
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
  convertLink,
  writeText
}: CopyProductBriefOptions): Promise<CopyProductBriefResult> => {
  let link = extractPromotionLink(item)
  let converted = false
  let linkGenerationFailed = false
  let responseData: ProductCopySource | null = null
  let error: unknown = null

  if (!link) {
    try {
      converted = true
      const response = await convertLink(activityId, productId, { scene })
      responseData = ((response as { data?: ProductCopySource })?.data || response || null) as ProductCopySource | null
      link = extractPromotionLink(responseData)
      linkGenerationFailed = !link
    } catch (caughtError) {
      linkGenerationFailed = true
      error = caughtError
    }
  }

  const text = buildProductBriefCopy(item, link)
  await writeText(text)

  return {
    text,
    link,
    converted,
    linkGenerationFailed,
    responseData,
    error
  }
}
