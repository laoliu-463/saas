<template>
  <div class="profile-page app-page" data-testid="profile-current-user">
    <PageHeader title="个人中心" description="查看当前账号、数据范围与权限包，并维护自己的登录密码。">
      <template #actions>
        <n-button :loading="loading" data-testid="profile-refresh" @click="loadProfile">刷新资料</n-button>
      </template>
    </PageHeader>

    <div class="profile-layout">
      <section class="app-panel profile-section">
        <div class="section-header">
          <div>
            <h2>账号资料</h2>
            <p>来自统一用户域当前用户接口。</p>
          </div>
          <n-tag :type="dataScopeTagType" size="small">{{ dataScopeLabel }}</n-tag>
        </div>
        <n-descriptions :column="2" bordered size="small">
          <n-descriptions-item label="用户名">{{ currentUser?.username || '-' }}</n-descriptions-item>
          <n-descriptions-item label="真实姓名">{{ currentUser?.realName || '-' }}</n-descriptions-item>
          <n-descriptions-item label="用户 ID">{{ currentUser?.userId || currentUser?.id || '-' }}</n-descriptions-item>
          <n-descriptions-item label="部门 ID">{{ currentUser?.deptId || '-' }}</n-descriptions-item>
          <n-descriptions-item label="数据范围">{{ dataScopeLabel }}</n-descriptions-item>
          <n-descriptions-item label="角色">
            <n-space size="small">
              <n-tag v-for="role in roleCodes" :key="role" size="small" bordered>{{ role }}</n-tag>
              <span v-if="!roleCodes.length">-</span>
            </n-space>
          </n-descriptions-item>
        </n-descriptions>
      </section>

      <section class="app-panel profile-section">
        <div class="section-header">
          <div>
            <h2>数据范围解析</h2>
            <p>用于校验本人/本组/全部数据边界。</p>
          </div>
        </div>
        <n-descriptions :column="1" bordered size="small">
          <n-descriptions-item label="范围">{{ scopeResponse?.scope || '-' }}</n-descriptions-item>
          <n-descriptions-item label="范围编码">{{ scopeResponse?.code ?? '-' }}</n-descriptions-item>
          <n-descriptions-item label="限制用户">
            {{ scopeUserIdsText }}
          </n-descriptions-item>
        </n-descriptions>
      </section>

      <section class="app-panel profile-section">
        <div class="section-header">
          <div>
            <h2>权限自检</h2>
            <p>按资源域和操作名调用统一权限检查。</p>
          </div>
          <n-tag v-if="permissionResult" :type="permissionResult.allowed ? 'success' : 'error'" size="small">
            {{ permissionResult.allowed ? '允许' : '拒绝' }}
          </n-tag>
        </div>
        <div class="permission-check-form">
          <n-input v-model:value="permissionForm.resource" placeholder="资源域，如 product" data-testid="profile-permission-resource" />
          <n-input v-model:value="permissionForm.action" placeholder="操作名，如 audit" data-testid="profile-permission-action" />
          <n-button type="primary" :loading="checkingPermission" data-testid="profile-permission-check" @click="handlePermissionCheck">
            检查权限
          </n-button>
        </div>
      </section>

      <section class="app-panel profile-section">
        <div class="section-header">
          <div>
            <h2>修改密码</h2>
            <p>仅修改当前登录账号密码。</p>
          </div>
        </div>
        <n-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-placement="top">
          <n-form-item label="原密码" path="oldPassword">
            <n-input
              v-model:value="passwordForm.oldPassword"
              type="password"
              show-password-on="click"
              data-testid="profile-old-password"
            />
          </n-form-item>
          <n-form-item label="新密码" path="newPassword">
            <n-input
              v-model:value="passwordForm.newPassword"
              type="password"
              show-password-on="click"
              data-testid="profile-new-password"
            />
          </n-form-item>
          <n-form-item label="确认新密码" path="confirmPassword">
            <n-input
              v-model:value="passwordForm.confirmPassword"
              type="password"
              show-password-on="click"
              data-testid="profile-confirm-password"
            />
          </n-form-item>
          <n-button
            type="primary"
            :loading="changingPassword"
            data-testid="profile-change-password"
            @click="handleChangePassword"
          >
            更新密码
          </n-button>
        </n-form>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { notifyApiFailure } from '../../utils/requestError'
