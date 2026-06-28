<!--
  ProductSelectionCard - 商品库选品卡片（V2 优化版）

  用途：在商品库页面以紧凑卡片展示商品核心信息；桌面端 hover 时弹出下拉抽屉，
       移动端点击卡片展开 / 收起，展示低频业务字段（招商/寄样/时间/团长/店铺/活动/库存）
       并提供逐字段复制按钮。

  布局：
    - 默认态：252px × 415px 固定尺寸（响应式断点降为 4 列 / 3 列 / 1 列）
    - 顶部图片区（aspect-ratio 1:1）+ 底部标题+销量+核心指标
    - 鼠标悬浮：图片区浮现“复制简介”和“快速寄样”按钮，且从卡片底部覆盖弹出字段抽屉，不改变商品网格布局

  Props:
    - card: 商品卡片视图数据（ProductCardView 类型），必填
    - canCopyBrief: 是否允许"复制简介"按钮，默认 false
    - canQuickSample: 是否显示"快速寄样"按钮，默认 true
    - copyBriefLoading: 复制简介按钮的 loading 状态，默认 false

  Events:
    - detail: 点击卡片或"查看详情"按钮时触发
    - copyBrief: 点击"复制简介"按钮时触发
    - quickSample: 点击"快速寄样"按钮时触发
    - refresh: 点击"刷新"按钮时触发
