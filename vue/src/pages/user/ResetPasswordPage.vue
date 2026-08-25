<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { KeyRound } from 'lucide-vue-next'
import { authApi } from '@/api'
import { useMessages } from '@/composables/useMessages'

const route = useRoute()
const router = useRouter()
const { addMessage } = useMessages()
const token = String(route.query.token || '')
const newPassword = ref('')
const confirmPassword = ref('')
const loading = ref(false)

async function handleSubmit() {
  if (!token) { addMessage('重置链接无效，请重新发送重置邮件', 'error'); return }
  if (!newPassword.value) { addMessage('请输入新密码', 'error'); return }
  if (newPassword.value !== confirmPassword.value) { addMessage('两次输入的新密码不一致', 'error'); return }
  if (newPassword.value.length < 6 || newPassword.value.length > 128) { addMessage('密码长度需为6-128位', 'error'); return }
  loading.value = true
  try {
    const res = await authApi.resetPassword(token, newPassword.value)
    if (res.ok) {
      addMessage('密码重置成功，请使用新密码登录', 'success')
      router.push({ name: 'login' })
    } else {
      addMessage(res.data?.message || '重置令牌无效或已过期', 'error')
    }
  } catch (e) { addMessage('重置失败', 'error') }
  finally { loading.value = false }
}
</script>

<template>
  <div class="max-w-md mx-auto">
    <div class="bg-white rounded-xl shadow-md border border-zinc-100">
      <div class="bg-amber-600 text-white px-6 py-4 rounded-t-xl text-center">
        <h3 class="text-xl font-bold flex items-center justify-center gap-2"><KeyRound :size="20" /> 重置密码</h3>
      </div>
      <div class="p-6">
        <div v-if="!token" class="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md mb-4 text-sm">
          重置链接无效或已过期，请重新<a href="/user/forgot-password" class="underline">发送重置邮件</a>。
        </div>
        <div v-else class="bg-amber-50 border border-amber-200 text-amber-800 px-4 py-3 rounded-md mb-4 text-sm">
          请为您重置密码的账号设置新密码（6-128位）。
        </div>
        <form @submit.prevent="handleSubmit">
          <div class="mb-4">
            <label class="block text-sm font-medium mb-1.5">新密码 <span class="text-red-500">*</span></label>
            <input v-model="newPassword" type="password" required class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-amber-500" />
          </div>
          <div class="mb-4">
            <label class="block text-sm font-medium mb-1.5">确认新密码 <span class="text-red-500">*</span></label>
            <input v-model="confirmPassword" type="password" required class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-amber-500" />
          </div>
          <button type="submit" :disabled="loading || !token" class="w-full bg-amber-600 text-white py-2.5 rounded-md hover:bg-amber-500 transition-colors disabled:opacity-50">
            {{ loading ? '提交中...' : '重置密码' }}
          </button>
        </form>
        <div class="text-center mt-4 text-sm">
          <p>想起密码了？<router-link to="/user/login" class="text-emerald-600 hover:underline">返回登录</router-link></p>
        </div>
      </div>
    </div>
  </div>
</template>
