import { describe, expect, it } from 'vitest'
import { pickDashboardTrack, resolveDualTrackMetrics } from './dashboard-metrics'

describe('resolveDualTrackMetrics', () => {
  it('unwraps estimate and settle tracks from the API envelope', () => {
    const payload = {
      code: 200,
      data: {
        estimate: { todayOrderCount: 12, trend7d: [{ date: '2026-05-24' }] },
        settle: { todayOrderCount: 3, trend7d: [{ date: '2026-05-24' }] }
      }
    }

    expect(resolveDualTrackMetrics(payload)).toEqual({
      estimate: { todayOrderCount: 12, trend7d: [{ date: '2026-05-24' }] },
      settle: { todayOrderCount: 3, trend7d: [{ date: '2026-05-24' }] }
    })
  })

  it('returns empty tracks when payload is missing nested metrics', () => {
    expect(resolveDualTrackMetrics({ code: 200, data: {} })).toEqual({
      estimate: {},
      settle: {}
    })
  })
})

describe('pickDashboardTrack', () => {
  it('selects estimate for createTime and settle for settleTime', () => {
    const payload = {
      data: {
        estimate: { todayOrderCount: 8 },
        settle: { todayOrderCount: 2 }
      }
    }

    expect(pickDashboardTrack(payload, 'createTime')).toEqual({ todayOrderCount: 8 })
    expect(pickDashboardTrack(payload, 'settleTime')).toEqual({ todayOrderCount: 2 })
  })
})
