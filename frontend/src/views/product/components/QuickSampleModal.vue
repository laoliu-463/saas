<template>
  <n-drawer
    v-model:show="visible"
    placement="right"
    width="min(920px, calc(100vw - 24px))"
    data-testid="quick-sample-drawer"
    @after-leave="resetForm"
  >
    <n-drawer-content :native-scrollbar="false">
      <div class="quick-sample-drawer">
        <header class="quick-sample-header">
          <h2 class="quick-sample-header__title">
            <span class="quick-sample-header__mark" aria-hidden="true"><i /><i /></span>
            批量申样
          </h2>
          <div class="quick-sample-header__actions">
            <n-button size="large" data-testid="quick-sample-close" @click="visible = false">关闭</n-button>
            <n-button
              type="primary"
              size="large"
              :loading="submitting"
              data-testid="quick-sample-submit"
              @click="submit"
            >
              提交
            </n-button>
          </div>
        </header>

        <main class="quick-sample-body">
          <div class="quick-sample-steps" aria-label="批量申样步骤">
            <div class="quick-sample-step quick-sample-step--active" data-testid="quick-sample-step-1">
              <span class="quick-sample-step__number">1</span>
              <div>
                <strong>第一步</strong>
                <span>选择合作对象</span>
              </div>
            </div>
            <span class="quick-sample-step__line" aria-hidden="true" />
            <div class="quick-sample-step" data-testid="quick-sample-step-2">
              <span class="quick-sample-step__number">2</span>
              <div>
                <strong>第二步</strong>
                <span>选择商品规格</span>
              </div>
            </div>
          </div>

          <n-form label-placement="top">
            <section class="quick-sample-section" data-testid="quick-sample-cooperation-section">
              <h3 class="quick-sample-section__title">选择合作对象</h3>

              <n-form-item v-if="isAdmin" label="媒介" required>
                <n-select
                  v-model:value="form.channelUserId"
                  :options="channelOptions"
                  :loading="channelLoading"
                  filterable
                  clearable
                  placeholder="请选择媒介"
                  data-testid="quick-sample-channel"
                  @update:value="handleChannelChange"
                />
              </n-form-item>

              <div class="quick-sample-talent-action">
                <n-button
                  text
                  type="primary"
                  size="large"
                  :disabled="isAdmin && !form.channelUserId"
                  data-testid="quick-sample-add-talent"
                  @click="openTalentPicker"
                >
                  <template #icon><span class="quick-sample-talent-action__plus">＋</span></template>
                  添加合作达人
                </n-button>
                <span class="quick-sample-talent-action__count">（已选 {{ selectedTalentCount }}/20）</span>
              </div>

              <div v-if="talentPickerVisible" class="quick-sample-talent-picker">
                <n-form-item :label="talentFieldLabel" required>
                  <n-select
                    v-model:value="form.talentIds"
                    :options="talentOptions"
                    :loading="talentLoading"
                    multiple
                    :placeholder="talentPlaceholder"
                    data-testid="quick-sample-talents"
                  >
                    <template #empty>
                      {{ talentEmptyHint }}
                    </template>
                  </n-select>
                </n-form-item>
              </div>
            </section>

            <section class="quick-sample-section" data-testid="quick-sample-spec-section">
              <h3 class="quick-sample-section__title">选择商品规格</h3>

              <div class="quick-sample-product-table" role="table" aria-label="商品规格">
                <div class="quick-sample-product-table__head" role="row">
                  <span role="columnheader">商品信息</span>
                  <span role="columnheader">商品规格</span>
                  <span role="columnheader">备注</span>
                </div>
                <div class="quick-sample-product-table__row" role="row">
                  <div class="quick-sample-product-info" role="cell">
                    <div class="quick-sample-product-info__cover">
                      <img v-if="productCover" :src="productCover" :alt="productTitle" />
                      <span v-else aria-hidden="true">商品</span>
                    </div>
                    <div class="quick-sample-product-info__text">
                      <strong class="quick-sample-product-info__title" :title="productTitle">
                        <span class="quick-sample-product-info__douyin" aria-hidden="true">♪</span>
                        {{ productTitle }}
                      </strong>
                      <span>商品ID：{{ productId || '—' }}</span>
                      <span>店铺：{{ productShopName || '—' }}</span>
                    </div>
                  </div>

                  <div class="quick-sample-product-spec" role="cell">
                    <ProductSpecSelector
                      v-model="form.skuId"
                      :skus="skuOptions"
                      :loading="skuLoading"
                      value-field="skuId"
                      placeholder="选择规格"
                      data-testid="quick-sample-spec"
                      @select="handleSkuSelect"
                    />
                    <n-input-number
                      v-model:value="form.quantity"
                      :min="1"
                      :max="100"
                      size="small"
                      data-testid="quick-sample-quantity"
                      aria-label="寄样数量"
                    />
                  </div>

                  <div class="quick-sample-product-remark" role="cell">
                    <n-button
                      v-if="!remarkEditing"
                      text
                      type="primary"
                      data-testid="quick-sample-remark-edit"
                      @click="remarkEditing = true"
                    >
                      编辑
                    </n-button>
                    <div v-else class="quick-sample-product-remark__editor">
                      <n-input
                        v-model:value="form.remark"
                        type="textarea"
                        :autosize="{ minRows: 2, maxRows: 4 }"
                        placeholder="请输入备注"
                        data-testid="quick-sample-remark"
                      />
                      <n-button text size="small" data-testid="quick-sample-remark-done" @click="remarkEditing = false">
                        完成
                      </n-button>
                    </div>
                  </div>
                </div>
              </div>
            </section>

            <section class="quick-sample-section quick-sample-section--optional" data-testid="quick-sample-address-section">
              <h3 class="quick-sample-section__title">收货信息</h3>
              <div class="quick-sample-address-grid">
                <n-form-item label="收货人">
                  <n-input v-model:value="form.recipientName" placeholder="收货人姓名" data-testid="quick-sample-recipient-name" />
                </n-form-item>
                <n-form-item label="联系电话">
                  <n-input v-model:value="form.recipientPhone" placeholder="收货人手机号" data-testid="quick-sample-recipient-phone" />
                </n-form-item>
                <n-form-item label="收货地址">
                  <n-input v-model:value="form.recipientAddress" type="textarea" rows="2" data-testid="quick-sample-address" />
                </n-form-item>
              </div>
            </section>
          </n-form>
        </main>
      </div>
    </n-drawer-content>
  </n-drawer>
