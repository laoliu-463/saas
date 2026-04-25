<template>
  <n-modal
    :show="show"
    preset="dialog"
    title="生成转链"
    positive-text="生成"
    negative-text="取消"
    @positive-click="handleSubmit"
    @update:show="updateShow"
  >
    <n-space vertical>
      <n-input v-model:value="promotionExternalId" placeholder="externalUniqueId（可选）" />
      <n-select v-model:value="promotionScene" :options="promotionSceneOptions" />
    </n-space>
  </n-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { convertActivityProductLink } from '../../../api/activityProduct'

const props = defineProps<{ show: boolean; activityId: string | number; productId: string | number }>()
const emit = defineEmits(['update:show', 'success'])
const message = useMessage()

const promotionExternalId = ref('')
const promotionScene = ref(4)

const promotionSceneOptions = [
  { label: '默认场景 (4)', value: 4 },
  { label: '直播间场景 (2)', value: 2 },
  { label: '橱窗场景 (1)', value: 1 }
]

watch(
  () => props.show,
  (val) => {
    if (val) {
      promotionExternalId.value = ''
      promotionScene.value = 4
    }
  }
)

const updateShow = (val: boolean) => emit('update:show', val)

const handleSubmit = async () => {
  try {
    const res: any = await convertActivityProductLink(props.activityId, props.productId, {
      externalUniqueId: promotionExternalId.value.trim() || undefined,
      promotionScene: promotionScene.value,
      needShortLink: true
    })
    const data = res?.data || {}
    message.success(data.shortLink ? `转链成功，短链：${data.shortLink}` : '转链成功')
    message.info('转链只会生成推广映射，订单页要更新还需要手动执行一次“同步订单”。')
    emit('success', data)
    updateShow(false)
    return true
  } catch (error: any) {
    message.error(error?.response?.data?.msg || error?.message || '转链失败')
    emit('success', null)
    return false
  }
}
</script>
