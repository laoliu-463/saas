<template>
  <n-drawer :show="show" width="850" placement="right" @update:show="updateShow">
    <n-drawer-content closable>
      <template #header>
        <div class="drawer-header">
          <n-space align="center" :size="12">
            <span class="drawer-title">商品业务全貌</span>
            <n-tag :type="statusBadgeClass(detail?.bizStatus)" size="small" round>
              {{ getStatusLabel(detail?.bizStatus) }}
            </n-tag>
          </n-space>
        </div>
      </template>

      <n-spin :show="loading">
        <div v-if="detail" class="detail-container">
          <div class="action-bar">
            <n-space>
              <n-button
                v-if="detail.bizStatus === 'PENDING_AUDIT' && canDo('audit')"
                type="warning"
                size="small"
                secondary
                @click="handleAction('audit')"
              >
                审核商品
              </n-button>
              <n-button
                v-if="['APPROVED', 'BOUND'].includes(detail.bizStatus) && canDo('assign')"
                type="info"
                size="small"
                secondary
                @click="handleAction('assign')"
              >
                分配招商
              </n-button>
            </n-space>
          </div>

          <n-tabs type="segment" animated style="margin-top: 12px;">
            <n-tab-pane name="progress" tab="业务推进">
              <div class="pane-content">
                <n-steps :current="currentStep" status="process" size="small" class="biz-steps">
                  <n-step title="待审核" description="系统同步" />
                  <n-step title="审核通过" description="商品初筛" />
                  <n-step title="已分配" description="指定招商" />
                  <n-step title="推广就绪" description="后台准备链接" />
                </n-steps>

                <div v-if="detail.bizStatus === 'REJECTED'" class="status-alert">
                  <n-alert type="error" title="审核已拒绝" bordered>
                    原因：{{ detail.auditRemark || '未填写原因' }}
                  </n-alert>
                </div>

                <n-card title="推进概览" size="small" style="margin-top: 24px;">
                  <n-descriptions label-placement="left" :column="2" size="small">
                    <n-descriptions-item label="当前状态">
                      <n-tag :type="statusBadgeClass(detail.bizStatus)" size="tiny">
                        {{ getStatusLabel(detail.bizStatus) }}
                      </n-tag>
                    </n-descriptions-item>
                    <n-descriptions-item label="最后操作时间">{{ detail.lastOperationAt || '-' }}</n-descriptions-item>
                    <n-descriptions-item label="招商负责人">{{ detail.assigneeName || '未分配' }}</n-descriptions-item>
                    <n-descriptions-item label="来源活动">{{ detail.activityId || '-' }}</n-descriptions-item>
                  </n-descriptions>
                </n-card>
              </div>
            </n-tab-pane>

            <n-tab-pane name="basic" tab="基础信息">
              <div class="pane-content">
                <div class="basic-info-grid">
                  <div class="info-image">
                    <n-image :src="detail.cover" width="120" height="120" object-fit="cover" />
                  </div>
                  <div class="info-main">
                    <h3 class="product-title">{{ detail.title }}</h3>
                    <n-descriptions label-placement="left" :column="2" size="small" style="margin-top: 12px;">
                      <n-descriptions-item label="商品ID">{{ detail.productId }}</n-descriptions-item>
                      <n-descriptions-item label="店铺名称">{{ detail.shopName || '-' }}</n-descriptions-item>
                      <n-descriptions-item label="商家信息">{{ detail.merchantName || detail.merchantShopName || '-' }}</n-descriptions-item>
                      <n-descriptions-item label="售价">{{ detail.priceText || '-' }}</n-descriptions-item>
                      <n-descriptions-item label="佣金率"><span class="highlight-text">{{ detail.activityCosRatioText || '-' }}</span></n-descriptions-item>
                      <n-descriptions-item label="服务费率">{{ formatPercent(detail.serviceFeeRate) }}</n-descriptions-item>
                      <n-descriptions-item label="当前库存">{{ detail.productStock || '-' }}</n-descriptions-item>
                      <n-descriptions-item label="累计销量">{{ detail.sales30d ?? detail.sales ?? 0 }}</n-descriptions-item>
                      <n-descriptions-item label="活动剩余">{{ detail.timeLeft || '长期' }}</n-descriptions-item>
                      <n-descriptions-item label="同步时间">{{ detail.syncTime || '-' }}</n-descriptions-item>
                      <n-descriptions-item label="详情链接">
                        <n-button text type="primary" size="tiny" tag="a" :href="detail.detailUrl" target="_blank">点击跳转</n-button>
                      </n-descriptions-item>
                    </n-descriptions>
                  </div>
                </div>
              </div>
            </n-tab-pane>

            <n-tab-pane name="promotion" tab="推广链接">
              <div class="pane-content">
                <n-card size="small" title="推广链接状态">
                  <n-descriptions label-placement="left" :column="2" size="small">
                    <n-descriptions-item label="当前状态">
                      <n-tag :type="promotionTagType(promotion.status)">{{ promotion.statusLabel }}</n-tag>
                    </n-descriptions-item>
                    <n-descriptions-item label="生成时间">{{ promotion.generatedAt || '-' }}</n-descriptions-item>
                    <n-descriptions-item label="有效期">{{ promotion.expireAt || '约 3 个月' }}</n-descriptions-item>
                    <n-descriptions-item label="失败原因">{{ promotion.failReason || '-' }}</n-descriptions-item>
                    <n-descriptions-item label="推广链接" :span="2">
                      <div class="promotion-link-box">
                        <span class="promotion-link-text">{{ promotion.link || '后台处理中，暂不可复制' }}</span>
                        <n-button
                          v-if="canDo('promotion')"
                          size="small"
                          type="primary"
                          secondary
                          :loading="promotionGenerating"
                          @click="copyPromotionLink"
                        >
                          复制推广链接
                        </n-button>
                      </div>
                    </n-descriptions-item>
                  </n-descriptions>
                </n-card>

                <div class="summary-panel">
                  <n-grid :cols="4" :x-gap="12" align-items="center">
                    <n-gi><n-statistic label="累计出单" :value="detail.orderCount || 0" /></n-gi>
                    <n-gi><n-statistic label="已归因订单" :value="detail.attributedCount || 0" /></n-gi>
                    <n-gi><n-statistic label="未归因订单" :value="detail.unattributedCount || 0" /></n-gi>
                    <n-gi>
                      <n-button type="primary" secondary @click="goToOrders(detail.productId)">查看订单明细</n-button>
                    </n-gi>
                  </n-grid>
                </div>

                <h4 class="section-title">历史推广记录</h4>
                <n-data-table :columns="promotionColumns" :data="promotionLinks" size="small" :pagination="false" />
                <n-empty v-if="!promotionLinks.length" description="暂无推广记录" style="margin-top: 20px;" />
              </div>
            </n-tab-pane>

            <n-tab-pane name="materials" tab="推广资料包">
              <div class="pane-content">
                <div class="material-actions">
                  <n-space>
                    <n-button type="primary" size="small" ghost @click="copyText(detail?.promotionMaterialPack?.outreachScript)">复制建联话术</n-button>
                    <n-button type="primary" size="small" ghost @click="copyText(detail?.promotionMaterialPack?.shortVideoScript)">复制短视频脚本</n-button>
                    <n-button v-if="promotion.link" type="info" size="small" @click="copyText(promotion.link)">复制推广链接</n-button>
                  </n-space>
                </div>

                <n-descriptions label-placement="top" :column="1" bordered size="small">
                  <n-descriptions-item label="核心卖点">
                    <ul class="material-list">
                      <li v-for="point in materialSellingPoints" :key="point">{{ point }}</li>
                    </ul>
                  </n-descriptions-item>
                  <n-descriptions-item label="建联话术示例">
                    <n-log :log="detail?.promotionMaterialPack?.outreachScript || '-'" />
                  </n-descriptions-item>
                  <n-descriptions-item label="短视频脚本">
                    <n-log :log="detail?.promotionMaterialPack?.shortVideoScript || '-'" />
                  </n-descriptions-item>
                </n-descriptions>
              </div>
            </n-tab-pane>

            <n-tab-pane name="logs" tab="操作日志">
              <div class="pane-content">
                <n-data-table :columns="logColumns" :data="operationLogs" size="small" :pagination="{ pageSize: 10 }" />
              </div>
            </n-tab-pane>
          </n-tabs>
        </div>
      </n-spin>
    </n-drawer-content>
  </n-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useMessage } from 'naive-ui';
