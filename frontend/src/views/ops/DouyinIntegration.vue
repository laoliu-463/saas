<template>
  <div class="douyin-integration app-page">
    <PageHeader
      title="抖店联调"
      description="汇总 real-pre 精选联盟链路状态，供授权、活动商品、SKU、订单同步和看板排查使用。"
    >
      <template #actions>
        <n-input v-model:value="appId" placeholder="appId，可留空" clearable class="app-id-input" />
        <n-input v-model:value="activityId" placeholder="活动ID" clearable class="activity-id-input" />
        <n-input v-model:value="productId" placeholder="商品ID（SKU探针）" clearable class="product-id-input" />
        <n-button type="primary" :loading="loading.fullCheck" @click="runFullCheck">
          一键刷新联调状态
        </n-button>
      </template>
    </PageHeader>

    <n-alert type="warning" :show-icon="false" class="scope-alert app-page-alert">
      当前 real-pre 用于精选联盟 / 团长链路取证；店铺商品与店铺订单接口不纳入本页联调进度。
    </n-alert>

    <div class="status-grid">
      <div v-for="item in orderedChecks" :key="item.key" class="status-card app-status-card" :class="item.status">
        <div class="status-card-head">
          <n-tag :type="statusTagType(item.status)" size="small">{{ statusLabel(item.status) }}</n-tag>
          <span v-if="item.updatedAt" class="status-time">{{ item.updatedAt }}</span>
        </div>
        <div class="status-title">{{ item.title }}</div>
        <div class="status-detail">{{ item.detail }}</div>
      </div>
    </div>

    <n-tabs type="line" animated class="integration-tabs">
      <n-tab-pane name="summary" tab="联调总览">
        <div class="summary-layout">
          <div class="summary-panel">
            <div class="panel-title">最近一次执行</div>
            <n-descriptions v-if="lastRun" bordered label-placement="left" :column="2">
              <n-descriptions-item label="执行时间">{{ lastRun.checkedAt }}</n-descriptions-item>
              <n-descriptions-item label="活动ID">{{ lastRun.activityId || '-' }}</n-descriptions-item>
              <n-descriptions-item label="活动商品">{{ lastRun.productCount }} 条</n-descriptions-item>
              <n-descriptions-item label="订单总数">{{ lastRun.orderTotal }}</n-descriptions-item>
              <n-descriptions-item label="今日订单">{{ lastRun.dashboardOrders }}</n-descriptions-item>
              <n-descriptions-item label="今日 GMV">¥{{ lastRun.dashboardAmount }}</n-descriptions-item>
              <n-descriptions-item label="商品 SKU">{{ lastRun.skuStatus }}</n-descriptions-item>
            </n-descriptions>
            <n-empty v-else description="尚未执行联调状态刷新" />
          </div>

          <div class="summary-panel">
            <div class="panel-title">执行证据</div>
            <n-input
              v-model:value="latestSummary"
              type="textarea"
              :rows="14"
              readonly
              placeholder="一键刷新后显示脱敏后的执行摘要"
            />
          </div>
        </div>
      </n-tab-pane>

      <n-tab-pane name="token" tab="Token 管理">
        <div class="tool-panel">
          <div class="tool-actions">
            <n-button type="success" :loading="loading.oauth" @click="startDouyinOAuth">去抖店授权</n-button>
            <n-button secondary :loading="loading.powerManage" @click="openDouyinPowerManage">官方授权管理</n-button>
            <n-button type="primary" :loading="loading.status" @click="checkTokenStatus">查询状态</n-button>
            <n-button type="warning" :loading="loading.refresh" @click="refreshToken">刷新 Token</n-button>
          </div>

          <div class="create-token-row">
            <n-select
              v-model:value="createForm.grantType"
              :options="grantTypeOptions"
              placeholder="授权类型"
              class="grant-select"
            />
            <n-input
              v-model:value="createForm.code"
              clearable
              placeholder="请输入授权码 code"
            />
            <n-button type="success" :loading="loading.create" @click="createToken">创建 Token</n-button>
          </div>

          <n-alert
            v-if="tokenStatusAlert"
            :type="tokenStatusAlert.type"
            :show-icon="false"
            class="inline-alert"
          >
            {{ tokenStatusAlert.text }}
          </n-alert>

          <n-descriptions v-if="tokenStatus" bordered label-placement="left" class="token-descriptions">
            <n-descriptions-item label="App ID">{{ tokenStatus.appId || '-' }}</n-descriptions-item>
            <n-descriptions-item label="Access Token">{{ tokenStatus.hasAccessToken ? '已获取' : '未获取' }}</n-descriptions-item>
            <n-descriptions-item label="Refresh Token">{{ tokenStatus.hasRefreshToken ? '已获取' : '未获取' }}</n-descriptions-item>
            <n-descriptions-item label="Access Token 摘要">{{ tokenStatus.maskedAccessToken || '-' }}</n-descriptions-item>
            <n-descriptions-item label="Refresh Token 摘要">{{ tokenStatus.maskedRefreshToken || '-' }}</n-descriptions-item>
            <n-descriptions-item label="到期时间">{{ formatExpireTime(tokenStatus.tokenExpireAtEpochSeconds) }}</n-descriptions-item>
            <n-descriptions-item label="即将过期">{{ tokenStatus.tokenExpiringSoon ? '是' : '否' }}</n-descriptions-item>
            <n-descriptions-item label="需重新授权">{{ tokenStatus.reauthorizeRequired ? '是' : '否' }}</n-descriptions-item>
          </n-descriptions>
        </div>
      </n-tab-pane>

      <n-tab-pane name="debug" tab="接口探针">
        <div class="tool-panel">
          <div class="tool-actions">
            <n-button type="primary" :loading="loading.activity" @click="testActivityList">活动列表</n-button>
            <n-button type="info" :loading="loading.productActivities" @click="testProductActivities">活动商品活动列表</n-button>
            <n-button type="info" :loading="loading.activityProductList" @click="testActivityProductList">指定活动商品</n-button>
            <n-button type="warning" :loading="loading.orderSettlements" @click="testOrderSettlements">1603 查询团长订单（结算口径）</n-button>
            <n-button type="warning" :loading="loading.productSkuProbe" @click="probeProductSkus">商品 SKU 探针</n-button>
          </div>

          <div class="settlement-query-row">
            <n-input
              v-model:value="settlementForm.orderIds"
              clearable
              placeholder="订单号列表，逗号分隔；留空则走时间窗"
            />
            <n-select
              v-model:value="settlementForm.timeType"
              :options="settlementTimeTypeOptions"
              class="settlement-time-type"
            />
            <n-input
              v-model:value="settlementForm.startTime"
              clearable
              placeholder="开始时间 yyyy-MM-dd HH:mm:ss"
            />
            <n-input
              v-model:value="settlementForm.endTime"
              clearable
              placeholder="结束时间 yyyy-MM-dd HH:mm:ss"
            />
          </div>

          <n-alert
            v-if="debugAlert"
            :type="debugAlert.type"
            :show-icon="false"
            class="inline-alert"
          >
            {{ debugAlert.text }}
          </n-alert>

          <n-input
            v-model:value="apiResult"
            type="textarea"
            :rows="16"
            readonly
            placeholder="接口响应会显示在这里"
          />
        </div>
      </n-tab-pane>
    </n-tabs>
  </div>
