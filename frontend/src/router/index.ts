import { createRouter, createWebHistory } from 'vue-router'
import { ROLE_CODES, hasAccess } from '../constants/rbac'
import { useAuthStore } from '../stores/auth'
import { nowMs, recordFrontendTiming } from '../utils/performanceTiming'
import { createGuardWarningDeduper, resolveGuardDecision, type GuardRedirectDecision } from './guard'

const ROLE = ROLE_CODES
const HOME_CANDIDATES = ['/data', '/orders', '/product', '/product/manage', '/product/manage/products', '/talent', '/ops/shipping', '/sample', '/system/users']
const CHANNEL_STAFF_HOME_CANDIDATES = ['/product', '/talent', '/sample', '/data']
const BIZ_STAFF_HOME_CANDIDATES = ['/product/manage/products', '/sample', '/data']
const OPS_STAFF_HOME_CANDIDATES = ['/ops/shipping']

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('../views/Login.vue') },
    {
      path: '/',
      component: () => import('../views/layout/index.vue'),
      children: [
        {
          path: 'product',
          component: () => import('../views/product/ProductLibrary.vue'),
          meta: { title: '商品库', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF] }
        },
        {
          path: 'product/library',
          component: () => import('../views/product/ProductLibrary.vue'),
          meta: { title: '商品库', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF] }
        },
        {
          path: 'product/manage',
          component: () => import('../views/product/ActivityList.vue'),
          meta: { title: '活动列表', roles: [ROLE.BIZ_LEADER] }
        },
        {
          path: 'product/manage/products',
          component: () => import('../views/product/index.vue'),
          meta: { title: '商品列表', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF] }
        },
        {
          path: 'product/manage/list',
          redirect: '/product/manage/products'
        },
        {
          path: 'product/manage/:activityId',
          component: () => import('../views/product/index.vue'),
          meta: { title: '商品列表', roles: [ROLE.BIZ_LEADER] }
        },
        {
          path: 'product/activity',
          redirect: '/product/manage',
          meta: { title: '商品管理', roles: [ROLE.BIZ_LEADER] }
        },
        {
          path: 'product/activity/:activityId',
          redirect: (to) => `/product/manage/${to.params.activityId}`,
          meta: { title: '商品列表', roles: [ROLE.BIZ_LEADER] }
        },
        {
          path: 'product/:id',
          redirect: '/product',
          meta: { title: '商品库', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF] }
        },
        {
          path: 'talent',
          component: () => import('../views/talent/index.vue'),
          meta: { title: '达人 CRM', roles: [ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF] }
        },
        {
          path: 'sample',
          component: () => import('../views/sample/index.vue'),
          meta: { title: '合作单', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF] }
        },
        {
          path: 'sample/apply',
          component: () => import('../views/sample/Apply.vue'),
          meta: { title: '寄样申请', roles: [ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF] }
        },
        {
          path: 'sample/:id',
          component: () => import('../views/sample/SampleDetail.vue'),
          meta: { title: '寄样详情', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF, ROLE.OPS_STAFF] }
        },
        {
          path: 'ops/exclusive',
          component: () => import('../views/ops/ExclusiveStatus.vue'),
          meta: { title: '独家状态', roles: [ROLE.BIZ_LEADER, ROLE.CHANNEL_LEADER, ROLE.ADMIN] }
        },
        {
          path: 'ops/shipping',
          component: () => import('../views/ops/Shipping.vue'),
          meta: { title: '发货台', roles: [ROLE.OPS_STAFF] }
        },
        {
          path: 'data',
          component: () => import('../views/data/index.vue'),
          meta: { title: '数据看板', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF] }
        },
        {
          path: 'data/orders',
          component: () => import('../views/data/OrderList.vue'),
          meta: { title: '订单明细', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF] }
        },
        {
          path: 'system',
          redirect: '/system/users',
          meta: { title: '系统管理', roles: [ROLE.ADMIN] }
        },
        {
          path: 'system/users',
          component: () => import('../views/system/UserList.vue'),
          meta: { title: '用户管理', roles: [ROLE.ADMIN] }
        },
        {
          path: 'system/roles',
          component: () => import('../views/system/RoleList.vue'),
          meta: { title: '角色管理', roles: [ROLE.ADMIN] }
        },
        {
          path: 'system/depts',
          component: () => import('../views/system/DeptList.vue'),
          meta: { title: '部门管理', roles: [ROLE.ADMIN] }
        },
        {
          path: 'system/departments',
          redirect: '/system/depts',
          meta: { title: '部门管理', roles: [ROLE.ADMIN] }
        },
        {
          path: 'system/rule-center',
          component: () => import('../views/system/rule-center/index.vue'),
          meta: { title: '规则中心', roles: [ROLE.ADMIN] }
        },
        {
          path: 'system/config',
          component: () => import('../views/system/ConfigList.vue'),
          meta: { title: '高级配置', roles: [ROLE.ADMIN] }
        },
        {
          path: 'system/commission-rules',
          component: () => import('../views/system/CommissionRuleList.vue'),
          meta: { title: '提成规则', roles: [ROLE.ADMIN] }
        },
        {
          path: 'system/douyin',
          component: () => import('../views/ops/DouyinIntegration.vue'),
          meta: { title: '抖店联调', roles: [ROLE.ADMIN] }
        },
        {
          path: 'system/operation-logs',
          component: () => import('../views/system/OperationLogList.vue'),
          meta: { title: '操作日志', roles: [ROLE.ADMIN] }
        },
        {
          path: 'profile',
          component: () => import('../views/profile/UserProfile.vue'),
          meta: { title: '个人中心' }
        },
        {
          path: 'orders',
          component: () => import('../views/orders/index.vue'),
          meta: { title: '订单工作台', roles: [ROLE.BIZ_LEADER, ROLE.CHANNEL_LEADER, ROLE.ADMIN] }
        },
        {
          path: 'dashboard',
          component: () => import('../views/dashboard/index.vue'),
          meta: { title: '归因概览', roles: [ROLE.BIZ_LEADER, ROLE.CHANNEL_LEADER, ROLE.ADMIN] }
        },
        { path: '', redirect: '/data' }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/data' }
  ]
})

