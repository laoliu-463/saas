<template>
  <n-drawer :show="show" :width="DRAWER_WIDTH_PX.lg" placement="right" @update:show="updateShow">
    <n-drawer-content closable>
      <template #header>
        <div class="drawer-header">
          <n-space align="center" :size="12">
            <span class="drawer-title">商品业务全貌</span>
            <n-tag :type="statusBadgeClass(detail?.bizStatus)" size="small" round>
              {{ getStatusLabel(detail?.bizStatus) }}
            </n-tag>
            <n-tag v-if="detail?.pinned" type="error" size="small" round data-testid="product-detail-pinned-badge">
              置顶
            </n-tag>
          </n-space>
        </div>
      </template>

      <n-spin :show="loading">
        <div v-if="detail" class="detail-container">
          <div class="action-bar">
            <n-space>
              <n-button
                v-if="!libraryMode && detail.bizStatus === 'PENDING_AUDIT' && canDo('auditOwner')"
                type="info"
                size="small"
                secondary
                @click="handleAction('auditOwner')"
              >
                分配审核人
              </n-button>
              <n-button
                v-if="!libraryMode && detail.bizStatus === 'PENDING_AUDIT' && canDo('audit')"
                type="warning"
                size="small"
                secondary
                @click="handleAction('audit')"
              >
                审核商品
              </n-button>
              <n-button
                v-if="!libraryMode && canAssignDetail"
                type="info"
                size="small"
                secondary
                @click="handleAction('assign')"
              >
                {{ detail.assigneeName || detail.bizStatus === 'ASSIGNED' ? '重新分配' : '分配招商' }}
              </n-button>
              <n-tag
                v-if="libraryDisplay.selectedToLibrary"
                :type="libraryDisplay.entryTagType"
                size="small"
                round
                data-testid="product-library-entry-tag"
              >
                {{ libraryDisplay.entryLabel }}
              </n-tag>
              <n-tag v-if="detail.pinned" type="error" size="small" round data-testid="product-detail-pinned-tag">
                {{ pinnedStatusText }}
              </n-tag>
              <n-button
                v-if="businessReady && canDo('promotion')"
                size="small"
                secondary
                @click="goToSampleApply"
              >
                内部寄样
              </n-button>
            </n-space>
          </div>

          <div v-if="libraryDisplay.hiddenFromList" class="library-visibility-alert">
            <n-alert type="warning" title="已入库，但共享商品库列表不可见" bordered>
              {{ libraryDisplay.visibilityHint }}
            </n-alert>
          </div>
          <div v-else-if="!libraryDisplay.selectedToLibrary && libraryReadiness.code !== 'REJECTED'" class="library-visibility-alert">
            <n-alert
              :type="libraryReadiness.canDisplayAfterEntry ? 'success' : 'warning'"
              :title="libraryReadiness.label"
              bordered
            >
              {{ libraryReadiness.hint }}
            </n-alert>
          </div>

          <n-tabs type="segment" animated style="margin-top: 12px;">
            <n-tab-pane name="progress" tab="业务推进">
              <div class="pane-content">
                <n-steps :current="currentStep" status="process" size="small" class="biz-steps">
                  <n-step title="待审核" />
                  <n-step title="审核通过" />
                  <n-step title="已分配招商" />
                  <n-step title="推广就绪" />
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
                    <n-descriptions-item :label="detail.bizStatus === 'PENDING_AUDIT' ? '审核负责人' : '招商负责人'">
                      {{ detail.assigneeName || (detail.bizStatus === 'PENDING_AUDIT' ? '未分配审核人' : '未分配') }}
                    </n-descriptions-item>
                    <n-descriptions-item label="来源活动">{{ detail.activityId || '-' }}</n-descriptions-item>
                    <n-descriptions-item v-if="detail.pinned" label="置顶状态">
                      <n-tag type="error" size="tiny" round>{{ pinnedStatusText }}</n-tag>
                    </n-descriptions-item>
                  </n-descriptions>
                </n-card>

                <n-card title="推进判断" size="small" style="margin-top: 16px;">
                  <div class="decision-card">
                    <n-alert v-if="!libraryDisplay.selectedToLibrary" type="info" :bordered="false">
                      商品通过审核后会自动进入商品库；仅当满足联盟「推广中」等展示规则时，才会出现在共享商品库列表。
                    </n-alert>
                    <div class="decision-current">
                      <n-tag :type="decisionTagType(latestDecision?.level)" size="small" round>
                        {{ latestDecision?.label || '暂无判断' }}
                      </n-tag>
                      <span class="decision-reason">{{ latestDecision?.reason || '还没有留下主推、暂缓或放弃原因' }}</span>
                    </div>
                    <n-alert
                      v-if="decisionRiskSummary"
                      :type="decisionRiskSummary.type"
                      :title="decisionRiskSummary.title"
                      bordered
                    >
                      <div class="decision-risk-content">
                        <div>{{ decisionRiskSummary.content }}</div>
                        <ul class="decision-risk-actions">
                          <li v-for="action in decisionRiskSummary.actions" :key="action">{{ action }}</li>
                        </ul>
                        <div v-if="decisionRiskSummary.closingNote" class="decision-risk-note">
                          {{ decisionRiskSummary.closingNote }}
                        </div>
                      </div>
                    </n-alert>
                    <div class="decision-form">
                      <n-radio-group v-model:value="decisionForm.level" size="small">
                        <n-radio-button value="MAIN">主推</n-radio-button>
                        <n-radio-button value="SECONDARY">次推</n-radio-button>
                        <n-radio-button value="PAUSE">暂缓</n-radio-button>
                        <n-radio-button value="DROP">放弃</n-radio-button>
                      </n-radio-group>
                      <n-input
                        v-model:value="decisionForm.reason"
                        type="textarea"
                        :autosize="{ minRows: 2, maxRows: 4 }"
                        placeholder="填写判断原因，例如：佣金高且库存稳定，适合优先找达人"
                      />
                      <div v-if="businessReady && canDo('decision')" class="decision-actions">
                        <n-button
                          type="primary"
                          size="small"
                          :loading="decisionSaving"
                          @click="saveDecision"
                        >
                          保存判断
                        </n-button>
                      </div>
                    </div>
                  </div>
                </n-card>

                <n-card title="最近推进时间线" size="small" style="margin-top: 16px;">
                  <div v-if="timelineEvents.length" class="timeline-list">
                    <div v-for="event in timelineEvents" :key="event.key" class="timeline-item">
                      <div class="timeline-marker" :class="event.markerClass"></div>
                      <div class="timeline-body">
                        <div class="timeline-title-row">
                          <span class="timeline-title">{{ event.title }}</span>
                          <span class="timeline-time">{{ event.time }}</span>
                        </div>
                        <div class="timeline-meta">{{ event.meta }}</div>
                        <div v-if="event.remark" class="timeline-remark">{{ event.remark }}</div>
                      </div>
                    </div>
                  </div>
                  <n-empty v-else description="暂无推进记录" />
                </n-card>
              </div>
            </n-tab-pane>

            <n-tab-pane name="basic" tab="基础信息">
              <div class="pane-content">
                <div class="basic-info-grid">
                  <div class="info-image">
                    <n-image :src="detail.cover" width="120" height="120" object-fit="cover" />
                    <span v-if="detail.pinned" class="detail-pin-badge" data-testid="product-detail-cover-pinned-badge">置顶</span>
                  </div>
                  <div class="info-main">
                    <div class="product-title-row">
                      <h3 class="product-title">{{ detail.title }}</h3>
                      <n-tag v-if="detail.pinned" type="error" size="small" round>置顶</n-tag>
                    </div>
                    <n-descriptions label-placement="left" :column="2" size="small" style="margin-top: 12px;">
                      <n-descriptions-item label="商品ID">{{ detail.productId }}</n-descriptions-item>
                      <n-descriptions-item label="联盟推广状态">
                        {{ detail.statusText || '-' }}
                        <template v-if="detail.status !== undefined && detail.status !== null">
                          （{{ detail.status }}）
                        </template>
                      </n-descriptions-item>
                      <n-descriptions-item label="商品库展示">
                        {{ libraryDisplay.selectedToLibrary ? libraryDisplay.listVisibilityLabel : libraryReadiness.label }}
                        <template v-if="libraryDisplay.displayStatusLabel && libraryDisplay.selectedToLibrary">
                          （{{ libraryDisplay.displayStatusLabel }}）
                        </template>
                      </n-descriptions-item>
                      <n-descriptions-item v-if="libraryReadiness.hint && !libraryDisplay.selectedToLibrary" label="展示说明" :span="2">
                        {{ libraryReadiness.hint }}
                      </n-descriptions-item>
                      <n-descriptions-item label="店铺名称">{{ detail.shopName || '-' }}</n-descriptions-item>
                      <n-descriptions-item label="商家信息">{{ detail.merchantName || detail.merchantShopName || '-' }}</n-descriptions-item>
                      <n-descriptions-item label="售价">{{ detail.priceText || '-' }}</n-descriptions-item>
                      <n-descriptions-item label="佣金率"><span class="highlight-text">{{ detail.activityCosRatioText || '-' }}</span></n-descriptions-item>
                      <n-descriptions-item label="服务费率">{{ formatPercent(detail.serviceFeeRate) }}</n-descriptions-item>
                      <n-descriptions-item label="当前库存">{{ detail.productStock || '-' }}</n-descriptions-item>
                      <n-descriptions-item label="累计销量">{{ detail.sales30d ?? detail.sales ?? 0 }}</n-descriptions-item>
                      <n-descriptions-item label="活动剩余">{{ detail.timeLeft || '长期' }}</n-descriptions-item>
                      <n-descriptions-item label="同步时间">{{ detail.syncTime || '-' }}</n-descriptions-item>
                      <n-descriptions-item v-if="detail.pinned" label="置顶状态">
                        <n-tag type="error" size="tiny" round>{{ pinnedStatusText }}</n-tag>
                      </n-descriptions-item>
                      <n-descriptions-item label="详情链接">
                        <n-button text type="primary" size="tiny" tag="a" :href="detail.detailUrl" target="_blank">点击跳转</n-button>
                      </n-descriptions-item>
                    </n-descriptions>
                  </div>
                </div>

                <n-card title="SKU 规格" size="small" style="margin-top: 16px;">
                  <n-form-item v-if="productSkus.length" label="选择规格" label-placement="left" label-width="80">
                    <ProductSpecSelector v-model="selectedSkuName" :skus="productSkus" :loading="skusLoading" />
                  </n-form-item>
                  <n-spin :show="skusLoading">
                    <n-data-table
                      v-if="productSkus.length"
                      size="small"
                      :columns="skuColumns"
                      :data="productSkus"
                      :bordered="false"
                      :row-key="(row: any) => row.skuId"
                    />
                    <n-empty v-else-if="!skusLoading" description="暂无 SKU 数据（需抖店授权且上游返回规格）" />
                  </n-spin>
                </n-card>
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
                          v-if="businessReady && canDo('promotion')"
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
                    <n-button
                      v-if="businessReady && canDo('promotion')"
                      type="primary"
                      size="small"
                      :loading="briefCopying"
                      @click="copyProductBrief"
                    >
                      复制讲解 + 短链
                    </n-button>
                    <n-button type="primary" size="small" ghost @click="copyText(detail?.promotionMaterialPack?.outreachScript)">复制建联话术</n-button>
                    <n-button type="primary" size="small" ghost @click="copyText(detail?.promotionMaterialPack?.shortVideoScript)">复制短视频脚本</n-button>
                    <n-button v-if="promotion.link" type="info" size="small" @click="copyText(promotion.link)">复制推广链接</n-button>
                  </n-space>
                </div>

                <n-card size="small" title="审核补充信息">
                  <template #header-extra>
                    <n-tag v-if="auditSchemaVersion" size="small" round type="default">
                      schema v{{ auditSchemaVersion }}
                    </n-tag>
                  </template>
                  <n-empty v-if="!hasAuditSupplement" description="暂无审核补充信息" />
                  <n-descriptions v-else label-placement="left" :column="2" size="small">
                    <n-descriptions-item label="专属价说明">
                      {{ auditSupplement.exclusivePriceRemark || '-' }}
                    </n-descriptions-item>
                    <n-descriptions-item label="发货信息">
                      {{ auditSupplement.shippingInfo || '-' }}
                    </n-descriptions-item>
                    <n-descriptions-item label="是否支持投流">
                      <n-tag
                        v-if="auditSupplement.supportsAds !== undefined && auditSupplement.supportsAds !== null"
                        :type="auditSupplement.supportsAds ? 'success' : 'warning'"
                        size="small"
                        round
                      >
                        {{ auditSupplement.supportsAds ? '支持投流' : '暂不支持投流' }}
                      </n-tag>
                      <span v-else>-</span>
                    </n-descriptions-item>
                    <n-descriptions-item v-if="auditSupplement.supportsAds" label="投流规则" :span="2">
                      {{ auditSupplement.adsRule || '暂未补充' }}
                    </n-descriptions-item>
                    <n-descriptions-item label="活动时间">
                      {{ auditSupplement.campaignTimeRemark || '-' }}
                    </n-descriptions-item>
                    <n-descriptions-item label="奖励说明" :span="2">
                      {{ auditSupplement.rewardRemark || '-' }}
                    </n-descriptions-item>
                    <n-descriptions-item label="参与要求" :span="2">
                      {{ auditSupplement.participationRequirements || '-' }}
                    </n-descriptions-item>
                    <n-descriptions-item label="推广话术" :span="2">
                      <n-log :log="auditSupplement.promotionScript || '-'" />
                    </n-descriptions-item>
                    <n-descriptions-item label="手卡素材" :span="2">
                      <div v-if="auditMaterialFiles.length" class="material-file-list">
                        <div v-for="file in auditMaterialFiles" :key="file" class="material-file-item">
                          <n-button
                            v-if="isHttpLink(file)"
                            text
                            type="primary"
                            tag="a"
                            :href="file"
                            target="_blank"
                          >
                            {{ file }}
                          </n-button>
                          <span v-else>{{ file }}</span>
                        </div>
                      </div>
                      <span v-else>-</span>
                    </n-descriptions-item>
                  </n-descriptions>
                </n-card>

                <n-card v-if="hasSampleThreshold" size="small" title="寄样门槛配置" style="margin-top: 16px;">
                  <n-descriptions label-placement="left" :column="2" size="small">
                    <n-descriptions-item label="30天销售额要求">
                      <span class="highlight-text">{{ auditSupplement.sampleThresholdSales !== undefined ? `≥ ${auditSupplement.sampleThresholdSales}` : '-' }}</span>
                    </n-descriptions-item>
                    <n-descriptions-item label="达人等级要求">
                      <n-tag v-if="auditSupplement.sampleThresholdLevel !== undefined" type="warning" size="small" round>
                        ≥ LV{{ auditSupplement.sampleThresholdLevel }}
                      </n-tag>
                      <span v-else>-</span>
                    </n-descriptions-item>
                    <n-descriptions-item label="补充门槛说明" :span="2">
                      {{ auditSupplement.sampleThresholdRemark || '暂无补充说明' }}
                    </n-descriptions-item>
                  </n-descriptions>
                </n-card>

                <n-card size="small" title="核心卖点" style="margin-top: 16px;">
                  <div v-if="materialSellingPoints.length" class="selling-point-list">
                    <n-tag v-for="point in materialSellingPoints" :key="point" type="info" size="small" round>
                      {{ point }}
                    </n-tag>
                  </div>
                  <n-empty v-else description="暂无可展示的推广卖点" />
                </n-card>

                <n-card size="small" title="建联话术示例" style="margin-top: 16px;">
                  <n-log :log="detail?.promotionMaterialPack?.outreachScript || '-'" />
                </n-card>

                <n-card size="small" title="短视频脚本" style="margin-top: 16px;">
                  <n-log :log="detail?.promotionMaterialPack?.shortVideoScript || '-'" />
                </n-card>
              </div>
            </n-tab-pane>

            <n-tab-pane name="logs" tab="操作日志">
              <div class="pane-content">
                <n-data-table :columns="logColumns" :data="operationLogs" size="small" :pagination="{ pageSize: 20, showSizePicker: true, pageSizes: [20, 50, 100] }" />
              </div>
            </n-tab-pane>
          </n-tabs>
        </div>
      </n-spin>
    </n-drawer-content>
  </n-drawer>
