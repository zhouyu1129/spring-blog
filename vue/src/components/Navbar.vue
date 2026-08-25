<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Home, BookOpen, Info, Search, LogIn, UserPlus, User, LogOut, ShieldCheck } from 'lucide-vue-next'
import { useAuth } from '@/composables/useAuth'
import { useMessages } from '@/composables/useMessages'
import { authApi } from '@/api'

const router = useRouter()
const { isAuthenticated, isStaff, user, setUser } = useAuth()
const { addMessage } = useMessages()
const searchQuery = ref('')
const mobileMenuOpen = ref(false)

const displayName = computed(() => user.value?.nickname || user.value?.username || '')

function handleSearch() {
  if (searchQuery.value.trim()) {
    router.push({ name: 'article-list', query: { search: searchQuery.value.trim() } })
  }
}

async function handleLogout() {
  try {
    await authApi.logout()
  } catch (e) { /* ignore */ }
  localStorage.removeItem('isAuthenticated')
  localStorage.removeItem('user')
  setUser(null)
  addMessage('您已成功登出', 'info')
  router.push({ name: 'home' })
}
</script>

<template>
  <nav class="bg-zinc-900 text-white shadow-lg">
    <div class="container mx-auto px-4">
      <div class="flex items-center justify-between h-16">
        <!-- Logo -->
        <router-link to="/" class="flex items-center gap-2 text-xl font-bold hover:text-emerald-400 transition-colors">
          <BookOpen :size="24" />
          <span>校园博客</span>
        </router-link>

        <!-- Desktop Nav -->
        <div class="hidden md:flex items-center gap-6">
          <router-link to="/" class="flex items-center gap-1 hover:text-emerald-400 transition-colors">
            <Home :size="16" /> 首页
          </router-link>
          <router-link to="/article" class="flex items-center gap-1 hover:text-emerald-400 transition-colors">
            <BookOpen :size="16" /> 博客
          </router-link>
          <router-link to="/about" class="flex items-center gap-1 hover:text-emerald-400 transition-colors">
            <Info :size="16" /> 关于
          </router-link>
          <router-link v-if="isStaff" to="/admin" class="flex items-center gap-1 text-amber-400 hover:text-amber-300 transition-colors">
            <ShieldCheck :size="16" /> 后台管理
          </router-link>
        </div>

        <!-- Search & Auth -->
        <div class="hidden md:flex items-center gap-3">
          <form @submit.prevent="handleSearch" class="flex items-center">
            <input
              v-model="searchQuery"
              type="search"
              placeholder="搜索..."
              class="bg-zinc-800 text-white text-sm px-3 py-1.5 rounded-l-md border border-zinc-700 focus:outline-none focus:border-emerald-500"
            />
            <button type="submit" class="bg-zinc-700 px-3 py-1.5 rounded-r-md hover:bg-zinc-600 transition-colors">
              <Search :size="16" />
            </button>
          </form>

          <template v-if="isAuthenticated">
            <span class="text-sm text-zinc-300">欢迎, {{ displayName }}</span>
            <router-link to="/user/profile" class="flex items-center gap-1 text-sm bg-emerald-600 px-3 py-1.5 rounded-md hover:bg-emerald-500 transition-colors">
              <User :size="16" /> 用户中心
            </router-link>
            <button @click="handleLogout" class="flex items-center gap-1 text-sm bg-red-600 px-3 py-1.5 rounded-md hover:bg-red-500 transition-colors">
              <LogOut :size="16" /> 登出
            </button>
          </template>
          <template v-else>
            <router-link to="/user/login" class="flex items-center gap-1 text-sm bg-zinc-700 px-3 py-1.5 rounded-md hover:bg-zinc-600 transition-colors">
              <LogIn :size="16" /> 登录
            </router-link>
            <router-link to="/user/register" class="flex items-center gap-1 text-sm bg-amber-600 px-3 py-1.5 rounded-md hover:bg-amber-500 transition-colors">
              <UserPlus :size="16" /> 注册
            </router-link>
          </template>
        </div>

        <!-- Mobile menu button -->
        <button @click="mobileMenuOpen = !mobileMenuOpen" class="md:hidden p-2">
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
          </svg>
        </button>
      </div>

      <!-- Mobile menu -->
      <div v-if="mobileMenuOpen" class="md:hidden pb-4 space-y-2">
        <router-link to="/" class="block py-2 hover:text-emerald-400" @click="mobileMenuOpen = false">
          <Home :size="16" class="inline mr-1" /> 首页
        </router-link>
        <router-link to="/article" class="block py-2 hover:text-emerald-400" @click="mobileMenuOpen = false">
          <BookOpen :size="16" class="inline mr-1" /> 博客
        </router-link>
        <router-link to="/about" class="block py-2 hover:text-emerald-400" @click="mobileMenuOpen = false">
          <Info :size="16" class="inline mr-1" /> 关于
        </router-link>
        <router-link v-if="isStaff" to="/admin" class="block py-2 text-amber-400 hover:text-amber-300" @click="mobileMenuOpen = false">
          <ShieldCheck :size="16" class="inline mr-1" /> 后台管理
        </router-link>
        <form @submit.prevent="handleSearch" class="flex">
          <input v-model="searchQuery" type="search" placeholder="搜索..." class="flex-1 bg-zinc-800 text-white text-sm px-3 py-1.5 rounded-l-md border border-zinc-700" />
          <button type="submit" class="bg-zinc-700 px-3 py-1.5 rounded-r-md"><Search :size="16" /></button>
        </form>
        <template v-if="isAuthenticated">
          <router-link to="/user/profile" class="block py-2 hover:text-emerald-400">
            <User :size="16" class="inline mr-1" /> 用户中心
          </router-link>
          <button @click="handleLogout" class="block py-2 text-red-400 hover:text-red-300">
            <LogOut :size="16" class="inline mr-1" /> 登出
          </button>
        </template>
        <template v-else>
          <router-link to="/user/login" class="block py-2 hover:text-emerald-400">登录</router-link>
          <router-link to="/user/register" class="block py-2 hover:text-emerald-400">注册</router-link>
        </template>
      </div>
    </div>
  </nav>
</template>
