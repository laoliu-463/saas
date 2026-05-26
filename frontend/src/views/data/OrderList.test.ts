import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { exportOrders, getOrderSummary } from '../../api/data'
import OrderList from './OrderList.vue'

const routeMock = vi.hoisted(() => ({
  query: {
    productId: 'ROUTE-PRODUCT-1',
    timeField: 'settleTime'
  } as Record<string, string>
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeMock
}))

vi.mock('../../api/data', () => ({
  exportOrders: vi.fn(),
  getOrderSummary: vi.fn()
}))

vi.mock('../orders/order-user-filter-options', () => ({
  loadOrderRecruiterOptions: vi.fn().mockResolvedValue([]),
  loadOrderChannelOptions: vi.fn().mockResolvedValue([])
}))

vi.mock('../../utils/requestError', () => ({
  notifyApiFailure: vi.fn(),
  notifyClientPermission: vi.fn()
}))

const messageApi = {
  success: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
  info: vi.fn()
}

vi.mock('naive-ui', async (importOriginal) => {
  const actual = await importOriginal<typeof import('naive-ui')>()
  return {
    ...actual,
    useMessage: () => messageApi
  }
})

const summaryPayload = {
  total: {
    talentPromoterCount: 290,
    colonelPromoterCount: 0,
    productCount: 262,
    orderCount: 4209,
    orderAmount: 81185.26,
    productAverageServiceFeeRate: 1.64,
    orderAverageServiceFeeRate: 1.49,
    serviceFeeIncome: 1330.32,
    techServiceFee: 121.4,
    serviceFeeExpense: 0,
    serviceFeeProfit: 1208.92,
    grossProfit: 910.65
  },
  records: [
    {
      date: '2026-05-25',
      talentPromoterCount: 290,
      colonelPromoterCount: 0,
      productCount: 262,
      orderCount: 4209,
      orderAmount: 81185.26,
      productAverageServiceFeeRate: 1.64,
      orderAverageServiceFeeRate: 1.49,
      serviceFeeIncome: 1330.32,
      techServiceFee: 121.4,
      serviceFeeExpense: 0,
      serviceFeeProfit: 1208.92,
      grossProfit: 910.65
    }
  ]
}

const stubControl = (tag: string) => ({
  props: ['value', 'checked'],
  emits: ['update:value', 'click'],
  template: `<${tag} @click="$emit('click', $event)"><slot />{{ value }}</${tag}>`
})

const globalStubs = {
  NButton: {
    emits: ['click'],
    template: '<button type="button" @click="$emit(\'click\', $event)"><slot /></button>'
  },
  NButtonGroup: { template: '<div><slot /></div>' },
  NCheckbox: { template: '<label><input type="checkbox" /><slot /></label>' },
  NCheckboxGroup: { template: '<div><slot /></div>' },
  NDataTable: {
    props: ['columns', 'data', 'loading'],
    template: `
      <table data-testid="data-orders-table">
        <tbody>
          <tr v-for="row in data" :key="row.date">
            <td v-for="column in columns" :key="column.key">
              <component :is="{ render: () => column.render ? column.render(row) : row[column.key] }" />
            </td>
          </tr>
        </tbody>
      </table>
    `
  },
  NDatePicker: stubControl('div'),
  NInput: stubControl('div'),
  NPopover: { template: '<div><slot name="trigger" /><slot /></div>' },
  NSelect: stubControl('div'),
  NText: { template: '<span><slot /></span>' }
}

const mountOrderList = async () => {
  const pinia = createPinia()
  setActivePinia(pinia)
  localStorage.setItem('userInfo', JSON.stringify({ roleCodes: ['biz_leader'] }))
  pinia.state.value.auth = {
    token: '',
    refreshToken: '',
    refreshExpiresIn: null,
    accessTokenExpiresIn: null,
    userInfo: { roleCodes: ['biz_leader'] }
  }

  const wrapper = mount(OrderList, {
    global: {
      plugins: [pinia],
      stubs: globalStubs
    }
  })
  await flushPromises()
  return wrapper
}