</template>

<script setup lang="ts">
import { notifyApiFailure } from '../../utils/requestError'
import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useMessage } from 'naive-ui';
import { DRAWER_WIDTH_PX } from '../../constants/ui';
import { convertActivityProductLink, getActivityProductDetail, getActivityProductOperationLogs, getActivityProductSkus, updateActivityProductDecision } from '../../api/activityProduct';
import { useAuthStore } from '../../stores/auth';
import { hasAccess } from '../../constants/rbac';
import { copyProductBriefWithLink } from './product-copy';
import { mergeLibraryDisplayFields, resolveProductLibraryDisplay, resolveProductLibraryReadiness } from './product-library-display';
import {
  buildOperationSummary,
  formatOperationTime,
  formatOperatorLabel,
  formatStatusFlow,
  getBizStatusLabel,
  getDecisionLevelLabel,
  getOperationTypeLabel,
  normalizeLogText,
  parseOperationPayload
} from './product-operation-log-display';
import { buildProductSampleContext } from '../sample/sample-context';
import ProductSpecSelector from './components/ProductSpecSelector.vue';

const props = defineProps<{
  show: boolean;
  activityId: string | number | null;
  productId: string | number | null;
  refreshKey?: number;
  pickMode?: boolean;
  libraryMode?: boolean;
  canPutIntoLibrary?: boolean;
}>();