</template>

<script setup lang="ts">
import { notifyApiFailure } from '../../utils/requestError'
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { useMessage } from 'naive-ui';
import PageHeader from '../../components/PageHeader.vue';
import { getActivityProducts } from '../../api/activityProduct';
import { getMetrics } from '../../api/data';
import { pickDashboardTrack } from '../data/dashboard-metrics';
import { getOrders, syncOrders } from '../../api/order';
import {
  createDouyinToken,
  getDouyinAuthorizeUrl,
  getDouyinActivityProductList,
  getDouyinActivityTest,
  getDouyinInstitutionInfo,
  getDouyinOrderSettlements,
  getDouyinProductActivities,
  getDouyinTokenStatus,
  postDouyinRawProbe,
  refreshDouyinToken,
  type DouyinDebugResult,
  type DouyinTokenStatus
} from '../../api/douyin';

type AlertType = 'info' | 'success' | 'warning' | 'error';
type CheckStatus = 'pending' | 'running' | 'success' | 'warning' | 'error';
type CheckKey = 'token' | 'institution' | 'products' | 'productSkus' | 'orders' | 'dashboard';

interface IntegrationCheck {
  key: CheckKey;
  title: string;
  detail: string;
  status: CheckStatus;
  updatedAt?: string;
}

interface LastRunSummary {
  checkedAt: string;
  activityId: string;
  productCount: number;
  orderTotal: number;
  dashboardOrders: number;
  dashboardAmount: string;
  skuStatus: string;
}

const message = useMessage();
const route = useRoute();
const REAL_PRE_REQUEST_TIMEOUT_MS = 120_000;
const appId = ref('');
const activityId = ref('3916506');
const productId = ref('3810562766247428542');
const tokenStatus = ref<DouyinTokenStatus | null>(null);
const apiResult = ref('');
const latestSummary = ref('');
const debugResult = ref<DouyinDebugResult | null>(null);
const lastRun = ref<LastRunSummary | null>(null);

const createForm = reactive({
  grantType: 'authorization_code',
  code: ''
});

const settlementForm = reactive({
  orderIds: '',
  timeType: 'settle',
  startTime: '',
  endTime: ''
});

const grantTypeOptions = [
  { label: 'authorization_code', value: 'authorization_code' }
];

const settlementTimeTypeOptions = [
  { label: '按更新时间', value: 'update' },
  { label: '按结算时间', value: 'settle' }
];

