<template>
  <div class="order-list app-page" data-testid="data-orders-page">
    <section class="platform-switch" aria-label="订单来源平台">
      <button
        type="button"
        class="platform-tab active"
        aria-pressed="true"
      >
        <span class="platform-icon douyin">抖</span>
        <span>抖音</span>
      </button>
    </section>

    <section class="order-filter-panel app-panel">
      <div class="filter-grid">
        <div class="filter-field">
          <span class="filter-label">订单ID</span>
          <n-input v-model:value="searchParams.orderId" placeholder="请输入" clearable />
        </div>
        <div class="filter-field">
          <span class="filter-label">活动ID</span>
          <n-input v-model:value="searchParams.activityId" placeholder="请选择" clearable />
        </div>
        <div class="filter-field">
          <span class="filter-label">活动名称</span>
          <n-input v-model:value="searchParams.activityName" placeholder="请输入" clearable />
        </div>
        <div class="filter-field">
          <span class="filter-label">渠道</span>
          <n-select v-model:value="searchParams.channelName" :options="channelOptions" placeholder="请选择" clearable filterable />
        </div>
        <div class="filter-field">
          <span class="filter-label">招商</span>
          <n-select v-model:value="searchParams.recruiterName" :options="recruiterOptions" placeholder="请选择" clearable filterable />
        </div>

        <div class="filter-field">
          <span class="filter-label">商品ID</span>
          <n-input v-model:value="searchParams.productId" placeholder="请输入" clearable />
        </div>
        <div class="filter-field">
          <span class="filter-label">商品名称</span>
          <n-input v-model:value="searchParams.productName" placeholder="请输入" clearable />
        </div>
        <div class="filter-field">
          <span class="filter-label">推广者ID</span>
          <n-input v-model:value="searchParams.talentId" placeholder="请输入" clearable />
        </div>
        <div class="filter-field">
          <span class="filter-label">推广者昵称</span>
          <n-input v-model:value="searchParams.talentName" placeholder="请输入" clearable />
        </div>

        <div class="filter-field">
          <span class="filter-label">合作方ID</span>
          <n-input v-model:value="searchParams.partnerId" placeholder="请输入" clearable />
        </div>
        <div class="filter-field">
          <span class="filter-label">合作方名称</span>
          <n-input v-model:value="searchParams.partnerName" placeholder="请输入" clearable />
        </div>
        <div class="filter-field">
          <span class="filter-label">合作类型</span>
          <n-select v-model:value="searchParams.recruitType" :options="recruitTypeOptions" placeholder="请选择" clearable />
        </div>
        <div class="filter-field">
          <span class="filter-label">团长名称</span>
          <n-input v-model:value="searchParams.colonelName" placeholder="请选择" clearable />
        </div>

        <div class="filter-field">
          <span class="filter-label">招商部门</span>
          <n-select
            v-model:value="searchParams.recruiterDeptIds"
            :options="recruiterDeptOptions"
            multiple
            filterable
            clearable
            max-tag-count="responsive"
            placeholder="请选择"
          />
        </div>
        <div class="filter-field">
          <span class="filter-label">渠道部门</span>
          <n-select
            v-model:value="searchParams.channelDeptIds"
            :options="channelDeptOptions"
            multiple
            filterable
            clearable
            max-tag-count="responsive"
            placeholder="请选择"
          />
        </div>
        <div class="filter-field filter-field-wide">
          <span class="filter-label">时间筛选</span>
          <div class="time-filter">
            <n-select
              v-model:value="timeField"
              :options="timeFieldOptions"
              class="time-field-select"
              @update:value="handleTimeFieldChange"
            />
            <n-button-group class="time-presets">
              <n-popover
                v-model:show="recentDaysPopoverVisible"
                trigger="click"
                placement="bottom-start"
              >
                <template #trigger>
                  <n-button
                    :type="timePreset === 'recent' ? 'primary' : 'default'"
                    :ghost="timePreset !== 'recent'"
                    data-testid="data-orders-recent-days-trigger"
                  >
                    {{ recentPresetLabel }}
                  </n-button>
                </template>
                <div class="recent-days-options">
                  <n-button
                    v-for="option in recentDaysOptions"
                    :key="option.value"
                    size="small"
                    :type="timePreset === 'recent' && recentDaysOption === option.value ? 'primary' : 'default'"
                    :ghost="!(timePreset === 'recent' && recentDaysOption === option.value)"
                    :data-testid="`data-orders-recent-days-${option.value}`"
                    @click="applyRecentDaysOption(option.value)"
                  >
                    {{ option.label }}
                  </n-button>
                </div>
              </n-popover>
              <n-button
                v-for="preset in fixedTimePresetOptions"
                :key="preset.value"
                :type="timePreset === preset.value ? 'primary' : 'default'"
                :ghost="timePreset !== preset.value"
                @click="applyTimePreset(preset.value)"
              >
                {{ preset.label }}
              </n-button>
            </n-button-group>
            <n-date-picker
              v-model:value="dateRange"
              type="daterange"
              format="yyyy/MM/dd"
              clearable
              class="date-picker"
              @update:value="handleDateRangeChange"
            />
          </div>
        </div>

        <div class="filter-field">
          <span class="filter-label">订单状态</span>
          <n-select v-model:value="searchParams.status" :options="statusOptions" placeholder="请选择" clearable />
        </div>
      </div>

      <div class="filter-actions">
        <n-button @click="resetFilters">重 置</n-button>
        <n-button type="primary" data-testid="data-orders-search-submit" @click="fetchData">搜 索</n-button>
      </div>

      <div class="data-tab-bar">
        <button
          type="button"
          :class="['data-tab', { active: activeTab === 'summary' }]"
          @click="switchTab('summary')"
        >汇总</button>
        <button
          type="button"
          :class="['data-tab', { active: activeTab === 'detail' }]"
          @click="switchTab('detail')"
        >订单明细</button>
      </div>

      <div v-if="activeTab === 'summary'" class="dimension-row">
        <span class="dimension-label">汇总维度</span>
        <n-checkbox :checked="true" disabled />
        <n-select
          v-model:value="summaryDimension"
          :options="summaryDimensionOptions"
          class="dimension-select"
          disabled
        />
        <n-checkbox
          v-for="item in disabledDimensionOptions"
          :key="item"
          disabled
        >
          {{ item }}
        </n-checkbox>
      </div>
    </section>

    <section v-if="activeTab === 'summary'" class="summary-panel app-panel" aria-label="订单汇总">
      <div class="summary-strip">
        <div v-for="item in summaryItems" :key="item.key" class="summary-item">
          <div class="summary-title">
            {{ item.title }}
            <n-tooltip v-if="item.tooltip" placement="top" :delay="100">
              <template #trigger>
                <span class="tooltip-trigger" aria-label="查看说明">?</span>
              </template>
              <div class="tooltip-content" v-html="item.tooltip" />
            </n-tooltip>
          </div>
          <div v-for="line in item.lines" :key="line.label" class="summary-line">
            <span>{{ line.label }}</span>
            <strong>{{ line.value }}</strong>
          </div>
        </div>
      </div>
    </section>

    <section v-if="activeTab === 'summary'" class="table-panel app-panel app-table-shell">
      <div class="table-toolbar">
        <n-popover trigger="click" placement="bottom-end">
          <template #trigger>
            <n-button quaternary>⚙ 自定义表头</n-button>
          </template>
          <n-checkbox-group v-model:value="visibleColumnKeys" class="column-picker">
            <n-checkbox
              v-for="column in configurableColumns"
              :key="column.key"
              :value="column.key"
            >
              {{ column.title }}
            </n-checkbox>
          </n-checkbox-group>
        </n-popover>
        <n-button v-if="canExport" type="primary" secondary data-testid="data-orders-export" @click="handleExport">
          导 出
        </n-button>
      </div>

      <n-data-table
        data-testid="data-orders-table"
        :columns="columns"
        :data="tableRows"
        :loading="loading"
        :row-key="(row: any) => row.date || 'total'"
        :scroll-x="1280"
      />
    </section>

    <OrderDetailTab
      v-if="activeTab === 'detail'"
      ref="detailTabRef"
      :filters="searchParams"
      :time-field="timeField"
      :date-range="dateRange"
      @row-count="handleDetailRowCount"
      @export="handleExport"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { NText, useMessage } from 'naive-ui'
