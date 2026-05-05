export const TALENT_VIEW_OPTIONS = [
  { label: '团队公海', value: 'TEAM_PUBLIC' },
  { label: '我的达人', value: 'MY_TALENTS' },
  { label: '自然出单达人', value: 'NATURAL_ORDERS' },
  { label: '达人黑名单', value: 'BLACKLIST' }
] as const

export const TALENT_VIEW_LABEL_MAP = TALENT_VIEW_OPTIONS.reduce<Record<string, string>>((acc, item) => {
  acc[item.value] = item.label
  return acc
}, {})

export const CLAIM_STATUS_OPTIONS = [
  { label: '全部认领状态', value: null },
  { label: '未认领', value: 'UNCLAIMED' },
  { label: '已认领', value: 'CLAIMED' },
  { label: '保护期内', value: 'PROTECTED' },
  { label: '已过保护期', value: 'EXPIRED' }
]

export const CATEGORY_OPTIONS = [
  { label: '全部经营分类', value: null },
  { label: '食品饮料', value: '食品饮料' },
  { label: '美妆', value: '美妆' },
  { label: '个护家清', value: '个护家清' },
  { label: '母婴宠物', value: '母婴宠物' },
  { label: '服饰内衣', value: '服饰内衣' },
  { label: '鞋靴箱包', value: '鞋靴箱包' },
  { label: '智能家居', value: '智能家居' },
  { label: '3C数码家电', value: '3C数码家电' }
]

export const TALENT_VIEW_HELP_MAP: Record<string, string> = {
  TEAM_PUBLIC: '聚焦团队可认领达人；即使他人已先认领，你仍可继续认领并进入自己的私海。',
  MY_TALENTS: '查看本人私海达人，按经营表现继续跟进、释放或转黑。',
  NATURAL_ORDERS: '筛出已经自然出单的达人，优先补建合作关系和归属。',
  BLACKLIST: '管理已拉黑达人，集中处理风险达人与误拉黑恢复。'
}

export const DEFAULT_TALENT_FILTERS = {
  keyword: '',
  category: null as string | null,
  claimStatus: null as string | null,
  minFans: null as number | null,
  maxFans: null as number | null,
  region: ''
}

export function formatFans(value?: number | null) {
  if (value === null || value === undefined) return '-'
  if (value >= 100000000) return `${(value / 100000000).toFixed(1)}亿`
  if (value >= 10000) return `${(value / 10000).toFixed(1)}万`
  return String(value)
}

export function formatMoney(value?: number | null) {
  if (value === null || value === undefined) return '-'
  return `¥${(Number(value) / 100).toFixed(2)}`
}

export function formatDateTime(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

export function getPoolTagType(poolStatus?: string | null) {
  if (poolStatus === 'BLACKLIST') return 'error'
  if (poolStatus === 'PRIVATE') return 'success'
  return 'warning'
}

export function getPoolLabel(poolStatus?: string | null) {
  if (poolStatus === 'BLACKLIST') return '黑名单'
  if (poolStatus === 'PRIVATE') return '我的达人'
  return '团队公海'
}
