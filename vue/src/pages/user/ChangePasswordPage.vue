<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { User, Edit, Mail, Lock } from 'lucide-vue-next'
import { authApi } from '@/api'
import { useMessages } from '@/composables/useMessages'

const router = useRouter()
const { addMessage } = useMessages()

const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const loading = ref(false)

async function handleSubmit() {
  if (!oldPassword.value || !newPassword.value) { addMessage('请填写完整信息', 'error'); return }
  if (newPassword.value !== confirmPassword.value) { addMessage('两次输入的新密码不一致', 'error'); return }
  loading.value = true
  try {
    const res = await authApi.changePassword(oldPassword.value, newPassword.value, confirmPassword.value)
    if (res.ok) { addMessage('密码修改成功', 'success'); router.push({ name: 'profile' }) }
    else { addMessage('修改失败', 'error') }
  } catch (e) { addMessage('修改失败', 'error') }
  finally { loading.value = false }
}
</script>

<template>
  <div class="grid md:grid-cols-4 gap-6">
    <div class="bg-white rounded-xl shadow-sm border border-zinc-100 h-fit">
      <div class="bg-emerald-700 text-white px-4 py-3 rounded-t-xl"><h5 class="font-semibold text-sm">用户中心</h5></div>
      <div class="divide-y">
        <router-link to="/user/profile" class="flex items-center gap-2 px-4 py-2.5 text-sm hover:bg-zinc-50 transition-colors"><User :size="14" /> 个人信息</router-link>
        <router-link to="/user/profile/edit" class="flex items-center gap-2 px-4 py-2.5 text-sm hover:bg-zinc-50 transition-colors"><Edit :size="14" /> 编辑资料</router-link>
        <router-link to="/user/profile/change-email" class="flex items-center gap-2 px-4 py-2.5 text-sm hover:bg-zinc-50 transition-colors"><Mail :size="14" /> 修改邮箱</router-link>
        <router-link to="/user/profile/change-password" class="flex items-center gap-2 px-4 py-2.5 text-sm bg-red-50 text-red-700 font-medium"><Lock :size="14" /> 修改密码</router-link>
      </div>
    </div>
    <div class="md:col-span-3">
      <div class="bg-white rounded-xl shadow-sm border border-zinc-100">
        <div class="bg-red-600 text-white px-6 py-4 rounded-t-xl"><h5 class="font-semibold">修改密码</h5></div>
        <div class="p-6">
          <div class="bg-amber-50 border border-amber-200 text-amber-800 px-4 py-3 rounded-md mb-4 text-sm">
            为了您的账户安全，修改密码需要验证当前密码。
          </div>
          <form @submit.prevent="handleSubmit">
            <div class="mb-4"><label class="block text-sm font-medium mb-1.5">当前密码</label><input v-model="oldPassword" type="password" required class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-red-500" /></div>
            <div class="mb-4"><label class="block text-sm font-medium mb-1.5">新密码</label><input v-model="newPassword" type="password" required class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-red-500" /></div>
            <div class="mb-6"><label class="block text-sm font-medium mb-1.5">确认新密码</label><input v-model="confirmPassword" type="password" required class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-red-500" /><p v-if="confirmPassword && newPassword !== confirmPassword" class="text-xs text-red-500 mt-1">密码不匹配</p><p v-else-if="confirmPassword && newPassword === confirmPassword" class="text-xs text-emerald-600 mt-1">密码匹配</p></div>
            <div class="flex justify-end gap-3">
              <router-link to="/user/profile" class="bg-zinc-200 text-zinc-700 px-4 py-2 rounded-md hover:bg-zinc-300 transition-colors text-sm">取消</router-link>
              <button type="submit" :disabled="loading" class="bg-red-600 text-white px-4 py-2 rounded-md hover:bg-red-500 transition-colors text-sm disabled:opacity-50">{{ loading ? '修改中...' : '修改密码' }}</button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </div>
</template>
