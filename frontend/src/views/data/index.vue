<template>
  <div class="dashboard">
    <!-- 顶部工具栏 -->
    <div class="dashboard-toolbar">
      <h2 class="page-title">核心看板</h2>
      <div class="toolbar-actions">
        <n-button type="primary" size="small" @click="$router.push('/data/orders')">
          查看完整明细
        </n-button>
      </div>
    </div>

    <!-- 主指标卡片 -->
    <n-spin :show="loading">
      <div class="metric-cards">
        <div class="metric-card">
          <div class="metric-icon orders">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
              <path d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z"/>
            </svg>
          </div>
          <div class="metric-body">
            <div class="metric-label">总订单数</div>
            <div class="metric-value">{{ metrics.totalOrders ?? 0 }}</div>
            <div class="metric-trend up">较上周 +12.5%</div>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-icon amount">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
              <line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 000 7h5a3.5 3.5 0 010 7H6"/>
            </svg>
          </div>
          <div class="metric-body">
            <div class="metric-label">订单总额</div>
            <div class="metric-value">¥{{ metrics.totalAmount || '0.00' }}</div>
            <div class="metric-trend up">较上周 +8.3%</div>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-icon fee">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
              <circle cx="12" cy="12" r="10"/><path d="M16 8h-4a2 2 0 000 4h2a2 2 0 010 4h-5"/>
            </svg>
          </div>
          <div class="metric-body">
            <div class="metric-label">服务费净收</div>
            <div class="metric-value">¥{{ metrics.serviceFee || '0.00' }}</div>
            <div class="metric-trend up">较上周 +5.7%</div>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-icon profit">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22">
              <polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/><polyline points="16 7 22 7 22 13"/>
            </svg>
          </div>
          <div class="metric-body">
            <div class="metric-label">毛利</div>
            <div class="metric-value">¥{{ metrics.grossProfit || '0.00' }}</div>
            <div class="metric-trend down">较上周 -2.1%</div>
          </div>
        </div>
      </div>

      <!-- 业绩分拆标签组 -->
      <div class="breakdown-section">
        <h3 class="section-title">收入分拆</h3>
        <div class="breakdown-tags">
          <div class="breakdown-item">
            <span class="breakdown-label">招商+渠道提成</span>
            <span class="breakdown-value accent">¥{{ metrics.commission || '0.00' }}</span>
          </div>
          <div class="breakdown-item">
            <span class="breakdown-label">服务费收入</span>
            <span class="breakdown-value">¥{{ metrics.serviceFeeIncome || '0.00' }}</span>
          </div>
          <div class="breakdown-item">
            <span class="breakdown-label">技术服务费</span>
            <span class="breakdown-value">¥{{ metrics.techServiceFee || '0.00' }}</span>
          </div>
          <div class="breakdown-item">
            <span class="breakdown-label">达人分佣</span>
            <span class="breakdown-value">¥{{ metrics.talentCommission || '0.00' }}</span>
          </div>
          <div class="breakdown-item">
            <span class="breakdown-label">招商提成</span>
            <span class="breakdown-value">¥{{ metrics.bizCommission || '0.00' }}</span>
          </div>
          <div class="breakdown-item">
            <span class="breakdown-label">渠道提成</span>
            <span class="breakdown-value">¥{{ metrics.channelCommission || '0.00' }}</span>
          </div>
        </div>
      </div>

      <!-- 快速入口 -->
      <div class="quick-links">
        <n-card size="small" class="quick-link-card" @click="$router.push('/data/orders')">
          <div class="quick-link-content">
            <span class="quick-link-icon">&#128202;</span>
            <span class="quick-link-text">订单明细管理</span>
            <span class="quick-link-arrow">&#8594;</span>
          </div>
        </n-card>
      </div>
    </n-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import { getMetrics } from '../../api/data'

const message = useMessage()
const loading = ref(false)
const metrics = ref<any>({})

const loadMetrics = async () => {
  loading.value = true
  try {
    const res = await getMetrics()
    metrics.value = res?.data || res || {}
  } catch (error: any) {
    message.warning(error?.message || '获取指标异常')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadMetrics()
})
</script>

<style scoped>
.dashboard {
  max-width: 1200px;
}

/* ---- 工具栏 ---- */
.dashboard-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--spacing-lg);
}

.page-title {
  font-size: var(--text-xl);
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
}

/* ---- 主指标卡片 ---- */
.metric-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-lg);
}

.metric-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 20px;
  display: flex;
  align-items: flex-start;
  gap: 16px;
  box-shadow: var(--shadow-card);
  transition: box-shadow var(--transition-normal), transform var(--transition-normal);
}

.metric-card:hover {
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}

.metric-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.metric-icon.orders {
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.metric-icon.amount {
  background: var(--color-info-light);
  color: var(--color-info);
}

.metric-icon.fee {
  background: var(--color-success-light);
  color: var(--color-success);
}

.metric-icon.profit {
  background: var(--color-warning-light);
  color: var(--color-warning);
}

.metric-body {
  flex: 1;
  min-width: 0;
}

.metric-label {
  font-size: var(--text-sm);
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.metric-value {
  font-size: var(--text-2xl);
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 6px;
  line-height: 1.2;
}

.metric-trend {
  font-size: var(--text-xs);
  font-weight: 500;
}

.metric-trend.up {
  color: var(--color-success);
}

.metric-trend.down {
  color: var(--color-danger);
}

/* ---- 分拆标签 ---- */
.breakdown-section {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 20px 24px;
  box-shadow: var(--shadow-card);
  margin-bottom: var(--spacing-md);
}

.section-title {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 16px;
}

.breakdown-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.breakdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: var(--bg-page);
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-color);
}

.breakdown-label {
  font-size: var(--text-sm);
  color: var(--text-secondary);
}

.breakdown-value {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-primary);
}

.breakdown-value.accent {
  color: var(--color-primary);
}

/* ---- 快速入口 ---- */
.quick-links {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--spacing-md);
}

.quick-link-card {
  cursor: pointer;
  border-radius: var(--radius-md);
  transition: box-shadow var(--transition-normal);
}

.quick-link-card:hover {
  box-shadow: var(--shadow-card-hover);
}

.quick-link-content {
  display: flex;
  align-items: center;
  gap: 12px;
}

.quick-link-icon {
  font-size: 20px;
}

.quick-link-text {
  flex: 1;
  font-size: var(--text-base);
  font-weight: 500;
  color: var(--text-primary);
}

.quick-link-arrow {
  color: var(--text-muted);
  font-size: 16px;
}
</style>
