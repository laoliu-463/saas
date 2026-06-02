<template>
  <n-modal
    :show="show"
    preset="dialog"
    title="审核商品"
    :style="{ width: MODAL_WIDTH.lg }"
    @update:show="updateShow"
  >
    <n-form label-placement="top">
      <n-radio-group v-model:value="auditApproved">
        <n-radio :value="true">通过</n-radio>
        <n-radio :value="false">拒绝</n-radio>
      </n-radio-group>

      <template v-if="auditApproved">
        <n-form-item label="审核备注">
          <n-input
            v-model:value="auditRemark"
            type="textarea"
            :rows="2"
            placeholder="审核通过备注，可为空"
          />
        </n-form-item>
        <n-form-item label="专属价说明">
          <n-input v-model:value="form.exclusivePriceRemark" placeholder="直播间专属价、日常到手价等" />
        </n-form-item>
        <n-form-item label="发货信息">
          <n-input v-model:value="form.shippingInfo" placeholder="发货时效、物流覆盖区域等" />
        </n-form-item>
        <n-form-item label="商品卖点">
          <n-input
            v-model:value="form.sellingPoints"
            type="textarea"
            :rows="3"
            placeholder="每行一个卖点"
          />
        </n-form-item>
        <n-form-item label="推广话术">
          <n-input v-model:value="form.promotionScript" type="textarea" :rows="3" />
        </n-form-item>
        <n-form-item label="投流设置">
          <n-space vertical>
            <n-checkbox v-model:checked="form.supportsAds">支持投流</n-checkbox>
            <n-input v-model:value="form.adsRule" type="textarea" :rows="2" placeholder="投流规则，可为空" />
          </n-space>
        </n-form-item>
        <n-form-item label="奖励说明">
          <n-input v-model:value="form.rewardRemark" type="textarea" :rows="2" />
        </n-form-item>
        <n-form-item label="参与要求">
          <n-input v-model:value="form.participationRequirements" type="textarea" :rows="2" />
        </n-form-item>
        <n-form-item label="活动时间">
          <n-input v-model:value="form.campaignTimeRemark" placeholder="活动起止时间或开团节奏" />
        </n-form-item>
        <n-form-item label="手卡素材">
          <n-input
            v-model:value="form.materialFiles"
            type="textarea"
            :rows="3"
            placeholder="每行一个素材 URL"
          />
        </n-form-item>
      </template>

      <n-form-item v-else label="拒绝原因">
        <n-input
          v-model:value="rejectReason"
          type="textarea"
          :rows="3"
          placeholder="拒绝原因必填"
        />
      </n-form-item>
    </n-form>
    <template #action>
      <n-space justify="end">
        <n-button @click="updateShow(false)">取消</n-button>
        <n-button type="primary" :loading="submitting" @click="handleSubmit">提交</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { MODAL_WIDTH } from '../../../constants/ui'
import { approveProduct, rejectProduct } from '../../../api/productManage'
import { notifyApiFailure } from '../../../utils/requestError'
import type { ProductManageRow } from '../../../types/productManage'
import { isProductRelationId, resolveProductRelationId } from '../product-relation-id'
import {
  buildApproveProductPayload,
  createAuditSupplementForm,
  validateApprovalSupplement
} from '../product-audit-supplement'

const props = defineProps<{
  show: boolean
  relationId: string | number | null
  row?: ProductManageRow | null
}>()

const emit = defineEmits(['update:show', 'success'])
const message = useMessage()

const auditApproved = ref(true)
const auditRemark = ref('')
const rejectReason = ref('')
const submitting = ref(false)
const form = reactive(createAuditSupplementForm(null))

const resetForm = () => {
  auditApproved.value = true
  auditRemark.value = ''
  rejectReason.value = ''
  Object.assign(form, createAuditSupplementForm(props.row))
}

watch(
  () => [props.show, props.row],
  () => {
    if (props.show) resetForm()
  }
)

const updateShow = (val: boolean) => {
  emit('update:show', val)
}

const handleSubmit = async () => {
  const relationId = props.relationId == null
    ? resolveProductRelationId(props.row)
    : String(props.relationId).trim()
  if (!isProductRelationId(relationId)) {
    message.warning('缺少有效商品关系 ID，暂时无法提交审核')
    return false
  }

  if (auditApproved.value) {
    const missing = validateApprovalSupplement(form)
    if (missing.length > 0) {
      message.warning(`审核通过前请补充：${missing.join('、')}`)
      return false
    }
  }

  if (!auditApproved.value && !rejectReason.value.trim()) {
    message.warning('拒绝时必须填写原因')
    return false
  }

  submitting.value = true
  try {
    const response: any = auditApproved.value
      ? await approveProduct(relationId, buildApproveProductPayload(auditRemark.value, form))
      : await rejectProduct(relationId, { reason: rejectReason.value.trim() })
    message.success(auditApproved.value ? '审核通过，商品已进入商品库展示竞争' : '审核拒绝')
    emit('success', response?.data)
    updateShow(false)
    return true
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '审核失败' })
    return false
  } finally {
    submitting.value = false
  }
}
</script>
