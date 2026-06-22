import { describe, expect, it } from 'vitest'

import {
  activityProductStageToAllianceStatus,
  activityProductStageToOfficialStatus,
  buildActivityProductStatusStages,
  countActivityProductStatusGroups,
  formatActivityProductStatusCount,
  isActivityProductStageMatch,
  normalizeActivityProductStatusCounts,
  resolveActivityProductOfficialStatusView
} from './activity-product-status-display'

describe('activity-product-status-display', () => {
  it('counts activity products by upstream status instead of local library status', () => {
    const rows = [
      { productId: 'P-1', status: 1, statusText: '推广中', bizStatus: 'PENDING_AUDIT', selectedToLibrary: false },
      { productId: 'P-2', status: 0, statusText: '待审核', bizStatus: 'APPROVED', selectedToLibrary: true },
      { productId: 'P-3', status: 2, statusText: '申请未通过', bizStatus: 'APPROVED', selectedToLibrary: true },
      { productId: 'P-4', status: 3, statusText: '合作已终止', bizStatus: 'LINKED', selectedToLibrary: true },
      { productId: 'P-5', status: 6, statusText: '合作已到期', bizStatus: 'ASSIGNED', selectedToLibrary: true }
    ]

    expect(countActivityProductStatusGroups(rows)).toEqual({
      total: 5,
      pendingReview: 1,
      promoting: 1,
      rejected: 1,
      terminated: 1,
      expired: 1
    })
  })

  it('builds status stages aligned to upstream activity product query status params', () => {
    const stages = buildActivityProductStatusStages([
      { status: 1, statusText: '推广中' },
      { status: 1, statusText: '推广中' },
      { status: 2, statusText: '申请未通过' }
    ])

    expect(stages.map((stage) => [stage.key, stage.label, stage.count])).toEqual([
      ['pendingReview', '待审核', 0],
      ['promoting', '推广中', 2],
      ['rejected', '申请未通过', 1],
      ['terminated', '合作已终止', 0],
      ['expired', '合作已到期', 0]
    ])
    expect(activityProductStageToAllianceStatus('promoting')).toBe('promoting')
    expect(activityProductStageToAllianceStatus('pendingReview')).toBe('pending_audit')
    expect(activityProductStageToAllianceStatus('all')).toBeNull()
    expect(activityProductStageToOfficialStatus('terminated')).toBe('TERMINATED')
    expect(activityProductStageToOfficialStatus('all')).toBeNull()
  })

  it('uses backend full status counts and caps status labels at 99+', () => {
    const counts = normalizeActivityProductStatusCounts({
      total: 1274,
      pendingReview: 10,
      promoting: 726,
      rejected: 486,
      terminated: 46,
      expired: 6
    })
    const stages = buildActivityProductStatusStages(counts)

    expect(stages.map((stage) => [stage.key, stage.count, stage.displayCount])).toEqual([
      ['pendingReview', 10, '10'],
      ['promoting', 726, '99+'],
      ['rejected', 486, '99+'],
      ['terminated', 46, '46'],
      ['expired', 6, '6']
    ])
    expect(formatActivityProductStatusCount(99)).toBe('99')
    expect(formatActivityProductStatusCount(100)).toBe('99+')
  })

  it('matches rows by upstream status stage and exposes display labels', () => {
    expect(isActivityProductStageMatch({ status: 1, bizStatus: 'PENDING_AUDIT' }, 'promoting')).toBe(true)
    expect(isActivityProductStageMatch({ status: 0, statusText: '待审核', bizStatus: 'APPROVED' }, 'promoting')).toBe(false)
    expect(isActivityProductStageMatch({ statusText: '合作已到期' }, 'expired')).toBe(true)

    expect(resolveActivityProductOfficialStatusView({ status: 1, statusText: '推广中' })).toMatchObject({
      status: 'PROMOTING',
      label: '推广中',
      allianceStatus: 'promoting',
      tagType: 'success'
    })
  })

  it('maps upstream status 4 into terminated activity status', () => {
    expect(isActivityProductStageMatch({ status: 4, statusText: '合作前取消' }, 'terminated')).toBe(true)
    expect(resolveActivityProductOfficialStatusView({ status: 4, statusText: '合作前取消' })).toMatchObject({
      status: 'TERMINATED'
    })
  })
})