describe('OrderList summary page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    routeMock.query = {
      productId: 'ROUTE-PRODUCT-1',
      timeField: 'settleTime'
    }
    vi.mocked(getOrderSummary).mockResolvedValue({ data: summaryPayload } as any)
    vi.mocked(exportOrders).mockResolvedValue(new Blob(['订单号\nORDER-1']) as any)
  })

  it('loads summary data with route filters and renders aggregate values', async () => {
    const wrapper = await mountOrderList()

    const summaryParams = vi.mocked(getOrderSummary).mock.calls[0]?.[0] as Record<string, unknown>
    expect(getOrderSummary).toHaveBeenCalledWith(expect.objectContaining({
      productId: 'ROUTE-PRODUCT-1',
      timeField: 'settleTime'
    }))
    expect(summaryParams).not.toHaveProperty('page')
    expect(summaryParams).not.toHaveProperty('size')
    expect(wrapper.text()).toContain('出单推广者数')
    expect(wrapper.text()).toContain('4,209')
    expect(wrapper.text()).toContain('¥81185.26')
    expect(wrapper.text()).toContain('2026-05-25')
  })

  it('passes supported filter fields to summary query when searching', async () => {
    const wrapper = await mountOrderList()
    const vm = wrapper.vm as any
    Object.assign(vm.searchParams, {
      orderId: 'ORDER-7788',
      status: 'FINISHED',
      talentId: '4d6c5562-f19f-4e49-a06d-111111111111',
      merchantId: 'merchant_10086',
      activityId: 'ACT-1',
      shopName: '测试店铺',
      productId: 'P-1',
      productName: '测试商品',
      talentName: '达人甲',
      colonelName: '招商甲',
      recruitType: 'PROMOTION'
    })

    await vm.fetchData()

    expect(getOrderSummary).toHaveBeenLastCalledWith(expect.objectContaining({
      orderId: 'ORDER-7788',
      status: 'FINISHED',
      talentId: '4d6c5562-f19f-4e49-a06d-111111111111',
      merchantId: 'merchant_10086',
      colonelActivityId: 'ACT-1',
      shopName: '测试店铺',
      productId: 'P-1',
      productName: '测试商品',
      talentName: '达人甲',
      colonelName: '招商甲',
      recruitType: 'PROMOTION'
    }))
  })

  it('exports with the same supported filters for leader roles', async () => {
    const createObjectURL = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:orders')
    const revokeObjectURL = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    const wrapper = await mountOrderList()
    const vm = wrapper.vm as any
    Object.assign(vm.searchParams, {
      orderId: 'ORDER-1',
      productName: '测试商品',
      colonelName: '招商甲'
    })

    expect(vm.canExport).toBe(true)
    await vm.handleExport()

    expect(exportOrders).toHaveBeenCalledWith(expect.objectContaining({
      orderId: 'ORDER-1',
      productName: '测试商品',
      colonelName: '招商甲'
    }))

    createObjectURL.mockRestore()
    revokeObjectURL.mockRestore()
  })

  it('clears route-scoped product and activity filters when route query removes them', async () => {
    routeMock.query = {
      productId: 'ROUTE-PRODUCT-1',
      activityId: 'ROUTE-ACT-1',
      timeField: 'settleTime'
    }
    const wrapper = await mountOrderList()
    const vm = wrapper.vm as any

    expect(vm.searchParams.productId).toBe('ROUTE-PRODUCT-1')
    expect(vm.searchParams.activityId).toBe('ROUTE-ACT-1')

    routeMock.query = {
      timeField: 'createTime'
    }
    vm.syncFiltersFromRoute()

    expect(vm.searchParams.productId).toBe('')
    expect(vm.searchParams.activityId).toBe('')
    expect(vm.timeField).toBe('createTime')
  })
})
