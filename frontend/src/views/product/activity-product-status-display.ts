import type { ProductManageRow, ProductOfficialStatus } from '../../types/productManage'

import { resolveOfficialStatus } from './product-actions'

export type ActivityProductStatusStageKey =
  | 'all'
  | 'pendingReview'
  | 'promoting'
  | 'rejected'
  | 'terminated'
  | 'canceled'
  | 'expired'

export type ActivityProductStatusTagType = 'success' | 'warning' | 'error' | 'info' | 'default'

export interface ActivityProductStatusCounts {
  total: number
  pendingReview: number
  promoting: number
  rejected: number
  terminated: number
  canceled: number
  expired: number
}

export interface ActivityProductStatusView {
  status: ProductOfficialStatus
  label: string
  allianceStatus: string
  tagType: ActivityProductStatusTagType
  hint: string
}

export interface ActivityProductStatusStage {
  key: Exclude<ActivityProductStatusStageKey, 'all'>
  label: string
  count: number
  displayCount: string
  hint: string
  allianceStatus: string
  tagType: ActivityProductStatusTagType
}

const OFFICIAL_STATUS_VIEWS: Record<ProductOfficialStatus, ActivityProductStatusView> = {
  PENDING_REVIEW: {
    status: 'PENDING_REVIEW',
    label: '待审核',
    allianceStatus: 'pending_audit',
    tagType: 'warning',
    hint: '上游活动商品仍在审核或待审核'
  },
  PROMOTING: {
    status: 'PROMOTING',
    label: '推广中',
    allianceStatus: 'promoting',
    tagType: 'success',
    hint: '上游活动商品当前可推广'
  },
  REJECTED: {
    status: 'REJECTED',
    label: '申请未通过',
    allianceStatus: 'rejected',
    tagType: 'error',
    hint: '上游活动商品申请未通过'
  },
  TERMINATED: {
    status: 'TERMINATED',
    label: '合作已终止',
    allianceStatus: 'terminated',
    tagType: 'default',
    hint: '上游活动商品合作已终止'
  },
  CANCELED: {
    status: 'CANCELED',
    label: '合作前取消',
    allianceStatus: 'canceled',
    tagType: 'default',
    hint: '上游活动商品合作前取消'
  },
  EXPIRED: {
    status: 'EXPIRED',
    label: '合作已到期',
    allianceStatus: 'expired',
    tagType: 'info',
    hint: '上游活动商品合作已到期'
  }
}

const STAGE_ORDER: Array<Exclude<ActivityProductStatusStageKey, 'all'>> = [
  'pendingReview',
  'promoting',
  'rejected',
  'terminated',
  'canceled',
  'expired'
]

const STAGE_TO_OFFICIAL_STATUS: Record<Exclude<ActivityProductStatusStageKey, 'all'>, ProductOfficialStatus> = {
  pendingReview: 'PENDING_REVIEW',
  promoting: 'PROMOTING',
  rejected: 'REJECTED',
  terminated: 'TERMINATED',
  canceled: 'CANCELED',
  expired: 'EXPIRED'
}

export function resolveActivityProductOfficialStatusView(row: ProductManageRow): ActivityProductStatusView {
  return OFFICIAL_STATUS_VIEWS[resolveOfficialStatus(row)]
}

export function countActivityProductStatusGroups(rows: ProductManageRow[]): ActivityProductStatusCounts {
  const counts: ActivityProductStatusCounts = {
    total: rows.length,
    pendingReview: 0,
    promoting: 0,
    rejected: 0,
    terminated: 0,
    canceled: 0,
    expired: 0
  }

  rows.forEach((row) => {
    const status = resolveOfficialStatus(row)
    if (status === 'PENDING_REVIEW') counts.pendingReview += 1
    else if (status === 'PROMOTING') counts.promoting += 1
    else if (status === 'REJECTED') counts.rejected += 1
    else if (status === 'TERMINATED') counts.terminated += 1
    else if (status === 'CANCELED') counts.canceled += 1
    else if (status === 'EXPIRED') counts.expired += 1
  })

  return counts
}

function normalizeCount(value: unknown) {
  const count = Number(value)
  return Number.isFinite(count) && count > 0 ? Math.floor(count) : 0
}

export function normalizeActivityProductStatusCounts(
  value: Partial<ActivityProductStatusCounts> | null | undefined
): ActivityProductStatusCounts {
  return {
    total: normalizeCount(value?.total),
    pendingReview: normalizeCount(value?.pendingReview),
    promoting: normalizeCount(value?.promoting),
    rejected: normalizeCount(value?.rejected),
    terminated: normalizeCount(value?.terminated),
    canceled: normalizeCount(value?.canceled),
    expired: normalizeCount(value?.expired)
  }
}

export function formatActivityProductStatusCount(count: number): string {
  const normalized = normalizeCount(count)
  return normalized >= 100 ? '99+' : String(normalized)
}

export function formatActivityProductLoadSummary(loaded: number, total: number): string {
  return `已加载 ${normalizeCount(loaded)} / 共 ${normalizeCount(total)} 个商品`
}

export function buildActivityProductStatusStages(
  source: ProductManageRow[] | ActivityProductStatusCounts
): ActivityProductStatusStage[] {
  const counts = Array.isArray(source)
    ? countActivityProductStatusGroups(source)
    : normalizeActivityProductStatusCounts(source)
  return STAGE_ORDER.map((key) => {
    const view = OFFICIAL_STATUS_VIEWS[STAGE_TO_OFFICIAL_STATUS[key]]
    return {
      key,
      label: view.label,
      count: counts[key],
      displayCount: formatActivityProductStatusCount(counts[key]),
      hint: view.hint,
      allianceStatus: view.allianceStatus,
      tagType: view.tagType
    }
  })
}

export function activityProductStageToAllianceStatus(stage: ActivityProductStatusStageKey): string | null {
  if (stage === 'all') return null
  return OFFICIAL_STATUS_VIEWS[STAGE_TO_OFFICIAL_STATUS[stage]].allianceStatus
}

export function activityProductStageToOfficialStatus(stage: ActivityProductStatusStageKey): ProductOfficialStatus | null {
  if (stage === 'all') return null
  return STAGE_TO_OFFICIAL_STATUS[stage]
}

export function isActivityProductStageMatch(row: ProductManageRow, stage: ActivityProductStatusStageKey): boolean {
  if (stage === 'all') return true
  return resolveOfficialStatus(row) === STAGE_TO_OFFICIAL_STATUS[stage]
}
