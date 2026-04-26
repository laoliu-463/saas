<template>
  <div class="mock-console">
    <n-space vertical :size="20">
      <div class="page-head">
        <div>
          <n-h1 style="margin-bottom: 8px">Mock 调试台</n-h1>
          <n-text depth="3">
            用这一个页面串起本地演示流程：重置数据、造订单、推物流、再跳到订单页、看板和寄样台核对结果。
          </n-text>
        </div>
        <n-tag type="warning" size="small">LOCAL MOCK</n-tag>
      </div>

      <n-alert v-if="!isMockEnv" type="warning" title="当前环境不是 Mock 环境">
        这个页面只建议在开发环境或 local-mock 环境使用。
      </n-alert>

      <n-grid responsive="screen" cols="1 m:2" :x-gap="16" :y-gap="16">
        <n-gi>
          <n-card title="数据初始化" bordered>
            <n-space vertical>
              <n-text depth="3">
                初始化会生成活动、商品、达人、寄样单、映射关系和基础订单数据；重置会清空当前演示数据。
              </n-text>
              <n-space>
                <n-button type="primary" :loading="loading.seed" @click="handleAction('seed', seedMockData)">
                  初始化数据
                </n-button>
                <n-button type="error" ghost :loading="loading.reset" @click="handleAction('reset', resetMockData)">
                  重置数据
                </n-button>
              </n-space>
            </n-space>
          </n-card>
        </n-gi>

        <n-gi>
          <n-card title="订单场景" bordered>
            <n-space vertical>
              <n-button :loading="loading.attributed" @click="handleAction('attributed', generateAttributedOrder)">
                生成已归因订单
              </n-button>
              <n-text depth="3">用于验证渠道归因、看板统计和寄样自动完成。</n-text>

              <n-button :loading="loading.noPick" @click="handleAction('noPick', generateNoPickSourceOrder)">
                生成无 pick_source 订单
              </n-button>
              <n-text depth="3">用于验证未归因原因：缺少归因码。</n-text>

              <n-button :loading="loading.missing" @click="handleAction('missing', generateMissingMappingOrder)">
                生成映射缺失订单
              </n-button>
              <n-text depth="3">用于验证未归因原因：MAPPING_NOT_FOUND。</n-text>

              <n-button type="info" ghost :loading="loading.sync" @click="handleAction('sync', syncMockOrders)">
                触发订单同步
              </n-button>
            </n-space>
          </n-card>
        </n-gi>

        <n-gi>
          <n-card title="物流 / 寄样" bordered>
            <n-space vertical>
              <n-input
                v-model:value="sampleRequestId"
                placeholder="请输入 sampleRequestId，例如在寄样台复制一条记录 ID"
              />
              <n-space>
                <n-button :disabled="!sampleRequestId" :loading="loading.ship" @click="handleLogistics('ship')">
                  模拟发货
                </n-button>
                <n-button :disabled="!sampleRequestId" :loading="loading.sign" @click="handleLogistics('sign')">
                  模拟签收
                </n-button>
              </n-space>
              <n-text depth="3">状态流转：待发货 -> 快递中 -> 待交作业 -> 已完成。</n-text>
            </n-space>
          </n-card>
        </n-gi>

        <n-gi>
          <n-card title="快捷跳转" bordered>
            <n-space vertical>
              <n-space>
                <n-button quaternary type="primary" @click="router.push('/product')">商品库</n-button>
                <n-button quaternary type="primary" @click="router.push('/orders')">订单工作台</n-button>
                <n-button quaternary type="primary" @click="router.push('/dashboard')">归因看板</n-button>
                <n-button quaternary type="primary" @click="router.push('/sample')">寄样台</n-button>
              </n-space>
              <n-text depth="3">
                推荐演示顺序：重置 -> 初始化 -> 生成已归因订单 -> 去订单页刷新 -> 去寄样台看自动完成 -> 去看板看统计变化。
              </n-text>
            </n-space>
          </n-card>
        </n-gi>
      </n-grid>

      <n-card v-if="lastAction" :title="`最近一次操作：${actionLabelMap[lastAction] || lastAction}`" bordered>
        <n-space vertical>
          <n-alert v-if="suggestion" type="info" :title="suggestion" />
          <pre class="result-box">{{ JSON.stringify(lastResult, null, 2) }}</pre>
        </n-space>
      </n-card>
    </n-space>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import {
  generateAttributedOrder,
  generateMissingMappingOrder,
  generateNoPickSourceOrder,
  mockShipSample,
  mockSignSample,
  resetMockData,
  seedMockData,
  syncMockOrders
} from '../../api/mock'
import { isMockEnv } from '../../utils/env'

type ActionKey = 'seed' | 'reset' | 'attributed' | 'noPick' | 'missing' | 'sync' | 'ship' | 'sign'

const router = useRouter()
const message = useMessage()

const sampleRequestId = ref('')
const lastResult = ref<unknown>(null)
const lastAction = ref<ActionKey | ''>('')

const loading = reactive<Record<ActionKey, boolean>>({
  seed: false,
  reset: false,
  attributed: false,
  noPick: false,
  missing: false,
  sync: false,
  ship: false,
  sign: false
})

const actionLabelMap: Record<ActionKey, string> = {
  seed: '初始化数据',
  reset: '重置数据',
  attributed: '生成已归因订单',
  noPick: '生成无 pick_source 订单',
  missing: '生成映射缺失订单',
  sync: '触发订单同步',
  ship: '模拟发货',
  sign: '模拟签收'
}

const suggestionMap: Record<ActionKey, string> = {
  seed: '初始化完成。现在可以去寄样台查看待发货和待交作业数据，再回到这里造订单。',
  reset: '重置完成。建议先重新初始化数据，再开始本轮演示。',
  attributed: '已归因订单生成成功。下一步去订单工作台刷新，再去寄样台确认是否自动完成。',
  noPick: '无归因码订单已生成。下一步去订单工作台切到未归因视图确认原因文案。',
  missing: '映射缺失订单已生成。下一步去订单工作台确认未归因原因是否为 MAPPING_NOT_FOUND。',
  sync: '订单同步已触发。稍后去订单工作台或看板刷新结果。',
  ship: '发货成功。下一步可继续执行“模拟签收”，把寄样单推进到待交作业。',
  sign: '签收成功。下一步生成已归因订单，验证待交作业是否自动完成。'
}

const suggestion = ref('')

async function handleAction(key: ActionKey, action: () => Promise<unknown>) {
  loading[key] = true
  try {
    const result = await action()
    lastResult.value = result
    lastAction.value = key
    suggestion.value = suggestionMap[key]
    message.success(`${actionLabelMap[key]}成功`)
  } catch (error: any) {
    lastResult.value = {
      message: error?.message || '未知错误',
      response: error?.response?.data || null
    }
    lastAction.value = key
    suggestion.value = ''
    message.error(`${actionLabelMap[key]}失败`)
  } finally {
    loading[key] = false
  }
}

function handleLogistics(type: 'ship' | 'sign') {
  const action = type === 'ship' ? () => mockShipSample(sampleRequestId.value) : () => mockSignSample(sampleRequestId.value)
  return handleAction(type, action)
}
</script>

<style scoped>
.mock-console {
  padding: 24px;
}

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.result-box {
  margin: 0;
  padding: 12px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  max-height: 360px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
}
</style>