const emit = defineEmits(['update:show', 'action']);
const message = useMessage();
const router = useRouter();
const authStore = useAuthStore();

const canDo = (action: string) => {
  const roles = authStore.roleCodes;
  if (roles.includes('admin')) return true;
  if (action === 'audit') return hasAccess(roles, ['biz_staff']);
  if (action === 'assign') return hasAccess(roles, ['biz_leader']);
  if (action === 'auditOwner') return hasAccess(roles, ['biz_leader']);
  if (action === 'decision') return hasAccess(roles, ['biz_staff']);
  if (action === 'promotion') return hasAccess(roles, ['channel_leader', 'channel_staff']);
  return true;
};

const loading = ref(false);
const skusLoading = ref(false);
const promotionGenerating = ref(false);
const briefCopying = ref(false);
const decisionSaving = ref(false);
const detail = ref<any>(null);
const productSkus = ref<any[]>([]);
const selectedSkuName = ref('');

const skuColumns = [
  { title: 'SKU ID', key: 'skuId', width: 180, ellipsis: { tooltip: true } },
  { title: '规格', key: 'skuName', minWidth: 160, ellipsis: { tooltip: true } },
  { title: '价格', key: 'priceText', width: 100 },
  { title: '库存', key: 'stock', width: 80 }
];
const operationLogs = ref<any[]>([]);
const decisionForm = ref<{ level: 'MAIN' | 'SECONDARY' | 'PAUSE' | 'DROP'; reason: string }>({
  level: 'MAIN',
  reason: ''
});

