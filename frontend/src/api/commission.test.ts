import { beforeEach, describe, expect, it, vi } from 'vitest'

import request from '../utils/request'
import {
  createCommissionRule,
  deleteCommissionRule,
  getCommissionRulePage,
  updateCommissionRule
} from './commission'

vi.mock('../utils/request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
}))

describe('commission rule API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('calls commission rule page endpoint with filters', () => {
    getCommissionRulePage({ page: 1, size: 20, dimensionType: 'product', commissionType: 'recruiter' })

    expect(request.get).toHaveBeenCalledWith('/commission-rules', {
      params: { page: 1, size: 20, dimensionType: 'product', commissionType: 'recruiter' }
    })
  })

  it('creates commission rules through the internal endpoint', () => {
    const payload = {
      dimensionType: 'activity',
      dimensionId: 'A-1',
      commissionType: 'channel',
      ratio: 0.18
    }

    createCommissionRule(payload)

    expect(request.post).toHaveBeenCalledWith('/commission-rules', payload)
  })

  it('updates commission rules by id', () => {
    const payload = {
      dimensionType: 'user',
      dimensionId: 'USER-1',
      commissionType: 'recruiter',
      ratio: 0.22
    }

    updateCommissionRule('RULE-1', payload)

    expect(request.put).toHaveBeenCalledWith('/commission-rules/RULE-1', payload)
  })

  it('deletes commission rules by id', () => {
    deleteCommissionRule('RULE-1')

    expect(request.delete).toHaveBeenCalledWith('/commission-rules/RULE-1')
  })
})
