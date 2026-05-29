<template>
  <n-modal
    :show="show"
    preset="dialog"
    title="批量分配招商"
    positive-text="确认"
    negative-text="取消"
    @positive-click="handleSubmit"
    @update:show="updateShow"
  >
    <div class="batch-summary">已选择 {{ productIds.length }} 个商品，将统一指定招商组长。</div>
    <n-form-item label="招商组长" required>
      <n-select
        v-model:value="assigneeId"
        :options="userOptions"
        :loading="loadingUsers"
        filterable
        clearable
        remote
        placeholder="请选择招商组长"
        @search="handleSearch"
      />
    </n-form-item>
    <div class="form-tip">单个商品失败不会中断整批，完成后会汇总成功与失败数量。</div>
  </n-modal>
</template>

<script setup lang="ts">
import { notifyApiFailure } from '../../../utils/requestError'
import { ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { batchAssignActivityProducts } from '../../../api/activityProduct'
import { useDebouncedFn } from '../../../utils/debounce'
import { loadProductAssigneeOptions } from '../product-assignee-options'
import { formatBatchResultMessage, normalizeBatchProductIds, type BatchActionResult } from '../product-batch'

const props = defineProps<{
  show: boolean
  activityId: string | number | null
  productIds: string[]
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
  success: [payload: BatchActionResult]
}>()

const message = useMessage()
const assigneeId = ref<string | null>(null)
const userOptions = ref<{ label: string; value: string }[]>([])
const loadingUsers = ref(false)

watch(
  () => props.show,
  async (visible) => {
    if (!visible) return
    assigneeId.value = null
    if (!userOptions.value.length) {
      await fetchUsers('')
    }
  }
)

const updateShow = (value: boolean) => emit('update:show', value)

const fetchUsers = async (keyword: string) => {
  loadingUsers.value = true
  try {
    userOptions.value = await loadProductAssigneeOptions(keyword)
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '加载负责人列表失败' })
  } finally {
    loadingUsers.value = false
  }
}

const debouncedFetchUsers = useDebouncedFn((keyword: string) => {
  void fetchUsers(keyword)
}, 250)

const handleSearch = (keyword: string) => {
  debouncedFetchUsers(String(keyword || '').trim())
}

const handleSubmit = async () => {
  const activityId = props.activityId
  const productIds = normalizeBatchProductIds(props.productIds)
  if (!activityId) {
    message.warning('缺少活动 ID，暂不可批量分配')
    return false
  }
  if (!productIds.length) {
    message.warning('请先选择商品')
    return false
  }
  if (!assigneeId.value) {
    message.warning('请选择招商组长')
    return false
  }
  try {
    const res: any = await batchAssignActivityProducts(activityId, {
      productIds,
      assigneeId: assigneeId.value
    })
    const data = (res?.data || {}) as BatchActionResult
    message.success(formatBatchResultMessage(data, '批量分配招商'))
    emit('success', data)
    updateShow(false)
    return true
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '批量分配招商失败' })
    return false
  }
}
</script>

<style scoped>
.batch-summary {
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--text-secondary, #4b5563);
}

.form-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-muted, #9ca3af);
}
</style>
