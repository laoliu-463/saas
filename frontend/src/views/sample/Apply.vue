<template>
  <div class="sample-apply app-page">
    <n-card :title="applyCardTitle" :bordered="false" class="app-panel apply-card">
      <n-form ref="formRef" :model="formData" :rules="rules" label-placement="top">
        <n-grid :cols="24" :x-gap="12">
          <n-form-item-gi :span="24" label="达人搜索">
            <n-space style="width: 100%;" wrap>
              <n-input v-model:value="talentQuery.keyword" placeholder="昵称/达人号" clearable style="width: 220px" />
              <n-input v-model:value="talentQuery.region" placeholder="地域" clearable style="width: 140px" />
              <n-input-number v-model:value="talentQuery.minFans" :min="0" placeholder="最低粉丝" style="width: 140px" />
              <n-input-number v-model:value="talentQuery.maxFans" :min="0" placeholder="最高粉丝" style="width: 140px" />
              <n-button type="primary" :loading="loadingTalents" @click="fetchTalents(1)">搜索我的达人</n-button>
            </n-space>
          </n-form-item-gi>

          <n-form-item-gi :span="24" path="talentId" label="选择达人">
            <div style="width: 100%; margin-bottom: 8px; color: var(--text-secondary); font-size: var(--text-xs);">
              仅支持为我已认领的达人申请寄样。
            </div>
            <n-data-table
              :columns="talentColumns"
              :data="talentRows"
              :loading="loadingTalents"
              size="small"
              :pagination="talentPagination"
              @update:page="(p:number)=>fetchTalents(p)"
            />
          </n-form-item-gi>

          <n-form-item-gi :span="24" v-if="selectedTalent" label="已选达人">
            <n-space align="center">
              <n-avatar :src="resolveSafeAvatarUrl(selectedTalent.avatarUrl)" round :size="48" />
              <div>
                <div style="font-weight: 600">{{ selectedTalent.nickname }}</div>
                <div style="color: var(--text-secondary); font-size: var(--text-xs);">
                  粉丝 {{ formatFans(selectedTalent.fansCount) }} · 评分 {{ selectedTalent.creditScore ?? '-' }} · {{ selectedTalent.region || '-' }}
                </div>
              </div>
            </n-space>
          </n-form-item-gi>

          <n-form-item-gi :span="24" label="选择商品" path="productId">
            <n-select
              v-model:value="formData.productId"
              filterable
              placeholder="搜索商品"
              :options="productOptions"
              :loading="loadingProducts"
              :disabled="productLocked"
              @search="handleSearchProduct"
              :clearable="!productLocked"
              remote
            >
              <template #empty>暂无匹配商品</template>
            </n-select>
          </n-form-item-gi>

          <n-form-item-gi :span="8" label="寄样数量" path="quantity">
            <n-input-number v-model:value="formData.quantity" :min="1" :max="10" />
          </n-form-item-gi>

          <n-form-item-gi :span="16" label="申请理由" path="reason">
            <n-input v-model:value="formData.reason" placeholder="例如：短视频测品" />
          </n-form-item-gi>

          <n-form-item-gi :span="8" label="收货人" path="receiverName">
            <n-input v-model:value="formData.receiverName" placeholder="请输入收货人" />
          </n-form-item-gi>
          <n-form-item-gi :span="8" label="手机号" path="receiverPhone">
            <n-input v-model:value="formData.receiverPhone" placeholder="请输入手机号" />
          </n-form-item-gi>
          <n-form-item-gi :span="24" label="收货地址" path="receiverAddress">
            <n-input v-model:value="formData.receiverAddress" placeholder="请输入完整收货地址" />
          </n-form-item-gi>

          <n-form-item-gi :span="24" label="补充说明" path="remark">
            <n-input v-model:value="formData.remark" type="textarea" placeholder="可填写发货偏好、沟通备注等" />
          </n-form-item-gi>

          <n-form-item-gi :span="24">
            <n-button type="primary" block @click="handleSubmit" :loading="submitting">提交申请</n-button>
          </n-form-item-gi>
        </n-grid>
      </n-form>

      <n-button quaternary block @click="$router.back()">返回寄样列表</n-button>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue';
