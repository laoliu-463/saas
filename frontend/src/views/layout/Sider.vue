<template>
  <n-layout-sider bordered collapse-mode="width" :collapsed-width="64" :width="200" v-model:collapsed="appStore.collapsed" show-trigger="bar">
    <div style="height: 64px; display: flex; justify-content: center; align-items: center; font-weight: bold; font-size: 20px; border-bottom: 1px solid #efefef;">
      <span v-if="!appStore.collapsed">SAAS</span>
      <span v-else>S</span>
    </div>
    <n-menu :options="menuOptions" :value="route.path" @update:value="handleMenuClick" />
  </n-layout-sider>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useAppStore } from '../../stores/app';
import { useAuthStore } from '../../stores/auth';

const appStore = useAppStore();
const authStore = useAuthStore();
const router = useRouter();
const route = useRoute();

const rawMenus = [
    { label: '数据平台', key: '/data', roles: ['admin', 'zs_leader', 'qd_leader'] },
    { label: '商品库', key: '/product', roles: ['admin', 'zs_staff', 'qd_staff'] },
    { label: '达人 CRM', key: '/talent', roles: ['admin', 'qd_staff'] },
    { label: '寄样台', key: '/sample', roles: ['admin', 'zs_staff', 'qd_staff'] },
];

const menuOptions = computed(() => {
    const roles = authStore.userInfo?.roleCodes || [];
    return rawMenus.filter(m => m.roles.some(r => roles.includes(r)));
});

const handleMenuClick = (key: string) => {
    router.push(key);
};
</script>
