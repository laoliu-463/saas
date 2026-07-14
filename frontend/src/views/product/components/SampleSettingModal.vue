<template>
  <n-drawer
    :show="show"
    :width="548"
    placement="right"
    class="sample-setting-drawer"
    @update:show="updateShow"
  >
    <n-drawer-content :native-scrollbar="false">
      <template #header>
        <div class="sample-setting-header">
          <div class="sample-setting-title">
            <span class="sample-setting-title-accent" aria-hidden="true" />
            <span>寄样设置</span>
          </div>
          <button class="sample-setting-close" type="button" aria-label="关闭" @click="updateShow(false)">
            ×
          </button>
        </div>
      </template>

      <div class="sample-setting-content">
        <div v-if="loading" class="sample-setting-loading">正在读取寄样设置…</div>
        <div v-if="loadError" class="sample-setting-error" role="alert">{{ loadError }}</div>

        <div class="sample-setting-choice-row">
          <span class="sample-setting-choice-label">是否支持免费寄样：</span>
          <n-radio-group v-model:value="form.supportFreeSample" class="sample-setting-radio-group">
            <n-radio :value="true">支持</n-radio>
            <n-radio :value="false">不支持</n-radio>
          </n-radio-group>
        </div>

        <div class="sample-setting-choice-row">
          <span class="sample-setting-choice-label">是否有寄样门槛：</span>
          <n-radio-group v-model:value="form.hasSampleThreshold" class="sample-setting-radio-group">
            <n-radio :value="true">是</n-radio>
            <n-radio :value="false">否</n-radio>
          </n-radio-group>
        </div>

        <div class="sample-setting-section-title">达人寄样标准</div>
        <div class="sample-setting-criteria">
          <div class="sample-setting-criteria-row">
            <span class="sample-setting-criteria-label">近30天橱窗销量</span>
            <div class="sample-setting-criteria-control">
              <span class="sample-setting-operator">≥</span>
              <n-input-number
                v-model:value="form.minWindowSales30d"
                :min="0"
                clearable
                placeholder="选填项"
                :disabled="!form.hasSampleThreshold"
              />
            </div>
          </div>

          <div class="sample-setting-criteria-row">
            <span class="sample-setting-criteria-label">近30天销售额</span>
            <div class="sample-setting-criteria-control">
              <span class="sample-setting-operator">≥</span>
              <n-input-number
                v-model:value="form.minSales30d"
                :min="0"
                clearable
                placeholder="选填项"
                :disabled="!form.hasSampleThreshold"
              />
            </div>
          </div>

          <div class="sample-setting-criteria-row">
            <span class="sample-setting-criteria-label">粉丝数</span>
            <div class="sample-setting-criteria-control">
              <span class="sample-setting-operator">≥</span>
              <n-input-number
                v-model:value="form.minFans"
                :min="0"
                clearable
                placeholder="选填项"
                :disabled="!form.hasSampleThreshold"
              />
            </div>
          </div>

          <div class="sample-setting-criteria-row">
            <span class="sample-setting-criteria-label">达人带货等级</span>
            <n-select
              v-model:value="form.minTalentLevel"
              :options="TALENT_LEVEL_OPTIONS"
              clearable
              placeholder="选填项"
              :disabled="!form.hasSampleThreshold"
              class="sample-setting-level-control"
            />
          </div>

          <div class="sample-setting-placeholder-row" aria-hidden="true">
            <n-input disabled placeholder="请输入" />
            <n-input disabled placeholder="请输入" />
          </div>
        </div>

        <div class="sample-setting-notes">
          <div>1）在小程序选品时，符合标准的达人才能发起寄样申请；快速申样不进行该标准的判断</div>
          <div>2）近30天橱窗销量/销售额：指有效订单的统计</div>
          <div>3）系统会自动同步一级团长设置的寄样标准，您将无法修改</div>
        </div>
      </div>

      <template #footer>
        <div class="sample-setting-footer">
          <n-button type="default" @click="updateShow(false)">取消</n-button>
          <n-button
            type="primary"
            data-testid="sample-setting-confirm"
            :loading="submitting"
            @click="submit"
          >
            确定
          </n-button>
        </div>
      </template>
    </n-drawer-content>
  </n-drawer>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { notifyApiFailure } from '../../../utils/requestError'
import { fetchSampleSetting, updateSampleSetting } from '../../../api/productManage'
import type { ProductManageRow } from '../../../types/productManage'
import { resolveProductRelationId } from '../product-relation-id'
import {
  normalizeSampleSetting,
  TALENT_LEVEL_OPTIONS,
  toSampleSettingPayload,
  type ProductSampleSettingForm
} from '../sample-setting'