import { NButton, useDialog, useMessage } from 'naive-ui';
import { useRoute, useRouter } from 'vue-router';
import { checkSampleEligibility, createSample, searchSampleProducts } from '../../api/sample';
import { getTalentPage, getTalentShippingAddress } from '../../api/talent';
import { useAuthStore } from '../../stores/auth';
import { resolveSafeAvatarUrl } from '../../utils/media';
import { useDebouncedFn } from '../../utils/debounce';
import {
  INTERNAL_QUICK_SAMPLE_SOURCE,
  MANUAL_SAMPLE_APPLY_SOURCE,
  buildSampleRemark,
  isMainlandMobile,
  mergeLockedOption,
  normalizeSampleApplySource,
  type SampleSelectOption
} from './sample-context';

const message = useMessage();
const route = useRoute();
const router = useRouter();
const dialog = useDialog();
const authStore = useAuthStore();

const formRef = ref();
const submitting = ref(false);

const formData = reactive({
  talentId: '',
  talentNickname: '',
  talentFansCount: null as number | null,
  talentCreditScore: null as number | null,
  talentMainCategory: '',
  productId: null as string | null,
  quantity: 1,
  receiverName: '',
  receiverPhone: '',
  receiverAddress: '',
  reason: '',
  remark: ''
});

const rules = {
  talentId: { required: true, message: '请选择达人', trigger: 'change' },
  productId: { required: true, message: '请选择商品', trigger: 'change' },
  quantity: { type: 'number', required: true, message: '请输入寄样数量', trigger: 'blur' },
  reason: { required: true, message: '请输入申请理由', trigger: 'blur' },
  receiverName: { required: true, message: '请输入收货人', trigger: 'blur' },
  receiverPhone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string) => isMainlandMobile(value),
      message: '请输入 11 位手机号',
      trigger: ['blur', 'input']
    }
  ],
  receiverAddress: { required: true, message: '请输入收货地址', trigger: 'blur' }
};

const talentQuery = reactive({
  keyword: '',
  region: '',
  minFans: null as number | null,
  maxFans: null as number | null,
  page: 1,
  size: 8,
  total: 0
});

const loadingTalents = ref(false);
const talentRows = ref<any[]>([]);
const selectedTalent = ref<any | null>(null);

const talentPagination = computed(() => ({
  page: talentQuery.page,
  pageSize: talentQuery.size,
  itemCount: talentQuery.total,
  pageSlot: 7
}));

const talentColumns = [
  {
    title: '达人',
    key: 'nickname'
  },
  {
    title: '粉丝',
    key: 'fansCount',
    render: (row: any) => formatFans(row.fansCount)
  },
  {
    title: '评分',
    key: 'creditScore'
  },
  {
    title: '地区',
    key: 'region'
  },
  {
    title: '操作',
    key: 'actions',
    render: (row: any) => h(NButton, {
      size: 'small',
      type: formData.talentId === row.talentId ? 'success' : 'primary',
      ghost: formData.talentId === row.talentId,
      onClick: () => chooseTalent(row)
    }, { default: () => formData.talentId === row.talentId ? '已选择' : '选择' })
  }
];

const productOptions = ref<{ label: string; value: string }[]>([]);
const loadingProducts = ref(false);
const productLocked = ref(false);
const lockedProductOption = ref<SampleSelectOption | null>(null);
const applySource = ref(MANUAL_SAMPLE_APPLY_SOURCE);
const applyCardTitle = computed(() =>
  applySource.value === INTERNAL_QUICK_SAMPLE_SOURCE ? '内部寄样申请' : '申请寄样'
);

const getRouteString = (key: string) => {
  const value = route.query[key]
  if (Array.isArray(value)) return String(value[0] || '')
  return typeof value === 'string' ? value : ''
}

const getRouteNumber = (key: string) => {
  const raw = getRouteString(key)
  if (!raw) return null
  const value = Number(raw)
  return Number.isFinite(value) ? value : null
}

const getSubmitRemark = () => buildSampleRemark({
  reason: formData.reason,
  receiverName: formData.receiverName,
  receiverPhone: formData.receiverPhone,
  receiverAddress: formData.receiverAddress,
  extraRemark: formData.remark
});

