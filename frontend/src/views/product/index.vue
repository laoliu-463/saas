<template>
  <div class="product-page">
    <n-alert v-if="resolvedActivityId" type="info" style="margin-bottom: 16px;">
      当前正在查看活动下的商品
      <template #action>
        <n-button size="small" @click="$router.push('/product/activity')">返回活动列表</n-button>
      </template>
    </n-alert>

    <div class="product-toolbar">
      <n-space vertical :size="12">
        <n-space wrap :size="12" align="center">
          <n-select
            v-model:value="selectedProduct"
            :options="productOptions"
            :loading="productOptionsLoading"
            filterable
            remote
            clearable
            placeholder="搜索商品 / 店铺"
            style="width: 280px"
            @search="handleProductSearch"
          />
          <n-select
            v-model:value="filters.category"
            :options="categoryOptions"
            placeholder="商品分类/标签"
            clearable
            style="width: 160px"
          />
          <n-select
            v-model:value="filters.commission"
            :options="commissionOptions"
            placeholder="佣金率区间"
            clearable
            style="width: 140px"
          />
          <n-select
            v-model:value="filters.hasSample"
            :options="yesNoOptions"
            placeholder="是否可寄样"
            clearable
            style="width: 120px"
          />
          <n-select
            v-model:value="filters.assignee"
            :options="assigneeOptions"
            placeholder="招商负责人"
            clearable
            style="width: 160px"
          />
          <n-select
            v-model:value="status"
            :options="statusOptions"
            placeholder="业务状态"
            clearable
            style="width: 160px"
          />
          <n-button type="primary" :loading="loading" @click="refreshProducts">组合查询</n-button>
          <n-button @click="resetFilters">重置</n-button>
        </n-space>
      </n-space>
    </div>

    <n-spin :show="loading">
      <div v-if="products.length" class="product-grid">
        <div v-for="item in products" :key="item.productId" class="product-card">
          <div class="card-image">
            <img :src="item.cover || undefined" :alt="item.title || '商品图片'" @error="handleImageError" />
            <div v-if="!item.cover" class="card-image-fallback">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="32" height="32">
                <rect x="3" y="3" width="18" height="18" rx="2" />
                <circle cx="8.5" cy="8.5" r="1.5" />
                <path d="M21 15l-5-5L5 21" />
              </svg>
            </div>
            <span class="card-badge" :class="statusBadgeClass(item.bizStatus)">
              {{ getBusinessStatusLabel(item) }}
            </span>
          </div>

          <div class="card-body">
            <h4 class="card-title" :title="item.title || '-'">{{ item.title || '-' }}</h4>

            <div class="card-tags">
              <n-tag v-for="tag in cardTags(item)" :key="tag.text" :type="tag.type" size="small" bordered>
                {{ tag.text }}
              </n-tag>
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
                <span class="metric-label">预估服务费</span>
                <span class="metric-val accent">¥{{ item.estimatedServiceFee || '0.00' }}</span>
              </div>
              <div class="metric-item">
                <span class="metric-label">店铺</span>
                <span class="metric-val small">{{ item.shopName || '-' }}</span>
              </div>
            </div>

            <div class="business-strip">
              <div class="business-line">
                <span class="business-label">寄样提示</span>
                <span class="business-value">{{ item.hasSampleRule ? '可查看寄样要求' : '寄样要求待补充' }}</span>
              </div>
              <div class="business-line">
                <span class="business-label">推广链接</span>
                <span class="promotion-value" :class="promotionStatusClass(item.promotion?.status || item.promotionLinkStatus)">
                  {{ item.promotion?.statusLabel || item.promotionLinkStatusLabel || '未生成' }}
                </span>
              </div>
              <div v-if="item.promotionLinkFailReason" class="promotion-reason">
                {{ item.promotionLinkFailReason }}
              </div>
            </div>

            <div class="promotion-strip">
              <n-button
                v-if="canDo('promotion')"
                size="tiny"
                type="primary"
                secondary
                :loading="promotionLoadingIds.has(String(item.productId))"
                @click="copyPromotionLink(item)"
              >
                复制推广链接
              </n-button>
            </div>

            <div class="card-footer">
              <div class="card-stats">
                <span class="stats-active">活动商品</span>
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
                  v-if="['APPROVED', 'BOUND'].includes(item.bizStatus) && canDo('assign')"
                  size="tiny"
                  quaternary
                  type="info"
                  @click="openDialog('assign', item)"
                >
                  分配招商
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
    <ProductAssignDialog
      v-model:show="dialogs.assign"
      :product-id="currentRow?.productId"
      :activity-id="resolvedActivityId"
      @success="(payload: any) => handleActionSuccess('assign', payload)"
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
import { convertActivityProductLink, getActivityProducts } from '../../api/activityProduct';
import { getColonelActivityPage } from '../../api/activity';

