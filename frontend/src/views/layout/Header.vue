<template>
  <n-layout-header bordered style="height: 64px; padding: 0 24px; display: flex; align-items: center; justify-content: space-between; background: #fff;">
    <div style="display: flex; align-items: center; gap: 16px;">
      <n-switch v-model:value="appStore.collapsed" size="small" />
      <div style="font-weight: 600; font-size: 18px; color: var(--primary-color);">抖音团长 SaaS V2.2</div>
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
import { useRouter } from 'vue-router';
import { useMessage } from 'naive-ui';

const authStore = useAuthStore();
const appStore = useAppStore();
const router = useRouter();
const message = useMessage();

const logout = () => {
    authStore.logout();
    message.success('已安全退出');
    router.push('/login');
};
</script>
