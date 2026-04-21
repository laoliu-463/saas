import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { ROLE_CODES, hasAccess } from '../constants/rbac';

const ROLE = ROLE_CODES;

const HOME_CANDIDATES = ['/data', '/product', '/talent', '/ops/shipping', '/sample', '/system/users'];

const resolveHomePath = (authStore: ReturnType<typeof useAuthStore>): string => {
  const roles = authStore.roleCodes;
  const accessible = HOME_CANDIDATES.find((path) => {
    const matched = router.resolve(path).matched;
    const required = matched[matched.length - 1]?.meta?.roles as string[] | undefined;
    return hasAccess(roles, required);
  });
  return accessible || '/login';
};

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
          component: () => import('../views/product/index.vue'),
          meta: { title: '商品库', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF] }
        },
        {
          path: 'product/activity',
          component: () => import('../views/product/ActivityList.vue'),
          meta: { title: '活动列表', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF] }
        },
        {
          path: 'product/:id',
          component: () => import('../views/product/ProductDetail.vue'),
          meta: { title: '商品详情', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF] }
        },
        {
          path: 'talent',
          component: () => import('../views/talent/index.vue'),
          meta: { title: '达人 CRM', roles: [ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF] }
        },
        {
          path: 'talent/:id',
          component: () => import('../views/talent/TalentDetail.vue'),
          meta: { title: '达人详情', roles: [ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF] }
        },
        {
          path: 'sample',
          component: () => import('../views/sample/index.vue'),
          meta: { title: '寄样台', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF, ROLE.OPS_STAFF] }
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
          meta: { title: '独家状态', roles: [ROLE.OPS_STAFF, ROLE.BIZ_LEADER, ROLE.CHANNEL_LEADER, ROLE.ADMIN] }
        },
        {
          path: 'ops/shipping',
          component: () => import('../views/ops/Shipping.vue'),
          meta: { title: '物流发货', roles: [ROLE.OPS_STAFF] }
        },
        {
          path: 'data',
          component: () => import('../views/data/index.vue'),
          meta: { title: '核心看板', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF] }
        },
        {
          path: 'data/orders',
          component: () => import('../views/data/OrderList.vue'),
          meta: { title: '订单明细', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF] }
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
        { path: '', redirect: '/data' }
      ]
    }
  ]
});

router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore();
  if (to.path !== '/login' && !authStore.isLoggedIn) {
    next('/login');
    return;
  }
  if (to.path === '/login' && authStore.isLoggedIn) {
    next(resolveHomePath(authStore));
    return;
  }
  if (to.path === '/') {
    next(resolveHomePath(authStore));
    return;
  }
  const requiredRoles = to.meta?.roles as string[] | undefined;
  if (!hasAccess(authStore.roleCodes, requiredRoles)) {
    next(resolveHomePath(authStore));
    return;
  }
  next();
});

export default router;
