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
import { NIcon } from 'naive-ui'
import { useAuthStore } from '../../stores/auth'
import { ROLE_CODES, hasAccess } from '../../constants/rbac'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()
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

const handleUserMenu = (key: string) => {
  if (key === 'logout') {
    authStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.top-header {
  display: flex;
  align-items: center;
  padding: 0 20px;
  background: linear-gradient(135deg, #ff4757 0%, #ff3b4a 100%);
  color: white;
  flex-shrink: 0;
  z-index: 100;
  box-shadow: 0 2px 8px rgba(255, 71, 87, 0.25);
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
  font-size: 17px;
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
  font-size: 14px;
  font-weight: 500;
  border-radius: var(--radius-md);
  cursor: pointer;
  white-space: nowrap;
  transition: background var(--transition-fast);
  color: rgba(255, 255, 255, 0.75);
}

.nav-tab:hover {
  background: rgba(255, 255, 255, 0.12);
  color: white;
}

.nav-tab.active {
  background: rgba(255, 255, 255, 0.2);
  color: white;
  font-weight: 600;
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
  font-size: 11px;
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
  padding: 4px 8px;
  border-radius: var(--radius-md);
  transition: background var(--transition-fast);
}

.user-info:hover {
  background: rgba(255, 255, 255, 0.12);
}

.user-name {
  font-size: 14px;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