const statusMap: Record<string, number> = {
  PENDING_AUDIT: 1,
  APPROVED: 2,
  BOUND: 2,
  ASSIGNED: 3,
  LINKED: 4,
  FOLLOWING: 4
};

const currentStep = computed(() => statusMap[detail.value?.bizStatus || 'PENDING_AUDIT'] ?? 0);
const libraryDisplay = computed(() => resolveProductLibraryDisplay(detail.value));
const libraryReadiness = computed(() => resolveProductLibraryReadiness(detail.value));
const businessReady = computed(() => libraryDisplay.value.selectedToLibrary);

const formatPinnedUntil = (value?: string | null) => {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

const pinnedStatusText = computed(() => {
  if (!detail.value?.pinned) return '';
  return detail.value?.pinnedUntil
    ? `置顶至 ${formatPinnedUntil(detail.value.pinnedUntil)}`
    : '当前商品已置顶';
});

const canAssignDetail = computed(() =>
  businessReady.value &&
  ['APPROVED', 'BOUND', 'ASSIGNED'].includes(String(detail.value?.bizStatus || '')) &&
  canDo('assign')
);

const promotion = computed(() => detail.value?.promotion || {
  status: detail.value?.promotionLinkStatus || 'PENDING',
  statusLabel: detail.value?.promotionLinkStatusLabel || '未生成',
  link: detail.value?.promotionLink || null,
  generatedAt: detail.value?.promotionLinkGeneratedAt || null,
  expireAt: detail.value?.promotionLinkExpireAt || null,
  failReason: detail.value?.promotionLinkFailReason || null
});

const auditSupplement = computed(() => detail.value?.auditSupplement || {});
const auditSchemaVersion = computed(() => auditSupplement.value?.schemaVersion);
const hasAuditSupplement = computed(() => {
  const supplement = auditSupplement.value;
  return Object.keys(supplement).some((key) => {
    if (key === 'schemaVersion') return false;
    const value = supplement[key];
    return Array.isArray(value) ? value.length > 0 : value !== null && value !== undefined && String(value).trim() !== '';
  });
});

const hasSampleThreshold = computed(() => {
  const supplement = auditSupplement.value;
  return supplement?.sampleThresholdSales !== undefined || supplement?.sampleThresholdLevel !== undefined || supplement?.sampleThresholdRemark;
});

const materialSellingPoints = computed(() => {
  const supplementPoints = auditSupplement.value?.sellingPoints;
  if (Array.isArray(supplementPoints) && supplementPoints.length) {
    return supplementPoints;
  }
  const packPoints = detail.value?.promotionMaterialPack?.sellingPoints;
  return Array.isArray(packPoints) && packPoints.length ? packPoints : [];
});

const auditMaterialFiles = computed(() => {
  const files = auditSupplement.value?.materialFiles;
  return Array.isArray(files) && files.length ? files : [];
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

const getStatusLabel = getBizStatusLabel;
const normalizeText = normalizeLogText;
const decisionLabel = getDecisionLevelLabel;
const formatOperatorDisplay = formatOperatorLabel;

const operationLogContext = computed(() => ({
  assigneeName: detail.value?.assigneeName
}));

const buildOperationSummaryForRow = (row: any) => buildOperationSummary(row, operationLogContext.value);

const buildTimelineMeta = (type: string, operatorName: string, assigneeName: string) => {
  if (type === 'DECISION') {
    return `${operatorName} 更新了商品推进判断`;
  }
  if (type === 'ASSIGN_AUDIT') {
    return `${operatorName} 指定审核负责人：${assigneeName}`;
  }
  if (type === 'ASSIGN') {
    return `${operatorName} 完成分配，当前负责人：${assigneeName}`;
  }
  if (type === 'AUDIT') {
    return `${operatorName} 完成审核判断`;
  }
  if (type === 'TALENT_FOLLOW' || type === 'TALENT_FOLLOW_APPEND') {
    return `${operatorName} 更新了达人推进动作`;
  }
  if (type === 'PROMOTION_LINK' || type === 'LINK') {
    return `${operatorName} 完成推广链接准备`;
  }
  return `${operatorName} 记录了一次业务动作`;
};

const latestDecision = computed(() => {
  const log = operationLogs.value.find((item: any) => item?.operationType === 'DECISION');
  if (!log) return null;
  const payload = parseOperationPayload(log.operationPayload);
  return {
    level: payload.decisionLevel || '',
    label: payload.decisionLabel || decisionLabel(payload.decisionLevel),
    reason: normalizeText(log.operationRemark),
    operatorName: formatOperatorDisplay(log),
    time: log.createTime || '-'
  };
});

const decisionRiskSummary = computed(() => {
  const level = latestDecision.value?.level;
  const reason = latestDecision.value?.reason || '未填写原因';
  if (level === 'PAUSE') {
    return {
      type: 'warning' as const,
      title: '当前已标记为暂缓',
      content: `本轮不建议继续强推，建议先处理这条原因：${reason}`,
      actions: [
        '先补库存、佣金、素材或活动信息，再决定是否恢复推进',
        '暂时不要继续催达人或重复分发，避免无效消耗',
        '处理完成后重新保存一次判断，确认是否转回主推或次推'
      ]
    };
  }
  if (level === 'DROP') {
    return {
      type: 'error' as const,
      title: '当前已标记为放弃',
      content: `这款商品本轮建议停止投入，核心原因：${reason}`,
      actions: [
        '停止继续转链、催跟进或追加沟通，避免继续投入时间',
        '如已占用达人或招商精力，优先把资源切回更值得推进的商品',
        '如果后续条件发生明显变化，再重新进入详情页补一次新判断'
      ],
      closingNote: '当前建议按“停止继续推进、保留历史记录、等待条件变化后再重新评估”的方式收口。'
    };
  }
  return null;
});

const timelineEvents = computed(() => {
  return operationLogs.value.slice(0, 5).map((log: any, index: number) => {
    const payload = parseOperationPayload(log?.operationPayload);
    const type = String(log?.operationType || '');
    const assigneeName = payload.assigneeName || detail.value?.assigneeName || '未识别负责人';
    const operatorName = formatOperatorDisplay(log);
    const titles: Record<string, string> = {
      LIBRARY_ENTRY: '商品已加入商品库',
      ASSIGN_AUDIT: `商品已分配给审核人 ${assigneeName}`,
      ASSIGN: `商品已分配给 ${assigneeName}`,
      AUDIT: log?.afterStatus === 'REJECTED' ? '商品审核被拒绝' : '商品审核通过',
      DECISION: payload.eventLabel || '商品推进判断已更新',
      BIND_ACTIVITY: '商品活动绑定已更新',
      LINK: '商品推广链接已准备',
      PROMOTION_LINK: '商品推广链接已准备',
      TALENT_FOLLOW: '商品进入达人跟进',
      TALENT_FOLLOW_APPEND: '商品追加达人跟进',
      SYNC: '商品进入本地商品池'
    };
    return {
      key: `${log?.id || type}-${index}`,
      title: titles[type] || buildOperationSummaryForRow(log),
      time: log?.createTime || '-',
      meta: buildTimelineMeta(type, operatorName, assigneeName),
      remark: normalizeText(log?.operationRemark),
      markerClass: `marker-${String(type || 'default').toLowerCase()}`
    };
  });
});

const logColumns = [
  { title: '时间', key: 'createTime', width: 160, render: (row: any) => formatOperationTime(row?.createTime) },
  { title: '事件', key: 'operationTypeLabel', width: 140, render: (row: any) => getOperationTypeLabel(row?.operationType) },
  { title: '状态变更', key: 'statusFlow', width: 180, render: (row: any) => formatStatusFlow(row?.beforeStatus, row?.afterStatus) || '—' },
  { title: '结果', key: 'success', width: 80, render: (row: any) => (row.success === false ? '失败' : '成功') },
  { title: '操作人', key: 'operatorName', width: 140, render: (row: any) => formatOperatorDisplay(row) },
  { title: '说明', key: 'operationRemark', minWidth: 260, render: (row: any) => buildOperationSummaryForRow(row) }
];

const promotionColumns = [
  { title: '时间', key: 'time', width: 160 },
  { title: '场景', key: 'scene', width: 100 },
  { title: '短链', key: 'shortLink', minWidth: 200 },
  { title: '推广链接', key: 'promoteLink', minWidth: 200, ellipsis: { tooltip: true } }
];

const loadSkus = async () => {
  if (!props.activityId || !props.productId) {
    productSkus.value = [];
    return;
  }
  skusLoading.value = true;
  try {
    const res: any = await getActivityProductSkus(props.activityId, props.productId);
    productSkus.value = Array.isArray(res?.data) ? res.data : [];
  } catch {
    productSkus.value = [];
  } finally {
    skusLoading.value = false;
  }
};

const fetchData = async () => {
  if (!props.activityId || !props.productId) return;
  loading.value = true;
  try {
    const detailRes: any = await getActivityProductDetail(props.activityId, props.productId);
    detail.value = mergeLibraryDisplayFields(detailRes?.data || {});
    const logsRes: any = await getActivityProductOperationLogs(props.activityId, props.productId, { page: 1, size: 50 });
    operationLogs.value = logsRes?.data?.records || [];
    await loadSkus();
  } catch (error) {
    notifyApiFailure(error, message, { fallbackMessage: '加载商品详情失败' });
  } finally {
    loading.value = false;
  }
};

watch(() => props.show, (val) => {
  if (val) fetchData();
  else {
    detail.value = null;
    operationLogs.value = [];
    productSkus.value = [];
    selectedSkuName.value = '';
  }
});

watch(() => props.refreshKey, () => {
  if (props.show) fetchData();
});

const updateShow = (val: boolean) => emit('update:show', val);

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

const decisionTagType = (level?: string) => {
  if (level === 'MAIN') return 'success';
  if (level === 'SECONDARY') return 'info';
  if (level === 'PAUSE') return 'warning';
  if (level === 'DROP') return 'error';
  return 'default';
};

const handleAction = (action: string) => {
  emit('action', { action, row: detail.value });
};

const saveDecision = async () => {
  if (!props.activityId || !props.productId) {
    message.warning('商品信息不完整，暂不可保存判断');
    return;
  }
  if (!businessReady.value) {
    message.warning('请先完成审核并进入商品库后，再保存推进判断');
    return;
  }
  const reason = decisionForm.value.reason.trim();
  if (!reason) {
    message.warning('请填写推进判断原因');
    return;
  }
  decisionSaving.value = true;
  try {
    await updateActivityProductDecision(props.activityId, props.productId, {
      decisionLevel: decisionForm.value.level,
      reason
    });
    message.success('推进判断已保存');
    decisionForm.value.reason = '';
    await fetchData();
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '推进判断保存失败，请稍后重试' });
  } finally {
    decisionSaving.value = false;
  }
};

const goToOrders = (productId: string) => {
  if (!productId) return;
  router.push({ path: '/orders', query: { productId: String(productId) } });
  emit('update:show', false);
};

const goToSampleApply = () => {
  const context = buildProductSampleContext({
    ...(detail.value || {}),
    id: detail.value?.id || detail.value?.productRecordId || props.productId
  });
  if (!context.query.productId) {
    message.warning('商品信息不完整，暂不可发起内部寄样');
    return;
  }
  router.push({ path: '/sample/apply', query: context.query });
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
    message.warning('内容已生成，但浏览器未允许写入剪贴板，请手动复制');
  }
};