import { exportOrders, exportOrderDetail, getOrderSummary } from '../../api/data'
import { getOrderFilterOptions } from '../../api/order'
import { loadOrderRecruiterOptions, loadOrderChannelOptions } from '../orders/order-user-filter-options'
import { useAuthStore } from '../../stores/auth'
import { notifyApiFailure, notifyClientPermission } from '../../utils/requestError'
import { buildOrderExportParams, type OrderTimeField } from './order-list-query'
import {
  buildMonthRange,
  buildRecentRange,
  buildWeekRange,
  getRecentPresetLabel,
  recentDaysOptions,
  type RecentDaysOption,
  type TimePreset
} from './order-list-time-presets'
import OrderDetailTab from './OrderDetailTab.vue'

type SummaryRow = {
  date?: string | null
  talentPromoterCount?: number | null
  colonelPromoterCount?: number | null
  productCount?: number | null
  orderCount?: number | null
  orderAmount?: number | string | null
  productAverageServiceFeeRate?: number | string | null
  orderAverageServiceFeeRate?: number | string | null
  serviceFeeIncome?: number | string | null
  techServiceFee?: number | string | null
  serviceFeeExpense?: number | string | null
  serviceFeeProfit?: number | string | null
  grossProfit?: number | string | null
}

const authStore = useAuthStore()
const route = useRoute()
const message = useMessage()
const loading = ref(false)
const canExport = computed(() => authStore.isAdmin || authStore.isLeader)

