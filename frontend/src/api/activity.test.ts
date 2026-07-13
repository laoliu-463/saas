import { beforeEach, describe, expect, it, vi } from 'vitest'
import { assignColonelActivity, getColonelActivityPage, triggerActivityListSync, getActivitySyncJob } from './activity'
import request from '../utils/request'

vi.mock('../utils/request', () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
    post: vi.fn()
  }
}))

describe('activity api', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getColonelActivityPage calls colonel activities list', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: { activityList: [] } })
    await getColonelActivityPage({ page: 1, pageSize: 20 })
    expect(request.get).toHaveBeenCalledWith('/colonel/activities', { params: { page: 1, pageSize: 20 } })
  })

  it('assignColonelActivity sends assignee payload', async () => {
    vi.mocked(request.put).mockResolvedValue({ data: { activityId: '100018' } })
    await assignColonelActivity('100018', { assigneeId: '22222222-2222-2222-2222-222222222222' })
    expect(request.put).toHaveBeenCalledWith('/colonel/activities/100018/assignee', {
      assigneeId: '22222222-2222-2222-2222-222222222222'
    })
  })

  it('triggerActivityListSync posts to the activity sync endpoint', async () => {
    vi.mocked(request.post).mockResolvedValue({ data: { jobId: 'job-123' } })
    await triggerActivityListSync()
    expect(request.post).toHaveBeenCalledWith('/colonel/activities/sync')
  })

  it('getActivitySyncJob gets job status', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: { status: 'SUCCESS' } })
    await getActivitySyncJob('job-123')
    expect(request.get).toHaveBeenCalledWith('/colonel/activities/sync-jobs/job-123')
  })
})
