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
        <n-radio :value="true">通过并上架</n-radio>
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
        <n-divider title-placement="left">基础补充信息</n-divider>
        <n-grid :cols="2" :x-gap="12" :y-gap="12">
          <n-gi>
            <n-form-item label="专属价说明">
              <n-input v-model:value="form.exclusivePriceRemark" placeholder="如：团长专属9折" />
            </n-form-item>
          </n-gi>
          <n-gi>
            <n-form-item label="发货信息">
              <n-input v-model:value="form.shippingInfo" placeholder="发货地、快递等" />
            </n-form-item>
          </n-gi>
          <n-gi :span="2">
            <n-form-item label="商品卖点">
              <n-input v-model:value="sellingPointsText" type="textarea" :rows="2" placeholder="每行一条卖点" />
            </n-form-item>
          </n-gi>
          <n-gi :span="2">
            <n-form-item label="推广话术">
              <n-input v-model:value="form.promotionScript" type="textarea" :rows="2" placeholder="给达人的推广建议" />
            </n-form-item>
          </n-gi>
          <n-gi>
            <n-form-item label="支持投流">
              <n-radio-group v-model:value="form.supportsAds">
                <n-radio :value="true">是</n-radio>
                <n-radio :value="false">否</n-radio>
              </n-radio-group>
            </n-form-item>
          </n-gi>
          <n-gi>
            <n-form-item label="活动时间">
              <n-input v-model:value="form.campaignTimeRemark" placeholder="如：长期在线" />
            </n-form-item>
          </n-gi>
          <n-gi :span="2">
            <n-form-item label="奖励说明">
              <n-input v-model:value="form.rewardRemark" type="textarea" :rows="2" placeholder="如：破峰值有奖励、额外返佣规则" />
            </n-form-item>
          </n-gi>
          <n-gi :span="2">
            <n-form-item label="参与要求">
              <n-input v-model:value="form.participationRequirements" type="textarea" :rows="2" placeholder="如：食品饮料达人优先，需真人出镜" />
            </n-form-item>
          </n-gi>
          <n-gi :span="2">
            <n-form-item label="手卡素材">
              <n-input v-model:value="materialFilesText" type="textarea" :rows="2" placeholder="每行填写一个素材链接或文件名" />
            </n-form-item>
          </n-gi>
        </n-grid>

        <n-divider title-placement="left">寄样门槛配置</n-divider>
        <n-grid :cols="2" :x-gap="12" :y-gap="12">
          <n-gi>
            <n-form-item label="30天销售额 ≥">
              <n-input-number v-model:value="form.sampleThresholdSales" :min="0" style="width: 100%" placeholder="默认 30000" />
            </n-form-item>
          </n-gi>
          <n-gi>
            <n-form-item label="达人等级 ≥">
              <n-select
                v-model:value="form.sampleThresholdLevel"
                :options="[
                  { label: 'LV0', value: 0 },
                  { label: 'LV1', value: 1 },
                  { label: 'LV2', value: 2 },
                  { label: 'LV3', value: 3 },
                  { label: 'LV4', value: 4 },
                  { label: 'LV5', value: 5 },
                  { label: 'LV6', value: 6 }
                ]"
              />
            </n-form-item>
          </n-gi>
          <n-gi :span="2">
            <n-form-item label="寄样补充要求">
              <n-input v-model:value="form.sampleThresholdRemark" type="textarea" :rows="2" placeholder="如：需真人出镜，粉丝量>10万" />
            </n-form-item>
          </n-gi>
        </n-grid>
      </template>
    </n-space>
  </n-modal>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue';
import { useMessage } from 'naive-ui';
import { auditActivityProduct } from '../../../api/activityProduct';

const props = defineProps<{
  show: boolean;
  activityId: string | number | null;
  productId: string | number | null;
}>();

const emit = defineEmits(['update:show', 'success']);
const message = useMessage();

const auditApproved = ref(true);
const auditReason = ref('');
const sellingPointsText = ref('');
const materialFilesText = ref('');

const form = reactive({
  exclusivePriceRemark: '',
  shippingInfo: '',
  promotionScript: '',
  supportsAds: true,
  rewardRemark: '',
  participationRequirements: '',
  campaignTimeRemark: '',
  sampleThresholdSales: 30000,
  sampleThresholdLevel: 1,
  sampleThresholdRemark: ''
});

const resetForm = () => {
  auditApproved.value = true;
  auditReason.value = '';
  sellingPointsText.value = '';
  materialFilesText.value = '';
  form.exclusivePriceRemark = '';
  form.shippingInfo = '';
  form.promotionScript = '';
  form.supportsAds = true;
  form.rewardRemark = '';
  form.participationRequirements = '';
  form.campaignTimeRemark = '';
  form.sampleThresholdSales = 30000;
  form.sampleThresholdLevel = 1;
  form.sampleThresholdRemark = '';
};

watch(
  () => props.show,
  (val) => {
    if (val) {
      resetForm();
    }
  }
);

const updateShow = (val: boolean) => {
  emit('update:show', val);
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
  try {
    const sellingPoints = sellingPointsText.value
      .split('\n')
      .map((s) => s.trim())
      .filter(Boolean);
    const materialFiles = materialFilesText.value
      .split('\n')
      .map((s) => s.trim())
      .filter(Boolean);

    if (auditApproved.value) {
      const missing: string[] = [];
      if (!form.exclusivePriceRemark.trim()) missing.push('专属价说明');
      if (!form.shippingInfo.trim()) missing.push('发货信息');
      if (!sellingPoints.length) missing.push('商品卖点');
      if (!form.promotionScript.trim()) missing.push('推广话术');
      if (form.supportsAds === undefined || form.supportsAds === null) missing.push('是否支持投流');
      if (!form.rewardRemark.trim()) missing.push('奖励说明');
      if (!form.participationRequirements.trim()) missing.push('参与要求');
      if (!form.campaignTimeRemark.trim()) missing.push('活动时间');
      if (!materialFiles.length) missing.push('手卡素材');
      if (missing.length) {
        message.warning(`审核通过前请补充：${missing.join('、')}`);
        return false;
      }
    }

    const payload = {
      approved: auditApproved.value,
      reason: auditApproved.value ? undefined : auditReason.value.trim(),
      // 补充信息
      exclusivePriceRemark: auditApproved.value ? form.exclusivePriceRemark : undefined,
      shippingInfo: auditApproved.value ? form.shippingInfo : undefined,
      sellingPoints: auditApproved.value ? sellingPoints : undefined,
      promotionScript: auditApproved.value ? form.promotionScript : undefined,
      supportsAds: auditApproved.value ? form.supportsAds : undefined,
      rewardRemark: auditApproved.value ? form.rewardRemark : undefined,
      participationRequirements: auditApproved.value ? form.participationRequirements : undefined,
      campaignTimeRemark: auditApproved.value ? form.campaignTimeRemark : undefined,
      materialFiles: auditApproved.value ? materialFiles : undefined,
      // 门槛信息
      sampleThresholdSales: auditApproved.value ? form.sampleThresholdSales : undefined,
      sampleThresholdLevel: auditApproved.value ? form.sampleThresholdLevel : undefined,
      sampleThresholdRemark: auditApproved.value ? form.sampleThresholdRemark : undefined
    };

    const res: any = await auditActivityProduct(props.activityId, props.productId, payload);
    message.success(auditApproved.value ? '审核通过，商品已自动加入商品库' : '审核驳回');
    emit('success', res?.data);
    updateShow(false);
    return true;
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '审核失败');
    return false;
  }
};
</script>