const loading = reactive({
  fullCheck: false,
  status: false,
  refresh: false,
  create: false,
  oauth: false,
  powerManage: false,
  activity: false,
  productActivities: false,
  activityProductList: false,
  orderSettlements: false,
  productSkuProbe: false
});

const checks = reactive<Record<CheckKey, IntegrationCheck>>({
  token: {
    key: 'token',
    title: 'Token 待检查',
    detail: '查询 access token 与 refresh token 是否完整。',
    status: 'pending'
  },
  institution: {
    key: 'institution',
    title: '授权主体待检查',
    detail: '确认当前授权主体可以访问抖店开放平台。',
    status: 'pending'
  },
  products: {
    key: 'products',
    title: '活动商品待刷新',
    detail: '刷新指定活动商品并回写业务快照。',
    status: 'pending'
  },
  orders: {
    key: 'orders',
    title: '订单同步待执行',
    detail: '同步最近 30 分钟团长侧订单。',
    status: 'pending'
  },
  dashboard: {
    key: 'dashboard',
    title: 'Dashboard 待读取',
    detail: '使用 createTime 口径读取真实订单指标。',
    status: 'pending'
  },
  productSkus: {
    key: 'productSkus',
    title: '商品 SKU 待探针',
    detail: '验证精选联盟 /buyin/productSkus/v2 返回结构。',
    status: 'pending'
  }
});

const orderedChecks = computed(() => [
  checks.token,
  checks.institution,
  checks.products,
  checks.productSkus,
  checks.orders,
  checks.dashboard
]);

const defaultCheckState: Record<CheckKey, Pick<IntegrationCheck, 'title' | 'detail'>> = {
  token: {
    title: 'Token 待检查',
    detail: '查询 access token 与 refresh token 是否完整。'
  },
  institution: {
    title: '授权主体待检查',
    detail: '确认当前授权主体可以访问抖店开放平台。'
  },
  products: {
    title: '活动商品待刷新',
    detail: '刷新指定活动商品并回写业务快照。'
  },
  orders: {
    title: '订单同步待执行',
    detail: '同步最近 30 分钟团长侧订单。'
  },
  dashboard: {
    title: 'Dashboard 待读取',
    detail: '使用 createTime 口径读取真实订单指标。'
  },
  productSkus: {
    title: '商品 SKU 待探针',
    detail: '验证精选联盟 /buyin/productSkus/v2 返回结构。'
  }
};

const formatExpireTime = (epochSeconds?: number) => {
  if (!epochSeconds || epochSeconds <= 0) {
    return '-';
  }
  return new Date(epochSeconds * 1000).toLocaleString('zh-CN', { hour12: false });
};

const nowText = () => new Date().toLocaleTimeString('zh-CN', { hour12: false });

const statusLabel = (status: CheckStatus) => {
  if (status === 'success') return '通过';
  if (status === 'warning') return '待补';
  if (status === 'error') return '异常';
  if (status === 'running') return '执行中';
  return '待检查';
};

const statusTagType = (status: CheckStatus) => {
  if (status === 'success') return 'success';
  if (status === 'warning') return 'warning';
  if (status === 'error') return 'error';
  if (status === 'running') return 'info';
  return 'default';
};

const setCheck = (key: CheckKey, status: CheckStatus, title: string, detail: string) => {
  checks[key].status = status;
  checks[key].title = title;
  checks[key].detail = detail;
  checks[key].updatedAt = nowText();
};

const resetChecks = () => {
  (Object.keys(defaultCheckState) as CheckKey[]).forEach((key) => {
    checks[key].status = 'pending';
    checks[key].title = defaultCheckState[key].title;
    checks[key].detail = defaultCheckState[key].detail;
    checks[key].updatedAt = undefined;
  });
};

const markChecksBlockedByToken = () => {
  const blockedDetail = 'Token 不完整或需要重新授权，已跳过后续真实联调步骤。';
  (['institution', 'products', 'productSkus', 'orders', 'dashboard'] as CheckKey[]).forEach((key) => {
    setCheck(key, 'warning', `${defaultCheckState[key].title}（已跳过）`, blockedDetail);
  });
};

const unwrapApiData = <T = any>(response: any): T => response?.data ?? response;

const normalizeNumber = (value: any) => {
  const number = Number(value ?? 0);
  return Number.isFinite(number) ? number : 0;
};

const maskText = (value?: string | null) => {
  const text = String(value || '').trim();
  if (text.length <= 8) return text || '-';
  return `${text.slice(0, 4)}...${text.slice(-4)}`;
};

