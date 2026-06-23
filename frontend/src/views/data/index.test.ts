import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getMetrics } from '../../api/data'
import { getPerformanceSummary } from '../../api/performance'
import DataDashboard from './index.vue'

vi.mock('../../api/data', () => ({
  getMetrics: vi.fn()
}))

vi.mock('../../api/performance', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/performance')>()
  return {
    ...actual,
    getPerformanceSummary: vi.fn()
  }
})

vi.mock('../../composables/useRuntimeEnvironment', () => ({
  useRuntimeEnvironment: () => ({
    usesRealDouyinUpstream: { value: true },
    environmentLabel: { value: 'REAL-PRE' }
  })
}))

vi.mock('../../utils/requestError', () => ({
  handleApiFailure: vi.fn()
}))

vi.mock('echarts/core', () => ({
  use: vi.fn(),
  init: vi.fn(() => ({
    setOption: vi.fn(),
    resize: vi.fn(),
    dispose: vi.fn()
  }))
}))

vi.mock('echarts/charts', () => ({
  BarChart: {},
  LineChart: {}
}))

vi.mock('echarts/components', () => ({
  GridComponent: {},
  LegendComponent: {},
  TooltipComponent: {}
}))

vi.mock('echarts/renderers', () => ({
  CanvasRenderer: {}
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: vi.fn()
  })
}))

const messageApi = {
  warning: vi.fn()
}

vi.mock('naive-ui', async (importOriginal) => {
  const actual = await importOriginal<typeof import('naive-ui')>()
  return {
    ...actual,
    useMessage: () => messageApi
  }
})

const stubs = {
  PageHeader: {
    props: ['title', 'description'],
    template: '<header><h1>{{ title }}</h1><p>{{ description }}</p><slot name="actions" /></header>'
  },
  NAlert: { template: '<div><slot /></div>' },
  NButton: {
    emits: ['click'],
    template: '<button type="button" @click="$emit(\'click\', $event)"><slot /></button>'
  },
  NCard: { template: '<div><slot /></div>' },
  NRadioButton: {
    props: ['value'],
    template: '<button type="button"><slot /></button>'
  },
  NRadioGroup: { template: '<div><slot /></div>' },
  NSkeleton: { template: '<div />' },
  NSpin: { template: '<div><slot /></div>' }
}

const metricsPayload = {
  code: 200,
  data: {
    estimate: {
      todayOrderCount: 2,
      todayGmv: 300,
      totalOrders: 2,
      totalAmount: 300,
      serviceFee: '9.00',
      serviceFeeIncome: '10.00',
      techServiceFee: '1.00',
      commission: '3.00',
      talentCommission: '2.00',
      bizCommission: '1.00',
      channelCommission: '2.00',
      grossProfit: '6.00',
      refundOrderCount: 3,
      refundOrderAmount: '120.00',
      refundServiceFee: '4.50',
      amountTrack: 'estimate',
      trend7d: [{ date: '2026-06-04', orderCount: 2, gmv: 300 }]
    },
    settle: {
      todayOrderCount: 0,
      todayGmv: 0,
      totalOrders: 0,
      totalAmount: 0,
      serviceFee: '0.00',
      serviceFeeIncome: '0.00',
      techServiceFee: '0.00',
      commission: '0.00',
      talentCommission: '0.00',
      bizCommission: '0.00',
      channelCommission: '0.00',
      grossProfit: '0.00',
      refundOrderCount: 0,
      refundOrderAmount: '0.00',
      refundServiceFee: '0.00',
      amountTrack: 'effective',
      trend7d: []
    }
  }
}

describe('DataDashboard business metric matrix', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    const pinia = createPinia()
    setActivePinia(pinia)
    pinia.state.value.auth = {
      token: 'token',
      refreshToken: '',
      refreshExpiresIn: null,
      accessTokenExpiresIn: null,
      userInfo: { roleCodes: ['biz_leader'] }
    }
    vi.mocked(getMetrics).mockResolvedValue(metricsPayload as any)
    vi.mocked(getPerformanceSummary).mockResolvedValue({
      data: {
        estimate: { orderCount: 2, orderAmount: 30000, grossProfit: 600 },
        effective: { orderCount: 0, orderAmount: 0, grossProfit: 0 }
      }
    } as any)
  })

  it('renders all required business metrics with trade/estimate and settlement tracks', async () => {
    const wrapper = mount(DataDashboard, {
      global: {
        plugins: [createPinia()],
        stubs
      }
    })

    await flushPromises()

    const text = wrapper.text()
    for (const label of [
      '总订单数',
      '订单额',
      '服务费收入',
      '技术服务费',
      '服务费支出',
      '服务费收益',
      '招商提成',
      '渠道提成',
      '毛利'
    ]) {
      expect(text).toContain(label)
    }

    expect(text).toContain('成交：2')
    expect(text).toContain('结算：0')
    expect(text).toContain('成交：¥300.00')
    expect(text).toContain('预估：¥10.00')
    expect(text).toContain('预估：¥9.00')
    expect(text).toContain('预估：¥1.00')
    expect(text).toContain('预估：¥2.00')
    expect(text).toContain('预估：¥6.00')
  })

  it('uses explicit zero service fee expense instead of deriving fallback money', async () => {
    vi.mocked(getMetrics).mockResolvedValue({
      ...metricsPayload,
      data: {
        ...metricsPayload.data,
        estimate: {
          ...metricsPayload.data.estimate,
          serviceFeeExpense: '0.00',
          serviceFeeIncome: '10.00',
          techServiceFee: '1.00',
          serviceFee: '8.00'
        }
      }
    } as any)

    const wrapper = mount(DataDashboard, {
      global: {
        plugins: [createPinia()],
        stubs
      }
    })

    await flushPromises()

    const text = wrapper.find('[data-testid="dashboard-business-metrics"]').text()
    expect(text).toContain('服务费支出预估：¥0.00')
  })

  it('labels top cards as create-track estimate metrics instead of paid net metrics', async () => {
    const wrapper = mount(DataDashboard, {
      global: {
        plugins: [createPinia()],
        stubs
      }
    })

    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('今日订单数（创建轨）')
    expect(text).toContain('今日 GMV（创建轨）')
    expect(text).toContain('今日服务费净收·预估金额')
    expect(text).toContain('今日提成·预估金额')
    expect(text).toContain('按订单创建时间统计，仅统计有效订单，不等于付款订单额 - 退款订单额')
    expect(text).toContain('付费/退款为状态或付款退款口径，与创建轨卡片不可直接相减对账')
    expect(text).toContain('¥9.00')
    expect(text).toContain('¥3.00')
    expect(text).not.toContain('今日付款净额')
  })

  it('renders refund metrics on dashboard visualization', async () => {
    const wrapper = mount(DataDashboard, {
      global: {
        plugins: [createPinia()],
        stubs
      }
    })

    await flushPromises()

    const text = wrapper.find('[data-testid="dashboard-refund-metrics"]').text()
    expect(text).toContain('退款指标')
    expect(text).toContain('退款订单数')
    expect(text).toContain('3 单')
    expect(text).toContain('订单退款服务费')
    expect(text).toContain('¥4.50')
    expect(text).toContain('退款订单额')
    expect(text).toContain('¥120.00')
  })
})