-->
<template>
  <article
    class="selection-card"
    :class="{ 'is-expanded': expanded, 'hover-mode': supportsHover }"
    data-testid="product-selection-card"
    @mouseenter="handleMouseEnter"
    @mouseleave="handleMouseLeave"
  >
    <div class="selection-card__body" @click="handleCardBodyClick">
      <div class="selection-card__media">
        <img
          v-if="imageVisible"
          :src="card.imageUrl"
          :alt="card.productName"
          class="selection-card__img"
          loading="lazy"
          decoding="async"
          @error="onImageError"
        />
        <div v-else class="selection-card__img-fallback" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="36" height="36">
            <rect x="3" y="3" width="18" height="18" rx="2" />
            <circle cx="8.5" cy="8.5" r="1.5" />
            <path d="M21 15l-5-5L5 21" />
          </svg>
        </div>

        <span v-if="card.isPinned" class="selection-card__pin" data-testid="product-pinned-badge">置顶</span>
        <span v-if="stockAlertText" class="selection-card__stock-alert" data-testid="product-stock-alert">
          {{ stockAlertText }}
        </span>
        <span class="selection-card__shop-tag" :title="card.shopName">{{ shopTagText }}</span>
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
            class="selection-card__btn selection-card__btn--ghost"
            data-testid="product-quick-sample"
            @click.stop="$emit('quickSample', card.raw)"
          >
            快速寄样
          </button>
        </div>
      </div>

      <div class="selection-card__content">
        <div class="selection-card__title-row">
          <h3 class="selection-card__title" :title="card.productName">
            <svg class="selection-card__title-dy" viewBox="0 0 24 24" width="12" height="12" fill="currentColor">
              <path d="M12.525.02c1.31-.02 2.61-.01 3.91-.01.08 1.53.63 3.02 1.59 4.23.86 1.08 2.07 1.85 3.4 2.17.02 1.34.01 2.68.01 4.02-1.58-.05-3.13-.59-4.4-1.55-.42-.32-.81-.69-1.15-1.1v7.02c0 3.42-1.93 6.16-5.18 6.94-1.92.46-4.04.14-5.73-.89-1.91-1.17-3.07-3.34-3.01-5.6.08-2.91 2.11-5.46 5-6.07.45-.1 1.08-.12 1.54-.03v4.18c-.8-.21-1.74-.08-2.39.46-.72.6-1.02 1.63-.78 2.53.25.96 1.13 1.65 2.12 1.69 1.58.07 2.45-1.09 2.46-2.52.01-3.69-.01-7.38.01-11.07.01-1.39.01-2.77.01-4.16z" />
            </svg>
            {{ card.productName }}
          </h3>
          <div class="selection-card__quick-actions" @click.stop>
            <button
              type="button"
              class="selection-card__icon-btn"
              title="复制ID"
              data-testid="product-copy-id"
              @click="copyField(card.productId, '商品ID')"
            >
              ID
            </button>
            <button
              type="button"
              class="selection-card__icon-btn"
              title="复制链接"
              data-testid="product-copy-url"
              :disabled="!card.productUrl"
              @click="copyField(card.productUrl, '商品链接')"
            >
              <svg viewBox="0 0 24 24" width="10" height="10" fill="none" stroke="currentColor" stroke-width="2.5">
                <path d="M10 13a5 5 0 007.54.54l3-3a5 5 0 00-7.07-7.07l-1.72 1.71" />
                <path d="M14 11a5 5 0 00-7.54-.54l-3 3a5 5 0 007.07 7.07l1.71-1.71" />
              </svg>
            </button>
            <button
              type="button"
              class="selection-card__icon-btn"
              title="刷新"
              data-testid="product-refresh"
              @click="$emit('refresh', card.raw)"
            >
              <svg viewBox="0 0 24 24" width="10" height="10" fill="none" stroke="currentColor" stroke-width="2.5">
                <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 11-.57-8.38l5.67-5.67" />
              </svg>
            </button>
            <button
              type="button"
              class="selection-card__icon-btn"
              title="查看详情"
              data-testid="product-detail-btn"
              @click="$emit('detail', card.raw)"
            >
              详情
            </button>
          </div>
        </div>

        <div class="selection-card__sales-row">
          <span class="selection-card__sales-btn">
            总销量{{ card.totalSalesText }}单
          </span>
          <a
            v-if="card.baiyingUrl"
            :href="card.baiyingUrl"
            target="_blank"
            class="selection-card__buyin-btn"
            @click.stop
          >
            去百应
          </a>
          <span v-else class="selection-card__buyin-btn selection-card__buyin-btn--disabled">
            去百应
          </span>
        </div>

        <div class="selection-card__metrics">
          <div class="selection-card__metrics-row">
            <span class="selection-card__metric-tag" :title="`公开佣金 ${displayCommissionRate}`">
              佣 {{ displayCommissionRate }}
            </span>
            <span class="selection-card__metric-tag">
              {{ card.specs?.length ? card.specs.length + '规格' : '单规格' }}
            </span>
            <span class="selection-card__metric-tag" :title="String(card.raw?.colonelCouponInfo || card.raw?.colonel_coupon_info || '无券')">
              {{ String(card.raw?.colonelCouponInfo || card.raw?.colonel_coupon_info || '无券') }}
            </span>
          </div>
          <div class="selection-card__metrics-grid">
            <div class="selection-card__metric-item">
              <span class="selection-card__metric-num">{{ card.livePrice || '-' }}</span>
              <span class="selection-card__metric-label">直播价</span>
            </div>
            <div class="selection-card__metric-item">
              <span class="selection-card__metric-num selection-card__metric-num--primary">{{ card.commissionRate || '-' }}</span>
              <span class="selection-card__metric-label">佣金率</span>
            </div>
            <div class="selection-card__metric-item">
              <span class="selection-card__metric-num selection-card__metric-num--accent">{{ card.serviceFeeRate || '-' }}</span>
              <span class="selection-card__metric-label">服务费率</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <NCollapseTransition :show="drawerVisible || supportsHover">
      <div class="selection-card__drawer-shell" @click.stop>
        <div
          class="selection-card__drawer"
          data-testid="product-selection-drawer"
        >
          <div class="selection-card__drawer-header">
            <span class="selection-card__drawer-title">详细信息</span>
          </div>
          <dl class="selection-card__fields">
            <div v-for="field in infoFields" :key="field.key" class="selection-card__field">
              <dt>{{ field.label }}</dt>
              <dd
                :class="{
                  'is-empty': !field.value || field.value === '-',
                  'is-warning': field.warning
                }"
                :title="field.value"
              >
                {{ field.value || '-' }}
              </dd>
              <button
                type="button"
                class="selection-card__copy-icon"
                :aria-label="`复制${field.label}`"
                :disabled="!field.copyText"
                data-testid="product-field-copy"
                @click="copyField(field.copyText, field.label)"
              >
                <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="9" y="9" width="13" height="13" rx="2" />
                  <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1" />
                </svg>
              </button>
            </div>
          </dl>
        </div>
      </div>
    </NCollapseTransition>
  </article>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { NCollapseTransition, useMessage } from 'naive-ui'
import type { ProductCardView } from '../../views/product/product-library-display'

const props = withDefaults(
  defineProps<{
    /** 商品卡片视图数据，必填 */
    card: ProductCardView
    /** 是否允许"复制简介"按钮，默认 false */
    canCopyBrief?: boolean
    /** 是否显示"快速寄样"按钮，默认 true */
    canQuickSample?: boolean
    /** 复制简介按钮的 loading 状态，默认 false */
    copyBriefLoading?: boolean
  }>(),
  {
    canCopyBrief: false,
    canQuickSample: true,
    copyBriefLoading: false
  }
)

