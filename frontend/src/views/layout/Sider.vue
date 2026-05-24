<template>
  <aside class="app-sider" :class="{ collapsed: appStore.collapsed }">
    <div class="sider-toolbar">
      <button
        type="button"
        class="collapse-trigger"
        :aria-label="appStore.collapsed ? '展开侧边栏' : '收起侧边栏'"
        @click="appStore.toggle"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path
            :d="appStore.collapsed ? 'M9 18l6-6-6-6' : 'M15 18l-6-6 6-6'"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </button>
    </div>

    <div class="sider-menu">
      <n-menu
        v-if="menuOptions.length"
        :key="activeTopKey || 'empty'"
        :options="menuOptions"
        :value="activeLeftKey"
        :expanded-keys="expandedKeys"
        :collapsed="appStore.collapsed"
        :collapsed-width="64"
        :collapsed-icon-size="22"
        :indent="18"
        data-testid="sidebar-menu"
        @update:value="handleLeftMenuClick"
        @update:expanded-keys="handleExpandedKeys"
      />
      <div v-else class="empty-tip">当前账号没有可见菜单</div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, h, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NIcon } from 'naive-ui'
import { ROLE_CODES } from '../../constants/rbac'
import {
  buildAccessibleMenuTree,
  getLeftMenus,
  resolveActiveLeftKey,
  resolveActiveTopKey,
  TALENT_MENU_KEYS,
  type MenuTreeNode
} from '../../router/menuTree'
import { resolveMenuNavigateTarget } from '../../router/navigation'
import { getAccessibleTalentViewOptions } from '../talent/constants'
import { useAppStore } from '../../stores/app'
import { useAuthStore } from '../../stores/auth'

const appStore = useAppStore()
const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()
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

const iconData = (paths: string | string[]) => () =>
  h(NIcon, null, {
    default: () =>
      h(
        'svg',
        {
          class: 'nav-icon-svg',
          viewBox: '0 0 24 24',
          fill: 'none',
          stroke: 'currentColor',
          'stroke-width': '2',
          'stroke-linecap': 'round',
          'stroke-linejoin': 'round',
          width: 20,
          height: 20
        },
        (Array.isArray(paths) ? paths : [paths]).map((d, index) =>
          h('path', { d, opacity: index === 0 ? 1 : 0.74 })
        )
      )
  })

const icons = {
  chart: iconData(['M5 19V13.5', 'M12 19V5', 'M19 19V9', 'M4 19h16']),
  bag: iconData(['M6.5 9h11l1.1 11h-13L6.5 9z', 'M9 9V7a3 3 0 016 0v2', 'M10 13h4']),
  user: iconData(['M12 12a4 4 0 100-8 4 4 0 000 8z', 'M5 20a7 7 0 0114 0', 'M17.5 7.5h3M19 6v3']),
  gift: iconData(['M4 11h16v9H4v-9z', 'M3 7h18v4H3V7z', 'M12 7v13M8.5 7C7 7 6 6 6 4.8S7.1 3 8.3 3C10.2 3 12 7 12 7M15.5 7C17 7 18 6 18 4.8S16.9 3 15.7 3C13.8 3 12 7 12 7']),
  settings: iconData(['M12 15.5a3.5 3.5 0 100-7 3.5 3.5 0 000 7z', 'M19 12a7 7 0 00-.1-1.2l2-1.5-2-3.4-2.4 1a7 7 0 00-2-1.2L14.2 3h-4.4l-.3 2.7a7 7 0 00-2 1.2l-2.4-1-2 3.4 2 1.5A7 7 0 005 12c0 .4 0 .8.1 1.2l-2 1.5 2 3.4 2.4-1a7 7 0 002 1.2l.3 2.7h4.4l.3-2.7a7 7 0 002-1.2l2.4 1 2-3.4-2-1.5c.1-.4.1-.8.1-1.2z']),
  truck: iconData(['M3 7h11v9H3V7z', 'M14 10h4l3 3v3h-7v-6z', 'M7 19a2 2 0 100-4 2 2 0 000 4zM17 19a2 2 0 100-4 2 2 0 000 4z']),
  shield: iconData(['M12 21s7-3.6 7-9.2V5.5L12 3 5 5.5v6.3C5 17.4 12 21 12 21z', 'M9 12l2 2 4-5']),
  list: iconData(['M8 6h11M8 12h11M8 18h11', 'M4.5 6h.01M4.5 12h.01M4.5 18h.01'])
}

const ICON_BY_KEY: Record<string, () => unknown> = {
  '/dashboard': icons.chart,
  '/orders': icons.truck,
  '/data': icons.chart,
  '/data/orders': icons.list,
  '/ops/exclusive': icons.shield,
  '/ops/shipping': icons.truck,
  '/product': icons.bag,
  '/product/manage': icons.list,
  '/product/manage/products': icons.bag,
  '/sample': icons.gift,
  '/system/users': icons.user,
  '/system/roles': icons.shield,
  '/system/depts': icons.list,
  '/system/rule-center': icons.settings,
  '/system/config': icons.settings,
  '/system/commission-rules': icons.settings,
  '/system/douyin': icons.truck,
  '/system/operation-logs': icons.list,
  [TALENT_MENU_KEYS.teamPublic]: icons.user,
  [TALENT_MENU_KEYS.myTalents]: icons.user,
  [TALENT_MENU_KEYS.naturalOrders]: icons.chart,
  [TALENT_MENU_KEYS.blacklist]: icons.shield
}