import ProductDetail from './ProductDetail.vue';
import ProductAuditDialog from './components/ProductAuditDialog.vue';
import ProductAssignDialog from './components/ProductAssignDialog.vue';
import ProductOperationLogDrawer from './components/ProductOperationLogDrawer.vue';

type ProductAction = 'audit' | 'assign';

const message = useMessage();
const route = useRoute();
const authStore = useAuthStore();

const loading = ref(false);
const loadingMore = ref(false);
const products = ref<any[]>([]);
const nextCursor = ref('');
const hasMore = ref(false);
const promotionLoadingIds = ref<Set<string>>(new Set());
const selectedProduct = ref<string | null>(null);
const productKeyword = ref('');
const productOptions = ref<{ label: string; value: string }[]>([]);
const productOptionsLoading = ref(false);
const status = ref<string | null>(null);
const fallbackActivityId = ref('');
const currentRow = ref<any | null>(null);
const showDetail = ref(false);
const detailRefreshKey = ref(0);

const dialogs = ref({
  audit: false,
  assign: false,
  logs: false
});

const filters = ref({
  category: null as string | null,
  commission: null as string | null,
  hasSample: null as string | null,
  assignee: null as string | null
});

const categoryOptions = [
  { label: '高佣爆款', value: 'high_commission' },
  { label: '支持投流', value: 'traffic' },
  { label: '新品首发', value: 'new' },
  { label: '高客单价', value: 'high_price' }
];

const commissionOptions = [
  { label: '20%以上', value: 'gt20' },
  { label: '10%-20%', value: '10_20' },
  { label: '10%以下', value: 'lt10' }
];

const yesNoOptions = [
  { label: '是', value: '1' },
  { label: '否', value: '0' }
];

const assigneeOptions = [
  { label: '已分配负责人', value: 'assigned' },
  { label: '未分配负责人', value: 'unassigned' }
];

const statusOptions = [
  { label: '待审核', value: 'PENDING_AUDIT' },
  { label: '审核通过', value: 'APPROVED' },
  { label: '审核拒绝', value: 'REJECTED' },
  { label: '历史已绑定', value: 'BOUND' },
  { label: '已分配招商', value: 'ASSIGNED' },
  { label: '已转链', value: 'LINKED' },
  { label: '已转交达人 CRM', value: 'FOLLOWING' }
];

const forcedStatusMap: Record<ProductAction, string> = {
  audit: 'APPROVED',
  assign: 'ASSIGNED'
};

const canDo = (action: string) => {
  const roles = authStore.roleCodes;
  if (roles.includes('admin')) return true;
  if (action === 'audit') return hasAccess(roles, ['biz_leader', 'biz_staff']);
  if (action === 'assign') return hasAccess(roles, ['biz_leader', 'channel_leader']);
  if (action === 'promotion') return hasAccess(roles, ['channel_leader', 'channel_staff']);
  return true;
};

const resolvedActivityId = computed(() => String(route.params.activityId || fallbackActivityId.value || ''));

const getStatusLabel = (statusCode?: string) => {
  const map: Record<string, string> = {
    PENDING_AUDIT: '待审核',
    APPROVED: '审核通过',
    REJECTED: '审核拒绝',
    BOUND: '历史已绑定',
    ASSIGNED: '已分配招商',
    LINKED: '已转链',
    FOLLOWING: '已转交达人 CRM'
  };
  return map[statusCode || ''] || statusCode || '未知';
};