import { computed, onMounted, reactive, ref } from 'vue'
import { useMessage } from 'naive-ui'
import PageHeader from '../../components/PageHeader.vue'
import {
  changeCurrentUserPassword,
  checkCurrentUserPermission,
  getCurrentUser,
  getCurrentUserDataScope
} from '../../api/sys'
import { useAuthStore } from '../../stores/auth'

const message = useMessage()
const authStore = useAuthStore()
const loading = ref(false)
const checkingPermission = ref(false)
const changingPassword = ref(false)
const currentUser = ref<any>(null)
const scopeResponse = ref<any>(null)
const permissionResult = ref<any>(null)
const passwordFormRef = ref()

const permissionForm = reactive({
  resource: 'product',
  action: 'audit'
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const passwordRules = {
  oldPassword: { required: true, message: '请输入原密码', trigger: 'blur' },
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, message: '新密码至少 8 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string) => value === passwordForm.newPassword,
      message: '两次输入的新密码不一致',
      trigger: 'blur'
    }
  ]
}

const unwrapData = (res: any) => res?.data || res

const roleCodes = computed(() =>
  Array.isArray(currentUser.value?.roleCodes) ? currentUser.value.roleCodes : authStore.roleCodes
)

const dataScopeLabel = computed(() => {
  const scope = currentUser.value?.dataScopeName || scopeResponse.value?.scope || authStore.dataScope
  const labels: Record<string, string> = {
    self: '本人数据',
    group: '本组数据',
    all: '全部数据'
  }
  return labels[String(scope || '').toLowerCase()] || String(scope || '-')
})

const dataScopeTagType = computed(() => {
  const scope = String(currentUser.value?.dataScopeName || scopeResponse.value?.scope || '').toLowerCase()
  if (scope === 'all') return 'success'
  if (scope === 'group') return 'info'
  return 'warning'
})

const scopeUserIdsText = computed(() => {
  const userIds = scopeResponse.value?.userIds
  return Array.isArray(userIds) && userIds.length ? userIds.join('、') : '无用户限制'
})

const isValidationFailure = (error: unknown): boolean => Array.isArray(error)

const syncCurrentUserToAuthStore = (user: any) => {
  authStore.setUserInfo({
    ...user,
    id: user?.id || user?.userId,
    dataScope: user?.dataScopeName || user?.dataScope
  })
}

const loadProfile = async () => {
  loading.value = true
  try {
    const [userRes, scopeRes] = await Promise.all([
      getCurrentUser(),
      getCurrentUserDataScope()
    ])
    currentUser.value = unwrapData(userRes)
    scopeResponse.value = unwrapData(scopeRes)
    syncCurrentUserToAuthStore(currentUser.value)
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '加载个人资料失败' })
  } finally {
    loading.value = false
  }
}

const handlePermissionCheck = async () => {
  const resource = permissionForm.resource.trim()
  const action = permissionForm.action.trim()
  if (!resource || !action) {
    message.warning('请输入资源域和操作名')
    return
  }
  checkingPermission.value = true
  try {
    const res = await checkCurrentUserPermission({ resource, action })
    permissionResult.value = unwrapData(res)
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '权限检查失败' })
  } finally {
    checkingPermission.value = false
  }
}

const handleChangePassword = async () => {
  if (!passwordFormRef.value || changingPassword.value) return
  try {
    await passwordFormRef.value.validate()
  } catch (error) {
    if (!isValidationFailure(error)) {
      message.error((error as any)?.message || '表单校验失败')
    }
    return
  }

  changingPassword.value = true
  try {
    await changeCurrentUserPassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    message.success('密码已更新')
  } catch (error: any) {
    notifyApiFailure(error, message, { fallbackMessage: '密码更新失败' })
  } finally {
    changingPassword.value = false
  }
}

onMounted(() => {
  loadProfile()
})
</script>

<style scoped>
.profile-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 0.72fr);
  gap: 16px;
}

.profile-section {
  padding: 18px;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.section-header h2 {
  margin: 0;
  font-size: var(--text-lg);
  font-weight: 600;
  color: var(--text-primary);
}

.section-header p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: var(--text-sm);
}

.permission-check-form {
  display: grid;
  grid-template-columns: minmax(120px, 1fr) minmax(120px, 1fr) auto;
  gap: 12px;
  align-items: center;
}

@media (max-width: 960px) {
  .profile-layout {
    grid-template-columns: 1fr;
  }

  .permission-check-form {
    grid-template-columns: 1fr;
  }
}
</style>
