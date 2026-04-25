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
            <span class="drawer-subtitle">ID: {{ detail?.productId }}</span>
          </n-space>
        </div>
      </template>

      <n-spin :show="loading">
        <div v-if="detail" class="detail-container">
          <!-- 顶部快捷操作栏 -->
          <div class="action-bar">
            <n-space>
              <n-button v-if="detail.bizStatus === 'PENDING_AUDIT' && canDo('audit')" type="warning" size="small" secondary @click="handleAction('audit')">审核商品</n-button>
              <n-button v-if="detail.bizStatus === 'APPROVED' && canDo('bind')" type="primary" size="small" secondary @click="handleAction('bind')">绑定活动</n-button>
              <n-button v-if="detail.bizStatus === 'BOUND' && canDo('assign')" type="info" size="small" secondary @click="handleAction('assign')">分配招商</n-button>
              <n-button v-if="detail.bizStatus === 'ASSIGNED' && canDo('convert')" type="success" size="small" secondary @click="handleAction('convert')">生成转链</n-button>
              <n-button v-if="['LINKED', 'FOLLOWING'].includes(detail.bizStatus) && canDo('follow')" type="success" size="small" secondary @click="handleAction('follow')">
                {{ detail.bizStatus === 'LINKED' ? '跟进达人' : '追加跟进' }}
              </n-button>
            </n-space>
          </div>

          <n-tabs type="segment" animated style="margin-top: 12px;">
            <!-- 1. 业务推进 (Steps) -->
            <n-tab-pane name="progress" tab="业务推进">
              <div class="pane-content">
                <n-steps :current="currentStep" status="process" size="small" class="biz-steps">
                  <n-step title="待审核" description="系统同步" />
                  <n-step title="审核通过" description="商品初筛" />
                  <n-step title="已绑定" description="关联活动" />
                  <n-step title="已分配" description="指定招商" />
                  <n-step title="已转链" description="渠道推广" />
                  <n-step title="跟进中" description="达人对接" />
                </n-steps>

                <div v-if="detail.bizStatus === 'REJECTED'" class="status-alert">
                  <n-alert type="error" title="审核已拒绝" bordered>
                    原因：{{ detail.auditRemark || '未填写原因' }}
                  </n-alert>
                </div>

                <n-card title="推进概要" size="small" style="margin-top: 24px;">
                  <n-descriptions label-placement="left" :column="2" size="small">
                    <n-descriptions-item label="当前状态">
                      <n-tag :type="statusBadgeClass(detail.bizStatus)" size="tiny">{{ getStatusLabel(detail.bizStatus) }}</n-tag>
                    </n-descriptions-item>
                    <n-descriptions-item label="最后操作时间">{{ detail.lastOperationAt || '-' }}</n-descriptions-item>
                    <n-descriptions-item label="招商负责人">{{ detail.assigneeId || '未分配' }}</n-descriptions-item>
                    <n-descriptions-item label="绑定活动">{{ detail.boundActivityId || '未绑定' }}</n-descriptions-item>
                  </n-descriptions>
                </n-card>
              </div>
            </n-tab-pane>

            <!-- 2. 基础信息 -->
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
                      <n-descriptions-item label="店铺名称">{{ detail.shopName }}</n-descriptions-item>
                      <n-descriptions-item label="售价">{{ detail.priceText }}</n-descriptions-item>
                      <n-descriptions-item label="佣金率">
                        <span style="color: var(--color-primary); font-weight: 600;">{{ detail.activityCosRatioText }}</span>
                      </n-descriptions-item>
                      <n-descriptions-item label="当前库存">{{ detail.productStock }}</n-descriptions-item>
                      <n-descriptions-item label="累计销量">{{ detail.sales || 0 }}</n-descriptions-item>
                      <n-descriptions-item label="同步时间">{{ detail.syncTime }}</n-descriptions-item>
                      <n-descriptions-item label="详情链接">
                        <n-button text type="primary" size="tiny" tag="a" :href="detail.detailUrl" target="_blank">点击跳转</n-button>
                      </n-descriptions-item>
                    </n-descriptions>
                  </div>
                </div>
              </div>
            </n-tab-pane>

            <!-- 3. 达人跟进 (Timeline) -->
            <n-tab-pane name="follow" tab="达人跟进">
              <div class="pane-content">
                <n-timeline v-if="detail.followRecords?.length" style="padding-left: 12px;">
                  <n-timeline-item
                    v-for="record in detail.followRecords"
                    :key="record.id"
                    :type="getFollowType(record.followStatus)"
                    :title="`${record.talentName} - ${getFollowStatusLabel(record.followStatus)}`"
                    :time="record.createTime"
                  >
                    <div class="follow-content">
                      <p>{{ record.content }}</p>
                      <div class="follow-footer">
                        <span>操作人: {{ record.operatorName || record.operatorId }}</span>
                        <span v-if="record.nextFollowTime" style="margin-left: 12px;">下次跟进: {{ record.nextFollowTime }}</span>
                      </div>
                    </div>
                  </n-timeline-item>
                </n-timeline>
                <n-empty v-else description="暂无达人跟进记录" />
              </div>
            </n-tab-pane>

            <!-- 4. 推广链接 -->
            <n-tab-pane name="promotion" tab="推广链接">
              <div class="pane-content">
                <n-data-table :columns="promotionColumns" :data="promotionLinks" size="small" :pagination="false" />
                <n-empty v-if="!promotionLinks.length" description="暂无推广链接" style="margin-top: 20px;" />
              </div>
            </n-tab-pane>

            <!-- 5. 操作日志 -->
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
import { useMessage } from 'naive-ui';
import { getActivityProductDetail, getActivityProductOperationLogs } from '../../api/activityProduct';
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
const authStore = useAuthStore();