const statusBadgeClass = (statusCode?: string) => {
  if (statusCode === 'PENDING_AUDIT') return 'badge-review';
  if (['LINKED', 'FOLLOWING'].includes(statusCode || '')) return 'badge-active';
  if (statusCode === 'REJECTED') return 'badge-ended';
  return 'badge-default';
};

const getBusinessStatusLabel = (item: any) => {
  if (item.activityExpired) return '活动过期';
  if (Array.isArray(item.alertTags) && item.alertTags.includes('库存不足')) return '库存不足';
  return getStatusLabel(item.bizStatus);
};

const cardTags = (item: any) => {
  const tags: Array<{ text: string; type: 'error' | 'warning' | 'info' | 'success' }> = [];
  if (!item.promotion?.link && !item.promotionLink && (item.promotion?.status || item.promotionLinkStatus) === 'FAILED') {
    tags.push({ text: '链接失败', type: 'error' });
  }
  if (!item.hasMaterial) tags.push({ text: '无话术', type: 'warning' });
  if (!item.hasSampleRule) tags.push({ text: '无寄样要求', type: 'warning' });
  if (!item.assigneeName) tags.push({ text: '未分配负责人', type: 'error' });
  return tags;
};

const promotionStatusClass = (statusCode?: string) => {
  if (statusCode === 'READY') return 'promotion-ready';
  if (statusCode === 'FAILED') return 'promotion-failed';
  return 'promotion-pending';
};

const handleImageError = (event: Event) => {
  (event.target as HTMLImageElement).style.display = 'none';
};

const ensureActivityId = async () => {
  if (route.params.activityId) {
    fallbackActivityId.value = '';
    return String(route.params.activityId);
  }
  if (fallbackActivityId.value) return fallbackActivityId.value;

  const res: any = await getColonelActivityPage({
    page: 1,
    pageSize: 1,
    status: 0,
    searchType: 0,
    sortType: 1
  });
  const activity = res?.data?.activityList?.[0];
  if (!activity?.activityId) throw new Error('暂无可用活动');
  fallbackActivityId.value = String(activity.activityId);
  return fallbackActivityId.value;
};

const normalizeItem = (item: any) => ({
  ...item,
  bizStatus: item?.bizStatus || 'PENDING_AUDIT',
  bizStatusLabel: item?.bizStatusLabel || getStatusLabel(item?.bizStatus),
  productId: String(item?.productId ?? ''),
  sales30d: item?.sales30d ?? item?.sales ?? 0,
  gmv30d: item?.gmv30d ?? '0.00',
  estimatedServiceFee: item?.estimatedServiceFee ?? item?.estimatedServiceFeeAmount ?? '0.00',
  hasMaterial: item?.hasMaterial ?? false,
  hasSampleRule: item?.hasSampleRule ?? true,
  activityExpired: Boolean(item?.activityExpired),
  systemTags: Array.isArray(item?.systemTags) ? item.systemTags : [],
  alertTags: Array.isArray(item?.alertTags) ? item.alertTags : [],
  promotion: item?.promotion || {
    status: item?.promotionLinkStatus || 'PENDING',
    statusLabel: item?.promotionLinkStatusLabel || '未生成',
    link: item?.promotionLink || null,
    generatedAt: item?.promotionLinkGeneratedAt || null,
    expireAt: item?.promotionLinkExpireAt || null,
    failReason: item?.promotionLinkFailReason || null
  }
});

const matchCategory = (item: any, category: string | null) => {
  if (!category) return true;
  const tags = Array.isArray(item.systemTags) ? item.systemTags : [];
  if (category === 'high_commission') return tags.includes('高佣');
  if (category === 'traffic') return tags.includes('抖音商品标');
  if (category === 'new') return (item.sales30d ?? 0) < 100;
  if (category === 'high_price') return parsePrice(item.priceText) >= 300;
  return true;
};

const matchCommission = (item: any, commission: string | null) => {
  if (!commission) return true;
  const rate = parsePercent(item.activityCosRatioText);
  if (commission === 'gt20') return rate >= 20;
  if (commission === '10_20') return rate >= 10 && rate < 20;
  if (commission === 'lt10') return rate < 10;
  return true;
};

const matchSample = (item: any, hasSample: string | null) => {
  if (!hasSample) return true;
  return hasSample === '1' ? Boolean(item.hasSampleRule) : !item.hasSampleRule;
};

