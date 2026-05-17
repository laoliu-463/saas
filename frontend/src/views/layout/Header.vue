<template>
  <div class="top-header" :style="{ height: 'var(--header-height)' }">
    <div class="header-left">
      <div class="header-logo">
        <span class="logo-icon">
          <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
            <rect width="28" height="28" rx="8" fill="rgba(255,255,255,0.2)" />
            <path d="M7 20L14 6L21 20H7Z" fill="white" />
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
        :class="{ active: isActiveTab(tab) }"
        :data-testid="tab.testId"
        @click="handleTabClick(tab)"
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
import { ROLE_CODES, hasAccess } from '../../constants/rbac'

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

interface NavTab {
  label: string
  key: string
  roles: string[]
  testId: string
}

const navTabs: NavTab[] = [
  { label: '数据看板', key: '/data', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF, ROLE.ADMIN], testId: 'nav-dashboard' },
  { label: '商品库', key: '/product', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF], testId: 'nav-product' },
  { label: '商品管理', key: '/product/manage', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF], testId: 'nav-activity-product' },
  { label: '达人 CRM', key: '/talent', roles: [ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF], testId: 'nav-talent' },
  { label: '寄样审核', key: '/sample', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF], testId: 'nav-sample' },
  { label: '寄样发货台', key: '/ops/shipping', roles: [ROLE.OPS_STAFF], testId: 'nav-shipping' },
  { label: '系统管理', key: '/system/users', roles: [ROLE.ADMIN], testId: 'nav-system' }
]

const visibleTabs = computed(() =>
  navTabs
    .filter((tab) => hasAccess(authStore.roleCodes, tab.roles))
    .map((tab) => {
      if (isChannelStaffOnly.value) {
        if (tab.key === '/data') return { ...tab, label: '我的业绩' }
        if (tab.key === '/talent') return { ...tab, label: '我的达人' }
        if (tab.key === '/sample') return { ...tab, label: '寄样台' }
      }
      if (isBizStaffOnly.value) {
        if (tab.key === '/data') return { ...tab, label: '我的业绩' }
        if (tab.key === '/sample') return { ...tab, label: '寄样台' }
      }
      if (isOpsStaffOnly.value && tab.key === '/ops/shipping') {
        return { ...tab, label: '寄样发货台' }
      }
      return tab
    })
)

const isActiveTab = (tab: NavTab) => {
  if (tab.key === '/system/users') {
    return route.path.startsWith('/system')
  }
  if (tab.key === '/data') {
    return route.path.startsWith('/data')
  }
  if (tab.key === '/ops/shipping') return route.path.startsWith('/ops/shipping')
  if (tab.key === '/product') {
    return route.path === '/product'
  }
  if (tab.key === '/product/manage') {
    return route.path.startsWith('/product/manage')
  }
  return route.path.startsWith(tab.key) && !route.path.startsWith('/data/orders') && !route.path.startsWith('/product/manage')
}

const handleTabClick = (tab: NavTab) => {
  if (tab.key === '/product/manage' && authStore.roleCodes.includes(ROLE.BIZ_STAFF) && !authStore.roleCodes.includes(ROLE.BIZ_LEADER)) {
    router.push('/product/manage/products')
    return
  }
  router.push(tab.key)
}

const userInitial = computed(() => {
  const name = authStore.userInfo?.realName || authStore.userInfo?.username || 'U'
  return name.charAt(0).toUpperCase()
})

const userMenuOptions = [{ label: '退出登录', key: 'logout' }]

const revokeServerSession = async (accessToken: string, refreshToken: string) => {
  if (!accessToken) {
    return
  }
  try {
    const response = await fetch('/api/auth/logout', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${accessToken}`
      },
      body: JSON.stringify({
        accessToken,
        refreshToken: refreshToken || undefined
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
  padding: 0 20px;
  background: linear-gradient(135deg, rgba(255, 71, 87, 0.95) 0%, rgba(255, 107, 129, 0.95) 100%);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  color: white;
  flex-shrink: 0;
  z-index: 100;
  box-shadow: var(--shadow-sm);
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
  padding: 8px 18px;
  font-size: var(--text-base);
  font-weight: 500;
  border-radius: var(--radius-md);
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  color: rgba(255, 255, 255, 0.75);
  transform: translateY(0);
}

.nav-tab:hover {
  background: rgba(255, 255, 255, 0.15);
  color: white;
  transform: translateY(-1px);
}

.nav-tab:active {
  transform: translateY(1px);
}

.nav-tab.active {
  background: rgba(255, 255, 255, 0.2);
  color: white;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
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
  background: rgba(255, 255, 255, 0.15);
  transform: translateY(-1px);
}

.user-info:active {
  transform: translateY(1px);
}

.user-name {
  font-size: var(--text-base);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
