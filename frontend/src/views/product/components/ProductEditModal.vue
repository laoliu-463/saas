<template>
  <n-modal :show="show" preset="dialog" title="编辑商品" style="width: 640px" @update:show="updateShow">
    <n-form label-placement="top">
      <n-form-item label="商品标签">
        <n-select v-model:value="form.productTags" multiple tag filterable :options="tagOptions" placeholder="输入或选择商品标签" />
      </n-form-item>
      <n-form-item label="货品标签">
        <n-select v-model:value="form.goodsTags" multiple tag filterable :options="tagOptions" placeholder="输入或选择货品标签" />
      </n-form-item>
      <n-form-item label="推广话术">
        <n-input v-model:value="form.promotionScript" type="textarea" :rows="3" />
      </n-form-item>
      <n-form-item label="卖点">
        <n-input v-model:value="form.sellingPoints" type="textarea" :rows="3" />
      </n-form-item>
      <n-form-item label="投流规则">
        <n-space vertical>
          <n-checkbox v-model:checked="form.supportsAds">支持投流</n-checkbox>
          <n-input v-model:value="form.adsRule" type="textarea" :rows="2" />
        </n-space>
      </n-form-item>
      <n-form-item label="手卡素材">
        <n-input v-model:value="form.handCardUrl" placeholder="手卡素材 URL" />
      </n-form-item>
      <n-form-item label="备注">
        <n-input v-model:value="form.remark" type="textarea" :rows="2" />
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
import { updateProduct } from '../../../api/productManage'
import type { ProductManageRow } from '../../../types/productManage'
import { resolveProductRelationId } from '../product-relation-id'

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
const tagOptions = [
  { label: '主推', value: '主推' },
  { label: '次推', value: '次推' },
  { label: '手卡', value: '手卡' },
  { label: '专属价', value: '专属价' }
]

const form = reactive({
  productTags: [] as string[],
  goodsTags: [] as string[],
  promotionScript: '',
  sellingPoints: '',
  supportsAds: false,
  adsRule: '',
  handCardUrl: '',
  remark: ''
})

function relationId() {
  return resolveProductRelationId(props.row)
}

function resetForm() {
  form.productTags = Array.isArray(props.row?.productTags) ? [...props.row.productTags] : []
  form.goodsTags = Array.isArray(props.row?.goodsTags) ? [...props.row.goodsTags] : []
  form.promotionScript = String(props.row?.promotionMaterialPack?.outreachScript || '')
  form.sellingPoints = ''
  form.supportsAds = Boolean(props.row?.auditSupplement?.supportsAds)
  form.adsRule = String(props.row?.auditSupplement?.adsRule || '')
  form.handCardUrl = String(props.row?.handCardUrl || '')
  form.remark = ''
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
      productTags: form.productTags,
      goodsTags: form.goodsTags,
      promotionScript: form.promotionScript,
      sellingPoints: form.sellingPoints.split('\n').map((item) => item.trim()).filter(Boolean),
      supportsAds: form.supportsAds,
      adsRule: form.adsRule,
      handCardUrl: form.handCardUrl,
      remark: form.remark
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
})
</script>
