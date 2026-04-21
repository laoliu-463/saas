<template>
  <n-layout-header bordered style="height: 64px; padding: 0 24px; display: flex; align-items: center; justify-content: space-between; background: #fff;">
    <div style="display: flex; align-items: center; gap: 16px;">
      <n-switch v-model:value="appStore.collapsed" size="small" />
      <n-breadcrumb>
        <n-breadcrumb-item v-for="item in matchedRoutes" :key="item.path">
          {{ item.meta?.title || '首页' }}
        </n-breadcrumb-item>
      </n-breadcrumb>
    </div>
    <div style="display: flex; align-items: center; gap: 16px;">
        <span style="color: #666;">欢迎您，{{ authStore.userInfo?.realName || authStore.userInfo?.username }}</span>
        <n-button @click="logout" type="warning" size="small" ghost>退出登录</n-button>
    </div>
  </n-layout-header>
</template>

<script setup lang="ts">
import { useAuthStore } from '../../stores/auth';
import { useAppStore } from '../../stores/app';
import { useRouter, useRoute } from 'vue-router';
import { useMessage } from 'naive-ui';
import { computed } from 'vue';

const authStore = useAuthStore();
const appStore = useAppStore();
const router = useRouter();
const route = useRoute();
const message = useMessage();

const matchedRoutes = computed(() => route.matched.filter(item => item.meta && item.meta.title));

const logout = () => {
    authStore.logout();
    message.success('已安全退出');
    router.push('/login');
};
</script>
