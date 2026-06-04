/**
 * @file orders/index.vue 商品信息列渲染测试
 * @description 验证订单归因页面商品信息列的丰富展示
 */
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getOrders, getOrderFilterOptions, getOrderStats, syncOrders } from '../../api/order'
import OrdersPage from './index.vue'

/** 模拟路由 */
const routeMock = vi.hoisted(() => ({
  query: {} as Record<string, string>
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeMock,
  useRouter: () => ({ push: vi.fn() })
}))

/** 模拟订单 API */
vi.mock('../../api/order', () => ({
  getOrders: vi.fn(),
  getOrderFilterOptions: vi.fn(),
  getOrderStats: vi.fn(),
  syncOrders: vi.fn()
}))

/** 模拟筛选选项加载 */
vi.mock('./order-user-filter-options', () => ({
  loadOrderChannelOptions: vi.fn().mockResolvedValue([]),
  loadOrderRecruiterOptions: vi.fn().mockResolvedValue([])
}))

/** 模拟请求错误通知 */
vi.mock('../../utils/requestError', () => ({
  notifyApiFailure: vi.fn(),
  notifyClientPermission: vi.fn()
}))

/** 模拟 Naive UI message */
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

/** 模拟归因原因文本 */
vi.mock('../../constants/orderAttribution', () => ({
  getAttributionReasonText: (reason: string) => reason
}))

/** 通用控件桩 */
const stubControl = (tag: string) => ({
  props: ['value', 'checked'],
  emits: ['update:value', 'click'],
  template: `<${tag} @click="$emit('click', $event)"><slot />{{ value }}</${tag}>`
})

/** 全局 Naive UI 桩，关键：NDataTable 实际渲染 column.render */
const globalStubs = {
  NButton: {
    emits: ['click'],
    template: '<button type="button" @click="$emit(\'click\', $event)"><slot /></button>'
  },
  NButtonGroup: { template: '<div><slot /></div>' },
  NCard: { template: '<div><slot /></div>' },
  NDataTable: {
    props: ['columns', 'data', 'loading'],
    template: `
      <table data-testid="orders-table">
        <tbody>
          <tr v-for="row in data" :key="row.orderId">
            <td v-for="column in columns" :key="column.key">
              <component :is="{ render: () => column.render ? column.render(row) : row[column.key] }" />
            </td>
          </tr>
        </tbody>
      </table>
    `
  },
  NDatePicker: stubControl('div'),
  NEmpty: { template: '<div><slot /></div>' },
  NInput: stubControl('div'),
  NPopover: { template: '<div><slot name="trigger" /><slot /></div>' },
  NSelect: stubControl('div'),
  NSpace: { template: '<div><slot /></div>' },
  NTag: { template: '<span><slot /></span>' },
  NText: { template: '<span><slot /></span>' },
  NCheckbox: { template: '<label><slot /></label>' },
  NCheckboxGroup: { template: '<div><slot /></div>' },
  NDivider: { template: '<hr />' },
  NGrid: { template: '<div><slot /></div>' },
  NGi: { template: '<div><slot /></div>' },
  NList: { template: '<ul><slot /></ul>' },
  NListItem: { template: '<li><slot /></li>' },
  NSkeleton: { template: '<div />' },
  NStatistic: { template: '<div><slot /></div>' },
  NThing: { template: '<div><slot /></div>' },
  NAlert: { template: '<div><slot /></div>' },
  NRadioGroup: { template: '<div><slot /></div>' },
  NRadioButton: { template: '<button><slot /></button>' },
  NSpin: { template: '<div><slot /></div>' }
}

/** 构造带商品信息字段的订单行 */
function buildOrderRow(overrides: Record<string, unknown> = {}) {
  return {
    orderId: 'ORDER-001',
    productId: '3762868644720279662',
    productName: '测试商品-新疆大枣',
    productTitle: '新疆大枣 500g 特级红枣',
    shopName: '新日月新炒货',
    activityId: 'ACT-001',
    orderAmount: 9900,
    attributionStatus: 'ATTRIBUTED',
    pickSource: 'PICK-001',
    channelUserName: '张三',
    createTime: '2026-06-01T10:00:00',
    settleTime: '2026-06-03T14:00:00',
    ...overrides
  }
}

const defaultListResponse = {
  data: { records: [buildOrderRow()], total: 1 }
}

