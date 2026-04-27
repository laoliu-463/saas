<template>
  <div class="dashboard-page">
    <PageHeader title="业务概览" description="实时掌握商品推广进度、订单归因率及招商业绩分布。" />

    <n-grid :cols="4" :x-gap="16" :y-gap="16" style="margin-bottom: 24px;">
      <n-gi v-for="stat in stats" :key="stat.label">
        <n-card :bordered="false" class="stat-card">
          <n-statistic :label="stat.label">
            {{ stat.value }}
          </n-statistic>
          <div class="stat-footer">
            <span class="stat-trend" :class="stat.trend > 0 ? 'up' : 'down'">
              {{ stat.trend > 0 ? '↑' : '↓' }} {{ Math.abs(stat.trend) }}%
            </span>
            <span class="stat-period">较昨日</span>
          </div>
        </n-card>
      </n-gi>
    </n-grid>

    <n-grid :cols="2" :x-gap="16" :y-gap="16">
      <n-gi>
        <n-card title="近 7 日订单归因趋势" :bordered="false">
          <div style="height: 300px; display: flex; align-items: center; justify-content: center; color: #999;">
            [趋势图表加载中...]
          </div>
        </n-card>
      </n-gi>
      <n-gi>
        <n-card title="招商团队表现榜" :bordered="false">
          <n-list hoverable clickable>
            <n-list-item v-for="(item, index) in ranking" :key="item.name">
              <template #prefix>
                <n-tag :type="index < 3 ? 'primary' : 'default'" size="small" round>{{ index + 1 }}</n-tag>
              </template>
              <n-thing :title="item.name" :description="`完成归因业绩: ¥${item.amount}`" />
            </n-list-item>
          </n-list>
        </n-card>
      </n-gi>
    </n-grid>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import PageHeader from '../../components/PageHeader.vue'
import request from '../../utils/request'

const stats = ref([
  { label: '今日 GMV', value: '¥128,450', trend: 12.5 },
  { label: '待处理订单', value: '42', trend: -5.2 },
  { label: '归因成功率', value: '94.2%', trend: 2.1 },
  { label: '活跃达人数', value: '156', trend: 8.4 }
])

const ranking = ref([
  { name: '张三 (招商部)', amount: '45,200' },
  { name: '李四 (渠道部)', amount: '38,150' },
  { name: '王五 (招商部)', amount: '29,400' },
  { name: '赵六 (电商二组)', amount: '18,900' },
  { name: '钱七 (新锐中心)', amount: '12,000' }
])

const fetchSummary = async () => {
  try {
    await request.get('/dashboard/summary')
    // 这里可以更新 stats 变量
  } catch (err) {
    // 降级使用 Test 数据
  }
}

onMounted(fetchSummary)
</script>

<style scoped>
.dashboard-page { padding: 24px; }
.stat-card { border-radius: 12px; transition: transform 0.3s; }
.stat-card:hover { transform: translateY(-4px); box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
.stat-footer { margin-top: 12px; font-size: 12px; }
.stat-trend { font-weight: 600; margin-right: 4px; }
.stat-trend.up { color: #18a058; }
.stat-trend.down { color: #d03050; }
.stat-period { color: #999; }
</style>

