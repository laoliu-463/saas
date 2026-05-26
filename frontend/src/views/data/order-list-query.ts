export interface OrderListFilters {
  orderId: string
  status: string | null
  talentId: string
  merchantId: string
  activityId: string
  shopName: string
  productId: string
  productName: string
  talentName: string
  colonelName: string
  channelName: string
  recruitType: string | null
}

export type OrderTimeField = 'createTime' | 'settleTime'

interface BaseOrderQueryInput {
  filters: OrderListFilters
  timeField: OrderTimeField
  dateRange: [number, number] | null
}

interface OrderPageQueryInput extends BaseOrderQueryInput {
  page: number
  pageSize: number
}

function dateToYmd(value: number) {
  const date = new Date(value)
  const pad = (num: number) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function resolveDateParams(dateRange: [number, number] | null) {
  if (!dateRange) {
    return {
      startDate: undefined,
      endDate: undefined
    }
  }
  return {
    startDate: dateToYmd(dateRange[0]),
    endDate: dateToYmd(dateRange[1])
  }
}

export function buildOrderExportParams(input: BaseOrderQueryInput) {
  const { filters, timeField, dateRange } = input
  return {
    orderId: filters.orderId || undefined,
    status: filters.status || undefined,
    talentId: filters.talentId || undefined,
    merchantId: filters.merchantId || undefined,
    colonelActivityId: filters.activityId || undefined,
    shopName: filters.shopName || undefined,
    productId: filters.productId || undefined,
    productName: filters.productName || undefined,
    talentName: filters.talentName || undefined,
    colonelName: filters.colonelName || undefined,
    channelName: filters.channelName || undefined,
    recruitType: filters.recruitType || undefined,
    timeField,
    ...resolveDateParams(dateRange)
  }
}

export function buildOrderPageParams(input: OrderPageQueryInput) {
  const { page, pageSize } = input
  return {
    page,
    size: pageSize,
    ...buildOrderExportParams(input)
  }
}
