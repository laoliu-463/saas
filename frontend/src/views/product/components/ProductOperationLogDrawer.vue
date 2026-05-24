<template>
  <n-drawer :show="show" :width="DRAWER_WIDTH_PX.md" placement="right" @update:show="updateShow">
    <n-drawer-content closable>
      <template #header>
        <div class="drawer-header">
          <div class="drawer-title">操作日志</div>
          <div v-if="headerSubtitle" class="drawer-subtitle">{{ headerSubtitle }}</div>
        </div>
      </template>

      <n-spin :show="loading">
        <div v-if="logViews.length" class="log-list">
          <div v-for="item in logViews" :key="item.id" class="log-item">
            <div class="log-item__time">{{ item.timeLabel }}</div>
            <div class="log-item__body">
              <div class="log-item__head">
                <n-tag size="small" :type="item.eventTagType" :bordered="false">{{ item.eventLabel }}</n-tag>
                <n-tag v-if="!item.success" size="small" type="error" :bordered="false">失败</n-tag>
              </div>
              <div class="log-item__summary">{{ item.summary }}</div>
              <div v-if="item.statusFlow" class="log-item__meta">状态变更：{{ item.statusFlow }}</div>
              <div v-for="line in item.detailLines" :key="line" class="log-item__meta">{{ line }}</div>
              <div v-if="item.failureReason" class="log-item__error">{{ item.failureReason }}</div>
              <div class="log-item__operator">操作人：{{ item.operatorLabel }}</div>
            </div>
          </div>
        </div>
        <div v-else-if="!loading" class="empty-state">暂无操作日志</div>
      </n-spin>
    </n-drawer-content>
  </n-drawer>
</template>

<script setup lang="ts">
import { notifyApiFailure } from '../../../utils/requestError'
import { computed, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { DRAWER_WIDTH_PX } from '../../../constants/ui'
import { getActivityProductOperationLogs } from '../../../api/activityProduct'
import {
  mapProductOperationLogViews,
  normalizeLogText,
  type ProductOperationLogRow,
  type ProductOperationLogView
} from '../product-operation-log-display'

const props = defineProps<{
  show: boolean
  activityId: string | number | null
  productId: string | number | null
  productTitle?: string | null
}>()

const emit = defineEmits(['update:show'])
const message = useMessage()

const loading = ref(false)
const logs = ref<ProductOperationLogRow[]>([])

const headerSubtitle = computed(() => {
  const parts = [normalizeLogText(props.productTitle), normalizeLogText(String(props.productId || ''))]
    .filter(Boolean)
  return parts.join(' · ')
})

const logViews = computed<ProductOperationLogView[]>(() => mapProductOperationLogViews(logs.value))

const fetchLogs = async () => {
  if (!props.activityId || !props.productId) return
  loading.value = true
  try {
    const res: any = await getActivityProductOperationLogs(props.activityId, props.productId, {
      page: 1,
      size: 100
    })
    logs.value = res?.data?.records || []
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '加载操作日志失败' })
  } finally {
    loading.value = false
  }
}

watch(
  () => props.show,
  (val) => {
    if (val) {
      fetchLogs()
    } else {
      logs.value = []
    }
  }
)

const updateShow = (val: boolean) => emit('update:show', val)
</script>

<style scoped>
.drawer-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.drawer-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.drawer-subtitle {
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.4;
}

.log-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.log-item {
  display: grid;
  grid-template-columns: 132px minmax(0, 1fr);
  gap: 12px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-color, rgba(0, 0, 0, 0.06));
}

.log-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.log-item__time {
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.6;
  white-space: nowrap;
}

.log-item__head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.log-item__summary {
  font-size: 14px;
  color: var(--text-primary);
  line-height: 1.6;
}

.log-item__meta,
.log-item__operator {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.5;
}

.log-item__error {
  margin-top: 6px;
  font-size: 12px;
  color: var(--error-color, #d03050);
  line-height: 1.5;
}

.empty-state {
  text-align: center;
  padding: 40px;
  color: var(--text-muted);
}
</style>
