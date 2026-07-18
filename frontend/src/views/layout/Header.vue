<!--
  Header - 顶部导航栏组件

  用途：应用顶部的全局导航栏，包含品牌 Logo、一级导航菜单、环境标签和用户信息。

  特性：
    - 根据用户角色动态显示不同的一级菜单（如渠道专员看到"我的业绩"而非"数据"）
    - 环境标签优先读取服务端返回值，回退到 Vite 环境变量
    - 用户下拉菜单支持"个人中心"和"退出登录"
    - 退出登录时调用后端注销接口，失败时仍清除本地登录态

  依赖的 store：
    - authStore: 用户信息、角色、Token 管理
-->
<template>
  <!-- 顶部导航栏容器：品牌色渐变背景，高度由 CSS 变量控制 -->
  <div class="top-header" :style="{ height: 'var(--header-height)' }">
    <!-- 左侧区域：Logo 和品牌名称 -->
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

    <!-- 中间区域：一级导航菜单 Tab 列表 -->
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

    <!-- 右侧区域：环境标签和用户信息下拉菜单 -->
    <div class="header-right">
      <!-- 环境标签：显示当前部署环境（如 DEV / TEST / PRE） -->
      <span v-if="envLabel" class="env-badge" data-testid="current-env-badge">环境：{{ envLabel }}</span>
      <!-- 用户下拉菜单：包含"个人中心"和"退出登录" -->
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
import { isNavigationFailure, NavigationFailureType, useRouter, useRoute } from 'vue-router'
import { NIcon, useMessage } from 'naive-ui'
import { useAuthStore } from '../../stores/auth'
import { ROLE_CODES, hasOnlyCanonicalRole } from '../../constants/rbac'
import { getTopMenus, isTopMenuActive, resolveTopMenuDefaultPath } from '../../router/menuTree'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()
const message = useMessage()
const ROLE = ROLE_CODES

/* ---- 角色判断计算属性 ---- */
/* 判断当前用户是否仅为渠道专员（非组长、非管理员），用于菜单文案本地化 */

const isChannelStaffOnly = computed(() => {
  return hasOnlyCanonicalRole(authStore.roleCodes, ROLE.CHANNEL_STAFF)
})

const isBizStaffOnly = computed(() => {
  return hasOnlyCanonicalRole(authStore.roleCodes, ROLE.BIZ_STAFF)
})

const isOpsStaffOnly = computed(() => {
  return hasOnlyCanonicalRole(authStore.roleCodes, ROLE.OPS_STAFF)
})

/** Vite 构建时注入的环境标签（兜底） */
const rawEnvLabel = import.meta.env.VITE_ENV_LABEL as string | undefined
/** 从服务端接口获取的环境标签（优先级更高） */
const serverEnvLabel = ref('')
/**
 * 组件挂载后从后端 /api/system/env 接口读取环境标签
 * 失败时静默忽略，后续回退到 Vite 环境变量
 */
onMounted(async () => {
  try {
    const headers: Record<string, string> = {}
    const bearerCredential = authStore.token || localStorage.getItem('token') || ''
    if (bearerCredential) {
      headers.Authorization = `Bearer ${bearerCredential}`
    }
    const res = await fetch('/api/system/env', { headers })
    const body = await res.json()
    const label = body?.data?.environmentLabel
    if (label != null && String(label).trim() !== '') {
      serverEnvLabel.value = String(label).trim()
    }
  } catch {
    // ignore: banner falls back to Vite label
  }
})
/**
 * 最终的环境标签：优先使用服务端返回值，为空时回退到 Vite 环境变量
 * 统一转为大写展示
 */
const envLabel = computed(() => {
  const fromServer = String(serverEnvLabel.value || '').trim().toUpperCase()
  if (fromServer) return fromServer
  return String(rawEnvLabel || '').trim().toUpperCase()
})

/**
 * 可见的一级导航菜单 Tab 列表
 * 根据基础菜单列表，对特定角色进行文案本地化：
 * - 渠道专员：数据 -> "我的业绩"，达人 -> "我的达人"，寄样 -> "合作管理"
 * - 业务专员：数据 -> "我的业绩"，寄样 -> "合作管理"
 * - 运营专员：寄样 -> "合作管理"
 */
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

/**
 * 执行带结果反馈的页面跳转。
 * Vue Router 将重复导航、守卫中止等情况作为返回值交给调用方；如果不等待，
 * 用户只能看到页面没有变化，无法区分重复点击、权限拦截和加载异常。
 */
const safeNavigate = async (target: string) => {
  try {
    const failure = await router.push(target)
    if (!failure) return true

    if (isNavigationFailure(failure, NavigationFailureType.duplicated)) {
      message.warning('当前已在该页面')
    } else {
      message.error('页面跳转失败，请稍后重试')
    }
    console.warn('[router navigation] failed', { target, failureType: failure.type })
  } catch (error) {
    message.error('页面跳转失败，请稍后重试')
    console.error('[router navigation] exception', { target, error })
  }
  return false
}

/** 顶部菜单 Tab 点击处理：解析该菜单下的默认路由并跳转 */
const handleTopMenuClick = async (topKey: string) => {
  const target = resolveTopMenuDefaultPath(topKey, authStore.roleCodes)
  if (!target) return
  await safeNavigate(target)
}

/** 用户头像显示的首字母：取姓名或用户名的第一个字符，大写 */
const userInitial = computed(() => {
  const name = authStore.userInfo?.realName || authStore.userInfo?.username || 'U'
  return name.charAt(0).toUpperCase()
})

const userMenuOptions = [
  { label: '个人中心', key: 'profile' },
  { label: '退出登录', key: 'logout' }
]

/**
 * 调用后端注销接口，撤销服务端的 Token 会话
 * 即使注销失败也仅给出警告，不影响后续清除本地登录态
 */
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

/** 用户下拉菜单选择处理：跳转个人中心或执行退出登录流程 */
const handleUserMenu = async (key: string) => {
  if (key === 'profile') {
    await safeNavigate('/profile')
    return
  }
  if (key === 'logout') {
    const accessCredential = authStore.token || localStorage.getItem('token') || ''
    const refreshCredential = authStore.refreshToken || localStorage.getItem('refreshToken') || ''
    await revokeServerSession(accessCredential, refreshCredential)
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
