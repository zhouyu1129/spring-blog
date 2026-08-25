<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CheckCircle2, XCircle } from 'lucide-vue-next'
import { useAuth } from '@/composables/useAuth'
import { authApi } from '@/api'

const route = useRoute()
const router = useRouter()
const { user, setUser } = useAuth()

const isSuccess = computed(() => route.query.status === 'success')

onMounted(() => {
  if (isSuccess.value) {
    // 优先从 localStorage 读取已有用户信息并更新 email_verified
    const raw = localStorage.getItem('user')
    if (raw) {
      try {
        const stored = JSON.parse(raw)
        stored.email_verified = true
        localStorage.setItem('user', JSON.stringify(stored))
        setUser(stored)
      } catch { /* ignore */ }
    } else {
      // 新标签页打开，没有本地缓存，尝试从后端获取最新 profile
      authApi.getProfile().then(res => {
        if (res.ok && res.data.user) {
          localStorage.setItem('user', JSON.stringify(res.data.user))
          setUser(res.data.user)
        }
      }).catch(() => {})
    }
  }
})

function handleLogout() {
  localStorage.removeItem('isAuthenticated')
  localStorage.removeItem('user')
  authApi.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="max-w-md mx-auto">
    <div class="bg-white rounded-xl shadow-md border border-zinc-100">
      <div v-if="isSuccess" class="p-8 text-center">
        <div class="flex justify-center mb-4">
          <CheckCircle2 :size="56" class="text-emerald-500" />
        </div>
        <h3 class="text-xl font-bold text-zinc-900 mb-2">邮箱验证成功</h3>
        <p class="text-sm text-zinc-500 mb-6">您的邮箱已成功验证，现在可以正常使用所有功能。</p>
        <button
          @click="router.push('/')"
          class="w-full bg-emerald-600 text-white py-2.5 rounded-md hover:bg-emerald-500 transition-colors mb-3"
        >
          进入首页
        </button>
        <button
          @click="handleLogout"
          class="text-sm text-zinc-400 hover:text-zinc-600 transition-colors"
        >
          退出登录
        </button>
      </div>
      <div v-else class="p-8 text-center">
        <div class="flex justify-center mb-4">
          <XCircle :size="56" class="text-red-500" />
        </div>
        <h3 class="text-xl font-bold text-zinc-900 mb-2">验证链接无效或已过期</h3>
        <p class="text-sm text-zinc-500 mb-6">该验证链接已失效，请重新发送验证邮件。</p>
        <button
          @click="router.push({ name: 'verify-email' })"
          class="w-full bg-emerald-600 text-white py-2.5 rounded-md hover:bg-emerald-500 transition-colors mb-3"
        >
          重新验证
        </button>
        <button
          @click="handleLogout"
          class="text-sm text-zinc-400 hover:text-zinc-600 transition-colors"
        >
          退出登录
        </button>
      </div>
    </div>
  </div>
</template>
