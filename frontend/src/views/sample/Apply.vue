<template>
  <div class="sample-apply">
    <n-card title="申请寄样" :bordered="false" style="max-width: 860px; margin: 0 auto;">
      <n-form ref="formRef" :model="formData" :rules="rules" label-placement="top">
        <n-grid :cols="24" :x-gap="12">
          <n-form-item-gi :span="24" label="达人搜索">
            <n-space style="width: 100%;" wrap>
              <n-input v-model:value="talentQuery.keyword" placeholder="昵称/达人号" clearable style="width: 220px" />
              <n-input v-model:value="talentQuery.region" placeholder="地域" clearable style="width: 140px" />
              <n-input-number v-model:value="talentQuery.minFans" :min="0" placeholder="最低粉丝" style="width: 140px" />
              <n-input-number v-model:value="talentQuery.maxFans" :min="0" placeholder="最高粉丝" style="width: 140px" />
              <n-input-number v-model:value="talentQuery.minScore" :min="0" :max="5" :step="0.1" placeholder="最低评分" style="width: 140px" />
              <n-button type="primary" :loading="loadingTalents" @click="fetchTalents(1)">搜索达人</n-button>
            </n-space>
          </n-form-item-gi>

          <n-form-item-gi :span="24" path="talentId" label="选择达人">
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
              <n-avatar :src="selectedTalent.avatarUrl" round :size="48" />
              <div>
                <div style="font-weight: 600">{{ selectedTalent.nickname }}</div>
                <div style="color: #666; font-size: 12px;">
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
import { computed, h, onMounted, reactive, ref } from 'vue';
import { NButton, useDialog, useMessage } from 'naive-ui';
import { useRouter } from 'vue-router';
import { createSample, searchSampleProducts, searchSampleTalents } from '../../api/sample';
import { useAuthStore } from '../../stores/auth';

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
  minScore: null as number | null,
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

const handleSearchProduct = async (query: string) => {
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

const fetchTalents = async (page = 1) => {
  loadingTalents.value = true;
  try {
    talentQuery.page = page;
    const res = await searchSampleTalents({
      keyword: talentQuery.keyword || undefined,
      region: talentQuery.region || undefined,
      minFans: talentQuery.minFans ?? undefined,
      maxFans: talentQuery.maxFans ?? undefined,
      minScore: talentQuery.minScore ?? undefined,
      page: talentQuery.page,
      size: talentQuery.size
    });
    const pageData: any = (res as any)?.data ?? (res as any) ?? {};
    talentRows.value = pageData.records || [];
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

const handleSubmit = () => {
  if (!canSubmit.value) {
    message.warning('当前角色无权提交寄样申请');
    return;
  }
  formRef.value?.validate((errors: any) => {
    if (!errors) {
      dialog.warning({
        title: '确认提交',
        content: `确认向达人 ${formData.talentNickname} 寄样？`,
        positiveText: '确认',
        negativeText: '取消',
        onPositiveClick: doSubmit
      });
    }
  });
};

const formatFans = (fans?: number) => {
  if (fans === null || fans === undefined) return '-';
  if (fans >= 100000000) return `${(fans / 100000000).toFixed(1)}亿`;
  if (fans >= 10000) return `${(fans / 10000).toFixed(1)}万`;
  return `${fans}`;
};

onMounted(async () => {
  await fetchTalents(1);
  await handleSearchProduct('');
});
</script>

<style scoped>
.sample-apply {
  min-height: 100%;
  padding: 24px;
}
</style>

