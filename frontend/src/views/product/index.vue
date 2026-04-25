<template>
  <div class="product-page">
    <n-alert v-if="resolvedActivityId" type="info" style="margin-bottom: 16px;">
      当前正在查看活动（ID: {{ resolvedActivityId }}）下的商品
      <template #action>
        <n-button size="small" @click="$router.push('/product/activity')">返回活动列表</n-button>
      </template>
    </n-alert>

    <div class="product-toolbar">
      <n-space wrap :size="12">
        <n-input
          v-model:value="productInfo"
          placeholder="商品名称或商品ID"
          clearable
          style="width: 220px"
          @keydown.enter="refreshProducts"
        />
        <n-select
          v-model:value="status"
          :options="statusOptions"
          placeholder="业务状态"
          clearable
          style="width: 170px"
        />
        <n-button type="primary" size="small" :loading="loading" @click="refreshProducts">
          查询商品
        </n-button>
        <n-button
          v-if="Boolean(route.params.activityId)"
          type="info"
          size="small"
          secondary
          :loading="syncing"
          @click="handleSync"
        >
          同步活动商品
        </n-button>
      </n-space>
    </div>

    <n-spin :show="loading">
      <div v-if="products.length" class="product-grid">
        <div v-for="item in products" :key="item.productId" class="product-card">
          <div class="card-image">
            <img
              :src="item.cover || undefined"
              :alt="item.title || '商品图片'"
              @error="handleImageError"
            />
            <div v-if="!item.cover" class="card-image-fallback">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="32" height="32">
                <rect x="3" y="3" width="18" height="18" rx="2" />
                <circle cx="8.5" cy="8.5" r="1.5" />
                <path d="M21 15l-5-5L5 21" />
              </svg>
            </div>
            <span class="card-badge" :class="statusBadgeClass(item.bizStatus)">
              {{ getStatusLabel(item.bizStatus) }}
            </span>
          </div>

          <div class="card-body">
            <h4 class="card-title" :title="item.title || '-'">{{ item.title || '-' }}</h4>
            <div class="card-meta">
              <span class="card-id">ID: {{ item.productId }}</span>
              <span class="card-shop">{{ item.shopName || '-' }}</span>
            </div>

            <div class="card-metrics">
              <div class="metric-item">
                <span class="metric-label">售价</span>
                <span class="metric-val">{{ item.priceText || '-' }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">佣金</span>
                <span class="metric-val primary">{{ item.activityCosRatioText || '-' }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">负责人</span>
                <span class="metric-val metric-assignee">{{ item.assigneeId || '未分配' }}</span>
              </div>
            </div>

            <div class="card-footer">
              <div class="card-stats">
                <span v-if="item.boundActivityId" class="stats-active">已绑活动</span>
                <span v-else>未绑活动</span>
              </div>
              <div class="card-actions">
                <n-button
                  v-if="item.bizStatus === 'PENDING_AUDIT' && canDo('audit')"
                  size="tiny"
                  quaternary
                  type="warning"
                  @click="openDialog('audit', item)"
                >
                  审核
                </n-button>
                <n-button
                  v-if="item.bizStatus === 'APPROVED' && canDo('bind')"
                  size="tiny"
                  quaternary
                  type="primary"
                  @click="openDialog('bind', item)"
                >
                  绑定活动
                </n-button>
                <n-button
                  v-if="item.bizStatus === 'BOUND' && canDo('assign')"
                  size="tiny"
                  quaternary
                  type="info"
                  @click="openDialog('assign', item)"
                >
                  分配招商
                </n-button>
                <n-button
                  v-if="item.bizStatus === 'ASSIGNED' && canDo('convert')"
                  size="tiny"
                  quaternary
                  type="success"
                  @click="openDialog('convert', item)"
                >
                  生成转链
                </n-button>
                <n-button
                  v-if="['LINKED', 'FOLLOWING'].includes(item.bizStatus) && canDo('follow')"
                  size="tiny"
                  quaternary
                  type="success"
                  @click="openDialog('follow', item)"
                >
                  {{ item.bizStatus === 'LINKED' ? '跟进达人' : '追加跟进' }}
                </n-button>
                <n-button size="tiny" quaternary type="default" @click="openDetail(item)">详情</n-button>
                <n-dropdown trigger="click" :options="moreActionsOptions()" @select="(key: string) => handleMoreAction(key, item)">
                  <n-button size="tiny" quaternary>更多</n-button>
                </n-dropdown>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-else-if="!loading" class="empty-state">
        <div class="empty-icon">&#128230;</div>
        <p>暂无商品数据</p>
      </div>
    </n-spin>

    <div class="load-more" v-if="products.length">
      <n-button v-if="hasMore" :loading="loadingMore" @click="loadMore" secondary>加载更多</n-button>
      <span v-else class="no-more">已加载全部</span>
    </div>

    <ProductDetail
      v-model:show="showDetail"
      :product-id="currentRow?.productId"
      :activity-id="resolvedActivityId"
      :refresh-key="detailRefreshKey"
      @action="handleDetailAction"
    />
    <ProductAuditDialog
      v-model:show="dialogs.audit"
      :product-id="currentRow?.productId"
      :activity-id="resolvedActivityId"
      @success="(payload: any) => handleActionSuccess('audit', payload)"
    />
    <ProductBindActivityDialog
      v-model:show="dialogs.bind"
      :product-id="currentRow?.productId"
      :activity-id="resolvedActivityId"
      @success="(payload: any) => handleActionSuccess('bind', payload)"
    />
    <ProductAssignDialog
      v-model:show="dialogs.assign"
      :product-id="currentRow?.productId"
      :activity-id="resolvedActivityId"
      @success="(payload: any) => handleActionSuccess('assign', payload)"
    />
    <ProductConvertLinkDialog
      v-model:show="dialogs.convert"
      :product-id="currentRow?.productId"
      :activity-id="resolvedActivityId"
      @success="(payload: any) => handleActionSuccess('convert', payload)"
    />
    <ProductFollowDialog
      v-model:show="dialogs.follow"
      :product-id="currentRow?.productId"
      :activity-id="resolvedActivityId"
      @success="(payload: any) => handleActionSuccess('follow', payload)"
    />
    <ProductOperationLogDrawer
      v-model:show="dialogs.logs"
      :product-id="currentRow?.productId"
      :activity-id="resolvedActivityId"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useMessage } from 'naive-ui';
import { useRoute } from 'vue-router';
import { useAuthStore } from '../../stores/auth';
import { hasAccess } from '../../constants/rbac';
import { getActivityProducts, syncActivityProducts } from '../../api/activityProduct';
import { getColonelActivityPage } from '../../api/activity';

import ProductDetail from './ProductDetail.vue';
import ProductAuditDialog from './components/ProductAuditDialog.vue';
import ProductBindActivityDialog from './components/ProductBindActivityDialog.vue';
import ProductAssignDialog from './components/ProductAssignDialog.vue';
import ProductConvertLinkDialog from './components/ProductConvertLinkDialog.vue';
import ProductFollowDialog from './components/ProductFollowDialog.vue';
import ProductOperationLogDrawer from './components/ProductOperationLogDrawer.vue';

type ProductAction = 'audit' | 'bind' | 'assign' | 'convert' | 'follow';

const message = useMessage();
const route = useRoute();
const authStore = useAuthStore();

const loading = ref(false);
const loadingMore = ref(false);
const syncing = ref(false);
const products = ref<any[]>([]);
const nextCursor = ref('');
const hasMore = ref(false);
const productInfo = ref('');
const status = ref<string | null>(null);
const fallbackActivityId = ref('');
const currentRow = ref<any | null>(null);
const showDetail = ref(false);
const detailRefreshKey = ref(0);

const dialogs = ref({
  audit: false,
  bind: false,
  assign: false,
  convert: false,
  follow: false,
  logs: false
});

const statusOptions = [
  { label: '待审核 (PENDING_AUDIT)', value: 'PENDING_AUDIT' },
  { label: '审核通过 (APPROVED)', value: 'APPROVED' },
  { label: '审核拒绝 (REJECTED)', value: 'REJECTED' },
  { label: '已绑定活动 (BOUND)', value: 'BOUND' },
  { label: '已分配招商 (ASSIGNED)', value: 'ASSIGNED' },
  { label: '已转链 (LINKED)', value: 'LINKED' },
  { label: '达人跟进中 (FOLLOWING)', value: 'FOLLOWING' }
];

const forcedStatusMap: Record<ProductAction, string> = {
  audit: 'APPROVED',
  bind: 'BOUND',
  assign: 'ASSIGNED',
  convert: 'LINKED',
  follow: 'FOLLOWING'
};

const canDo = (action: string) => {
  const roles = authStore.roleCodes;
  if (roles.includes('admin')) return true;
  if (action === 'audit') return hasAccess(roles, ['biz_leader', 'biz_staff']);
  if (action === 'bind' || action === 'assign') return hasAccess(roles, ['biz_leader', 'channel_leader']);
  if (action === 'convert' || action === 'follow') return hasAccess(roles, ['channel_leader', 'channel_staff']);
  return true;
};

const resolvedActivityId = computed(() => String(route.params.activityId || fallbackActivityId.value || ''));

const getStatusLabel = (statusCode?: string) => {
  const map: Record<string, string> = {
    PENDING_AUDIT: '待审核',
    APPROVED: '审核通过',
    REJECTED: '审核拒绝',
    BOUND: '已绑定活动',
    ASSIGNED: '已分配招商',
    LINKED: '已转链',
    FOLLOWING: '达人跟进中'
  };
  return map[statusCode || ''] || statusCode || '未知';
};

const statusBadgeClass = (statusCode?: string) => {
  if (statusCode === 'PENDING_AUDIT') return 'badge-review';
  if (['LINKED', 'FOLLOWING'].includes(statusCode || '')) return 'badge-active';
  if (statusCode === 'REJECTED') return 'badge-ended';
  return 'badge-default';
};

const handleImageError = (event: Event) => {
  (event.target as HTMLImageElement).style.display = 'none';
};

const ensureActivityId = async () => {
  if (route.params.activityId) {
    fallbackActivityId.value = '';
    return String(route.params.activityId);
  }
  if (fallbackActivityId.value) {
    return fallbackActivityId.value;
  }
  const res: any = await getColonelActivityPage({
    page: 1,
    pageSize: 1,
    status: 0,
    searchType: 0,
    sortType: 1
  });
  const activity = res?.data?.activityList?.[0];
  if (!activity?.activityId) {
    throw new Error('暂无可用活动');
  }
  fallbackActivityId.value = String(activity.activityId);
  return fallbackActivityId.value;
};

const normalizeItem = (item: any) => ({
  ...item,
  bizStatus: item?.bizStatus || 'PENDING_AUDIT',
  bizStatusLabel: item?.bizStatusLabel || getStatusLabel(item?.bizStatus),
  productId: String(item?.productId ?? '')
});

const fetchProducts = async (reset: boolean) => {
  if (reset) loading.value = true;
  else loadingMore.value = true;

  try {
    const activityId = await ensureActivityId();
    const res: any = await getActivityProducts(activityId, {
      count: 20,
      cursor: reset ? undefined : nextCursor.value,
      productInfo: productInfo.value.trim() || undefined,
      retrieveMode: 1
    });

    const data = res?.data || {};
    let items = Array.isArray(data.items) ? data.items.map(normalizeItem) : [];
    if (status.value) {
      items = items.filter((item: any) => item.bizStatus === status.value);
    }

    products.value = reset ? items : products.value.concat(items);
    nextCursor.value = String(data.nextCursor || '');
    hasMore.value = Boolean(data.hasMore || data.nextCursor);
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '商品查询失败');
    if (reset) {
      products.value = [];
      nextCursor.value = '';
      hasMore.value = false;
    }
  } finally {
    loading.value = false;
    loadingMore.value = false;
  }
};

