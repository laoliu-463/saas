<template>
  <div class="douyin-integration">
    <n-card title="抖店联调" :bordered="false">
      <n-tabs type="line" animated>
        <n-tab-pane name="token" tab="Token 管理">
          <n-space vertical size="large">
            <n-card size="small" title="Token 状态">
              <n-space>
                <n-input v-model:value="appId" placeholder="appId，可留空使用默认配置" style="width: 260px" clearable />
                <n-button type="primary" :loading="loading.status" @click="checkTokenStatus">查询状态</n-button>
                <n-button type="warning" :loading="loading.refresh" @click="refreshToken">刷新 Token</n-button>
              </n-space>

              <n-grid :cols="24" :x-gap="12" style="margin-top: 16px">
                <n-grid-item :span="8">
                  <n-select
                    v-model:value="createForm.grantType"
                    :options="grantTypeOptions"
                    placeholder="授权类型"
                  />
                </n-grid-item>
                <n-grid-item :span="12">
                  <n-input
                    v-model:value="createForm.code"
                    clearable
                    placeholder="请输入授权码 code（authorization_code 模式必填）"
                  />
                </n-grid-item>
                <n-grid-item :span="4">
                  <n-button type="success" block :loading="loading.create" @click="createToken">创建 Token</n-button>
                </n-grid-item>
              </n-grid>

              <n-alert
                v-if="tokenStatusAlert"
                :type="tokenStatusAlert.type"
                :show-icon="false"
                style="margin-top: 16px"
              >
                {{ tokenStatusAlert.text }}
              </n-alert>

              <n-descriptions v-if="tokenStatus" bordered label-placement="left" style="margin-top: 16px">
                <n-descriptions-item label="App ID">{{ tokenStatus.appId || '-' }}</n-descriptions-item>
                <n-descriptions-item label="Access Token">{{ tokenStatus.hasAccessToken ? '已获取' : '未获取' }}</n-descriptions-item>
                <n-descriptions-item label="Refresh Token">{{ tokenStatus.hasRefreshToken ? '已获取' : '未获取' }}</n-descriptions-item>
                <n-descriptions-item label="Access Token 摘要">{{ tokenStatus.maskedAccessToken || '-' }}</n-descriptions-item>
                <n-descriptions-item label="Refresh Token 摘要">{{ tokenStatus.maskedRefreshToken || '-' }}</n-descriptions-item>
                <n-descriptions-item label="到期时间">{{ formatExpireTime(tokenStatus.tokenExpireAtEpochSeconds) }}</n-descriptions-item>
                <n-descriptions-item label="即将过期">{{ tokenStatus.tokenExpiringSoon ? '是' : '否' }}</n-descriptions-item>
                <n-descriptions-item label="需重新授权">{{ tokenStatus.reauthorizeRequired ? '是' : '否' }}</n-descriptions-item>
              </n-descriptions>

              <n-alert type="info" :show-icon="false" style="margin-top: 16px">
                创建 Token 用于抖店授权链路。若失败会展示可读性更高的错误提示，便于快速排查授权码、授权类型或权限问题。
              </n-alert>
            </n-card>
          </n-space>
        </n-tab-pane>

        <n-tab-pane name="debug" tab="活动联调">
          <n-space vertical size="large">
            <n-card size="small" title="联调入口">
              <n-space>
                <n-button type="primary" :loading="loading.activity" @click="testActivityList">活动列表</n-button>
                <n-button type="info" :loading="loading.productActivities" @click="testProductActivities">活动商品列表</n-button>
              </n-space>

              <n-alert
                v-if="debugAlert"
                :type="debugAlert.type"
                :show-icon="false"
                style="margin-top: 16px"
              >
                {{ debugAlert.text }}
              </n-alert>

              <n-input
                v-model:value="apiResult"
                type="textarea"
                :rows="14"
                readonly
                placeholder="接口响应会显示在这里"
                style="margin-top: 16px"
              />
            </n-card>
          </n-space>
        </n-tab-pane>
      </n-tabs>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useMessage } from 'naive-ui';
import {
  createDouyinToken,
  getDouyinActivityTest,
  getDouyinProductActivities,
  getDouyinTokenStatus,
  refreshDouyinToken,
  type DouyinDebugResult,
  type DouyinTokenStatus
} from '../../api/douyin';

type AlertType = 'info' | 'success' | 'warning' | 'error';

const message = useMessage();
const appId = ref('');
const tokenStatus = ref<DouyinTokenStatus | null>(null);
const apiResult = ref('');
const debugResult = ref<DouyinDebugResult | null>(null);

const createForm = reactive({
  grantType: 'authorization_code',
  code: ''
});

const grantTypeOptions = [
  { label: 'authorization_code（推荐）', value: 'authorization_code' },
  { label: 'authorization_self', value: 'authorization_self' }
];

const loading = reactive({
  status: false,
  refresh: false,
  create: false,
  activity: false,
  productActivities: false
});

const formatExpireTime = (epochSeconds?: number) => {
  if (!epochSeconds || epochSeconds <= 0) {
    return '-';
  }
  return new Date(epochSeconds * 1000).toLocaleString('zh-CN', { hour12: false });
};

const buildDebugAlert = (result: DouyinDebugResult | null): { type: AlertType; text: string } | null => {
  if (!result) {
    return null;
  }
  if (result.status === 'success') {
    return { type: 'success', text: '接口已成功命中后端与抖店链路。' };
  }
  if (result.errorCode === 50002 && result.message?.includes('招商团长')) {
    return { type: 'warning', text: '当前后端链路正常，但抖店侧缺少“招商团长”业务授权，属于外部权限不足，不是前端或后端路由问题。' };
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

const renderDebugResult = (result: DouyinDebugResult) => {
  apiResult.value = JSON.stringify(result, null, 2);
};

const extractErrorMessage = (error: any, fallback: string) => {
  return error?.response?.data?.msg || error?.message || fallback;
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
    message.success('Token 状态已更新');
  } catch (error: any) {
    message.error(extractErrorMessage(error, 'Token 状态查询失败'));
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
    message.success('Token 创建成功，已写入缓存并刷新状态');
    createForm.code = '';
  } catch (error: any) {
    const raw = extractErrorMessage(error, 'Token 创建失败');
    message.error(mapCreateTokenError(raw));
  } finally {
    loading.create = false;
  }
};

const refreshToken = async () => {
  loading.refresh = true;
  try {
    tokenStatus.value = await refreshDouyinToken(appId.value || undefined);
    message.success('Token 已刷新');
  } catch (error: any) {
    message.error(extractErrorMessage(error, 'Token 刷新失败'));
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
    message.success(result.status === 'success' ? '活动列表联调成功' : '活动列表已命中后端，结果见下方');
  } catch (error: any) {
    debugResult.value = null;
    message.error(extractErrorMessage(error, '活动列表联调失败'));
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
    message.success(result.status === 'success' ? '活动商品联调成功' : '活动商品接口已命中后端，结果见下方');
  } catch (error: any) {
    debugResult.value = null;
    message.error(extractErrorMessage(error, '活动商品联调失败'));
  } finally {
    loading.productActivities = false;
  }
};

onMounted(() => {
  checkTokenStatus();
});
</script>

<style scoped>
.douyin-integration {
  min-height: 100%;
}
</style>
