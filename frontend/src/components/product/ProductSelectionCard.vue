<!--
  ProductSelectionCard - 商品库选品卡片（V2 优化版）

  用途：在商品库页面以紧凑卡片展示商品核心信息；桌面端 hover 时弹出下拉抽屉，
       移动端点击卡片展开 / 收起，展示低频业务字段（招商/寄样/时间/团长/店铺/活动/库存）
       并提供逐字段复制按钮。

  布局：
    - 默认态：252px × 254px 固定尺寸（响应式断点降为 4 列 / 3 列 / 1 列）
    - 顶部图片区（aspect-ratio 1:1）+ 底部标题+价格
    - 鼠标悬浮：从卡片底部展开一个绝对定位的字段抽屉（z-index 30），
      覆盖下方一排卡片，避免整行布局跳动

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
    ref="cardRef"
    class="selection-card"
    :class="{ 'is-expanded': expanded, 'hover-mode': supportsHover, 'opens-up': drawerDirection === 'up' }"
    data-testid="product-selection-card"
    @mouseenter="handleMouseEnter"
    @mouseleave="handleMouseLeave"
    @focusin="updateDrawerDirection"
  >
    <div class="selection-card__body" @click="handleCardBodyClick">
      <div class="selection-card__media">
        <img
          v-if="imageVisible"
          :src="card.imageUrl"
          :alt="card.productName"
          class="selection-card__img"
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
            class="selection-card__btn selection-card__btn--primary"
            data-testid="product-quick-sample"
            @click.stop="$emit('quickSample', card.raw)"
          >
            快速寄样
          </button>
        </div>
      </div>

      <div class="selection-card__footer">
        <h3 class="selection-card__title" :title="card.productName">{{ card.productName }}</h3>
        <div class="selection-card__price-row">
          <span class="selection-card__price">{{ card.livePrice }}</span>
          <span class="selection-card__commission">佣 {{ card.commissionRate }}</span>
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
            <span class="selection-card__drawer-title">商品信息</span>
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
const cardRef = ref<HTMLElement | null>(null)
const imageVisible = ref(Boolean(props.card.imageUrl))
/**
 * 设备能力：mount 时一次性探测，之后不再变动。
 * - hover-mode=true：桌面/支持 hover 的环境，drawer 由 hoverActive 控制；
 * - hover-mode=false：触屏/无 hover 设备，drawer 由 expanded（点击）控制。
 *
 * 注意：探测条件是"明确不支持 hover"，而不是"必须同时满足 hover:hover + pointer:fine"。
 * 之前的 `(hover: hover) and (pointer: fine)` 在触屏笔记本、特殊 webview、容器化部署等
 * 真实环境中可能误判为 false，导致桌面端 hover 抽屉永不展开。
 */
const supportsHover = ref(true)
/** 桌面/支持 hover 设备的 hover 状态，与 expanded 互斥使用。 */
const hoverActive = ref(false)
/** 触屏设备的点击展开态。 */
const expanded = ref(false)
const drawerDirection = ref<'down' | 'up'>('down')
let closeTimer: number | undefined

/**
 * 抽屉实际可见性：按设备能力路由到对应状态。
 * 这样 NCollapseTransition 的 show 单一驱动，模板里不写双分支。
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

const updateDrawerDirection = () => {
  if (!supportsHover.value || typeof window === 'undefined') {
    drawerDirection.value = 'down'
    return
  }

  const card = cardRef.value
  const drawer = card?.querySelector<HTMLElement>('.selection-card__drawer-shell')
  if (!card || !drawer) {
    drawerDirection.value = 'down'
    return
  }

  const cardRect = card.getBoundingClientRect()
  const drawerHeight = drawer.getBoundingClientRect().height || 230
  const gap = 4
  const spaceBelow = window.innerHeight - cardRect.bottom
  const spaceAbove = cardRect.top
  drawerDirection.value = spaceBelow < drawerHeight + gap && spaceAbove > drawerHeight + gap ? 'up' : 'down'
}

const handleMouseEnter = () => {
  if (!supportsHover.value) {
    // 触屏分支保留旧逻辑：mouseenter 时直接展开
    clearCloseTimer()
    expanded.value = true
    return
  }
  // 桌面分支：直接翻状态，不走 closeTimer
  clearCloseTimer()
  updateDrawerDirection()
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
  // 桌面分支：直接翻状态
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

/**
 * 抽屉字段列表
 * 顺序：招商 / 寄样要求 / 推广时间 / 团长 / 店铺 / 活动 / 库存
 * - label: 显示名
 * - value: 抽屉中显示的文本
 * - copyText: 复制按钮写入剪贴板的文本（空时按钮 disabled）
 * - warning: 是否用 warning 样式（库存告警）
 */
