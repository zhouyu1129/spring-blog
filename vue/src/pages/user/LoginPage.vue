<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { LogIn } from 'lucide-vue-next'
import { authApi } from '@/api'
import { useAuth } from '@/composables/useAuth'
import { useMessages } from '@/composables/useMessages'

const router = useRouter()
const route = useRoute()
const { setUser } = useAuth()
const { addMessage } = useMessages()

const email = ref('')
const password = ref('')
const loading = ref(false)

async function handleSubmit() {
  if (!email.value || !password.value) { addMessage('请输入邮箱/学号/用户名和密码', 'error'); return }
  loading.value = true
  try {
    const res = await authApi.login(email.value, password.value)
    if (res.ok) {
      localStorage.setItem('isAuthenticated', 'true')
      if (res.data.user) {
        localStorage.setItem('user', JSON.stringify(res.data.user))
        setUser(res.data.user)
      }
      addMessage('登录成功！', 'success')
      // 邮箱未验证 → 跳转验证页
      if (res.data.user && !res.data.user.email_verified) {
        router.push({ name: 'verify-email' })
      } else {
        const next = (route.query.next as string) || '/'
        router.push(next)
      }
    } else {
      addMessage(res.data?.message || '登录失败', 'error')
    }
  } catch (e) {
    addMessage('登录失败，请重试', 'error')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="max-w-md mx-auto">
    <div class="bg-white rounded-xl shadow-md border border-zinc-100">
      <div class="bg-emerald-700 text-white px-6 py-4 rounded-t-xl text-center">
        <h3 class="text-xl font-bold">用户登录</h3>
      </div>
      <div class="p-6">
        <form @submit.prevent="handleSubmit">
          <div class="mb-4">
            <label class="block text-sm font-medium mb-1.5">邮箱/用户名</label>
            <input v-model="email" type="text" required class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-emerald-500" />
            <p class="text-xs text-zinc-400 mt-1">请输入您的邮箱或用户名</p>
          </div>
          <div class="mb-4">
            <label class="block text-sm font-medium mb-1.5">密码</label>
            <input v-model="password" type="password" required class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-emerald-500" />
          </div>
          <button type="submit" :disabled="loading" class="w-full bg-emerald-600 text-white py-2.5 rounded-md hover:bg-emerald-500 transition-colors disabled:opacity-50">
            {{ loading ? '登录中...' : '登录' }}
          </button>
        </form>
        <div class="text-center mt-4 space-y-1 text-sm">
          <p>还没有账号？<router-link to="/user/register" class="text-emerald-600 hover:underline">立即注册</router-link></p>
          <p><router-link to="/user/forgot-password" class="text-zinc-500 hover:underline">忘记密码？</router-link></p>
        </div>
      </div>
    </div>
  </div>
</template>