const defaultStatsResponse = {
  data: {
    totalOrders: 1,
    attributedOrders: 1,
    unattributedOrders: 0,
    partialOrders: 0,
    lastSyncTime: '2026-06-01T10:00:00'
  }
}

const defaultFilterOptionsResponse = {
  data: {
    orderStatuses: [],
    attributionStatuses: [],
    unattributedReasons: [],
    products: [],
    channels: [],
    colonels: [],
    recruiterDepartments: [],
    channelDepartments: []
  }
}

describe('Orders page - 商品信息列', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.setItem('userInfo', JSON.stringify({ roleCodes: ['biz_leader'] }))
    vi.mocked(getOrders).mockResolvedValue(defaultListResponse as any)
    vi.mocked(getOrderStats).mockResolvedValue(defaultStatsResponse as any)
    vi.mocked(getOrderFilterOptions).mockResolvedValue(defaultFilterOptionsResponse as any)
    vi.mocked(syncOrders).mockResolvedValue({} as any)
  })

  async function mountPage() {
    const pinia = createPinia()
    setActivePinia(pinia)
    pinia.state.value.auth = {
      token: '',
      refreshToken: '',
      refreshExpiresIn: null,
      accessTokenExpiresIn: null,
      userInfo: { roleCodes: ['biz_leader'] }
    }

    const wrapper = mount(OrdersPage, {
      global: {
        plugins: [pinia],
        stubs: {
          ...globalStubs,
          PageHeader: { template: '<div data-testid="page-header"><slot name="actions"/></div>' },
          OrderDetailModal: { template: '<div/>' }
        }
      }
    })
    await flushPromises()
    return wrapper
  }

  it('商品信息列展示：完整字段正常渲染', async () => {
    const wrapper = await mountPage()
    const html = wrapper.html()

    expect(html).toContain('新疆大枣 500g 特级红枣')
    expect(html).toContain('商品ID：3762868644720279662')
    expect(html).toContain('店铺：新日月新炒货')
  })

  it('商品信息列展示：字段为空时显示 -', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({
          productTitle: null,
          productName: null,
          productId: null,
          shopName: null,
          quantity: null,
          commissionRate: null,
          serviceFeeRate: null
        })],
        total: 1
      }
    } as any)

    const wrapper = await mountPage()
    const html = wrapper.html()

    expect(html).toContain('商品ID：-')
    expect(html).toContain('店铺：-')
    expect(html).toContain('商品数量：-')
    expect(html).toContain('佣金率：-')
    expect(html).toContain('服务费率：-')
  })

  it('商品信息列展示：无图片时显示占位', async () => {
    const wrapper = await mountPage()
    expect(wrapper.html()).toContain('暂无图片')
  })

  it('商品信息列展示：有图片时渲染 img 标签', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({ productImage: 'https://example.com/product.jpg' })],
        total: 1
      }
    } as any)

    const wrapper = await mountPage()
    const img = wrapper.find('.product-info-image')
    expect(img.exists()).toBe(true)
    expect(img.attributes('src')).toBe('https://example.com/product.jpg')
  })

  it('佣金率格式化：小数形式 (0.07 → 7%)', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({ commissionRate: 0.07 })],
        total: 1
      }
    } as any)

    const wrapper = await mountPage()
    expect(wrapper.html()).toContain('佣金率：7%')
  })

  it('佣金率格式化：整数形式 (7 → 7%)', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({ commissionRate: 7 })],
        total: 1
      }
    } as any)

    const wrapper = await mountPage()
    expect(wrapper.html()).toContain('佣金率：7%')
  })

  it('服务费率格式化：不重复乘 100', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({ serviceFeeRate: 1 })],
        total: 1
      }
    } as any)

    const wrapper = await mountPage()
    expect(wrapper.html()).toContain('服务费率：1%')
  })

  it('服务费率格式化：小数形式 (0.01 → 1%)', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({ serviceFeeRate: 0.01 })],
        total: 1
      }
    } as any)

    const wrapper = await mountPage()
    expect(wrapper.html()).toContain('服务费率：1%')
  })

  it('其他列不受影响：订单号、归因状态等仍正常渲染', async () => {
    const wrapper = await mountPage()
    const html = wrapper.html()

    expect(html).toContain('ORDER-001')
    expect(html).toContain('已归因')
    expect(html).toContain('张三')
  })
})
