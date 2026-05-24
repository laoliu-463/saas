<template>
  <div class="rule-center app-page" data-testid="rule-center-page">
    <PageHeader
      title="规则中心"
      description="按业务分组维护寄样、达人、提成、推广与安全规则。"
    />

    <div class="rule-center__layout">
      <n-card class="rule-center__nav app-panel" :bordered="false">
        <n-menu
          v-model:value="activeGroup"
          :options="groupMenuOptions"
          @update:value="handleGroupChange"
        />
      </n-card>

      <div class="rule-center__content">
        <n-spin :show="loading">
          <n-card
            v-for="group in visibleGroups"
            :key="group.groupCode"
            :bordered="false"
            class="app-panel rule-center__group-card"
            :data-testid="`rule-group-${group.groupCode}`"
          >
            <template #header>
              <div class="rule-center__group-header">
                <div class="rule-center__group-title">{{ group.groupName }}</div>
                <div class="rule-center__group-desc">{{ group.description }}</div>
              </div>
            </template>

            <n-form label-placement="left" label-width="180">
              <div
                v-for="item in group.items"
                :key="item.key"
                class="rule-center__field"
              >
                <n-form-item>
                  <template #label>
                    <div class="rule-center__field-label">
                      <span>{{ item.label }}</span>
                      <n-button text size="tiny" @click="openChangeLogs(item.key)">历史</n-button>
                    </div>
                  </template>

                  <div class="rule-center__field-body">
                    <n-alert
                      v-if="item.reservedNote"
                      type="warning"
                      :bordered="false"
                      class="rule-center__reserved"
                    >
                      {{ item.reservedNote }}
                    </n-alert>

                    <n-switch
                      v-if="item.valueType === 'boolean'"
                      v-model:value="draftValues[item.key]"
                      :disabled="!item.enabled"
                      checked-value="true"
                      unchecked-value="false"
                    />
                    <n-input-number
                      v-else-if="item.valueType === 'integer'"
                      v-model:value="numberDraft[item.key]"
                      :min="item.min ?? undefined"
                      :max="item.max ?? undefined"
                      :disabled="!item.enabled"
                      style="width: 220px"
                      @update:value="(v: number | null) => syncNumberDraft(item.key, v)"
                    />
                    <n-input-number
                      v-else-if="item.valueType === 'decimal'"
                      v-model:value="numberDraft[item.key]"
                      :min="item.min ?? undefined"
                      :max="item.max ?? undefined"
                      :step="0.01"
                      :disabled="!item.enabled"
                      style="width: 220px"
                      @update:value="(v: number | null) => syncNumberDraft(item.key, v)"
                    />
                    <n-input
                      v-else-if="item.valueType === 'string'"
                      v-model:value="draftValues[item.key]"
                      type="textarea"
                      :rows="4"
                      :disabled="!item.enabled"
                    />
                    <n-input
                      v-else
                      v-model:value="draftValues[item.key]"
                      type="textarea"
                      :rows="4"
                      :disabled="!item.enabled"
                    />
                    <span v-if="item.unit" class="rule-center__unit">{{ item.unit }}</span>
                  </div>
                </n-form-item>
              </div>
            </n-form>

            <PromotionTemplateEditor
              v-if="group.groupCode === 'promotion'"
              v-model="draftValues['promotion.copy_brief_template']"
              :editable="canEditPromotionTemplate"
            />

            <div class="rule-center__group-actions">
              <n-input
                v-model:value="changeReason"
                placeholder="修改原因（可选）"
                style="max-width: 320px"
              />
              <n-button
                type="primary"
                :loading="saving"
                data-testid="rule-center-save-btn"
                @click="openConfirm(group)"
              >
                保存本组
              </n-button>
            </div>
          </n-card>
        </n-spin>
      </div>
    </div>

    <n-modal v-model:show="showConfirm" preset="card" title="确认保存规则变更" :style="{ width: MODAL_WIDTH.lg }">
      <div v-if="pendingDiffs.length" class="rule-center__diff-list">
        <div v-for="diff in pendingDiffs" :key="diff.key" class="rule-center__diff-item">
          <strong>{{ diff.label }}</strong>
          <div class="rule-center__diff-values">
            <div>旧值：{{ diff.oldValue || '（空）' }}</div>
            <div>新值：{{ diff.newValue || '（空）' }}</div>
          </div>
          <div v-if="diff.note" class="rule-center__diff-note">{{ diff.note }}</div>
        </div>
      </div>
      <div class="app-modal-footer">
        <n-button @click="showConfirm = false">取消</n-button>
        <n-button type="primary" :loading="saving" @click="confirmSave">确认保存</n-button>
      </div>
    </n-modal>

    <n-drawer v-model:show="showLogs" :width="520" placement="right">
      <n-drawer-content :title="logTitle">
        <n-spin :show="logsLoading">
          <n-empty v-if="!changeLogs.length" description="暂无变更记录" />
          <n-timeline v-else>
            <n-timeline-item
              v-for="log in changeLogs"
              :key="log.id"
              :title="`${log.changeAction} · ${log.changedAt || ''}`"
            >
              <div>旧值：{{ log.oldValue || '（空）' }}</div>
              <div>新值：{{ log.newValue || '（空）' }}</div>
              <div v-if="log.changeReason" class="rule-center__log-reason">原因：{{ log.changeReason }}</div>
              <div v-if="log.eventId" class="rule-center__log-event">
                <span>事件：{{ log.eventId }}</span>
                <n-button
                  text
                  size="tiny"
                  :loading="loadingEventId === log.eventId"
                  @click="loadEventStatus(log.eventId)"
                >
                  查看事件状态
                </n-button>
              </div>
              <div v-if="log.eventId && eventStatusMap[log.eventId]" class="rule-center__event-status">
                <div>
                  Outbox：{{ eventStatusMap[log.eventId].status || '-' }}
                  <span v-if="eventStatusMap[log.eventId].retryCount != null">
                    · 重试 {{ eventStatusMap[log.eventId].retryCount }} 次
                  </span>
                </div>
                <div v-if="eventStatusMap[log.eventId].errorMessage">
                  错误：{{ eventStatusMap[log.eventId].errorMessage }}
                </div>
                <div v-if="eventStatusMap[log.eventId].consumers?.length">
                  消费者：
                  <span
                    v-for="consumer in eventStatusMap[log.eventId].consumers"
                    :key="consumer.consumerName"
                    class="rule-center__consumer"
                  >
                    {{ consumer.consumerName }} / {{ consumer.status }}
                  </span>
                </div>
              </div>
            </n-timeline-item>
          </n-timeline>
        </n-spin>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup lang="ts">
