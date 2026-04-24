<template>
  <n-layout-sider
    v-model:collapsed="appStore.collapsed"
    bordered
    collapse-mode="width"
    :collapsed-width="64"
    :width="200"
    show-trigger="bar"
  >
    <div class="logo">
      <span v-if="!appStore.collapsed">SAAS</span>
      <span v-else>S</span>
    </div>
    <div v-if="envLabel && !appStore.collapsed" class="env-label">{{ envLabel }}</div>
    <n-menu v-if="menuOptions.length" :options="menuOptions" :value="route.path" @update:value="handleMenuClick" />
    <div v-else class="empty-tip">
      当前账号没有可见菜单，请联系管理员配置角色权限。
    </div>
  </n-layout-sider>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useAppStore } from '../../stores/app';
import { useAuthStore } from '../../stores/auth';
import { ROLE_CODES, hasAccess } from '../../constants/rbac';

const appStore = useAppStore();
const authStore = useAuthStore();
const router = useRouter();
const route = useRoute();
const ROLE = ROLE_CODES;
const envLabel = import.meta.env.VITE_ENV_LABEL as string | undefined;

const rawMenus = [
  {
    label: '数据平台',
    key: 'data-group',
    roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF],
    children: [
      { label: '核心看板', key: '/data', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF] },
      { label: '订单明细', key: '/data/orders', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF] }
    ]
  },
  {
    label: '商品库',
    key: 'product-group',
    roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF],
    children: [
      { label: '商品列表', key: '/product', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF] },
      { label: '活动列表', key: '/product/activity', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF] }
    ]
  },
  { label: '达人 CRM', key: '/talent', roles: [ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF] },
  { label: '寄样台', key: '/sample', roles: [ROLE.BIZ_LEADER, ROLE.BIZ_STAFF, ROLE.CHANNEL_LEADER, ROLE.CHANNEL_STAFF, ROLE.OPS_STAFF] },
  {
    label: '运营中心',
    key: 'ops-group',
    roles: [ROLE.OPS_STAFF, ROLE.BIZ_LEADER, ROLE.CHANNEL_LEADER],
    children: [
      { label: '独家状态监控', key: '/ops/exclusive', roles: [ROLE.OPS_STAFF, ROLE.BIZ_LEADER, ROLE.CHANNEL_LEADER] },
      { label: '物流发货', key: '/ops/shipping', roles: [ROLE.OPS_STAFF] }
    ]
  },
  {
    label: '系统管理',
    key: 'system',
    roles: [ROLE.ADMIN],
    children: [
      { label: '用户管理', key: '/system/users', roles: [ROLE.ADMIN] },
      { label: '角色管理', key: '/system/roles', roles: [ROLE.ADMIN] }
    ]
  }
];

const menuOptions = computed(() => {
  const roles = authStore.roleCodes;
  return rawMenus
    .map((menu) => {
      if (!hasAccess(roles, menu.roles)) return null;
      if (!Array.isArray(menu.children)) return menu;
      const children = menu.children.filter((child) => hasAccess(roles, child.roles));
      if (!children.length) return null;
      return { ...menu, children };
    })
    .filter(Boolean);
});

const handleMenuClick = (key: string) => {
  router.push(key);
};
</script>

<style scoped>
.logo {
  height: 64px;
  display: flex;
  justify-content: center;
  align-items: center;
  font-weight: bold;
  font-size: 20px;
  border-bottom: 1px solid #efefef;
}

.env-label {
  margin: 8px 12px 4px 12px;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  color: #8a6d00;
  background: #fff8db;
  border: 1px solid #f0d27a;
  text-align: center;
}

.empty-tip {
  padding: 16px;
  color: #999;
  font-size: 12px;
}
</style>





