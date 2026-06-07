/**
 * @file OrderList 订单汇总页面的单元测试
 * @description 测试订单汇总页面的数据加载、筛选条件传递、导出功能及路由参数同步
 */
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { exportOrders, exportOrderDetail, getOrderSummary, getOrderDetailPage } from '../../api/data'
import { getOrderFilterOptions } from '../../api/order'
import { useAuthStore } from '../../stores/auth'
import OrderList from './OrderList.vue'

/** 模拟路由查询参数，用于测试路由驱动的筛选联动 */
const routeMock = vi.hoisted(() => ({
  query: {
    productId: 'ROUTE-PRODUCT-1',
    timeField: 'settleTime'
  } as Record<string, string>
}))

/** 模拟 vue-router，返回预设的路由查询参数 */
vi.mock('vue-router', () => ({
  useRoute: () => routeMock
}))

/** 模拟订单数据 API 接口 */
vi.mock('../../api/data', () => ({
  exportOrders: vi.fn(),
  exportOrderDetail: vi.fn(),
  getOrderSummary: vi.fn(),
  getOrderDetailPage: vi.fn()
}))

vi.mock('../../api/order', () => ({
  getOrderFilterOptions: vi.fn()
}))

/** 模拟订单用户筛选选项加载函数（招商人/渠道） */
vi.mock('../orders/order-user-filter-options', () => ({
  loadOrderRecruiterOptions: vi.fn().mockResolvedValue([]),
  loadOrderChannelOptions: vi.fn().mockResolvedValue([])
}))

/** 模拟订单明细Tab子组件，避免解析其内部依赖 */
vi.mock('./OrderDetailTab.vue', () => ({
  default: {
    name: 'OrderDetailTab',
    props: ['filters', 'timeField', 'dateRange'],
    emits: ['rowCount', 'export'],
    template: '<div data-testid="order-detail-tab-stub">明细表</div>',
    methods: { refresh() {} }
  }
}))

/** 模拟请求错误通知工具 */
vi.mock('../../utils/requestError', () => ({
  notifyApiFailure: vi.fn(),
  notifyClientPermission: vi.fn()
}))

/** 模拟 Naive UI 的 message API 实例，用于验证消息提示调用 */
const messageApi = {
  success: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
  info: vi.fn()
}

/** 模拟 Naive UI 模块，保留原始导出但替换 useMessage 钩子 */
vi.mock('naive-ui', async (importOriginal) => {
  const actual = await importOriginal<typeof import('naive-ui')>()
  return {
    ...actual,
    useMessage: () => messageApi
  }
})

/**
 * 模拟订单汇总 API 返回的数据结构
 * 包含总览统计（total）和按日明细记录（records）
 */
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

/**
 * 生成通用输入控件桩组件
 * @param tag - HTML 标签名
 * @returns 支持 value 双向绑定和 click 事件的桩组件配置
 */
const stubControl = (tag: string) => ({
  props: ['value', 'checked'],
  emits: ['update:value', 'click'],
  template: `<${tag} @click="$emit('click', $event)"><slot />{{ value }}</${tag}>`
})

/** 全局 Naive UI 组件桩配置，用简化 HTML 模拟组件行为 */
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
  NPopover: {
    props: ['show'],
    emits: ['update:show'],
    template: '<div><slot name="trigger" /><div class="popover-content"><slot /></div></div>'
  },
  NSelect: stubControl('div'),
  NText: { template: '<span><slot /></span>' },
  NTooltip: { template: '<span><slot name="trigger" /><slot /></span>' },
  OrderDetailTab: {
    props: ['filters', 'timeField', 'dateRange'],
    emits: ['rowCount', 'export'],
    template: '<div data-testid="order-detail-tab-stub">明细表</div>',
    methods: { refresh() {} }
  }
}

/**
 * 挂载 OrderList 组件的辅助函数
 * 初始化 Pinia 状态管理、设置用户角色为 biz_leader、挂载组件并等待异步完成
 * @returns 挂载后的 Vue 测试包装器
 */
const mountOrderList = async () => {
  const pinia = createPinia()
  setActivePinia(pinia)
  const authStore = useAuthStore(pinia)
  authStore.login('unit-test-token', { roleCodes: ['biz_leader'] })

  const wrapper = mount(OrderList, {
    global: {
      plugins: [pinia],
      stubs: globalStubs
    }
  })
  await flushPromises()
  return wrapper
}

