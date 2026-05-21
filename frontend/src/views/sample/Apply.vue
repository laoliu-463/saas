<template>
  <div class="sample-apply app-page">
    <n-card title="申请寄样" :bordered="false" class="app-panel apply-card">
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
              @search="handleSearchProduct"
              clearable
              remote
            />
          </n-form-item-gi>

          <n-form-item-gi :span="12" label="寄样数量" path="quantity">
            <n-input-number v-model:value="formData.quantity" :min="1" :max="10" />
          </n-form-item-gi>

          <n-form-item-gi :span="24" label="备注" path="remark">
            <n-input v-model:value="formData.remark" type="textarea" placeholder="补充说明" />
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
import { computed, h, onMounted, onUnmounted, reactive, ref } from 'vue';
import { NButton, useDialog, useMessage } from 'naive-ui';
import { useRouter } from 'vue-router';
import { checkSampleEligibility, createSample, searchSampleProducts } from '../../api/sample';
import { getTalentPage } from '../../api/talent';
import { useAuthStore } from '../../stores/auth';
import { resolveSafeAvatarUrl } from '../../utils/media';

const message = useMessage();
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
  remark: ''
});

const rules = {
  talentId: { required: true, message: '请选择达人', trigger: 'change' },
  productId: { required: true, message: '请选择商品', trigger: 'change' },
  quantity: { type: 'number', required: true, message: '请输入寄样数量', trigger: 'blur' }
};

const talentQuery = reactive({
  keyword: '',
  region: '',
  minFans: null as number | null,
  maxFans: null as number | null,
  page: 1,
  size: 10,
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
let productSearchTimer: ReturnType<typeof window.setTimeout> | null = null;

const fetchProductOptions = async (query: string) => {
  loadingProducts.value = true;
  try {
    const res = await searchSampleProducts({ keyword: query || undefined, size: 20 });
    const payload: any = (res as any)?.data ?? (res as any);
    const records = payload?.records || [];
    productOptions.value = records.map((p: any) => ({
      label: p.productName || p.name || p.title || p.productId || p.id,
      value: p.id
    }));
  } finally {
    loadingProducts.value = false;
  }
};

const handleSearchProduct = (query: string) => {
  if (productSearchTimer) {
    window.clearTimeout(productSearchTimer);
  }
  productSearchTimer = window.setTimeout(() => {
    fetchProductOptions(query).catch((error: any) => {
      message.error(error?.message || '搜索商品失败');
    });
  }, 300);
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

const chooseTalent = (row: any) => {
  selectedTalent.value = row;
  formData.talentId = row.talentId;
  formData.talentNickname = row.nickname;
  formData.talentFansCount = row.fansCount;
  formData.talentCreditScore = row.creditScore;
  formData.talentMainCategory = row.mainCategory;
};

const canSubmit = computed(() =>
  !authStore.userInfo?.roleCodes?.includes('VISITOR') && !authStore.userInfo?.roleCodes?.includes('READONLY')
);

const doSubmit = async () => {
  submitting.value = true;
  try {
    await createSample({
      talentId: formData.talentId,
      talentNickname: formData.talentNickname,
      talentFansCount: formData.talentFansCount,
      talentCreditScore: formData.talentCreditScore,
      talentMainCategory: formData.talentMainCategory,
      productId: formData.productId,
      quantity: formData.quantity,
      remark: formData.remark
    });
    message.success('寄样申请提交成功');
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
    const eligibilityResponse = await checkSampleEligibility({
      talentId: formData.talentId,
      talentNickname: formData.talentNickname,
      talentFansCount: formData.talentFansCount,
      talentCreditScore: formData.talentCreditScore,
      talentMainCategory: formData.talentMainCategory,
      productId: formData.productId,
      quantity: formData.quantity,
      remark: formData.remark
    })
    eligibility = eligibilityResponse?.data || eligibilityResponse
  } catch (error: any) {
    message.error(error?.message || '寄样资格检查失败')
    return
  }

  if (eligibility?.needReason && !String(formData.remark || '').trim()) {
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
  await fetchTalents(1);
  await fetchProductOptions('');
});

onUnmounted(() => {
  if (productSearchTimer) {
    window.clearTimeout(productSearchTimer);
  }
});
</script>

<style scoped>
.apply-card {
  max-width: 860px;
  margin: 0 auto;
}
</style>