const activeTab = ref<'summary' | 'detail'>('summary')
const detailTabRef = ref<InstanceType<typeof OrderDetailTab> | null>(null)
const detailRowCount = ref(0)

const switchTab = (tab: 'summary' | 'detail') => {
  activeTab.value = tab
  if (tab === 'summary') {
    void fetchData()
  } else {
    detailTabRef.value?.refresh()
  }
}

const handleDetailRowCount = (count: number) => {
  detailRowCount.value = count
}

const dateRange = ref<[number, number] | null>(buildWeekRange())
const timePreset = ref<TimePreset>('week')
const recentDaysOption = ref<RecentDaysOption>('15d')
const recentDaysPopoverVisible = ref(false)
const timeField = ref<OrderTimeField>('createTime')
const summaryDimension = ref('paymentDateDay')

const searchParams = reactive({
  orderId: '',
  status: null as string | null,
  talentId: '',
  merchantId: '',
  activityId: '',
  activityName: '',
  shopName: '',
  partnerId: '',
  partnerName: '',
  productId: '',
  productName: '',
  talentName: '',
  colonelName: '',
  channelName: '',
  recruitType: null as string | null,
  // t2-orders 修复：原模板中"招商"与"团长名称"两个筛选控件都
  // v-model 到 searchParams.colonelName（行 30 与行 64），改其一即双向覆盖。
  // 现拆出独立字段 recruiterName 承载"招商"下拉（仅前端状态，不入后端 query），
  // 后端仍由 buildOrderExportParams 通过 colonelName 转发团长名称。
  recruiterName: null as string | null,
  recruiterDeptIds: null as string[] | null,
  channelDeptIds: null as string[] | null
})

const channelOptions = ref<Array<{ label: string; value: string }>>([])
const recruiterOptions = ref<Array<{ label: string; value: string }>>([])
const recruiterDeptOptions = ref<Array<{ label: string; value: string }>>([])
const channelDeptOptions = ref<Array<{ label: string; value: string }>>([])

const emptySummary: SummaryRow = {
  talentPromoterCount: 0,
  colonelPromoterCount: 0,
  productCount: 0,
  orderCount: 0,
  orderAmount: '0.00',
  productAverageServiceFeeRate: '0.00',
  orderAverageServiceFeeRate: '0.00',
  serviceFeeIncome: '0.00',
  techServiceFee: '0.00',
  serviceFeeExpense: '0.00',
  serviceFeeProfit: '0.00'
}

const totalSummary = ref<SummaryRow>({ ...emptySummary })
const tableRows = ref<SummaryRow[]>([])

