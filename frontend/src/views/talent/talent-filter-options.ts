/** 与后端 TalentQueryService 区间口径对齐 */
export const TALENT_SALES_BAND_OPTIONS = [
  { label: '请选择', value: null },
  { label: '1W以下', value: '1W以下' },
  { label: '1W~2.5W', value: '1W~2.5W' },
  { label: '2.5W~5W', value: '2.5W~5W' },
  { label: '5W~7.5W', value: '5W~7.5W' },
  { label: '7.5W~10W', value: '7.5W~10W' },
  { label: '10W~25W', value: '10W~25W' },
  { label: '25W~50W', value: '25W~50W' },
  { label: '50W以上', value: '50W以上' }
] as const

export const TALENT_FANS_BAND_OPTIONS = [
  { label: '请选择', value: null },
  { label: '1W以下', value: '1W以下' },
  { label: '1W~5W', value: '1W~5W' },
  { label: '5W~10W', value: '5W~10W' },
  { label: '10W~50W', value: '10W~50W' },
  { label: '50W~100W', value: '50W~100W' },
  { label: '100W以上', value: '100W以上' }
] as const

export const TALENT_PLAY_BAND_OPTIONS = [
  { label: '请选择', value: null },
  { label: '5千以下', value: '5千以下' },
  { label: '5千~1W', value: '5千~1W' },
  { label: '1W~5W', value: '1W~5W' },
  { label: '5W以上', value: '5W以上' }
] as const

export const TALENT_GPM_BAND_OPTIONS = [
  { label: '请选择', value: null },
  { label: '50~100', value: '50~100' },
  { label: '100~500', value: '100~500' },
  { label: '500~1000', value: '500~1000' },
  { label: '1000+', value: '1000+' }
] as const

export const TALENT_LEVEL_OPTIONS = [
  { label: '请选择', value: null },
  { label: 'LV0', value: 'LV0' },
  { label: 'LV1', value: 'LV1' },
  { label: 'LV2', value: 'LV2' },
  { label: 'LV3', value: 'LV3' },
  { label: 'LV4', value: 'LV4' },
  { label: 'LV5', value: 'LV5' },
  { label: 'LV6', value: 'LV6' },
  { label: 'LV7', value: 'LV7' }
] as const

export const TALENT_GENDER_OPTIONS = [
  { label: '请选择', value: null },
  { label: '男', value: 'MALE' },
  { label: '女', value: 'FEMALE' }
] as const

export const TALENT_CONTACT_STATUS_OPTIONS = [
  { label: '请选择', value: null },
  { label: '有联系方式', value: 'HAS_CONTACT' },
  { label: '无联系方式', value: 'NO_CONTACT' }
] as const

export const TALENT_MAIN_CATEGORY_TAGS = [
  '玩具乐器',
  '服饰内衣',
  '个护家清',
  '智能家居',
  '生鲜',
  '美妆',
  '母婴宠物',
  '鲜花园艺',
  '本地生活',
  '食品饮料',
  '3C数码家电',
  '图书教育',
  '鞋靴箱包',
  '虚拟充值',
  '运动户外'
] as const

export type TalentFansBand = (typeof TALENT_FANS_BAND_OPTIONS)[number]['value']

export function resolveFansBandRange(band: TalentFansBand): { minFans?: number; maxFans?: number } {
  switch (band) {
    case '1W以下':
      return { maxFans: 9999 }
    case '1W~5W':
      return { minFans: 10000, maxFans: 49999 }
    case '5W~10W':
      return { minFans: 50000, maxFans: 99999 }
    case '10W~50W':
      return { minFans: 100000, maxFans: 499999 }
    case '50W~100W':
      return { minFans: 500000, maxFans: 999999 }
    case '100W以上':
      return { minFans: 1000000 }
    default:
      return {}
  }
}
