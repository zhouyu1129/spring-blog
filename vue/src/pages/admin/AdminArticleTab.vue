<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Pencil, EyeOff, Eye, Trash2, RotateCcw, X, Search } from 'lucide-vue-next'
import Pagination from '@/components/Pagination.vue'
import { adminApi } from '@/api'
import { useAuth } from '@/composables/useAuth'
import { useMessages } from '@/composables/useMessages'

const { isAdmin } = useAuth()
const { addMessage } = useMessages()

const articles = ref<any[]>([])
const page = ref(1)
const totalPages = ref(1)
const search = ref('')
// 状态过滤：'' 全部 / 'deleted' / 'hidden' / 'normal'
const statusFilter = ref('')
const loading = ref(false)

// 编辑文章弹窗
const showEditModal = ref(false)
const editForm = ref({ indexId: 0, title: '', content: '' })
const saving = ref(false)

function filterParams() {
  if (statusFilter.value === 'deleted') return { deleted: true }
  if (statusFilter.value === 'hidden') return { hidden: true, deleted: false }
  if (statusFilter.value === 'normal') return { deleted: false, hidden: false }
  return {}
}

async function fetchArticles() {
  loading.value = true
  try {
    const res = await adminApi.listArticles({
      search: search.value || undefined,
      ...filterParams(),
      page: page.value,
    })
    if (res.ok) {
      articles.value = res.data.page_obj.object_list
      totalPages.value = res.data.page_obj.paginator.num_pages
      page.value = res.data.page_obj.number
    } else {
      addMessage(res.data?.message || '获取文章列表失败', 'error')
    }
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchArticles()
}

function handleFilterChange() {
  page.value = 1
  fetchArticles()
}

function handlePageChange(newPage: number) {
  page.value = newPage
  fetchArticles()
}

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

// 编辑：先取详情（含正文），再打开弹窗
async function openEditModal(a: any) {
  const res = await adminApi.getArticle(a.index_id)
  if (!res.ok) {
    addMessage(res.data?.message || '获取文章详情失败', 'error')
    return
  }
  editForm.value = {
    indexId: a.index_id,
    title: res.data.article.title,
    content: res.data.article.content || '',
  }
  showEditModal.value = true
}

async function handleEdit() {
  saving.value = true
  try {
    const res = await adminApi.updateArticle(editForm.value.indexId, {
      title: editForm.value.title,
      content: editForm.value.content,
    })
    if (res.ok) {
      showEditModal.value = false
      addMessage('文章已更新（生成新版本）', 'success')
      fetchArticles()
    } else {
      addMessage(res.data?.message || '更新失败', 'error')
    }
  } finally {
    saving.value = false
  }
}

async function toggleHidden(a: any) {
  const res = await adminApi.updateArticle(a.index_id, { is_hidden: !a.is_hidden })
  if (res.ok) {
    addMessage(a.is_hidden ? '已取消隐藏' : '已隐藏', 'success')
    fetchArticles()
  } else {
    addMessage(res.data?.message || '操作失败', 'error')
  }
}

async function toggleDeleted(a: any) {
  const action = a.is_deleted ? '恢复' : '删除'
  if (!a.is_deleted && !confirm(`确定删除文章「${a.title}」？（软删除，可恢复）`)) return
  const res = await adminApi.updateArticle(a.index_id, { is_deleted: !a.is_deleted })
  if (res.ok) {
    addMessage(`文章已${action}`, 'success')
    fetchArticles()
  } else {
    addMessage(res.data?.message || '操作失败', 'error')
  }
}

onMounted(fetchArticles)
</script>

<template>
  <div>
    <!-- 工具栏 -->
    <div class="flex flex-wrap items-center gap-3 mb-4">
      <form @submit.prevent="handleSearch" class="flex">
        <input
          v-model="search"
          type="search"
          placeholder="搜索文章标题..."
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

    <!-- 文章表格 -->
    <div class="bg-white rounded-xl shadow-sm border border-zinc-100 overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
          <tr class="bg-zinc-50 text-left text-zinc-500">
            <th class="px-4 py-3 font-medium">#</th>
            <th class="px-4 py-3 font-medium">标题</th>
            <th class="px-4 py-3 font-medium">作者</th>
            <th class="px-4 py-3 font-medium">状态</th>
            <th class="px-4 py-3 font-medium">创建时间</th>
            <th v-if="isAdmin" class="px-4 py-3 font-medium text-right">操作</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-zinc-100">
          <tr v-if="loading">
            <td colspan="6" class="px-4 py-8 text-center text-zinc-400">加载中...</td>
          </tr>
          <tr v-else-if="articles.length === 0">
            <td colspan="6" class="px-4 py-8 text-center text-zinc-400">暂无文章</td>
          </tr>
          <tr v-for="a in articles" :key="a.index_id" class="hover:bg-zinc-50">
            <td class="px-4 py-3 text-zinc-400">{{ a.index_id }}</td>
            <td class="px-4 py-3 font-medium">
              <router-link :to="`/article/${a.index_id}`" class="hover:text-emerald-600">{{ a.title }}</router-link>
            </td>
            <td class="px-4 py-3 text-zinc-500">
              <router-link v-if="a.author" :to="`/user/${a.author.id}`" class="hover:text-emerald-600">
                {{ a.author.nickname || a.author.username }}
              </router-link>
              <span v-else>-</span>
            </td>
            <td class="px-4 py-3 space-x-1 whitespace-nowrap">
              <span v-if="a.is_deleted" class="text-xs bg-red-100 text-red-700 px-1.5 py-0.5 rounded-full">已删除</span>
              <span v-else-if="a.is_hidden" class="text-xs bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded-full">已隐藏</span>
              <span v-else class="text-xs bg-emerald-100 text-emerald-700 px-1.5 py-0.5 rounded-full">正常</span>
            </td>
            <td class="px-4 py-3 text-zinc-500">{{ formatDate(a.created_at) }}</td>
            <td v-if="isAdmin" class="px-4 py-3 text-right space-x-1 whitespace-nowrap">
              <button @click="openEditModal(a)" class="inline-flex items-center gap-1 text-sky-600 hover:text-sky-500">
                <Pencil :size="14" /> 编辑
              </button>
              <button @click="toggleHidden(a)" class="inline-flex items-center gap-1 text-amber-600 hover:text-amber-500">
                <template v-if="a.is_hidden"><Eye :size="14" /> 取消隐藏</template>
                <template v-else><EyeOff :size="14" /> 隐藏</template>
              </button>
              <button @click="toggleDeleted(a)" :class="a.is_deleted ? 'text-emerald-600 hover:text-emerald-500' : 'text-red-600 hover:text-red-500'" class="inline-flex items-center gap-1">
                <template v-if="a.is_deleted"><RotateCcw :size="14" /> 恢复</template>
                <template v-else><Trash2 :size="14" /> 删除</template>
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <Pagination :current-page="page" :total-pages="totalPages" @page-change="handlePageChange" />

    <!-- 编辑文章弹窗 -->
    <div v-if="showEditModal" class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" @click.self="showEditModal = false">
      <div class="bg-white rounded-xl shadow-xl w-full max-w-2xl p-6 max-h-[90vh] overflow-y-auto">
        <div class="flex items-center justify-between mb-4">
          <h3 class="font-bold text-lg">编辑文章 #{{ editForm.indexId }}</h3>
          <button @click="showEditModal = false" class="text-zinc-400 hover:text-zinc-600"><X :size="18" /></button>
        </div>
        <form @submit.prevent="handleEdit" class="space-y-3">
          <input v-model="editForm.title" required placeholder="标题" class="w-full border border-zinc-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:border-emerald-500" />
          <textarea v-model="editForm.content" required rows="12" placeholder="正文（Markdown）" class="w-full border border-zinc-300 rounded-md px-3 py-2 text-sm font-mono focus:outline-none focus:border-emerald-500" />
          <p class="text-xs text-zinc-400">保存后将生成新版本，作者不变，图片/文件关联保留；隐藏/删除状态保持不变。</p>
          <div class="flex justify-end gap-2 pt-2">
            <button type="button" @click="showEditModal = false" class="px-4 py-2 text-sm rounded-md bg-zinc-100 hover:bg-zinc-200">取消</button>
            <button type="submit" :disabled="saving" class="px-4 py-2 text-sm rounded-md bg-emerald-600 text-white hover:bg-emerald-500 disabled:opacity-50">
              {{ saving ? '保存中...' : '保存' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>
