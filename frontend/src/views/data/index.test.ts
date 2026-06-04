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
      amountTrack: 'effective',
      trend7d: []
    }
  }
}

describe('DataDashboard V1 money cards', () => {
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

  it('does not render gross profit labels on the V1 dashboard', async () => {
    const wrapper = mount(DataDashboard, {
      global: {
        plugins: [createPinia()],
        stubs
      }
    })

    await flushPromises()

    expect(wrapper.text()).not.toContain('今日毛利')
    expect(wrapper.text()).not.toContain('预估轨毛利')
    expect(wrapper.text()).not.toContain('结算轨毛利')
  })
})
