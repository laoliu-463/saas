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
      <n-form-item v-if="isAdmin" label="渠道" required>
        <n-select
          v-model:value="form.channelUserId"
          :options="channelOptions"
          :loading="channelLoading"
          filterable
          clearable
          placeholder="选择渠道"
          data-testid="quick-sample-channel"
          @update:value="handleChannelChange"
        />
      </n-form-item>
      <n-form-item :label="talentFieldLabel" required>
        <n-select
          v-model:value="form.talentIds"
          :options="talentOptions"
          :loading="talentLoading"
          multiple
          :placeholder="talentPlaceholder"
          :disabled="isAdmin && !form.channelUserId"
          data-testid="quick-sample-talents"
        >
          <template #empty>
            {{ talentEmptyHint }}
          </template>
        </n-select>
      </n-form-item>
      <n-form-item label="商品规格">
        <ProductSpecSelector
          v-model="form.skuId"
          :skus="skuOptions"
          :loading="skuLoading"
          value-field="skuId"
          data-testid="quick-sample-spec"
          @select="handleSkuSelect"
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
import { notifyApiFailure } from '../../../utils/requestError'
import { computed, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { applyQuickSample } from '../../../api/product'
import { getActivityProductSkus } from '../../../api/activityProduct'
import {
  getTalentPrivate,
  getTalentByChannel,
  parsePrivateTalentPoolResponse,
  toPrivateTalentSelectOption
} from '../../../api/talent'
import { useAuthStore } from '../../../stores/auth'
import { loadSampleChannelOptions } from '../../sample/sample-user-filter-options'
import ProductSpecSelector from './ProductSpecSelector.vue'

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

const props = defineProps<{ product: any | null }>()
const emit = defineEmits<{ success: [] }>()

const visible = defineModel<boolean>('show', { default: false })
const message = useMessage()
const authStore = useAuthStore()
const submitting = ref(false)
const channelLoading = ref(false)
const channelOptions = ref<Array<{ label: string; value: string }>>([])
const talentLoading = ref(false)
const talentLoadFailed = ref(false)
const talentOptions = ref<Array<{ label: string; value: string }>>([])
/** 管理员可以选渠道并查询该渠道的达人，非管理员只能选自己的私海 */
const isAdmin = computed(() => authStore.isAdmin)
const talentFieldLabel = computed(() => (isAdmin.value ? '选择达人' : '私海达人'))
const talentPlaceholder = computed(() =>
  isAdmin.value ? '请先选择渠道，再选择达人' : '选择当前渠道已认领达人'
)
const talentEmptyHint = computed(() => {
  if (talentLoading.value) return '加载中…'
  if (talentLoadFailed.value) return '加载失败，请关闭弹窗后重试'
  if (isAdmin.value && !form.value.channelUserId) return '请先选择渠道'
  if (isAdmin.value) return '该渠道暂无认领达人'
  return '暂无私海达人，请先在「达人」页认领后再选'
})
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
  channelUserId: '' as string,
  talentIds: [] as string[],
  skuId: '',
  specification: '',
  quantity: 1,
  recipientName: '',
  recipientPhone: '',
  recipientAddress: '',
  remark: ''
})

const handleSkuSelect = (sku: any | null) => {
  form.value.specification = String(sku?.skuName || '').trim()
}

const mapTalentSelectOptions = (records: any[]) =>
  records
    .map((item: any) => toPrivateTalentSelectOption(item))
    .filter((item: { label: string; value: string }) => item.label && item.value)

const loadChannels = async () => {
  channelLoading.value = true
  try {
    channelOptions.value = await loadSampleChannelOptions('')
  } catch {
    channelOptions.value = []
  } finally {
    channelLoading.value = false
  }
}

const loadTalents = async () => {
  talentLoading.value = true
  talentLoadFailed.value = false
  try {
    if (isAdmin.value) {
      // 管理员：必须先选渠道才能选达人
      const channelUserId = String(form.value.channelUserId || '').trim()
      if (!channelUserId) {
        talentOptions.value = []
        return
      }
      if (!UUID_PATTERN.test(channelUserId)) {
        talentOptions.value = []
        talentLoadFailed.value = true
        message.warning('渠道选项无效，请重新选择')
        return
      }
      // 按渠道查达人（path 参数须为 UUID）
      const res: any = await getTalentByChannel(channelUserId)
      talentOptions.value = mapTalentSelectOptions(parsePrivateTalentPoolResponse({ data: res }))
    } else {
      // 非管理员：查自己的私海
      const res: any = await getTalentPrivate()
      talentOptions.value = mapTalentSelectOptions(parsePrivateTalentPoolResponse(res))
      if (!talentOptions.value.length) {
        message.info('当前账号私海暂无达人，请先在达人页认领')
      }
    }
  } catch (error: any) {
    talentOptions.value = []
    talentLoadFailed.value = true
    notifyApiFailure(error, message, {
      fallbackMessage: isAdmin.value ? '加载达人失败' : '加载私海达人失败'
    })
  } finally {
    talentLoading.value = false
  }
}

const handleChannelChange = () => {
  // 切换渠道时清空已选达人，重新加载该渠道的达人
  form.value.talentIds = []
  if (form.value.channelUserId) {
    loadTalents()
  }
}

watch(visible, (show) => {
  if (show) {
    if (isAdmin.value) {
      loadChannels()
    }
    loadTalents()
    loadSkus()
  }
})

const resetForm = () => {
  form.value = { channelUserId: '', talentIds: [], skuId: '', specification: '', quantity: 1, recipientName: '', recipientPhone: '', recipientAddress: '', remark: '' }
  skuOptions.value = []
}

const submit = async () => {
  if (!relationId.value) {
    message.warning('商品信息不完整')
    return
  }
  if (isAdmin.value && !form.value.channelUserId) {
    message.warning('请先选择渠道')
    return
  }
  if (!form.value.talentIds.length) {
    message.warning(isAdmin.value ? '请至少选择一位达人' : '请至少选择一位私海达人')
    return
  }
  submitting.value = true
  try {
    const res: any = await applyQuickSample(relationId.value, {
      talentIds: form.value.talentIds,
      skuId: form.value.skuId || undefined,
      specification: form.value.specification || undefined,
      quantity: form.value.quantity,
      recipientName: form.value.recipientName || undefined,
      recipientPhone: form.value.recipientPhone || undefined,
      recipientAddress: form.value.recipientAddress || undefined,
      remark: form.value.remark || undefined,
      // 管理员代选渠道时传递 channelUserId
      channelUserId: isAdmin.value && UUID_PATTERN.test(String(form.value.channelUserId || '').trim())
        ? String(form.value.channelUserId).trim()
        : undefined
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
    notifyApiFailure(error, message, { fallbackMessage: '快速寄样失败' })
  } finally {
    submitting.value = false
  }
}
</script>
