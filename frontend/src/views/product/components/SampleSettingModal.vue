<template>
  <n-modal :show="show" preset="dialog" title="寄样设置" style="width: 620px" @update:show="updateShow">
    <n-form label-placement="top">
      <n-checkbox v-model:checked="form.allowSample">允许寄样</n-checkbox>
      <n-form-item label="寄样类型">
        <n-select v-model:value="form.sampleType" :options="sampleTypeOptions" clearable />
      </n-form-item>
      <n-form-item label="寄样负责方">
        <n-input v-model:value="form.responsibleParty" />
      </n-form-item>
      <n-form-item label="寄样要求">
        <n-input v-model:value="form.requirement" type="textarea" :rows="2" />
      </n-form-item>
      <n-form-item label="近30天销售额要求">
        <n-input-number v-model:value="form.salesRequirement30d" :min="0" style="width: 100%" />
      </n-form-item>
      <n-form-item label="达人等级要求">
        <n-input v-model:value="form.talentLevelRequirement" />
      </n-form-item>
      <n-form-item label="规格选项">
        <n-select v-model:value="form.specOptions" multiple tag filterable :options="[]" />
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
import { updateSampleSetting } from '../../../api/productManage'
import type { ProductManageRow } from '../../../types/productManage'

const props = defineProps<{ show: boolean; row: ProductManageRow | null }>()
const emit = defineEmits<{ 'update:show': [value: boolean]; success: [payload?: unknown] }>()
const message = useMessage()
const submitting = ref(false)
const sampleTypeOptions = [
  { label: '免费寄样', value: 'FREE' },
  { label: '付费寄样', value: 'PAID' },
  { label: '不寄样', value: 'NONE' }
]

const form = reactive({
  allowSample: true,
  sampleType: null as string | null,
  responsibleParty: '',
  requirement: '',
  salesRequirement30d: null as number | null,
  talentLevelRequirement: '',
  specOptions: [] as string[],
  remark: ''
})

function updateShow(value: boolean) {
  emit('update:show', value)
}

function resetForm() {
  const setting = props.row?.sampleSetting || {}
  form.allowSample = setting.allowSample !== false
  form.sampleType = setting.sampleType || null
  form.responsibleParty = String(setting.responsibleParty || '')
  form.requirement = String(setting.requirement || '')
  form.salesRequirement30d = Number(setting.salesRequirement30d || 0) || null
  form.talentLevelRequirement = String(setting.talentLevelRequirement || '')
  form.specOptions = Array.isArray(setting.specOptions) ? [...setting.specOptions] : []
  form.remark = String(setting.remark || '')
}

async function submit() {
  const id = String(props.row?.relationId || props.row?.productId || '')
  if (!id) {
    message.warning('缺少商品关系 ID，暂时无法保存')
    return
  }
  submitting.value = true
  try {
    const res = await updateSampleSetting(id, { ...form })
    message.success('寄样设置已保存')
    emit('success', res?.data)
    updateShow(false)
  } catch (error) {
    notifyApiFailure(error, message, { fallbackMessage: '寄样设置保存失败' })
  } finally {
    submitting.value = false
  }
}

watch(() => [props.show, props.row], () => {
  if (props.show) resetForm()
})
</script>
