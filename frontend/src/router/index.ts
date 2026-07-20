import { createRouter, createWebHistory } from 'vue-router'
import { PERMISSION_CODES, hasPermission } from '../constants/permissions'
import { useAuthStore } from '../stores/auth'
import { nowMs, recordFrontendTiming } from '../utils/performanceTiming'
import { createGuardWarningDeduper, resolveGuardDecision, type GuardRedirectDecision } from './guard'

const HOME_CANDIDATES = ['/data', '/orders', '/product', '/product/manage', '/product/manage/products', '/talent', '/ops/shipping', '/sample', '/system/users']
const PERMISSION = PERMISSION_CODES

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
          meta: { title: '商品库', permissions: [PERMISSION.PRODUCT_ACCESS] }
        },
        {
          path: 'product/library',
          component: () => import('../views/product/ProductLibrary.vue'),
          meta: { title: '商品库', permissions: [PERMISSION.PRODUCT_ACCESS] }
        },
        {
          path: 'product/manage',
          component: () => import('../views/product/ActivityList.vue'),
          meta: { title: '活动列表', permissions: [PERMISSION.PRODUCT_MANAGE_ACCESS] }
        },
        {
          path: 'product/manage/products',
          component: () => import('../views/product/index.vue'),
          meta: { title: '商品列表', permissions: [PERMISSION.PRODUCT_MANAGE_ACCESS] }
        },
        {
          path: 'product/manage/list',
          redirect: '/product/manage/products'
        },
        {
          // 历史活动商品入口统一进入商品管理列表，使用 query 参数保留活动上下文。
          path: 'product/manage/:activityId',
          redirect: (to) => ({
            path: '/product/manage/products',
            query: { activityId: String(to.params.activityId ?? '') }
          }),
          meta: { title: '商品列表' }
        },
        {
          path: 'product/activity',
          redirect: '/product/manage',
          meta: { title: '商品管理', permissions: [PERMISSION.PRODUCT_MANAGE_ACCESS] }
        },
        {
          // 同步重定向到统一入口，避免二次跳转
          path: 'product/activity/:activityId',
          redirect: (to) => ({
            path: '/product/manage/products',
            query: { activityId: String(to.params.activityId ?? '') }
          }),
          meta: { title: '商品列表' }
        },
        {
          path: 'product/:id',
          redirect: '/product',
          meta: { title: '商品库', permissions: [PERMISSION.PRODUCT_ACCESS] }
        },
        {
          path: 'talent',
          component: () => import('../views/talent/index.vue'),
          meta: { title: '达人 CRM', permissions: [PERMISSION.TALENT_ACCESS] }
        },
        {
          path: 'sample',
          component: () => import('../views/sample/index.vue'),
          meta: { title: '合作单', permissions: [PERMISSION.SAMPLE_ACCESS] }
        },
        {
          path: 'sample/apply',
          component: () => import('../views/sample/Apply.vue'),
          meta: { title: '寄样申请', permissions: [PERMISSION.SAMPLE_ACCESS] }
        },
        {
          path: 'sample/:id',
          component: () => import('../views/sample/SampleDetail.vue'),
          meta: { title: '寄样详情', permissions: [PERMISSION.SAMPLE_ACCESS, PERMISSION.SHIPPING_ACCESS] }
        },
        {
          path: 'ops/exclusive',
          component: () => import('../views/ops/ExclusiveStatus.vue'),
          meta: { title: '独家状态', permissions: [PERMISSION.EXCLUSIVE_ACCESS] }
        },
        {
          path: 'ops/shipping',
          component: () => import('../views/ops/Shipping.vue'),
          meta: { title: '发货台', permissions: [PERMISSION.SHIPPING_ACCESS] }
        },
        {
          path: 'data',
          component: () => import('../views/data/index.vue'),
          meta: { title: '数据看板', permissions: [PERMISSION.DATA_ACCESS] }
        },
        {
          path: 'data/orders',
          component: () => import('../views/data/OrderList.vue'),
          meta: { title: '订单明细', permissions: [PERMISSION.DATA_ACCESS] }
        },
        {
          path: 'system',
          redirect: '/system/users',
          meta: { title: '系统管理', permissions: [PERMISSION.SYS_USER_ACCESS] }
        },
        {
          path: 'system/users',
          component: () => import('../views/system/UserList.vue'),
          meta: { title: '用户管理', permissions: [PERMISSION.SYS_USER_ACCESS] }
        },
        {
          path: 'system/roles',
          component: () => import('../views/system/RoleList.vue'),
          meta: { title: '角色管理', permissions: [PERMISSION.SYS_ROLE_ACCESS] }
        },
        {
          path: 'system/depts',
          component: () => import('../views/system/DeptList.vue'),
          meta: { title: '部门管理', permissions: [PERMISSION.SYS_DEPT_ACCESS] }
        },
        {
          path: 'system/departments',
          redirect: '/system/depts',
          meta: { title: '部门管理', permissions: [PERMISSION.SYS_DEPT_ACCESS] }
        },
        {
          path: 'system/rule-center',
          component: () => import('../views/system/rule-center/index.vue'),
          meta: { title: '规则中心', permissions: [PERMISSION.RULE_CENTER_ACCESS] }
        },
        {
          path: 'system/config',
          component: () => import('../views/system/ConfigList.vue'),
          meta: { title: '高级配置', permissions: [PERMISSION.SYS_CONFIG_ACCESS] }
        },
        {
          path: 'system/commission-rules',
          component: () => import('../views/system/CommissionRuleList.vue'),
          meta: { title: '提成规则', permissions: [PERMISSION.COMMISSION_RULE_ACCESS] }
        },
        {
          path: 'system/douyin',
          component: () => import('../views/ops/DouyinIntegration.vue'),
          meta: { title: '抖店联调', permissions: [PERMISSION.DOUYIN_ACCESS] }
        },
        {
          path: 'system/operation-logs',
          component: () => import('../views/system/OperationLogList.vue'),
          meta: { title: '操作日志', permissions: [PERMISSION.OPERATION_LOG_ACCESS] }
        },
        {
          path: 'profile',
          component: () => import('../views/profile/UserProfile.vue'),
          meta: { title: '个人中心' }
        },
        {
          path: 'orders',
          component: () => import('../views/orders/index.vue'),
          meta: { title: '订单工作台', permissions: [PERMISSION.ORDER_ACCESS] }
        },
        {
          path: 'dashboard',
          component: () => import('../views/dashboard/index.vue'),
          meta: { title: '归因概览', permissions: [PERMISSION.DASHBOARD_ACCESS] }
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
  const permissionCodes = authStore.permissionCodes
  const accessible = HOME_CANDIDATES.find((path) => {
    const matched = router.resolve(path).matched
    const required = matched[matched.length - 1]?.meta?.permissions as string[] | undefined
    return hasPermission(permissionCodes, required)
  })
  return accessible || '/profile'
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

  const permissionCodes = authStore.permissionCodes
  const requiredPermissions = to.meta?.permissions as string[] | undefined
  const decision = resolveGuardDecision({
    toPath: to.path,
    toFullPath: to.fullPath,
    fromPath: from.path,
    isLoggedIn: authStore.isLoggedIn,
    permissionCodes,
    requiredPermissions,
    resolveHomePath: () => resolveHomePath(authStore)
  })

  if (decision.type !== 'allow') {
    warnGuardDecision(decision, from.fullPath, to.fullPath, permissionCodes)
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

function warnGuardDecision(decision: GuardRedirectDecision, from: string, to: string, permissionCodes: string[]) {
  if (!shouldEmitGuardWarning(decision, from, to, permissionCodes)) {
    return
  }
  console.warn('[router guard] redirect', {
    from,
    to,
    permissionCodes,
    redirectTarget: decision.redirectTarget,
    reason: decision.reason
  })
}

export default router
