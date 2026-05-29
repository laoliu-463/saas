export type ActivityRow = Record<string, unknown>

export type ActivityStatusTab = {
  status: number
  label: string
}

export const ACTIVITY_STATUS_TABS: ActivityStatusTab[] = [
  { status: 0, label: '全部' },
  { status: 1, label: '未上线' },
  { status: 2, label: '报名未开始' },
  { status: 3, label: '报名中' },
  { status: 4, label: '推广未开始' },
  { status: 5, label: '推广中' },
  { status: 7, label: '已结束' }
]

export type ActivityProductStats = {
  promoting: number | null
  pending: number | null
}

const BUYIN_ACTIVITY_URL =
  'https://buyin.jinritemai.com/dashboard/servicehall/institution/activity/detail'

export function buildBuyinActivityUrl(activityId: string | number): string {
  const id = String(activityId || '').trim()
  if (!id) return ''
  return `${BUYIN_ACTIVITY_URL}?id=${encodeURIComponent(id)}`
}

export function formatDateOnly(value: unknown): string {
  const text = String(value ?? '').trim()
  if (!text) return '—'
  return text.length >= 10 ? text.slice(0, 10) : text
}

export function formatDateRange(start: unknown, end: unknown): { start: string; end: string } {
  return {
    start: formatDateOnly(start),
    end: formatDateOnly(end)
  }
}

export function resolveActivityAssigneeName(row: ActivityRow): string {
  const activityName = String(row.activityAssigneeName ?? '').trim()
  if (activityName) return activityName
  const legacy = String(row.assigneeName ?? row.recruiterName ?? row.managerName ?? '').trim()
  return legacy || '—'
}

export function resolveActivityStatusLabel(row: ActivityRow): string {
  const text = String(row.statusText ?? '').trim()
  if (text) return text
  const status = Number(row.status ?? row.activityStatus ?? 0)
  const tab = ACTIVITY_STATUS_TABS.find((item) => item.status === status)
  return tab?.label || '未知'
}

export function formatActivityCategories(categoriesLimit: unknown): string {
  if (categoriesLimit == null || categoriesLimit === '') return '—'
  if (typeof categoriesLimit === 'string') return categoriesLimit.trim() || '—'
  if (Array.isArray(categoriesLimit)) {
    const values = categoriesLimit
      .map((item) => {
        if (item == null) return ''
        if (typeof item === 'string') return item.trim()
        if (typeof item === 'object') {
          return Object.values(item as Record<string, unknown>)
            .map((value) => String(value ?? '').trim())
            .filter(Boolean)
            .join('、')
        }
        return String(item).trim()
      })
      .filter(Boolean)
    return values.length ? values.join('、') : '—'
  }
  if (typeof categoriesLimit === 'object') {
    const values = Object.values(categoriesLimit as Record<string, unknown>)
      .map((value) => String(value ?? '').trim())
      .filter(Boolean)
    return values.length ? values.join('、') : '—'
  }
  return String(categoriesLimit)
}

export function formatPercentValue(value: unknown): string {
  const text = String(value ?? '').trim()
  if (!text) return '—'
  if (text.endsWith('%')) return text
  const numeric = Number(text)
  if (Number.isFinite(numeric)) {
    if (numeric > 1) return `${numeric.toFixed(2)}%`
    return `${(numeric * 100).toFixed(2)}%`
  }
  return text
}

export function formatMechanismSummary(row: ActivityRow): {
  typeLabel: string
  commissionLine: string
  serviceLine: string
} {
  const commissionRate = row.commissionRate ?? row.commission_rate
  const serviceRate = row.serviceRate ?? row.service_rate
  const adCommissionRate = row.adCommissionRate ?? row.ad_commission_rate
  const adServiceRate = row.adServiceRate ?? row.ad_service_rate
  const cosLimitType = row.cosLimitType ?? row.cos_limit_type

  const dailyCommission = formatPercentValue(commissionRate)
  const adCommission = formatPercentValue(adCommissionRate)
  const dailyService = formatPercentValue(serviceRate)
  const adService = formatPercentValue(adServiceRate)

  return {
    typeLabel: cosLimitType == null || cosLimitType === '' ? '无限制' : String(cosLimitType),
    commissionLine: `日常≥${dailyCommission === '—' ? '0.00%' : dailyCommission} 投放期${adCommission === '—' ? '0.00%' : adCommission}`,
    serviceLine: `日常≥${dailyService === '—' ? '0.00%' : dailyService} 投放期${adService === '—' ? '0.00%' : adService}`
  }
}

export function resolveActivityRequirement(row: ActivityRow): string {
  const text = String(row.activityDesc ?? row.activity_desc ?? row.activityRequirement ?? '').trim()
  if (text) return text
  return String(row.activityName ?? '—').trim() || '—'
}

export function resolveColonelName(row: ActivityRow, institutionName: string): string {
  const fromRow = String(
    row.colonelName ?? row.colonel_name ?? row.institutionName ?? row.institution_name ?? ''
  ).trim()
  if (fromRow) return fromRow
  return institutionName.trim() || '—'
}

export function countActivityProductStats(items: Array<Record<string, unknown>>): ActivityProductStats {
  if (!items.length) {
    return { promoting: 0, pending: 0 }
  }
  let promoting = 0
  let pending = 0
  items.forEach((item) => {
    const status = Number(item.status ?? -1)
    const statusText = String(item.statusText ?? item.status_text ?? '').trim()
    if (status === 1 || statusText.includes('推广')) {
      promoting += 1
      return
    }
    if (status === 0 || statusText.includes('待审核')) {
      pending += 1
    }
  })
  return { promoting, pending }
}

export function extractInstitutionName(payload: unknown): string {
  if (!payload || typeof payload !== 'object') return ''
  const root = payload as Record<string, unknown>
  const candidates = [
    root.institution_name,
    root.institutionName,
    root.colonel_name,
    root.colonelName,
    root.name,
    (root.data as Record<string, unknown> | undefined)?.institution_name,
    (root.data as Record<string, unknown> | undefined)?.institutionName,
    (root.data as Record<string, unknown> | undefined)?.colonel_name,
    (root.remoteResponse as Record<string, unknown> | undefined)?.data
  ]
  for (const candidate of candidates) {
    if (candidate && typeof candidate === 'object') {
      const nested = extractInstitutionName(candidate)
      if (nested) return nested
    }
    const text = String(candidate ?? '').trim()
    if (text && text !== '[object Object]') return text
  }
  return ''
}