const statusOptions = [
  { label: '已下单', value: 'ORDERED' },
  { label: '已发货', value: 'SHIPPED' },
  { label: '已完成', value: 'FINISHED' },
  { label: '已取消', value: 'CANCELLED' }
]

const recruitTypeOptions = [
  { label: '商家型招商单', value: 'MERCHANT' },
  { label: '推广单', value: 'PROMOTION' },
  { label: '混合单', value: 'MIXED' },
  { label: '团长型', value: 'COLONEL' }
]

const timeFieldOptions = [
  { label: '付款时间', value: 'createTime' },
  { label: '结算时间', value: 'settleTime' }
]

const fixedTimePresetOptions: Array<{ label: string; value: Exclude<TimePreset, 'recent'> }> = [
  { label: '周', value: 'week' },
  { label: '月', value: 'month' },
  { label: '自定义', value: 'custom' }
]

const recentPresetLabel = computed(() => getRecentPresetLabel(timePreset.value, recentDaysOption.value))

const summaryDimensionOptions = computed(() => [
  { label: `${activeTimeTitle.value}按日`, value: 'paymentDateDay' }
])

const disabledDimensionOptions = ['活动ID', '合作方信息ID', '店铺ID', '商品ID', '推广者ID', '订单来源', '团长名称']

const activeTimeTitle = computed(() => (timeField.value === 'settleTime' ? '结算时间' : '付款时间'))

const configurableColumns = [
  { title: '出单推广者数', key: 'promoterCount' },
  { title: '出单商品数', key: 'productCount' },
  { title: '订单数', key: 'orderCount' },
  { title: '订单额', key: 'orderAmount' },
  { title: '平均服务费率', key: 'averageRate' },
  { title: '服务费收入', key: 'serviceFeeIncome' },
  { title: '技术服务费', key: 'techServiceFee' },
  { title: '服务费支出', key: 'serviceFeeExpense' },
  { title: '服务费收益', key: 'serviceFeeProfit' }
]

const visibleColumnKeys = ref(configurableColumns.map((item) => item.key))

const formatNumber = (value: unknown) => {
  const numeric = Number(value ?? 0)
  return Number.isFinite(numeric) ? numeric.toLocaleString('zh-CN') : '0'
}

const formatMoney = (value: unknown) => {
  const numeric = Number(value ?? 0)
  return `¥${Number.isFinite(numeric) ? numeric.toFixed(2) : '0.00'}`
}

const formatCompactMoney = (value: unknown) => {
  const numeric = Number(value ?? 0)
  return `¥${Number.isFinite(numeric) ? numeric.toString() : '0'}`
}

const formatPercent = (value: unknown) => {
  const numeric = Number(value ?? 0)
  return `${Number.isFinite(numeric) ? numeric.toFixed(2) : '0.00'}%`
}

const formatEstimateTrack = (value: unknown, formatter = formatMoney) =>
  timeField.value === 'settleTime' ? '-' : formatter(value)

const formatSettleTrack = (value: unknown, formatter = formatMoney) =>
  timeField.value === 'settleTime' ? formatter(value) : '-'

