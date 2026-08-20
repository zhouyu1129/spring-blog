<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { User, Home, Edit, Mail, Lock } from 'lucide-vue-next'
import { authApi } from '@/api'
import { useAuth } from '@/composables/useAuth'

const router = useRouter()
const { user, setUser } = useAuth()

const genderMap: Record<string, string> = { male: '男', female: '女', other: '其他' }

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

onMounted(async () => {
  try {
    const res = await authApi.getProfile()
    if (res.ok && res.data.user) {
      setUser(res.data.user)
      localStorage.setItem('user', JSON.stringify(res.data.user))
    }
  } catch (e) { /* ignore */ }
})
</script>

<template>
  <div class="grid md:grid-cols-4 gap-6" v-if="user">
    <!-- Sidebar -->
    <div class="bg-white rounded-xl shadow-sm border border-zinc-100 h-fit">
      <div class="bg-emerald-700 text-white px-4 py-3 rounded-t-xl"><h5 class="font-semibold text-sm">用户中心</h5></div>
      <div class="divide-y">
        <router-link :to="{ name: 'user-profile', params: { userId: user.id } }" class="flex items-center gap-2 px-4 py-2.5 text-sm hover:bg-zinc-50 transition-colors"><Home :size="14" /> 我的主页</router-link>
        <router-link to="/user/profile" class="flex items-center gap-2 px-4 py-2.5 text-sm bg-emerald-50 text-emerald-700 font-medium"><User :size="14" /> 个人信息</router-link>
        <router-link to="/user/profile/edit" class="flex items-center gap-2 px-4 py-2.5 text-sm hover:bg-zinc-50 transition-colors"><Edit :size="14" /> 编辑资料</router-link>
        <router-link to="/user/profile/change-email" class="flex items-center gap-2 px-4 py-2.5 text-sm hover:bg-zinc-50 transition-colors"><Mail :size="14" /> 修改邮箱</router-link>
        <router-link to="/user/profile/change-password" class="flex items-center gap-2 px-4 py-2.5 text-sm hover:bg-zinc-50 transition-colors"><Lock :size="14" /> 修改密码</router-link>
      </div>
    </div>

    <!-- Content -->
    <div class="md:col-span-3">
      <div class="bg-white rounded-xl shadow-sm border border-zinc-100">
        <div class="bg-sky-600 text-white px-6 py-4 rounded-t-xl"><h5 class="font-semibold">个人信息</h5></div>
        <div class="p-6 divide-y">
          <div class="flex py-3"><span class="w-28 font-medium text-sm text-zinc-500">用户名：</span><span class="text-sm">{{ user.username }}</span></div>
          <div class="flex py-3"><span class="w-28 font-medium text-sm text-zinc-500">邮箱：</span><span class="text-sm">{{ user.email }} <span v-if="user.email_verified" class="text-xs bg-emerald-100 text-emerald-700 px-1.5 py-0.5 rounded-full">已验证</span><span v-else class="text-xs bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded-full">未验证</span></span></div>
          <div class="flex py-3"><span class="w-28 font-medium text-sm text-zinc-500">学号：</span><span class="text-sm">{{ user.student_number }}</span></div>
          <div class="flex py-3"><span class="w-28 font-medium text-sm text-zinc-500">昵称：</span><span class="text-sm">{{ user.nickname || '未设置' }}</span></div>
          <div class="flex py-3"><span class="w-28 font-medium text-sm text-zinc-500">真实姓名：</span><span class="text-sm">{{ user.real_name || '未设置' }}</span></div>
          <div class="flex py-3"><span class="w-28 font-medium text-sm text-zinc-500">手机号：</span><span class="text-sm">{{ user.mobile || '未设置' }}</span></div>
          <div class="flex py-3"><span class="w-28 font-medium text-sm text-zinc-500">性别：</span><span class="text-sm">{{ genderMap[user.gender || ''] || '未设置' }}</span></div>
          <div class="flex py-3"><span class="w-28 font-medium text-sm text-zinc-500">注册时间：</span><span class="text-sm">{{ formatDate(user.date_joined) }}</span></div>
          <div class="flex py-3"><span class="w-28 font-medium text-sm text-zinc-500">最后登录：</span><span class="text-sm">{{ user.last_login ? formatDate(user.last_login) : '从未登录' }}</span></div>
        </div>
      </div>
    </div>
  </div>
</template>
