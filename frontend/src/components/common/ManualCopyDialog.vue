<template>
  <n-modal
    :show="show"
    preset="card"
    :title="title"
    closable
    :style="{ width: '640px', maxWidth: 'calc(100vw - 32px)' }"
    @update:show="handleUpdateShow"
  >
    <div class="manual-copy-dialog">
      <p class="manual-copy-dialog__description">{{ description }}</p>

      <div v-if="promotionLink" class="manual-copy-dialog__meta">
        <span class="manual-copy-dialog__label">推广链接</span>
        <span class="manual-copy-dialog__value">{{ promotionLink }}</span>
      </div>
      <div v-if="pickSource" class="manual-copy-dialog__meta">
        <span class="manual-copy-dialog__label">pick_source</span>
        <span class="manual-copy-dialog__value">{{ pickSource }}</span>
      </div>

      <n-alert v-if="pickSourceWarning" type="warning" :title="pickSourceWarning" data-testid="manual-copy-warning" />

      <textarea
        ref="contentTextarea"
        class="manual-copy-dialog__textarea"
        data-testid="manual-copy-content"
        :value="content"
        readonly
      />

      <div v-if="reason" class="manual-copy-dialog__reason">原因：{{ reason }}</div>

      <div v-if="baiyingUrl" class="manual-copy-dialog__baiying">
        <n-button
          tag="a"
          type="primary"
          secondary
          :href="baiyingUrl"
          target="_blank"
          rel="noopener noreferrer"
          data-testid="manual-copy-baiying"
        >
          前往百应
        </n-button>
      </div>

      <n-space justify="end" :size="8" class="manual-copy-dialog__actions">
        <n-button data-testid="manual-copy-select" @click="selectContent">全选内容</n-button>
        <n-button type="primary" secondary data-testid="manual-copy-retry" @click="emit('retry')">
          再次尝试复制
        </n-button>
        <n-button data-testid="manual-copy-close" @click="emit('close')">关闭</n-button>
      </n-space>
    </div>
  </n-modal>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const props = withDefaults(
  defineProps<{
    show: boolean
    title?: string
    description?: string
    content: string
    promotionLink?: string | null
    pickSource?: string | null
    pickSourceWarning?: string | null
    reason?: string
    baiyingUrl?: string | null
  }>(),
  {
    title: '复制受限，请手动复制',
    description: '当前浏览器环境或不安全端口不允许自动写入剪贴板，请从下方内容中手动复制。',
    promotionLink: null,
    pickSource: null,
    pickSourceWarning: null,
    reason: '',
    baiyingUrl: null
  }
)

const emit = defineEmits<{
  retry: []
  close: []
}>()

const contentTextarea = ref<HTMLTextAreaElement | null>(null)

const selectContent = () => {
  contentTextarea.value?.focus()
  contentTextarea.value?.select()
}

const handleUpdateShow = (value: boolean) => {
  if (!value && props.show) {
    emit('close')
  }
}
</script>

<style scoped>
.manual-copy-dialog {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.manual-copy-dialog__description {
  margin: 0;
  color: var(--text-secondary, #64748b);
  font-size: 13px;
  line-height: 1.6;
}

.manual-copy-dialog__meta {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 8px;
  align-items: start;
  color: var(--text-primary, #0f172a);
  font-size: 13px;
  line-height: 1.6;
}

.manual-copy-dialog__label {
  color: var(--text-muted, #94a3b8);
}

.manual-copy-dialog__value {
  min-width: 0;
  word-break: break-all;
}

.manual-copy-dialog__textarea {
  width: 100%;
  min-height: 220px;
  max-height: 360px;
  resize: vertical;
  padding: 12px;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 6px;
  background: var(--bg-card, #fff);
  color: var(--text-primary, #0f172a);
  font: inherit;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.manual-copy-dialog__textarea:focus {
  outline: 2px solid rgba(239, 68, 68, 0.18);
  border-color: var(--color-primary, #ef4444);
}

.manual-copy-dialog__reason {
  color: var(--text-muted, #94a3b8);
  font-size: 12px;
  line-height: 1.5;
}

.manual-copy-dialog__baiying,
.manual-copy-dialog__actions {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 640px) {
  .manual-copy-dialog__meta {
    grid-template-columns: minmax(0, 1fr);
    gap: 2px;
  }
}
</style>
