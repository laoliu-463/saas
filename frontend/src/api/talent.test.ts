import { beforeEach, describe, expect, it, vi } from 'vitest'

import request from '../utils/request'
import { getPresetTalentTags, getTalentStatusTransitions } from './talent'

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
})
