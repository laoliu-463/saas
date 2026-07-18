<template>
  <n-modal
    v-model:show="visible"
    preset="card"
    title="修改订单"
    :style="{ width: '720px', maxWidth: 'calc(100vw - 32px)' }"
    :mask-closable="!saving"
  >
    <n-spin :show="loading">
      <template v-if="context">
        <n-descriptions bordered :column="2" label-placement="left" size="small">
          <n-descriptions-item label="达人昵称">{{ display(context.talentNickname) }}</n-descriptions-item>
          <n-descriptions-item label="抖音号">{{ display(context.talentDouyinNo) }}</n-descriptions-item>
          <n-descriptions-item label="粉丝数">{{ display(context.talentFansCount) }}</n-descriptions-item>
          <n-descriptions-item label="30天橱窗销量">{{ display(context.talentWindowSales30d) }}</n-descriptions-item>
          <n-descriptions-item label="商品名称">{{ display(context.productName) }}</n-descriptions-item>
          <n-descriptions-item label="商品ID">{{ display(context.productExternalId) }}</n-descriptions-item>
          <n-descriptions-item label="商品规格">{{ display(context.productSpecification) }}</n-descriptions-item>
          <n-descriptions-item label="活动名称">{{ display(context.activityName) }}</n-descriptions-item>
          <n-descriptions-item label="申请数量">{{ display(context.quantity) }}</n-descriptions-item>
          <n-descriptions-item label="申请规则">{{ formatThreshold(context.sampleThreshold) }}</n-descriptions-item>
        </n-descriptions>

        <n-form class="edit-form" label-placement="top">
          <n-form-item label="申请备注">
            <n-input
              v-model:value="form.remark"
              type="textarea"
              maxlength="200"
              show-count
              data-testid="sample-edit-remark"
            />
          </n-form-item>
          <n-alert type="info" :bordered="false">
            收货信息取自该达人的已保存地址，保存后会同步更新该达人地址。
          </n-alert>
          <div class="address-grid">
            <n-form-item label="收货人">
              <n-input v-model:value="form.recipientName" maxlength="100" />
            </n-form-item>
            <n-form-item label="收货电话">
              <n-input v-model:value="form.recipientPhone" maxlength="32" />
            </n-form-item>
          </div>
          <n-form-item label="收货地址">
            <n-input v-model:value="form.recipientAddress" maxlength="512" />
          </n-form-item>
        </n-form>
      </template>
    </n-spin>

    <template #footer>
      <n-space justify="end">
        <n-button :disabled="saving" @click="visible = false">取消</n-button>
        <n-button
          type="primary"
          :loading="saving"
          :disabled="!context"
          data-testid="sample-edit-save"
          @click="save"
        >保存</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { getSampleEditContext, updateSampleCooperationDetails } from '../../api/sample'
import type { SampleEditContext } from '../../types'
import { notifyApiFailure } from '../../utils/requestError'

const props = defineProps<{ sampleId: string }>()
const emit = defineEmits<{ saved: [] }>()
const visible = defineModel<boolean>('show', { default: false })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const context = ref<SampleEditContext | null>(null)
const form = reactive({ remark: '', recipientName: '', recipientPhone: '', recipientAddress: '' })
let sessionGeneration = 0

const unwrap = <T,>(response: any): T => (response?.data || response) as T
const display = (value: unknown) => value === null || value === undefined || value === '' ? '---' : String(value)
const formatThreshold = (value?: Record<string, unknown> | null) => {
  if (!value || !Object.keys(value).length) return '---'
  return Object.entries(value).map(([key, item]) => `${key}：${display(item)}`).join('；')
}

const clearForm = () => {
  context.value = null
  Object.assign(form, { remark: '', recipientName: '', recipientPhone: '', recipientAddress: '' })
}

const reset = () => {
  sessionGeneration += 1
  loading.value = false
  saving.value = false
  clearForm()
}

const isCurrentSession = (generation: number, sampleId: string) => (
  generation === sessionGeneration && visible.value && props.sampleId === sampleId
)

const load = async () => {
  if (!props.sampleId) return
  const sampleId = props.sampleId
  const generation = ++sessionGeneration
  clearForm()
  loading.value = true
  try {
    const result = unwrap<SampleEditContext>(await getSampleEditContext(sampleId))
    if (!isCurrentSession(generation, sampleId)) return
    context.value = result
    Object.assign(form, {
      remark: result.remark || '',
      recipientName: result.recipientName || '',
      recipientPhone: result.recipientPhone || '',
      recipientAddress: result.recipientAddress || ''
    })
  } catch (error) {
    if (!isCurrentSession(generation, sampleId)) return
    notifyApiFailure(error, message, { fallbackMessage: '加载订单信息失败' })
  } finally {
    if (isCurrentSession(generation, sampleId)) loading.value = false
  }
}

const save = async () => {
  if (!context.value) return
  const addressFields = [form.recipientName, form.recipientPhone, form.recipientAddress].map((value) => value.trim())
  const presentCount = addressFields.filter(Boolean).length
  if (presentCount !== 0 && presentCount !== 3) {
    message.error('收货人、收货电话和收货地址必须同时填写')
    return
  }
  const generation = sessionGeneration
  const sampleId = props.sampleId
  const version = context.value.version
  const payload = {
    version,
    remark: form.remark,
    recipientName: addressFields[0],
    recipientPhone: addressFields[1],
    recipientAddress: addressFields[2]
  }
  saving.value = true
  try {
    await updateSampleCooperationDetails(sampleId, payload)
    if (!isCurrentSession(generation, sampleId)) return
    message.success('订单修改成功')
    emit('saved')
    visible.value = false
  } catch (error: any) {
    if (!isCurrentSession(generation, sampleId)) return
    if (error?.response?.status === 409 || error?.code === 409 || error?.response?.data?.code === 409) {
      message.error('数据已更新，请刷新后重试')
    } else {
      notifyApiFailure(error, message, { fallbackMessage: '订单修改失败' })
    }
  } finally {
    if (isCurrentSession(generation, sampleId)) saving.value = false
  }
}

watch(
  () => [visible.value, props.sampleId] as const,
  ([show]) => {
    if (show) load()
    else reset()
  },
  { immediate: true }
)
</script>

<style scoped>
.edit-form { margin-top: 18px; }
.address-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
@media (max-width: 640px) { .address-grid { grid-template-columns: 1fr; } }
</style>
