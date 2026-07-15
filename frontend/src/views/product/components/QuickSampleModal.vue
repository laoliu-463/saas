<template>
  <n-drawer
    v-model:show="visible"
    placement="right"
    :width="DRAWER_WIDTH_PX.lg"
    data-testid="quick-sample-drawer"
    @after-leave="resetForm"
  >
    <n-drawer-content closable>
      <template #header>
        <div class="drawer-header">
          <div class="drawer-title">批量申样</div>
          <div class="drawer-subtitle">选择渠道、合作达人和商品规格</div>
        </div>
      </template>

      <div class="quick-sample-drawer">
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

              <div class="quick-sample-talent-list" role="table" aria-label="已选合作达人" data-testid="quick-sample-selected-talents">
                <div class="quick-sample-talent-list__head" role="row">
                  <span role="columnheader">达人信息</span>
                  <span role="columnheader">收货地址</span>
                  <span role="columnheader">操作</span>
                </div>
                <div class="quick-sample-talent-list__body" role="rowgroup">
                  <div
                    v-for="row in selectedTalentRows"
                    :key="row.value"
                    class="quick-sample-talent-list__row"
                    role="row"
                    :data-testid="`quick-sample-selected-talent-${row.value}`"
                  >
                    <div class="quick-sample-talent-list__info" role="cell">
                      <n-avatar round :size="52" :src="resolveSafeAvatarUrl(row.avatarUrl)">
                        {{ (row.nickname || row.douyinNo || '达').slice(0, 1) }}
                      </n-avatar>
                      <div class="quick-sample-talent-list__info-copy">
                        <strong class="quick-sample-talent-list__name">
                          <span class="quick-sample-talent-list__douyin-icon" aria-hidden="true">♪</span>
                          {{ row.nickname || row.douyinNo || '未命名达人' }}
                        </strong>
                        <span>抖音号：{{ row.douyinNo || row.value || '-' }}</span>
                        <span>达人等级：{{ row.talentLevel || '-' }}</span>
                        <span v-if="row.remark" class="quick-sample-talent-list__profile-remark">达人备注：{{ row.remark }}</span>
                      </div>
                    </div>

                    <div class="quick-sample-talent-list__address" role="cell">
                      <div v-if="talentAddressFor(row.value).loading" class="quick-sample-talent-list__address-loading">正在读取地址…</div>
                      <template v-else>
                        <strong v-if="hasTalentAddress(row.value)">
                          {{ talentAddressFor(row.value).recipientName || '收货人未填写' }}
                          {{ talentAddressFor(row.value).recipientPhone }}
                        </strong>
                        <span v-if="talentAddressFor(row.value).recipientAddress" class="quick-sample-talent-list__address-text">
                          {{ talentAddressFor(row.value).recipientAddress }}
                        </span>
                        <span v-else class="quick-sample-talent-list__address-empty">暂无默认收货地址</span>
                      </template>
                      <div class="quick-sample-talent-list__address-actions">
                        <n-button text type="primary" :data-testid="`quick-sample-address-edit-${row.value}`" @click="startTalentAddressEdit(row)">
                          {{ hasTalentAddress(row.value) ? '编辑' : '添加' }}
                        </n-button>
                        <button type="button" class="quick-sample-talent-list__address-plus" :aria-label="`编辑${row.nickname || '达人'}地址`" @click="startTalentAddressEdit(row)">＋</button>
                      </div>
                    </div>

                    <div class="quick-sample-talent-list__actions" role="cell">
                      <n-button text type="primary" :data-testid="`quick-sample-remark-edit-${row.value}`" @click="toggleTalentRemark(row.value)">备注</n-button>
                      <n-button text type="primary" :data-testid="`quick-sample-remove-talent-${row.value}`" @click="removeTalent(row.value)">删除</n-button>
                    </div>

                    <div v-if="remarkEditingTalentValue === row.value" class="quick-sample-talent-list__remark-editor">
                      <span>本次寄样备注</span>
                      <n-input
                        v-model:value="form.remark"
                        type="textarea"
                        :autosize="{ minRows: 2, maxRows: 4 }"
                        placeholder="请输入本次批量寄样备注"
                        data-testid="quick-sample-remark"
                      />
                      <n-button text size="small" data-testid="quick-sample-remark-done" @click="remarkEditingTalentValue = ''">完成</n-button>
                    </div>
                  </div>
                  <div v-if="!selectedTalentRows.length" class="quick-sample-talent-list__empty" data-testid="quick-sample-selected-talent-empty">
                    请选择合作达人
                  </div>
                </div>
              </div>

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
            </section>

            <section class="quick-sample-section" data-testid="quick-sample-spec-section">
              <h3 class="quick-sample-section__title">选择商品规格</h3>

              <div class="quick-sample-product-table" role="table" aria-label="商品规格">
                <div class="quick-sample-product-table__head" role="row">
                  <span role="columnheader">商品信息</span>
                  <span role="columnheader">商品规格</span>
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

                </div>
              </div>
            </section>
          </n-form>
        </main>
      </div>

      <template #footer>
        <n-space justify="end" :size="8">
          <n-button data-testid="quick-sample-close" @click="visible = false">关闭</n-button>
          <n-button
            type="primary"
            :loading="submitting"
            data-testid="quick-sample-submit"
            @click="submit"
          >
            提交
          </n-button>
        </n-space>
      </template>
    </n-drawer-content>
  </n-drawer>

  <n-modal
    v-model:show="addressModalVisible"
    preset="card"
    :title="addressModalTitle"
    closable
    :mask-closable="false"
    :style="{ width: '520px', maxWidth: 'calc(100vw - 32px)' }"
    data-testid="quick-sample-address-modal"
  >
    <n-form label-placement="top" class="quick-sample-address-form">
      <n-form-item label="收货人" required>
        <n-input v-model:value="addressDraft.recipientName" placeholder="请输入收货人姓名" data-testid="quick-sample-address-recipient" />
      </n-form-item>
      <n-form-item label="手机号" required>
        <n-input v-model:value="addressDraft.recipientPhone" placeholder="请输入收货人手机号" data-testid="quick-sample-address-phone" />
      </n-form-item>
      <n-form-item label="收货地址" required>
        <n-input
          v-model:value="addressDraft.recipientAddress"
          type="textarea"
          :autosize="{ minRows: 3, maxRows: 5 }"
          placeholder="请输入详细收货地址"
          data-testid="quick-sample-address-detail"
        />
      </n-form-item>
    </n-form>

    <template #footer>
      <n-space justify="end" :size="8">
        <n-button :disabled="addressSaving" data-testid="quick-sample-address-cancel" @click="cancelTalentAddressEdit">取消</n-button>
        <n-button type="primary" :loading="addressSaving" data-testid="quick-sample-address-save" @click="saveTalentAddress">保存地址</n-button>
      </n-space>
    </template>
  </n-modal>

  <QuickSampleTalentPicker
    v-model:show="talentSelectionVisible"
    :rows="talentOptions"
    :selected-values="form.talentIds"
    :loading="talentLoading"
    :empty-text="talentEmptyHint"
    @update:selected-values="handleTalentSelection"
  />
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
  updateTalentShippingAddress,
  parsePrivateTalentPoolResponse,
  toPrivateTalentSelectOption
} from '../../../api/talent'
import { useAuthStore } from '../../../stores/auth'
import { DRAWER_WIDTH_PX } from '../../../constants/ui'
import { resolveSafeAvatarUrl } from '../../../utils/media'
import { loadSampleChannelOptions } from '../../sample/sample-user-filter-options'
import ProductSpecSelector from './ProductSpecSelector.vue'
import QuickSampleTalentPicker, { type QuickSampleTalentRow } from './QuickSampleTalentPicker.vue'

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
const talentOptions = ref<QuickSampleTalentRow[]>([])
/** 下拉选项提交的是抖音 UID，收货地址接口需要达人记录 UUID。 */
const talentRecordIdByValue = ref<Record<string, string>>({})
type TalentAddress = {
  recipientName: string
  recipientPhone: string
  recipientAddress: string
  loaded: boolean
  loading: boolean
}
const talentShippingByValue = ref<Record<string, TalentAddress>>({})
const addressEditingTalentValue = ref('')
const addressDraft = ref({ recipientName: '', recipientPhone: '', recipientAddress: '' })
const addressSaving = ref(false)
const addressModalVisible = ref(false)
/** 管理员可以选渠道并查询该渠道的达人，非管理员只能选自己的私海 */
const isAdmin = computed(() => authStore.isAdmin)
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

