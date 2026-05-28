/**
 * 商品负责人选项加载 - 单元测试
 *
 * 测试场景：
 * 1. 从用户主数据加载招商候选人，验证关键字规范化和 label 格式
 * 2. 空关键字时省略 keyword 参数
 */
import { describe, expect, it, vi } from 'vitest'

import { getUserMasterRecruiters } from '../../api/sys'
import { loadProductAssigneeOptions } from './product-assignee-options'

/** 模拟用户主数据接口 */
vi.mock('../../api/sys', () => ({
  getUserMasterRecruiters: vi.fn()
}))

describe('商品负责人选项加载（product assignee options）', () => {
  it('从用户主数据加载招商候选人，关键字应被 trim，label 格式为"姓名 (用户名)"', async () => {
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

    /* 验证搜索关键字被正确 trim */
    expect(getUserMasterRecruiters).toHaveBeenCalledWith({
      keyword: '招',
      limit: 50
    })
    /* 验证 label 格式：有真实姓名时为 "姓名 (用户名)"，仅有用户名时直接显示用户名 */
    expect(options).toEqual([
      { label: '张三 (zhangsan)', value: 'USER-1' },
      { label: 'lisi', value: 'USER-2' }
    ])
  })

  it('空关键字加载初始候选人时，keyword 参数应为 undefined', async () => {
    vi.mocked(getUserMasterRecruiters).mockResolvedValueOnce({ data: [] } as any)

    await loadProductAssigneeOptions('')

    /* 验证空关键字时不传递 keyword 参数 */
    expect(getUserMasterRecruiters).toHaveBeenCalledWith({
      keyword: undefined,
      limit: 50
    })
  })
})