const copyPromotionLink = async () => {
  if (!props.activityId || !props.productId) {
    message.warning('商品信息不完整，暂不可生成推广链接');
    return;
  }
  if (!businessReady.value) {
    message.warning('请先完成审核并进入商品库后，再生成推广链接');
    return;
  }
  if (promotion.value?.link) {
    await copyText(promotion.value.link);
    return;
  }
  promotionGenerating.value = true;
  try {
    const res: any = await convertActivityProductLink(props.activityId, props.productId, { scene: 'PRODUCT_DETAIL' });
    const data = res?.data || {};
    const link = data.promoteLink || data.promotionUrl || data.shortLink;
    if (!link) {
      message.warning('推广链接生成成功，但未返回可复制链接');
      await fetchData();
      return;
    }
    detail.value = {
      ...(detail.value || {}),
      promotion: {
        ...(detail.value?.promotion || {}),
        ...data,
        status: 'READY',
        statusLabel: data.statusLabel || '已生成',
        link,
        failReason: null
      },
      promotionLink: link,
      promotionLinkStatus: 'READY',
      promotionLinkStatusLabel: data.statusLabel || '已生成',
      promotionLinkFailReason: null
    };
    try {
      await navigator.clipboard.writeText(link);
      message.success('推广链接已复制');
    } catch {
      message.warning('推广链接已生成，但浏览器未允许写入剪贴板，请手动复制');
    }
    await fetchData();
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '推广链接生成失败，请稍后重试' });
  } finally {
    promotionGenerating.value = false;
  }
};

