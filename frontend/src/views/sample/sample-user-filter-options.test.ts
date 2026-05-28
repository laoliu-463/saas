/**
 * 寄样用户筛选选项单元测试
 *
 * 验证渠道和招商人员筛选选项的加载、数据映射、关键词标准化等功能。
 * 通过 vi.mock 模拟用户主数据 API 返回不同格式的用户数据。
 */
import { describe, expect, it, vi } from 'vitest'

import { getUserMasterChannels, getUserMasterRecruiters } from '../../api/sys'
import { loadSampleChannelOptions, loadSampleRecruiterOptions } from './sample-user-filter-options'

/** 模拟用户主数据 API，返回可控的测试数据 */
vi.mock('../../api/sys', () => ({
  getUserMasterChannels: vi.fn(),
  getUserMasterRecruiters: vi.fn()
}))

describe('sample user filter options', () => {
  it('loads channel candidates from user master data', async () => {
    // 模拟渠道人员 API 返回：一个有姓名+用户名，一个仅有用户名
    vi.mocked(getUserMasterChannels).mockResolvedValueOnce({
      data: [
        { id: 'CHANNEL-1', realName: '渠道甲', username: 'channel_a' },
        { id: 'CHANNEL-2', realName: '', username: 'channel_b' }
      ]
    } as any)

    // 调用加载函数，传入带空格的关键词验证 trim 处理
    const options = await loadSampleChannelOptions(' 渠 ')

    // 验证关键词被正确 trim 后传给 API，且 limit 为 50
    expect(getUserMasterChannels).toHaveBeenCalledWith({
      keyword: '渠',
      limit: 50
    })
    // 验证选项映射：有 realName+username 时显示「姓名 (用户名)」格式
    expect(options).toEqual([
      { label: '渠道甲 (channel_a)', value: 'CHANNEL-1' },
      { label: 'channel_b', value: 'CHANNEL-2' }
    ])
  })

  it('loads recruiter candidates from user master data', async () => {
    // 模拟招商人员 API 返回单条记录
    vi.mocked(getUserMasterRecruiters).mockResolvedValueOnce({
      data: [
        { id: 'BIZ-1', realName: '招商甲', username: 'biz_a' }
      ]
    } as any)

    // 空关键词应转换为 undefined 传给 API
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
