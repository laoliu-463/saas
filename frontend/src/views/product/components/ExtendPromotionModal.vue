<template>
  <n-modal :show="show" preset="dialog" title="延期推广" style="width: 520px" @update:show="updateShow">
    <n-alert type="info" class="modal-alert">仅维护本系统推广展示时间，不修改抖音官方活动时间。</n-alert>
    <n-form label-placement="top">
      <n-form-item label="延期结束时间" required>
        <n-input v-model:value="form.endTime" placeholder="例如 2026-06-30 23:59:59" />
      </n-form-item>
      <n-form-item label="延期原因" required>
        <n-input v-model:value="form.reason" type="textarea" :rows="3" />
      </n-form-item>
    </n-form>
    <template #action>
      <n-space justify="end">
        <n-button @click="updateShow(false)">取消</n-button>
        <n-button type="primary" :loading="submitting" @click="submit">保存</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { notifyApiFailure } from '../../../utils/requestError'
import { extendPromotion } from '../../../api/productManage'
import type { ProductManageRow } from '../../../types/productManage'

const props = defineProps<{ show: boolean; row: ProductManageRow | null }>()
const emit = defineEmits<{ 'update:show': [value: boolean]; success: [payload?: unknown] }>()
const message = useMessage()
const submitting = ref(false)
const form = reactive({ endTime: '', reason: '' })

function updateShow(value: boolean) {
  emit('update:show', value)
}

function resetForm() {
  form.endTime = ''
  form.reason = ''
}

async function submit() {
  if (!form.endTime.trim() || !form.reason.trim()) {
    message.warning('请填写延期结束时间和延期原因')
    return
  }
  const id = String(props.row?.relationId || props.row?.productId || '')
  if (!id) {
    message.warning('缺少商品关系 ID，暂时无法保存')
    return
  }
  submitting.value = true
  try {
    const res = await extendPromotion(id, { endTime: form.endTime.trim(), reason: form.reason.trim() })
    message.success('延期推广已保存')
    emit('success', res?.data)
    updateShow(false)
  } catch (error) {
    notifyApiFailure(error, message, { fallbackMessage: '延期推广保存失败' })
  } finally {
    submitting.value = false
  }
}

watch(() => props.show, (show) => {
  if (show) resetForm()
})
</script>

<style scoped>
.modal-alert {
  margin-bottom: 12px;
}
</style>
