import { describe, expect, it } from 'vitest'

import {
  activityProductStageToAllianceStatus,
  activityProductStageToOfficialStatus,
  buildActivityProductStatusStages,
  countActivityProductStatusGroups,
  formatActivityProductLoadedSummary,
  isActivityProductStageMatch,
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

  it('formats loaded count with backend total when available', () => {
    expect(formatActivityProductLoadedSummary(20, 45)).toBe('已加载 20 / 共 45 个商品')
    expect(formatActivityProductLoadedSummary(0, 0)).toBe('已加载 0 / 共 0 个商品')
    expect(formatActivityProductLoadedSummary(8, null)).toBe('已加载 8 个商品')
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

  it('does not map unsupported status 4 into terminated activity status', () => {
    expect(isActivityProductStageMatch({ status: 4, statusText: '合作前取消' }, 'terminated')).toBe(false)
    expect(resolveActivityProductOfficialStatusView({ status: 4, statusText: '合作前取消' })).not.toMatchObject({
      status: 'TERMINATED'
    })
  })
})