const talentSelectionVisible = ref(false)
const remarkEditingTalentValue = ref('')
const selectedTalentCount = computed(() => form.value.talentIds.length)
const selectedTalentRows = computed(() => form.value.talentIds
  .map((value) => talentOptions.value.find((row) => row.value === value))
  .filter((row): row is QuickSampleTalentRow => Boolean(row)))
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
  talentSelectionVisible.value = true
}

const mapTalentSelectOptions = (records: any[]): QuickSampleTalentRow[] => {
  const recordIdMap: Record<string, string> = {}
  const addressMap: Record<string, TalentAddress> = {}
  const options = records
    .map((item: any) => {
      const option = toPrivateTalentSelectOption(item)
      const optionValue = String(option?.value || '').trim()
      const recordId = String(item?.id || '').trim()
      if (optionValue && UUID_PATTERN.test(recordId)) {
        recordIdMap[optionValue] = recordId
      }
      const recipientName = String(item?.recipientName || item?.claim?.recipientName || item?.shippingRecipientName || '').trim()
      const recipientPhone = String(item?.recipientPhone || item?.claim?.recipientPhone || item?.shippingRecipientPhone || '').trim()
      const recipientAddress = String(item?.recipientAddress || item?.claim?.recipientAddress || item?.shippingRecipientAddress || '').trim()
      if (optionValue) {
        addressMap[optionValue] = {
          recipientName,
          recipientPhone,
          recipientAddress,
          loaded: Boolean(recipientName || recipientPhone || recipientAddress),
          loading: false
        }
      }
      return {
        value: optionValue,
        nickname: String(item?.nickname || item?.talentName || '').trim(),
        douyinNo: String(item?.douyinNo || '').trim(),
        fansCount: item?.fansCount ?? item?.fans ?? null,
        avatarUrl: String(item?.avatarUrl || '').trim() || null,
        talentLevel: String(item?.talentLevel || item?.level || '').trim() || null,
        remark: String(item?.remark || '').trim() || null,
        recipientName: recipientName || null,
        recipientPhone: recipientPhone || null,
        recipientAddress: recipientAddress || null
      }
    })
    .filter((item: QuickSampleTalentRow) => item.value && (item.nickname || item.douyinNo))
  talentRecordIdByValue.value = recordIdMap
  talentShippingByValue.value = addressMap
  return options
}

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
  talentRecordIdByValue.value = {}
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
  talentRecordIdByValue.value = {}
  talentShippingByValue.value = {}
  addressEditingTalentValue.value = ''
  remarkEditingTalentValue.value = ''
  talentSelectionVisible.value = false
  clearAddressFields()
  if (form.value.channelUserId) {
    loadTalents()
  }
}