const summaryItems = computed(() => {
  const total = totalSummary.value || emptySummary
  return [
    {
      key: 'promoter',
      title: '出单推广者数',
      tooltip: '<b>计算公式</b><br>达人：去重出单达人数<br>团长：去重出单团长数<br><b>数据来源</b>：performance_records',
      lines: [
        { label: '达人：', value: formatNumber(total.talentPromoterCount) },
        { label: '团长：', value: formatNumber(total.colonelPromoterCount) }
      ]
    },
    {
      key: 'product',
      title: '出单商品数',
      tooltip: '<b>计算公式</b><br>去重出单商品数<br><b>数据来源</b>：performance_records',
      lines: [{ label: '', value: formatNumber(total.productCount) }]
    },
    {
      key: 'orders',
      title: '订单数',
      tooltip: '<b>计算公式</b><br>筛选范围内的订单总数<br><b>数据来源</b>：performance_records',
      lines: [{ label: '', value: formatNumber(total.orderCount) }]
    },
    {
      key: 'amount',
      title: '订单额',
      tooltip: '<b>计算公式</b><br>支付：订单支付金额汇总<br>结算：实际结算金额汇总<br><b>数据来源</b>：performance_records',
      lines: [
        { label: '支付：', value: formatEstimateTrack(total.orderAmount) },
        { label: '结算：', value: formatSettleTrack(total.orderAmount) }
      ]
    },
    {
      key: 'rate',
      title: '平均服务费率',
      tooltip: '<b>计算公式</b><br>商品：服务费收入 ÷ 结算金额<br>订单：服务费收入 ÷ 订单额<br><b>数据来源</b>：performance_records',
      lines: [
        { label: '商品：', value: formatPercent(total.productAverageServiceFeeRate) },
        { label: '订单：', value: formatPercent(total.orderAverageServiceFeeRate) }
      ]
    },
    {
      key: 'income',
      title: '服务费收入',
      tooltip: '<b>计算公式</b><br>预估：各订单服务费收入汇总<br>结算：已结算订单服务费汇总<br><b>数据来源</b>：performance_records',
      lines: [
        { label: '预估：', value: formatEstimateTrack(total.serviceFeeIncome) },
        { label: '结算：', value: formatSettleTrack(total.serviceFeeIncome) }
      ]
    },
    {
      key: 'tech',
      title: '技术服务费',
      tooltip: '<b>计算公式</b><br>预估：各订单技术服务费汇总<br>结算：已结算订单技术费汇总<br><b>数据来源</b>：performance_records',
      lines: [
        { label: '预估：', value: formatEstimateTrack(total.techServiceFee, formatCompactMoney) },
        { label: '结算：', value: formatSettleTrack(total.techServiceFee, formatCompactMoney) }
      ]
    },
    {
      key: 'expense',
      title: '服务费支出',
      tooltip: '<b>计算公式</b><br>服务费支出 = 服务费收入 − 技术服务费 − 服务费收益<br><b>数据来源</b>：performance_records 平台侧实际服务费',
      lines: [
        { label: '预估：', value: formatEstimateTrack(total.serviceFeeExpense) },
        { label: '结算：', value: formatSettleTrack(total.serviceFeeExpense) }
      ]
    },
    {
      key: 'profit',
      title: '服务费收益',
      tooltip: '<b>计算公式</b><br>服务费收益 = 服务费收入 − 技术服务费 − 服务费支出<br><b>数据来源</b>：performance_records 计算',
      lines: [
        { label: '预估：', value: formatEstimateTrack(total.serviceFeeProfit) },
        { label: '结算：', value: formatSettleTrack(total.serviceFeeProfit) }
      ]
    }
  ]
})

const hasVisibleColumn = (key: string) => visibleColumnKeys.value.includes(key)

