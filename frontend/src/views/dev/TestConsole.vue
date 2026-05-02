<template>
  <div class="test-console">
    <PageHeader title="开发调试台" description="仅在 test 环境下可见，用于一键模拟业务场景、重置测试数据。" />

    <n-grid :cols="3" :x-gap="16" :y-gap="16">
      <n-gi>
        <n-card title="演示准备" size="small">
          <p class="desc">初始化活动、商品、达人及基础映射关系，为演示全链路做准备。</p>
          <n-button block type="primary" :loading="seedLoading" @click="handleAction('seed')">一键铺设演示数据</n-button>
        </n-card>
      </n-gi>
      <n-gi>
        <n-card title="数据重置" size="small">
          <p class="desc">清空所有业务数据（订单、寄样、归因记录），将系统恢复至初始状态。</p>
          <n-button block type="error" ghost :loading="resetLoading" @click="handleAction('reset')">清空业务数据</n-button>
        </n-card>
      </n-gi>
      <n-gi>
        <n-card title="订单模拟" size="small">
          <p class="desc">模拟不同类型的订单回流，用于测试归因引擎的准确性。</p>
          <n-space vertical>
            <n-button block quaternary type="primary" @click="handleAction('gen-attributed')">生成已归因订单</n-button>
            <n-button block quaternary type="warning" @click="handleAction('gen-no-pick')">生成无标识订单</n-button>
            <n-button block quaternary type="error" @click="handleAction('gen-missing')">生成映射缺失订单</n-button>
          </n-space>
        </n-card>
      </n-gi>
    </n-grid>

    <n-card title="环境信息" style="margin-top: 24px;">
      <n-descriptions label-placement="left" bordered :column="2">
        <n-descriptions-item label="当前 Profile">test (Test Enabled)</n-descriptions-item>
        <n-descriptions-item label="后端基地址">/api</n-descriptions-item>
        <n-descriptions-item label="Test 服务状态">
          <n-tag type="success" size="small">运行中</n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="最后重置时间">-</n-descriptions-item>
      </n-descriptions>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useMessage } from 'naive-ui'
import PageHeader from '../../components/PageHeader.vue'
import { seedTestData, resetTestData, generateAttributedOrder, generateNoPickSourceOrder, generateMissingMappingOrder } from '../../api/test'

const message = useMessage()
const seedLoading = ref(false)
const resetLoading = ref(false)

const handleAction = async (action: string) => {
  const loadingRef = action === 'seed' ? seedLoading : action === 'reset' ? resetLoading : null
  if (loadingRef) loadingRef.value = true
  try {
    if (action === 'seed') {
      await seedTestData()
      message.success('演示数据铺设成功')
    } else if (action === 'reset') {
      await resetTestData()
      message.success('数据已重置')
    } else if (action === 'gen-attributed') {
      await generateAttributedOrder()
      message.success('已模拟生成一条可归因订单')
    } else if (action === 'gen-no-pick') {
      await generateNoPickSourceOrder()
      message.success('已模拟生成一条无 pick_source 订单')
    } else if (action === 'gen-missing') {
      await generateMissingMappingOrder()
      message.success('已模拟生成一条 Mapping 丢失订单')
    }
  } catch (err: any) {
    message.error(err?.response?.data?.msg || err?.message || '操作失败')
  } finally {
    if (loadingRef) loadingRef.value = false
  }
}
</script>

<style scoped>
.test-console { padding: 24px; }
.desc { color: #999; font-size: 13px; margin-bottom: 16px; min-height: 40px; }
</style>

