import type { ProductManageApprovePayload } from '../../api/productManage'
import type { ProductManageRow } from '../../types/productManage'

export interface ProductAuditSupplementForm {
  exclusivePriceRemark: string
  shippingInfo: string
  sellingPoints: string
  promotionScript: string
  supportsAds: boolean
  adsRule: string
  rewardRemark: string
  participationRequirements: string
  campaignTimeRemark: string
  materialFiles: string
}

const requiredFields: Array<[keyof ProductAuditSupplementForm, string, 'text' | 'list']> = [
  ['exclusivePriceRemark', '专属价说明', 'text'],
  ['shippingInfo', '发货信息', 'text'],
  ['sellingPoints', '商品卖点', 'list'],
  ['promotionScript', '推广话术', 'text'],
  ['rewardRemark', '奖励说明', 'text'],
  ['participationRequirements', '参与要求', 'text'],
  ['campaignTimeRemark', '活动时间', 'text'],
  ['materialFiles', '手卡素材', 'list']
]

const asRecord = (value: unknown): Record<string, unknown> =>
  value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}

const textValue = (value: unknown): string => {
  if (value == null) return ''
  if (typeof value === 'string') return value.trim()
  if (typeof value === 'number' || typeof value === 'boolean') return String(value).trim()
  return ''
}

export const splitTextList = (value: unknown): string[] => {
  if (Array.isArray(value)) {
    return value.map(textValue).filter(Boolean)
  }
  const text = textValue(value)
  if (!text) return []
  return text.split(/[\n,，]+/).map((item) => item.trim()).filter(Boolean)
}

const listToTextarea = (value: unknown): string => splitTextList(value).join('\n')

const booleanValue = (value: unknown): boolean => {
  if (typeof value === 'boolean') return value
  const text = textValue(value).toLowerCase()
  if (!text) return false
  return ['true', '1', 'yes', 'y', '是', '支持'].includes(text)
}

const fallbackCampaignTime = (row: ProductManageRow | null | undefined): string => {
  const start = textValue(row?.promotionStartTime || row?.activityStartTime)
  const end = textValue(row?.promotionEndTime || row?.activityEndTime)
  if (start && end) return `${start} - ${end}`
  return start || end
}

export const createAuditSupplementForm = (
  row: ProductManageRow | null | undefined
): ProductAuditSupplementForm => {
  const supplement = asRecord(row?.auditSupplement)
  return {
    exclusivePriceRemark: textValue(supplement.exclusivePriceRemark),
    shippingInfo: textValue(supplement.shippingInfo),
    sellingPoints: listToTextarea(supplement.sellingPoints),
    promotionScript: textValue(supplement.promotionScript || row?.promotionMaterialPack?.outreachScript),
    supportsAds: booleanValue(supplement.supportsAds),
    adsRule: textValue(supplement.adsRule),
    rewardRemark: textValue(supplement.rewardRemark),
    participationRequirements: textValue(supplement.participationRequirements),
    campaignTimeRemark: textValue(supplement.campaignTimeRemark) || fallbackCampaignTime(row),
    materialFiles: listToTextarea(supplement.materialFiles) || textValue(row?.handCardUrl)
  }
}

export const validateApprovalSupplement = (form: ProductAuditSupplementForm): string[] =>
  requiredFields
    .filter(([key, , type]) => {
      const value = form[key]
      return type === 'list' ? splitTextList(value).length === 0 : !textValue(value)
    })
    .map(([, label]) => label)

export const buildApproveProductPayload = (
  remark: string,
  form: ProductAuditSupplementForm
): ProductManageApprovePayload => ({
  remark: textValue(remark),
  exclusivePriceRemark: textValue(form.exclusivePriceRemark),
  shippingInfo: textValue(form.shippingInfo),
  sellingPoints: splitTextList(form.sellingPoints),
  promotionScript: textValue(form.promotionScript),
  supportsAds: form.supportsAds,
  adsRule: textValue(form.adsRule),
  rewardRemark: textValue(form.rewardRemark),
  participationRequirements: textValue(form.participationRequirements),
  campaignTimeRemark: textValue(form.campaignTimeRemark),
  materialFiles: splitTextList(form.materialFiles)
})
