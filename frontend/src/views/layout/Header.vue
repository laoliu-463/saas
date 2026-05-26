<template>
  <div class="top-header" :style="{ height: 'var(--header-height)' }">
    <div class="header-left">
      <div class="header-logo">
        <span class="logo-icon">
          <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
            <rect width="28" height="28" rx="8" fill="rgba(255,255,255,0.2)" />
            <path d="M14 5.6L21.2 20.8H6.8L14 5.6Z" fill="white" opacity="0.94" />
            <path d="M10.2 18.2H17.8" stroke="rgba(255,255,255,0.72)" stroke-width="1.6" stroke-linecap="round" />
            <circle cx="14" cy="15.3" r="2.2" fill="var(--color-primary)" />
            <circle cx="14" cy="15.3" r="0.8" fill="white" opacity="0.88" />
          </svg>
        </span>
        <span class="logo-text">Colonel SaaS</span>
      </div>
    </div>

    <div class="header-nav">
      <div
        v-for="tab in visibleTabs"
        :key="tab.key"
        class="nav-tab"
        :class="{ active: isTopMenuActive(tab.key, route.path) }"
        :data-testid="tab.testId"
        @click="handleTopMenuClick(tab.key)"
      >
        {{ tab.label }}
      </div>
    </div>

    <div class="header-right">
      <span v-if="envLabel" class="env-badge" data-testid="current-env-badge">环境：{{ envLabel }}</span>
      <n-dropdown :options="userMenuOptions" @select="handleUserMenu">
        <div class="user-info">
          <n-avatar round size="small" :style="{ backgroundColor: 'rgba(255,255,255,0.2)' }">
            {{ userInitial }}
          </n-avatar>
          <span class="user-name">{{ authStore.userInfo?.realName || authStore.userInfo?.username || '用户' }}</span>
          <n-icon size="16">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M6 9l6 6 6-6" />
            </svg>
          </n-icon>
        </div>
      </n-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NIcon, useMessage } from 'naive-ui'
import { useAuthStore } from '../../stores/auth'
import { ROLE_CODES } from '../../constants/rbac'
import { getTopMenus, isTopMenuActive, resolveTopMenuDefaultPath } from '../../router/menuTree'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()
const message = useMessage()
const ROLE = ROLE_CODES

const isChannelStaffOnly = computed(() => {
  const roles = authStore.roleCodes
  return roles.includes(ROLE.CHANNEL_STAFF) && !roles.includes(ROLE.CHANNEL_LEADER) && !roles.includes(ROLE.ADMIN)
})

const isBizStaffOnly = computed(() => {
  const roles = authStore.roleCodes
  return roles.includes(ROLE.BIZ_STAFF) && !roles.includes(ROLE.BIZ_LEADER) && !roles.includes(ROLE.ADMIN)
})

const isOpsStaffOnly = computed(() => {
  const roles = authStore.roleCodes
  return roles.includes(ROLE.OPS_STAFF) && !roles.includes(ROLE.ADMIN)
})

const rawEnvLabel = import.meta.env.VITE_ENV_LABEL as string | undefined
const serverEnvLabel = ref('')
onMounted(async () => {
  try {
    const res = await fetch('/api/system/env')
    const body = await res.json()
    const label = body?.data?.environmentLabel
    if (label != null && String(label).trim() !== '') {
      serverEnvLabel.value = String(label).trim()
    }
  } catch {
    // ignore: banner falls back to Vite label
  }
})
const envLabel = computed(() => {
  const fromServer = String(serverEnvLabel.value || '').trim().toUpperCase()
  if (fromServer) return fromServer
  return String(rawEnvLabel || '').trim().toUpperCase()
})

const visibleTabs = computed(() =>
  getTopMenus(authStore.roleCodes).map((tab) => {
    if (isChannelStaffOnly.value) {
      if (tab.key === 'data') return { ...tab, label: '我的业绩' }
      if (tab.key === 'talent') return { ...tab, label: '我的达人' }
      if (tab.key === 'sample') return { ...tab, label: '合作管理' }
    }
    if (isBizStaffOnly.value) {
      if (tab.key === 'data') return { ...tab, label: '我的业绩' }
      if (tab.key === 'sample') return { ...tab, label: '合作管理' }
    }
    if (isOpsStaffOnly.value && tab.key === 'sample') {
      return { ...tab, label: '合作管理' }
    }
    return tab
  })
)

const handleTopMenuClick = (topKey: string) => {
  const target = resolveTopMenuDefaultPath(topKey, authStore.roleCodes)
  if (!target) return
  router.push(target)
}

const userInitial = computed(() => {
  const name = authStore.userInfo?.realName || authStore.userInfo?.username || 'U'
  return name.charAt(0).toUpperCase()
})

const userMenuOptions = [
  { label: '个人中心', key: 'profile' },
  { label: '退出登录', key: 'logout' }
]

const revokeServerSession = async (accessToken: string, refreshToken: string) => {
  if (!refreshToken) {
    return
  }
  try {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json'
    }
    if (accessToken) {
      headers.Authorization = `Bearer ${accessToken}`
    }
    const response = await fetch('/api/auth/logout', {
      method: 'POST',
      headers,
      body: JSON.stringify({
        accessToken: accessToken || undefined,
        refreshToken
      })
    })
    if (!response.ok) {
      throw new Error(`Logout request failed: ${response.status}`)
    }
  } catch (_error) {
    message.warning('登出接口执行失败，已清除本地登录态')
  }
}

const handleUserMenu = async (key: string) => {
  if (key === 'profile') {
    router.push('/profile')
    return
  }
  if (key === 'logout') {
    const accessToken = authStore.token || localStorage.getItem('token') || ''
    const refreshToken = authStore.refreshToken || localStorage.getItem('refreshToken') || ''
    await revokeServerSession(accessToken, refreshToken)
    authStore.logout()
    await router.replace('/login')
  }
}
</script>

<style scoped>
.top-header {
  display: flex;
  align-items: center;
  padding: 0 var(--spacing-lg);
  background: var(--color-primary-gradient);
  color: var(--text-inverse);
  flex-shrink: 0;
  z-index: 100;
  box-shadow: var(--shadow-header);
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
}

.header-left {
  flex-shrink: 0;
  width: var(--sider-width);
}

.header-logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-text {
  font-size: var(--text-lg);
  font-weight: 700;
  letter-spacing: 0.5px;
}

.header-nav {
  flex: 1;
  display: flex;
  justify-content: center;
  gap: 4px;
  overflow-x: auto;
}

.nav-tab {
  flex: 0 0 auto;
  padding: 7px 16px;
  font-size: var(--text-sm);
  font-weight: 500;
  border-radius: var(--radius-full);
  cursor: pointer;
  white-space: nowrap;
  transition: background var(--transition-fast), color var(--transition-fast);
  color: rgba(255, 255, 255, 0.82);
}

.nav-tab:hover {
  background: rgba(255, 255, 255, 0.14);
  color: var(--text-inverse);
}

.nav-tab.active {
  background: rgba(255, 255, 255, 0.22);
  color: var(--text-inverse);
  font-weight: 600;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.12);
}

.header-right {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 16px;
}

.env-badge {
  padding: 3px 10px;
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
  font-weight: 600;
  background: rgba(255, 255, 255, 0.2);
  color: white;
  letter-spacing: 0;
  white-space: nowrap;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: var(--radius-md);
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.user-info:hover {
  background: rgba(255, 255, 255, 0.14);
}

.user-name {
  font-size: var(--text-base);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