const columns = computed(() => {
  const cols: any[] = [
    {
      title: activeTimeTitle.value,
      key: 'date',
      width: 160,
      fixed: 'left' as const,
      render: (row: SummaryRow) => row.date || '-'
    }
  ]

  if (hasVisibleColumn('promoterCount')) {
    cols.push({
      title: '出单推广者数',
      key: 'promoterCount',
      width: 180,
      render: (row: SummaryRow) => h('div', { class: 'table-multi-line' }, [
        h('div', `达人：${formatNumber(row.talentPromoterCount)}`),
        h('div', `团长：${formatNumber(row.colonelPromoterCount)}`)
      ])
    })
  }
  if (hasVisibleColumn('productCount')) {
    cols.push({ title: '出单商品数', key: 'productCount', width: 140, render: (row: SummaryRow) => formatNumber(row.productCount) })
  }
  if (hasVisibleColumn('orderCount')) {
    cols.push({ title: '订单数', key: 'orderCount', width: 140, sorter: (a: SummaryRow, b: SummaryRow) => Number(a.orderCount || 0) - Number(b.orderCount || 0), render: (row: SummaryRow) => formatNumber(row.orderCount) })
  }
  if (hasVisibleColumn('orderAmount')) {
    cols.push({
      title: '订单额',
      key: 'orderAmount',
      width: 180,
      sorter: (a: SummaryRow, b: SummaryRow) => Number(a.orderAmount || 0) - Number(b.orderAmount || 0),
      render: (row: SummaryRow) => h('div', { class: 'table-multi-line' }, [
        h('div', `支付：${formatEstimateTrack(row.orderAmount)}`),
        h(NText, { depth: 3 }, { default: () => `结算：${formatSettleTrack(row.orderAmount)}` })
      ])
    })
  }
  if (hasVisibleColumn('averageRate')) {
    cols.push({
      title: '平均服务费率',
      key: 'averageRate',
      width: 170,
      render: (row: SummaryRow) => h('div', { class: 'table-multi-line' }, [
        h('div', `商品：${formatPercent(row.productAverageServiceFeeRate)}`),
        h('div', `订单：${formatPercent(row.orderAverageServiceFeeRate)}`)
      ])
    })
  }
  if (hasVisibleColumn('serviceFeeIncome')) {
    cols.push({
      title: '服务费收入',
      key: 'serviceFeeIncome',
      width: 170,
      render: (row: SummaryRow) => h('div', { class: 'table-multi-line' }, [
        h('div', `预估：${formatEstimateTrack(row.serviceFeeIncome)}`),
        h(NText, { depth: 3 }, { default: () => `结算：${formatSettleTrack(row.serviceFeeIncome)}` })
      ])
    })
  }
  if (hasVisibleColumn('techServiceFee')) {
    cols.push({
      title: '技术服务费',
      key: 'techServiceFee',
      width: 160,
      render: (row: SummaryRow) => h('div', { class: 'table-multi-line' }, [
        h('div', `预估：${formatEstimateTrack(row.techServiceFee, formatCompactMoney)}`),
        h(NText, { depth: 3 }, { default: () => `结算：${formatSettleTrack(row.techServiceFee, formatCompactMoney)}` })
      ])
    })
  }
  if (hasVisibleColumn('serviceFeeExpense')) {
    cols.push({
      title: '服务费支出',
      key: 'serviceFeeExpense',
      width: 160,
      render: (row: SummaryRow) => h('div', { class: 'table-multi-line' }, [
        h('div', `预估：${formatEstimateTrack(row.serviceFeeExpense)}`),
        h(NText, { depth: 3 }, { default: () => `结算：${formatSettleTrack(row.serviceFeeExpense)}` })
      ])
    })
  }
  if (hasVisibleColumn('serviceFeeProfit')) {
    cols.push({
      title: '服务费收益',
      key: 'serviceFeeProfit',
      width: 160,
      render: (row: SummaryRow) => h('div', { class: 'table-multi-line' }, [
        h('div', `预估：${formatEstimateTrack(row.serviceFeeProfit)}`),
        h(NText, { depth: 3 }, { default: () => `结算：${formatSettleTrack(row.serviceFeeProfit)}` })
      ])
    })
  }
  return cols
})

const syncFiltersFromRoute = () => {
  const rawTimeField = route.query.timeField
  const nextTimeField = Array.isArray(rawTimeField) ? rawTimeField[0] : rawTimeField
  if (nextTimeField === 'createTime' || nextTimeField === 'settleTime') {
    timeField.value = nextTimeField
  }
  searchParams.productId = typeof route.query.productId === 'string' ? route.query.productId : ''
  searchParams.activityId = typeof route.query.activityId === 'string' ? route.query.activityId : ''
  searchParams.activityName = typeof route.query.activityName === 'string' ? route.query.activityName : ''
}

const applyRecentDaysOption = (option: RecentDaysOption) => {
  recentDaysOption.value = option
  timePreset.value = 'recent'
  dateRange.value = buildRecentRange(option)
  recentDaysPopoverVisible.value = false
  void fetchData()
}

const applyTimePreset = (preset: Exclude<TimePreset, 'recent'>) => {
  timePreset.value = preset
  if (preset === 'week') {
    dateRange.value = buildWeekRange()
  } else if (preset === 'month') {
    dateRange.value = buildMonthRange()
  }
  if (preset !== 'custom') {
    void fetchData()
  }
}

const handleDateRangeChange = () => {
  timePreset.value = 'custom'
}

const handleTimeFieldChange = () => {
  void fetchData()
}

