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
      <span class="dialog-tip">系统将根据当前登录渠道自动生成推广链接并写入归因映射。</span>
    </n-space>
  </n-modal>
</template>

<script setup lang="ts">
import { useMessage } from 'naive-ui'
import { convertActivityProductLink } from '../../../api/activityProduct'

const props = defineProps<{ show: boolean; activityId: string | number; productId: string | number }>()
const emit = defineEmits(['update:show', 'success'])
const message = useMessage()

const updateShow = (val: boolean) => emit('update:show', val)

const handleSubmit = async () => {
  try {
    const res: any = await convertActivityProductLink(props.activityId, props.productId, { scene: 'PRODUCT_DETAIL' })
    const data = res?.data || {}
    message.success(data.shortLink ? `转链成功，短链：${data.shortLink}` : '转链成功')
    message.info('转链完成后，后端会按同步任务自动处理后续订单回流与归因。')
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
