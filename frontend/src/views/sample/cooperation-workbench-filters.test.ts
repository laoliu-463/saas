import { describe, expect, it } from 'vitest'

import {
  buildCooperationSampleFilterParams,
  type CooperationWorkbenchFilters
} from './cooperation-workbench-filters'

const emptyFilters = (): CooperationWorkbenchFilters => ({
  productKeyword: '',
  shopKeyword: '',
  trackingNo: '',
  requestNo: '',
  talentKeyword: '',
  cooperationType: null,
  sampleOwnerType: null,
  homeworkType: null,
  recipientName: '',
  recipientPhone: '',
  channelUserId: null,
  recruiterUserId: null,
  applyRange: null,
  homeworkRange: null,
  logisticsCompany: null
})

describe('cooperation workbench filters', () => {
  it('builds /samples params for the cooperation order workbench', () => {
    const filters = emptyFilters()
    Object.assign(filters, {
      productKeyword: '10901825',
      shopKeyword: '演示店铺',
      trackingNo: 'SF123',
      requestNo: 'TEST-SAMPLE-001',
      talentKeyword: '达人A',
      cooperationType: 'FREE_SAMPLE',
      sampleOwnerType: 'MERCHANT',
      homeworkType: 'HAS_ORDER',
      recipientName: '张三',
      recipientPhone: '13800138000',
      channelUserId: 'channel-user',
      recruiterUserId: 'recruiter-user',
      applyRange: [Date.parse('2026-05-01T08:00:00'), Date.parse('2026-05-02T09:30:00')],
      homeworkRange: [Date.parse('2026-05-03T10:00:00'), Date.parse('2026-05-04T11:30:00')],
      logisticsCompany: 'SF'
    })

    expect(buildCooperationSampleFilterParams(filters, 'PENDING_SHIP')).toEqual({
      status: 'PENDING_SHIP',
      productKeyword: '10901825',
      shopKeyword: '演示店铺',
      trackingNo: 'SF123',
      requestNo: 'TEST-SAMPLE-001',
      talentKeyword: '达人A',
      cooperationType: 'FREE_SAMPLE',
      sampleOwnerType: 'MERCHANT',
      homeworkType: 'HAS_ORDER',
      recipientName: '张三',
      recipientPhone: '13800138000',
      channelUserId: 'channel-user',
      recruiterUserId: 'recruiter-user',
      applyStartTime: '2026-05-01T08:00:00',
      applyEndTime: '2026-05-02T09:30:00',
      homeworkStartTime: '2026-05-03T10:00:00',
      homeworkEndTime: '2026-05-04T11:30:00',
      logisticsCompany: 'SF'
    })
  })

  it('omits empty status for the all-tab view', () => {
    expect(buildCooperationSampleFilterParams(emptyFilters(), '')).toMatchObject({
      status: undefined,
      productKeyword: undefined,
      trackingNo: undefined
    })
  })
})