const matchAssignee = (item: any, assignee: string | null) => {
  if (!assignee) return true;
  return assignee === 'assigned' ? Boolean(item.assigneeName) : !item.assigneeName;
};

const applyFilters = (items: any[]) => items.filter((item) => {
  if (status.value && item.bizStatus !== status.value) return false;
  if (!matchCategory(item, filters.value.category)) return false;
  if (!matchCommission(item, filters.value.commission)) return false;
  if (!matchSample(item, filters.value.hasSample)) return false;
  if (!matchAssignee(item, filters.value.assignee)) return false;
  return true;
});

const fetchProducts = async (reset: boolean) => {
  if (reset) loading.value = true;
  else loadingMore.value = true;

  try {
    const activityId = await ensureActivityId();
    const res: any = await getActivityProducts(activityId, {
      count: 20,
      cursor: reset ? undefined : nextCursor.value,
      productInfo: selectedProduct.value || productKeyword.value.trim() || undefined,
      retrieveMode: 1
    });

    const data = res?.data || {};
    const items = applyFilters((Array.isArray(data.items) ? data.items : []).map(normalizeItem));
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

const buildProductOption = (item: any) => {
  const title = String(item?.title || item?.productName || '未命名商品').trim();
  const shopName = String(item?.shopName || '').trim();
  const productId = String(item?.productId || '').trim();
  return {
    label: shopName ? `${title} / ${shopName}` : title,
    value: productId
  };
};

const loadProductOptions = async (keyword: string) => {
  productOptionsLoading.value = true;
  try {
    const activityId = await ensureActivityId();
    const res: any = await getActivityProducts(activityId, {
      count: 20,
      productInfo: keyword || undefined,
      retrieveMode: 1
    });
    const items = Array.isArray(res?.data?.items) ? res.data.items : [];
    productOptions.value = items.map(buildProductOption).filter((item: { label: string; value: string }) => Boolean(item.value));
  } catch {
    productOptions.value = [];
  } finally {
    productOptionsLoading.value = false;
  }
};

const handleProductSearch = async (keyword: string) => {
  productKeyword.value = String(keyword || '').trim();
  await loadProductOptions(productKeyword.value);
};

const resetFilters = () => {
  selectedProduct.value = null;
  status.value = null;
  filters.value = { category: null, commission: null, hasSample: null, assignee: null };
  refreshProducts();
};

const refreshProducts = () => {
  fetchProducts(true);
};

const loadMore = () => {
  if (hasMore.value) fetchProducts(false);
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
  if (key === 'logs') openDialog('logs', row);
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

const copyPromotionLink = async (item: any) => {
  const productId = String(item?.productId || '');
  const activityId = resolvedActivityId.value;
  if (!productId || !activityId) {
    message.warning('商品信息不完整，暂不可生成推广链接');
    return;
  }
  promotionLoadingIds.value = new Set(promotionLoadingIds.value).add(productId);
  try {
    const res: any = await convertActivityProductLink(activityId, productId, { scene: 'PRODUCT_LIBRARY' });
    const data = res?.data || {};
    const link = data.promoteLink || data.promotionUrl || data.shortLink;
    if (!link) {
      message.warning('推广链接生成成功，但未返回可复制链接');
      return;
    }
    await navigator.clipboard.writeText(link);
    message.success('推广链接已复制');
    const merged = normalizeItem({
      ...item,
      bizStatus: 'LINKED',
      bizStatusLabel: getStatusLabel('LINKED'),
      promotion: {
        status: 'READY',
        statusLabel: '已生成',
        link,
        generatedAt: new Date().toISOString(),
        expireAt: null,
        failReason: null
      },
      promotionLink: link,
      promotionLinkStatus: 'READY',
      promotionLinkStatusLabel: '已生成',
      promotionLinkFailReason: null
    });
    products.value = products.value.map((row: any) =>
      String(row.productId) === productId ? { ...row, ...merged } : row
    );
    if (String(currentRow.value?.productId || '') === productId) {
      currentRow.value = { ...currentRow.value, ...merged };
    }
    detailRefreshKey.value += 1;
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '推广链接生成失败，请稍后重试');
  } finally {
    const next = new Set(promotionLoadingIds.value);
    next.delete(productId);
    promotionLoadingIds.value = next;
  }
};

const parsePercent = (value?: string) => {
  if (!value) return 0;
  return Number(String(value).replace('%', '').trim()) || 0;
};

const parsePrice = (value?: string) => {
  if (!value) return 0;
  return Number(String(value).replace(/[^\d.]/g, '')) || 0;
};

onMounted(() => {
  loadProductOptions('');
  refreshProducts();
});

watch(() => route.params.activityId, () => {
  nextCursor.value = '';
  refreshProducts();
});
</script>

<style scoped>
.product-page { max-width: 100%; }
.product-toolbar { background: var(--bg-card); border-radius: var(--radius-md); padding: 16px 20px; margin-bottom: var(--spacing-md); box-shadow: var(--shadow-sm); }
.product-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: var(--spacing-md); }
.product-card { background: var(--bg-card); border-radius: var(--radius-md); overflow: hidden; box-shadow: var(--shadow-card); transition: box-shadow var(--transition-normal), transform var(--transition-normal); }
.product-card:hover { box-shadow: var(--shadow-card-hover); transform: translateY(-2px); }
.card-image { position: relative; width: 100%; padding-top: 56.25%; background: linear-gradient(135deg, #f8f9fb 0%, #eef0f4 100%); overflow: hidden; }
.card-image img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.card-image-fallback { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; color: var(--text-muted); }
.card-badge { position: absolute; top: 8px; left: 8px; padding: 3px 10px; border-radius: var(--radius-sm); font-size: var(--text-xs); font-weight: 600; color: #fff; }
.badge-active { background: var(--color-primary); }
.badge-review { background: var(--color-warning); }
.badge-ended { background: var(--color-error); }
.badge-default { background: var(--color-info); }
.card-body { padding: 14px 16px; }
.card-title { font-size: var(--text-base); font-weight: 600; color: var(--text-primary); margin: 0 0 6px; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.card-tags { margin-bottom: 8px; display: flex; gap: 4px; flex-wrap: wrap; }
.card-metrics { display: flex; gap: 8px; padding: 10px 0; border-top: 1px solid var(--border-color-light); border-bottom: 1px solid var(--border-color-light); margin-bottom: 10px; }
.metric-item { flex: 1; text-align: center; }
.metric-label { display: block; font-size: var(--text-xs); color: var(--text-muted); margin-bottom: 2px; }
.metric-val { font-size: var(--text-base); font-weight: 600; color: var(--text-primary); }
.metric-val.primary { color: var(--color-primary); }
.metric-val.accent { color: #f2a900; }
.metric-val.small { font-size: 13px; }
.business-strip { margin-bottom: 10px; padding: 8px 0; border-bottom: 1px solid var(--border-color-light); }
.business-line { display: flex; align-items: center; justify-content: space-between; gap: 12px; font-size: var(--text-xs); }
.business-line + .business-line { margin-top: 6px; }
.business-label { color: var(--text-muted); }
.business-value { color: var(--text-primary); font-weight: 500; }
.promotion-strip { display: flex; align-items: center; justify-content: flex-end; gap: 12px; margin-bottom: 10px; min-height: 28px; }
.promotion-value { font-weight: 600; }
.promotion-ready { color: #18a058; }
.promotion-pending { color: #f0a020; }
.promotion-failed { color: #d03050; }
.promotion-reason { margin-top: 6px; font-size: var(--text-xs); color: var(--text-muted); }
.promotion-waiting { font-size: var(--text-xs); color: var(--text-muted); }
.card-footer { display: flex; align-items: center; justify-content: space-between; }
.card-stats { font-size: var(--text-xs); color: var(--text-secondary); }
.stats-active { color: var(--color-primary); }
.card-actions { display: flex; gap: 2px; flex-wrap: wrap; justify-content: flex-end; }
.load-more { text-align: center; padding: 24px 0; }
.no-more { color: var(--text-muted); font-size: var(--text-sm); }
.empty-state { text-align: center; padding: 80px 20px; color: var(--text-muted); }
.empty-icon { font-size: 48px; margin-bottom: 16px; }
.empty-state p { margin: 4px 0; font-size: var(--text-base); }
</style>
