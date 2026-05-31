<template>
  <div class="product-toolbar app-toolbar">
    <n-space vertical :size="12">
      <n-space wrap :size="12" align="center">
        <n-select
          :value="selectedProduct"
          :options="productOptions"
          :loading="productOptionsLoading"
          filterable
          remote
          clearable
          placeholder="搜索商品名称 / ID"
          style="width: 260px"
          @update:value="$emit('update:selectedProduct', $event)"
          @search="$emit('search', $event)"
        />
        <n-input
          :value="filters.shopKeyword"
          clearable
          placeholder="店铺 / 合作方"
          style="width: 160px"
          @update:value="updateFilter('shopKeyword', $event)"
        />
        <n-select
          :value="filters.recruitActivityId"
          :options="assignedActivityOptions"
          :loading="assignedActivityOptionsLoading"
          filterable
          clearable
          placeholder="招商活动"
          style="width: 220px"
          data-testid="filter-assigned-activity"
          @update:value="updateAssignedActivity"
        />
        <n-select
          v-if="mode === 'manage'"
          :value="filters.categoryName"
          :options="categoryNameOptions"
          placeholder="抖音类目"
          clearable
          filterable
          tag
          style="width: 150px"
          @update:value="updateFilter('categoryName', $event)"
        />
        <n-select
          v-else
          :value="filters.categories"
          :options="libraryCategoryOptions"
          placeholder="商品类目"
          multiple
          clearable
          filterable
          style="width: 220px"
          data-testid="filter-library-categories"
          @update:value="updateFilter('categories', $event || [])"
        />
        <n-select
          :value="filters.systemTag"
          :options="systemTagOptions"
          placeholder="系统标签"
          clearable
          style="width: 140px"
          @update:value="updateFilter('systemTag', $event)"
        />
        <n-select
          :value="filters.commission"
          :options="commissionOptions"
          placeholder="佣金区间"
          clearable
          style="width: 130px"
          @update:value="updateFilter('commission', $event)"
        />
        <n-select
          v-if="mode === 'library'"
          :value="filters.serviceFee"
          :options="serviceFeeOptions"
          placeholder="服务费率"
          clearable
          style="width: 130px"
          @update:value="updateFilter('serviceFee', $event)"
        />
        <n-select
          v-if="mode === 'library'"
          :value="filters.supportsAds"
          :options="supportsAdsOptions"
          placeholder="投流"
          clearable
          style="width: 120px"
          @update:value="updateFilter('supportsAds', $event)"
        />
        <n-select
          :value="filters.salesRange"
          :options="salesRangeOptions"
          placeholder="近30天销量"
          clearable
          style="width: 170px"
          @update:value="updateFilter('salesRange', $event)"
        />
        <n-select
          :value="filters.goodsTags"
          :options="goodsTagOptions"
          placeholder="货品标签"
          multiple
          tag
          clearable
          filterable
          style="width: 180px"
          @update:value="updateFilter('goodsTags', $event)"
        />
        <n-select
          :value="filters.productTags"
          :options="productTagOptions"
          placeholder="商品标签"
          multiple
          tag
          clearable
          filterable
          style="width: 180px"
          @update:value="updateFilter('productTags', $event)"
        />
        <n-button type="primary" :loading="loading" @click="$emit('search-click')">查询</n-button>
        <n-button @click="$emit('reset')">重置</n-button>
      </n-space>

      <n-space wrap :size="12" align="center">
        <n-select
          :value="filters.allianceStatus"
          :options="allianceStatusOptions"
          placeholder="联盟推广状态"
          clearable
          style="width: 150px"
          @update:value="updateFilter('allianceStatus', $event)"
        />
        <n-select
          :value="filters.promotionLink"
          :options="promotionLinkOptions"
          placeholder="转链状态"
          clearable
          style="width: 140px"
          @update:value="updateFilter('promotionLink', $event)"
        />
        <n-select
          :value="filters.hasSample"
          :options="yesNoOptions"
          placeholder="支持寄样"
          clearable
          style="width: 120px"
          @update:value="updateFilter('hasSample', $event)"
        />
        <template v-if="mode === 'manage'">
          <n-select
            v-if="showAssigneeFilter"
            :value="filters.assignee"
            :options="assigneeOptions"
            placeholder="招商归属"
            clearable
            style="width: 150px"
            @update:value="updateFilter('assignee', $event)"
          />
          <n-select
            :value="filters.decision"
            :options="decisionOptions"
            placeholder="推进判断"
            clearable
            style="width: 140px"
            @update:value="updateFilter('decision', $event)"
          />
          <n-select
            :value="status"
            :options="bizStatusOptions"
            placeholder="业务状态"
            clearable
            style="width: 150px"
            @update:value="$emit('update:status', $event)"
          />
        </template>
        <template v-else-if="mode === 'library'">
          <n-select
            :value="filters.assigneeId"
            :options="recruiterOptions"
            placeholder="招商组长"
            clearable
            filterable
            style="width: 180px"
            @update:value="updateFilter('assigneeId', $event)"
          />
          <n-select
            :value="libraryStatus"
            :options="libraryShelfOptions"
            placeholder="联盟推广状态"
            clearable
            style="width: 150px"
            @update:value="$emit('update:libraryStatus', $event)"
          />
          <n-input
            :value="filters.colonelName"
            clearable
            placeholder="团长名称"
            style="width: 150px"
            data-testid="filter-colonel-name"
            @update:value="updateFilter('colonelName', $event)"
          />
          <n-select
            :value="filters.published"
            :options="yesNoOptions"
            placeholder="已发布"
            clearable
            style="width: 120px"
            data-testid="filter-published"
            @update:value="updateFilter('published', $event)"
          />
          <n-select
            :value="filters.listed"
            :options="yesNoOptions"
            placeholder="已挂车"
            clearable
            style="width: 120px"
            data-testid="filter-listed"
            @update:value="updateFilter('listed', $event)"
          />
          <n-select
            :value="filters.freeSample"
            :options="yesNoOptions"
            placeholder="免费寄样"
            clearable
            style="width: 120px"
            data-testid="filter-free-sample"
            @update:value="updateFilter('freeSample', $event)"
          />
          <n-input
            :value="filters.commissionMin"
            clearable
            placeholder="佣金率下限%"
            style="width: 120px"
            data-testid="filter-commission-min"
            @update:value="updateFilter('commissionMin', $event)"
          />
          <n-input
            :value="filters.commissionMax"
            clearable
            placeholder="佣金率上限%"
            style="width: 120px"
            data-testid="filter-commission-max"
            @update:value="updateFilter('commissionMax', $event)"
          />
          <n-input
            :value="filters.livePriceMin"
            clearable
            placeholder="直播价下限"
            style="width: 110px"
            @update:value="updateFilter('livePriceMin', $event)"
          />
          <n-input
            :value="filters.livePriceMax"
            clearable
            placeholder="直播价上限"
            style="width: 110px"
            @update:value="updateFilter('livePriceMax', $event)"
          />
        </template>
      </n-space>
      <n-space v-if="mode === 'library'" wrap :size="12" data-testid="product-library-checkbox-filters">
        <n-checkbox :checked="filters.supportsAds === '1'" data-testid="filter-supports-ads" @update:checked="(v: boolean) => updateFilter('supportsAds', v ? '1' : null)">支持投流</n-checkbox>
        <n-checkbox :checked="filters.materialDownload" data-testid="filter-material-download" @update:checked="(v: boolean) => updateFilter('materialDownload', v)">素材下载</n-checkbox>
        <n-checkbox :checked="filters.exclusivePrice" data-testid="filter-exclusive-price" @update:checked="(v: boolean) => updateFilter('exclusivePrice', v)">专属价</n-checkbox>
        <n-checkbox :checked="filters.productChain" data-testid="filter-product-chain" @update:checked="(v: boolean) => updateFilter('productChain', v)">商品链组</n-checkbox>
        <n-checkbox :checked="filters.handCard" data-testid="filter-hand-card" @update:checked="(v: boolean) => updateFilter('handCard', v)">手卡</n-checkbox>
        <n-checkbox :checked="filters.doubleCommission" data-testid="filter-double-commission" @update:checked="(v: boolean) => updateFilter('doubleCommission', v)">双佣金</n-checkbox>
        <n-checkbox :checked="filters.notInLibrary" data-testid="filter-not-in-library" @update:checked="(v: boolean) => updateFilter('notInLibrary', v)">仅未加入货盘</n-checkbox>
        <n-checkbox :checked="filters.dedup" data-testid="filter-dedup" @update:checked="(v: boolean) => updateFilter('dedup', v)">选品去重</n-checkbox>
      </n-space>
    </n-space>
  </div>
