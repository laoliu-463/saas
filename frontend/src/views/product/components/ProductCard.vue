<template>
  <div
    class="product-card-shell"
    data-testid="product-card"
    :class="{ expanded }"
    @mouseenter="$emit('toggle', product.productId)"
    @mouseleave="$emit('toggle', null)"
  >
    <div class="product-card" @click="$emit('detail', product)" style="cursor: pointer;">
      <div class="card-main">
        <div class="card-image">
          <img :src="product.cover || undefined" :alt="product.title || '商品图片'" @error="handleImageError" />
          <div v-if="!product.cover" class="card-image-fallback">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="32" height="32">
              <rect x="3" y="3" width="18" height="18" rx="2" />
              <circle cx="8.5" cy="8.5" r="1.5" />
              <path d="M21 15l-5-5L5 21" />
            </svg>
          </div>
          <span class="card-badge" :class="statusBadgeClass(product.bizStatus)">
            {{ getBusinessStatusLabel(product) }}
          </span>
        </div>

        <div class="card-body">
          <div class="card-header">
            <h4 class="card-title" :title="product.title || '-'">{{ product.title || '-' }}</h4>
            <p class="card-subtitle">{{ product.shopName || '未识别店铺' }}</p>
          </div>

          <div class="card-tags">
            <n-tag v-for="tag in cardTags(product)" :key="tag.text" :type="tag.type" size="small" bordered>
              {{ tag.text }}
            </n-tag>
            <n-tag
              v-if="product.latestDecisionLabel"
              :type="decisionTagType(product.latestDecisionLevel)"
              size="small"
              round
            >
              {{ product.latestDecisionLabel }}
            </n-tag>
          </div>

          <div class="card-metrics">
            <div class="metric-item">
              <span class="metric-label">售价</span>
              <span class="metric-val">{{ product.priceText || '-' }}</span>
            </div>
            <div class="metric-item">
              <span class="metric-label">佣金率</span>
              <span class="metric-val primary">{{ product.activityCosRatioText || '-' }}</span>
            </div>
            <div class="metric-item">
              <span class="metric-label">预估服务费</span>
              <span class="metric-val accent">¥{{ product.estimatedServiceFee || '0.00' }}</span>
            </div>
            <div class="metric-item">
              <span class="metric-label">近30天销量</span>
              <span class="metric-val">{{ formatCardSales(product) }}</span>
            </div>
            <div v-if="product.categoryName" class="metric-item">
              <span class="metric-label">类目</span>
              <span class="metric-val">{{ product.categoryName }}</span>
            </div>
          </div>

          <div v-if="hasAuditSummary(product)" class="card-audit-summary">
            <div v-if="getAuditSellingPoints(product).length" class="audit-selling-points">
              <n-tag
                v-for="point in getAuditSellingPoints(product).slice(0, 2)"
                :key="point"
                size="small"
                type="info"
                round
                bordered
              >
                {{ point }}
              </n-tag>
            </div>
            <div class="audit-summary-text" :title="getAuditSummaryText(product)">
              {{ getAuditSummaryText(product) }}
            </div>
          </div>

          <div class="card-footer">
            <div class="card-stats">
              <span class="stats-active">{{ product.activityName || '所属活动' }}</span>
              <span class="stats-meta" :title="buildCardProgressSummary(product)">
                {{ buildCardProgressSummary(product) }}
              </span>
            </div>
            <div class="card-actions">
              <n-button
                v-if="product.bizStatus === 'PENDING_AUDIT' && canAssignAuditOwner"
                size="small"
                quaternary
                type="info"
                @click.stop="$emit('assignAuditOwner', product)"
              >
                分配审核人
              </n-button>
              <n-button
                v-if="product.bizStatus === 'PENDING_AUDIT' && canAudit"
                size="small"
                quaternary
                type="warning"
                @click.stop="$emit('audit', product)"
              >
                审核
              </n-button>
              <n-button
                v-if="product.selectedToLibrary && ['APPROVED', 'BOUND', 'ASSIGNED'].includes(product.bizStatus) && canAssign"
                size="small"
                quaternary
                type="info"
                @click.stop="$emit('assign', product)"
              >
                {{ product.assigneeName || product.bizStatus === 'ASSIGNED' ? '重新分配' : '分配招商' }}
              </n-button>
              <n-tag
                v-if="product.selectedToLibrary"
                size="small"
                type="success"
                bordered
              >
                已入商品库
              </n-tag>
              <n-button size="small" quaternary data-testid="product-detail-button" @click.stop="$emit('detail', product)">详情</n-button>
            </div>
          </div>
        </div>
      </div>

      <div class="quick-view-panel" @click.stop>
        <div class="qv-header">
          <div class="qv-title">业务快照</div>
          <n-tag size="small" :type="statusBadgeClass(product.bizStatus)">{{ getStatusLabel(product.bizStatus) }}</n-tag>
        </div>
        <div class="qv-content">
          <div class="qv-section">
            <div class="section-label">经营表现 (近30天)</div>
            <n-grid :cols="2" :x-gap="12">
              <n-gi>
                <div class="qv-stat">
                  <div class="qv-stat-label">成交数</div>
                  <div class="qv-stat-val">{{ product.sales30d || 0 }}</div>
                </div>
              </n-gi>
              <n-gi>
                <div class="qv-stat">
                  <div class="qv-stat-label">GMV</div>
                  <div class="qv-stat-val">¥{{ product.gmv30d || '0.00' }}</div>
                </div>
              </n-gi>
            </n-grid>
          </div>

          <div class="qv-section">
            <div class="section-label">业务推进</div>
            <div class="qv-steps">
              <div class="qv-step" :class="{ active: ['PENDING_AUDIT', 'APPROVED', 'ASSIGNED', 'LINKED'].includes(product.bizStatus) }">同步</div>
              <div class="qv-step-line"></div>
              <div class="qv-step" :class="{ active: ['APPROVED', 'ASSIGNED', 'LINKED'].includes(product.bizStatus) }">初筛</div>
              <div class="qv-step-line"></div>
              <div class="qv-step" :class="{ active: ['ASSIGNED', 'LINKED'].includes(product.bizStatus) }">分配</div>
              <div class="qv-step-line"></div>
              <div class="qv-step" :class="{ active: ['LINKED'].includes(product.bizStatus) }">就绪</div>
            </div>
            <div class="qv-summary">
              <div class="qv-summary-row">
                <span class="qv-summary-label">当前负责人</span>
                <span class="qv-summary-value">{{ product.assigneeName || '未分配' }}</span>
              </div>
              <div class="qv-summary-row">
                <span class="qv-summary-label">推进判断</span>
                <span class="qv-summary-value">{{ product.latestDecisionLabel || '暂无判断' }}</span>
              </div>
              <div class="qv-summary-row">
                <span class="qv-summary-label">最近动作</span>
                <span class="qv-summary-value">{{ getRecentActionLabel(product) }}</span>
              </div>
              <div v-if="hasAuditSummary(product)" class="qv-summary-row qv-summary-block">
                <span class="qv-summary-label">审核补充</span>
                <span class="qv-summary-value qv-summary-multiline">{{ getAuditSummaryText(product) }}</span>
              </div>
              <div v-if="hasSampleThreshold(product)" class="qv-summary-row qv-summary-block" style="border-top: 1px dashed var(--border-color-light); padding-top: 6px; margin-top: 4px;">
                <span class="qv-summary-label">寄样门槛</span>
                <span class="qv-summary-value qv-summary-multiline" style="color: var(--color-warning); font-weight: 600;">
                  {{ getSampleThresholdText(product) }}
                </span>
              </div>
            </div>
          </div>

          <div class="qv-section">
            <div class="section-label">快速操作</div>
            <n-space vertical :size="8">
              <n-tag v-if="product.selectedToLibrary" type="success" size="small" round>已入商品库</n-tag>
              <n-button
                v-if="canCopyLink"
                block
                size="small"
                type="primary"
                secondary
                data-testid="product-copy-link"
                @click.stop="$emit('copyLink', product)"
              >
                复制讲解 + 短链
              </n-button>
              <n-button block size="small" @click.stop="$emit('showLogs', product)">
                查看操作日志
              </n-button>
            </n-space>
          </div>
        </div>
        <div class="qv-footer" @click.stop="$emit('detail', product)">
          点击卡片查看全貌详情 →
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { formatSales30d } from '../product-filters'