const fetchData = async () => {
  if (activeTab.value !== 'summary') return
  loading.value = true
  try {
    const res = await getOrderSummary(buildOrderExportParams({
      timeField: timeField.value,
      dateRange: dateRange.value,
      filters: searchParams
    }))
    const payload: any = res?.data || res
    totalSummary.value = payload?.total || { ...emptySummary }
    tableRows.value = Array.isArray(payload?.records) ? payload.records : []
  } catch (error: any) {
    totalSummary.value = { ...emptySummary }
    tableRows.value = []
    notifyApiFailure(error, message, { fallbackMessage: '获取订单明细汇总失败' })
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  searchParams.orderId = ''
  searchParams.status = null
  searchParams.talentId = ''
  searchParams.merchantId = ''
  searchParams.activityId = ''
  searchParams.activityName = ''
  searchParams.shopName = ''
  searchParams.partnerId = ''
  searchParams.partnerName = ''
  searchParams.productId = ''
  searchParams.productName = ''
  searchParams.talentName = ''
  searchParams.colonelName = ''
  searchParams.channelName = ''
  searchParams.recruitType = null
  searchParams.recruiterName = null
  searchParams.recruiterDeptIds = null
  searchParams.channelDeptIds = null
  timeField.value = 'createTime'
  applyTimePreset('week')
}

const handleExport = async () => {
  if (!canExport.value) {
    notifyClientPermission('当前角色无权导出订单数据')
    return
  }

  const isDetail = activeTab.value === 'detail'
  const hasData = isDetail ? detailRowCount.value > 0 : tableRows.value.length > 0
  if (!hasData) {
    message.warning('暂无数据可导出')
    return
  }

  try {
    const params = buildOrderExportParams({
      timeField: timeField.value,
      dateRange: dateRange.value,
      filters: searchParams
    })
    const res: any = isDetail
      ? await exportOrderDetail(params)
      : await exportOrders(params)
    const prefix = isDetail ? 'order-detail' : 'orders'
    const filename = `${prefix}-${new Date().toISOString().slice(0, 10)}.csv`
    const url = window.URL.createObjectURL(new Blob([res]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', filename)
    document.body.appendChild(link)
    link.click()
    link.parentNode?.removeChild(link)
    window.URL.revokeObjectURL(url)
    message.success('导出成功')
  } catch (error) {
    notifyApiFailure(error, message, { fallbackMessage: '导出失败' })
  }
}

watch(
  () => [route.query.timeField, route.query.productId, route.query.activityId],
  () => {
    syncFiltersFromRoute()
    void fetchData()
  }
)

onMounted(async () => {
  syncFiltersFromRoute()
  void fetchData()
  try {
    const [recruiters, channels] = await Promise.all([
      loadOrderRecruiterOptions(''),
      loadOrderChannelOptions('')
    ])
    recruiterOptions.value = recruiters
    channelOptions.value = channels
  } catch {
    // silent — filters stay empty if master data unavailable
  }
  try {
    const res: any = await getOrderFilterOptions()
    const payload = res?.data || {}
    const map = (list: any): { label: string; value: string }[] => Array.isArray(list)
      ? list
          .filter((item: any) => item && item.value)
          .map((item: any) => ({ label: String(item.label || item.value), value: String(item.value) }))
      : []
    recruiterDeptOptions.value = map(payload.recruiterDepartments)
    channelDeptOptions.value = map(payload.channelDepartments)
  } catch {
    // silent — dept filters stay empty if unavailable
  }
})
</script>

<style scoped>
.order-list {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  gap: 22px;
  max-width: none;
}

.platform-switch {
  display: flex;
  align-items: center;
  gap: 28px;
  min-height: 54px;
  padding: 0 4px;
  border-bottom: 1px solid var(--border-color-light);
  background: var(--bg-card);
  border-radius: 8px 8px 0 0;
}

.platform-tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  height: 54px;
  padding: 0 4px;
  border: 0;
  background: transparent;
  color: var(--text-primary);
  font-size: 18px;
  font-weight: 700;
  cursor: pointer;
}

.platform-tab:disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

.platform-tab.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 3px;
  background: #202124;
}

.platform-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  color: #fff;
  font-size: 13px;
  font-weight: 800;
}