const handleTalentSelection = (talentIds: string[]) => {
  form.value.talentIds = talentIds.slice(0, 20)
  addressEditingTalentValue.value = ''
  remarkEditingTalentValue.value = ''
}

const clearAddressFields = () => {
  form.value.recipientName = ''
  form.value.recipientPhone = ''
  form.value.recipientAddress = ''
}

const talentAddressFor = (value: string): TalentAddress => {
  return talentShippingByValue.value[value] || {
    recipientName: '',
    recipientPhone: '',
    recipientAddress: '',
    loaded: true,
    loading: false
  }
}

const hasTalentAddress = (value: string) => {
  const address = talentAddressFor(value)
  return Boolean(address.recipientName || address.recipientPhone || address.recipientAddress)
}

const addressModalTitle = computed(() => {
  const value = addressEditingTalentValue.value
  return value && hasTalentAddress(value) ? '编辑收货地址' : '添加收货地址'
})

const applyFirstTalentAddress = (value: string, address: TalentAddress) => {
  if (String(form.value.talentIds?.[0] || '').trim() !== value) return
  form.value.recipientName = address.recipientName
  form.value.recipientPhone = address.recipientPhone
  form.value.recipientAddress = address.recipientAddress
}

const ensureTalentShippingAddress = async (value: string) => {
  const current = talentAddressFor(value)
  if (current.loading || current.loaded) {
    if (current.loaded) applyFirstTalentAddress(value, current)
    return current
  }

  const talentRecordId = talentRecordIdByValue.value[value]
  if (!talentRecordId) return current

  talentShippingByValue.value = {
    ...talentShippingByValue.value,
    [value]: { ...current, loading: true }
  }
  try {
    const addr = await getTalentShippingAddress(talentRecordId)
    const next: TalentAddress = {
      recipientName: String(addr?.recipientName || '').trim(),
      recipientPhone: String(addr?.recipientPhone || '').trim(),
      recipientAddress: String(addr?.recipientAddress || '').trim(),
      loaded: true,
      loading: false
    }
    talentShippingByValue.value = { ...talentShippingByValue.value, [value]: next }
    applyFirstTalentAddress(value, next)
    return next
  } catch {
    const next = { ...current, loaded: true, loading: false }
    talentShippingByValue.value = { ...talentShippingByValue.value, [value]: next }
    return next
  }
}