const refreshProducts = () => {
  fetchProducts(true);
};

const loadMore = () => {
  if (hasMore.value) {
    fetchProducts(false);
  }
};

const handleSync = async () => {
  if (!route.params.activityId) return;
  syncing.value = true;
  try {
    await syncActivityProducts(String(route.params.activityId));
    message.success('活动商品同步成功');
    refreshProducts();
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '同步失败');
  } finally {
    syncing.value = false;
  }
};

const openDetail = (row: any) => {
  currentRow.value = { ...row };
  showDetail.value = true;
};

const openDialog = (type: keyof typeof dialogs.value, row: any) => {
  currentRow.value = { ...row };
  dialogs.value[type] = true;
};

const moreActionsOptions = () => [{ label: '操作日志', key: 'logs' }];

const handleMoreAction = (key: string, row: any) => {
  if (key === 'logs') {
    openDialog('logs', row);
  }
};

const handleDetailAction = (payload: { action: string; row: any }) => {
  openDialog(payload.action as keyof typeof dialogs.value, payload.row);
};

const mergeProductRow = (payload?: any, fallbackAction?: ProductAction) => {
  const row = currentRow.value;
  const productId = String(payload?.productId || row?.productId || '');
  if (!productId) return;

  const merged = normalizeItem({
    ...row,
    ...payload,
    productId,
    bizStatus: payload?.bizStatus || forcedStatusMap[fallbackAction || 'audit'] || row?.bizStatus,
    bizStatusLabel: payload?.bizStatusLabel || getStatusLabel(payload?.bizStatus || forcedStatusMap[fallbackAction || 'audit'] || row?.bizStatus)
  });

  products.value = products.value.map((item: any) =>
    String(item.productId) === productId ? { ...item, ...merged } : item
  );
  currentRow.value = { ...merged };
};

