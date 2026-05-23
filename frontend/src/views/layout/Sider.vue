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
        :options="menuOptions"
        :value="activeMenuKey"
        :expanded-keys="expandedKeys"
        :collapsed="appStore.collapsed"
        :collapsed-width="64"
        :collapsed-icon-size="22"
        :indent="18"
        data-testid="sidebar-menu"
        @update:value="handleMenuClick"
        @update:expanded-keys="handleExpandedKeys"
      />
      <div v-else class="empty-tip">当前账号没有可见菜单</div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, h, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NIcon } from 'naive-ui'
import { ROLE_CODES, hasAccess } from '../../constants/rbac'
import {
  filterAccessibleMenus,
  isRoutePathUnderPrefix,
  resolveActiveSection,
  resolveMenuNavigateTarget,
  type NavigationMenuItem
} from '../../router/navigation'
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

interface MenuItem extends NavigationMenuItem {
  label: string
  key: string
  icon?: () => any
  roles?: string[]
  children?: MenuItem[]
  _section?: string
}

const TALENT_MENU_KEYS = {
  teamPublic: '/talent?view=TEAM_PUBLIC',
  myTalents: '/talent?view=MY_TALENTS',
  naturalOrders: '/talent?view=NATURAL_ORDERS',
  blacklist: '/talent?view=BLACKLIST'
} as const

const rawMenus: MenuItem[] = [
  {
    label: '归因工作台',
    key: 'attribution-group',
    icon: icons.shield,
    _section: 'attribution',
    roles: [ROLE.BIZ_LEADER, ROLE.CHANNEL_LEADER, ROLE.ADMIN],
    children: [
      { label: '归因概览', key: '/dashboard', icon: icons.chart },
      { label: '订单工作台', key: '/orders', icon: icons.truck }
    ]
  },
  {
    label: '数据平台',
    key: 'data-group',
    icon: icons.chart,
    _section: 'data',
    roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF],
    children: [
      { label: '核心看板', key: '/data', icon: icons.chart },
      { label: '订单明细', key: '/data/orders', icon: icons.list },
      { label: '独家状态', key: '/ops/exclusive', icon: icons.shield, roles: [ROLE.BIZ_LEADER, ROLE.CHANNEL_LEADER, ROLE.ADMIN] }
    ]
  },
  {
    label: '寄样发货台',
    key: '/ops/shipping',
    icon: icons.truck,
    _section: 'ops',
    roles: [ROLE.OPS_STAFF]
  },
  {
    label: '商品库',
    key: '/product',
    icon: icons.bag,
    _section: 'product',
    roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF]
  },
  {
    label: '商品管理',
    key: 'product-manage-group',
    icon: icons.list,
    _section: 'product-manage',
    roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF],
    children: [
      { label: '活动列表', key: '/product/manage', icon: icons.list, roles: [ROLE.BIZ_LEADER] },
      { label: '商品列表', key: '/product/manage/products', icon: icons.bag, roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF] }
    ]
  },
  {
    label: '达人 CRM',
    key: 'talent-group',
    icon: icons.user,
    _section: 'talent',
    roles: [ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF],
    children: [
      { label: '团队公海', key: TALENT_MENU_KEYS.teamPublic, icon: icons.user },
      { label: '我的达人', key: TALENT_MENU_KEYS.myTalents, icon: icons.user },
      { label: '自然出单达人', key: TALENT_MENU_KEYS.naturalOrders, icon: icons.chart },
      { label: '达人黑名单', key: TALENT_MENU_KEYS.blacklist, icon: icons.shield }
    ]
  },
  {
    label: '寄样审核',
    key: '/sample',
    icon: icons.gift,
    _section: 'sample',
    roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF]
  },
  {
    label: '系统管理',
    key: 'system-group',
    icon: icons.settings,
    _section: 'system',
    roles: [ROLE.ADMIN],
    children: [
      { label: '用户管理', key: '/system/users', icon: icons.user },
      { label: '角色管理', key: '/system/roles', icon: icons.shield },
      { label: '部门管理', key: '/system/depts', icon: icons.list },
      { label: '系统配置', key: '/system/config', icon: icons.settings },
      { label: '提成规则', key: '/system/commission-rules', icon: icons.settings },
      { label: '抖店联调', key: '/system/douyin', icon: icons.truck },
      { label: '操作日志', key: '/system/operation-logs', icon: icons.list }
    ]
  }
]

