<template>
  <div class="login-container">
    <n-card title="抖音团长 SaaS V2.2 登录" bordered size="huge" style="width: 400px; border-radius: 12px; box-shadow: 0 8px 24px rgba(0,0,0,0.1);">
      <n-form @submit.prevent="handleLogin" size="large">
        <n-form-item label="用户名">
          <n-input v-model:value="form.username" placeholder="请输入 admin" />
        </n-form-item>
        <n-form-item label="密码">
          <n-input type="password" v-model:value="form.password" placeholder="admin123" show-password-on="click" />
        </n-form-item>
        <n-button type="primary" block attr-type="submit" style="margin-top: 16px;">登录</n-button>
      </n-form>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useMessage } from 'naive-ui';
import { useAuthStore } from '../stores/auth';

const router = useRouter();
const message = useMessage();
const authStore = useAuthStore();

const form = ref({ username: '', password: '' });

const handleLogin = () => {
    if (form.value.username === 'admin' && form.value.password === 'admin123') {
        authStore.login('mock-token-12345', { 
            username: 'admin', 
            realName: '管理员',
            roleCodes: ['admin'], 
            dataScope: 'ALL'
        });
        message.success('登录成功');
        router.push('/');
    } else {
        message.error('账号或密码错误（请输入 admin / admin123）');
    }
};
</script>

<style scoped>
.login-container {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 100vh;
    background: linear-gradient(135deg, #e0f2f1 0%, #ffffff 100%);
}
</style>