import { convertActivityProductLink, getActivityProductDetail, getActivityProductOperationLogs } from '../../api/activityProduct';
import { useAuthStore } from '../../stores/auth';
import { hasAccess } from '../../constants/rbac';

const props = defineProps<{
  show: boolean;
  activityId: string | number | null;
  productId: string | number | null;
  refreshKey?: number;
}>();

const emit = defineEmits(['update:show', 'action']);
const message = useMessage();
const router = useRouter();
const authStore = useAuthStore();

const canDo = (action: string) => {
  const roles = authStore.roleCodes;
  if (roles.includes('admin')) return true;
  if (action === 'audit') return hasAccess(roles, ['biz_leader', 'biz_staff']);
  if (action === 'assign') return hasAccess(roles, ['biz_leader', 'channel_leader']);
  if (action === 'promotion') return hasAccess(roles, ['channel_leader', 'channel_staff']);
  return true;
};

const loading = ref(false);
const promotionGenerating = ref(false);
const detail = ref<any>(null);
const operationLogs = ref<any[]>([]);

const statusMap: Record<string, number> = {
  PENDING_AUDIT: 0,
  APPROVED: 1,
  BOUND: 1,
  ASSIGNED: 2,
  LINKED: 3,
  FOLLOWING: 3
};

const currentStep = computed(() => statusMap[detail.value?.bizStatus || 'PENDING_AUDIT'] ?? 0);

