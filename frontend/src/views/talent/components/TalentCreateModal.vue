<template>
  <n-modal :show="show" preset="card" title="新增达人" style="width: 760px" @update:show="closeModal">
    <n-form :model="formData" label-placement="left" label-width="130">
      <n-form-item label="抖音号 / 链接" required>
        <n-input
          v-model:value="profileInput"
          placeholder="抖音号、主页链接或分享链接"
          data-testid="talent-profile-input"
        />
      </n-form-item>
      <n-form-item label=" ">
        <n-space>
          <n-button
            type="primary"
            :loading="resolving"
            data-testid="talent-resolve-profile"
            @click="fetchRealProfile(false)"
          >
            获取真实数据
          </n-button>
          <n-button v-if="resolveResult" :loading="resolving" @click="fetchRealProfile(true)">强制刷新</n-button>
        </n-space>
      </n-form-item>

      <n-card v-if="resolveResult" size="small" title="真实资料结果" data-testid="talent-profile-result">
        <n-descriptions :column="2" size="small">
          <n-descriptions-item label="昵称">{{ displayValue(resolveResult.profile?.nickname) }}</n-descriptions-item>
          <n-descriptions-item label="头像">
            <n-avatar v-if="resolveResult.profile?.avatarUrl" :src="resolveResult.profile?.avatarUrl" round size="small" />
            <span v-else>-</span>
          </n-descriptions-item>
          <n-descriptions-item label="粉丝数">{{ displayNumber(resolveResult.profile?.fansCount) }}</n-descriptions-item>
          <n-descriptions-item label="获赞数">{{ displayNumber(resolveResult.profile?.likeCount) }}</n-descriptions-item>
          <n-descriptions-item label="关注数">{{ displayNumber(resolveResult.profile?.followingCount) }}</n-descriptions-item>
          <n-descriptions-item label="作品数">{{ displayNumber(resolveResult.profile?.worksCount) }}</n-descriptions-item>
          <n-descriptions-item label="IP属地">{{ displayValue(resolveResult.profile?.ipLocation) }}</n-descriptions-item>
          <n-descriptions-item label="达人等级">{{ displayOptional(resolveResult.profile?.talentLevel) }}</n-descriptions-item>
          <n-descriptions-item label="近30天销售额">{{ displayOptionalNumber(resolveResult.profile?.sales30d) }}</n-descriptions-item>
          <n-descriptions-item label="数据来源">{{ displayValue(resolveResult.dataSource || resolveResult.provider) }}</n-descriptions-item>
          <n-descriptions-item label="同步状态">{{ displayValue(resolveResult.syncStatus) }}</n-descriptions-item>
          <n-descriptions-item label="未支持字段" :span="2">
            <n-space v-if="resolveResult.unsupportedFields?.length" :size="6">
              <n-tag v-for="field in resolveResult.unsupportedFields" :key="field" size="small" type="warning">{{ field }}</n-tag>
            </n-space>
            <span v-else>-</span>
          </n-descriptions-item>
          <n-descriptions-item v-if="resolveResult.syncErrorMessage" label="失败原因" :span="2">
            <n-text type="error">{{ resolveResult.syncErrorMessage }}</n-text>
          </n-descriptions-item>
        </n-descriptions>
      </n-card>

      <n-divider v-if="allowManualFill" title-placement="left">人工补充（data_source=manual）</n-divider>
      <template v-if="allowManualFill">
        <n-alert type="warning" :show-icon="false" style="margin-bottom: 12px">
          自动采集失败或未返回完整字段时，可人工补充；保存后数据来源将标记为 manual，不会伪造真实平台数据。
        </n-alert>
        <n-form-item label="达人昵称">
          <n-input v-model:value="manualForm.nickname" placeholder="人工填写昵称" />
        </n-form-item>
        <n-form-item label="粉丝数">
          <n-input-number v-model:value="manualForm.fansCount" :min="0" style="width: 100%" />
        </n-form-item>
        <n-form-item label="获赞数">
          <n-input-number v-model:value="manualForm.likeCount" :min="0" style="width: 100%" />
        </n-form-item>
        <n-form-item label="关注数">
          <n-input-number v-model:value="manualForm.followingCount" :min="0" style="width: 100%" />
        </n-form-item>
        <n-form-item label="作品数">
          <n-input-number v-model:value="manualForm.worksCount" :min="0" style="width: 100%" />
        </n-form-item>
        <n-form-item label="IP属地">
          <n-input v-model:value="manualForm.ipLocation" placeholder="如：北京" />
        </n-form-item>
        <n-form-item label=" ">
          <n-button :loading="resolving" @click="applyManualProfile">应用人工补充</n-button>
        </n-form-item>
      </template>

      <n-divider title-placement="left">保存信息</n-divider>
      <n-form-item label="联系方式">
        <n-input v-model:value="formData.contactPhone" placeholder="手机号、微信或其他联系方式" />
      </n-form-item>
      <n-form-item label="备注">
        <n-input v-model:value="formData.intro" type="textarea" placeholder="补充合作背景或达人说明" />
      </n-form-item>
    </n-form>

    <template #footer>
      <n-space justify="end">
        <n-button @click="closeModal">取消</n-button>
        <n-button type="primary" :loading="submitting" data-testid="talent-save" @click="submit">保存为达人</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import {
  createTalent,
  resolveTalentProfile,
  type ResolveTalentProfileResponse
} from '../../../api/talent'

const props = defineProps<{ show: boolean }>()
const emit = defineEmits<{ 'update:show': [value: boolean]; success: [] }>()
const message = useMessage()
const submitting = ref(false)
const resolving = ref(false)
const profileInput = ref('')
const resolveResult = ref<ResolveTalentProfileResponse | null>(null)

