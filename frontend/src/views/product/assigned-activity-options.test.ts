import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  loadAssignedActivityOptions,
  mapActivityRowsToAssignedOptions
} from './assigned-activity-options'

vi.mock('../../api/activity', () => ({
  getColonelActivityPage: vi.fn()
}))

import { getColonelActivityPage } from '../../api/activity'

describe('assigned-activity-options', () => {
  beforeEach(() => {
    vi.mocked(getColonelActivityPage).mockReset()
  })

  it('mapActivityRowsToAssignedOptions dedupes and formats labels', () => {
    const options = mapActivityRowsToAssignedOptions([
      { activityId: '1001', activityName: '春季招商' },
      { activityId: '1001', activityName: '重复' },
      { activityId: '1002', activityName: '' }
    ])
    expect(options).toEqual([
      { label: '春季招商 (1001)', value: '1001' },
      { label: '1002', value: '1002' }
    ])
  })

  it('loadAssignedActivityOptions uses assigned for admin', async () => {
    vi.mocked(getColonelActivityPage).mockResolvedValue({
      data: { total: 1, activityList: [{ activityId: '88', activityName: '测试活动' }] }
    } as any)
    const options = await loadAssignedActivityOptions(true)
    expect(getColonelActivityPage).toHaveBeenCalledWith(
      expect.objectContaining({ assignmentFilter: 'assigned', pageSize: 20, page: 1 })
    )
    expect(options).toEqual([{ label: '测试活动 (88)', value: '88' }])
  })

  it('loadAssignedActivityOptions paginates until total is reached', async () => {
    vi.mocked(getColonelActivityPage)
      .mockResolvedValueOnce({
        data: {
          total: 25,
          activityList: Array.from({ length: 20 }, (_, index) => ({
            activityId: String(index + 1),
            activityName: `活动${index + 1}`
          }))
        }
      } as any)
      .mockResolvedValueOnce({
        data: {
          total: 25,
          activityList: Array.from({ length: 5 }, (_, index) => ({
            activityId: String(index + 21),
            activityName: `活动${index + 21}`
          }))
        }
      } as any)

    const options = await loadAssignedActivityOptions(false)
    expect(getColonelActivityPage).toHaveBeenCalledTimes(2)
    expect(options).toHaveLength(25)
  })

  it('loadAssignedActivityOptions uses mine for recruiter roles', async () => {
    vi.mocked(getColonelActivityPage).mockResolvedValue({
      data: { activityList: [] }
    } as any)
    await loadAssignedActivityOptions(false)
    expect(getColonelActivityPage).toHaveBeenCalledWith(
      expect.objectContaining({ assignmentFilter: 'mine' })
    )
  })
})
