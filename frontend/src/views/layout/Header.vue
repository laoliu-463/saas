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
        @click="handleTabClick(tab)"
      >
        {{ tab.label }}
      </div>
    </div>

    <div class="header-right">
      <span v-if="envLabel" class="env-badge">{{ envLabel }}</span>
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
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NIcon, useMessage } from 'naive-ui'
import { useAuthStore } from '../../stores/auth'
import { ROLE_CODES, hasAccess } from '../../constants/rbac'
import { logout as logoutApi } from '../../api/auth'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()
const message = useMessage()
const ROLE = ROLE_CODES
const envLabel = import.meta.env.VITE_ENV_LABEL as string | undefined

interface NavTab {
  label: string
  key: string
  roles: string[]
}

const navTabs: NavTab[] = [
  { label: '数据平台', key: '/data', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF] },
  { label: '商品库', key: '/product', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF] },
  { label: '达人 CRM', key: '/talent', roles: [ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF] },
  { label: '寄样台', key: '/sample', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF, ROLE.OPS_STAFF] },
  { label: '运营中心', key: '/ops/exclusive', roles: [ROLE.OPS_STAFF, ROLE.BIZ_LEADER, ROLE.CHANNEL_LEADER] },
  { label: '系统管理', key: '/system/users', roles: [ROLE.ADMIN] }
]

const visibleTabs = computed(() => navTabs.filter((tab) => hasAccess(authStore.roleCodes, tab.roles)))

const isActiveTab = (tab: NavTab) => {
  if (tab.key === '/ops/exclusive') {
    return route.path.startsWith('/ops')
  }
  if (tab.key === '/system/users') {
    return route.path.startsWith('/system')
  }
  return route.path.startsWith(tab.key) && !route.path.startsWith('/data/orders')
}

const handleTabClick = (tab: NavTab) => {
  router.push(tab.key)
}

const userInitial = computed(() => {
  const name = authStore.userInfo?.realName || authStore.userInfo?.username || 'U'
  return name.charAt(0).toUpperCase()
})

const userMenuOptions = [{ label: '退出登录', key: 'logout' }]

const handleUserMenu = async (key: string) => {
  if (key === 'logout') {
    const accessToken = authStore.token || localStorage.getItem('token') || ''
    const refreshToken = authStore.refreshToken || localStorage.getItem('refreshToken') || ''
    try {
      if (accessToken) {
        await logoutApi({
          accessToken,
          refreshToken: refreshToken || undefined
        })
      }
    } catch (_error) {
      message.warning('登出接口执行失败，已清除本地登录态')
    } finally {
      authStore.logout()
      router.push('/login')
    }
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
  padding: 2px 10px;
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
  font-weight: 600;
  background: rgba(255, 255, 255, 0.2);
  color: white;
  letter-spacing: 1px;
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
