<template>
  <n-modal :show="show" preset="dialog" title="合作设置" style="width: 620px" @update:show="updateShow">
    <n-form label-placement="top">
      <n-form-item label="合作类型">
        <n-select v-model:value="form.cooperationType" :options="cooperationTypeOptions" clearable />
      </n-form-item>
      <n-form-item label="寄样标准">
        <n-input v-model:value="form.sampleStandard" />
      </n-form-item>
      <n-form-item label="近30天销售额要求">
        <n-input-number v-model:value="form.salesRequirement30d" :min="0" style="width: 100%" />
      </n-form-item>
      <n-form-item label="达人等级要求">
        <n-input v-model:value="form.talentLevelRequirement" />
      </n-form-item>
      <n-space vertical>
        <n-checkbox v-model:checked="form.supportsReport">支持提报</n-checkbox>
        <n-checkbox v-model:checked="form.supportsCollection">支持采集</n-checkbox>
        <n-checkbox v-model:checked="form.reportLimitExceeded">提报超限</n-checkbox>
        <n-checkbox v-model:checked="form.manualTagsEnabled">人工设置标签</n-checkbox>
        <n-checkbox v-model:checked="form.specialCommissionEnabled">特殊提报比例</n-checkbox>
      </n-space>
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
import { updateCooperationSetting } from '../../../api/productManage'
import { cooperationTypeOptions, type ProductManageRow } from '../../../types/productManage'

const props = defineProps<{ show: boolean; row: ProductManageRow | null }>()
const emit = defineEmits<{ 'update:show': [value: boolean]; success: [payload?: unknown] }>()
const message = useMessage()
const submitting = ref(false)

const form = reactive({
  cooperationType: null as string | null,
  sampleStandard: '',
  salesRequirement30d: null as number | null,
  talentLevelRequirement: '',
  supportsReport: false,
  supportsCollection: false,
  reportLimitExceeded: false,
  manualTagsEnabled: false,
  specialCommissionEnabled: false,
  remark: ''
})

function updateShow(value: boolean) {
  emit('update:show', value)
}

function resetForm() {
  const setting = props.row?.cooperationSetting || {}
  form.cooperationType = setting.cooperationType || null
  form.sampleStandard = String(setting.sampleStandard || '')
  form.salesRequirement30d = Number(setting.salesRequirement30d || 0) || null
  form.talentLevelRequirement = String(setting.talentLevelRequirement || '')
  form.supportsReport = Boolean(setting.supportsReport)
  form.supportsCollection = Boolean(setting.supportsCollection)
  form.reportLimitExceeded = Boolean(setting.reportLimitExceeded)
  form.manualTagsEnabled = Boolean(setting.manualTagsEnabled)
  form.specialCommissionEnabled = Boolean(setting.specialCommissionEnabled)
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
    const res = await updateCooperationSetting(id, { ...form })
    message.success('合作设置已保存')
    emit('success', res?.data)
    updateShow(false)
  } catch (error) {
    notifyApiFailure(error, message, { fallbackMessage: '合作设置保存失败' })
  } finally {
    submitting.value = false
  }
}

watch(() => [props.show, props.row], () => {
  if (props.show) resetForm()
})
</script>
