import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getOrderDetailPage } from '../../api/data'
import OrderDetailTab from './OrderDetailTab.vue'

vi.mock('../../api/data', () => ({
  getOrderDetailPage: vi.fn()
}))

vi.mock('../../utils/requestError', () => ({
  notifyApiFailure: vi.fn()
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

const row = {
  orderId: 'ORDER-DETAIL-1',
  orderTypeText: '推广者推广',
  activityName: '活动甲',
  activityId: 'ACT-1',
  productImage: 'https://cdn.example.com/product.jpg',
  productName: '订单明细商品',
  productId: 'P-1',
  partnerName: '合作方甲',
  partnerId: 'PARTNER-1',
  colonelName: '众鼎',
  talentName: '达人甲',
  talentId: 'TALENT-1',
  talentDouyinId: 'tg12201212',
  videoId: '7621357005994936827',
  channelName: '渠道甲',
  recruiterName: '招商甲',
  contentTypeText: '短视频',
  orderStatusText: '待结算',
  payAmount: 199,
  settleAmount: null,
  estimateServiceFee: 10,
  effectiveServiceFee: null,
  estimateTechServiceFee: 2,
  effectiveTechServiceFee: null,
  estimateServiceFeeExpense: 3,
  effectiveServiceFeeExpense: null,
  estimateServiceProfit: 8,
  effectiveServiceProfit: null,
  estimateRecruiterCommission: 1,
  effectiveRecruiterCommission: null,
  estimateChannelCommission: 2,
  effectiveChannelCommission: null,
  payTime: '2026-06-04T13:57:32',
  deliveryTime: '2026-06-05T10:00:00',
  settleTime: null,
  expireTime: '2026-07-04T00:00:00',
  orderCreateTime: '2026-06-04T13:50:00'
}

const globalStubs = {
  NButton: {
    emits: ['click'],
    template: '<button type="button" @click="$emit(\'click\', $event)"><slot /></button>'
  },
  NCheckbox: { props: ['value'], template: '<label><input type="checkbox" /><slot /></label>' },
  NCheckboxGroup: { props: ['value'], template: '<div><slot /></div>' },
  NDataTable: {
    props: ['columns', 'data'],
    template: `
      <table data-testid="data-order-detail-table">
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
  NImage: {
    props: ['src'],
    template: '<img class="n-image" :src="src" />'
  },
  NPopover: { template: '<div><slot name="trigger" /><slot /></div>' },
  NTag: { template: '<span><slot /></span>' },
  NText: { template: '<span><slot /></span>' },
  NTooltip: { template: '<span><slot name="trigger" /><slot /></span>' }
}

function mountTab() {
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

  return mount(OrderDetailTab, {
    props: {
      filters: {
        orderId: 'ORDER-DETAIL-1',
        status: null,
        talentId: 'TALENT-1',
        merchantId: '',
        activityId: 'ACT-1',
        activityName: '活动甲',
        shopName: '',
        partnerId: 'PARTNER-1',
        partnerName: '合作方甲',
        productId: 'P-1',
        productName: '订单明细商品',
        talentName: '达人甲',
        colonelName: '',
        channelName: '渠道甲',
        recruiterName: '招商甲',
        recruitType: null
      },
      timeField: 'createTime',
      dateRange: null
    },
    global: {
      plugins: [pinia],
      stubs: globalStubs
    }
  })
}

describe('OrderDetailTab', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    vi.mocked(getOrderDetailPage).mockResolvedValue({ data: { records: [row], total: 1 } } as any)
  })

  it('renders screenshot-style order detail columns and upstream product image', async () => {
    const wrapper = mountTab()
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('订单ID')
    expect(text).toContain('活动信息')
    expect(text).toContain('商品信息')
    expect(text).toContain('合作方信息')
    expect(text).toContain('推广者')
    expect(text).toContain('媒介')
    expect(text).toContain('招商')
    expect(text).toContain('订单时间')
    expect(text).not.toContain('订单状态')
    expect(text).not.toContain('服务费收益')
    expect(text).not.toContain('渠道提成')
    expect(text).not.toContain('毛利')

    expect(wrapper.html()).toContain(row.productImage)
    expect(wrapper.find('.order-detail-product-image').attributes('src')).toBe(row.productImage)
    expect(text).toContain('推广者推广')
    expect(text).toContain('短视频')
    expect(text).toContain('订单明细商品')
    expect(text).toContain('P-1')
    expect(text).toContain('商家:')
    expect(text).toContain('合作方甲')
    expect(text).toContain('团长:')
    expect(text).toContain('众鼎')
    expect(text).toContain('达人甲')
    expect(text).toContain('tg12201212')
    expect(text).toContain('出单视频:')
    expect(text).toContain('7621357005994936827')
    expect(text).toContain('渠道甲')
    expect(text).toContain('招商甲')
    expect(text).toContain('付款:')
    expect(text).toContain('2026-06-04 13:57:32')
    expect(text).toContain('收货:')
    expect(text).toContain('2026-06-05 10:00:00')
    expect(text).toContain('结算:')
    expect(text).toContain('失效:')
    expect(text).toContain('2026-07-04 00:00:00')
    expect(wrapper.find('[data-testid="data-order-detail-export"]').exists()).toBe(true)

    expect(getOrderDetailPage).toHaveBeenCalledWith(expect.objectContaining({
      orderId: 'ORDER-DETAIL-1',
      colonelActivityId: 'ACT-1',
      activityName: '活动甲',
      partnerId: 'PARTNER-1',
      partnerName: '合作方甲',
      recruiterName: '招商甲'
    }))
  })

  it('uses compatible upstream image aliases when productImage is absent', async () => {
    vi.mocked(getOrderDetailPage).mockResolvedValue({
      data: {
        records: [{
          ...row,
          productImage: null,
          product_pic: 'https://cdn.example.com/product-snake.jpg'
        }],
        total: 1
      }
    } as any)

    const wrapper = mountTab()
    await flushPromises()

    expect(wrapper.find('.order-detail-product-image').attributes('src')).toBe('https://cdn.example.com/product-snake.jpg')
  })
})