const promotion = computed(() => detail.value?.promotion || {
  status: detail.value?.promotionLinkStatus || 'PENDING',
  statusLabel: detail.value?.promotionLinkStatusLabel || '未生成',
  link: detail.value?.promotionLink || null,
  generatedAt: detail.value?.promotionLinkGeneratedAt || null,
  expireAt: detail.value?.promotionLinkExpireAt || null,
  failReason: detail.value?.promotionLinkFailReason || null
});

const materialSellingPoints = computed(() => {
  const points = detail.value?.promotionMaterialPack?.sellingPoints;
  return Array.isArray(points) && points.length ? points : ['暂无可展示的推广卖点'];
});

const promotionLinks = computed(() => {
  if (Array.isArray(detail.value?.promotionLinks) && detail.value.promotionLinks.length) {
    return detail.value.promotionLinks.map((item: any) => ({
      time: item.createdAt || item.time || '-',
      shortLink: item.shortLink || item.shortUrl || '-',
      promoteLink: item.promoteLink || item.promotionUrl || '-',
      scene: detail.value?.promotionScene === 1 ? '活动页' : '商品页'
    }));
  }
  return [];
});

const logColumns = [
  { title: '时间', key: 'createTime', width: 160 },
  { title: '类型', key: 'operationType', width: 100 },
  { title: '状态流转', key: 'statusFlow', width: 180, render: (row: any) => `${row.beforeStatus || '-'} -> ${row.afterStatus || '-'}` },
  { title: '结果', key: 'success', width: 80, render: (row: any) => (row.success ? '成功' : '失败') },
  { title: '操作人', key: 'operatorId', width: 100 },
  { title: '备注', key: 'operationRemark', minWidth: 150 }
];

const promotionColumns = [
  { title: '时间', key: 'time', width: 160 },
  { title: '场景', key: 'scene', width: 100 },
  { title: '短链', key: 'shortLink', minWidth: 200 },
  { title: '推广链接', key: 'promoteLink', minWidth: 200, ellipsis: { tooltip: true } }
];

const fetchData = async () => {
  if (!props.activityId || !props.productId) return;
  loading.value = true;
  try {
    const detailRes: any = await getActivityProductDetail(props.activityId, props.productId);
    detail.value = detailRes?.data || {};
    const logsRes: any = await getActivityProductOperationLogs(props.activityId, props.productId, { page: 1, size: 50 });
    operationLogs.value = logsRes?.data?.records || [];
  } catch {
    message.error('加载商品详情失败');
  } finally {
    loading.value = false;
  }
};

