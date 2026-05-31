import { getActivityProducts } from '../../api/activityProduct'
import {
  type ActivityRow,
  isActivityAssigned,
  isActivityPromoting,
  shouldForceLibraryDisplayFromRow
} from './activity-list-display'

export type ActivityProductSyncItemResult = {
  activityId: string
  ok: boolean
  error?: string
  autoLibraryEligible: boolean
  promotingWithoutAssignee: boolean
  syncedProductCount?: number
  libraryEntryCount?: number
}

export type ActivityProductSyncBatchSummary = {
  results: ActivityProductSyncItemResult[]
  succeeded: number
  failed: number
  autoLibraryCount: number
  promotingWithoutAssigneeCount: number
  totalSyncedProducts: number
  totalLibraryEntries: number
}

const ACTIVITY_PRODUCT_SYNC_QUERY = {
  count: 20,
  retrieveMode: 1,
  refresh: true
} as const

type ActivityProductSyncStats = {
  syncedProductCount?: number
  libraryEntryCount?: number
  autoLibraryEligible?: boolean
}

export function resolveActivitySyncFlags(row: ActivityRow | undefined): {
  autoLibraryEligible: boolean
  promotingWithoutAssignee: boolean
} {
  if (!row) {
    return { autoLibraryEligible: false, promotingWithoutAssignee: false }
  }
  const autoLibraryEligible = shouldForceLibraryDisplayFromRow(row)
  const promotingWithoutAssignee = isActivityPromoting(row) && !isActivityAssigned(row)
  return { autoLibraryEligible, promotingWithoutAssignee }
}

function readSyncStats(payload: unknown): ActivityProductSyncStats {
  if (!payload || typeof payload !== 'object') return {}
  const syncStats = (payload as Record<string, unknown>).syncStats
  if (!syncStats || typeof syncStats !== 'object') return {}
  const stats = syncStats as Record<string, unknown>
  return {
    syncedProductCount: Number(stats.syncedProductCount ?? 0) || undefined,
    libraryEntryCount: Number(stats.libraryEntryCount ?? 0) || undefined,
    autoLibraryEligible: stats.autoLibraryEligible === true
  }
}

export async function syncSingleActivityProducts(
  activityId: string,
  row?: ActivityRow
): Promise<ActivityProductSyncItemResult> {
  const normalizedId = String(activityId || '').trim()
  const flags = resolveActivitySyncFlags(row)
  if (!normalizedId) {
    return {
      activityId: normalizedId,
      ok: false,
      error: '活动 ID 无效',
      autoLibraryEligible: false,
      promotingWithoutAssignee: false
    }
  }
  try {
    const res: any = await getActivityProducts(normalizedId, ACTIVITY_PRODUCT_SYNC_QUERY)
    const stats = readSyncStats(res?.data)
    const autoLibraryEligible = stats.autoLibraryEligible ?? flags.autoLibraryEligible
    return {
      activityId: normalizedId,
      ok: true,
      autoLibraryEligible,
      promotingWithoutAssignee: flags.promotingWithoutAssignee,
      syncedProductCount: stats.syncedProductCount,
      libraryEntryCount: stats.libraryEntryCount
    }
  } catch (err: unknown) {
    const error = err instanceof Error ? err.message : '同步失败'
    return {
      activityId: normalizedId,
      ok: false,
      error,
      autoLibraryEligible: flags.autoLibraryEligible,
      promotingWithoutAssignee: flags.promotingWithoutAssignee
    }
  }
}

export async function batchSyncActivityProducts(
  activityIds: Array<string | number>,
  rowByActivityId: Map<string, ActivityRow> = new Map()
): Promise<ActivityProductSyncBatchSummary> {
  const results: ActivityProductSyncItemResult[] = []
  for (const rawId of activityIds) {
    const activityId = String(rawId ?? '').trim()
    if (!activityId) continue
    const row = rowByActivityId.get(activityId)
    // eslint-disable-next-line no-await-in-loop
    results.push(await syncSingleActivityProducts(activityId, row))
  }
  return summarizeActivityProductSyncResults(results)
}

export function summarizeActivityProductSyncResults(
  results: ActivityProductSyncItemResult[]
): ActivityProductSyncBatchSummary {
  let succeeded = 0
  let failed = 0
  let autoLibraryCount = 0
  let promotingWithoutAssigneeCount = 0
  let totalSyncedProducts = 0
  let totalLibraryEntries = 0
  results.forEach((item) => {
    if (item.ok) {
      succeeded += 1
      if (item.autoLibraryEligible) autoLibraryCount += 1
      if (item.promotingWithoutAssignee) promotingWithoutAssigneeCount += 1
      totalSyncedProducts += Number(item.syncedProductCount ?? 0)
      totalLibraryEntries += Number(item.libraryEntryCount ?? 0)
    } else {
      failed += 1
    }
  })
  return {
    results,
    succeeded,
    failed,
    autoLibraryCount,
    promotingWithoutAssigneeCount,
    totalSyncedProducts,
    totalLibraryEntries
  }
}

export function formatActivityProductSyncMessage(summary: ActivityProductSyncBatchSummary): string {
  if (!summary.results.length) {
    return '请先选择活动'
  }
  if (summary.succeeded === 0) {
    return '活动商品同步失败，请稍后重试'
  }
  const parts = [`已同步 ${summary.succeeded} 个活动`]
  if (summary.totalSyncedProducts > 0) {
    parts.push(`共拉取 ${summary.totalSyncedProducts} 个商品`)
  }
  if (summary.totalLibraryEntries > 0) {
    parts.push(`其中 ${summary.totalLibraryEntries} 个已进入商品库`)
  } else if (summary.autoLibraryCount > 0) {
    parts.push('推广中且已分配招商的活动商品将自动进入商品库')
  }
  if (summary.promotingWithoutAssigneeCount > 0) {
    parts.push(`${summary.promotingWithoutAssigneeCount} 个推广中活动尚未分配招商，商品已同步但未自动入商品库`)
  }
  if (summary.failed > 0) {
    parts.push(`${summary.failed} 个活动同步失败`)
  }
  return parts.join('；')
}
