<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { UserPlus } from 'lucide-vue-next'
import { authApi } from '@/api'
import { useAuth } from '@/composables/useAuth'
import { useMessages } from '@/composables/useMessages'

const router = useRouter()
const { setUser } = useAuth()
const { addMessage } = useMessages()

const username = ref('')
const email = ref('')
const studentNumber = ref('')
const password = ref('')
const confirmPassword = ref('')
const loading = ref(false)

async function handleSubmit() {
  if (!username.value || !email.value || !studentNumber.value || !password.value) {
    addMessage('请填写所有必填字段', 'error'); return
  }
  if (password.value !== confirmPassword.value) {
    addMessage('两次输入的密码不一致', 'error'); return
  }
  if (!/^\d{10}$/.test(studentNumber.value)) {
    addMessage('学号必须是10位数字', 'error'); return
  }
  loading.value = true
  try {
    const res = await authApi.register({
      username: username.value,
      email: email.value,
      student_number: studentNumber.value,
      password: password.value,
      confirm_password: confirmPassword.value,
    })
    if (res.ok) {
      addMessage('注册成功！请查收验证邮件', 'success')
      // 自动登录并跳转到邮箱验证页面
      const loginRes = await authApi.login(email.value, password.value)
      if (loginRes.ok) {
        localStorage.setItem('isAuthenticated', 'true')
        if (loginRes.data.user) {
          localStorage.setItem('user', JSON.stringify(loginRes.data.user))
          setUser(loginRes.data.user)
        }
        router.push({ name: 'verify-email' })
      } else {
        router.push({ name: 'login' })
      }
    } else {
      addMessage(res.data?.message || '注册失败', 'error')
    }
  } catch (e) {
    addMessage('注册失败，请重试', 'error')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="max-w-md mx-auto">
    <div class="bg-white rounded-xl shadow-md border border-zinc-100">
      <div class="bg-emerald-700 text-white px-6 py-4 rounded-t-xl text-center">
        <h3 class="text-xl font-bold">用户注册</h3>
      </div>
      <div class="p-6">
        <form @submit.prevent="handleSubmit">
          <div class="mb-4">
            <label class="block text-sm font-medium mb-1.5">用户名 <span class="text-red-500">*</span></label>
            <input v-model="username" type="text" required class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-emerald-500" />
            <p class="text-xs text-zinc-400 mt-1">只能包含ASCII字符且不能包含空格和@符号</p>
          </div>
          <div class="mb-4">
            <label class="block text-sm font-medium mb-1.5">邮箱 <span class="text-red-500">*</span></label>
            <input v-model="email" type="email" required class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-emerald-500" />
          </div>
          <div class="mb-4">
            <label class="block text-sm font-medium mb-1.5">学号 <span class="text-red-500">*</span></label>
            <input v-model="studentNumber" type="text" required class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-emerald-500" />
            <p class="text-xs text-zinc-400 mt-1">学号必须为10位数字</p>
          </div>
          <div class="mb-4">
            <label class="block text-sm font-medium mb-1.5">密码 <span class="text-red-500">*</span></label>
            <input v-model="password" type="password" required class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-emerald-500" />
          </div>
          <div class="mb-4">
            <label class="block text-sm font-medium mb-1.5">确认密码 <span class="text-red-500">*</span></label>
            <input v-model="confirmPassword" type="password" required class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-emerald-500" />
          </div>
          <button type="submit" :disabled="loading" class="w-full bg-emerald-600 text-white py-2.5 rounded-md hover:bg-emerald-500 transition-colors disabled:opacity-50">
            {{ loading ? '注册中...' : '注册' }}
          </button>
        </form>
        <div class="text-center mt-4 text-sm">
          <p>已有账号？<router-link to="/user/login" class="text-emerald-600 hover:underline">立即登录</router-link></p>
        </div>
      </div>
    </div>
  </div>
</template>
