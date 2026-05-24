<template>
  <article
    class="selection-card"
    data-testid="product-selection-card"
    @click="$emit('detail', card.raw)"
  >
    <div class="selection-card__media">
      <img
        v-if="imageVisible"
        :src="card.imageUrl"
        :alt="card.productName"
        class="selection-card__img"
        @error="onImageError"
      />
      <div v-else class="selection-card__img-fallback" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="40" height="40">
          <rect x="3" y="3" width="18" height="18" rx="2" />
          <circle cx="8.5" cy="8.5" r="1.5" />
          <path d="M21 15l-5-5L5 21" />
        </svg>
      </div>

      <span v-if="card.isPinned" class="selection-card__pin" data-testid="product-pinned-badge">置顶</span>
      <span class="selection-card__shop-tag">{{ shopTagText }}</span>
      <span v-if="card.supportInvestment" class="selection-card__ads-tag">投流</span>

      <div class="selection-card__media-actions" @click.stop>
        <button
          type="button"
          class="selection-card__btn selection-card__btn--ghost"
          :disabled="!canCopyBrief || copyBriefLoading"
          data-testid="product-copy-brief"
          @click.stop="$emit('copyBrief', card.raw)"
        >
          {{ copyBriefLoading ? '复制中…' : '复制简介' }}
        </button>
        <button
          v-if="canQuickSample"
          type="button"
          class="selection-card__btn selection-card__btn--primary"
          data-testid="product-quick-sample"
          @click.stop="$emit('quickSample', card.raw)"
        >
          快速寄样
        </button>
      </div>
    </div>

    <div class="selection-card__body">
      <div class="selection-card__title-row">
        <span class="selection-card__platform" aria-label="抖音">抖</span>
        <h3 class="selection-card__title" :title="card.productName">{{ card.productName }}</h3>
        <span v-if="card.productId" class="selection-card__id-tag">{{ card.productId }}</span>
      </div>

      <div class="selection-card__inline-actions" @click.stop>
        <n-button
          size="tiny"
          quaternary
          :disabled="!linkUrl"
          data-testid="product-open-link"
          @click="openLink"
        >
          链接
        </n-button>
        <n-button size="tiny" quaternary data-testid="product-refresh" @click="$emit('refresh', card.raw)">
          刷新
        </n-button>
        <n-button size="tiny" quaternary data-testid="product-detail-button" @click="$emit('detail', card.raw)">
          查看详情
        </n-button>
      </div>

      <div class="selection-card__sales-row" @click.stop>
        <div class="selection-card__sales-badge">
          总销量 <strong>{{ card.totalSalesText }}</strong>单
        </div>
        <button
          type="button"
          class="selection-card__baiying-btn"
          :disabled="!card.baiyingUrl && !card.productUrl"
          data-testid="product-baiying"
          @click="openBaiying"
        >
          去百应
        </button>
      </div>

      <div class="selection-card__commission">
        <div class="selection-card__commission-row">
          <div class="selection-card__metric">
            <span class="selection-card__metric-label">直播价</span>
            <span class="selection-card__metric-value">{{ card.livePrice }}</span>
          </div>
          <div class="selection-card__metric">
            <span class="selection-card__metric-label">佣金率</span>
            <span class="selection-card__metric-value selection-card__metric-value--accent">{{ card.commissionRate }}</span>
          </div>
          <div class="selection-card__metric">
            <span class="selection-card__metric-label">服务费率</span>
            <span class="selection-card__metric-value">{{ card.serviceFeeRate }}</span>
          </div>
        </div>
        <div class="selection-card__commission-sub">
          <span>投放期佣金率 {{ card.campaignCommissionRate }}</span>
          <span>投放期服务费率 {{ card.campaignServiceFeeRate }}</span>
        </div>
      </div>

      <dl class="selection-card__fields" @click.stop>
        <div v-for="field in infoFields" :key="field.key" class="selection-card__field">
          <dt>{{ field.label }}</dt>
          <dd :title="field.value">{{ field.value }}</dd>
          <button
            type="button"
            class="selection-card__copy-icon"
            :aria-label="`复制${field.label}`"
            :disabled="!field.copyText"
            @click="copyField(field.copyText, field.label)"
          >
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="9" y="9" width="13" height="13" rx="2" />
              <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1" />
            </svg>
          </button>
        </div>
      </dl>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMessage } from 'naive-ui'
import type { ProductCardView } from '../../views/product/product-library-display'

const props = withDefaults(
  defineProps<{
    card: ProductCardView
    canCopyBrief?: boolean
    canQuickSample?: boolean
    copyBriefLoading?: boolean
  }>(),
  {
    canCopyBrief: false,
    canQuickSample: true,
    copyBriefLoading: false
  }
)

defineEmits<{
  detail: [raw: Record<string, unknown>]
  copyBrief: [raw: Record<string, unknown>]
  quickSample: [raw: Record<string, unknown>]
  refresh: [raw: Record<string, unknown>]
}>()

const message = useMessage()
const imageVisible = ref(Boolean(props.card.imageUrl))

const shopTagText = computed(() => {
  const shop = props.card.shopName?.trim()
  if (!shop || shop === '未识别店铺') return '官方旗舰店'
  return shop.length > 14 ? `${shop.slice(0, 14)}…` : shop
})

const linkUrl = computed(() => props.card.productUrl || props.card.baiyingUrl || '')

