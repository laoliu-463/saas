import { beforeEach, describe, expect, it, vi } from 'vitest'

import request from '../utils/request'
import {
  getPresetTalentTags,
  getTalentStatusTransitions,
  parsePrivateTalentPoolResponse,
  toPrivateTalentSelectOption
} from './talent'

vi.mock('../utils/request', () => ({
  default: {
    get: vi.fn(() => Promise.resolve({ data: [] }))
  }
}))

describe('talent API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('calls talent status transitions endpoint', () => {
    getTalentStatusTransitions()

    expect(request.get).toHaveBeenCalledWith('/talents/status-transitions')
  })

  it('loads preset talent tags from the internal talent endpoint', async () => {
    vi.mocked(request.get).mockResolvedValueOnce({ data: ['美妆', '高转化'] })

    await expect(getPresetTalentTags()).resolves.toEqual(['美妆', '高转化'])

    expect(request.get).toHaveBeenCalledWith('/talents/preset-tags')
  })

  it('parsePrivateTalentPoolResponse accepts array data from private pool API', () => {
    expect(parsePrivateTalentPoolResponse({ data: [{ id: '1' }] })).toHaveLength(1)
    expect(parsePrivateTalentPoolResponse({ data: { records: [{ id: '2' }] } })).toHaveLength(1)
    expect(parsePrivateTalentPoolResponse({ data: null })).toEqual([])
  })

  it('toPrivateTalentSelectOption prefers douyinUid as submit value', () => {
    expect(
      toPrivateTalentSelectOption({
        nickname: '达人A',
        douyinUid: 'dy_001',
        douyinNo: '12345',
        id: 'uuid-1'
      })
    ).toEqual({
      label: '达人A（12345）',
      value: 'dy_001'
    })
  })
})