const props = defineProps<{ show: boolean; row: ProductManageRow | null }>()
const emit = defineEmits<{ 'update:show': [value: boolean]; success: [payload?: unknown] }>()
const message = useMessage()
const loading = ref(false)
const loadError = ref('')
const submitting = ref(false)
const form = reactive<ProductSampleSettingForm>(normalizeSampleSetting())
let loadSequence = 0

function updateShow(value: boolean) {
  emit('update:show', value)
}

function resetForm() {
  const setting = props.row?.sampleSetting || props.row?.auditSupplement || {}
  Object.assign(form, normalizeSampleSetting(setting as Record<string, unknown>))
}

async function loadSetting() {
  resetForm()
  loadError.value = ''
  const id = resolveProductRelationId(props.row)
  if (!id) {
    loadError.value = '寄样设置读取失败：缺少有效商品关系 ID'
    return
  }

  const sequence = ++loadSequence
  loading.value = true
  try {
    const res = await fetchSampleSetting(id)
    if (sequence !== loadSequence) return
    const setting = (res as { data?: Record<string, unknown> } | undefined)?.data || {}
    Object.assign(form, normalizeSampleSetting(setting))
  } catch (error) {
    if (sequence !== loadSequence) return
    loadError.value = '寄样设置读取失败'
    notifyApiFailure(error, message, { fallbackMessage: '寄样设置读取失败' })
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
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
  if (props.show) void loadSetting()
}, { immediate: true })
</script>

<style scoped>
.sample-setting-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.sample-setting-title {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #1f2937;
  font-size: 18px;
  font-weight: 600;
}

.sample-setting-title-accent {
  width: 4px;
  height: 20px;
  border-radius: 2px;
  background: #f5222d;
}

.sample-setting-close {
  border: 0;
  padding: 0 4px;
  color: #9ca3af;
  background: transparent;
  cursor: pointer;
  font-size: 26px;
  line-height: 1;
}

.sample-setting-content {
  color: #1f2937;
  font-size: 14px;
}

.sample-setting-loading,
.sample-setting-error {
  margin-bottom: 16px;
  font-size: 13px;
}

.sample-setting-loading {
  color: #6b7280;
}

.sample-setting-error {
  color: #d4380d;
}

.sample-setting-choice-row {
  display: flex;
  align-items: center;
  gap: 18px;
  min-height: 24px;
  margin-bottom: 26px;
}

.sample-setting-choice-label {
  white-space: nowrap;
}

.sample-setting-radio-group {
  display: flex;
  gap: 20px;
}

.sample-setting-section-title {
  margin: 28px 0 16px;
  font-size: 16px;
}

.sample-setting-criteria {
  display: grid;
  gap: 14px;
}

.sample-setting-criteria-row {
  display: grid;
  grid-template-columns: 1.18fr 1fr;
  min-height: 48px;
}

.sample-setting-criteria-label,
.sample-setting-criteria-control,
.sample-setting-level-control {
  border: 1px solid #d9d9d9;
}

.sample-setting-criteria-label {
  display: flex;
  align-items: center;
  padding: 0 16px;
  color: #333;
}

.sample-setting-criteria-control {
  display: flex;
  align-items: center;
  min-width: 0;
  border-left: 0;
  border-radius: 0 8px 8px 0;
  padding: 0 12px;
}

.sample-setting-operator {
  flex: 0 0 auto;
  margin-right: 8px;
  color: #333;
}

.sample-setting-criteria-control :deep(.n-input-number),
.sample-setting-level-control :deep(.n-base-selection) {
  flex: 1;
  min-width: 0;
  border: 0;
  box-shadow: none;
}

.sample-setting-level-control {
  width: auto;
  border-radius: 0 8px 8px 0;
}

.sample-setting-placeholder-row {
  display: grid;
  grid-template-columns: 1.18fr 1fr;
  gap: 8px;
}

.sample-setting-placeholder-row :deep(.n-input) {
  width: 100%;
}

.sample-setting-notes {
  margin-top: 20px;
  color: #999;
  font-size: 13px;
  line-height: 1.65;
}

.sample-setting-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  width: 100%;
}

@media (max-width: 640px) {
  .sample-setting-choice-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  .sample-setting-criteria-row {
    grid-template-columns: 1fr;
  }

  .sample-setting-criteria-label {
    min-height: 40px;
    border-radius: 8px 8px 0 0;
  }

  .sample-setting-criteria-control,
  .sample-setting-level-control {
    min-height: 48px;
    border-left: 1px solid #d9d9d9;
    border-top: 0;
    border-radius: 0 0 8px 8px;
  }
}
</style>