const startTalentAddressEdit = (row: QuickSampleTalentRow) => {
  const current = talentAddressFor(row.value)
  addressDraft.value = {
    recipientName: current.recipientName,
    recipientPhone: current.recipientPhone,
    recipientAddress: current.recipientAddress
  }
  addressEditingTalentValue.value = row.value
  addressModalVisible.value = true
}

const cancelTalentAddressEdit = () => {
  addressModalVisible.value = false
  addressEditingTalentValue.value = ''
}

const saveTalentAddress = async () => {
  const value = addressEditingTalentValue.value
  const talentRecordId = talentRecordIdByValue.value[value]
  if (!talentRecordId) {
    message.warning('缺少达人记录 ID，暂时无法保存地址')
    return
  }
  const normalizedAddress = {
    recipientName: addressDraft.value.recipientName.trim(),
    recipientPhone: addressDraft.value.recipientPhone.trim(),
    recipientAddress: addressDraft.value.recipientAddress.trim()
  }
  if (!normalizedAddress.recipientName || !normalizedAddress.recipientPhone || !normalizedAddress.recipientAddress) {
    message.warning('请完整填写收货人、手机号和收货地址')
    return
  }
  addressSaving.value = true
  try {
    const saved: any = await updateTalentShippingAddress(talentRecordId, normalizedAddress)
    const next: TalentAddress = {
      recipientName: String(saved?.shippingRecipientName ?? saved?.recipientName ?? normalizedAddress.recipientName).trim(),
      recipientPhone: String(saved?.shippingRecipientPhone ?? saved?.recipientPhone ?? normalizedAddress.recipientPhone).trim(),
      recipientAddress: String(saved?.shippingRecipientAddress ?? saved?.recipientAddress ?? normalizedAddress.recipientAddress).trim(),
      loaded: true,
      loading: false
    }
    talentShippingByValue.value = { ...talentShippingByValue.value, [value]: next }
    applyFirstTalentAddress(value, next)
    addressDraft.value = { ...normalizedAddress }
    cancelTalentAddressEdit()
    message.success('已保存默认收货地址')
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '保存默认收货地址失败' })
  } finally {
    addressSaving.value = false
  }
}

const toggleTalentRemark = (value: string) => {
  remarkEditingTalentValue.value = remarkEditingTalentValue.value === value ? '' : value
}

const removeTalent = (value: string) => {
  form.value.talentIds = form.value.talentIds.filter((item) => item !== value)
  if (remarkEditingTalentValue.value === value) remarkEditingTalentValue.value = ''
  if (addressEditingTalentValue.value === value) cancelTalentAddressEdit()
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
}, { immediate: true })

/** 选中达人后加载每位达人的默认收货地址；提交协议仍沿用首位达人地址兼容逻辑。 */
watch(() => form.value.talentIds, (talentIds) => {
  const selectedValues = talentIds.map((value) => String(value || '').trim()).filter(Boolean)
  if (!selectedValues.length) {
    clearAddressFields()
    return
  }

  clearAddressFields()
  selectedValues.forEach((value) => {
    void ensureTalentShippingAddress(value)
  })
}, { deep: true })

