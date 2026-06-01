import { describe, expect, it, vi, beforeEach } from 'vitest'
import {
  batchSyncActivityProducts,
  formatActivityProductSyncMessage,
  summarizeActivityProductSyncResults
} from './activity-sync'

vi.mock('../../api/activityProduct', () => ({
  getActivityProducts: vi.fn()
}))

import { getActivityProducts } from '../../api/activityProduct'

describe('activity-sync', () => {
  beforeEach(() => {
    vi.mocked(getActivityProducts).mockReset()
    vi.mocked(getActivityProducts).mockResolvedValue({ data: { items: [] } } as never)
  })

  it('batch syncs selected activities sequentially', async () => {
    const summary = await batchSyncActivityProducts(['1001', '1002'])
    expect(getActivityProducts).toHaveBeenCalledTimes(2)
    expect(getActivityProducts).toHaveBeenNthCalledWith(1, '1001', {
      count: 20,
      retrieveMode: 1,
      refresh: true
    })
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
      totalSyncedProducts: 17,
      totalLibraryEntries: 12
    })
    const message = formatActivityProductSyncMessage(summary)
    expect(message).toContain('已同步 2 个活动')
    expect(message).toContain('共拉取 17 个商品')
    expect(message).toContain('12 个已进入商品库')
    expect(message).not.toContain('尚未分配招商')
    expect(message).toContain('1 个活动同步失败')
  })
})