const handleActionSuccess = (action: ProductAction, payload?: any) => {
  mergeProductRow(payload, action);
  detailRefreshKey.value += 1;
  refreshProducts();
};

onMounted(() => {
  refreshProducts();
});

watch(() => route.params.activityId, () => {
  nextCursor.value = '';
  refreshProducts();
});
</script>

<style scoped>
.product-page {
  max-width: 100%;
}

.product-toolbar {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: 16px 20px;
  margin-bottom: var(--spacing-md);
  box-shadow: var(--shadow-sm);
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--spacing-md);
}

.product-card {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: var(--shadow-card);
  transition: box-shadow var(--transition-normal), transform var(--transition-normal);
}

.product-card:hover {
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}

.card-image {
  position: relative;
  width: 100%;
  padding-top: 56.25%;
  background: linear-gradient(135deg, #f8f9fb 0%, #eef0f4 100%);
  overflow: hidden;
}

.card-image img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
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
  color: #fff;
}

.badge-active {
  background: var(--color-primary);
}

.badge-review {
  background: var(--color-warning);
}

.badge-ended {
  background: var(--color-error);
}

.badge-default {
  background: var(--color-info);
}

.card-body {
  padding: 14px 16px;
}

.card-title {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 6px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  font-size: var(--text-xs);
  color: var(--text-muted);
}

.card-id {
  font-family: var(--font-mono);
}

.card-metrics {
  display: flex;
  gap: 16px;
  padding: 10px 0;
  border-top: 1px solid var(--border-color-light);
  border-bottom: 1px solid var(--border-color-light);
  margin-bottom: 10px;
}

.metric-item {
  flex: 1;
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

.metric-assignee {
  font-size: 12px;
  font-weight: 400;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-stats {
  font-size: var(--text-xs);
  color: var(--text-secondary);
}

.stats-active {
  color: var(--color-primary);
}

.card-actions {
  display: flex;
  gap: 2px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.load-more {
  text-align: center;
  padding: 24px 0;
}

.no-more {
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.empty-state {
  text-align: center;
  padding: 80px 20px;
  color: var(--text-muted);
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-state p {
  margin: 4px 0;
  font-size: var(--text-base);
}
</style>
