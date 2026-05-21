<template>
  <n-modal :show="show" preset="card" title="手动新增达人" :style="{ width: MODAL_WIDTH.md }" @update:show="closeModal">
    <n-alert type="info" :show-icon="false" style="margin-bottom: 16px">
      先录入基础资料即可保存；平台粉丝、等级等数据可在达人详情中后续同步。
    </n-alert>
    <n-form :model="formData" label-placement="left" label-width="96">
      <n-form-item label="抖音号" required>
        <n-input
          v-model:value="formData.douyinNo"
          placeholder="抖音号、UID 或主页链接"
          data-testid="talent-profile-input"
        />
      </n-form-item>
      <n-form-item label="昵称" required>
        <n-input v-model:value="formData.nickname" placeholder="达人昵称" data-testid="talent-nickname-input" />
      </n-form-item>
      <n-form-item label="联系方式">
        <n-input
          v-model:value="formData.contactPhone"
          placeholder="手机号、微信等（选填）"
          data-testid="talent-contact-input"
        />
      </n-form-item>
      <n-form-item label="备注">
        <n-input
          v-model:value="formData.intro"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 4 }"
          placeholder="合作背景或补充说明（选填）"
          data-testid="talent-intro-input"
        />
      </n-form-item>
    </n-form>

    <template #footer>
      <n-space justify="end">
        <n-button @click="closeModal">取消</n-button>
        <n-button type="primary" :loading="submitting" data-testid="talent-save" @click="submit">保存</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { MODAL_WIDTH } from '../../../constants/ui'
import { createTalent } from '../../../api/talent'

const props = defineProps<{ show: boolean }>()
const emit = defineEmits<{ 'update:show': [value: boolean]; success: [] }>()
const message = useMessage()
const submitting = ref(false)

const formData = reactive({
  douyinNo: '',
  nickname: '',
  contactPhone: '',
  intro: ''
})

function resetForm() {
  formData.douyinNo = ''
  formData.nickname = ''
  formData.contactPhone = ''
  formData.intro = ''
}

function closeModal() {
  emit('update:show', false)
}

function buildCreatePayload() {
  const input = formData.douyinNo.trim()
  const profileUrl = input.startsWith('http') ? input : undefined
  return {
    douyinNo: input,
    profileUrl,
    nickname: formData.nickname.trim(),
    douyinAccount: input,
    contactPhone: formData.contactPhone.trim() || undefined,
    intro: formData.intro.trim() || undefined,
    dataSource: 'manual',
    syncStatus: 'success',
    unsupportedFields: ['talentLevel', 'sales30d']
  }
}

async function submit() {
  if (!formData.douyinNo.trim()) {
    message.warning('请填写抖音号或主页链接')
    return
  }
  if (!formData.nickname.trim()) {
    message.warning('请填写达人昵称')
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
