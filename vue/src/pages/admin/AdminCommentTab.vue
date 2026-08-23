<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Pencil, EyeOff, Eye, Trash2, RotateCcw, X, Search } from 'lucide-vue-next'
import Pagination from '@/components/Pagination.vue'
import { adminApi } from '@/api'
import { useAuth } from '@/composables/useAuth'
import { useMessages } from '@/composables/useMessages'

const { isAdmin } = useAuth()
const { addMessage } = useMessages()

const comments = ref<any[]>([])
const page = ref(1)
const totalPages = ref(1)
const search = ref('')
const statusFilter = ref('')
const loading = ref(false)

// 编辑评论弹窗
const showEditModal = ref(false)
const editForm = ref({ indexId: 0, content: '' })

function filterParams() {
  if (statusFilter.value === 'deleted') return { deleted: true }
  if (statusFilter.value === 'hidden') return { hidden: true, deleted: false }
  if (statusFilter.value === 'normal') return { deleted: false, hidden: false }
  return {}
}

async function fetchComments() {
  loading.value = true
  try {
    const res = await adminApi.listComments({
      search: search.value || undefined,
      ...filterParams(),
      page: page.value,
    })
    if (res.ok) {
      comments.value = res.data.page_obj.object_list
      totalPages.value = res.data.page_obj.paginator.num_pages
      page.value = res.data.page_obj.number
    } else {
      addMessage(res.data?.message || '获取评论列表失败', 'error')
    }
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchComments()
}

function handleFilterChange() {
  page.value = 1
  fetchComments()
}

function handlePageChange(newPage: number) {
  page.value = newPage
  fetchComments()
}

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

function openEditModal(c: any) {
  editForm.value = { indexId: c.index_id, content: c.content }
  showEditModal.value = true
}

async function handleEdit() {
  const res = await adminApi.updateComment(editForm.value.indexId, { content: editForm.value.content })
  if (res.ok) {
    showEditModal.value = false
    addMessage('评论已更新（生成新版本）', 'success')
    fetchComments()
  } else {
    addMessage(res.data?.message || '更新失败', 'error')
  }
}

async function toggleHidden(c: any) {
  const res = await adminApi.updateComment(c.index_id, { is_hidden: !c.is_hidden })
  if (res.ok) {
    addMessage(c.is_hidden ? '已取消隐藏' : '已隐藏', 'success')
    fetchComments()
  } else {
    addMessage(res.data?.message || '操作失败', 'error')
  }
}

async function toggleDeleted(c: any) {
  const action = c.is_deleted ? '恢复' : '删除'
  if (!c.is_deleted && !confirm('确定删除该评论？（软删除，可恢复）')) return
  const res = await adminApi.updateComment(c.index_id, { is_deleted: !c.is_deleted })
  if (res.ok) {
    addMessage(`评论已${action}`, 'success')
    fetchComments()
  } else {
    addMessage(res.data?.message || '操作失败', 'error')
  }
}

onMounted(fetchComments)
</script>

<template>
  <div>
    <!-- 工具栏 -->
    <div class="flex flex-wrap items-center gap-3 mb-4">
      <form @submit.prevent="handleSearch" class="flex">
        <input
          v-model="search"
          type="search"
          placeholder="搜索评论内容..."
          class="bg-white text-sm px-3 py-1.5 rounded-l-md border border-zinc-300 focus:outline-none focus:border-emerald-500 w-56"
        />
        <button type="submit" class="bg-zinc-700 text-white px-3 py-1.5 rounded-r-md hover:bg-zinc-600 transition-colors">
          <Search :size="16" />
        </button>
      </form>
      <select
        v-model="statusFilter"
        @change="handleFilterChange"
        class="bg-white text-sm px-3 py-1.5 rounded-md border border-zinc-300 focus:outline-none focus:border-emerald-500"
      >
        <option value="">全部状态</option>
        <option value="normal">正常</option>
        <option value="hidden">已隐藏</option>
        <option value="deleted">已删除</option>
      </select>
      <span v-if="!isAdmin" class="text-xs text-zinc-400">仅管理员可修改数据</span>
    </div>

    <!-- 评论列表 -->
    <div class="space-y-3">
      <div v-if="loading" class="bg-white rounded-xl border border-zinc-100 p-8 text-center text-zinc-400">加载中...</div>
      <div v-else-if="comments.length === 0" class="bg-white rounded-xl border border-zinc-100 p-8 text-center text-zinc-400">暂无评论</div>
      <div v-for="c in comments" :key="c.index_id" class="bg-white rounded-xl shadow-sm border border-zinc-100 p-4">
        <div class="flex items-start justify-between gap-3">
          <div class="min-w-0 flex-1">
            <div class="flex flex-wrap items-center gap-2 text-sm text-zinc-500 mb-1">
              <router-link v-if="c.author" :to="`/user/${c.author.id}`" class="font-medium text-zinc-700 hover:text-emerald-600">
                {{ c.author.nickname || c.author.username }}
              </router-link>
              <span>评论于</span>
              <router-link :to="`/comment/${c.article_index_id}`" class="hover:text-emerald-600">
                《{{ c.article_title || `文章 #${c.article_index_id}` }}》
              </router-link>
              <span class="text-xs space-x-1">
                <span v-if="c.is_deleted" class="bg-red-100 text-red-700 px-1.5 py-0.5 rounded-full">已删除</span>
                <span v-else-if="c.is_hidden" class="bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded-full">已隐藏</span>
              </span>
            </div>
            <p class="text-sm text-zinc-700 whitespace-pre-wrap break-words">{{ c.content }}</p>
            <p class="text-xs text-zinc-400 mt-1">{{ formatDate(c.created_at) }}</p>
          </div>
          <div v-if="isAdmin" class="flex flex-col gap-1 shrink-0 text-right">
            <button @click="openEditModal(c)" class="inline-flex items-center justify-end gap-1 text-sky-600 hover:text-sky-500 text-sm">
              <Pencil :size="14" /> 编辑
            </button>
            <button @click="toggleHidden(c)" class="inline-flex items-center justify-end gap-1 text-amber-600 hover:text-amber-500 text-sm">
              <template v-if="c.is_hidden"><Eye :size="14" /> 取消隐藏</template>
              <template v-else><EyeOff :size="14" /> 隐藏</template>
            </button>
            <button @click="toggleDeleted(c)" :class="c.is_deleted ? 'text-emerald-600 hover:text-emerald-500' : 'text-red-600 hover:text-red-500'" class="inline-flex items-center justify-end gap-1 text-sm">
              <template v-if="c.is_deleted"><RotateCcw :size="14" /> 恢复</template>
              <template v-else><Trash2 :size="14" /> 删除</template>
            </button>
          </div>
        </div>
      </div>
    </div>

    <Pagination :current-page="page" :total-pages="totalPages" @page-change="handlePageChange" />

    <!-- 编辑评论弹窗 -->
    <div v-if="showEditModal" class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" @click.self="showEditModal = false">
      <div class="bg-white rounded-xl shadow-xl w-full max-w-lg p-6">
        <div class="flex items-center justify-between mb-4">
          <h3 class="font-bold text-lg">编辑评论 #{{ editForm.indexId }}</h3>
          <button @click="showEditModal = false" class="text-zinc-400 hover:text-zinc-600"><X :size="18" /></button>
        </div>
        <form @submit.prevent="handleEdit" class="space-y-3">
          <textarea v-model="editForm.content" required rows="6" placeholder="评论内容" class="w-full border border-zinc-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:border-emerald-500" />
          <p class="text-xs text-zinc-400">保存后将生成新版本，作者与所属文章不变。</p>
          <div class="flex justify-end gap-2 pt-2">
            <button type="button" @click="showEditModal = false" class="px-4 py-2 text-sm rounded-md bg-zinc-100 hover:bg-zinc-200">取消</button>
            <button type="submit" class="px-4 py-2 text-sm rounded-md bg-emerald-600 text-white hover:bg-emerald-500">保存</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>
