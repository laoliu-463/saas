/**
 * @file orders/index.vue 商品信息列 + 渠道文案测试
 * @description 验证订单归因页面商品信息列布局（96px 图片 + 右侧文字）和渠道文案统一
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
        <thead>
          <tr>
            <th v-for="column in columns" :key="column.key">{{ column.title }}</th>
          </tr>
        </thead>
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
    productQuantity: 1,
    commissionRate: 10,
    serviceFeeRate: 1,
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

describe('Orders page - 商品信息列布局', () => {
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
          OrderDetailModal: {
            props: ['show', 'orderId'],
            template: '<div v-if="show" data-testid="order-detail-modal">{{ orderId }}</div>'
          }
        }
      }
    })
    await flushPromises()
    return wrapper
  }

  it('商品信息列：完整字段正常渲染', async () => {
    const wrapper = await mountPage()
    const html = wrapper.html()

    expect(html).toContain('新疆大枣 500g 特级红枣')
    expect(html).toContain('商品ID：3762868644720279662')
    expect(html).toContain('店铺：新日月新炒货')
    expect(html).toContain('商品数量：1')
    expect(html).toContain('佣金率：10%')
    expect(html).toContain('服务费率：1%')
  })

  it('商品信息列：使用 order-product-cell 类名', async () => {
    const wrapper = await mountPage()
    expect(wrapper.find('.order-product-cell').exists()).toBe(true)
  })

  it('商品信息列：标题使用 order-product-title 类名（红色样式）', async () => {
    const wrapper = await mountPage()
    const title = wrapper.find('.order-product-title')
    expect(title.exists()).toBe(true)
    expect(title.text()).toContain('新疆大枣 500g 特级红枣')
  })

  it('商品信息列：详情使用 order-product-line 类名', async () => {
    const wrapper = await mountPage()
    const lines = wrapper.findAll('.order-product-line')
    expect(lines.length).toBe(5) // 商品ID、店铺、商品数量、佣金率、服务费率
  })

  it('商品信息列：字段为空时显示 -', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({
          productTitle: null,
          productName: null,
          productId: null,
          shopName: null,
          productQuantity: null,
          product_quantity: null,
          goodsNum: null,
          itemNum: null,
          itemCount: null,
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

  it('商品信息列：无图片时显示占位 div', async () => {
    const wrapper = await mountPage()
    const placeholder = wrapper.find('.order-product-image--placeholder')
    expect(placeholder.exists()).toBe(true)
  })

  it('商品信息列：有图片时渲染 img 标签', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({ productPic: 'https://example.com/product.jpg' })],
        total: 1
      }
    } as any)

    const wrapper = await mountPage()
    const img = wrapper.find('.order-product-image')
    expect(img.exists()).toBe(true)
    expect(img.attributes('src')).toBe('https://example.com/product.jpg')
  })

  it('商品信息列：兼容后端字段别名', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({
          productImage: 'https://example.com/product-image.jpg',
          productTitle: null,
          productName: null,
          goodsName: '别名商品标题',
          productId: null,
          goodsId: 'GOODS-001',
          shopName: null,
          storeName: '别名店铺',
          productQuantity: null,
          goodsNum: 4,
          commissionRate: null,
          cosRatio: 0.1,
          serviceFeeRate: null,
          serviceRate: '2%'
        })],
        total: 1
      }
    } as any)

    const wrapper = await mountPage()
    const html = wrapper.html()
    expect(wrapper.find('.order-product-image').attributes('src')).toBe('https://example.com/product-image.jpg')
    expect(html).toContain('别名商品标题')
    expect(html).toContain('商品ID：GOODS-001')
    expect(html).toContain('店铺：别名店铺')
    expect(html).toContain('商品数量：4')
    expect(html).toContain('佣金率：10%')
    expect(html).toContain('服务费率：2%')
  })

  it('佣金率格式化：小数形式 (0.1 → 10%)', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({ commissionRate: 0.1 })],
        total: 1
      }
    } as any)

    const wrapper = await mountPage()
    expect(wrapper.html()).toContain('佣金率：10%')
  })

  it('佣金率格式化：整数百分比 (10 → 10%)', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({ commissionRate: 10 })],
        total: 1
      }
    } as any)

    const wrapper = await mountPage()
    expect(wrapper.html()).toContain('佣金率：10%')
  })

  it('佣金率格式化：基点形式 (500 → 5%)', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({ commissionRate: 500 })],
        total: 1
      }
    } as any)

    const wrapper = await mountPage()
    expect(wrapper.html()).toContain('佣金率：5%')
  })

  it('佣金率格式化：基点形式 (1400 → 14%)', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({ commissionRate: 1400 })],
        total: 1
      }
    } as any)

    const wrapper = await mountPage()
    expect(wrapper.html()).toContain('佣金率：14%')
  })

  it('佣金率格式化：字符串 "10%" 直接展示', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({ commissionRate: '10%' })],
        total: 1
      }
    } as any)

    const wrapper = await mountPage()
    expect(wrapper.html()).toContain('佣金率：10%')
  })

  it('佣金率格式化：null → -', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({ commissionRate: null })],
        total: 1
      }
    } as any)

    const wrapper = await mountPage()
    expect(wrapper.html()).toContain('佣金率：-')
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

  it('服务费率格式化：字符串 "1%" 直接展示', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({ serviceFeeRate: '1%' })],
        total: 1
      }
    } as any)

    const wrapper = await mountPage()
    expect(wrapper.html()).toContain('服务费率：1%')
  })

  it('服务费率格式化：整数百分比 (10 → 10%)', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({ serviceFeeRate: 10 })],
        total: 1
      }
    } as any)

    const wrapper = await mountPage()
    expect(wrapper.html()).toContain('服务费率：10%')
  })

  it('商品数量显示 itemNum 字段', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({ productQuantity: null, itemNum: 3 })],
        total: 1
      }
    } as any)
  
    const wrapper = await mountPage()
    expect(wrapper.html()).toContain('商品数量：3')
  })

  it('商品字段 normalize：兼容 snake_case 和历史别名', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({
          productPic: null,
          productImage: null,
          product_pic: 'https://example.com/snake-product.jpg',
          productTitle: null,
          productName: null,
          product_title: '蛇形字段商品标题',
          productId: null,
          product_id: 'P-SNAKE',
          shopName: null,
          shop_name: '蛇形字段店铺',
          productQuantity: null,
          product_quantity: null,
          goodsNum: 4,
          commissionRate: null,
          commission_rate: 700,
          serviceFeeRate: null,
          service_fee_rate: 2
        })],
        total: 1
      }
    } as any)

    const wrapper = await mountPage()
    expect(wrapper.find('.order-product-image').attributes('src')).toBe('https://example.com/snake-product.jpg')
    expect(wrapper.html()).toContain('蛇形字段商品标题')
    expect(wrapper.html()).toContain('商品ID：P-SNAKE')
    expect(wrapper.html()).toContain('店铺：蛇形字段店铺')
    expect(wrapper.html()).toContain('商品数量：4')
    expect(wrapper.html()).toContain('佣金率：7%')
    expect(wrapper.html()).toContain('服务费率：2%')
  })
  
  it('表头显示渠道列', async () => {
    const wrapper = await mountPage()
    const html = wrapper.html()

    expect(html).toContain('渠道')
    expect(html.includes('\u5a92\u4ecb')).toBe(false)
  })

  it('渠道列仍能显示 channelUserName 字段数据', async () => {
    const wrapper = await mountPage()
    expect(wrapper.html()).toContain('张三')
  })

  it('渠道列兼容 channelName 字段', async () => {
    vi.mocked(getOrders).mockResolvedValueOnce({
      data: {
        records: [buildOrderRow({ channelUserName: null, channelName: '渠道别名' })],
        total: 1
      }
    } as any)
    const wrapper = await mountPage()
    expect(wrapper.html()).toContain('渠道别名')
  })

  it('渠道字段缺失时显示未归因', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({
          channelUserName: null,
          channelName: null
        })],
        total: 1
      }
    } as any)

    const wrapper = await mountPage()
    expect(wrapper.find('[data-testid="order-channel"]').text()).toBe('未归因')
  })

  it('其他列不受影响：订单号、渠道等仍正常渲染', async () => {
    const wrapper = await mountPage()
    const html = wrapper.html()

    expect(html).toContain('ORDER-001')
    expect(html).toContain('张三')
  })

  it('操作列：点击详情按钮打开订单详情弹窗', async () => {
    const wrapper = await mountPage()

    await wrapper.find('[data-testid="order-detail-button"]').trigger('click')
    await flushPromises()

    const modal = wrapper.find('[data-testid="order-detail-modal"]')
    expect(modal.exists()).toBe(true)
    expect(modal.text()).toContain('ORDER-001')
  })

  it('订单ID列：显示订单号、订单类型和内容类型标签', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({ orderTypeText: '推广者推广', contentTypeText: '短视频' })],
        total: 1
      }
    } as any)

    const wrapper = await mountPage()
    const html = wrapper.html()
    expect(html).toContain('ORDER-001')
    expect(html).toContain('推广者推广')
    expect(html).toContain('短视频')
  })

  it('活动信息列：显示活动名称和活动ID', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({ activityName: '星链达客3' })],
        total: 1
      }
    } as any)

    const wrapper = await mountPage()
    const html = wrapper.html()
    expect(html).toContain('星链达客3')
    expect(html).toContain('ID: ACT-001')
  })

  it('合作方信息列：显示商家和团长', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({ colonelUserName: '众鼎' })],
        total: 1
      }
    } as any)

    const wrapper = await mountPage()
    const html = wrapper.html()
    expect(html).toContain('商家:')
    expect(html).toContain('新日月新炒货')
    expect(html).toContain('团长:')
    expect(html).toContain('众鼎')
  })

  it('推广者列：显示达人昵称、ID、达人标签和出单视频', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({
          talentName: '哆咪哆零食',
          talentId: '123456',
          awemeId: '7621357005994936827'
        })],
        total: 1
      }
    } as any)

    const wrapper = await mountPage()
    const html = wrapper.html()
    expect(html).toContain('哆咪哆零食')
    expect(html).toContain('ID: 123456')
    expect(html).toContain('达人')
    expect(html).toContain('7621357005994936827')
    expect(html).toContain('出单视频:')
  })

  it('渠道列：未归因时显示"未归因"', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({
          channelUserName: null,
          channelName: null
        })],
        total: 1
      }
    } as any)

    const wrapper = await mountPage()
    expect(wrapper.find('[data-testid="order-channel"]').text()).toBe('未归因')
  })

  it('订单时间列：显示付款/收货/结算/失效四行', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({
          payTime: '2026-06-01 10:00:00',
          deliveryTime: '2026-06-02 14:00:00',
          settleTime: '2026-06-03 14:00:00',
          expireTime: '2026-07-01 00:00:00'
        })],
        total: 1
      }
    } as any)

    const wrapper = await mountPage()
    const html = wrapper.html()
    expect(html).toContain('付款:')
    expect(html).toContain('2026-06-01 10:00:00')
    expect(html).toContain('收货:')
    expect(html).toContain('2026-06-02 14:00:00')
    expect(html).toContain('结算:')
    expect(html).toContain('2026-06-03 14:00:00')
    expect(html).toContain('失效:')
    expect(html).toContain('2026-07-01 00:00:00')
  })

  it('订单时间列：无值时标签保留但值为空', async () => {
    vi.mocked(getOrders).mockResolvedValue({
      data: {
        records: [buildOrderRow({
          payTime: null,
          deliveryTime: null,
          settleTime: null,
          expireTime: null
        })],
        total: 1
      }
    } as any)

    const wrapper = await mountPage()
    const html = wrapper.html()
    expect(html).toContain('付款:')
    expect(html).toContain('收货:')
    expect(html).toContain('结算:')
    expect(html).toContain('失效:')
    // 不应显示 null
    expect(html).not.toContain('null')
    expect(html).not.toContain('undefined')
  })
})
