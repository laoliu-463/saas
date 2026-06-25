import { describe, expect, it, vi, beforeEach } from 'vitest'
import {
  batchSyncActivityProducts,
  formatActivityProductSyncMessage,
  ACTIVITY_PRODUCT_SYNC_MAX_POLLS,
  getActivityProductSyncPollDelayMs,
  POST_SYNC_REFRESH_DELAYS_MS,
  isActivityProductSyncSuccess,
  isActivityProductSyncTerminal,
  shouldPollActivityProductSyncJob,
  shouldSchedulePostSyncRefresh,
  summarizeActivityProductSyncResults
} from './activity-sync'

vi.mock('../../api/activityProduct', () => ({
  syncActivityProducts: vi.fn()
}))

import { syncActivityProducts } from '../../api/activityProduct'

describe('activity-sync', () => {
  beforeEach(() => {
    vi.mocked(syncActivityProducts).mockReset()
    vi.mocked(syncActivityProducts).mockResolvedValue({ data: { syncStatus: 'ACCEPTED' } } as never)
  })

  it('submits selected activities to the background sync endpoint sequentially', async () => {
    const summary = await batchSyncActivityProducts(['1001', '1002'])
    expect(syncActivityProducts).toHaveBeenCalledTimes(2)
    expect(syncActivityProducts).toHaveBeenNthCalledWith(1, '1001')
    expect(syncActivityProducts).toHaveBeenNthCalledWith(2, '1002')
    expect(summary.succeeded).toBe(2)
    expect(summary.failed).toBe(0)
  })

  it('summarizes mixed sync results', () => {
    const summary = summarizeActivityProductSyncResults([
      {
        activityId: '1',
        ok: true,
        syncedProductCount: 12,
        libraryEntryCount: 12
      },
      { activityId: '2', ok: true, syncedProductCount: 5 },
      { activityId: '3', ok: false, error: 'fail' }
    ])
    expect(summary).toMatchObject({
      succeeded: 2,
      failed: 1,
      queued: 0,
      running: 0,
      totalSyncedProducts: 17,
      totalLibraryEntries: 12
    })
    const message = formatActivityProductSyncMessage(summary)
    expect(message).toContain('已提交 2 个活动商品后台同步')
    expect(message).toContain('共拉取 17 个商品')
    expect(message).toContain('12 个已进入商品库')
    expect(message).not.toContain('尚未分配招商')
    expect(message).toContain('1 个活动同步失败')
  })

  it('mentions activities that are already syncing', () => {
    const summary = summarizeActivityProductSyncResults([
      { activityId: '1', ok: true, syncStatus: 'QUEUED' },
      { activityId: '2', ok: true, syncStatus: 'RUNNING' },
      { activityId: '2', ok: true, syncStatus: 'ACCEPTED' }
    ])
    expect(summary.queued).toBe(1)
    expect(summary.running).toBe(1)
    expect(formatActivityProductSyncMessage(summary)).toContain('1 个活动已在同步中')
    expect(formatActivityProductSyncMessage(summary)).toContain('1 个活动已排队')
  })

  it('schedules short polling refreshes only while background sync can still update data', () => {
    expect(POST_SYNC_REFRESH_DELAYS_MS).toEqual([1500, 4000, 8000, 15000])
    expect(shouldSchedulePostSyncRefresh('ACCEPTED')).toBe(true)
    expect(shouldSchedulePostSyncRefresh('QUEUED')).toBe(true)
    expect(shouldSchedulePostSyncRefresh('RUNNING')).toBe(true)
    expect(shouldSchedulePostSyncRefresh('SUCCESS')).toBe(false)
    expect(shouldSchedulePostSyncRefresh('FAILED')).toBe(false)
    expect(shouldSchedulePostSyncRefresh()).toBe(false)
  })

  it('classifies manual sync job statuses for completion polling', () => {
    expect(getActivityProductSyncPollDelayMs(0)).toBe(1000)
    expect(getActivityProductSyncPollDelayMs(9)).toBe(1000)
    expect(getActivityProductSyncPollDelayMs(10)).toBe(3000)
    expect(getActivityProductSyncPollDelayMs(29)).toBe(3000)
    expect(getActivityProductSyncPollDelayMs(30)).toBe(10000)
    expect(ACTIVITY_PRODUCT_SYNC_MAX_POLLS).toBe(89)
    expect(shouldPollActivityProductSyncJob('ACCEPTED')).toBe(true)
    expect(shouldPollActivityProductSyncJob('QUEUED')).toBe(true)
    expect(shouldPollActivityProductSyncJob('RUNNING')).toBe(true)
    expect(shouldPollActivityProductSyncJob('SUCCESS')).toBe(false)
    expect(isActivityProductSyncSuccess('SUCCESS')).toBe(true)
    expect(isActivityProductSyncTerminal('SUCCESS')).toBe(true)
    expect(isActivityProductSyncTerminal('PARTIAL')).toBe(true)
    expect(isActivityProductSyncTerminal('FAILED')).toBe(true)
    expect(isActivityProductSyncTerminal('ABANDONED')).toBe(true)
    expect(isActivityProductSyncTerminal('CANCELED')).toBe(true)
    expect(isActivityProductSyncTerminal('TIMEOUT')).toBe(true)
    expect(isActivityProductSyncTerminal('QUEUE_FULL')).toBe(true)
    expect(isActivityProductSyncTerminal('RUNNING')).toBe(false)
    expect(isActivityProductSyncTerminal('QUEUED')).toBe(false)
  })

  it('treats queue full as a failed batch item instead of submitted work', () => {
    const summary = summarizeActivityProductSyncResults([
      { activityId: '1', ok: true, syncStatus: 'QUEUE_FULL' },
      { activityId: '2', ok: true, syncStatus: 'QUEUED' }
    ])
    expect(summary.succeeded).toBe(1)
    expect(summary.failed).toBe(1)
    expect(summary.queued).toBe(1)
    expect(formatActivityProductSyncMessage(summary)).toContain('1 个活动同步失败')
  })
})
