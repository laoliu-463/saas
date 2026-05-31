<!--
  Login - 登录页面组件

  用途：用户登录入口，提供用户名/密码表单认证。
  采用左右分栏布局：左侧品牌展示区（产品特色介绍），右侧登录表单区。

  特性：
    - Naive UI 表单验证（用户名和密码均必填）
    - 登录成功后存储 Token 和用户信息到 authStore
    - 支持 redirect 查询参数，登录后跳转到指定页面（经安全校验）
    - 登录失败显示服务端返回的错误消息
    - 响应式设计：窄屏时隐藏左侧品牌区

  使用场景：未登录用户访问任意页面时的登录入口。
-->
<template>
  <div class="login-wrapper">
    <!-- 左侧品牌展示区：产品 Logo、名称、功能卖点列表 -->
    <div class="login-brand">
      <div class="brand-content">
        <div class="brand-logo">
          <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
            <rect width="48" height="48" rx="12" fill="rgba(255,255,255,0.2)"/>
            <path d="M24 11L36 36H12L24 11Z" fill="white" opacity="0.92"/>
            <path d="M17 32H31" stroke="rgba(255,255,255,0.72)" stroke-width="2.4" stroke-linecap="round"/>
            <circle cx="24" cy="27" r="4" fill="var(--color-primary)"/>
            <circle cx="24" cy="27" r="1.4" fill="white" opacity="0.9"/>
          </svg>
        </div>
        <h1 class="brand-title">抖音团长 SaaS</h1>
        <p class="brand-desc">一站式达人分销管理系统</p>
        <div class="brand-features">
          <div class="feature-item">
            <span class="feature-dot"></span>
            <span>商品库智能选品</span>
          </div>
          <div class="feature-item">
            <span class="feature-dot"></span>
            <span>达人CRM精细化运营</span>
          </div>
          <div class="feature-item">
            <span class="feature-dot"></span>
            <span>寄样全流程自动化</span>
          </div>
          <div class="feature-item">
            <span class="feature-dot"></span>
            <span>数据看板实时分析</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧登录表单区：登录卡片包含表头、登录表单和版本信息 -->
    <div class="login-form-area">
      <div class="login-card">
        <!-- 登录卡片头部：欢迎文案 -->
        <div class="login-header">
          <h2>欢迎回来</h2>
          <p>请登录您的账号以继续</p>
        </div>

        <n-form
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          @submit.prevent="handleLogin"
        >
          <n-form-item path="username">
            <n-input
              v-model:value="form.username"
              placeholder="请输入用户名或姓名"
              data-testid="login-username"
              :input-props="{ autocomplete: 'username' }"
            >
              <template #prefix>
                <n-icon size="20"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="8" r="4"/><path d="M4 21c0-4.4 3.6-8 8-8s8 3.6 8 8"/></svg></n-icon>
              </template>
            </n-input>
          </n-form-item>

          <n-form-item path="password">
            <n-input
              v-model:value="form.password"
              type="password"
              placeholder="请输入密码"
              data-testid="login-password"
              show-password-on="click"
              :input-props="{ autocomplete: 'current-password' }"
            >
              <template #prefix>
                <n-icon size="20"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0110 0v4"/><circle cx="12" cy="16" r="1"/></svg></n-icon>
              </template>
            </n-input>
          </n-form-item>

          <n-button
            type="primary"
            block
            attr-type="submit"
            :loading="loading"
            class="login-btn"
            data-testid="login-submit"
          >
            登 录
          </n-button>
        </n-form>

        <div class="login-footer">
          <span>V2.2 | Colonel SaaS</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage, NIcon } from 'naive-ui'
import { useAuthStore } from '../stores/auth'
import { login as loginApi } from '../api/auth'
import { resolveSafePostLoginRedirect } from '../router/redirect'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const authStore = useAuthStore()