const activityLine = computed(() => {
  const id = props.card.activityId || '-'
  const name = props.card.activityName
  return name ? `${name}（${id}）` : id
})

const timeLine = computed(() => {
  const start = props.card.activityStartTime
  const end = props.card.activityEndTime
  if (start && end) return `${start} ~ ${end}`
  if (start) return start
  if (end) return end
  return '-'
})

const infoFields = computed(() => [
  { key: 'recruiter', label: '招商', value: props.card.recruiterName || '-', copyText: props.card.recruiterName },
  { key: 'sample', label: '寄样', value: props.card.sampleRequirement || '-', copyText: props.card.sampleRequirement },
  { key: 'time', label: '时间', value: timeLine.value, copyText: timeLine.value !== '-' ? timeLine.value : '' },
  { key: 'colonel', label: '团长', value: props.card.colonelName || '-', copyText: props.card.colonelName },
  { key: 'shop', label: '店铺', value: props.card.shopName || '-', copyText: props.card.shopName },
  { key: 'activity', label: '活动', value: activityLine.value, copyText: activityLine.value !== '-' ? activityLine.value : '' }
])

const onImageError = () => {
  imageVisible.value = false
}

const writeClipboard = async (text: string) => {
  if (!text) return false
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    return false
  }
}

const copyField = async (text: string | undefined, label: string) => {
  const value = String(text || '').trim()
  if (!value) {
    message.warning(`${label}暂无内容`)
    return
  }
  const ok = await writeClipboard(value)
  if (ok) message.success(`已复制${label}`)
  else message.warning('浏览器未允许写入剪贴板')
}

const openLink = () => {
  const url = linkUrl.value
  if (!url) {
    message.warning('暂无商品链接')
    return
  }
  window.open(url, '_blank', 'noopener,noreferrer')
}

const openBaiying = () => {
  const url = props.card.baiyingUrl || props.card.productUrl
  if (!url) {
    message.warning('暂无百应链接，请稍后在商品详情中查看')
    return
  }
  window.open(url, '_blank', 'noopener,noreferrer')
}
</script>

<style scoped>
.selection-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(15, 23, 42, 0.08);
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.selection-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
}

.selection-card__media {
  position: relative;
  aspect-ratio: 1 / 1;
  background: #f8fafc;
  overflow: hidden;
}

.selection-card__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.selection-card__img-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  background: linear-gradient(145deg, #f1f5f9, #e2e8f0);
}

.selection-card__pin {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 2;
  padding: 2px 10px;
  border-radius: 999px;
  background: rgba(220, 38, 38, 0.92);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  line-height: 20px;
}

.selection-card__shop-tag {
  position: absolute;
  top: 10px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 2;
  max-width: calc(100% - 88px);
  padding: 2px 10px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.72);
  color: #fff;
  font-size: 11px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.selection-card__ads-tag {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 2;
  padding: 2px 8px;
  border-radius: 4px;
  background: #f97316;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
}

.selection-card__media-actions {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 2;
  display: flex;
  gap: 8px;
  padding: 10px;
  background: linear-gradient(180deg, transparent, rgba(15, 23, 42, 0.55));
}

.selection-card__btn {
  flex: 1;
  border: none;
  border-radius: 8px;
  padding: 8px 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.15s ease;
}

.selection-card__btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.selection-card__btn--ghost {
  background: #fff;
  color: #dc2626;
}

.selection-card__btn--primary {
  background: #dc2626;
  color: #fff;
}

.selection-card__body {
  padding: 12px 14px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.selection-card__title-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
}

.selection-card__platform {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: #111827;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.selection-card__title {
  flex: 1;
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  color: #0f172a;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.selection-card__id-tag {
  flex-shrink: 0;
  max-width: 88px;
  padding: 2px 6px;
  border-radius: 4px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selection-card__inline-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.selection-card__sales-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.selection-card__sales-badge {
  flex: 1;
  min-width: 0;
  padding: 6px 10px;
  border-radius: 8px;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 13px;
}

.selection-card__sales-badge strong {
  font-size: 16px;
  font-weight: 700;
  margin: 0 2px;
}

.selection-card__baiying-btn {
  flex-shrink: 0;
  border: none;
  border-radius: 8px;
  padding: 8px 12px;
  background: #ffedd5;
  color: #c2410c;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.selection-card__baiying-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.selection-card__commission {
  border-top: 1px solid #f1f5f9;
  padding-top: 10px;
}

.selection-card__commission-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.selection-card__metric-label {
  display: block;
  font-size: 11px;
  color: #94a3b8;
  margin-bottom: 2px;
}

.selection-card__metric-value {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.selection-card__metric-value--accent {
  color: #dc2626;
}

.selection-card__commission-sub {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 8px;
  font-size: 12px;
  color: #64748b;
}

.selection-card__fields {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.selection-card__field {
  display: grid;
  grid-template-columns: 42px 1fr 24px;
  gap: 8px;
  align-items: center;
  font-size: 12px;
}

.selection-card__field dt {
  margin: 0;
  color: #94a3b8;
}

.selection-card__field dd {
  margin: 0;
  color: #334155;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selection-card__copy-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
}

.selection-card__copy-icon:hover:not(:disabled) {
  background: #f1f5f9;
  color: #dc2626;
}

.selection-card__copy-icon:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
</style>