const shouldEmitGuardWarning = createGuardWarningDeduper()
let routeStartedAt = 0

const resolveHomePath = (authStore: ReturnType<typeof useAuthStore>): string => {
  const roles = authStore.roleCodes
  const candidates =
    roles.includes(ROLE.CHANNEL_STAFF) && !roles.includes(ROLE.CHANNEL_LEADER) && !roles.includes(ROLE.ADMIN)
      ? CHANNEL_STAFF_HOME_CANDIDATES
      : roles.includes(ROLE.OPS_STAFF) && !roles.includes(ROLE.ADMIN)
        ? OPS_STAFF_HOME_CANDIDATES
      : roles.includes(ROLE.BIZ_STAFF) && !roles.includes(ROLE.BIZ_LEADER) && !roles.includes(ROLE.ADMIN)
        ? BIZ_STAFF_HOME_CANDIDATES
        : HOME_CANDIDATES
  const accessible = candidates.find((path) => {
    const matched = router.resolve(path).matched
    const required = matched[matched.length - 1]?.meta?.roles as string[] | undefined
    return hasAccess(roles, required)
  })
  return accessible || '/login'
}

router.beforeEach((to, from) => {
  routeStartedAt = nowMs()
  recordFrontendTiming('router', {
    phase: 'beforeEach',
    from: from.fullPath || '(start)',
    to: to.fullPath
  })

  const authStore = useAuthStore()
  authStore.hydrateFromStorage()

  const roleCodes = authStore.roleCodes
  const requiredRoles = to.meta?.roles as string[] | undefined
  const decision = resolveGuardDecision({
    toPath: to.path,
    toFullPath: to.fullPath,
    fromPath: from.path,
    isLoggedIn: authStore.isLoggedIn,
    roleCodes,
    requiredRoles,
    resolveHomePath: () => resolveHomePath(authStore)
  })

  if (decision.type !== 'allow') {
    warnGuardDecision(decision, from.fullPath, to.fullPath, roleCodes)
  }

  if (decision.type === 'redirect') {
    return decision.redirectTarget
  }

  if (decision.type === 'abort') {
    return false
  }

  return true
})

router.afterEach((to, from, failure) => {
  const durationMs = routeStartedAt ? Math.round(nowMs() - routeStartedAt) : 0
  recordFrontendTiming('router', {
    phase: 'afterEach',
    from: from.fullPath || '(start)',
    to: to.fullPath,
    durationMs,
    failure: failure ? failure.type : undefined
  }, { failed: Boolean(failure) })
})

function warnGuardDecision(decision: GuardRedirectDecision, from: string, to: string, roleCodes: string[]) {
  if (!shouldEmitGuardWarning(decision, from, to, roleCodes)) {
    return
  }
  console.warn('[router guard] redirect', {
    from,
    to,
    roleCodes,
    redirectTarget: decision.redirectTarget,
    reason: decision.reason
  })
}

export default router
