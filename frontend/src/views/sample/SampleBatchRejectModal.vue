<template>
  <n-modal
    v-model:show="visible"
    preset="card"
    title="批量拒绝寄样申请"
    :style="{ width: MODAL_WIDTH.md }"
    :mask-closable="!submitting"
    :closable="!submitting"
    data-testid="sample-batch-reject-modal"
    @after-leave="resetForm"
  >
    <n-alert type="warning" :bordered="false">
      拒绝后所选寄样单将变为「已拒绝」，渠道侧可见拒绝原因，请确认后再提交。
    </n-alert>

    <div class="batch-summary" data-testid="sample-batch-reject-summary">
      已选择 <strong>{{ requestNos.length }}</strong> 条待审核寄样单
      <span v-if="requestNos.length > 0">（单次最多 100 条）</span>
    </div>

    <n-scrollbar v-if="requestNos.length" class="request-no-list" style="max-height: 120px">
      <n-space :size="6" wrap>
        <n-tag v-for="no in requestNos" :key="no" size="small" :bordered="false">
          {{ no }}
        </n-tag>
      </n-space>
    </n-scrollbar>

    <n-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-placement="top"
      require-mark-placement="right-hanging"
      class="reject-form"
    >
      <n-form-item label="拒绝原因" path="remark">
        <n-input
          v-model:value="form.remark"
          type="textarea"
          placeholder="请填写拒绝原因，将同步给申请人（如：商品库存不足、达人不符合寄样条件等）"
          :rows="4"
          maxlength="500"
          show-count
          :disabled="submitting"
          data-testid="sample-batch-reject-remark"
        />
      </n-form-item>
    </n-form>

    <div class="form-tip">
      单条失败不会中断整批处理，提交后将汇总成功与失败数量。
    </div>

    <template #footer>
      <n-space justify="end">
        <n-button :disabled="submitting" data-testid="sample-batch-reject-cancel" @click="closeModal">
          取消
        </n-button>
        <n-button
          type="error"
          :loading="submitting"
          data-testid="sample-batch-reject-confirm"
          @click="handleConfirm"
        >
          确认拒绝
        </n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import type { FormInst, FormRules } from 'naive-ui'
import { MODAL_WIDTH } from '../../constants/ui'

const props = defineProps<{
  requestNos: string[]
  submitting?: boolean
}>()

const emit = defineEmits<{
  submit: [remark: string]
}>()

const visible = defineModel<boolean>('show', { default: false })

const formRef = ref<FormInst | null>(null)
const form = reactive({
  remark: ''
})

const rules: FormRules = {
  remark: [
    { required: true, message: '请填写拒绝原因', trigger: ['input', 'blur'] },
    {
      validator: (_rule, value: string) => {
        if (!value || !String(value).trim()) {
          return new Error('拒绝原因不能为空')
        }
        if (String(value).trim().length < 2) {
          return new Error('拒绝原因至少 2 个字')
        }
        return true
      },
      trigger: ['input', 'blur']
    }
  ]
}

watch(
  () => visible.value,
  (open) => {
    if (open) {
      resetForm()
    }
  }
)

const resetForm = () => {
  form.remark = ''
  formRef.value?.restoreValidation()
}

const closeModal = () => {
  if (props.submitting) return
  visible.value = false
}

const handleConfirm = () => {
  formRef.value?.validate((errors) => {
    if (errors) return
    emit('submit', form.remark.trim())
  })
}
</script>

<style scoped>
.batch-summary {
  margin: 12px 0 8px;
  font-size: 13px;
  color: var(--text-secondary, #4b5563);
}

.batch-summary strong {
  color: var(--text-primary, #0f172a);
  font-weight: 600;
}

.request-no-list {
  margin-bottom: 12px;
  padding: 8px 10px;
  border-radius: var(--radius-md, 8px);
  background: var(--bg-subtle, #f8fafc);
  border: 1px solid var(--border-color, #e2e8f0);
}

.reject-form {
  margin-top: 4px;
}

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-muted, #9ca3af);
}
</style>
