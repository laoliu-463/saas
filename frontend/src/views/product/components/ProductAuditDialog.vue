<template>
  <n-modal
    :show="show"
    preset="dialog"
    title="审核商品"
    positive-text="提交"
    negative-text="取消"
    style="width: 760px;"
    @positive-click="handleSubmit"
    @update:show="updateShow"
  >
    <n-space vertical :size="16">
      <n-radio-group v-model:value="auditApproved">
        <n-radio :value="true">通过</n-radio>
        <n-radio :value="false">驳回</n-radio>
      </n-radio-group>

      <n-input
        v-if="auditApproved === false"
        v-model:value="auditReason"
        type="textarea"
        :rows="3"
        placeholder="驳回时必须填写原因"
      />

      <template v-else>
        <n-grid :cols="2" :x-gap="12">
          <n-gi>
            <n-input v-model:value="form.exclusivePriceRemark" placeholder="专属价说明" />
          </n-gi>
          <n-gi>
            <n-input v-model:value="form.shippingInfo" placeholder="发货信息" />
          </n-gi>
        </n-grid>

        <n-input
          v-model:value="sellingPointsText"
          type="textarea"
          :rows="4"
          placeholder="商品卖点，每行一条"
        />

        <n-input
          v-model:value="form.promotionScript"
          type="textarea"
          :rows="4"
          placeholder="推广话术"
        />

        <n-radio-group v-model:value="form.supportsAds">
          <n-radio :value="true">支持投流</n-radio>
          <n-radio :value="false">不支持投流</n-radio>
        </n-radio-group>

        <n-input
          v-model:value="form.rewardRemark"
          type="textarea"
          :rows="3"
          placeholder="奖励说明"
        />

        <n-input
          v-model:value="form.participationRequirements"
          type="textarea"
          :rows="3"
          placeholder="参与要求"
        />

        <n-input
          v-model:value="form.campaignTimeRemark"
          type="textarea"
          :rows="3"
          placeholder="活动时间说明"
        />

        <n-input
          v-model:value="materialFilesText"
          type="textarea"
          :rows="4"
          placeholder="手卡素材链接或文件说明，每行一条"
        />
      </template>
    </n-space>
  </n-modal>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import { useMessage } from 'naive-ui';
import { auditActivityProduct } from '../../../api/activityProduct';

const props = defineProps<{ show: boolean; activityId: string | number | null; productId: string | number | null }>();
const emit = defineEmits(['update:show', 'success']);
const message = useMessage();

const auditApproved = ref(true);
const auditReason = ref('');
const sellingPointsText = ref('');
const materialFilesText = ref('');
const form = reactive<{
  exclusivePriceRemark: string;
  shippingInfo: string;
  promotionScript: string;
  supportsAds: boolean | null;
  rewardRemark: string;
  participationRequirements: string;
  campaignTimeRemark: string;
}>({
  exclusivePriceRemark: '',
  shippingInfo: '',
  promotionScript: '',
  supportsAds: null,
  rewardRemark: '',
  participationRequirements: '',
  campaignTimeRemark: ''
});

const resetForm = () => {
  auditApproved.value = true;
  auditReason.value = '';
  sellingPointsText.value = '';
  materialFilesText.value = '';
  form.exclusivePriceRemark = '';
  form.shippingInfo = '';
  form.promotionScript = '';
  form.supportsAds = null;
  form.rewardRemark = '';
  form.participationRequirements = '';
  form.campaignTimeRemark = '';
};

watch(() => props.show, (val) => {
  if (val) resetForm();
});

const updateShow = (val: boolean) => {
  emit('update:show', val);
};

const splitLines = (value: string) =>
  value
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean);

const requiredTextFields = [
  ['exclusivePriceRemark', '专属价说明'],
  ['shippingInfo', '发货信息'],
  ['promotionScript', '推广话术'],
  ['rewardRemark', '奖励说明'],
  ['participationRequirements', '参与要求'],
  ['campaignTimeRemark', '活动时间']
] as const;

const validateApprovedForm = () => {
  for (const [field, label] of requiredTextFields) {
    if (!String(form[field] || '').trim()) {
      message.warning(`请填写${label}`);
      return false;
    }
  }
  if (!splitLines(sellingPointsText.value).length) {
    message.warning('请至少填写一条商品卖点');
    return false;
  }
  if (form.supportsAds === null) {
    message.warning('请选择是否支持投流');
    return false;
  }
  if (!splitLines(materialFilesText.value).length) {
    message.warning('请至少填写一条手卡素材');
    return false;
  }
  return true;
};

const handleSubmit = async () => {
  if (!props.activityId || !props.productId) {
    message.warning('商品信息不完整，暂不可提交审核');
    return false;
  }
  if (!auditApproved.value && !auditReason.value.trim()) {
    message.warning('驳回时必须填写原因');
    return false;
  }
  if (auditApproved.value && !validateApprovedForm()) {
    return false;
  }
  try {
    const res: any = await auditActivityProduct(props.activityId, props.productId, {
      approved: auditApproved.value,
      reason: auditApproved.value ? undefined : auditReason.value.trim(),
      exclusivePriceRemark: auditApproved.value ? form.exclusivePriceRemark.trim() : undefined,
      shippingInfo: auditApproved.value ? form.shippingInfo.trim() : undefined,
      sellingPoints: auditApproved.value ? splitLines(sellingPointsText.value) : undefined,
      promotionScript: auditApproved.value ? form.promotionScript.trim() : undefined,
      supportsAds: auditApproved.value ? form.supportsAds ?? undefined : undefined,
      rewardRemark: auditApproved.value ? form.rewardRemark.trim() : undefined,
      participationRequirements: auditApproved.value ? form.participationRequirements.trim() : undefined,
      campaignTimeRemark: auditApproved.value ? form.campaignTimeRemark.trim() : undefined,
      materialFiles: auditApproved.value ? splitLines(materialFilesText.value) : undefined
    });
    message.success(auditApproved.value ? '审核通过' : '审核驳回');
    emit('success', res?.data);
    updateShow(false);
    return true;
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '审核失败');
    return false;
  }
};
</script>
