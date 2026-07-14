export interface ProductSampleSettingForm {
  supportFreeSample: boolean
  hasSampleThreshold: boolean
  minWindowSales30d: number | null
  minSales30d: number | null
  minFans: number | null
  minTalentLevel: number | null
}

export const DEFAULT_SAMPLE_SETTING: ProductSampleSettingForm = {
  supportFreeSample: true,
  hasSampleThreshold: true,
  minWindowSales30d: null,
  minSales30d: 50000,
  minFans: null,
  minTalentLevel: 1
}

export const TALENT_LEVEL_OPTIONS = Array.from({ length: 8 }, (_, value) => ({
  label: `LV${value}`,
  value
}))

const asNumber = (value: unknown): number | null => {
  if (value === null || value === undefined || value === '') return null
  if (typeof value === 'string') {
    const level = value.trim().match(/^LV(\d+)$/i)
    if (level) return Number(level[1])
  }
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

const firstValue = (source: Record<string, unknown>, ...keys: string[]) => {
  for (const key of keys) {
    if (source[key] !== undefined && source[key] !== null && source[key] !== '') {
      return source[key]
    }
  }
  return undefined
}

export function normalizeSampleSetting(source?: Record<string, unknown> | null): ProductSampleSettingForm {
  const setting = source || {}
  const sampleType = String(setting.sampleType || '').toUpperCase()
  const thresholdValues = [
    'minWindowSales30d',
    'minSales30d',
    'minFans',
    'minTalentLevel',
    'sampleThresholdSales',
    'sampleThresholdLevel'
  ]
  const hasConfiguredThreshold = thresholdValues.some((key) => asNumber(setting[key]) !== null)

  return {
    supportFreeSample: typeof setting.supportFreeSample === 'boolean'
      ? setting.supportFreeSample
      : typeof setting.freeSample === 'boolean'
        ? setting.freeSample
        : sampleType
          ? sampleType === 'FREE'
          : DEFAULT_SAMPLE_SETTING.supportFreeSample,
    hasSampleThreshold: typeof setting.hasSampleThreshold === 'boolean'
      ? setting.hasSampleThreshold
      : hasConfiguredThreshold || DEFAULT_SAMPLE_SETTING.hasSampleThreshold,
    minWindowSales30d: asNumber(firstValue(setting, 'minWindowSales30d', 'windowSales30dMin')),
    minSales30d: asNumber(firstValue(setting, 'minSales30d', 'sampleThresholdSales', 'salesRequirement30d'))
      ?? DEFAULT_SAMPLE_SETTING.minSales30d,
    minFans: asNumber(firstValue(setting, 'minFans', 'fansMin')),
    minTalentLevel: asNumber(firstValue(setting, 'minTalentLevel', 'sampleThresholdLevel', 'talentLevelRequirement'))
      ?? DEFAULT_SAMPLE_SETTING.minTalentLevel
  }
}

export function toSampleSettingPayload(form: ProductSampleSettingForm): Record<string, unknown> {
  return {
    supportFreeSample: form.supportFreeSample,
    hasSampleThreshold: form.hasSampleThreshold,
    minWindowSales30d: form.hasSampleThreshold ? form.minWindowSales30d : null,
    minSales30d: form.hasSampleThreshold ? form.minSales30d : null,
    minFans: form.hasSampleThreshold ? form.minFans : null,
    minTalentLevel: form.hasSampleThreshold ? form.minTalentLevel : null,
    // 兼容现有商品库筛选和寄样门槛字段。
    allowSample: true,
    sampleType: form.supportFreeSample ? 'FREE' : 'PAID',
    sampleThresholdSales: form.hasSampleThreshold ? form.minSales30d : null,
    sampleThresholdLevel: form.hasSampleThreshold ? form.minTalentLevel : null
  }
}