import { notifyApiFailure } from '../../../utils/requestError'
import { computed, onMounted, reactive, ref } from 'vue'
import { useMessage } from 'naive-ui'
import PageHeader from '../../../components/PageHeader.vue'
import PromotionTemplateEditor from '../components/PromotionTemplateEditor.vue'
import { MODAL_WIDTH } from '../../../constants/ui'
import {
  batchUpdateRuleCenter,
  getRuleCenterChangeLogs,
  getRuleCenterEventStatus,
  getRuleCenterSchema,
  getRuleCenterValues,
  validateRuleCenter
} from '../../../api/ruleCenter'

type RuleItem = {
  key: string
  label: string
  valueType: string
  min?: number | null
  max?: number | null
  unit?: string | null
  enabled: boolean
  reservedNote?: string | null
}

type RuleGroup = {
  groupCode: string
  groupName: string
  description: string
  items: RuleItem[]
}

const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const groups = ref<RuleGroup[]>([])
const activeGroup = ref('sample')
const draftValues = reactive<Record<string, string>>({})
const originalValues = reactive<Record<string, string>>({})
const numberDraft = reactive<Record<string, number | null>>({})
const changeReason = ref('')
const showConfirm = ref(false)
const pendingGroup = ref<RuleGroup | null>(null)
const pendingDiffs = ref<Array<{ key: string; label: string; oldValue: string; newValue: string; note?: string }>>([])
const showLogs = ref(false)
const logsLoading = ref(false)
const changeLogs = ref<any[]>([])
const logTitle = ref('变更历史')
const loadingEventId = ref('')
const eventStatusMap = reactive<Record<string, any>>({})

const groupMenuOptions = computed(() =>
  groups.value.map((group) => ({ label: group.groupName, key: group.groupCode }))
)

const visibleGroups = computed(() =>
  groups.value.filter((group) => group.groupCode === activeGroup.value)
)

const canEditPromotionTemplate = computed(() =>
  groups.value.some((group) =>
    group.groupCode === 'promotion'
    && group.items.some((item) => item.key === 'promotion.copy_brief_template' && item.enabled)
  )
)

function handleGroupChange(value: string) {
  activeGroup.value = value
}

function syncNumberDraft(key: string, value: number | null) {
  numberDraft[key] = value
  draftValues[key] = value == null ? '' : String(value)
}

function hydrateNumberDrafts() {
  for (const group of groups.value) {
    for (const item of group.items) {
      if (item.valueType === 'integer' || item.valueType === 'decimal') {
        const raw = draftValues[item.key]
        numberDraft[item.key] = raw === '' || raw == null ? null : Number(raw)
      }
    }
  }
}

