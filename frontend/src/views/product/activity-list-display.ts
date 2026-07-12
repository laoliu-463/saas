export type ActivityRow = Record<string, unknown>

export type ActivityStatusTab = {
  status: number
  label: string
}

/** 列表筛选 Tab（status=0 仅表示「不按状态过滤」，不是活动真实状态） */
export const ACTIVITY_STATUS_TABS: ActivityStatusTab[] = [
  { status: 0, label: '全部' },
  { status: 1, label: '未上线' },
  { status: 2, label: '报名未开始' },
  { status: 3, label: '报名中' },
  { status: 4, label: '推广未开始' },
  { status: 5, label: '推广中' },
  { status: 7, label: '报名结束' }
]

/** 抖店活动状态码 → 展示文案（不含 0，与 ActivityStatusResolver 一致） */
export const ACTIVITY_STATUS_LABEL_BY_CODE: Record<number, string> = {
  1: '未上线',
  2: '报名未开始',
  3: '报名中',
  4: '推广未开始',
  5: '推广中',
  7: '报名结束'
}

/** 抖店活动「推广中」状态码，与后端 ActivityPromotionSupport 一致 */
export const PROMOTING_ACTIVITY_STATUS_CODE = 5

function readActivityStatusCode(row: ActivityRow): number | null {
  const raw = row.activityStatus ?? row.status
  if (raw === undefined || raw === null || String(raw).trim() === '') {
    return null
  }
  const code = Number(raw)
  return Number.isFinite(code) && code > 0 ? code : null
}

/** 活动维度是否处于推广中：有状态码时只认码 5，避免与 statusText 重叠判定 */
export function isActivityPromoting(row: ActivityRow): boolean {
  const statusCode = readActivityStatusCode(row)
  if (statusCode != null) {
    return statusCode === PROMOTING_ACTIVITY_STATUS_CODE
  }
  const statusText = String(row.statusText ?? row.activity_status_text ?? '').trim()
  return statusText.includes('推广中')
}

/** 活动是否已分配招商组长 */
export function isActivityAssigned(row: ActivityRow): boolean {
  const assigneeId = String(
    row.recruiterUserId ?? row.activityAssigneeId ?? row.assigneeId ?? ''
  ).trim()
  return Boolean(assigneeId)
}

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

export type ActivityAssignmentFilter = 'all' | 'assigned' | 'unassigned' | 'mine'

export const ACTIVITY_ASSIGNMENT_FILTER_OPTIONS: Array<{ label: string; value: ActivityAssignmentFilter }> = [
  { label: '全部活动', value: 'all' },
  { label: '已分配', value: 'assigned' },
  { label: '未分配', value: 'unassigned' },
  { label: '分配给我', value: 'mine' }
]

export const RECRUITER_MINE_ASSIGNMENT_FILTER_OPTIONS: Array<{ label: string; value: ActivityAssignmentFilter }> = [
  { label: '分配给我', value: 'mine' }
]

/** 按角色解析活动列表默认分配筛选 */
export function resolveDefaultAssignmentFilter(isAdmin: boolean): ActivityAssignmentFilter {
  return isAdmin ? 'all' : 'mine'
}

/** 按角色返回可见的分配筛选项 */
export function resolveAssignmentFilterOptions(isAdmin: boolean) {
  return isAdmin ? ACTIVITY_ASSIGNMENT_FILTER_OPTIONS : RECRUITER_MINE_ASSIGNMENT_FILTER_OPTIONS
}

/** 非 admin 固定 mine，admin 使用当前筛选值 */
export function resolveEffectiveAssignmentFilter(
  isAdmin: boolean,
  filter: ActivityAssignmentFilter
): ActivityAssignmentFilter {
  return isAdmin ? filter || 'all' : 'mine'
}

export function formatActivitySyncTime(value: unknown): string {
  const text = String(value ?? '').trim()
  if (!text) return '—'
  const normalized = text.replace('T', ' ').replace(/\.\d+Z?$/, '')
  return normalized.length >= 16 ? normalized.slice(0, 16) : normalized
}

export function resolveActivitySyncTime(row: ActivityRow): string {
  return formatActivitySyncTime(row.activityStatusSyncedAt ?? row.activity_status_synced_at ?? row.lastSyncAt ?? row.last_sync_at)
}

/**
 * 根据状态码解析活动状态标签（仅展示用，不把筛选 Tab「全部」当作活动状态）。
 */
export function resolveActivityStatusLabel(row: ActivityRow): string {
  const statusCode = readActivityStatusCode(row)
  if (statusCode == null) return '未知'
  return ACTIVITY_STATUS_LABEL_BY_CODE[statusCode] ?? '未知'
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
