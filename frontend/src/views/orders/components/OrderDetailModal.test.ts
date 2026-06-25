import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getOrderDetail } from '../../../api/order'
import OrderDetailModal from './OrderDetailModal.vue'

vi.mock('../../../api/order', () => ({
  getOrderDetail: vi.fn()
}))

vi.mock('../../../utils/requestError', () => ({
  notifyApiFailure: vi.fn()
}))

vi.mock('naive-ui', () => ({
  useMessage: () => ({
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    info: vi.fn()
  })
}))

vi.mock('../../../components/StatusTag.vue', () => ({
  default: { template: '<span />' }
}))

const detailFixture = {
  orderId: 'ORDER-1',
  orderStatus: 1,
  orderStatusText: '待结算',
  attributionStatus: 'ATTRIBUTED',
  pickSource: 'pick-1',
  product: {
    productId: 'P-1',
    productName: '商品甲',
    activityId: 'ACT-1',
    activityName: '活动甲',
    colonelUserId: 'c-1',
    colonelName: '招商甲'
  },
  channel: { channelUserId: 'ch-1', channelName: '渠道甲' },
  talent: {
    talentId: 't-1',
    talentUid: 'uid-1',
    authorId: 'auth-1',
    talentName: '达人甲'
  },
  amount: {
    orderAmount: 1000,
    settleAmount: 900,
    estimateServiceFee: 50,
    effectiveServiceFee: 45
  },
  promotion: {
    pickSource: 'pick-1',
    mappingId: 'map-1',
    promotionUrl: 'https://example.com/p',
    matched: true
  },
  sample: {
    matched: false,
    sampleRequestId: null,
    sampleStatusText: '-',
    completedByOrderRule: false
  },
  diagnosis: {},
  time: {
    createTime: '2026-06-01T10:00:00',
    settleTime: null,
    syncTime: '2026-06-01T11:00:00'
  }
}

const globalStubs = {
  NModal: { template: '<div><slot /><slot name="footer" /></div>' },
  NSpin: { template: '<div><slot /></div>' },
  NAlert: { template: '<div><slot /></div>' },
  NDescriptions: { template: '<div><slot /></div>' },
  NDescriptionsItem: { template: '<div><slot /></div>' },
  NEmpty: { template: '<div />' },
  NButton: { template: '<button><slot /></button>' },
  NTag: { template: '<span><slot /></span>' }
}

describe('OrderDetailModal field sources', () => {
  beforeEach(() => {
    vi.mocked(getOrderDetail).mockResolvedValue(detailFixture)
  })

  it('renders section source hints aligned with backend tables', async () => {
    const wrapper = mount(OrderDetailModal, {
      props: { show: false, orderId: 'ORDER-1' },
      global: { stubs: globalStubs }
    })

    await wrapper.setProps({ show: true })
    await flushPromises()

    const sources = wrapper.findAll('.section-source')
    expect(sources.length).toBeGreaterThanOrEqual(7)
    expect(wrapper.text()).toContain('colonelsettlement_order')
    expect(wrapper.text()).toContain('pick_source_mapping')
    expect(wrapper.text()).toContain('sample_request')
  })
})