const canDo = (action: string) => {
  const roles = authStore.roleCodes;
  if (roles.includes('admin')) return true;
  if (action === 'audit') return hasAccess(roles, ['biz_leader', 'biz_staff']);
  if (action === 'bind' || action === 'assign') return hasAccess(roles, ['biz_leader', 'channel_leader']);
  if (action === 'convert' || action === 'follow') return hasAccess(roles, ['channel_leader', 'channel_staff']);
  return true;
};

const loading = ref(false);
const detail = ref<any>(null);
const operationLogs = ref<any[]>([]);

const statusMap: Record<string, number> = {
  PENDING_AUDIT: 0,
  APPROVED: 1,
  BOUND: 2,
  ASSIGNED: 3,
  LINKED: 4,
  FOLLOWING: 5
};

const currentStep = computed(() => {
  if (!detail.value?.bizStatus) return 0;
  return statusMap[detail.value.bizStatus] ?? 0;
});

const promotionLinks = computed(() => {
  if (!detail.value?.shortLink && !detail.value?.promoteLink) return [];
  return [{
    time: detail.value.lastOperationAt || '-',
    shortLink: detail.value.shortLink || '-',
    promoteLink: detail.value.promoteLink || '-',
    scene: detail.value.promotionScene === 1 ? '活动页' : '商品页'
  }];
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
  { title: '短链接', key: 'shortLink', minWidth: 200, render: (row: any) => row.shortLink },
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
  } catch (error: any) {
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

const getStatusLabel = (s?: string) => {
  const map: Record<string, string> = {
    PENDING_AUDIT: '待审核',
    APPROVED: '审核通过',
    REJECTED: '审核拒绝',
    BOUND: '已绑定活动',
    ASSIGNED: '已分配招商',
    LINKED: '已转链',
    FOLLOWING: '跟进中'
  };
  return map[s || ''] || s || '未知';
};

const statusBadgeClass = (s?: string) => {
  if (s === 'PENDING_AUDIT') return 'warning';
  if (['LINKED', 'FOLLOWING'].includes(s || '')) return 'success';
  if (s === 'REJECTED') return 'error';
  return 'default';
};

const getFollowStatusLabel = (s: string) => {
  const map: Record<string, string> = {
    INVITED: '已邀约',
    REPLIED: '已回复',
    SAMPLED: '已寄样',
    PROMOTING: '推广中',
    REJECTED: '拒绝合作'
  };
  return map[s] || s;
};

const getFollowType = (s: string) => {
  if (s === 'PROMOTING') return 'success';
  if (s === 'REJECTED') return 'error';
  return 'info';
};

const handleAction = (action: string) => {
  emit('action', { action, row: detail.value });
};
</script>

<style scoped>
.drawer-header { padding-bottom: 8px; border-bottom: 1px solid var(--border-color); }
.drawer-title { font-size: 18px; font-weight: 600; color: var(--text-primary); }
.drawer-subtitle { font-size: 13px; color: var(--text-muted); }

.detail-container { display: flex; flex-direction: column; gap: 16px; }
.action-bar { padding: 12px; background: #f8f9fb; border-radius: 8px; border: 1px dashed #dcdfe6; }

.pane-content { padding: 16px 4px; }
.biz-steps { margin: 20px 0 32px 0; }
.status-alert { margin-bottom: 20px; }

.basic-info-grid { display: flex; gap: 24px; }
.info-image { flex-shrink: 0; border: 1px solid var(--border-color); border-radius: 8px; overflow: hidden; padding: 4px; background: #fff; }
.info-main { flex: 1; }
.product-title { font-size: 16px; font-weight: 600; color: var(--text-primary); margin: 0; line-height: 1.4; }

.follow-content { margin-top: 4px; background: #f9fafb; padding: 10px 14px; border-radius: 6px; border: 1px solid #f0f1f2; }
.follow-content p { margin: 0 0 8px 0; font-size: 14px; color: var(--text-secondary); line-height: 1.6; }
.follow-footer { display: flex; font-size: 12px; color: var(--text-muted); }
</style>