const infoFields = computed(() => [
  {
    key: 'recruiter',
    label: '招商',
    value: props.card.recruiterName,
    copyText: props.card.recruiterName
  },
  {
    key: 'sample',
    label: '寄样要求',
    value: props.card.sampleRequirement,
    copyText: props.card.sampleRequirement
  },
  {
    key: 'time',
    label: '推广时间',
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
   - 固定 252×254（响应式断点由父级 grid 控制列数）
   - 抽屉绝对定位覆盖下方卡片（不影响布局）
   ============================================================ */
.selection-card {
  position: relative;
  width: 252px;
  height: 254px;
  container-type: inline-size;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
  overflow: visible;
  cursor: pointer;
  transition: box-shadow 0.18s ease, transform 0.18s ease;
}

.selection-card:hover,
.selection-card.is-expanded {
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.14);
  transform: translateY(-1px);
}

/* ============================================================
   默认态（254 高度内）：图片 + 标题 + 价格
   ============================================================ */
.selection-card__body {
  width: 100%;
  height: 100%;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
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
  top: 6px;
  left: 6px;
  z-index: 3;
  padding: 1px 8px;
  border-radius: 999px;
  background: rgba(220, 38, 38, 0.92);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
}

.selection-card__stock-alert {
  position: absolute;
  top: 6px;
  right: 6px;
  z-index: 3;
  padding: 1px 6px;
  border-radius: 4px;
  background: #f97316;
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  line-height: 18px;
}

.selection-card__shop-tag {
  position: absolute;
  bottom: 38px;
  left: 6px;
  z-index: 3;
  max-width: calc(100% - 12px);
  padding: 1px 8px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.72);
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.selection-card__ads-tag {
  position: absolute;
  top: 28px;
  right: 6px;
  z-index: 3;
  padding: 1px 6px;
  border-radius: 4px;
  background: #f97316;
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  line-height: 16px;
}

.selection-card__media-actions {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 3;
  display: flex;
  gap: 4px;
  padding: 6px;
  background: linear-gradient(180deg, transparent, rgba(15, 23, 42, 0.55));
}

.selection-card__btn {
  flex: 1;
  border: none;
  border-radius: 6px;
  padding: 4px 4px;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.15s ease;
  line-height: 18px;
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

.selection-card__footer {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 6px 8px 8px;
  min-height: 0;
}

.selection-card__title {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.35;
  color: #0f172a;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-all;
}

.selection-card__price-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
}

.selection-card__price {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.selection-card__commission {
  font-size: 11px;
  color: #dc2626;
  font-weight: 600;
  white-space: nowrap;
}

/* ============================================================
   Hover 抽屉（桌面端绝对定位覆盖下方，z-index 30）

   桌面端（hover-mode）：
   - NCollapseTransition 的 show 在 hover-mode 下保持 true（DOM 常驻），
     避免 JS hoverActive 链路异常时抽屉被折叠掉。
   - 抽屉可见性由 CSS 单独控制：默认 opacity 0 + pointer-events: none，
     父 :hover / :focus-within 时切到 opacity 1 + pointer-events: auto。
   - 即便 JS 链路失效（hoverActive 翻不过 true），纯 CSS :hover 也能兜底展开。
   ============================================================ */
.selection-card__drawer-shell {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  width: 100%;
  z-index: 30;
  border-radius: 8px;
}

.selection-card.hover-mode .selection-card__drawer-shell {
  opacity: 0;
  pointer-events: none;
  transform: translateY(-4px);
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.selection-card.hover-mode.opens-up .selection-card__drawer-shell {
  top: auto;
  bottom: calc(100% + 4px);
  transform: translateY(4px);
}

.selection-card.hover-mode:hover .selection-card__drawer-shell,
.selection-card.hover-mode:focus-within .selection-card__drawer-shell {
  opacity: 1;
  pointer-events: auto;
  transform: translateY(0);
}

.selection-card__drawer {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.18);
  border: 1px solid #e2e8f0;
  padding: 8px 10px 10px;
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
  font-size: 12px;
  font-weight: 700;
  color: #0f172a;
}

.selection-card__fields {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.selection-card__field {
  display: grid;
  grid-template-columns: 68px 1fr 22px;
  gap: 6px;
  align-items: center;
  font-size: 11px;
  line-height: 1.4;
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

@media (hover: none), (pointer: coarse) {
  .selection-card {
    width: 252px;
    height: auto;
  }

  .selection-card__body {
    height: 254px;
  }

  .selection-card__drawer-shell {
    position: static;
    width: 100%;
    margin-top: 4px;
  }

  .selection-card__drawer {
    box-shadow: 0 6px 16px rgba(15, 23, 42, 0.1);
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
