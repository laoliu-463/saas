import { computed, onMounted, ref } from 'vue'

type SystemEnvResponse = {
  data?: {
    environmentLabel?: string
    appTestEnabled?: boolean
    douyinTestEnabled?: boolean
  }
}

export function useRuntimeEnvironment() {
  const viteEnvLabel = String(import.meta.env.VITE_ENV_LABEL || '').trim().toUpperCase()
  const serverEnvLabel = ref('')
  const appTestEnabled = ref<boolean | null>(null)
  const douyinTestEnabled = ref<boolean | null>(null)

  onMounted(async () => {
    try {
      const res = await fetch('/api/system/env', { headers: buildAuthHeaders() })
      const body = (await res.json()) as SystemEnvResponse
      const label = body?.data?.environmentLabel
      if (label != null && String(label).trim() !== '') {
        serverEnvLabel.value = String(label).trim().toUpperCase()
      }
      if (body?.data?.appTestEnabled != null) {
        appTestEnabled.value = Boolean(body.data.appTestEnabled)
      }
      if (body?.data?.douyinTestEnabled != null) {
        douyinTestEnabled.value = Boolean(body.data.douyinTestEnabled)
      }
    } catch {
      // fall back to Vite label and heuristics below
    }
  })

  const environmentLabel = computed(() => serverEnvLabel.value || viteEnvLabel)

  const usesRealDouyinUpstream = computed(() => {
    if (appTestEnabled.value === false && douyinTestEnabled.value === false) {
      return true
    }
    const label = environmentLabel.value
    return label.includes('REAL') || label === 'PROD' || label === 'PRODUCTION'
  })

  const activityDataSourceHint = computed(() => {
    if (usesRealDouyinUpstream.value) {
      const label = environmentLabel.value || 'REAL-PRE'
      return `当前为 ${label} 联调环境，活动数据实时请求抖店开放平台（精选联盟/团长链路）。`
    }
    const label = environmentLabel.value || 'TEST'
    return `当前为 ${label} 测试环境，活动数据来自后端 Test/Mock 服务，非抖店真实上游。`
  })

  const activityAlertType = computed(() => (usesRealDouyinUpstream.value ? 'info' : 'warning'))

  return {
    environmentLabel,
    usesRealDouyinUpstream,
    activityDataSourceHint,
    activityAlertType
  }
}

function buildAuthHeaders(): HeadersInit | undefined {
  const token = localStorage.getItem('token')
  return token ? { Authorization: `Bearer ${token}` } : undefined
}