.platform-icon.douyin {
  background: #111827;
  box-shadow: inset 3px 0 0 #23f4ee, inset -3px 0 0 #ff2d55;
}

.order-filter-panel {
  position: relative;
  padding: 30px 28px 20px;
  border-radius: 8px;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(220px, 1fr));
  gap: 24px 40px;
  align-items: center;
}

.filter-field {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.filter-field-wide {
  grid-column: span 2;
}

.filter-label,
.dimension-label {
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
}

.time-filter {
  display: flex;
  align-items: center;
  gap: 0;
  min-width: 0;
}

.time-field-select {
  width: 150px;
}

.time-presets {
  flex-shrink: 0;
}

.recent-days-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  max-width: 220px;
}

.date-picker {
  width: 260px;
  margin-left: 12px;
}

.filter-actions {
  display: flex;
  justify-content: flex-end;
  gap: 18px;
  margin-top: 24px;
}

.filter-actions :deep(.n-button) {
  min-width: 94px;
}

.dimension-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 24px;
}

.dimension-select {
  width: 170px;
}

.data-tab-bar {
  display: flex;
  gap: 0;
  margin-top: 20px;
  border-bottom: 2px solid var(--border-color-light);
}

.data-tab {
  position: relative;
  padding: 10px 28px;
  border: 0;
  background: transparent;
  color: var(--text-secondary, #6b7280);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: color 0.15s;
}

.data-tab:hover {
  color: var(--text-primary, #111827);
}

.data-tab.active {
  color: var(--text-primary, #111827);
}

.data-tab.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -2px;
  height: 2px;
  background: #111827;
}

.summary-panel,
.table-panel {
  border-radius: 8px;
}

.summary-panel {
  padding: 34px 36px;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(10, minmax(128px, 1fr));
  gap: 18px;
  padding: 24px 26px;
  background: #fff3f1;
  border-radius: 6px;
  overflow-x: auto;
}

.summary-item {
  min-width: 126px;
  text-align: center;
  color: #111827;
}

.summary-title {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-bottom: 12px;
  font-size: 15px;
  font-weight: 700;
  white-space: nowrap;
}

.tooltip-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 1px solid #9ca3af;
  color: #9ca3af;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
  cursor: help;
  flex-shrink: 0;
  transition: all 0.15s;
}

.tooltip-trigger:hover {
  border-color: #6b7280;
  color: #6b7280;
  background: rgba(107, 114, 128, 0.08);
}

:deep(.tooltip-content) {
  font-size: 13px;
  line-height: 1.6;
  max-width: 280px;
}

:deep(.tooltip-content b) {
  font-weight: 600;
}

.summary-line {
  display: flex;
  justify-content: center;
  gap: 4px;
  min-height: 24px;
  font-size: 15px;
  line-height: 24px;
  white-space: nowrap;
}

.summary-line strong {
  font-weight: 500;
}

.table-panel {
  padding: 36px 36px 12px;
}

.table-toolbar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 14px;
  margin-bottom: 26px;
}

.column-picker {
  display: grid;
  gap: 8px;
  min-width: 150px;
}

.table-multi-line {
  display: grid;
  gap: 4px;
  line-height: 1.5;
}

:deep(.n-input),
:deep(.n-base-selection) {
  --n-border-radius: 7px;
}

:deep(.n-data-table-th) {
  color: #111827;
  font-size: 15px;
  font-weight: 700;
}

:deep(.n-data-table-td) {
  color: #242934;
  vertical-align: top;
}

@media (max-width: 1700px) {
  .filter-grid {
    grid-template-columns: repeat(2, minmax(260px, 1fr));
  }

  .time-filter {
    flex-wrap: wrap;
    gap: 8px;
  }

  .date-picker {
    margin-left: 0;
  }
}

@media (max-width: 760px) {
  .order-list {
    padding: 14px;
  }

  .filter-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .filter-field {
    grid-template-columns: 82px minmax(0, 1fr);
  }

  .filter-field-wide {
    grid-column: span 1;
  }

  .summary-panel,
  .table-panel,
  .order-filter-panel {
    padding: 16px;
  }

  .summary-strip {
    grid-template-columns: repeat(10, 130px);
    padding: 18px;
  }
}
</style>