const emit = defineEmits<{
  detail: [raw: Record<string, unknown>]
  copyBrief: [raw: Record<string, unknown>]
  quickSample: [raw: Record<string, unknown>]
  refresh: [raw: Record<string, unknown>]
}>()

const message = useMessage()
const imageVisible = ref(Boolean(props.card.imageUrl))
/**
 * 设备能力：mount 时一次性探测，之后不再变动。
 * - hover-mode=true：桌面/支持 hover 的环境，drawer 由 hoverActive 控制；
 * - hover-mode=false：触屏/无 hover 设备，drawer 由 expanded（点击）控制。
 */
const supportsHover = ref(true)
/** 桌面/支持 hover 设备的 hover 状态，与 expanded 互斥使用。 */
const hoverActive = ref(false)
/** 触屏设备的点击展开态。 */
const expanded = ref(false)
let closeTimer: number | undefined

/**
 * 抽屉实际可见性：按设备能力路由到对应状态。
 */
const drawerVisible = computed(() =>
  supportsHover.value ? hoverActive.value : expanded.value
)

const isHoverCapable = () => {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return true
  // 仅在"明确不支持 hover"时才走 click 分支
  return !window.matchMedia('(hover: none)').matches
}

const clearCloseTimer = () => {
  if (closeTimer !== undefined) {
    window.clearTimeout(closeTimer)
    closeTimer = undefined
  }
}

const handleMouseEnter = () => {
  if (!supportsHover.value) {
    clearCloseTimer()
    expanded.value = true
    return
  }
  clearCloseTimer()
  hoverActive.value = true
}

const handleMouseLeave = () => {
  if (!supportsHover.value) {
    clearCloseTimer()
    closeTimer = window.setTimeout(() => {
      expanded.value = false
      closeTimer = undefined
    }, 120)
    return
  }
  clearCloseTimer()
  hoverActive.value = false
}

const handleCardBodyClick = () => {
  if (supportsHover.value) {
    emit('detail', props.card.raw)
    return
  }
  clearCloseTimer()
  expanded.value = !expanded.value
}

onMounted(() => {
  supportsHover.value = isHoverCapable()
})

onBeforeUnmount(() => {
  clearCloseTimer()
})

const shopTagText = computed(() => {
  const shop = props.card.shopName?.trim()
  if (!shop || shop === '未识别店铺') return '官方旗舰店'
  return shop.length > 10 ? `${shop.slice(0, 10)}…` : shop
})

/** 库存文本（来自上游 productStock）。空值返回 '-'，由复制按钮 disabled 控制。 */
const stockValue = computed(() => {
  const text = String(props.card.productStock ?? '').trim()
  return text
})

/** 库存告警：0 / 非正数 视为"无库存"；≤10 视为"库存紧张"。用于红色高亮 + 角标。 */
const stockAlertText = computed(() => {
  const raw = String(props.card.productStock ?? '').trim()
  if (!raw) return ''
  const num = Number(raw.replace(/[^0-9]/g, ''))
  if (!Number.isFinite(num)) return ''
  if (num <= 0) return '无库存'
  if (num <= 10) return '库存紧张'
  return ''
})

/** 库存字段在抽屉里是否用 warning 样式 */
const stockWarning = computed(() => Boolean(stockAlertText.value))

/** 活动时间范围：start ~ end，仅单侧时只显示一侧 */
const timeLine = computed(() => {
  const start = props.card.activityStartTime
  const end = props.card.activityEndTime
  if (start && end) return `${start} ~ ${end}`
  if (start) return start
  if (end) return end
  return '-'
})

const shopScoreValue = computed(() => {
  const score = props.card.shopScore
  if (score === null || score === undefined) return ''
  return String(score)
})

const displayCommissionRate = computed(() => {
  const campaign = String(props.card.campaignCommissionRate || '').trim()
  if (campaign && campaign !== '-') return campaign
  return props.card.commissionRate || '-'
})