</template>

<script setup lang="ts">
import { notifyApiFailure } from '../../../utils/requestError'
import { computed, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { applyQuickSample } from '../../../api/product'
import { getActivityProductSkus } from '../../../api/activityProduct'
import {
  getTalentPrivate,
  getTalentByChannel,
  getTalentShippingAddress,
  parsePrivateTalentPoolResponse,
  toPrivateTalentSelectOption
} from '../../../api/talent'
import { useAuthStore } from '../../../stores/auth'
import { loadSampleChannelOptions } from '../../sample/sample-user-filter-options'
import ProductSpecSelector from './ProductSpecSelector.vue'

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
const props = defineProps<{ product: any | null }>()
const emit = defineEmits<{ success: [] }>()

const visible = defineModel<boolean>('show', { default: false })
const message = useMessage()
const authStore = useAuthStore()
const submitting = ref(false)
const channelLoading = ref(false)
const channelOptions = ref<Array<{ label: string; value: string }>>([])
const talentLoading = ref(false)
const talentLoadFailed = ref(false)
const talentOptions = ref<Array<{ label: string; value: string }>>([])
/** 管理员可以选渠道并查询该渠道的达人，非管理员只能选自己的私海 */
const isAdmin = computed(() => authStore.isAdmin)
const talentFieldLabel = computed(() => (isAdmin.value ? '合作达人' : '私海达人'))
const talentPlaceholder = computed(() =>
  isAdmin.value ? '请选择合作达人' : '选择当前渠道已认领达人'
)
const talentEmptyHint = computed(() => {
  if (talentLoading.value) return '加载中…'
  if (talentLoadFailed.value) return '加载失败，请关闭弹窗后重试'
  if (isAdmin.value && !form.value.channelUserId) return '请先选择渠道'
  if (isAdmin.value) return '该渠道暂无认领达人'
  return '暂无私海达人，请先在「达人」页认领后再选'
})
const skuOptions = ref<any[]>([])
const skuLoading = ref(false)

const activityProductIds = computed(() => ({
  activityId: String(props.product?.activityId || props.product?.sourceActivityId || '').trim(),
  productId: String(props.product?.productId || '').trim()
}))

const relationId = computed(() => String(props.product?.id || ''))

const loadSkus = async () => {
  const { activityId, productId } = activityProductIds.value
  if (!activityId || !productId) {
    skuOptions.value = []
    return
  }
  skuLoading.value = true
  try {
    const res: any = await getActivityProductSkus(activityId, productId)
    skuOptions.value = Array.isArray(res?.data) ? res.data : []
  } catch {
    skuOptions.value = []
  } finally {
    skuLoading.value = false
  }
}

const form = ref({
  channelUserId: '' as string,
  talentIds: [] as string[],
  skuId: '',
  specification: '',
  quantity: 1,
  recipientName: '',
  recipientPhone: '',
  recipientAddress: '',
  remark: ''
})

const talentPickerVisible = ref(false)
const remarkEditing = ref(false)
const selectedTalentCount = computed(() => form.value.talentIds.length)
const productTitle = computed(() => String(
  props.product?.title || props.product?.productName || props.product?.name || '未命名商品'
).trim())
const productId = computed(() => String(props.product?.productId || '').trim())
const productShopName = computed(() => String(
  props.product?.shopName || props.product?.merchantName || ''
).trim())
const productCover = computed(() => String(
  props.product?.cover || props.product?.imageUrl || props.product?.card?.imageUrl || ''
).trim())

const handleSkuSelect = (sku: any | null) => {
  form.value.specification = String(sku?.skuName || '').trim()
}

const openTalentPicker = () => {
  if (isAdmin.value && !form.value.channelUserId) {
    message.warning('请先选择媒介')
    return
  }
  talentPickerVisible.value = true
}

const mapTalentSelectOptions = (records: any[]) =>
  records
    .map((item: any) => toPrivateTalentSelectOption(item))
    .filter((item: { label: string; value: string }) => item.label && item.value)

const loadChannels = async () => {
  channelLoading.value = true
  try {
    channelOptions.value = await loadSampleChannelOptions('')
  } catch {
    channelOptions.value = []
  } finally {
    channelLoading.value = false
  }
}

const loadTalents = async () => {
  talentLoading.value = true
  talentLoadFailed.value = false
  try {
    if (isAdmin.value) {
      // 管理员：必须先选渠道才能选达人
      const channelUserId = String(form.value.channelUserId || '').trim()
      if (!channelUserId) {
        talentOptions.value = []
        return
      }
      if (!UUID_PATTERN.test(channelUserId)) {
        talentOptions.value = []
        talentLoadFailed.value = true
        message.warning('渠道选项无效，请重新选择')
        return
      }
      // 按渠道查达人（path 参数须为 UUID）
      const res: any = await getTalentByChannel(channelUserId)
      talentOptions.value = mapTalentSelectOptions(parsePrivateTalentPoolResponse({ data: res }))
    } else {
      // 非管理员：查自己的私海
      const res: any = await getTalentPrivate()
      talentOptions.value = mapTalentSelectOptions(parsePrivateTalentPoolResponse(res))
      if (!talentOptions.value.length) {
        message.info('当前账号私海暂无达人，请先在达人页认领')
      }
    }
  } catch (error: any) {
    talentOptions.value = []
    talentLoadFailed.value = true
    notifyApiFailure(error, message, {
      fallbackMessage: isAdmin.value ? '加载达人失败' : '加载私海达人失败'
    })
  } finally {
    talentLoading.value = false
  }
}

const handleChannelChange = () => {
  // 切换渠道时清空已选达人，重新加载该渠道的达人
  form.value.talentIds = []
  talentOptions.value = []
  talentPickerVisible.value = false
  clearAddressFields()
  if (form.value.channelUserId) {
    loadTalents()
  }
}

const clearAddressFields = () => {
  form.value.recipientName = ''
  form.value.recipientPhone = ''
  form.value.recipientAddress = ''
}

const formatQuickSampleFailureDetail = (data: any) => {
  const failures = Array.isArray(data?.items)
    ? data.items.filter((item: any) => item && item.success === false)
    : []
  return failures
    .map((item: any) => {
      const talentLabel = String(item?.talentName || item?.talentNickname || item?.talentId || '未知达人').trim()
      const reason = String(item?.message || '未返回失败原因').trim()
      return `${talentLabel}：${reason}`
    })
    .filter(Boolean)
    .slice(0, 5)
    .join('；')
}

watch(visible, (show) => {
  if (show) {
    if (isAdmin.value) {
      loadChannels()
    }
    loadTalents()
    loadSkus()
  }
})

/** 选择达人后自动加载默认收货地址 */
watch(() => form.value.talentIds, async (talentIds) => {
  if (!talentIds || talentIds.length === 0) return
  const talentId = talentIds[0]
  if (!talentId || !UUID_PATTERN.test(talentId)) return
  try {
    const addr = await getTalentShippingAddress(talentId)
    if (!form.value.recipientName && !form.value.recipientPhone && !form.value.recipientAddress) {
      form.value.recipientName = addr?.recipientName || ''
      form.value.recipientPhone = addr?.recipientPhone || ''
      form.value.recipientAddress = addr?.recipientAddress || ''
    }
  } catch {
    // 加载地址失败不阻断寄样流程
  }
})

const resetForm = () => {
  form.value = { channelUserId: '', talentIds: [], skuId: '', specification: '', quantity: 1, recipientName: '', recipientPhone: '', recipientAddress: '', remark: '' }
  skuOptions.value = []
  talentPickerVisible.value = false
  remarkEditing.value = false
}

const submit = async () => {
  if (!relationId.value) {
    message.warning('商品信息不完整')
    return
  }
  if (isAdmin.value && !form.value.channelUserId) {
    message.warning('请先选择渠道')
    return
  }
  if (!form.value.talentIds.length) {
    message.warning(isAdmin.value ? '请至少选择一位达人' : '请至少选择一位私海达人')
    return
  }
  submitting.value = true
  try {
    const res: any = await applyQuickSample(relationId.value, {
      talentIds: form.value.talentIds,
      skuId: form.value.skuId || undefined,
      specification: form.value.specification || undefined,
      quantity: form.value.quantity,
      recipientName: form.value.recipientName || undefined,
      recipientPhone: form.value.recipientPhone || undefined,
      recipientAddress: form.value.recipientAddress || undefined,
      remark: form.value.remark || undefined,
      // 管理员代选渠道时传递 channelUserId
      channelUserId: isAdmin.value && UUID_PATTERN.test(String(form.value.channelUserId || '').trim())
        ? String(form.value.channelUserId).trim()
        : undefined
    })
    const data = res?.data || {}
    const fallbackUsed = data.items?.some((item: any) => item.fallback || item.fallbackType === 'LOCAL_FALLBACK')
    const successCount = data.successCount ?? 0
    const failureCount = data.failureCount ?? 0
    if (failureCount > 0) {
      const detail = formatQuickSampleFailureDetail(data)
      const suffix = detail ? `。失败原因：${detail}` : '。失败原因：服务端未返回明细，请联系管理员查看日志'
      if (successCount > 0) {
        message.warning(`部分提交成功：成功 ${successCount}，失败 ${failureCount}${suffix}`)
        visible.value = false
        emit('success')
      } else {
        message.error(`快速寄样失败：成功 ${successCount}，失败 ${failureCount}${suffix}`)
      }
      return
    }
    const msg = fallbackUsed
      ? `提交完成：成功 ${successCount}，失败 ${failureCount}。抖店外部寄样暂未接通，已创建系统内寄样申请`
      : `提交完成：成功 ${successCount}，失败 ${failureCount}`
    message.success(msg)
    visible.value = false
    emit('success')
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '快速寄样失败' })
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.quick-sample-drawer {
  min-height: 100%;
  color: var(--text-color-1, #172033);
  background: var(--body-color, #fff);
}

.quick-sample-drawer :deep(.n-drawer-body-content) {
  padding: 0;
}

.quick-sample-header {
  position: sticky;
  z-index: 10;
  top: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 94px;
  padding: 16px 32px;
  border-bottom: 1px solid var(--divider-color, #edf0f3);
  background: var(--body-color, #fff);
}

.quick-sample-header__title {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 0;
  color: var(--text-color-1, #172033);
  font-size: 26px;
  font-weight: 700;
}

.quick-sample-header__mark {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.quick-sample-header__mark i {
  display: block;
  width: 5px;
  height: 28px;
  border-radius: 4px;
  background: var(--primary-color, #f5222d);
}

.quick-sample-header__mark i:last-child {
  height: 18px;
}

.quick-sample-header__actions {
  display: flex;
  gap: 16px;
}

.quick-sample-header__actions :deep(.n-button) {
  min-width: 120px;
  font-size: 18px;
  font-weight: 600;
}

.quick-sample-body {
  width: min(1440px, 100%);
  margin: 0 auto;
  padding: 34px 32px 56px;
  box-sizing: border-box;
}

.quick-sample-steps {
  display: flex;
  align-items: center;
  margin-bottom: 28px;
}

.quick-sample-step {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 14px;
  color: var(--text-color-3, #a0a6ad);
}

.quick-sample-step__number {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 62px;
  height: 62px;
  border-radius: 50%;
  color: var(--text-color-3, #8a919a);
  background: #f0f1f3;
  font-size: 24px;
}

.quick-sample-step--active {
  color: var(--text-color-1, #172033);
}

.quick-sample-step--active .quick-sample-step__number {
  color: #fff;
  background: var(--primary-color, #f5222d);
}

.quick-sample-step strong,
.quick-sample-step span:not(.quick-sample-step__number) {
  display: block;
  line-height: 1.45;
}

.quick-sample-step strong {
  font-size: 22px;
  font-weight: 600;
}

.quick-sample-step span:not(.quick-sample-step__number) {
  margin-top: 4px;
  font-size: 18px;
}

.quick-sample-step__line {
  flex: 1;
  min-width: 80px;
  height: 1px;
  margin: 0 28px;
  background: var(--divider-color, #e7e9ec);
}

.quick-sample-section {
  margin-bottom: 28px;
  padding: 22px 20px 24px;
  border: 1px solid var(--border-color, #e4e7eb);
  border-radius: 18px;
  background: var(--card-color, #fff);
  box-shadow: 0 2px 10px rgb(24 32 45 / 3%);
}

.quick-sample-section__title {
  margin: 0 0 22px;
  color: var(--text-color-1, #172033);
  font-size: 24px;
  font-weight: 700;
}

.quick-sample-section :deep(.n-form-item) {
  margin-bottom: 18px;
}

.quick-sample-section :deep(.n-form-item-label) {
  font-size: 16px;
  font-weight: 600;
}

.quick-sample-section :deep(.n-base-selection) {
  min-height: 44px;
}

.quick-sample-talent-action {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 2px;
}

.quick-sample-talent-action :deep(.n-button) {
  color: var(--primary-color, #f5222d);
  font-size: 20px;
  font-weight: 600;
}

.quick-sample-talent-action__plus {
  font-size: 26px;
  line-height: 1;
}

.quick-sample-talent-action__count {
  color: var(--text-color-2, #606873);
  font-size: 18px;
}

.quick-sample-talent-picker {
  max-width: 620px;
  margin-top: 12px;
  padding: 16px;
  border-radius: 12px;
  background: var(--hover-color, #f8f9fb);
}

.quick-sample-product-table {
  overflow: hidden;
  border: 1px solid var(--border-color, #e6e9ed);
  border-radius: 14px;
}

.quick-sample-product-table__head,
.quick-sample-product-table__row {
  display: grid;
  grid-template-columns: minmax(360px, 1.6fr) minmax(280px, 1fr) minmax(180px, .55fr);
  align-items: stretch;
}

.quick-sample-product-table__head {
  min-height: 70px;
  align-items: center;
  padding: 0 16px;
  color: var(--text-color-1, #172033);
  background: var(--hover-color, #fafafa);
  font-size: 18px;
  font-weight: 700;
}

.quick-sample-product-table__row {
  min-height: 164px;
  padding: 16px;
  border-top: 1px solid var(--divider-color, #edf0f3);
}

.quick-sample-product-table__head > span + span,
.quick-sample-product-table__row > div + div {
  padding-left: 18px;
  border-left: 1px solid var(--divider-color, #edf0f3);
}

.quick-sample-product-info {
  display: flex;
  align-items: center;
  gap: 18px;
  min-width: 0;
}

.quick-sample-product-info__cover {
  display: flex;
  flex: 0 0 128px;
  align-items: center;
  justify-content: center;
  width: 128px;
  height: 128px;
  overflow: hidden;
  border: 1px solid var(--border-color, #e3e6ea);
  border-radius: 4px;
  color: var(--text-color-3, #9ba1a8);
  background: var(--hover-color, #f7f8fa);
  font-size: 14px;
}

.quick-sample-product-info__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.quick-sample-product-info__text {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 8px;
  color: var(--text-color-2, #69717d);
  font-size: 17px;
}

.quick-sample-product-info__title {
  overflow: hidden;
  color: var(--primary-color, #f5222d);
  font-size: 20px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-sample-product-info__douyin {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  margin-right: 5px;
  border-radius: 5px;
  color: #fff;
  background: #101217;
  font-size: 14px;
  font-weight: 700;
}

.quick-sample-product-spec,
.quick-sample-product-remark {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-width: 0;
}

.quick-sample-product-spec :deep(.n-select) {
  flex: 1;
  min-width: 0;
}

.quick-sample-product-spec :deep(.n-base-selection__placeholder) {
  color: var(--primary-color, #f5222d);
  font-size: 18px;
}

.quick-sample-product-remark {
  align-items: flex-start;
  padding-top: 22px;
}

.quick-sample-product-remark > :deep(.n-button) {
  color: var(--primary-color, #f5222d);
  font-size: 18px;
}

.quick-sample-product-remark__editor {
  display: flex;
  width: 100%;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.quick-sample-product-remark__editor :deep(.n-button) {
  color: var(--primary-color, #f5222d);
}

.quick-sample-section--optional {
  padding-bottom: 4px;
}

.quick-sample-address-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

@media (max-width: 900px) {
  .quick-sample-header {
    min-height: 76px;
    padding: 12px 18px;
  }

  .quick-sample-header__title {
    gap: 9px;
    font-size: 20px;
  }

  .quick-sample-header__actions {
    gap: 8px;
  }

  .quick-sample-header__actions :deep(.n-button) {
    min-width: 78px;
    font-size: 15px;
  }

  .quick-sample-body {
    padding: 22px 16px 36px;
  }

  .quick-sample-step__number {
    width: 44px;
    height: 44px;
    font-size: 18px;
  }

  .quick-sample-step strong {
    font-size: 16px;
  }

  .quick-sample-step span:not(.quick-sample-step__number) {
    font-size: 13px;
  }

  .quick-sample-step__line {
    min-width: 20px;
    margin: 0 10px;
  }

  .quick-sample-section {
    padding: 18px 14px;
    border-radius: 12px;
  }

  .quick-sample-section__title {
    font-size: 20px;
  }

  .quick-sample-product-table__head {
    display: none;
  }

  .quick-sample-product-table__row {
    display: block;
    padding: 14px;
  }

  .quick-sample-product-table__row > div + div {
    margin-top: 16px;
    padding: 16px 0 0;
    border-top: 1px solid var(--divider-color, #edf0f3);
    border-left: 0;
  }

  .quick-sample-product-info__cover {
    flex-basis: 88px;
    width: 88px;
    height: 88px;
  }

  .quick-sample-product-info__title {
    font-size: 16px;
  }

  .quick-sample-product-info__text {
    font-size: 14px;
  }

  .quick-sample-address-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
