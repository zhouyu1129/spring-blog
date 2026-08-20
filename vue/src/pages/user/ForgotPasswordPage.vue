<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { KeyRound } from 'lucide-vue-next'
import { authApi } from '@/api'
import { useMessages } from '@/composables/useMessages'

const router = useRouter()
const { addMessage } = useMessages()
const email = ref('')
const studentNumber = ref('')
const loading = ref(false)

async function handleSubmit() {
  if (!email.value || !studentNumber.value) { addMessage('请填写邮箱和学号', 'error'); return }
  loading.value = true
  try {
    const res = await authApi.forgotPassword(email.value, studentNumber.value)
    if (res.ok) {
      addMessage('密码重置链接已发送到您的邮箱，请查收', 'success')
      router.push({ name: 'login' })
    } else { addMessage('邮箱和学号不匹配', 'error') }
  } catch (e) { addMessage('发送失败', 'error') }
  finally { loading.value = false }
}
</script>

<template>
  <div class="max-w-md mx-auto">
    <div class="bg-white rounded-xl shadow-md border border-zinc-100">
      <div class="bg-amber-600 text-white px-6 py-4 rounded-t-xl text-center">
        <h3 class="text-xl font-bold flex items-center justify-center gap-2"><KeyRound :size="20" /> 忘记密码</h3>
      </div>
      <div class="p-6">
        <div class="bg-amber-50 border border-amber-200 text-amber-800 px-4 py-3 rounded-md mb-4 text-sm">
          请输入您的邮箱和学号，我们将发送密码重置链接到您的邮箱。
        </div>
        <form @submit.prevent="handleSubmit">
          <div class="mb-4">
            <label class="block text-sm font-medium mb-1.5">邮箱 <span class="text-red-500">*</span></label>
            <input v-model="email" type="email" required class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-amber-500" />
          </div>
          <div class="mb-4">
            <label class="block text-sm font-medium mb-1.5">学号 <span class="text-red-500">*</span></label>
            <input v-model="studentNumber" type="text" required class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-amber-500" />
          </div>
          <button type="submit" :disabled="loading" class="w-full bg-amber-600 text-white py-2.5 rounded-md hover:bg-amber-500 transition-colors disabled:opacity-50">
            {{ loading ? '发送中...' : '发送重置链接' }}
          </button>
        </form>
        <div class="text-center mt-4 text-sm">
          <p>想起密码了？<router-link to="/user/login" class="text-emerald-600 hover:underline">返回登录</router-link></p>
        </div>
      </div>
    </div>
  </div>
</template>
