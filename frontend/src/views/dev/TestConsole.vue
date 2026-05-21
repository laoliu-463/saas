<template>
  <div class="test-console app-page">
    <PageHeader title="开发调试台" description="仅在 test 环境下可见，用于一键模拟业务场景、重置测试数据。" />

    <n-grid :cols="3" :x-gap="16" :y-gap="16">
      <n-gi>
        <n-card title="演示准备" size="small" class="app-panel">
          <p class="desc">初始化商品、达人、映射及寄样基线数据，为演示全链路做准备。</p>
          <n-button block type="primary" :loading="seedLoading" @click="handleAction('seed')">一键铺设演示数据</n-button>
        </n-card>
      </n-gi>
      <n-gi>
        <n-card title="数据重置" size="small" class="app-panel">
          <p class="desc">清空所有业务数据（订单、寄样、归因记录），恢复至初始状态。</p>
          <n-button block type="error" ghost :loading="resetLoading" @click="handleAction('reset')">清空业务数据</n-button>
        </n-card>
      </n-gi>
      <n-gi>
        <n-card title="订单模拟" size="small" class="app-panel">
          <p class="desc">模拟不同类型的订单回流，用于测试归因引擎的准确性。</p>
          <n-space vertical>
            <n-button block quaternary type="primary" @click="handleAction('gen-attributed')">生成已归因订单</n-button>
            <n-button block quaternary type="warning" @click="handleAction('gen-no-pick')">生成无标识订单</n-button>
            <n-button block quaternary type="error" @click="handleAction('gen-missing')">生成映射缺失订单</n-button>
          </n-space>
        </n-card>
      </n-gi>
      <n-gi>
        <n-card title="寄样物流" size="small" class="app-panel">
          <p class="desc">对「待发货」寄样单推进物流节点。需先执行「演示准备」获取寄样 ID。</p>
          <n-space vertical>
            <n-button
              block
              quaternary
              type="info"
              :disabled="!shippingSampleId"
              :loading="shipLoading"
              @click="handleAction('ship')"
            >
              {{ shippingSampleId ? '模拟发货' : '模拟发货（先执行演示准备）' }}
            </n-button>
            <n-button
              block
              quaternary
              type="success"
              :disabled="!shippedSampleId"
              :loading="signLoading"
              @click="handleAction('sign')"
            >
              {{ shippedSampleId ? '模拟签收' : '模拟签收（先执行发货）' }}
            </n-button>
          </n-space>
        </n-card>
      </n-gi>
      <n-gi :span="2">
        <n-card title="数据基线摘要" size="small" class="app-panel">
          <template v-if="seedResult">
            <n-descriptions label-placement="left" :column="2" size="small" bordered>
              <n-descriptions-item label="商品">3 条（主演示 / 映射缺失 / 无推广参数）</n-descriptions-item>
              <n-descriptions-item label="达人">7 条（A-G 覆盖各演示场景）</n-descriptions-item>
              <n-descriptions-item label="转链映射">2 条（TESTPS01 / TESTPS04）</n-descriptions-item>
              <n-descriptions-item label="寄样单">5 种状态（待发 / 快递中 / 待交作业 / 完成 / 拒绝 / 关闭）</n-descriptions-item>
              <n-descriptions-item label="待发货单号">{{ seedResult.shippingSampleRequestNo || '-' }}</n-descriptions-item>
              <n-descriptions-item label="最后 Seed 时间">{{ lastActionTime || '-' }}</n-descriptions-item>
            </n-descriptions>
          </template>
          <n-empty v-else description="执行「演示准备」后显示基线摘要" size="small" />
        </n-card>
      </n-gi>
    </n-grid>

    <n-card title="环境信息" class="app-panel" style="margin-top: 16px;">
      <n-descriptions label-placement="left" bordered :column="2">
        <n-descriptions-item label="当前环境">{{ currentEnv }}</n-descriptions-item>
        <n-descriptions-item label="后端基地址">/api</n-descriptions-item>
        <n-descriptions-item label="Test 服务状态">
          <n-tag type="success" size="small">运行中</n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="最后操作时间">{{ lastActionTime || '-' }}</n-descriptions-item>
      </n-descriptions>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useMessage } from 'naive-ui'
import PageHeader from '../../components/PageHeader.vue'
import {
  seedTestData,
  resetTestData,
  generateAttributedOrder,
  generateNoPickSourceOrder,
  generateMissingMappingOrder,
  testShipSample,
  testSignSample
} from '../../api/test'

const message = useMessage()
const seedLoading = ref(false)
const resetLoading = ref(false)
const shipLoading = ref(false)
const signLoading = ref(false)

const seedResult = ref<any>(null)
const shippingSampleId = ref<string | null>(null)
const shippedSampleId = ref<string | null>(null)
const lastActionTime = ref<string | null>(null)
const currentEnv = ref('TEST')

onMounted(async () => {
  try {
    const res = await fetch('/api/system/env')
    const body = await res.json()
    const label = body?.data?.environmentLabel
    if (label) {
      currentEnv.value = String(label).trim().toUpperCase()
    }
  } catch {
    currentEnv.value = 'TEST'
  }
})

const now = () => new Date().toLocaleString('zh-CN', { hour12: false })

const handleAction = async (action: string) => {
  const loadingMap: Record<string, ReturnType<typeof ref<boolean>>> = {
    seed: seedLoading,
    reset: resetLoading,
    ship: shipLoading,
    sign: signLoading
  }
  const loadingRef = loadingMap[action] ?? null
  if (loadingRef) loadingRef.value = true
  try {
    if (action === 'seed') {
      const res: any = await seedTestData()
      const data = res?.data ?? res
      seedResult.value = data
      shippingSampleId.value = data?.shippingSampleId ?? null
      shippedSampleId.value = null
      lastActionTime.value = now()
      message.success('演示数据铺设成功')
    } else if (action === 'reset') {
      await resetTestData()
      seedResult.value = null
      shippingSampleId.value = null
      shippedSampleId.value = null
      lastActionTime.value = now()
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
    } else if (action === 'ship') {
      const res: any = await testShipSample(shippingSampleId.value!)
      const data = res?.data ?? res
      shippedSampleId.value = shippingSampleId.value
      shippingSampleId.value = null
      lastActionTime.value = now()
      message.success(`寄样 ${data?.requestNo ?? ''} 已发货，快递单号：${data?.trackingNo ?? '-'}`)
    } else if (action === 'sign') {
      const res: any = await testSignSample(shippedSampleId.value!)
      const data = res?.data ?? res
      shippedSampleId.value = null
      lastActionTime.value = now()
      message.success(`寄样 ${data?.requestNo ?? ''} 已签收`)
    }
  } catch (err: any) {
    message.error(err?.response?.data?.msg || err?.message || '操作失败')
  } finally {
    if (loadingRef) loadingRef.value = false
  }
}
</script>

<style scoped>
.desc { color: var(--text-secondary); font-size: var(--text-sm); margin-bottom: 16px; min-height: 40px; }
</style>