const resetForm = () => {
  form.value = { channelUserId: '', talentIds: [], skuId: '', specification: '', quantity: 1, recipientName: '', recipientPhone: '', recipientAddress: '', remark: '' }
  skuOptions.value = []
  talentRecordIdByValue.value = {}
  talentShippingByValue.value = {}
  addressEditingTalentValue.value = ''
  addressDraft.value = { recipientName: '', recipientPhone: '', recipientAddress: '' }
  addressModalVisible.value = false
  talentSelectionVisible.value = false
  remarkEditingTalentValue.value = ''
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
  container-type: inline-size;
  color: var(--text-color-1, #172033);
  background: var(--body-color, #fff);
}

.quick-sample-drawer :deep(.n-drawer-body-content) {
  padding: 0;
}

.drawer-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.drawer-title {
  color: var(--text-primary, #172033);
  font-size: 16px;
  font-weight: 600;
}

.drawer-subtitle {
  color: var(--text-muted, #69717d);
  font-size: 12px;
  line-height: 1.4;
}

.quick-sample-body {
  width: 100%;
  margin: 0 auto;
  padding: 18px 22px 28px;
  box-sizing: border-box;
}

.quick-sample-steps {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}

.quick-sample-step {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
  color: var(--text-color-3, #a0a6ad);
}

.quick-sample-step__number {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  color: var(--text-color-3, #8a919a);
  background: #f0f1f3;
  font-size: 15px;
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
  font-size: 14px;
  font-weight: 600;
}

.quick-sample-step span:not(.quick-sample-step__number) {
  margin-top: 2px;
  font-size: 12px;
}

.quick-sample-step__line {
  flex: 1;
  min-width: 20px;
  height: 1px;
  margin: 0 10px;
  background: var(--divider-color, #e7e9ec);
}

.quick-sample-section {
  margin-bottom: 18px;
  padding: 16px 18px 18px;
  border: 1px solid var(--border-color, #e4e7eb);
  border-radius: 12px;
  background: var(--card-color, #fff);
  box-shadow: 0 2px 10px rgb(24 32 45 / 3%);
}

.quick-sample-section__title {
  margin: 0 0 14px;
  color: var(--text-color-1, #172033);
  font-size: 14px;
  font-weight: 700;
}

.quick-sample-section :deep(.n-form-item) {
  margin-bottom: 14px;
}

.quick-sample-section :deep(.n-form-item-label) {
  font-size: 13px;
  font-weight: 600;
}

.quick-sample-section :deep(.n-base-selection) {
  min-height: 40px;
}

.quick-sample-talent-action {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 12px;
}

.quick-sample-talent-action :deep(.n-button) {
  color: var(--primary-color, #f5222d);
  font-size: 14px;
  font-weight: 600;
}

.quick-sample-talent-action__plus {
  font-size: 18px;
  line-height: 1;
}

.quick-sample-talent-action__count {
  color: var(--text-color-2, #606873);
  font-size: 13px;
}

.quick-sample-talent-list {
  overflow: hidden;
  border: 1px solid var(--border-color, #e4e7eb);
  border-radius: 10px;
  background: var(--card-color, #fff);
}

.quick-sample-talent-list__head,
.quick-sample-talent-list__row {
  display: grid;
  grid-template-columns: minmax(220px, 1.1fr) minmax(300px, 1.8fr) 88px;
  align-items: center;
}

.quick-sample-talent-list__head {
  min-height: 44px;
  padding: 0 14px;
  color: var(--text-color-1, #172033);
  background: #fafafa;
  font-size: 14px;
  font-weight: 600;
}

.quick-sample-talent-list__body {
  max-height: 520px;
  overflow-y: auto;
}

.quick-sample-talent-list__row {
  min-height: 132px;
  padding: 14px 16px;
  border-top: 1px solid var(--divider-color, #edf0f3);
  column-gap: 18px;
}

.quick-sample-talent-list__info {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.quick-sample-talent-list__info :deep(.n-avatar) {
  flex: 0 0 auto;
}

.quick-sample-talent-list__info-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
  color: var(--text-color-2, #606873);
  font-size: 12px;
  line-height: 1.4;
}

.quick-sample-talent-list__name {
  display: flex;
  min-width: 0;
  align-items: center;
  overflow: hidden;
  color: var(--text-color-1, #172033);
  font-size: 15px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-sample-talent-list__douyin-icon {
  display: inline-flex;
  flex: 0 0 auto;
  width: 18px;
  height: 18px;
  align-items: center;
  justify-content: center;
  margin-right: 5px;
  border-radius: 4px;
  color: #fff;
  background: #101217;
  font-size: 12px;
  font-weight: 700;
}

.quick-sample-talent-list__profile-remark {
  overflow: hidden;
  color: var(--text-muted, #999);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-sample-talent-list__address {
  min-width: 0;
  padding: 9px 12px;
  border: 1px solid var(--border-color, #d9d9d9);
  border-radius: 8px;
  color: var(--text-color-1, #172033);
  font-size: 12px;
  line-height: 1.55;
}

.quick-sample-talent-list__address > strong,
.quick-sample-talent-list__address-text {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
}

.quick-sample-talent-list__address > strong {
  font-size: 13px;
  white-space: nowrap;
}

.quick-sample-talent-list__address-text {
  color: var(--text-color-1, #172033);
  font-weight: 600;
  white-space: normal;
}

.quick-sample-talent-list__address-empty,
.quick-sample-talent-list__address-loading {
  display: block;
  color: var(--text-muted, #999);
}

.quick-sample-talent-list__address-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-top: 4px;
}

.quick-sample-talent-list__address-plus {
  width: 22px;
  height: 22px;
  padding: 0;
  border: 1px solid var(--primary-color, #f5222d);
  border-radius: 50%;
  color: var(--primary-color, #f5222d);
  background: transparent;
  font-size: 18px;
  line-height: 18px;
  cursor: pointer;
}

.quick-sample-talent-list__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  white-space: nowrap;
}

.quick-sample-talent-list__actions :deep(.n-button) {
  color: var(--primary-color, #f5222d);
  font-size: 13px;
}

.quick-sample-address-form {
  padding-top: 2px;
}

.quick-sample-address-form :deep(.n-form-item) {
  margin-bottom: 14px;
}

.quick-sample-address-form :deep(.n-form-item-label) {
  font-size: 13px;
  font-weight: 600;
}

.quick-sample-talent-list__remark-editor {
  display: grid;
  grid-column: 1 / -1;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding-top: 10px;
  color: var(--text-color-2, #606873);
  font-size: 13px;
}

.quick-sample-talent-list__empty {
  display: flex;
  min-height: 96px;
  align-items: center;
  justify-content: center;
  color: var(--text-muted, #999);
  font-size: 14px;
}

.quick-sample-product-table {
  overflow: visible;
  border: 1px solid var(--border-color, #e6e9ed);
  border-radius: 14px;
}

.quick-sample-product-table__head,
.quick-sample-product-table__row {
  display: grid;
  align-items: stretch;
}

.quick-sample-product-table__head {
  display: none;
}

.quick-sample-product-table__row {
  display: block;
  padding: 12px;
  border-top: 1px solid var(--divider-color, #edf0f3);
}

.quick-sample-product-table__row > div + div {
  margin-top: 12px;
  padding: 12px 0 0;
  border-top: 1px solid var(--divider-color, #edf0f3);
  border-left: 0;
}

.quick-sample-product-info {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
}

.quick-sample-product-info__cover {
  display: flex;
  flex: 0 0 72px;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
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
  gap: 3px;
  color: var(--text-color-2, #69717d);
  font-size: 12px;
}

.quick-sample-product-info__title {
  overflow: hidden;
  color: var(--primary-color, #f5222d);
  font-size: 13px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-sample-product-info__douyin {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  margin-right: 4px;
  border-radius: 4px;
  color: #fff;
  background: #101217;
  font-size: 12px;
  font-weight: 700;
}

.quick-sample-product-spec,
.quick-sample-product-remark {
  display: flex;
  align-items: flex-start;
  justify-content: flex-start;
  gap: 6px;
  min-width: 0;
}

.quick-sample-product-spec {
  flex-direction: column;
}

.quick-sample-product-spec :deep(.n-select) {
  width: 100%;
  min-width: 0;
}

.quick-sample-product-spec :deep(.n-input-number) {
  flex: 0 0 auto;
  width: 120px;
}

.quick-sample-product-spec :deep(.n-base-selection__placeholder) {
  color: var(--primary-color, #f5222d);
  font-size: 14px;
}

.quick-sample-product-remark {
  padding-top: 0;
}

.quick-sample-product-remark > :deep(.n-button) {
  color: var(--primary-color, #f5222d);
  font-size: 14px;
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

@media (max-width: 900px) {
  .quick-sample-body {
    padding: 14px 12px 20px;
  }

  .quick-sample-step__number {
    width: 34px;
    height: 34px;
    font-size: 14px;
  }

  .quick-sample-step strong {
    font-size: 13px;
  }

  .quick-sample-step span:not(.quick-sample-step__number) {
    font-size: 11px;
  }

  .quick-sample-step__line {
    min-width: 16px;
    margin: 0 8px;
  }

  .quick-sample-section {
    padding: 12px 10px 14px;
    border-radius: 12px;
  }

  .quick-sample-section__title {
    font-size: 14px;
  }

  .quick-sample-talent-list__head,
  .quick-sample-talent-list__row {
    grid-template-columns: minmax(150px, 1fr) minmax(170px, 1.2fr) 78px;
  }

  .quick-sample-talent-list__row {
    column-gap: 8px;
    padding: 10px;
  }

  .quick-sample-talent-list__name {
    font-size: 14px;
  }

  .quick-sample-talent-list__info-copy,
  .quick-sample-talent-list__address {
    font-size: 12px;
  }

  .quick-sample-talent-list__remark-editor {
    grid-template-columns: 1fr;
  }
}
</style>