const copyProductBrief = async () => {
  if (!props.activityId || !props.productId) {
    message.warning('商品信息不完整，暂不可生成讲解');
    return;
  }
  if (!businessReady.value) {
    message.warning('请先完成审核并进入商品库后，再复制讲解');
    return;
  }
  if (!detail.value) {
    message.warning('商品详情尚未加载完成');
    return;
  }

  briefCopying.value = true;
  let clipboardWriteFailed = false;
  try {
    const convertLinkForBriefCopy = (
      activityId: string | number,
      productId: string | number,
      data: { scene: 'PRODUCT_LIBRARY' | 'PRODUCT_DETAIL' | 'TALENT_SHARE' | 'SAMPLE_DESK' }
    ) => convertActivityProductLink(activityId, productId, data, { suppressErrorNotice: true });
    const result = await copyProductBriefWithLink({
      item: detail.value,
      activityId: props.activityId,
      productId: props.productId,
      scene: 'PRODUCT_DETAIL',
      convertLink: convertLinkForBriefCopy,
      writeText: async (text: string) => {
        try {
          await navigator.clipboard.writeText(text);
        } catch {
          clipboardWriteFailed = true;
        }
      }
    });

    if (result.link && result.responseData) {
      const data = result.responseData;
      detail.value = {
        ...(detail.value || {}),
        promotion: {
          ...(detail.value?.promotion || {}),
          ...data,
          status: 'READY',
          statusLabel: data.statusLabel || '已生成',
          link: result.link,
          failReason: null
        },
        promotionLink: result.link,
        promotionLinkStatus: 'READY',
        promotionLinkStatusLabel: data.statusLabel || '已生成',
        promotionLinkFailReason: null
      };
      await fetchData();
    }

    if (clipboardWriteFailed) {
      message.warning('讲解已生成，但浏览器未允许写入剪贴板，请手动复制');
    } else if (result.linkGenerationFailed) {
      message.error('短链生成失败，已复制讲解（不含短链）');
    } else {
      message.success('讲解 + 短链已复制');
    }
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '讲解复制失败，请稍后重试' });
  } finally {
    briefCopying.value = false;
  }
};