const formData = reactive({
  contactPhone: '',
  intro: ''
})

const manualForm = reactive({
  nickname: '',
  fansCount: null as number | null,
  likeCount: null as number | null,
  followingCount: null as number | null,
  worksCount: null as number | null,
  ipLocation: ''
})

const allowManualFill = computed(() => {
  if (!resolveResult.value) return false
  return !resolveResult.value.success || resolveResult.value.dataSource === 'manual'
})

function resetForm() {
  profileInput.value = ''
  resolveResult.value = null
  formData.contactPhone = ''
  formData.intro = ''
  manualForm.nickname = ''
  manualForm.fansCount = null
  manualForm.likeCount = null
  manualForm.followingCount = null
  manualForm.worksCount = null
  manualForm.ipLocation = ''
}

function closeModal() {
  emit('update:show', false)
}

function displayValue(value?: string | null) {
  return value && String(value).trim() ? value : '-'
}

function displayNumber(value?: number | null) {
  return value === null || value === undefined ? '-' : value.toLocaleString('zh-CN')
}

function displayOptional(value?: string | null) {
  if (value === null || value === undefined || value === '') return 'unsupported'
  return value
}

function displayOptionalNumber(value?: number | null) {
  if (value === null || value === undefined) return 'unsupported'
  return value.toLocaleString('zh-CN')
}

async function fetchRealProfile(forceRefresh: boolean) {
  if (!profileInput.value.trim()) {
    message.warning('请先输入抖音号或主页链接')
    return
  }
  resolving.value = true
  try {
    resolveResult.value = await resolveTalentProfile({
      input: profileInput.value.trim(),
      forceRefresh
    })
    if (resolveResult.value.success) {
      message.success('真实资料获取成功')
      applyResolvedToManual(resolveResult.value)
    } else {
      message.warning(resolveResult.value.syncErrorMessage || '真实资料获取失败，可人工补充')
    }
  } catch (error: any) {
    message.error(error?.message || '获取真实资料失败')
  } finally {
    resolving.value = false
  }
}

function applyResolvedToManual(result: ResolveTalentProfileResponse) {
  const profile = result.profile
  if (!profile) return
  manualForm.nickname = profile.nickname || ''
  manualForm.fansCount = profile.fansCount ?? null
  manualForm.likeCount = profile.likeCount ?? null
  manualForm.followingCount = profile.followingCount ?? null
  manualForm.worksCount = profile.worksCount ?? null
  manualForm.ipLocation = profile.ipLocation || ''
}

async function applyManualProfile() {
  if (!profileInput.value.trim()) {
    message.warning('请先输入抖音号或主页链接')
    return
  }
  if (!manualForm.nickname.trim()) {
    message.warning('人工补充至少填写昵称')
    return
  }
  resolving.value = true
  try {
    resolveResult.value = await resolveTalentProfile({
      input: profileInput.value.trim(),
      manualFill: true,
      manualPayload: {
        nickname: manualForm.nickname.trim(),
        fansCount: manualForm.fansCount,
        likeCount: manualForm.likeCount,
        followingCount: manualForm.followingCount,
        worksCount: manualForm.worksCount,
        ipLocation: manualForm.ipLocation.trim() || undefined,
        douyinAccount: profileInput.value.trim()
      }
    })
    message.success('人工补充已应用（data_source=manual）')
  } catch (error: any) {
    message.error(error?.message || '人工补充失败')
  } finally {
    resolving.value = false
  }
}

function buildCreatePayload() {
  const profile = resolveResult.value?.profile
  const input = profileInput.value.trim()
  return {
    douyinNo: profile?.douyinAccount || input,
    douyinUid: profile?.talentUid || profile?.secUid || input,
    uid: profile?.talentUid || undefined,
    secUid: profile?.secUid || undefined,
    profileUrl: input.startsWith('http') ? input : undefined,
    nickname: profile?.nickname || manualForm.nickname.trim() || undefined,
    avatarUrl: profile?.avatarUrl || undefined,
    fans: profile?.fansCount ?? manualForm.fansCount ?? undefined,
    likesCount: profile?.likeCount ?? manualForm.likeCount ?? undefined,
    followingCount: profile?.followingCount ?? manualForm.followingCount ?? undefined,
    worksCount: profile?.worksCount ?? manualForm.worksCount ?? undefined,
    ipLocation: profile?.ipLocation || manualForm.ipLocation.trim() || undefined,
    talentLevel: profile?.talentLevel ?? undefined,
    sales30d: profile?.sales30d ?? undefined,
    douyinAccount: profile?.douyinAccount || input,
    talentUid: profile?.talentUid || undefined,
    dataSource: resolveResult.value?.dataSource || resolveResult.value?.provider || undefined,
    syncStatus: resolveResult.value?.syncStatus || undefined,
    unsupportedFields: resolveResult.value?.unsupportedFields || undefined,
    contactPhone: formData.contactPhone.trim() || undefined,
    intro: formData.intro.trim() || undefined
  }
}

async function submit() {
  if (!profileInput.value.trim() && !manualForm.nickname.trim()) {
    message.warning('请至少填写抖音号/链接或完成人工补充')
    return
  }
  submitting.value = true
  try {
    await createTalent(buildCreatePayload())
    message.success('新增达人成功')
    emit('success')
    closeModal()
  } catch (error: any) {
    message.error(error?.message || '新增达人失败')
  } finally {
    submitting.value = false
  }
}

watch(
  () => props.show,
  (show) => {
    if (show) resetForm()
  }
)
</script>