/** 登录按钮的加载状态，防止重复提交 */
const loading = ref(false)
/** Naive UI 表单组件引用，用于手动触发表单验证 */
const formRef = ref()
/** 登录表单数据：用户名和密码 */
const form = ref({ username: '', password: '' })

/** 表单验证规则：用户名和密码均必填，失去焦点时触发验证 */
const rules = {
  username: { required: true, message: '请输入用户名或姓名', trigger: 'blur' },
  password: { required: true, message: '请输入密码', trigger: 'blur' }
}

/**
 * 处理登录提交
 * 流程：表单验证 -> 调用登录 API -> 成功则存储 Token 并跳转，失败则显示错误消息
 * 跳转目标：优先使用 redirect 查询参数（经安全校验），默认跳转到 /data 页面
 */
const handleLogin = () => {
  formRef.value?.validate(async (errors: any) => {
    /** 表单验证失败时直接返回，不发起请求 */
    if (errors) return
    loading.value = true
    try {
      const res: any = await loginApi({
        username: form.value.username,
        password: form.value.password
      })
      if (res.code === 200 && res.data?.token) {
        /** 登录成功且返回了 Token：存储到 authStore 并跳转 */
        authStore.login(res.data.token, res.data)
        message.success('登录成功')
        router.push(resolveSafePostLoginRedirect(route.query.redirect, '/data'))
      } else if (res.code === 200 && !res.data?.token) {
        /** 登录接口返回成功但未返回 Token，属于异常情况 */
        message.error('登录成功但未返回访问令牌，请联系管理员排查')
      } else {
        /** 登录接口返回业务错误码 */
        message.error(res.msg || '登录失败')
      }
    } catch (error: any) {
      /** 网络异常或 HTTP 错误 */
      message.error(error?.response?.data?.msg || error?.msg || '登录失败，请检查账号密码')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-wrapper {
  display: flex;
  min-height: 100vh;
}

/* ---- 左侧品牌区 ---- */
.login-brand {
  flex: 1;
  background: var(--color-primary-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
  position: relative;
  overflow: hidden;
}

.login-brand::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -30%;
  width: 600px;
  height: 600px;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 50%;
}

.login-brand::after {
  content: '';
  position: absolute;
  bottom: -20%;
  left: -10%;
  width: 400px;
  height: 400px;
  background: rgba(255, 255, 255, 0.04);
  border-radius: 50%;
}

.brand-content {
  position: relative;
  z-index: 1;
  max-width: 400px;
  color: white;
}

.brand-logo {
  margin-bottom: 24px;
}

.brand-title {
  font-size: var(--text-3xl);
  font-weight: 700;
  margin: 0 0 12px;
  color: white;
  letter-spacing: 1px;
}

.brand-desc {
  font-size: var(--text-lg);
  opacity: 0.85;
  margin: 0 0 40px;
  line-height: 1.6;
}

.brand-features {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: var(--text-base);
  opacity: 0.9;
}

.feature-dot {
  width: 8px;
  height: 8px;
  background: white;
  border-radius: 50%;
  flex-shrink: 0;
}

/* ---- 右侧登录表单 ---- */
.login-form-area {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  padding: 48px;
}

.login-card {
  width: 100%;
  max-width: 400px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-xl);
  padding: 40px 36px;
  box-shadow: var(--shadow-modal);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-header h2 {
  font-size: var(--text-2xl);
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 8px;
}

.login-header p {
  font-size: var(--text-base);
  color: var(--text-secondary);
  margin: 0;
}

.login-btn {
  margin-top: 8px;
  height: 44px;
  font-size: var(--text-lg);
  font-weight: 600;
  border-radius: var(--radius-md);
  letter-spacing: 4px;
}

.login-footer {
  text-align: center;
  margin-top: 24px;
  font-size: var(--text-sm);
  color: var(--text-muted);
}

/* ---- 响应式 ---- */
@media (max-width: 768px) {
  .login-brand {
    display: none;
  }

  .login-form-area {
    padding: 24px;
  }

  .login-card {
    padding: 32px 24px;
  }
}
</style>
