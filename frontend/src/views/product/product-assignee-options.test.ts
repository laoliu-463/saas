import { describe, expect, it, vi } from 'vitest'

import { getUserMasterRecruiters } from '../../api/sys'
import { loadProductAssigneeOptions } from './product-assignee-options'

vi.mock('../../api/sys', () => ({
  getUserMasterRecruiters: vi.fn()
}))

describe('product assignee options', () => {
  it('loads recruiter candidates from user master data', async () => {
    vi.mocked(getUserMasterRecruiters).mockResolvedValueOnce({
      data: [
        {
          id: 'USER-1',
          realName: '张三',
          username: 'zhangsan',
          roleCodes: ['biz_staff']
        },
        {
          id: 'USER-2',
          realName: '',
          username: 'lisi',
          roleCodes: ['biz_staff']
        }
      ]
    } as any)

    const options = await loadProductAssigneeOptions(' 招 ')

    expect(getUserMasterRecruiters).toHaveBeenCalledWith({
      keyword: '招',
      limit: 50
    })
    expect(options).toEqual([
      { label: '张三 (zhangsan)', value: 'USER-1' },
      { label: 'lisi', value: 'USER-2' }
    ])
  })

  it('omits empty keyword when loading initial recruiter candidates', async () => {
    vi.mocked(getUserMasterRecruiters).mockResolvedValueOnce({ data: [] } as any)

    await loadProductAssigneeOptions('')

    expect(getUserMasterRecruiters).toHaveBeenCalledWith({
      keyword: undefined,
      limit: 50
    })
  })
})
