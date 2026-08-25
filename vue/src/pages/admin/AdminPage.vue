<script setup lang="ts">
import { ref } from 'vue'
import { ShieldAlert, Users, FileText, MessageSquare, ShieldCheck } from 'lucide-vue-next'
import { useAuth } from '@/composables/useAuth'
import AdminUserTab from './AdminUserTab.vue'
import AdminArticleTab from './AdminArticleTab.vue'
import AdminCommentTab from './AdminCommentTab.vue'
import AdminRoleTab from './AdminRoleTab.vue'

const { isStaff, isAdmin } = useAuth()
const activeTab = ref<'users' | 'articles' | 'comments' | 'roles'>('users')
</script>

<template>
  <!-- 非 staff 用户无权访问（后端同样拦截，此处仅提前提示） -->
  <div v-if="!isStaff" class="bg-white rounded-xl shadow-sm border border-zinc-100 p-12 text-center">
    <ShieldAlert :size="40" class="mx-auto text-zinc-300 mb-3" />
    <h2 class="font-bold text-lg mb-1">无权访问</h2>
    <p class="text-sm text-zinc-400">管理后台仅对员工（is_staff）和管理员（is_admin）开放</p>
  </div>

  <div v-else>
    <div class="mb-6">
      <h1 class="text-2xl font-bold mb-1">管理后台</h1>
      <p class="text-sm text-zinc-400">
        {{ isAdmin ? '您是管理员，可查看和修改所有数据' : '您是员工，仅可查看数据（修改需管理员权限）' }}
      </p>
    </div>

    <!-- Tabs -->
    <div class="flex gap-1 mb-4 bg-white rounded-xl shadow-sm border border-zinc-100 p-1">
      <button
        @click="activeTab = 'users'"
        :class="activeTab === 'users' ? 'bg-emerald-600 text-white' : 'text-zinc-600 hover:bg-zinc-100'"
        class="flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium transition-colors"
      >
        <Users :size="16" /> 用户
      </button>
      <button
        @click="activeTab = 'articles'"
        :class="activeTab === 'articles' ? 'bg-emerald-600 text-white' : 'text-zinc-600 hover:bg-zinc-100'"
        class="flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium transition-colors"
      >
        <FileText :size="16" /> 文章
      </button>
      <button
        @click="activeTab = 'comments'"
        :class="activeTab === 'comments' ? 'bg-emerald-600 text-white' : 'text-zinc-600 hover:bg-zinc-100'"
        class="flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium transition-colors"
      >
        <MessageSquare :size="16" /> 评论
      </button>
      <button
        @click="activeTab = 'roles'"
        :class="activeTab === 'roles' ? 'bg-emerald-600 text-white' : 'text-zinc-600 hover:bg-zinc-100'"
        class="flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium transition-colors"
      >
        <ShieldCheck :size="16" /> 角色
      </button>
    </div>

    <!-- Tab 内容（v-if 惰性渲染，切换时重新挂载以刷新数据） -->
    <AdminUserTab v-if="activeTab === 'users'" />
    <AdminArticleTab v-else-if="activeTab === 'articles'" />
    <AdminCommentTab v-else-if="activeTab === 'comments'" />
    <AdminRoleTab v-else />
  </div>
</template>
