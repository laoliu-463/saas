<template>
  <n-modal :show="show" preset="dialog" title="批量补录商品信息" style="width: 560px" @update:show="updateShow">
    <n-alert v-if="!productIds.length" type="warning" class="modal-alert">请先选择需要补录的商品。</n-alert>
    <n-alert v-else type="info" class="modal-alert">已选 {{ productIds.length }} 个商品，最多支持 100 个；空字段不会覆盖已有值。</n-alert>
    <n-form label-placement="top">
      <n-form-item label="商品标签">
        <n-select v-model:value="form.productTags" multiple tag filterable :options="[]" />
      </n-form-item>
      <n-form-item label="货品标签">
        <n-select v-model:value="form.goodsTags" multiple tag filterable :options="[]" />
      </n-form-item>
      <n-form-item label="推广话术">
        <n-input v-model:value="form.promotionScript" type="textarea" :rows="3" />
      </n-form-item>
      <n-form-item label="备注">
        <n-input v-model:value="form.remark" type="textarea" :rows="2" />
      </n-form-item>
    </n-form>
    <template #action>
      <n-space justify="end">
        <n-button @click="updateShow(false)">取消</n-button>
        <n-button type="primary" :disabled="!canSubmit" :loading="submitting" @click="submit">提交</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { notifyApiFailure } from '../../../utils/requestError'
import { batchSupplementProducts } from '../../../api/productManage'

const props = defineProps<{
  show: boolean
  productIds: string[]
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
  success: [payload?: unknown]
}>()

const message = useMessage()
const submitting = ref(false)
const form = reactive({
  productTags: [] as string[],
  goodsTags: [] as string[],
  promotionScript: '',
  remark: ''
})

const canSubmit = computed(() => props.productIds.length > 0 && props.productIds.length <= 100)

function resetForm() {
  form.productTags = []
  form.goodsTags = []
  form.promotionScript = ''
  form.remark = ''
}

function updateShow(value: boolean) {
  emit('update:show', value)
}

async function submit() {
  if (!canSubmit.value) {
    message.warning(props.productIds.length > 100 ? '一次最多补录 100 个商品' : '请先选择商品')
    return
  }
  submitting.value = true
  try {
    const res = await batchSupplementProducts({
      productIds: props.productIds,
      productTags: form.productTags.length ? form.productTags : undefined,
      goodsTags: form.goodsTags.length ? form.goodsTags : undefined,
      promotionScript: form.promotionScript || undefined,
      remark: form.remark || undefined
    })
    message.success('批量补录已提交')
    emit('success', res?.data)
    updateShow(false)
  } catch (error) {
    notifyApiFailure(error, message, { fallbackMessage: '批量补录失败' })
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