const menuOptions = computed(() => {
  const roles = authStore.roleCodes
  const section = resolveActiveSection(route.path)
  return filterAccessibleMenus(rawMenus, roles, section)
    .map(({ roles: _roles, _section: _s, children, ...menu }) => {
      const localizedMenu = { ...menu }
      if (isChannelStaffOnly.value && localizedMenu.key === 'talent-group') {
        localizedMenu.label = '我的达人'
      }
      if ((isChannelStaffOnly.value || isBizStaffOnly.value) && localizedMenu.key === 'data-group') {
        localizedMenu.label = '我的业绩'
      }
      if (isOpsStaffOnly.value && localizedMenu.key === '/ops/shipping') {
        localizedMenu.label = '寄样发货台'
      }
      if (!children?.length) return localizedMenu
      let filteredChildren = children.filter((child) => hasAccess(roles, child.roles))
      if (isChannelStaffOnly.value && localizedMenu.key === 'talent-group') {
        filteredChildren = filteredChildren.filter((child) =>
          [TALENT_MENU_KEYS.teamPublic, TALENT_MENU_KEYS.myTalents].includes(child.key as any)
        )
      }
      if (isChannelStaffOnly.value || isBizStaffOnly.value) {
        filteredChildren = filteredChildren.map((child) => {
          if (child.key === '/data') return { ...child, label: '我的业绩' }
          if (isChannelStaffOnly.value && child.key === TALENT_MENU_KEYS.myTalents) return { ...child, label: '我的达人' }
          if (isChannelStaffOnly.value && child.key === TALENT_MENU_KEYS.teamPublic) return { ...child, label: '公海达人' }
          return child
        })
      }
      return {
        ...localizedMenu,
        children: filteredChildren
      }
    })
    .filter((menu) => !('children' in menu) || !Array.isArray(menu.children) || menu.children.length > 0)
})

const activeMenuKey = computed(() => {
  if (isRoutePathUnderPrefix(route.path, '/ops/shipping')) return '/ops/shipping'
  if (isRoutePathUnderPrefix(route.path, '/ops/exclusive')) return '/ops/exclusive'
  if (isRoutePathUnderPrefix(route.path, '/system/depts')) return '/system/depts'
  if (isRoutePathUnderPrefix(route.path, '/system/config')) return '/system/config'
  if (isRoutePathUnderPrefix(route.path, '/system/douyin')) return '/system/douyin'
  if (isRoutePathUnderPrefix(route.path, '/system/operation-logs')) return '/system/operation-logs'
  if (isRoutePathUnderPrefix(route.path, '/system/roles')) return '/system/roles'
  if (isRoutePathUnderPrefix(route.path, '/system/users')) return '/system/users'
  if (route.path === '/data/orders') return '/data/orders'
  if (route.path === '/product/manage/products') return '/product/manage/products'
  if (route.path === '/product/manage') return '/product/manage'
  if (isRoutePathUnderPrefix(route.path, '/product/manage')) return '/product/manage'
  if (isRoutePathUnderPrefix(route.path, '/product/activity')) return '/product/manage'
  if (isRoutePathUnderPrefix(route.path, '/product/review')) return '/product/manage/products'
  if (isRoutePathUnderPrefix(route.path, '/product/library')) return '/product'
  if (isRoutePathUnderPrefix(route.path, '/talent')) {
    const view = typeof route.query.view === 'string' ? route.query.view : 'TEAM_PUBLIC'
    if (view === 'MY_TALENTS') return TALENT_MENU_KEYS.myTalents
    if (view === 'NATURAL_ORDERS') return TALENT_MENU_KEYS.naturalOrders
    if (view === 'BLACKLIST') return TALENT_MENU_KEYS.blacklist
    return TALENT_MENU_KEYS.teamPublic
  }
  return route.path
})

// 父菜单组 key -> section 的映射
const GROUP_KEY_TO_SECTION: Record<string, string> = {
  'attribution-group': 'attribution',
  'data-group': 'data',
  'product-manage-group': 'product-manage',
  'talent-group': 'talent',
  '/system': 'system'
}

// 受控展开 keys：当前 section 对应的父组自动展开，同时保留用户手动展开的其他组
const manualExpandedKeys = ref<string[]>([])

const expandedKeys = computed(() => {
  const section = resolveActiveSection(route.path)
  const autoKeys = Object.entries(GROUP_KEY_TO_SECTION)
    .filter(([, s]) => s === section)
    .map(([k]) => k)
  const combined = new Set([...autoKeys, ...manualExpandedKeys.value])
  return [...combined]
})

function handleExpandedKeys(keys: string[]) {
  manualExpandedKeys.value = keys
}

function handleMenuClick(key: string) {
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