defineProps<{
  product: any
  expanded: boolean
  canAudit: boolean
  canAssign: boolean
  canAssignAuditOwner?: boolean
  pickMode: boolean
  libraryMode: boolean
  canPutIntoLibrary: boolean
  canCopyLink?: boolean
}>()

defineEmits<{
  toggle: [productId: string | null]
  detail: [product: any]
  audit: [product: any]
  assign: [product: any]
  assignAuditOwner: [product: any]
  putIntoLibrary: [product: any]
  copyLink: [product: any]
  showLogs: [product: any]
}>()

const getStatusLabel = (statusCode?: string) => {
  const map: Record<string, string> = {
    PENDING_AUDIT: '待审核',
    APPROVED: '审核通过',
    REJECTED: '审核拒绝',
    BOUND: '历史已绑定',
    ASSIGNED: '已分配招商',
    LINKED: '已转链',
    FOLLOWING: '已转交达人 CRM'
  }
  return map[statusCode || ''] || statusCode || '未知状态'
}

const statusBadgeClass = (statusCode?: string) => {
  if (statusCode === 'PENDING_AUDIT') return 'warning'
  if (['LINKED', 'FOLLOWING'].includes(statusCode || '')) return 'success'
  if (statusCode === 'REJECTED') return 'error'
  return 'default'
}

