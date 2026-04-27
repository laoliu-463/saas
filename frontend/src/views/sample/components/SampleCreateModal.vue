<template>
  <n-modal :show="show" preset="card" title="新建寄样申请" style="width: 920px" @update:show="handleClose">
    <n-form ref="formRef" :model="formData" :rules="rules" label-placement="top">
      <n-grid cols="24" :x-gap="12" :y-gap="8">
        <n-form-item-gi :span="24" label="选择商品" path="productId">
          <n-select
            v-model:value="formData.productId"
            filterable
            remote
            clearable
            placeholder="搜索商品名称"
            :options="productOptions"
            :loading="loadingProducts"
            @search="handleSearchProduct"
          />
        </n-form-item-gi>

        <n-form-item-gi :span="24" label="达人搜索">
          <n-space wrap style="width: 100%">
            <n-input v-model:value="talentQuery.keyword" placeholder="昵称 / 达人号" clearable style="width: 220px" />
            <n-input v-model:value="talentQuery.region" placeholder="地区" clearable style="width: 140px" />
            <n-input-number v-model:value="talentQuery.minFans" :min="0" placeholder="最低粉丝" style="width: 140px" />
            <n-input-number v-model:value="talentQuery.maxFans" :min="0" placeholder="最高粉丝" style="width: 140px" />
            <n-input-number v-model:value="talentQuery.minScore" :min="0" :max="5" :step="0.1" placeholder="最低评分" style="width: 140px" />
            <n-button type="primary" :loading="loadingTalents" @click="fetchTalents(1)">搜索达人</n-button>
          </n-space>
        </n-form-item-gi>

        <n-form-item-gi :span="24" label="选择达人" path="talentId">
          <n-data-table
            size="small"
            :columns="talentColumns"
            :data="talentRows"
            :loading="loadingTalents"
            :pagination="talentPagination"
            @update:page="(page:number) => fetchTalents(page)"
          />
        </n-form-item-gi>

        <n-form-item-gi :span="24" v-if="selectedTalent" label="已选达人">
          <n-space align="center">
            <n-avatar round :src="selectedTalent.avatarUrl" :size="42" />
            <div>
              <div style="font-weight: 600">{{ selectedTalent.nickname }}</div>
              <div style="color: #666; font-size: 12px">
                粉丝 {{ formatFans(selectedTalent.fansCount) }} · 评分 {{ selectedTalent.creditScore ?? '-' }} · {{ selectedTalent.region || '-' }}
              </div>
            </div>
          </n-space>
        </n-form-item-gi>

        <n-form-item-gi :span="8" label="收货人" path="receiverName">
          <n-input v-model:value="formData.receiverName" placeholder="请输入收货人" />
        </n-form-item-gi>
        <n-form-item-gi :span="8" label="手机号" path="receiverPhone">
          <n-input v-model:value="formData.receiverPhone" placeholder="请输入手机号" />
        </n-form-item-gi>
        <n-form-item-gi :span="8" label="申请理由" path="reason">
          <n-input v-model:value="formData.reason" placeholder="例如：短视频测品" />
        </n-form-item-gi>

        <n-form-item-gi :span="24" label="收货地址" path="receiverAddress">
          <n-input v-model:value="formData.receiverAddress" type="textarea" placeholder="请输入完整收货地址" />
        </n-form-item-gi>
      </n-grid>
    </n-form>

    <template #footer>
      <n-space justify="end">
        <n-button @click="handleClose">取消</n-button>
        <n-button type="primary" :loading="submitting" @click="handleSubmit">提交申请</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import { NButton, useMessage } from 'naive-ui'
import { getProducts } from '../../../api/product'
import { createSample, getSamplePage, searchSampleTalents } from '../../../api/sample'
import { isTestEnv } from '../../../utils/env'

const props = defineProps<{ show: boolean }>()
const emit = defineEmits<{
  (e: 'update:show', value: boolean): void
  (e: 'success'): void
}>()

const message = useMessage()
const formRef = ref()
const submitting = ref(false)
const loadingProducts = ref(false)
const loadingTalents = ref(false)
const productOptions = ref<{ label: string; value: string }[]>([])
const talentRows = ref<any[]>([])
const selectedTalent = ref<any | null>(null)

const formData = reactive({
  productId: null as string | null,
  talentId: '',
  talentNickname: '',
  talentFansCount: null as number | null,
  talentCreditScore: null as number | null,
  talentMainCategory: '',
  receiverName: '',
  receiverPhone: '',
  receiverAddress: '',
  reason: ''
})

const rules = {
  productId: { required: true, message: '请选择商品', trigger: 'change' },
  talentId: { required: true, message: '请选择达人', trigger: 'change' },
  receiverName: { required: true, message: '请输入收货人', trigger: 'blur' },
  receiverPhone: { required: true, message: '请输入手机号', trigger: 'blur' },
  receiverAddress: { required: true, message: '请输入收货地址', trigger: 'blur' },
  reason: { required: true, message: '请输入申请理由', trigger: 'blur' }
}

const talentQuery = reactive({
  keyword: '',
  region: '',
  minFans: null as number | null,
  maxFans: null as number | null,
  minScore: null as number | null,
  page: 1,
  size: 8,
  total: 0
})

