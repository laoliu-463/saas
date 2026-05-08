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
        :collapsed="appStore.collapsed"
        :collapsed-width="64"
        :collapsed-icon-size="22"
        :indent="18"
        @update:value="handleMenuClick"
      />
      <div v-else class="empty-tip">当前账号没有可见菜单</div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NIcon } from 'naive-ui'
import { ROLE_CODES, hasAccess } from '../../constants/rbac'
import { useAppStore } from '../../stores/app'
import { useAuthStore } from '../../stores/auth'
import { isTestEnv } from '../../utils/env'

const appStore = useAppStore()
const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()
const ROLE = ROLE_CODES

const iconData = (d: string) => () =>
  h(NIcon, null, {
    default: () =>
      h(
        'svg',
        {
          viewBox: '0 0 24 24',
          fill: 'none',
          stroke: 'currentColor',
          'stroke-width': '2',
          'stroke-linecap': 'round',
          'stroke-linejoin': 'round',
          width: 20,
          height: 20
        },
        [h('path', { d })]
      )
  })

const icons = {
  chart: iconData('M18 20V10M12 20V4M6 20v-6'),
  bag: iconData('M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z'),
  user: iconData('M17 21v-2a4 4 0 00-4-4H11a4 4 0 00-4 4v2M12 11a4 4 0 100-8 4 4 0 000 8z'),
  gift: iconData('M20 12v8H4v-8M12 2v8M22 12H2M12 2c-2 0-4 4-4 4h4V2zM12 2c2 0 4 4 4 4h-4V2z'),
  settings: iconData('M12 15a3 3 0 100-6 3 3 0 000 6zM19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 11-2.83 2.83l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 11-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 112.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 112.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z'),
  truck: iconData('M1 6h2m0 0h16l4 6v6h-2M3 6v12m18-6h-6m-6 0H3m6 0v6m6-6v6m-6 0a2 2 0 100-4 2 2 0 000 4zm6 0a2 2 0 100-4 2 2 0 000 4z'),
  shield: iconData('M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z'),
  list: iconData('M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2')
}

interface MenuItem {
  label: string
  key: string
  icon?: () => any
  roles?: string[]
  children?: MenuItem[]
  _section?: string
}

/** 路由前缀 → 侧边栏分组 key 的映射（按长度降序匹配） */
const SECTION_MAP: [string, string][] = [
  ['/ops/shipping', 'ops'],
  ['/ops/exclusive', 'ops'],
  ['/system/operation-logs', 'system'],
  ['/system/config', 'system'],
  ['/system/douyin', 'system'],
  ['/system/roles', 'system'],
  ['/system/users', 'system'],
  ['/product/library', 'product'],
  ['/product/activity', 'product'],
  ['/product', 'product'],
  ['/data/orders', 'data'],
  ['/data', 'data'],
  ['/dashboard', 'attribution'],
  ['/orders', 'attribution'],
  ['/talent', 'talent'],
  ['/sample', 'sample'],
  ['/dev', 'dev']
]

const TALENT_MENU_KEYS = {
  teamPublic: '/talent?view=TEAM_PUBLIC',
  myTalents: '/talent?view=MY_TALENTS',
  naturalOrders: '/talent?view=NATURAL_ORDERS',
  blacklist: '/talent?view=BLACKLIST'
} as const

/** 根据当前路由确定应展示的侧边栏分组 */
const activeSection = computed(() => {
  const path = route.path
  for (const [prefix, section] of SECTION_MAP) {
    if (path.startsWith(prefix)) return section
  }
  return null
})

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
      { label: '订单明细', key: '/data/orders', icon: icons.list }
    ]
  },
  {
    label: '商品中心',
    key: 'product-group',
    icon: icons.bag,
    _section: 'product',
    roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF],
    children: [
      { label: '选品库', key: '/product', icon: icons.bag },
      { label: '商品库', key: '/product/library', icon: icons.list },
      { label: '活动列表', key: '/product/activity', icon: icons.list }
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
    label: '寄样台',
    key: '/sample',
    icon: icons.gift,
    _section: 'sample',
    roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF, ROLE.OPS_STAFF]
  },
  {
    label: '运营中心',
    key: 'ops-group',
    icon: icons.truck,
    _section: 'ops',
    roles: [ROLE.OPS_STAFF, ROLE.BIZ_LEADER, ROLE.CHANNEL_LEADER],
    children: [
      { label: '独家状态', key: '/ops/exclusive', icon: icons.shield },
      { label: '物流发货', key: '/ops/shipping', icon: icons.truck }
    ]
  },
  {
    label: '系统管理',
    key: 'system',
    icon: icons.settings,
    _section: 'system',
    roles: [ROLE.ADMIN],
    children: [
      { label: '用户管理', key: '/system/users', icon: icons.user },
      { label: '角色管理', key: '/system/roles', icon: icons.shield },
      { label: '系统配置', key: '/system/config', icon: icons.settings },
      { label: '抖店联调', key: '/system/douyin', icon: icons.truck },
      { label: '操作日志', key: '/system/operation-logs', icon: icons.list }
    ]
  }
]

if (isTestEnv) {
  rawMenus.push({
    label: '开发调试',
    key: 'dev-group',
    icon: icons.settings,
    _section: 'dev',
    roles: [ROLE.ADMIN],
    children: [{ label: '测试调试台', key: '/dev/test', icon: icons.settings }]
  })
}

const menuOptions = computed(() => {
  const roles = authStore.roleCodes
  const section = activeSection.value
  return rawMenus
    .filter((menu) => {
      // 按当前活跃的顶部导航分组过滤
      if (section && menu._section && menu._section !== section) return false
      return hasAccess(roles, menu.roles)
    })
    .map(({ roles: _roles, _section: _s, children, ...menu }) => {
      if (!children?.length) return menu
      return {
        ...menu,
        children: children.filter((child) => hasAccess(roles, child.roles))
      }
    })
    .filter((menu) => !('children' in menu) || !Array.isArray(menu.children) || menu.children.length > 0)
})

const activeMenuKey = computed(() => {
  if (route.path.startsWith('/ops/shipping')) return '/ops/shipping'
  if (route.path.startsWith('/ops/exclusive')) return '/ops/exclusive'
  if (route.path.startsWith('/system/config')) return '/system/config'
  if (route.path.startsWith('/system/douyin')) return '/system/douyin'
  if (route.path.startsWith('/system/operation-logs')) return '/system/operation-logs'
  if (route.path.startsWith('/system/roles')) return '/system/roles'
  if (route.path.startsWith('/system/users')) return '/system/users'
  if (route.path === '/data/orders') return '/data/orders'
  if (route.path.startsWith('/product/library')) return '/product/library'
  if (route.path.startsWith('/talent')) {
    const view = typeof route.query.view === 'string' ? route.query.view : 'TEAM_PUBLIC'
    if (view === 'MY_TALENTS') return TALENT_MENU_KEYS.myTalents
    if (view === 'NATURAL_ORDERS') return TALENT_MENU_KEYS.naturalOrders
    if (view === 'BLACKLIST') return TALENT_MENU_KEYS.blacklist
    return TALENT_MENU_KEYS.teamPublic
  }
  return route.path
})

function handleMenuClick(key: string) {
  if (key.startsWith('/talent?view=')) {
    const view = key.replace('/talent?view=', '')
    router.push({ path: '/talent', query: { view } })
    return
  }
  router.push(key)
}
</script>

<style scoped>
.app-sider {
  width: var(--sider-width);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  background: var(--bg-sidebar);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-right: 1px solid var(--border-color);
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
  transform: translateX(4px);
}
</style>