const decisionTagType = (level?: string) => {
  if (level === 'MAIN') return 'success'
  if (level === 'SECONDARY') return 'info'
  if (level === 'PAUSE') return 'warning'
  if (level === 'DROP') return 'error'
  return 'default'
}

const formatCardSales = (item: any) => formatSales30d(item)

const getBusinessStatusLabel = (item: any) => {
  if (item.activityExpired) return '活动过期'
  if (Array.isArray(item.alertTags) && item.alertTags.includes('库存不足')) return '库存不足'
  return getStatusLabel(item.bizStatus)
}

const getRecentActionLabel = (item: any) => {
  const map: Record<string, string> = {
    PENDING_AUDIT: '等待商品审核',
    APPROVED: '商品审核通过',
    REJECTED: '商品审核拒绝',
    BOUND: '历史商品已绑定',
    ASSIGNED: '已分配招商',
    LINKED: '推广链接已就绪',
    FOLLOWING: '已转交达人 CRM'
  }
  return map[item?.bizStatus || ''] || '等待推进'
}

const buildCardProgressSummary = (item: any) => {
  const assignee = item?.assigneeName || '未分配负责人'
  return `${assignee} · ${getRecentActionLabel(item)}`
}

const getAuditSupplement = (item: any) => item?.auditSupplement || {}

const getPromotionMaterialPack = (item: any) => item?.promotionMaterialPack || {}

const getAuditSellingPoints = (item: any) => {
  const supplementPoints = getAuditSupplement(item)?.sellingPoints
  if (Array.isArray(supplementPoints) && supplementPoints.length) return supplementPoints
  const packPoints = getPromotionMaterialPack(item)?.sellingPoints
  return Array.isArray(packPoints) ? packPoints.filter(Boolean) : []
}

const getAuditSummaryText = (item: any) => {
  const supplement = getAuditSupplement(item)
  const summaryParts = [
    supplement?.exclusivePriceRemark,
    supplement?.shippingInfo,
    supplement?.rewardRemark,
    supplement?.participationRequirements,
    supplement?.campaignTimeRemark
  ].map((value) => String(value || '').trim()).filter(Boolean)

  if (summaryParts.length) {
    return summaryParts[0]
  }

  const pack = getPromotionMaterialPack(item)
  const fallback = [pack?.outreachScript, pack?.shortVideoScript]
    .map((value) => String(value || '').trim())
    .find(Boolean)

  return fallback || ''
}

const hasAuditSummary = (item: any) => Boolean(getAuditSellingPoints(item).length || getAuditSummaryText(item))

const hasSampleThreshold = (item: any) => {
  const supplement = getAuditSupplement(item)
  return supplement?.sampleThresholdSales !== undefined || supplement?.sampleThresholdLevel !== undefined || supplement?.sampleThresholdRemark
}

const getSampleThresholdText = (item: any) => {
  const supplement = getAuditSupplement(item)
  const parts = []
  if (supplement?.sampleThresholdSales !== undefined) parts.push(`30天销售额≥${supplement.sampleThresholdSales}`)
  if (supplement?.sampleThresholdLevel !== undefined) parts.push(`达人等级≥LV${supplement.sampleThresholdLevel}`)
  if (supplement?.sampleThresholdRemark) parts.push(supplement.sampleThresholdRemark)
  return parts.join('；') || '未设置门槛'
}

const cardTags = (item: any) => {
  const tags: Array<{ text: string; type: 'error' | 'warning' | 'info' | 'success' }> = []
  if (!item.promotion?.link && !item.promotionLink && (item.promotion?.status || item.promotionLinkStatus) === 'FAILED') {
    tags.push({ text: '链接生成失败', type: 'error' })
  }
  if (!item.hasMaterial) tags.push({ text: '缺少话术素材', type: 'warning' })
  if (!hasSampleThreshold(item) && !item.hasSampleRule) tags.push({ text: '暂无寄样要求', type: 'warning' })
  if (!item.assigneeName) tags.push({ text: '待分配负责人', type: 'error' })
  if (item.selectedToLibrary) tags.push({ text: '商品库可见', type: 'success' })
  return tags
}