const applyRouteContext = async () => {
  applySource.value = normalizeSampleApplySource(getRouteString('applySource'))

  const productId = getRouteString('productId')
  if (productId) {
    const productLabel = getRouteString('productLabel') || productId
    formData.productId = productId
    productLocked.value = true
    lockedProductOption.value = { label: productLabel, value: productId }
    productOptions.value = mergeLockedOption(productOptions.value, lockedProductOption.value)
  }

  const talentId = getRouteString('talentId')
  if (talentId) {
    const talentRow = {
      talentId,
      nickname: getRouteString('talentNickname') || talentId,
      fansCount: getRouteNumber('talentFansCount'),
      creditScore: getRouteNumber('talentCreditScore'),
      region: '',
      mainCategory: getRouteString('talentMainCategory'),
      avatarUrl: ''
    }
    selectedTalent.value = talentRow
    formData.talentId = talentRow.talentId
    formData.talentNickname = talentRow.nickname
    formData.talentFansCount = talentRow.fansCount
    formData.talentCreditScore = talentRow.creditScore
    formData.talentMainCategory = talentRow.mainCategory
  }

  formData.receiverName = getRouteString('receiverName') || formData.receiverName
  formData.receiverPhone = getRouteString('receiverPhone') || formData.receiverPhone
  formData.receiverAddress = getRouteString('receiverAddress') || formData.receiverAddress

  const talentUuid = getRouteString('talentUuid')
  if (talentUuid && !formData.receiverName) {
    try {
      const address = await getTalentShippingAddress(talentUuid)
      formData.receiverName = address?.recipientName || formData.receiverName
      formData.receiverPhone = address?.recipientPhone || formData.receiverPhone
      formData.receiverAddress = address?.recipientAddress || formData.receiverAddress
    } catch {
      // 地址可选，失败时不阻断寄样申请
    }
  }
}

const fetchProductOptions = async (query: string) => {
  loadingProducts.value = true;
  try {
    const res = await searchSampleProducts({ keyword: query || undefined, size: 20 });
    const payload: any = (res as any)?.data ?? (res as any);
    const records = payload?.records || [];
    productOptions.value = records.map((p: any) => ({
      label: p.productName || p.name || p.title || p.productId || p.id,
      value: p.id || p.productId
    }));
    productOptions.value = mergeLockedOption(productOptions.value, lockedProductOption.value);
  } finally {
    loadingProducts.value = false;
  }
};

const debouncedFetchProductOptions = useDebouncedFn((query: string) => {
  fetchProductOptions(query).catch((error: any) => {
    message.error(error?.message || '搜索商品失败');
  });
}, 250);

const handleSearchProduct = (query: string) => {
  if (productLocked.value) return;
  debouncedFetchProductOptions(query);
};

const fetchTalents = async (page = 1) => {
  loadingTalents.value = true;
  try {
    talentQuery.page = page;
    const res = await getTalentPage({
      view: 'MY_TALENTS',
      keyword: talentQuery.keyword || undefined,
      region: talentQuery.region || undefined,
      minFans: talentQuery.minFans ?? undefined,
      maxFans: talentQuery.maxFans ?? undefined,
      page: talentQuery.page,
      size: talentQuery.size
    });
    const pageData: any = (res as any)?.data ?? (res as any) ?? {};
    talentRows.value = (pageData.records || []).map((item: any) => ({
      id: item.id,
      talentId: item.douyinUid || item.douyinNo || item.uid,
      nickname: item.nickname,
      fansCount: item.fansCount,
      creditScore: item.creditScore,
      region: item.ipLocation || item.region,
      mainCategory: item.mainCategory,
      avatarUrl: item.avatarUrl
    }));
    talentQuery.total = pageData.total || 0;
  } catch (error: any) {
    talentRows.value = [];
    talentQuery.total = 0;
    message.error(error?.message || '获取达人失败');
  } finally {
    loadingTalents.value = false;
  }
};