watch(() => props.show, (val) => {
  if (val) fetchData();
  else {
    detail.value = null;
    operationLogs.value = [];
  }
});

watch(() => props.refreshKey, () => {
  if (props.show) fetchData();
});

const updateShow = (val: boolean) => emit('update:show', val);

const getStatusLabel = (status?: string) => {
  const map: Record<string, string> = {
    PENDING_AUDIT: '待审核',
    APPROVED: '审核通过',
    REJECTED: '审核拒绝',
    BOUND: '历史已绑定',
    ASSIGNED: '已分配招商',
    LINKED: '已转链',
    FOLLOWING: '已转交达人 CRM'
  };
  return map[status || ''] || status || '未知';
};

const statusBadgeClass = (status?: string) => {
  if (status === 'PENDING_AUDIT') return 'warning';
  if (['LINKED', 'FOLLOWING'].includes(status || '')) return 'success';
  if (status === 'REJECTED') return 'error';
  return 'default';
};

const promotionTagType = (status?: string) => {
  if (status === 'READY') return 'success';
  if (status === 'FAILED') return 'error';
  return 'warning';
};

const handleAction = (action: string) => {
  emit('action', { action, row: detail.value });
};

const goToOrders = (productId: string) => {
  if (!productId) return;
  router.push({ path: '/orders', query: { productId: String(productId) } });
  emit('update:show', false);
};

const copyText = async (text?: string) => {
  if (!text) {
    message.warning('暂无可复制内容');
    return;
  }
  try {
    await navigator.clipboard.writeText(text);
    message.success('复制成功');
  } catch {
    message.error('复制失败');
  }
};

const copyPromotionLink = async () => {
  if (!props.activityId || !props.productId) {
    message.warning('商品信息不完整，暂不可生成推广链接');
    return;
  }
  promotionGenerating.value = true;
  try {
    const res: any = await convertActivityProductLink(props.activityId, props.productId, { scene: 'PRODUCT_DETAIL' });
    const data = res?.data || {};
    const link = data.promoteLink || data.promotionUrl || data.shortLink;
    if (!link) {
      message.warning('推广链接生成成功，但未返回可复制链接');
      return;
    }
    await navigator.clipboard.writeText(link);
    message.success('推广链接已复制');
    await fetchData();
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '推广链接生成失败，请稍后重试');
  } finally {
    promotionGenerating.value = false;
  }
};

const formatPercent = (value?: number | string | null) => {
  if (value === null || value === undefined || value === '') return '-';
  if (typeof value === 'string' && value.includes('%')) return value;
  return `${value}%`;
};
</script>

<style scoped>
.drawer-header { padding-bottom: 8px; border-bottom: 1px solid var(--border-color); }
.drawer-title { font-size: 18px; font-weight: 600; color: var(--text-primary); }
.detail-container { display: flex; flex-direction: column; gap: 16px; }
.action-bar { padding: 12px; background: #f8f9fb; border-radius: 8px; border: 1px dashed #dcdfe6; }
.pane-content { padding: 16px 4px; }
.biz-steps { margin: 20px 0 32px; }
.status-alert { margin-bottom: 20px; }
.basic-info-grid { display: flex; gap: 24px; }
.info-image { flex-shrink: 0; border: 1px solid var(--border-color); border-radius: 8px; overflow: hidden; padding: 4px; background: #fff; }
.info-main { flex: 1; }
.product-title { font-size: 16px; font-weight: 600; color: var(--text-primary); margin: 0; line-height: 1.4; }
.highlight-text { color: var(--color-primary); font-weight: 600; }
.summary-panel { margin-top: 16px; padding: 16px; background: #f8f9fb; border-radius: 8px; }
.promotion-link-box { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.promotion-link-text { word-break: break-all; color: var(--text-primary); }
.section-title { margin: 16px 0 12px; }
.material-actions { margin-bottom: 16px; }
.material-list { margin: 0; padding-left: 18px; }
</style>