interface SidebarMenuOption {
  label: string
  key: string
  icon?: () => unknown
  children?: SidebarMenuOption[]
}

const accessibleMenuTree = computed(() => buildAccessibleMenuTree(authStore.roleCodes))

const activeTopKey = computed(() => resolveActiveTopKey(route.path))

const activeLeftKey = computed(() => {
  const view = typeof route.query.view === 'string' ? route.query.view : null
  return resolveActiveLeftKey(route.path, view)
})

const manualExpandedKeys = ref<string[]>([])

watch(activeTopKey, () => {
  manualExpandedKeys.value = []
})

function localizeLeftMenu(node: MenuTreeNode): SidebarMenuOption {
  let label = node.label
  if (isChannelStaffOnly.value) {
    if (node.key === '/data') label = '我的业绩'
    if (node.key === TALENT_MENU_KEYS.myTalents) label = '我的达人'
    if (node.key === TALENT_MENU_KEYS.teamPublic) label = '公海达人'
  }
  if (isBizStaffOnly.value && node.key === '/data') {
    label = '我的业绩'
  }
  if (isOpsStaffOnly.value && node.key === '/ops/shipping') {
    label = '发货台'
  }

  const option: SidebarMenuOption = {
    label,
    key: node.key,
    icon: ICON_BY_KEY[node.key]
  }

  if (node.children?.length) {
    option.children = node.children.map(localizeLeftMenu)
  }

  return option
}

const accessibleTalentViewValues = computed(() =>
  new Set<string>(getAccessibleTalentViewOptions(authStore.roleCodes, authStore.isAdmin).map((item) => item.value))
)

const menuOptions = computed(() => {
  const leftMenus = getLeftMenus(accessibleMenuTree.value, activeTopKey.value)
  if (activeTopKey.value === 'talent') {
  const allowedViews = accessibleTalentViewValues.value
    return leftMenus
      .filter((item) => {
        if (!item.key.startsWith('/talent?view=')) return true
        const view = item.key.replace('/talent?view=', '')
        return allowedViews.has(view)
      })
      .map(localizeLeftMenu)
  }
  return leftMenus.map(localizeLeftMenu)
})

const expandedKeys = computed(() => {
  const expandable = menuOptions.value
    .filter((item) => item.children?.length)
    .map((item) => item.key)
  return [...new Set([...expandable, ...manualExpandedKeys.value])]
})

function handleExpandedKeys(keys: string[]) {
  manualExpandedKeys.value = keys
}

function handleLeftMenuClick(key: string) {
  if (key.startsWith('/talent?view=')) {
    const view = key.replace('/talent?view=', '')
    router.push({ path: '/talent', query: { view } })
    return
  }
  const target = resolveMenuNavigateTarget(key)
  if (!target) return
  router.push(target)
}
</script>

<style scoped>
.app-sider {
  width: var(--sider-width);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  background: var(--bg-sidebar);
  border-right: 1px solid var(--border-color);
  box-shadow: 2px 0 12px rgba(15, 23, 42, 0.03);
  transition: width var(--transition-normal);
  overflow: hidden;
}

.app-sider.collapsed {
  width: var(--sider-collapsed-width);
}

.sider-toolbar {
  display: flex;
  justify-content: flex-end;
  padding: 8px;
  border-bottom: 1px solid var(--border-color-light);
}

.collapse-trigger {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.collapse-trigger:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
  transform: scale(1.05);
}

.collapse-trigger:active {
  transform: scale(0.95);
}

.collapse-trigger svg {
  width: 16px;
  height: 16px;
}

.app-sider :deep(.nav-icon-svg) {
  stroke-width: 2.1;
  transition: transform var(--transition-fast), stroke-width var(--transition-fast);
}

.app-sider :deep(.n-menu .n-menu-item-content:hover .nav-icon-svg),
.app-sider :deep(.n-menu .n-menu-item-content--selected .nav-icon-svg) {
  transform: translateY(-1px);
  stroke-width: 2.25;
}

.sider-menu {
  flex: 1;
  overflow-y: auto;
}

.empty-tip {
  padding: 16px;
  color: var(--text-secondary);
  font-size: 14px;
}

.app-sider :deep(.n-menu) {
  padding: 8px 0;
  background: transparent;
}

.app-sider :deep(.n-menu .n-menu-item-content) {
  margin: 2px 8px;
  border-radius: var(--radius-md);
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.app-sider :deep(.n-menu .n-menu-item-content--selected) {
  color: var(--color-primary) !important;
  background: var(--color-primary-light) !important;
  font-weight: 600;
}

.app-sider :deep(.n-menu .n-menu-item-content--selected::before) {
  display: none;
}

.app-sider :deep(.n-menu .n-menu-item-content:not(.n-menu-item-content--selected):hover) {
  background: var(--bg-hover);
}
</style>