const chooseTalent = async (row: any) => {
  selectedTalent.value = row;
  formData.talentId = row.talentId;
  formData.talentNickname = row.nickname;
  formData.talentFansCount = row.fansCount;
  formData.talentCreditScore = row.creditScore;
  formData.talentMainCategory = row.mainCategory;
  formData.receiverName = row.receiverName || formData.receiverName;
  formData.receiverPhone = row.receiverPhone || formData.receiverPhone;
  formData.receiverAddress = row.receiverAddress || formData.receiverAddress;
  if (row.id) {
    try {
      const address = await getTalentShippingAddress(row.id);
      formData.receiverName = address?.recipientName || formData.receiverName;
      formData.receiverPhone = address?.recipientPhone || formData.receiverPhone;
      formData.receiverAddress = address?.recipientAddress || formData.receiverAddress;
    } catch {
      // ignore
    }
  }
};

const canSubmit = computed(() =>
  !authStore.userInfo?.roleCodes?.includes('VISITOR') && !authStore.userInfo?.roleCodes?.includes('READONLY')
);

const doSubmit = async () => {
  submitting.value = true;
  try {
    const remark = getSubmitRemark();
    await createSample({
      talentId: formData.talentId,
      talentNickname: formData.talentNickname,
      talentFansCount: formData.talentFansCount,
      talentCreditScore: formData.talentCreditScore,
      talentMainCategory: formData.talentMainCategory,
      productId: formData.productId,
      quantity: formData.quantity,
      recipientName: formData.receiverName,
      recipientPhone: formData.receiverPhone,
      recipientAddress: formData.receiverAddress,
      applySource: applySource.value,
      remark
    });
    message.success('寄样申请已提交，状态为待审核');
    router.push('/sample');
  } catch (error: any) {
    message.error(error?.message || '提交失败');
  } finally {
    submitting.value = false;
  }
};

const openEligibilityWarning = (eligibility: any) => {
  const reasons = Array.isArray(eligibility?.reasons) ? eligibility.reasons : []
  const standardBits = [
    eligibility?.min30DaySales ? `标准销售额 ${Number(eligibility.min30DaySales / 100).toFixed(2)}` : '',
    eligibility?.minLevel ? `标准等级 ${eligibility.minLevel}` : ''
  ].filter(Boolean)
  dialog.warning({
    title: '达人暂未满足默认寄样标准',
    content: `${standardBits.join('，') || '已触发默认寄样标准校验'}。当前校验结果：${reasons.join('；')}。请先在备注中填写申请原因，再重新提交。`,
    positiveText: '我知道了'
  })
}

const handleSubmit = async () => {
  if (!canSubmit.value) {
    message.warning('当前角色无权提交寄样申请');
    return;
  }
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  let eligibility: any = null
  try {
    const remark = getSubmitRemark()
    const eligibilityResponse = await checkSampleEligibility({
      talentId: formData.talentId,
      talentNickname: formData.talentNickname,
      talentFansCount: formData.talentFansCount,
      talentCreditScore: formData.talentCreditScore,
      talentMainCategory: formData.talentMainCategory,
      productId: formData.productId,
      quantity: formData.quantity,
      recipientName: formData.receiverName,
      recipientPhone: formData.receiverPhone,
      recipientAddress: formData.receiverAddress,
      applySource: applySource.value,
      remark
    })
    eligibility = eligibilityResponse?.data || eligibilityResponse
  } catch (error: any) {
    message.error(error?.message || '寄样资格检查失败')
    return
  }

  if (eligibility?.needReason && !String(formData.reason || '').trim()) {
    openEligibilityWarning(eligibility)
    return
  }

  dialog.warning({
    title: '确认提交',
    content: `确认向达人 ${formData.talentNickname} 寄样？`,
    positiveText: '确认',
    negativeText: '取消',
    onPositiveClick: doSubmit
  })
};

const formatFans = (fans?: number) => {
  if (fans === null || fans === undefined) return '-';
  if (fans >= 100000000) return `${(fans / 100000000).toFixed(1)}亿`;
  if (fans >= 10000) return `${(fans / 10000).toFixed(1)}万`;
  return `${fans}`;
};

onMounted(async () => {
  await applyRouteContext();
  await fetchTalents(1);
  await fetchProductOptions('');
});
</script>

<style scoped>
.apply-card {
  max-width: 860px;
  margin: 0 auto;
}
</style>