const formatLocalDateTime = (date: Date) => {
  const pad2 = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}`;
};

const findDeepValue = (input: any, keys: string[], seen = new Set<any>()): any => {
  if (!input || typeof input !== 'object' || seen.has(input)) return undefined;
  seen.add(input);
  for (const key of keys) {
    if (Object.prototype.hasOwnProperty.call(input, key)) {
      return input[key];
    }
  }
  for (const value of Object.values(input)) {
    const found = findDeepValue(value, keys, seen);
    if (found !== undefined) return found;
  }
  return undefined;
};

const findProductArray = (input: any, seen = new Set<any>()): any[] => {
  if (!input || seen.has(input)) return [];
  if (Array.isArray(input)) {
    const first = input[0];
    if (!first || typeof first !== 'object') return input;
    if ('product_id' in first || 'productId' in first || 'title' in first || 'productName' in first) return input;
  }
  if (typeof input !== 'object') return [];
  seen.add(input);
  for (const value of Object.values(input)) {
    const found = findProductArray(value, seen);
    if (found.length) return found;
  }
  return [];
};

const findActivityId = (input: any): string | null => {
  const value = findDeepValue(input, ['activityId', 'activity_id']);
  return value ? String(value) : null;
};

const findProductId = (input: any): string | null => {
  const value = findDeepValue(input, ['productId', 'product_id']);
  return value ? String(value) : null;
};

const findSkuCount = (input: any): number => {
  const container = findDeepValue(input, ['skus', 'sku_map', 'skuMap', 'sku_list', 'skuList', 'list']);
  if (Array.isArray(container)) return container.length;
  if (container && typeof container === 'object') return Object.keys(container).length;
  return 0;
};

const isUpstreamSuccess = (result: any) => {
  const code = findDeepValue(result, ['code', 'err_no', 'errorCode']);
  if (code === undefined || code === null || code === '') {
    return result?.status === 'success';
  }
  return ['10000', '0', '200'].includes(String(code));
};

const buildDebugAlert = (result: DouyinDebugResult | null): { type: AlertType; text: string } | null => {
  if (!result) {
    return null;
  }
  if (result.status === 'success') {
    return { type: 'success', text: '接口已成功命中后端与抖店链路。' };
  }
  if (result.errorCode === 50002 && result.message?.includes('招商团长')) {
    return { type: 'warning', text: '当前后端链路正常，但抖店侧缺少“招商团长”业务授权。' };
  }
  if (result.status === 'failed') {
    return {
      type: 'error',
      text: result.message || '接口已到达后端，但第三方业务调用失败，请查看下方原始返回。'
    };
  }
  return null;
};

const tokenStatusAlert = computed(() => {
  if (!tokenStatus.value) {
    return null;
  }
  if (tokenStatus.value.reauthorizeRequired) {
    return { type: 'warning' as const, text: '当前 Token 需要重新授权。' };
  }
  if (tokenStatus.value.hasAccessToken && tokenStatus.value.hasRefreshToken) {
    return { type: 'success' as const, text: 'Token 状态正常，可以继续进行抖店接口联调。' };
  }
  return { type: 'info' as const, text: 'Token 尚未准备完整，请先创建 Token 或完成授权。' };
});

const debugAlert = computed(() => buildDebugAlert(debugResult.value));

const renderDebugResult = (result: any) => {
  apiResult.value = JSON.stringify(result, null, 2);
};

const extractErrorMessage = (error: any, fallback: string) => {
  return error?.response?.data?.msg || error?.msg || error?.message || fallback;
};

const mapCreateTokenError = (rawMsg: string) => {
  const msgText = rawMsg.toLowerCase();
  if (msgText.includes('authorization code') || msgText.includes('code is invalid') || msgText.includes('expired')) {
    return '授权码无效或已过期，请重新授权后重试。';
  }
  if (msgText.includes('grant_type') || msgText.includes('grant type')) {
    return '授权类型不正确，请检查 grantType。';
  }
  if (msgText.includes('permission') || msgText.includes('scope')) {
    return '当前应用权限不足，请检查开放平台权限和范围配置。';
  }
  return rawMsg;
};

const checkTokenStatus = async () => {
  loading.status = true;
  try {
    tokenStatus.value = await getDouyinTokenStatus(appId.value || undefined);
    if (tokenStatus.value.hasAccessToken && tokenStatus.value.hasRefreshToken && !tokenStatus.value.reauthorizeRequired) {
      setCheck('token', 'success', 'Token 正常', `Access / Refresh Token 均存在，到期时间 ${formatExpireTime(tokenStatus.value.tokenExpireAtEpochSeconds)}。`);
    } else {
      setCheck('token', 'warning', 'Token 需处理', 'Token 不完整或需要重新授权。');
    }
    message.success('Token 状态已更新');
  } catch (error: any) {
    setCheck('token', 'error', 'Token 查询异常', extractErrorMessage(error, 'Token 状态查询失败'));
    notifyApiFailure(error, message, { fallbackMessage: 'Token 状态查询失败' });
  } finally {
    loading.status = false;
  }
};

const createToken = async () => {
  if (createForm.grantType === 'authorization_code' && !createForm.code.trim()) {
    message.warning('authorization_code 模式下，授权码 code 不能为空');
    return;
  }

  loading.create = true;
  try {
    tokenStatus.value = await createDouyinToken({
      appId: appId.value || undefined,
      grantType: createForm.grantType,
      code: createForm.code.trim() || undefined
    });
    setCheck('token', 'success', 'Token 正常', 'Token 创建成功，已写入缓存。');
    message.success('Token 创建成功，已写入缓存并刷新状态');
    createForm.code = '';
  } catch (error: any) {
    const raw = extractErrorMessage(error, 'Token 创建失败');
    setCheck('token', 'error', 'Token 创建失败', mapCreateTokenError(raw));
    notifyApiFailure(error, message, { fallbackMessage: mapCreateTokenError(raw) });
  } finally {
    loading.create = false;
  }
};

const startDouyinOAuth = async () => {
  loading.oauth = true;
  try {
    const result = await getDouyinAuthorizeUrl(appId.value || undefined);
    if (!result.authorizeUrl) {
      message.error('未获取到抖店授权地址');
      return;
    }
    window.location.href = result.authorizeUrl;
  } catch (error: any) {
    setCheck('token', 'error', '授权地址生成失败', extractErrorMessage(error, '抖店授权地址生成失败'));
    notifyApiFailure(error, message, { fallbackMessage: '抖店授权地址生成失败' });
  } finally {
    loading.oauth = false;
  }
};

const openDouyinPowerManage = async () => {
  loading.powerManage = true;
  try {
    const result = await getDouyinAuthorizeUrl(appId.value || undefined);
    const targetUrl = result.powerManageUrl || 'https://buyin.jinritemai.com/dashboard/institution/power-manage';
    window.location.href = targetUrl;
  } catch (error: any) {
    setCheck('token', 'error', '授权管理页打开失败', extractErrorMessage(error, '授权管理页打开失败'));
    notifyApiFailure(error, message, { fallbackMessage: '授权管理页打开失败' });
  } finally {
    loading.powerManage = false;
  }
};

const refreshToken = async () => {
  loading.refresh = true;
  try {
    tokenStatus.value = await refreshDouyinToken(appId.value || undefined);
    setCheck('token', 'success', 'Token 正常', `Token 已刷新，到期时间 ${formatExpireTime(tokenStatus.value.tokenExpireAtEpochSeconds)}。`);
    message.success('Token 已刷新');
  } catch (error: any) {
    setCheck('token', 'error', 'Token 刷新异常', extractErrorMessage(error, 'Token 刷新失败'));
    notifyApiFailure(error, message, { fallbackMessage: 'Token 刷新失败' });
  } finally {
    loading.refresh = false;
  }
};

const testActivityList = async () => {
  loading.activity = true;
  try {
    const result = await getDouyinActivityTest(appId.value || undefined);
    debugResult.value = result;
    renderDebugResult(result);
    const detectedActivityId = findActivityId(result);
    if (detectedActivityId) activityId.value = detectedActivityId;
    message.success(result.status === 'success' ? '活动列表联调成功' : '活动列表已命中后端，结果见下方');
  } catch (error: any) {
    debugResult.value = null;
    notifyApiFailure(error, message, { fallbackMessage: '活动列表联调失败' });
  } finally {
    loading.activity = false;
  }
};

const testProductActivities = async () => {
  loading.productActivities = true;
  try {
    const result = await getDouyinProductActivities({ appId: appId.value || undefined });
    debugResult.value = result;
    renderDebugResult(result);
    message.success(result.status === 'success' ? '活动商品活动列表联调成功' : '活动商品接口已命中后端，结果见下方');
  } catch (error: any) {
    debugResult.value = null;
    notifyApiFailure(error, message, { fallbackMessage: '活动商品联调失败' });
  } finally {
    loading.productActivities = false;
  }
};

const testActivityProductList = async () => {
  if (!activityId.value.trim()) {
    message.warning('请先填写活动ID');
    return;
  }
  loading.activityProductList = true;
  try {
    const result = await getDouyinActivityProductList({
      appId: appId.value || undefined,
      activityId: activityId.value.trim(),
      count: 20
    });
    debugResult.value = result;
    renderDebugResult(result);
    const productCount = findProductArray(result?.remoteResponse).length;
    message.success(`指定活动商品联调完成，当前返回 ${productCount} 条`);
  } catch (error: any) {
    debugResult.value = null;
    notifyApiFailure(error, message, { fallbackMessage: '指定活动商品联调失败' });
  } finally {
    loading.activityProductList = false;
  }
};

const buildSettlementParams = () => {
  const orderIds = settlementForm.orderIds.trim();
  if (orderIds) {
    return {
      appId: appId.value || undefined,
      size: 20,
      cursor: '0',
      timeType: settlementForm.timeType,
      orderIds
    };
  }
  const startTime = settlementForm.startTime.trim();
  const endTime = settlementForm.endTime.trim();
  if (!startTime || !endTime) {
    throw new Error('请输入 orderIds，或同时填写开始时间和结束时间');
  }
  return {
    appId: appId.value || undefined,
    size: 20,
    cursor: '0',
    timeType: settlementForm.timeType,
    startTime,
    endTime
  };
};

const testOrderSettlements = async () => {
  loading.orderSettlements = true;
  try {
    const result = await getDouyinOrderSettlements(buildSettlementParams());
    debugResult.value = result;
    renderDebugResult(result);
    const orderCount = Array.isArray(findDeepValue(result?.remoteResponse, ['orders'])) ? findDeepValue(result?.remoteResponse, ['orders']).length : 0;
    if (settlementForm.orderIds.trim()) {
      message.success(`1603 结算口径查询完成，当前返回 ${orderCount} 条`);
    } else {
      message.success(`1603 结算口径时间窗查询完成，当前返回 ${orderCount} 条`);
    }
  } catch (error: any) {
    debugResult.value = null;
    notifyApiFailure(error, message, { fallbackMessage: '1603 结算口径查询失败' });
  } finally {
    loading.orderSettlements = false;
  }
};

const buildProductSkuProbePayload = (id = productId.value) => {
  return {
    appId: appId.value || undefined,
    method: 'buyin.productSkus.v2',
    product_id: String(id || '').trim()
  };
};

const probeProductSkus = async () => {
  if (!productId.value.trim()) {
    message.warning('请先填写商品ID');
    return;
  }
  loading.productSkuProbe = true;
  try {
    const result = await postDouyinRawProbe(buildProductSkuProbePayload());
    debugResult.value = result;
    renderDebugResult(result);
    const skuCount = findSkuCount(result);
    if (isUpstreamSuccess(result) && skuCount > 0) {
      setCheck('productSkus', 'success', '商品 SKU 已验证', `精选联盟 SKU 返回 ${skuCount} 条。`);
    } else if (isUpstreamSuccess(result)) {
      setCheck('productSkus', 'warning', '商品 SKU 返回为空', '上游返回成功，但未解析到 SKU 明细。');
    } else {
      setCheck('productSkus', 'error', '商品 SKU 探针异常', String(findDeepValue(result, ['message', 'msg']) || '请查看原始返回。'));
    }
  } catch (error: any) {
    debugResult.value = null;
    setCheck('productSkus', 'error', '商品 SKU 探针异常', extractErrorMessage(error, '商品 SKU 探针失败'));
    notifyApiFailure(error, message, { fallbackMessage: '商品 SKU 探针失败' });
  } finally {
    loading.productSkuProbe = false;
  }
};

const runFullCheck = async () => {
  loading.fullCheck = true;
  latestSummary.value = '';
  lastRun.value = null;
  resetChecks();
  const summary: Record<string, any> = {};
  const initialActivityId = activityId.value.trim();
  let activeCheckKey: CheckKey | null = null;

  try {
    activeCheckKey = 'token';
    setCheck('token', 'running', 'Token 检查中', '正在读取 Token 状态。');
    tokenStatus.value = await getDouyinTokenStatus(appId.value || undefined);
    const tokenOk = tokenStatus.value.hasAccessToken && tokenStatus.value.hasRefreshToken && !tokenStatus.value.reauthorizeRequired;
    setCheck(
      'token',
      tokenOk ? 'success' : 'warning',
      tokenOk ? 'Token 正常' : 'Token 需处理',
      tokenOk
        ? `Access / Refresh Token 均存在，到期时间 ${formatExpireTime(tokenStatus.value.tokenExpireAtEpochSeconds)}。`
        : 'Token 不完整或需要重新授权。'
    );
    summary.token = {
      appId: maskText(tokenStatus.value.appId),
      hasAccessToken: tokenStatus.value.hasAccessToken, // example
      hasRefreshToken: tokenStatus.value.hasRefreshToken, // example
      tokenExpiringSoon: tokenStatus.value.tokenExpiringSoon,
      reauthorizeRequired: tokenStatus.value.reauthorizeRequired
    };
    if (!tokenOk) {
      markChecksBlockedByToken();
      activeCheckKey = null;
      latestSummary.value = JSON.stringify({
        ...summary,
        skipped: 'Token 不完整或需要重新授权，后续真实联调步骤已跳过。'
      }, null, 2);
      message.warning('Token 不可用，已跳过后续真实联调步骤');
      return;
    }

    activeCheckKey = 'institution';
    setCheck('institution', 'running', '授权主体检查中', '正在调用 buyin.institutionInfo。');
    const institutionResult = await getDouyinInstitutionInfo(appId.value || undefined);
    const institutionOk = isUpstreamSuccess(institutionResult);
    setCheck(
      'institution',
      institutionOk ? 'success' : 'error',
      institutionOk ? '授权主体正常' : '授权主体异常',
      institutionOk ? '上游返回成功，当前授权主体可用。' : String(findDeepValue(institutionResult, ['message', 'msg']) || '授权主体查询失败。')
    );
    summary.institution = {
      endpoint: institutionResult.endpoint,
      status: institutionResult.status,
      code: findDeepValue(institutionResult, ['code', 'err_no', 'errorCode']),
      logId: findDeepValue(institutionResult, ['logId', 'log_id'])
    };

    activeCheckKey = 'products';
    setCheck('products', 'running', '活动商品读取中', `正在读取活动 ${initialActivityId || '自动探测'} 的商品快照。`);
    const activityResult = await getDouyinActivityTest(appId.value || undefined);
    const detectedActivityId = findActivityId(activityResult);
    const selectedActivityId = initialActivityId || detectedActivityId || '3916506';
    activityId.value = selectedActivityId;
    const productResult = await getDouyinActivityProductList({
      appId: appId.value || undefined,
      activityId: selectedActivityId,
      count: 20
    });
    const rawProductCount = findProductArray(productResult?.remoteResponse).length;
    const businessProductResponse = await getActivityProducts(selectedActivityId, {
      appId: appId.value || undefined,
      page: 1,
      size: 5,
      count: 5
    }, {
      timeout: REAL_PRE_REQUEST_TIMEOUT_MS
    });
    const businessProductData = unwrapApiData<any>(businessProductResponse);
    const businessProductCount = Array.isArray(businessProductData?.items)
      ? businessProductData.items.length
      : findProductArray(businessProductData).length;
    setCheck(
      'products',
      businessProductCount > 0 ? 'success' : 'warning',
      businessProductCount > 0 ? '活动商品已刷新' : '活动商品刷新为空',
      `上游样本 ${rawProductCount} 条，业务视图 ${businessProductCount} 条。`
    );
    summary.products = {
      activityId: selectedActivityId,
      rawProductCount,
      businessProductCount,
      businessTotal: normalizeNumber(businessProductData?.total)
    };

    activeCheckKey = 'productSkus';
    const detectedProductId =
      productId.value.trim() ||
      findProductId(businessProductData) ||
      findProductId(productResult?.remoteResponse);
    if (detectedProductId) {
      productId.value = detectedProductId;
      setCheck('productSkus', 'running', '商品 SKU 探针中', `正在调用 /buyin/productSkus/v2，productId=${detectedProductId}。`);
      const skuResult = await postDouyinRawProbe(buildProductSkuProbePayload(detectedProductId));
      const skuCount = findSkuCount(skuResult);
      setCheck(
        'productSkus',
        isUpstreamSuccess(skuResult) && skuCount > 0 ? 'success' : 'warning',
        isUpstreamSuccess(skuResult) && skuCount > 0 ? '商品 SKU 已验证' : '商品 SKU 待补样本',
        isUpstreamSuccess(skuResult)
          ? `精选联盟 SKU 返回 ${skuCount} 条。`
          : String(findDeepValue(skuResult, ['message', 'msg']) || 'SKU 探针未返回成功。')
      );
      summary.productSkus = {
        productId: detectedProductId,
        endpoint: skuResult.endpoint,
        code: findDeepValue(skuResult, ['code', 'err_no', 'errorCode']),
        skuCount
      };
    } else {
      setCheck('productSkus', 'warning', '商品 SKU 已跳过', '当前活动商品样本未解析到 productId。');
      summary.productSkus = { skipped: 'missing productId' };
    }

    activeCheckKey = 'orders';
    setCheck('orders', 'running', '订单同步中', '正在同步最近 30 分钟团长侧订单。');
    const end = new Date();
    const start = new Date(end.getTime() - 30 * 60 * 1000);
    const syncResponse = await syncOrders(formatLocalDateTime(start), formatLocalDateTime(end), {
      timeout: REAL_PRE_REQUEST_TIMEOUT_MS
    });
    const syncData = unwrapApiData<any>(syncResponse);
    const failed = normalizeNumber(syncData?.failed ?? syncData?.failedCount);
    const fetched = normalizeNumber(syncData?.fetched ?? syncData?.total ?? syncData?.totalFetched);
    const created = normalizeNumber(syncData?.created);
    const updated = normalizeNumber(syncData?.updated);
    setCheck(
      'orders',
      failed === 0 ? 'success' : 'warning',
      failed === 0 ? '订单同步成功' : '订单同步有失败',
      `窗口 ${formatLocalDateTime(start)} ~ ${formatLocalDateTime(end)}，拉取 ${fetched}，新增 ${created}，更新 ${updated}，失败 ${failed}。`
    );
    const ordersResponse = await getOrders({ page: 1, size: 5 }, {
      timeout: REAL_PRE_REQUEST_TIMEOUT_MS
    });
    const ordersData = unwrapApiData<any>(ordersResponse);
    summary.orders = {
      sync: syncData,
      total: normalizeNumber(ordersData?.total)
    };

    activeCheckKey = 'dashboard';
    setCheck('dashboard', 'running', 'Dashboard 读取中', '正在读取 createTime 口径指标。');
    const metricsResponse = await getMetrics({ timeField: 'createTime' }, {
      timeout: REAL_PRE_REQUEST_TIMEOUT_MS
    });
    const metricsData = pickDashboardTrack(metricsResponse, 'createTime');
    const dashboardOrders = normalizeNumber(metricsData?.todayOrderCount ?? metricsData?.totalOrders);
    const dashboardAmount = String(metricsData?.todayGmv ?? metricsData?.totalAmount ?? '0.00');
    setCheck(
      'dashboard',
      dashboardOrders > 0 ? 'success' : 'warning',
      dashboardOrders > 0 ? 'Dashboard 已读取真实订单' : 'Dashboard 当前无订单',
      `createTime 今日订单 ${dashboardOrders}，今日 GMV ${dashboardAmount}。`
    );
    summary.dashboard = {
      todayOrderCount: dashboardOrders,
      todayGmv: dashboardAmount
    };

    lastRun.value = {
      checkedAt: new Date().toLocaleString('zh-CN', { hour12: false }),
      activityId: selectedActivityId,
      productCount: businessProductCount,
      orderTotal: normalizeNumber(ordersData?.total),
      dashboardOrders,
      dashboardAmount,
      skuStatus: checks.productSkus.title
    };
    activeCheckKey = null;
    latestSummary.value = JSON.stringify(summary, null, 2);
    message.success('抖店联调状态已刷新');
  } catch (error: any) {
    if (activeCheckKey) {
      setCheck(
        activeCheckKey,
        'error',
        `${checks[activeCheckKey].title.replace(/检查中|刷新中|同步中|读取中|探针中/, '').trim() || '联调步骤'}异常`,
        extractErrorMessage(error, '联调状态刷新失败')
      );
    }
    latestSummary.value = JSON.stringify({
      ...summary,
      error: extractErrorMessage(error, '联调状态刷新失败')
    }, null, 2);
    notifyApiFailure(error, message, { fallbackMessage: '联调状态刷新失败' });
  } finally {
    loading.fullCheck = false;
  }
};

onMounted(() => {
  const oauthResult = String(route.query.oauth || '');
  if (oauthResult === 'success') {
    message.success('抖店授权成功，正在刷新 Token 状态');
  } else if (oauthResult === 'failed') {
    message.error('抖店授权失败，请重新发起授权或使用手动 code 兜底');
  }
  checkTokenStatus();
});
</script>

<style scoped>
.douyin-integration {
  max-width: var(--page-max-width);
}

.app-id-input {
  width: 220px;
}

.activity-id-input {
  width: 180px;
}

.product-id-input {
  width: 240px;
}

.scope-alert {
  margin-bottom: var(--spacing-md);
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-lg);
}

.status-card {
  box-shadow: var(--shadow-card);
}

.status-card.success {
  border-color: rgba(7, 193, 96, 0.28);
}

.status-card.warning {
  border-color: rgba(255, 149, 0, 0.38);
}

.status-card.error {
  border-color: rgba(250, 81, 81, 0.34);
}

.status-card.running {
  border-color: rgba(59, 130, 246, 0.3);
}

.status-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}

.status-time {
  font-size: var(--text-xs);
  color: var(--text-muted);
}

.status-title {
  font-size: var(--text-lg);
  line-height: 1.35;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.status-detail {
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--text-secondary);
}

.integration-tabs {
  background: transparent;
}

.summary-layout {
  display: grid;
  grid-template-columns: minmax(360px, 0.9fr) minmax(420px, 1.1fr);
  gap: var(--spacing-md);
}

.summary-panel,
.tool-panel {
  padding: 20px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
}

.panel-title {
  margin-bottom: 14px;
  font-size: var(--text-base);
  font-weight: 700;
  color: var(--text-primary);
}

.tool-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.create-token-row {
  display: grid;
  grid-template-columns: 190px minmax(260px, 1fr) auto;
  gap: 10px;
  margin-bottom: 14px;
}

.settlement-query-row {
  display: grid;
  grid-template-columns: minmax(280px, 1.4fr) 150px minmax(220px, 1fr) minmax(220px, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}

.settlement-time-type {
  min-width: 140px;
}

.grant-select {
  min-width: 180px;
}

.inline-alert {
  margin-bottom: 14px;
}

.token-descriptions {
  margin-top: 14px;
}

@media (max-width: 960px) {
  .douyin-integration {
    padding: var(--spacing-md);
  }

  .summary-layout,
  .create-token-row,
  .settlement-query-row {
    grid-template-columns: 1fr;
  }

  .app-id-input,
  .activity-id-input,
  .product-id-input {
    width: 100%;
  }
}
</style>
