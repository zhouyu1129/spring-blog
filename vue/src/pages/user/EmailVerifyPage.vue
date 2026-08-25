<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Mail } from 'lucide-vue-next'
import { authApi } from '@/api'
import { useAuth } from '@/composables/useAuth'
import { useMessages } from '@/composables/useMessages'

const router = useRouter()
const route = useRoute()
const { user } = useAuth()
const { addMessage } = useMessages()

const isRedirected = computed(() => route.query.reason === 'unverified')

const COOLDOWN_MS = 60_000
const COOLDOWN_KEY = 'email_verify_cooldown'

const countdown = ref(0)
const sending = ref(false)
let timer: ReturnType<typeof setInterval> | null = null

function getRemainingSeconds(): number {
  const ts = localStorage.getItem(COOLDOWN_KEY)
  if (!ts) return 0
  const remaining = Math.ceil((Number(ts) + COOLDOWN_MS - Date.now()) / 1000)
  return remaining > 0 ? remaining : 0
}

function startCountdown() {
  localStorage.setItem(COOLDOWN_KEY, String(Date.now()))
  countdown.value = 60
  runTimer()
}

function resumeCountdown() {
  const remaining = getRemainingSeconds()
  if (remaining <= 0) {
    countdown.value = 0
    return
  }
  countdown.value = remaining
  runTimer()
}

function runTimer() {
  if (timer) clearInterval(timer)
  timer = setInterval(() => {
    countdown.value = getRemainingSeconds()
    if (countdown.value <= 0) {
      if (timer) clearInterval(timer)
      timer = null
      localStorage.removeItem(COOLDOWN_KEY)
    }
  }, 1000)
}

async function handleResend() {
  if (countdown.value > 0 || sending.value) return
  sending.value = true
  try {
    const res = await authApi.resendVerification()
    if (res.ok) {
      addMessage('验证邮件已重新发送', 'success')
      startCountdown()
    } else {
      addMessage(res.data?.message || '发送失败', 'error')
    }
  } catch (e) {
    console.error('重发验证邮件失败:', e)
    addMessage('发送失败，请检查网络后重试', 'error')
  } finally {
    sending.value = false
  }
}

function handleLogout() {
  localStorage.removeItem('isAuthenticated')
  localStorage.removeItem('user')
  localStorage.removeItem(COOLDOWN_KEY)
  authApi.logout()
  router.push({ name: 'login' })
}

onMounted(() => {
  resumeCountdown()
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="max-w-md mx-auto">
    <div class="bg-white rounded-xl shadow-md border border-zinc-100">
      <div class="bg-emerald-700 text-white px-6 py-4 rounded-t-xl text-center">
        <div class="flex justify-center mb-2">
          <Mail :size="32" />
        </div>
        <h3 class="text-xl font-bold">验证您的邮箱</h3>
      </div>
      <div class="p-6 text-center">
        <div v-if="isRedirected" class="bg-amber-50 border border-amber-200 text-amber-700 text-sm rounded-lg px-4 py-3 mb-4">
          您需要验证邮箱后才能继续使用网站功能。
        </div>
        <p class="text-sm text-zinc-600 mb-2">
          我们已向 <strong class="text-zinc-900">{{ user?.email }}</strong> 发送了一封验证邮件。
        </p>
        <p class="text-sm text-zinc-500 mb-6">
          请查收邮件并点击验证链接完成邮箱验证。
        </p>

        <button
          @click="handleResend"
          :disabled="countdown > 0 || sending"
          class="w-full bg-emerald-600 text-white py-2.5 rounded-md hover:bg-emerald-500 transition-colors disabled:opacity-50 disabled:cursor-not-allowed mb-3"
        >
          {{ sending ? '发送中...' : countdown > 0 ? `重新发送 (${countdown}s)` : '重新发送验证邮件' }}
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
