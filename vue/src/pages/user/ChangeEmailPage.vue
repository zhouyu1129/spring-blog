<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { User, Edit, Mail, Lock } from 'lucide-vue-next'
import { authApi } from '@/api'
import { useAuth } from '@/composables/useAuth'
import { useMessages } from '@/composables/useMessages'

const router = useRouter()
const { user } = useAuth()
const { addMessage } = useMessages()

const newEmail = ref('')
const verificationCode = ref('')
const countdown = ref(0)
const loading = ref(false)

let timer: ReturnType<typeof setInterval> | null = null

async function sendCode() {
  if (!newEmail.value) { addMessage('请先输入新邮箱地址', 'error'); return }
  try {
    const res = await authApi.sendEmailCode()
    if (res.data?.status === 'success') {
      addMessage('验证码已发送，请查收邮件', 'success')
      countdown.value = 60
      timer = setInterval(() => {
        countdown.value--
        if (countdown.value <= 0 && timer) { clearInterval(timer); timer = null }
      }, 1000)
    } else { addMessage(res.data?.message || '发送失败', 'error') }
  } catch (e) { addMessage('发送失败', 'error') }
}

async function handleSubmit() {
  if (!newEmail.value || !verificationCode.value) { addMessage('请填写完整信息', 'error'); return }
  loading.value = true
  try {
    const res = await authApi.changeEmail(newEmail.value, verificationCode.value)
    if (res.ok) { addMessage('邮箱修改成功，请查收验证邮件', 'success'); router.push({ name: 'profile' }) }
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
        <router-link to="/user/profile/change-email" class="flex items-center gap-2 px-4 py-2.5 text-sm bg-sky-50 text-sky-700 font-medium"><Mail :size="14" /> 修改邮箱</router-link>
        <router-link to="/user/profile/change-password" class="flex items-center gap-2 px-4 py-2.5 text-sm hover:bg-zinc-50 transition-colors"><Lock :size="14" /> 修改密码</router-link>
      </div>
    </div>
    <div class="md:col-span-3">
      <div class="bg-white rounded-xl shadow-sm border border-zinc-100">
        <div class="bg-sky-600 text-white px-6 py-4 rounded-t-xl"><h5 class="font-semibold">修改邮箱</h5></div>
        <div class="p-6">
          <div class="bg-sky-50 border border-sky-200 text-sky-800 px-4 py-3 rounded-md mb-4 text-sm">
            修改邮箱需要验证当前邮箱，我们将向您的当前邮箱 <strong>{{ user?.email }}</strong> 发送验证码。
          </div>
          <form @submit.prevent="handleSubmit">
            <div class="mb-4"><label class="block text-sm font-medium mb-1.5">新邮箱地址</label><input v-model="newEmail" type="email" required class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-sky-500" /></div>
            <div class="mb-6"><label class="block text-sm font-medium mb-1.5">验证码</label><div class="flex gap-2"><input v-model="verificationCode" type="text" maxlength="4" placeholder="请输入4位验证码" required class="flex-1 border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-sky-500" /><button type="button" @click="sendCode" :disabled="countdown > 0" class="bg-sky-600 text-white px-4 py-2.5 rounded-md text-sm hover:bg-sky-500 transition-colors disabled:opacity-50 whitespace-nowrap">{{ countdown > 0 ? `${countdown}秒后重试` : '发送验证码' }}</button></div><p class="text-xs text-zinc-400 mt-1">验证码5分钟内有效</p></div>
            <div class="flex justify-end gap-3">
              <router-link to="/user/profile" class="bg-zinc-200 text-zinc-700 px-4 py-2 rounded-md hover:bg-zinc-300 transition-colors text-sm">取消</router-link>
              <button type="submit" :disabled="loading" class="bg-sky-600 text-white px-4 py-2 rounded-md hover:bg-sky-500 transition-colors text-sm disabled:opacity-50">{{ loading ? '修改中...' : '确认修改' }}</button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </div>
</template>
