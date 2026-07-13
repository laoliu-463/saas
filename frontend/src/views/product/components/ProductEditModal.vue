<template>
  <n-drawer
    :show="show"
    :width="DRAWER_WIDTH_PX.md"
    placement="right"
    data-testid="product-edit-drawer"
    @update:show="updateShow"
  >
    <n-drawer-content closable>
      <template #header>
        <div class="drawer-header">
          <div class="drawer-title">编辑商品</div>
          <div class="drawer-subtitle">维护商品推广规则和活动时间</div>
        </div>
      </template>

      <n-form label-placement="top">
        <n-form-item label="专属价">
          <n-checkbox :checked="form.exclusivePrice" disabled>支持专属价</n-checkbox>
        </n-form-item>
        <n-form-item label="专属价说明">
          <n-input
            v-model:value="form.exclusivePriceRemark"
            data-testid="product-edit-exclusive-price-remark"
            type="textarea"
            :rows="3"
            placeholder="填写专属价、日常到手价等价格策略说明"
          />
        </n-form-item>
        <n-form-item label="是否支持投流">
          <n-checkbox v-model:checked="form.supportsAds">支持投流</n-checkbox>
        </n-form-item>
        <n-form-item label="奖励说明">
          <n-input
            v-model:value="form.rewardRemark"
            data-testid="product-edit-reward-remark"
            type="textarea"
            :rows="3"
          />
        </n-form-item>
        <n-form-item label="参与要求">
          <n-input
            v-model:value="form.participationRequirements"
            data-testid="product-edit-participation-requirements"
            type="textarea"
            :rows="3"
          />
        </n-form-item>
        <n-form-item label="开始时间">
          <n-input
            :value="form.startTime"
            data-testid="product-edit-start-time"
            readonly
            placeholder="暂无开始时间"
          />
        </n-form-item>
        <n-form-item label="结束时间">
          <n-input
            :value="form.endTime"
            data-testid="product-edit-end-time"
            readonly
            placeholder="暂无结束时间"
          />
        </n-form-item>
      </n-form>

      <template #footer>
        <n-space justify="end">
          <n-button @click="updateShow(false)">取消</n-button>
          <n-button type="primary" :loading="submitting" @click="submit">保存</n-button>
        </n-space>
      </template>
    </n-drawer-content>
  </n-drawer>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { notifyApiFailure } from '../../../utils/requestError'
import { updateProduct } from '../../../api/productManage'
import type { ProductManageRow } from '../../../types/productManage'
import { resolveProductRelationId } from '../product-relation-id'
import { DRAWER_WIDTH_PX } from '../../../constants/ui'

const props = defineProps<{
  show: boolean
  row: ProductManageRow | null
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
  success: [payload?: unknown]
}>()

const message = useMessage()
const submitting = ref(false)

const form = reactive({
  exclusivePrice: false,
  exclusivePriceRemark: '',
  supportsAds: false,
  rewardRemark: '',
  participationRequirements: '',
  startTime: '',
  endTime: ''
})

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' ? (value as Record<string, unknown>) : {}
}

function textValue(value: unknown): string {
  return value === null || value === undefined ? '' : String(value).trim()
}

function firstText(...values: unknown[]): string {
  return values.map(textValue).find(Boolean) || ''
}

function booleanValue(value: unknown): boolean {
  if (typeof value === 'boolean') return value
  if (typeof value === 'string') return value.trim().toLowerCase() === 'true' || value.trim() === '1'
  return Boolean(value)
}

function relationId() {
  return resolveProductRelationId(props.row)
}

function resetForm() {
  const row = props.row
  const supplement = asRecord(row?.auditSupplement)
  const productTags = Array.isArray(row?.productTags) ? row.productTags : []

  form.exclusivePrice = booleanValue(supplement.exclusivePrice) || productTags.includes('专属价')
  form.exclusivePriceRemark = textValue(supplement.exclusivePriceRemark)
  form.supportsAds = booleanValue(supplement.supportsAds)
  form.rewardRemark = textValue(supplement.rewardRemark)
  form.participationRequirements = textValue(supplement.participationRequirements)
  form.startTime = firstText(row?.promotionStartTime, row?.activityStartTime)
  form.endTime = firstText(row?.promotionEndTime, row?.activityEndTime)
}

function updateShow(value: boolean) {
  emit('update:show', value)
}

async function submit() {
  const id = relationId()
  if (!id) {
    message.warning('缺少有效商品关系 ID，暂时无法保存')
    return
  }
  submitting.value = true
  try {
    const res = await updateProduct(id, {
      exclusivePriceRemark: form.exclusivePriceRemark,
      supportsAds: form.supportsAds,
      rewardRemark: form.rewardRemark,
      participationRequirements: form.participationRequirements
    })
    message.success('商品信息已保存')
    emit('success', res?.data)
    updateShow(false)
  } catch (error) {
    notifyApiFailure(error, message, { fallbackMessage: '商品信息保存失败' })
  } finally {
    submitting.value = false
  }
}

watch(() => [props.show, props.row], () => {
  if (props.show) resetForm()
}, { immediate: true })
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
</style>