describe('OrderList 订单汇总页面', () => {
  /** 每个测试前重置所有模拟、清除 localStorage、恢复路由参数并设置 API 返回值 */
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    routeMock.query = {
      productId: 'ROUTE-PRODUCT-1',
      timeField: 'settleTime'
    }
    vi.mocked(getOrderSummary).mockResolvedValue({ data: summaryPayload } as any)
    vi.mocked(exportOrders).mockResolvedValue(new Blob(['订单号\nORDER-1']) as any)
    vi.mocked(exportOrderDetail).mockResolvedValue(new Blob(['订单号\nORDER-1']) as any)
    vi.mocked(getOrderDetailPage).mockResolvedValue({ data: { records: [], total: 0 } } as any)
    vi.mocked(getOrderFilterOptions).mockResolvedValue({ data: { recruiterDepartments: [], channelDepartments: [] } } as any)
  })

  /** 验证页面加载时使用路由参数作为筛选条件请求汇总数据，并正确渲染聚合统计值 */
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
    expect(wrapper.text()).not.toContain('毛利')
  })

  /** 验证手动搜索时，所有支持的筛选字段（订单号/状态/达人/商家/活动/店铺/商品/招商人等）正确传递给汇总查询接口 */
  it('passes supported filter fields to summary query when searching', async () => {
    const wrapper = await mountOrderList()
    const vm = wrapper.vm as any
    Object.assign(vm.searchParams, {
      orderId: 'ORDER-7788',
      status: 'FINISHED',
      talentId: '4d6c5562-f19f-4e49-a06d-111111111111',
      merchantId: 'merchant_10086',
      activityId: 'ACT-1',
      activityName: '活动甲',
      partnerId: 'PARTNER-1',
      partnerName: '合作方甲',
      productId: 'P-1',
      productName: '测试商品',
      talentName: '达人甲',
      recruiterName: '招商甲',
      recruitType: 'PROMOTION'
    })

    await vm.fetchData()

    expect(getOrderSummary).toHaveBeenLastCalledWith(expect.objectContaining({
      orderId: 'ORDER-7788',
      status: 'FINISHED',
      talentId: '4d6c5562-f19f-4e49-a06d-111111111111',
      merchantId: 'PARTNER-1',
      partnerId: 'PARTNER-1',
      colonelActivityId: 'ACT-1',
      activityName: '活动甲',
      shopName: '合作方甲',
      partnerName: '合作方甲',
      productId: 'P-1',
      productName: '测试商品',
      talentName: '达人甲',
      colonelName: '招商甲',
      recruiterName: '招商甲',
      recruitType: 'PROMOTION'
    }))
  })

  /** 验证导出功能使用与列表相同的筛选条件，并验证 biz_leader 角色具有导出权限 */
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

  /** 验证路由参数变化时，被移除的商品和活动筛选条件会被正确清空，时间字段同步更新 */
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

  it('renders tab switcher with summary and detail tabs', async () => {
    const wrapper = await mountOrderList()
    const tabs = wrapper.findAll('.data-tab')
    expect(tabs).toHaveLength(2)
    expect(tabs[0].text()).toBe('汇总')
    expect(tabs[1].text()).toBe('订单明细')
    expect(tabs[0].classes()).toContain('active')
  })

  it('hides summary panel when switching to detail tab', async () => {
    const wrapper = await mountOrderList()
    const vm = wrapper.vm as any

    expect(wrapper.find('.summary-panel').exists()).toBe(true)
    vm.switchTab('detail')
    await flushPromises()
    expect(wrapper.find('.summary-panel').exists()).toBe(false)
  })

  it('shows OrderDetailTab when detail tab is active', async () => {
    const wrapper = await mountOrderList()
    const vm = wrapper.vm as any

    vm.switchTab('detail')
    await flushPromises()
    expect(wrapper.find('[data-testid="order-detail-tab-stub"]').exists()).toBe(true)
  })

  it('calls exportOrderDetail when exporting from detail tab', async () => {
    const createObjectURL = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:detail')
    const revokeObjectURL = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    const wrapper = await mountOrderList()
    const vm = wrapper.vm as any

    vm.switchTab('detail')
    await flushPromises()
    vm.handleDetailRowCount(5)
    await vm.handleExport()

    expect(exportOrderDetail).toHaveBeenCalled()
    expect(exportOrders).not.toHaveBeenCalled()

    createObjectURL.mockRestore()
    revokeObjectURL.mockRestore()
  })

  it('shows recent-days popup options and applies selected range to summary query', async () => {
    const wrapper = await mountOrderList()
    const vm = wrapper.vm as any

    expect(wrapper.find('[data-testid="data-orders-recent-days-trigger"]').text()).toBe('近N天')
    expect(wrapper.find('[data-testid="data-orders-recent-days-today"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="data-orders-recent-days-yesterday"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="data-orders-recent-days-15d"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="data-orders-recent-days-30d"]').exists()).toBe(true)

    vi.mocked(getOrderSummary).mockClear()
    await vm.applyRecentDaysOption('30d')
    await flushPromises()

    expect(vm.timePreset).toBe('recent')
    expect(vm.recentDaysOption).toBe('30d')
    expect(vm.recentPresetLabel).toBe('30天')
    expect(getOrderSummary).toHaveBeenCalledWith(expect.objectContaining({
      startDate: expect.any(String),
      endDate: expect.any(String)
    }))
    const params = vi.mocked(getOrderSummary).mock.calls[0]?.[0] as Record<string, string>
    const start = new Date(`${params.startDate}T00:00:00`)
    const end = new Date(`${params.endDate}T00:00:00`)
    const daySpan = Math.round((end.getTime() - start.getTime()) / (24 * 60 * 60 * 1000)) + 1
    expect(daySpan).toBe(30)
  })
})
