import { syncActivityProducts } from '../../api/activityProduct'
import { type ActivityRow } from './activity-list-display'

export const POST_SYNC_REFRESH_DELAYS_MS = [1500, 4000, 8000, 15000]
export const ACTIVITY_PRODUCT_SYNC_MAX_POLLS = 89

export const getActivityProductSyncPollDelayMs = (attempt: number) => {
  if (attempt < 10) return 500
  if (attempt < 30) return 3000
  return 10000
}

export function shouldSchedulePostSyncRefresh(syncStatus?: string): boolean {
  return syncStatus === 'ACCEPTED' || syncStatus === 'QUEUED' || syncStatus === 'RUNNING'
}

export function shouldPollActivityProductSyncJob(syncStatus?: string): boolean {
  return syncStatus === 'ACCEPTED' || syncStatus === 'QUEUED' || syncStatus === 'RUNNING'
}

export function isActivityProductSyncSuccess(syncStatus?: string): boolean {
  return syncStatus === 'SUCCESS'
}

export function isActivityProductSyncTerminal(syncStatus?: string): boolean {
  return ['SUCCESS', 'PARTIAL', 'FAILED', 'FAILED_LOCKED', 'ABANDONED', 'CANCELED', 'TIMEOUT', 'QUEUE_FULL'].includes(String(syncStatus || ''))
}

export type ActivityProductSyncNoticeType = 'info' | 'success' | 'warning'

export type ActivityProductSyncNotice = {
  type: ActivityProductSyncNoticeType
  text: string
}

export function getActivityProductSyncNotice(syncStatus?: string): ActivityProductSyncNotice {
  switch (String(syncStatus || '').trim().toUpperCase()) {
    case 'SUCCESS':
      return { type: 'success', text: '商品同步完成，商品状态已更新。' }
    case 'PARTIAL':
      return { type: 'warning', text: '商品同步部分完成，当前可用商品状态已更新。' }
    case 'FAILED':
    case 'FAILED_LOCKED':
    case 'ABANDONED':
    case 'CANCELED':
    case 'TIMEOUT':
    case 'QUEUE_FULL':
      return { type: 'warning', text: '商品同步未完成，请稍后重试或查看后台日志。' }
    default:
      return { type: 'info', text: '商品同步进行中，已完成的商品状态会实时刷新到列表。' }
  }
}

export type ActivityProductSyncItemResult = {
  activityId: string
  ok: boolean
  error?: string
  jobId?: string
  syncStatus?: string
  syncedProductCount?: number
  libraryEntryCount?: number
}

export type ActivityProductSyncBatchSummary = {
  results: ActivityProductSyncItemResult[]
  succeeded: number
  failed: number
  queued: number
  running: number
  totalSyncedProducts: number
  totalLibraryEntries: number
}

type ActivityProductSyncStats = {
  jobId?: string
  syncStatus?: string
  syncedProductCount?: number
  libraryEntryCount?: number
}

function readSyncStats(payload: unknown): ActivityProductSyncStats {
  if (!payload || typeof payload !== 'object') return {}
  const data = payload as Record<string, unknown>
  const syncStats = (payload as Record<string, unknown>).syncStats
  const stats = syncStats && typeof syncStats === 'object'
    ? syncStats as Record<string, unknown>
    : {}
  return {
    jobId: typeof data.jobId === 'string' ? data.jobId : undefined,
    syncStatus: typeof data.syncStatus === 'string' ? data.syncStatus : undefined,
    syncedProductCount: Number(stats.syncedProductCount ?? 0) || undefined,
    libraryEntryCount: Number(stats.libraryEntryCount ?? 0) || undefined
  }
}

export async function syncSingleActivityProducts(
  activityId: string,
  row?: ActivityRow
): Promise<ActivityProductSyncItemResult> {
  const normalizedId = String(activityId || '').trim()
  void row
  if (!normalizedId) {
    return {
      activityId: normalizedId,
      ok: false,
      error: '活动 ID 无效'
    }
  }
  try {
    const res: any = await syncActivityProducts(normalizedId)
    const stats = readSyncStats(res?.data)
    return {
      activityId: normalizedId,
      ok: true,
      jobId: stats.jobId,
      syncStatus: stats.syncStatus,
      syncedProductCount: stats.syncedProductCount,
      libraryEntryCount: stats.libraryEntryCount
    }
  } catch (err: unknown) {
    const error = err instanceof Error ? err.message : '同步失败'
    return {
      activityId: normalizedId,
      ok: false,
      error
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
  let queued = 0
  let running = 0
  let totalSyncedProducts = 0
  let totalLibraryEntries = 0
  results.forEach((item) => {
    if (item.ok && item.syncStatus !== 'QUEUE_FULL') {
      succeeded += 1
      if (item.syncStatus === 'QUEUED') {
        queued += 1
      }
      if (item.syncStatus === 'RUNNING') {
        running += 1
      }
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
    queued,
    running,
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
  const parts = [`已提交 ${summary.succeeded} 个活动商品后台同步`]
  if (summary.queued > 0) {
    parts.push(`${summary.queued} 个活动已排队`)
  }
  if (summary.running > 0) {
    parts.push(`${summary.running} 个活动已在同步中`)
  }
  if (summary.totalSyncedProducts > 0) {
    parts.push(`共拉取 ${summary.totalSyncedProducts} 个商品`)
  }
  if (summary.totalLibraryEntries > 0) {
    parts.push(`其中 ${summary.totalLibraryEntries} 个已进入商品库`)
  }
  if (summary.failed > 0) {
    parts.push(`${summary.failed} 个活动同步失败`)
  }
  return parts.join('；')
}