const talentPagination = computed(() => ({
  page: talentQuery.page,
  pageSize: talentQuery.size,
  itemCount: talentQuery.total,
  pageSlot: 7
}))

const talentColumns = [
  { title: '达人', key: 'nickname' },
  { title: '粉丝', key: 'fansCount', render: (row: any) => formatFans(row.fansCount) },
  { title: '评分', key: 'creditScore', render: (row: any) => row.creditScore ?? '-' },
  { title: '地区', key: 'region', render: (row: any) => row.region || '-' },
  {
    title: '操作',
    key: 'actions',
    render: (row: any) =>
      h(
        NButton,
        {
          size: 'small',
          type: formData.talentId === row.talentId ? 'success' : 'primary',
          ghost: formData.talentId === row.talentId,
          onClick: () => chooseTalent(row)
        },
        { default: () => (formData.talentId === row.talentId ? '已选择' : '选择') }
      )
  }
]

function formatFans(fans?: number) {
  if (fans === null || fans === undefined) return '-'
  if (fans >= 100000000) return `${(fans / 100000000).toFixed(1)}亿`
  if (fans >= 10000) return `${(fans / 10000).toFixed(1)}万`
  return `${fans}`
}

async function handleSearchProduct(keyword: string) {
  loadingProducts.value = true
  try {
    try {
      const res = await getProducts({ productName: keyword || undefined, size: 20 })
      const payload: any = (res as any)?.data ?? res
      const records = payload?.records || []
      productOptions.value = records.map((item: any) => ({
        label: item.productName || item.name || item.title || item.productId || item.id,
        value: item.id
      }))
    } catch (error) {
      if (!isTestEnv) {
        throw error
      }
      const fallback = await getSamplePage({ page: 1, size: 100 })
      const payload: any = (fallback as any)?.data ?? fallback
      const records = Array.isArray(payload?.records) ? payload.records : []
      const uniqueProducts = new Map<string, { label: string; value: string }>()
      records.forEach((item: any) => {
        if (!item?.productId || uniqueProducts.has(item.productId)) {
          return
        }
        const label = item.productName || item.productId
        if (keyword && !String(label).toLowerCase().includes(keyword.toLowerCase())) {
          return
        }
        uniqueProducts.set(item.productId, { label, value: item.productId })
      })
      productOptions.value = Array.from(uniqueProducts.values())
    }
  } finally {
    loadingProducts.value = false
  }
}

async function fetchTalents(page = 1) {
  loadingTalents.value = true
  try {
    talentQuery.page = page
    const res = await searchSampleTalents({
      keyword: talentQuery.keyword || undefined,
      region: talentQuery.region || undefined,
      minFans: talentQuery.minFans ?? undefined,
      maxFans: talentQuery.maxFans ?? undefined,
      minScore: talentQuery.minScore ?? undefined,
      page,
      size: talentQuery.size
    })
    const payload: any = (res as any)?.data ?? res
    talentRows.value = payload?.records || []
    talentQuery.total = payload?.total || 0
  } catch (error: any) {
    message.error(error?.message || '获取达人列表失败')
  } finally {
    loadingTalents.value = false
  }
}

function chooseTalent(row: any) {
  selectedTalent.value = row
  formData.talentId = row.talentId
  formData.talentNickname = row.nickname
  formData.talentFansCount = row.fansCount
  formData.talentCreditScore = row.creditScore
  formData.talentMainCategory = row.mainCategory
}

function buildRemark() {
  return [
    `申请理由：${formData.reason}`,
    `收货人：${formData.receiverName}`,
    `手机号：${formData.receiverPhone}`,
    `地址：${formData.receiverAddress}`
  ].join('\n')
}

function resetForm() {
  formData.productId = null
  formData.talentId = ''
  formData.talentNickname = ''
  formData.talentFansCount = null
  formData.talentCreditScore = null
  formData.talentMainCategory = ''
  formData.receiverName = ''
  formData.receiverPhone = ''
  formData.receiverAddress = ''
  formData.reason = ''
  selectedTalent.value = null
  formRef.value?.restoreValidation?.()
}

function handleClose() {
  emit('update:show', false)
}

function handleSubmit() {
  formRef.value?.validate(async (errors: any) => {
    if (errors) return
    submitting.value = true
    try {
      await createSample({
        talentId: formData.talentId,
        talentNickname: formData.talentNickname,
        talentFansCount: formData.talentFansCount,
        talentCreditScore: formData.talentCreditScore,
        talentMainCategory: formData.talentMainCategory,
        productId: formData.productId,
        quantity: 1,
        remark: buildRemark()
      })
      message.success('寄样申请已提交，状态为待审核')
      emit('success')
      handleClose()
      resetForm()
    } catch (error: any) {
      message.error(error?.message || '提交寄样申请失败')
    } finally {
      submitting.value = false
    }
  })
}

watch(
  () => props.show,
  (show) => {
    if (show) {
      handleSearchProduct('')
      fetchTalents(1)
    } else {
      resetForm()
    }
  }
)

onMounted(() => {
  handleSearchProduct('')
  fetchTalents(1)
})
</script>
