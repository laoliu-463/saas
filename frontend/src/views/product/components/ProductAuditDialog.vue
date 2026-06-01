<template>
  <n-modal
    :show="show"
    preset="dialog"
    title="审核商品"
    positive-text="提交"
    negative-text="取消"
    :style="{ width: MODAL_WIDTH.sm }"
    @positive-click="handleSubmit"
    @update:show="updateShow"
  >
    <n-space vertical :size="16">
      <n-radio-group v-model:value="auditApproved">
        <n-radio :value="true">通过</n-radio>
        <n-radio :value="false">拒绝</n-radio>
      </n-radio-group>

      <n-input
        v-if="auditApproved"
        v-model:value="auditRemark"
        type="textarea"
        :rows="3"
        placeholder="审核通过备注，可为空"
      />
      <n-input
        v-else
        v-model:value="rejectReason"
        type="textarea"
        :rows="3"
        placeholder="拒绝原因必填"
      />
    </n-space>
  </n-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { MODAL_WIDTH } from '../../../constants/ui'
import { approveProduct, rejectProduct } from '../../../api/productManage'
import { notifyApiFailure } from '../../../utils/requestError'

const props = defineProps<{
  show: boolean
  relationId: string | number | null
}>()

const emit = defineEmits(['update:show', 'success'])
const message = useMessage()

const auditApproved = ref(true)
const auditRemark = ref('')
const rejectReason = ref('')

const resetForm = () => {
  auditApproved.value = true
  auditRemark.value = ''
  rejectReason.value = ''
}

watch(
  () => props.show,
  (val) => {
    if (val) resetForm()
  }
)

const updateShow = (val: boolean) => {
  emit('update:show', val)
}

const handleSubmit = async () => {
  const relationId = props.relationId == null ? '' : String(props.relationId).trim()
  if (!relationId) {
    message.warning('缺少商品关系 ID，暂时无法提交审核')
    return false
  }

  if (!auditApproved.value && !rejectReason.value.trim()) {
    message.warning('拒绝时必须填写原因')
    return false
  }

  try {
    const response: any = auditApproved.value
      ? await approveProduct(relationId, { remark: auditRemark.value.trim() })
      : await rejectProduct(relationId, { reason: rejectReason.value.trim() })
    message.success(auditApproved.value ? '审核通过，商品已进入商品库展示竞争' : '审核拒绝')
    emit('success', response?.data)
    updateShow(false)
    return true
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '审核失败' })
    return false
  }
}
</script>
