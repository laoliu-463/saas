<template>
  <div class="login-container">
    <n-card title="抖音团长 SaaS V2.2 登录" bordered size="huge" style="width: 400px; border-radius: 12px; box-shadow: 0 8px 24px rgba(0,0,0,0.1);">
      <n-form ref="formRef" :model="form" :rules="rules" @submit.prevent="handleLogin" size="large">
        <n-form-item label="用户名" path="username">
          <n-input v-model:value="form.username" placeholder="请输入 admin" />
        </n-form-item>
        <n-form-item label="密码" path="password">
          <n-input type="password" v-model:value="form.password" placeholder="请输入密码" show-password-on="click" />
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
import { login as loginApi } from '../api/auth';

const router = useRouter();
const message = useMessage();
const authStore = useAuthStore();

const formRef = ref();
const form = ref({ username: '', password: '' });

const rules = {
  username: { required: true, message: '请输入用户名', trigger: 'blur' },
  password: { required: true, message: '请输入密码', trigger: 'blur' }
};

const handleLogin = () => {
  formRef.value?.validate(async (errors: any) => {
    if (!errors) {
      try {
        const res: any = await loginApi({ username: form.value.username, password: form.value.password });
        if (res.code === 200 && res.data?.token) {
          authStore.login(res.data.token, res.data);
          message.success('登录成功');
          router.push('/');
        } else if (res.code === 200 && !res.data?.token) {
          message.error('登录成功但未返回访问令牌，请联系管理员排查');
        } else {
          message.error(res.msg || '登录失败');
        }
      } catch (error: any) {
        message.error(error?.response?.data?.msg || '登录失败，请检查账号密码');
      }
    }
  });
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
