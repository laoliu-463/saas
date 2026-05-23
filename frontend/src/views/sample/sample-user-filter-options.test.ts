import { describe, expect, it, vi } from 'vitest'

import { getUserMasterChannels, getUserMasterRecruiters } from '../../api/sys'
import { loadSampleChannelOptions, loadSampleRecruiterOptions } from './sample-user-filter-options'

vi.mock('../../api/sys', () => ({
  getUserMasterChannels: vi.fn(),
  getUserMasterRecruiters: vi.fn()
}))

describe('sample user filter options', () => {
  it('loads channel candidates from user master data', async () => {
    vi.mocked(getUserMasterChannels).mockResolvedValueOnce({
      data: [
        { id: 'CHANNEL-1', realName: '渠道甲', username: 'channel_a' },
        { id: 'CHANNEL-2', realName: '', username: 'channel_b' }
      ]
    } as any)

    const options = await loadSampleChannelOptions(' 渠 ')

    expect(getUserMasterChannels).toHaveBeenCalledWith({
      keyword: '渠',
      limit: 50
    })
    expect(options).toEqual([
      { label: '渠道甲 (channel_a)', value: 'CHANNEL-1' },
      { label: 'channel_b', value: 'CHANNEL-2' }
    ])
  })

  it('loads recruiter candidates from user master data', async () => {
    vi.mocked(getUserMasterRecruiters).mockResolvedValueOnce({
      data: [
        { id: 'BIZ-1', realName: '招商甲', username: 'biz_a' }
      ]
    } as any)

    const options = await loadSampleRecruiterOptions('')

    expect(getUserMasterRecruiters).toHaveBeenCalledWith({
      keyword: undefined,
      limit: 50
    })
    expect(options).toEqual([
      { label: '招商甲 (biz_a)', value: 'BIZ-1' }
    ])
  })
})
