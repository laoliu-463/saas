<template>
  <n-modal
    v-model:show="visible"
    preset="card"
    title="快速寄样"
    style="width: 640px"
    data-testid="quick-sample-modal"
    @after-leave="resetForm"
  >
    <n-alert type="info" :bordered="false" data-testid="quick-sample-external-hint">
      抖店外部寄样暂未接通，已为你创建系统内寄样申请（LOCAL_FALLBACK）。
    </n-alert>
    <n-form label-placement="left" label-width="96">
      <n-form-item label="私海达人" required>
        <n-select
          v-model:value="form.talentIds"
          :options="talentOptions"
          :loading="talentLoading"
          multiple
          filterable
          placeholder="选择当前渠道已认领达人"
          data-testid="quick-sample-talents"
        />
      </n-form-item>
      <n-form-item label="商品规格">
        <ProductSpecSelector
          v-model="form.specification"
          :skus="skuOptions"
          :loading="skuLoading"
          data-testid="quick-sample-spec"
        />
      </n-form-item>
      <n-form-item label="数量" required>
        <n-input-number v-model:value="form.quantity" :min="1" :max="100" style="width: 160px" />
      </n-form-item>
      <n-form-item label="收货人">
        <n-input v-model:value="form.recipientName" placeholder="收货人姓名" data-testid="quick-sample-recipient-name" />
      </n-form-item>
      <n-form-item label="联系电话">
        <n-input v-model:value="form.recipientPhone" placeholder="收货人手机号" data-testid="quick-sample-recipient-phone" />
      </n-form-item>
      <n-form-item label="收货地址">
        <n-input v-model:value="form.recipientAddress" type="textarea" rows="2" data-testid="quick-sample-address" />
      </n-form-item>
      <n-form-item label="备注">
        <n-input v-model:value="form.remark" type="textarea" rows="2" data-testid="quick-sample-remark" />
      </n-form-item>
    </n-form>

    <template #footer>
      <n-space justify="end">
        <n-button @click="visible = false">取消</n-button>
        <n-button type="primary" :loading="submitting" data-testid="quick-sample-submit" @click="submit">
          提交申请
        </n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { applyQuickSample } from '../../../api/product'
import { getActivityProductSkus } from '../../../api/activityProduct'
import { getTalentPrivate } from '../../../api/talent'
import { MAX_PAGE_SIZE } from '../../../utils/pagination'
import ProductSpecSelector from './ProductSpecSelector.vue'

const props = defineProps<{ product: any | null }>()
const emit = defineEmits<{ success: [] }>()

const visible = defineModel<boolean>('show', { default: false })
const message = useMessage()
const submitting = ref(false)
const talentLoading = ref(false)
const talentOptions = ref<Array<{ label: string; value: string }>>([])
const skuOptions = ref<any[]>([])
const skuLoading = ref(false)

const activityProductIds = computed(() => ({
  activityId: String(props.product?.activityId || props.product?.sourceActivityId || '').trim(),
  productId: String(props.product?.productId || '').trim()
}))

const relationId = computed(() => String(props.product?.id || ''))

const loadSkus = async () => {
  const { activityId, productId } = activityProductIds.value
  if (!activityId || !productId) {
    skuOptions.value = []
    return
  }
  skuLoading.value = true
  try {
    const res: any = await getActivityProductSkus(activityId, productId)
    skuOptions.value = Array.isArray(res?.data) ? res.data : []
  } catch {
    skuOptions.value = []
  } finally {
    skuLoading.value = false
  }
}

const form = ref({
  talentIds: [] as string[],
  specification: '',
  quantity: 1,
  recipientName: '',
  recipientPhone: '',
  recipientAddress: '',
  remark: ''
})

const loadTalents = async () => {
  talentLoading.value = true
  try {
    const res: any = await getTalentPrivate({ page: 1, size: MAX_PAGE_SIZE })
    const records = Array.isArray(res?.data?.records) ? res.data.records : []
    talentOptions.value = records
      .map((item: any) => ({
        label: String(item?.nickname || item?.talentName || item?.douyinUid || '').trim(),
        value: String(item?.douyinUid || item?.talentId || item?.id || '').trim()
      }))
      .filter((item: { label: string; value: string }) => item.label && item.value)
  } catch (error: any) {
    talentOptions.value = []
    message.error(error?.response?.data?.msg || error?.message || '加载私海达人失败')
  } finally {
    talentLoading.value = false
  }
}

watch(visible, (show) => {
  if (show) {
    loadTalents()
    loadSkus()
  }
})

const resetForm = () => {
  form.value = { talentIds: [], specification: '', quantity: 1, recipientName: '', recipientPhone: '', recipientAddress: '', remark: '' }
  skuOptions.value = []
}

const submit = async () => {
  if (!relationId.value) {
    message.warning('商品信息不完整')
    return
  }
  if (!form.value.talentIds.length) {
    message.warning('请至少选择一位私海达人')
    return
  }
  submitting.value = true
  try {
    const res: any = await applyQuickSample(relationId.value, {
      talentIds: form.value.talentIds,
      specification: form.value.specification || undefined,
      quantity: form.value.quantity,
      recipientName: form.value.recipientName || undefined,
      recipientPhone: form.value.recipientPhone || undefined,
      recipientAddress: form.value.recipientAddress || undefined,
      remark: form.value.remark || undefined
    })
    const data = res?.data || {}
    const fallbackUsed = data.items?.some((item: any) => item.fallback || item.fallbackType === 'LOCAL_FALLBACK')
    const msg = fallbackUsed
      ? `提交完成：成功 ${data.successCount ?? 0}，失败 ${data.failureCount ?? 0}。抖店外部寄样暂未接通，已创建系统内寄样申请`
      : `提交完成：成功 ${data.successCount ?? 0}，失败 ${data.failureCount ?? 0}`
    message.success(msg)
    visible.value = false
    emit('success')
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '快速寄样失败')
  } finally {
    submitting.value = false
  }
}
</script>
