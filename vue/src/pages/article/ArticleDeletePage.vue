<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { AlertTriangle, X, Trash2 } from 'lucide-vue-next'
import { articleApi } from '@/api'
import { useMessages } from '@/composables/useMessages'

const route = useRoute()
const router = useRouter()
const { addMessage } = useMessages()
const indexId = Number(route.params.indexId)
const article = ref<any>(null)
const loading = ref(false)

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

async function handleDelete() {
  loading.value = true
  try {
    const res = await articleApi.delete(indexId)
    if (res.ok) {
      addMessage('文章已删除', 'success')
      router.push({ name: 'article-list' })
    } else {
      addMessage('删除失败', 'error')
    }
  } catch (e) {
    addMessage('删除失败', 'error')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    const res = await articleApi.getDetail(indexId)
    if (res.ok) article.value = res.data.article
  } catch (e) { /* ignore */ }
})
</script>

<template>
  <div class="max-w-2xl mx-auto mt-8">
    <div class="bg-white rounded-xl shadow-md border border-zinc-100">
      <div class="bg-red-600 text-white px-6 py-4 rounded-t-xl flex items-center gap-2">
        <AlertTriangle :size="20" /> <h4 class="font-semibold">确认删除文章</h4>
      </div>
      <div class="p-6">
        <div class="bg-amber-50 border border-amber-200 text-amber-800 px-4 py-3 rounded-md mb-4 text-sm">
          <strong>警告：</strong>此操作将删除文章的所有版本，且无法撤销！
        </div>
        <template v-if="article">
          <h5 class="font-semibold mb-3">文章信息：</h5>
          <ul class="divide-y border border-zinc-200 rounded-lg mb-6">
            <li class="px-4 py-2.5 text-sm"><strong>标题：</strong> {{ article.title }}</li>
            <li class="px-4 py-2.5 text-sm"><strong>作者：</strong> {{ article.author_id?.nickname || article.author_id?.username }}</li>
            <li class="px-4 py-2.5 text-sm"><strong>创建时间：</strong> {{ formatDate(article.created_at) }}</li>
            <li class="px-4 py-2.5 text-sm"><strong>最后更新：</strong> {{ formatDate(article.updated_at) }}</li>
          </ul>
        </template>
        <div class="flex justify-between">
          <router-link :to="{ name: 'article-detail', params: { indexId } }" class="inline-flex items-center gap-1.5 bg-zinc-200 text-zinc-700 px-5 py-2.5 rounded-lg hover:bg-zinc-300 transition-colors">
            <X :size="16" /> 取消
          </router-link>
          <button @click="handleDelete" :disabled="loading" class="inline-flex items-center gap-1.5 bg-red-600 text-white px-5 py-2.5 rounded-lg hover:bg-red-500 transition-colors disabled:opacity-50">
            <Trash2 :size="16" /> {{ loading ? '删除中...' : '确认删除' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