async function fetchData() {
  loading.value = true
  try {
    const [schemaRes, valuesRes]: any[] = await Promise.all([getRuleCenterSchema(), getRuleCenterValues()])
    groups.value = schemaRes?.data?.groups || []
    const values = valuesRes?.data?.values || {}
    Object.keys(values).forEach((key) => {
      draftValues[key] = values[key] ?? ''
      originalValues[key] = values[key] ?? ''
    })
    hydrateNumberDrafts()
    if (groups.value.length && !groups.value.some((g) => g.groupCode === activeGroup.value)) {
      activeGroup.value = groups.value[0].groupCode
    }
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '加载规则中心失败' })
  } finally {
    loading.value = false
  }
}

function collectGroupValues(group: RuleGroup) {
  const payload: Record<string, string> = {}
  for (const item of group.items) {
    if (!item.enabled) continue
    payload[item.key] = draftValues[item.key] ?? ''
  }
  return payload
}

function buildDiffs(group: RuleGroup) {
  return group.items
    .filter((item) => item.enabled && (draftValues[item.key] ?? '') !== (originalValues[item.key] ?? ''))
    .map((item) => ({
      key: item.key,
      label: item.label,
      oldValue: originalValues[item.key] ?? '',
      newValue: draftValues[item.key] ?? '',
      note: item.reservedNote || undefined
    }))
}

async function openConfirm(group: RuleGroup) {
  const diffs = buildDiffs(group)
  if (!diffs.length) {
    message.info('本组没有检测到变更')
    return
  }
  try {
    const validation: any = await validateRuleCenter({ values: collectGroupValues(group) })
    const warnings = validation?.data?.warnings || []
    pendingDiffs.value = diffs.map((diff) => ({
      ...diff,
      note: [diff.note, ...warnings].filter(Boolean).join('；') || undefined
    }))
    pendingGroup.value = group
    showConfirm.value = true
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '校验失败' })
  }
}

async function confirmSave() {
  if (!pendingGroup.value) return
  saving.value = true
  try {
    const res: any = await batchUpdateRuleCenter({
      values: collectGroupValues(pendingGroup.value),
      changeReason: changeReason.value || undefined
    })
    const changedKeys: string[] = res?.data?.changedKeys || []
    changedKeys.forEach((key) => {
      originalValues[key] = draftValues[key] ?? ''
    })
    const warnings: string[] = res?.data?.warnings || []
    showConfirm.value = false
    message.success(warnings.length ? `保存成功：${warnings.join('；')}` : '保存成功')
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '保存失败' })
  } finally {
    saving.value = false
  }
}

async function openChangeLogs(configKey: string) {
  const item = groups.value.flatMap((g) => g.items).find((i) => i.key === configKey)
  logTitle.value = item ? `${item.label} · 变更历史` : '变更历史'
  showLogs.value = true
  logsLoading.value = true
  try {
    const res: any = await getRuleCenterChangeLogs({ key: configKey, page: 1, size: 20 })
    changeLogs.value = res?.data?.records || res?.data?.list || []
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '加载变更历史失败' })
  } finally {
    logsLoading.value = false
  }
}

async function loadEventStatus(eventId: string) {
  if (!eventId || eventStatusMap[eventId]) return
  loadingEventId.value = eventId
  try {
    const res: any = await getRuleCenterEventStatus(eventId)
    eventStatusMap[eventId] = res?.data || {}
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '加载事件状态失败' })
  } finally {
    loadingEventId.value = ''
  }
}

onMounted(fetchData)
</script>

<style scoped>
.rule-center__layout {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 16px;
}

.rule-center__nav {
  position: sticky;
  top: 16px;
  align-self: start;
}

.rule-center__content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.rule-center__group-title {
  font-size: 16px;
  font-weight: 600;
}

.rule-center__group-desc {
  color: var(--n-text-color-3);
  font-size: 13px;
  margin-top: 4px;
}

.rule-center__field-label {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rule-center__field-body {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  flex-wrap: wrap;
}

.rule-center__unit {
  color: var(--n-text-color-3);
}

.rule-center__reserved {
  margin-bottom: 8px;
  width: 100%;
}

.rule-center__group-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: flex-end;
  margin-top: 8px;
}

.rule-center__diff-item + .rule-center__diff-item {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--n-border-color);
}

.rule-center__diff-values {
  margin-top: 6px;
  font-size: 13px;
  color: var(--n-text-color-2);
}

.rule-center__diff-note,
.rule-center__log-reason,
.rule-center__log-event {
  margin-top: 4px;
  font-size: 12px;
  color: var(--n-text-color-3);
}

.rule-center__log-event {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.rule-center__event-status {
  margin-top: 6px;
  padding: 8px;
  border-radius: 6px;
  background: var(--n-color);
  color: var(--n-text-color-2);
  font-size: 12px;
}

.rule-center__consumer {
  display: inline-block;
  margin-right: 8px;
}

@media (max-width: 960px) {
  .rule-center__layout {
    grid-template-columns: 1fr;
  }
}
</style>