</template>

<script setup lang="ts">
import type { ProductFilterState } from '../product-filters'
import {
  allianceStatusOptions,
  assigneeOptions,
  bizStatusOptions,
  categoryNameOptions,
  commissionOptions,
  decisionOptions,
  goodsTagOptions,
  productTagOptions,
  promotionLinkOptions,
  salesRangeOptions,
  serviceFeeOptions,
  supportsAdsOptions,
  systemTagOptions,
  yesNoOptions
} from '../product-filters'

const props = withDefaults(defineProps<{
  filters: ProductFilterState
  selectedProduct: string | null
  status: string | null
  libraryStatus?: number | null
  productOptions: { label: string; value: string }[]
  productOptionsLoading: boolean
  loading: boolean
  showAssigneeFilter?: boolean
  /** manage=活动/推进池；library=共享商品库 */
  mode?: 'manage' | 'library'
  libraryCategoryOptions?: { label: string; value: string }[]
  recruiterOptions?: { label: string; value: string }[]
  assignedActivityOptions?: { label: string; value: string }[]
  assignedActivityOptionsLoading?: boolean
}>(), {
  showAssigneeFilter: true,
  mode: 'manage',
  libraryStatus: null,
  libraryCategoryOptions: () => [],
  recruiterOptions: () => [],
  assignedActivityOptions: () => [],
  assignedActivityOptionsLoading: false
})

const emit = defineEmits<{
  'update:filters': [value: ProductFilterState]
  'update:selectedProduct': [value: string | null]
  'update:status': [value: string | null]
  'update:libraryStatus': [value: number | null]
  search: [keyword: string]
  'search-click': []
  reset: []
}>()

function updateFilter<K extends keyof ProductFilterState>(key: K, value: ProductFilterState[K]) {
  emit('update:filters', { ...props.filters, [key]: value })
}

function updateAssignedActivity(value: string | null) {
  emit('update:filters', {
    ...props.filters,
    recruitActivityId: value,
    activityId: null,
    recruitActivityName: null
  })
}

const libraryShelfOptions = [
  { label: '推广中', value: 1 },
  { label: '待审核', value: 0 }
]
</script>
