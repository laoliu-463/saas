<template>
  <n-modal :show="show" preset="card" title="新增达人" style="width: 620px" @update:show="closeModal">
    <n-form :model="formData" label-placement="left" label-width="110">
      <n-form-item label="达人昵称">
        <n-input v-model:value="formData.nickname" placeholder="请输入达人昵称" />
      </n-form-item>
      <n-form-item label="抖音号">
        <n-input v-model:value="formData.douyinNo" placeholder="请输入抖音号" />
      </n-form-item>
      <n-form-item label="主页链接">
        <n-input v-model:value="formData.profileUrl" placeholder="https://www.douyin.com/user/xxx" />
      </n-form-item>
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
        <n-button type="primary" :loading="submitting" @click="submit">保存</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { createTalent } from '../../../api/talent'

const props = defineProps<{ show: boolean }>()
const emit = defineEmits<{ 'update:show': [value: boolean]; success: [] }>()
const message = useMessage()
const submitting = ref(false)

const formData = reactive({
  nickname: '',
  douyinNo: '',
  profileUrl: '',
  contactPhone: '',
  intro: ''
})

function resetForm() {
  formData.nickname = ''
  formData.douyinNo = ''
  formData.profileUrl = ''
  formData.contactPhone = ''
  formData.intro = ''
}

function closeModal() {
  emit('update:show', false)
}

async function submit() {
  if (!formData.douyinNo.trim() && !formData.profileUrl.trim()) {
    message.warning('请至少填写抖音号或主页链接')
    return
  }
  submitting.value = true
  try {
    await createTalent({
      nickname: formData.nickname.trim() || undefined,
      douyinNo: formData.douyinNo.trim() || undefined,
      profileUrl: formData.profileUrl.trim() || undefined,
      contactPhone: formData.contactPhone.trim() || undefined,
      intro: formData.intro.trim() || undefined
    })
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
