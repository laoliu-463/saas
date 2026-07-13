<template>
  <n-modal :show="show" preset="dialog" title="寄样设置" style="width: 620px" @update:show="updateShow">
    <n-form label-placement="top" class="sample-setting-form">
      <n-form-item label="是否支持免费寄样">
        <n-radio-group v-model:value="form.supportFreeSample">
          <n-radio :value="true">支持</n-radio>
          <n-radio :value="false">不支持</n-radio>
        </n-radio-group>
      </n-form-item>
      <n-form-item label="是否有寄样门槛">
        <n-radio-group v-model:value="form.hasSampleThreshold">
          <n-radio :value="true">是</n-radio>
          <n-radio :value="false">否</n-radio>
        </n-radio-group>
      </n-form-item>

      <div class="sample-setting-section-title">达人寄样标准</div>
      <div class="sample-threshold-grid">
        <div class="sample-threshold-row">
          <span>近30天橱窗销量</span>
          <span class="sample-threshold-operator">≥</span>
          <n-input-number v-model:value="form.minWindowSales30d" :min="0" clearable placeholder="不限" :disabled="!form.hasSampleThreshold" />
        </div>
        <div class="sample-threshold-row">
          <span>近30天销售额</span>
          <span class="sample-threshold-operator">≥</span>
          <n-input-number v-model:value="form.minSales30d" :min="0" clearable placeholder="不限" :disabled="!form.hasSampleThreshold" />
        </div>
        <div class="sample-threshold-row">
          <span>粉丝数</span>
          <span class="sample-threshold-operator">≥</span>
          <n-input-number v-model:value="form.minFans" :min="0" clearable placeholder="不限" :disabled="!form.hasSampleThreshold" />
        </div>
        <div class="sample-threshold-row">
          <span>达人带货等级</span>
          <span class="sample-threshold-operator">≥</span>
          <n-input-number v-model:value="form.minTalentLevel" :min="0" clearable placeholder="不限" :disabled="!form.hasSampleThreshold" />
        </div>
      </div>

      <div class="sample-setting-section-title">样品设置</div>
      <n-form-item label="样品盒数">
        <n-input-number v-model:value="form.sampleBoxCount" :min="1" style="width: 100%" />
        <span class="sample-setting-unit">盒（当前按 4 盒设置）</span>
      </n-form-item>
      <n-form-item label="每次寄样数量">
        <n-input-number v-model:value="form.sampleQuantity" :min="1" style="width: 100%" />
        <span class="sample-setting-unit">份（当前按 1 份设置）</span>
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
import { resolveProductRelationId } from '../product-relation-id'
import { normalizeSampleSetting, toSampleSettingPayload, type ProductSampleSettingForm } from '../sample-setting'

const props = defineProps<{ show: boolean; row: ProductManageRow | null }>()
const emit = defineEmits<{ 'update:show': [value: boolean]; success: [payload?: unknown] }>()
const message = useMessage()
const submitting = ref(false)
const form = reactive<ProductSampleSettingForm>(normalizeSampleSetting())

function updateShow(value: boolean) {
  emit('update:show', value)
}

function resetForm() {
  const setting = props.row?.sampleSetting || props.row?.auditSupplement || {}
  Object.assign(form, normalizeSampleSetting(setting as Record<string, unknown>))
}

async function submit() {
  const id = resolveProductRelationId(props.row)
  if (!id) {
    message.warning('缺少有效商品关系 ID，暂时无法保存')
    return
  }
  submitting.value = true
  try {
    const res = await updateSampleSetting(id, toSampleSettingPayload(form))
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

<style scoped>
.sample-setting-form {
  padding-top: 4px;
}

.sample-setting-section-title {
  margin: 18px 0 10px;
  color: #1f2937;
  font-size: 14px;
  font-weight: 600;
}

.sample-threshold-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 20px;
}

.sample-threshold-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 20px minmax(100px, 1fr);
  align-items: center;
  gap: 8px;
  color: #4b5563;
  font-size: 13px;
}

.sample-threshold-operator {
  color: #111827;
  font-weight: 600;
  text-align: center;
}

.sample-setting-unit {
  margin-left: 8px;
  color: #9ca3af;
  font-size: 12px;
}

@media (max-width: 640px) {
  .sample-threshold-grid {
    grid-template-columns: 1fr;
  }
}
</style>