const formatPercent = (value?: number | string | null) => {
  if (value === null || value === undefined || value === '') return '-';
  if (typeof value === 'string' && value.includes('%')) return value;
  return `${value}%`;
};

const isHttpLink = (value?: string | null) => /^https?:\/\//i.test(normalizeText(value));
</script>

<style scoped>
.drawer-header { padding-bottom: 8px; border-bottom: 1px solid var(--border-color); }
.drawer-title { font-size: var(--text-xl); font-weight: 600; color: var(--text-primary); }
.detail-container { display: flex; flex-direction: column; gap: 16px; }
.action-bar { padding: 12px; background: var(--bg-sidebar); border-radius: var(--radius-md); border: 1px dashed var(--border-color); }
.library-visibility-alert { margin-top: -4px; }
.pane-content { padding: 16px 4px; }
.biz-steps { margin: 20px 0 32px; padding: 4px 2px 0; }
.biz-steps :deep(.n-step) {
  transition: opacity .2s ease, transform .2s ease;
}
.biz-steps :deep(.n-step .n-step-content-header__title) {
  transition: color .2s ease, font-weight .2s ease;
}
.biz-steps :deep(.n-step--finish-status .n-step-splitor) {
  background-color: rgba(255, 92, 109, 0.42);
}
.biz-steps :deep(.n-step--process-status .n-step-indicator) {
  transform: scale(1.06);
  box-shadow:
    0 0 0 1px rgba(255, 92, 109, 0.95),
    0 0 0 6px rgba(255, 92, 109, 0.12);
}
.biz-steps :deep(.n-step--process-status .n-step-indicator-slot__index) {
  font-weight: 700;
}
.biz-steps :deep(.n-step--process-status .n-step-content-header__title) {
  color: #1f2937;
  font-weight: 700;
}
.biz-steps :deep(.n-step--wait-status .n-step-content-header__title) {
  color: #c0c4cc;
}
.biz-steps :deep(.n-step--wait-status .n-step-splitor) {
  background-color: rgba(192, 196, 204, 0.5);
}
.status-alert { margin-bottom: 20px; }
.decision-card { display: flex; flex-direction: column; gap: 14px; }
.decision-current { display: flex; align-items: center; gap: 10px; min-width: 0; }
.decision-reason { color: var(--text-secondary); font-size: var(--text-sm); line-height: 1.5; }
.decision-risk-content { display: flex; flex-direction: column; gap: 8px; }
.decision-risk-actions { margin: 0; padding-left: 18px; color: var(--text-secondary); line-height: 1.6; }
.decision-risk-note { color: var(--text-primary); font-weight: 600; line-height: 1.6; }
.decision-form { display: flex; flex-direction: column; gap: 10px; }
.decision-actions { display: flex; justify-content: flex-end; }
.basic-info-grid { display: flex; gap: 24px; }
.info-image { position: relative; flex-shrink: 0; border: 1px solid var(--border-color); border-radius: var(--radius-md); overflow: hidden; padding: 4px; background: var(--bg-card); }
.detail-pin-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 1;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  background: #d92d20;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.4;
  box-shadow: 0 2px 8px rgba(217, 45, 32, 0.22);
}
.info-main { flex: 1; }
.product-title-row { display: flex; align-items: center; gap: 8px; min-width: 0; }
.product-title { font-size: var(--text-base); font-weight: 600; color: var(--text-primary); margin: 0; line-height: 1.4; }
.highlight-text { color: var(--color-primary); font-weight: 600; }
.summary-panel { margin-top: 16px; padding: 16px; background: var(--bg-sidebar); border-radius: var(--radius-md); }
.timeline-list { display: flex; flex-direction: column; gap: 14px; }
.timeline-item { display: flex; gap: 12px; align-items: flex-start; }
.timeline-marker { width: 10px; height: 10px; border-radius: var(--radius-full); margin-top: 6px; background: var(--text-muted); flex-shrink: 0; }
.marker-assign { background: var(--color-info); }
.marker-audit { background: var(--color-warning); }
.marker-decision { background: var(--color-success); }
.marker-promotion_link,
.marker-link { background: var(--color-success); }
.marker-talent_follow,
.marker-talent_follow_append { background: var(--color-primary); }
.marker-sync { background: var(--text-secondary); }
.timeline-body { flex: 1; min-width: 0; }
.timeline-title-row { display: flex; justify-content: space-between; gap: 16px; align-items: baseline; }
.timeline-title { font-weight: 600; color: var(--text-primary); }
.timeline-time { color: var(--text-muted); font-size: var(--text-xs); white-space: nowrap; }
.timeline-meta { color: var(--text-secondary); margin-top: 2px; font-size: var(--text-sm); }
.timeline-remark { color: var(--text-muted); margin-top: 4px; font-size: var(--text-xs); }
.promotion-link-box { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.promotion-link-text { word-break: break-all; color: var(--text-primary); }
.section-title { margin: 16px 0 12px; }
.material-actions { margin-bottom: 16px; }
.material-file-list { display: flex; flex-direction: column; gap: 6px; word-break: break-all; }
.material-file-item { line-height: 1.6; }
.selling-point-list { display: flex; flex-wrap: wrap; gap: 8px; }
</style>