const handleImageError = (event: Event) => {
  ;(event.target as HTMLImageElement).style.display = 'none'
}
</script>

<style scoped>
.product-card-shell {
  min-width: 0;
}

.product-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: var(--shadow-card);
  transition: box-shadow 0.3s ease, transform 0.3s ease;
  position: relative;
  display: flex;
  flex-direction: column;
}

.product-card:hover {
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-6px);
}

.card-main {
  min-width: 0;
}

.card-image {
  position: relative;
  width: 100%;
  padding-top: 56.25%;
  background: linear-gradient(135deg, var(--bg-sidebar) 0%, var(--border-color-light) 100%);
  overflow: hidden;
}

.card-image img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.5s ease;
}

.product-card:hover .card-image img {
  transform: scale(1.05);
}

.card-image-fallback {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
}

.card-badge {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 3px 10px;
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--text-inverse);
  z-index: 2;
}

.card-body {
  padding: var(--spacing-lg);
}

.card-header {
  margin-bottom: 10px;
}

.card-title {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 4px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-subtitle {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--text-secondary);
}

.card-tags {
  margin-bottom: 10px;
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  min-height: 28px;
}

.card-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  padding: 10px 0;
  border-top: 1px solid var(--border-color-light);
  border-bottom: 1px solid var(--border-color-light);
  margin-bottom: 10px;
}

.metric-item {
  text-align: center;
}

.metric-label {
  display: block;
  font-size: var(--text-xs);
  color: var(--text-muted);
  margin-bottom: 2px;
}

.metric-val {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-primary);
}

.metric-val.primary {
  color: var(--color-primary);
}

.metric-val.accent {
  color: var(--color-warning);
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-audit-summary {
  margin-bottom: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  background: var(--bg-sidebar);
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-sm);
}

.audit-selling-points {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.audit-summary-text {
  color: var(--text-secondary);
  font-size: var(--text-xs);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-stats {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: var(--text-xs);
  color: var(--text-secondary);
  min-width: 0;
}

.stats-active {
  color: var(--color-primary);
}

.stats-meta {
  color: var(--text-tertiary);
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-actions {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

/* Quick View Panel */
.quick-view-panel {
  background: var(--bg-card);
  width: 100%;
  display: none;
  border-top: 1px solid var(--border-color-light);
}

.product-card-shell.expanded .quick-view-panel {
  display: flex;
  flex-direction: column;
}

.qv-header {
  padding: var(--spacing-sm) var(--spacing-lg);
  background: var(--bg-sidebar);
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--border-color-light);
}

.qv-title {
  font-weight: 600;
  color: var(--text-primary);
}

.qv-content {
  padding: var(--spacing-lg);
  flex: 1;
}

.qv-section {
  margin-bottom: var(--spacing-lg);
}

.qv-section:last-child {
  margin-bottom: 0;
}

.section-label {
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--text-tertiary);
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.qv-stat {
  padding: 8px;
  background: var(--bg-sidebar);
  border-radius: var(--radius-sm);
  text-align: center;
}

.qv-stat-label {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

.qv-stat-val {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-primary);
}

.qv-steps {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.qv-step {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  background: var(--bg-sidebar);
}

.qv-step.active {
  color: var(--text-inverse);
  background: var(--color-info);
}

.qv-step-line {
  flex: 1;
  height: 1px;
  background: var(--border-color-light);
  margin: 0 4px;
}

.qv-summary {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: var(--bg-sidebar);
  border: 1px solid var(--border-color-light);
}

.qv-summary-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: var(--text-xs);
}

.qv-summary-label {
  color: var(--text-secondary);
}

.qv-summary-value {
  color: var(--text-primary);
  text-align: right;
}

.qv-summary-block {
  align-items: flex-start;
}

.qv-summary-multiline {
  white-space: normal;
  line-height: 1.6;
  max-width: 180px;
}

.qv-footer {
  padding: 10px;
  text-align: center;
  font-size: var(--text-xs);
  color: var(--color-info);
  background: var(--color-info-light);
  cursor: pointer;
  border-top: 1px solid var(--border-color-light);
}

.qv-footer:hover {
  background: var(--color-primary-light);
}


</style>