const infoFields = computed(() => [
  {
    key: 'recruiter',
    label: '招商',
    value: props.card.recruiterName,
    copyText: props.card.recruiterName
  },
  {
    key: 'sample',
    label: '寄样',
    value: props.card.sampleRequirement,
    copyText: props.card.sampleRequirement
  },
  {
    key: 'time',
    label: '时间',
    value: timeLine.value,
    copyText: timeLine.value !== '-' ? timeLine.value : ''
  },
  {
    key: 'colonel',
    label: '团长',
    value: props.card.colonelName,
    copyText: props.card.colonelName
  },
  {
    key: 'shop',
    label: '店铺',
    value: props.card.shopName,
    copyText: props.card.shopName
  },
  {
    key: 'activity',
    label: '活动',
    value: props.card.activityName,
    copyText: props.card.activityName
  },
  {
    key: 'stock',
    label: '库存',
    value: stockValue.value,
    copyText: stockValue.value,
    warning: stockWarning.value
  },
  {
    key: 'shopScore',
    label: '商家评分',
    value: shopScoreValue.value,
    copyText: shopScoreValue.value
  }
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
</script>

<style scoped>
/* ============================================================
   容器
   - 默认主卡保持 252×415 紧凑高度（响应式断点由父级 grid 控制列数）
   - 桌面详情抽屉 absolute 覆盖下方卡片，不参与商品网格布局
   ============================================================ */
.selection-card {
  position: relative;
  width: min(252px, 100%);
  max-width: 252px;
  height: 415px;
  min-height: 415px;
  box-sizing: border-box;
  container-type: inline-size;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.05);
  overflow: visible;
  z-index: 1;
  cursor: pointer;
  transition: box-shadow 0.25s ease, transform 0.25s ease;
}

.selection-card:hover,
.selection-card.is-expanded {
  z-index: 30;
  box-shadow: 0 10px 25px rgba(15, 23, 42, 0.12);
  transform: translateY(-2px);
}

.selection-card:not(.hover-mode) {
  height: auto;
}

/* ============================================================
   默认态（415 高度内）：图片 + 内容区
   ============================================================ */
.selection-card__body {
  width: 100%;
  height: 415px;
  border-radius: 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.selection-card__media {
  position: relative;
  flex: 0 0 auto;
  width: 100%;
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
  top: 8px;
  left: 8px;
  z-index: 3;
  padding: 2px 10px;
  border-radius: 999px;
  background: rgba(220, 38, 38, 0.92);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
  box-shadow: 0 2px 6px rgba(220, 38, 38, 0.2);
}

.selection-card__stock-alert {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 3;
  padding: 2px 8px;
  border-radius: 4px;
  background: #f97316;
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  line-height: 16px;
}

.selection-card__shop-tag {
  position: absolute;
  bottom: 8px;
  left: 8px;
  z-index: 3;
  max-width: calc(100% - 16px);
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.72);
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  backdrop-filter: blur(4px);
}

.selection-card__ads-tag {
  position: absolute;
  top: 32px;
  right: 8px;
  z-index: 3;
  padding: 2px 8px;
  border-radius: 4px;
  background: #3b82f6;
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  line-height: 16px;
}

/* Hover Operations Overlay */
.selection-card__media-actions {
  position: absolute;
  inset: 0;
  z-index: 4;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 10px;
  padding: 16px;
  background: rgba(15, 23, 42, 0.45);
  opacity: 0;
  transition: opacity 0.25s ease;
  backdrop-filter: blur(2px);
}

.selection-card:hover .selection-card__media-actions {
  opacity: 1;
}

.selection-card__btn {
  border: none;
  border-radius: 20px;
  padding: 6px 18px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.2s ease, background-color 0.2s ease;
  line-height: 18px;
  width: 120px;
  text-align: center;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.selection-card__btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.selection-card__btn--ghost {
  background: #fff;
  color: #dc2626;
}

.selection-card__btn--ghost:hover:not(:disabled) {
  background: #fef2f2;
  transform: scale(1.05);
}

.selection-card__btn--primary {
  background: #dc2626;
  color: #fff;
}

.selection-card__btn--primary:hover {
  background: #b91c1c;
  transform: scale(1.05);
}

/* Content Area */
.selection-card__content {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 12px;
  min-height: 0;
}

/* Title & Quick Actions Row */
.selection-card__title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.selection-card__title {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
  color: #0f172a;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-all;
  flex: 1;
}

.selection-card__title-dy {
  display: inline-block;
  vertical-align: middle;
  margin-top: -2px;
  margin-right: 2px;
  color: #000000;
}

.selection-card__quick-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.selection-card__icon-btn {
  border: 1px solid #e2e8f0;
  background: #fff;
  border-radius: 4px;
  padding: 2px 5px;
  font-size: 9px;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s ease;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 18px;
  font-weight: 600;
}

.selection-card__icon-btn:hover {
  border-color: #dc2626;
  color: #dc2626;
  background: #fef2f2;
}

.selection-card__icon-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* Sales & Baiying Row */
.selection-card__sales-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.selection-card__sales-btn {
  background: #fff1f2;
  color: #e11d48;
  border-radius: 6px;
  padding: 3px 8px;
  font-size: 10px;
  font-weight: 600;
  display: inline-block;
}

.selection-card__buyin-btn {
  background: #fef9c3;
  color: #ca8a04;
  border-radius: 6px;
  padding: 3px 10px;
  font-size: 10px;
  font-weight: 600;
  text-decoration: none;
  display: inline-block;
  transition: opacity 0.2s ease;
}

.selection-card__buyin-btn:hover:not(.selection-card__buyin-btn--disabled) {
  opacity: 0.85;
}

.selection-card__buyin-btn--disabled {
  background: #f1f5f9;
  color: #94a3b8;
  cursor: not-allowed;
}

/* Core Metrics Area */
.selection-card__metrics {
  border-top: 1px solid #f1f5f9;
  padding-top: 8px;
  margin-top: auto;
}

.selection-card__metrics-row {
  display: flex;
  gap: 4px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.selection-card__metric-tag {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  color: #64748b;
  border-radius: 4px;
  padding: 1px 6px;
  font-size: 9px;
  white-space: nowrap;
  max-width: 72px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.selection-card__metrics-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 4px;
  text-align: center;
}

.selection-card__metric-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.selection-card__metric-num {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}

.selection-card__metric-num--primary {
  color: #dc2626;
}

.selection-card__metric-num--accent {
  color: #d97706;
}

.selection-card__metric-label {
  font-size: 9px;
  color: #94a3b8;
  margin-top: 2px;
}

/* ============================================================
   Hover 抽屉（桌面 absolute 覆盖，不推动下方商品）
   ============================================================ */
.selection-card__drawer-shell {
  position: absolute;
  left: 0;
  right: 0;
  top: 100%;
  z-index: 40;
  width: 100%;
  max-height: 0;
  opacity: 0;
  pointer-events: none;
  overflow: hidden;
  transform: translateY(-4px);
  transition: max-height 0.22s ease, opacity 0.18s ease, transform 0.18s ease;
  border-radius: 0 0 12px 12px;
}

.selection-card.hover-mode:hover .selection-card__drawer-shell,
.selection-card.hover-mode:focus-within .selection-card__drawer-shell,
.selection-card.is-expanded .selection-card__drawer-shell {
  max-height: 360px;
  opacity: 1;
  pointer-events: auto;
  transform: translateY(0);
}

.selection-card__drawer {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-top: none;
  border-radius: 0 0 12px 12px;
  box-shadow: 0 10px 25px rgba(15, 23, 42, 0.12);
  padding: 12px 16px;
  animation: drawerFadeIn 0.15s ease;
}

@keyframes drawerFadeIn {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}

.selection-card__drawer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 6px;
  margin-bottom: 6px;
  border-bottom: 1px solid #f1f5f9;
}

.selection-card__drawer-title {
  font-size: 11px;
  font-weight: 700;
  color: #0f172a;
}

.selection-card__fields {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.selection-card__field {
  display: grid;
  grid-template-columns: 60px 1fr 22px;
  gap: 6px;
  align-items: center;
  font-size: 11px;
  line-height: 1.35;
}

.selection-card__field dt {
  margin: 0;
  color: #94a3b8;
  font-weight: 500;
}

.selection-card__field dd {
  margin: 0;
  color: #334155;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.selection-card__field dd.is-empty {
  color: #cbd5e1;
  font-weight: 400;
}

.selection-card__field dd.is-warning {
  color: #dc2626;
  font-weight: 600;
}

.selection-card__copy-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.selection-card__copy-icon:hover:not(:disabled) {
  background: #fef2f2;
  color: #dc2626;
}

.selection-card__copy-icon:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.selection-card:not(.hover-mode) .selection-card__drawer-shell {
  position: static;
  max-height: none;
  opacity: 1;
  pointer-events: auto;
  overflow: visible;
  transform: none;
}

.selection-card:not(.hover-mode) .selection-card__drawer {
  margin-top: 4px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.1);
  padding: 8px 10px 10px;
}

@media (hover: none), (pointer: coarse) {
  .selection-card__body {
    height: auto;
  }
}

@container (max-width: 220px) {
  .selection-card__media-actions {
    flex-direction: column;
  }

  .selection-card__btn {
    width: 100%;
    min-width: 0;
    padding: 3px 2px;
    font-size: 10px;
    line-height: 14px;
    white-space: normal;
  }

  .selection-card__price-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 2px;
  }

  .selection-card__field {
    grid-template-columns: 1fr 22px;
    align-items: start;
  }

  .selection-card__field dt {
    grid-column: 1;
    grid-row: 1;
  }

  .selection-card__field dd {
    grid-column: 1 / -1;
    grid-row: 2;
    white-space: normal;
  }

  .selection-card__copy-icon {
    grid-column: 2;
    grid-row: 1;
  }
}
</style>
