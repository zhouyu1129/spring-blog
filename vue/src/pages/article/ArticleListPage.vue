<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus, Search, PenLine, User, LogIn, UserPlus } from 'lucide-vue-next'
import Pagination from '@/components/Pagination.vue'
import { articleApi } from '@/api'
import { useAuth } from '@/composables/useAuth'
import { markdownToText, renderMarkdown } from '@/lib/markdown'

const route = useRoute()
const router = useRouter()
const { isAuthenticated } = useAuth()

const articles = ref<any[]>([])
const currentPage = ref(1)
const totalPages = ref(1)
const searchQuery = ref('')
const loading = ref(false)

async function fetchArticles() {
  loading.value = true
  try {
    const res = await articleApi.getList({
      search: searchQuery.value || undefined,
      page: currentPage.value,
    })
    if (res.ok) {
      const list = res.data.page_obj?.object_list || res.data.articles || []
      // 后端只提供 Markdown 原文，摘要/首图由前端渲染计算
      articles.value = list.map((article: any) => ({
        ...article,
        rendered_html: renderMarkdown(article.content || ''),
      }))
      totalPages.value = res.data.page_obj?.paginator?.num_pages || 1
      currentPage.value = res.data.page_obj?.number || 1
    }
  } catch (e) {
    console.error('Failed to fetch articles', e)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  router.push({ query: searchQuery.value ? { search: searchQuery.value } : {} })
}

function handlePageChange(page: number) {
  currentPage.value = page
  fetchArticles()
}

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function getExcerpt(article: any): string {
  return markdownToText(article.content || '', 150)
}

function getFirstImage(article: any): string | null {
  const html = article.rendered_html || ''
  const match = html.match(/<img[^>]+src="([^"]+)"/)
  return match ? match[1] : null
}

onMounted(() => {
  searchQuery.value = (route.query.search as string) || ''
  fetchArticles()
})

watch(() => route.query.search, (val) => {
  searchQuery.value = (val as string) || ''
  fetchArticles()
})
</script>

<template>
  <div class="grid lg:grid-cols-4 gap-6">
    <!-- Main Content -->
    <div class="lg:col-span-3">
      <div class="flex items-center justify-between mb-6">
        <h2 class="text-2xl font-bold">文章列表</h2>
        <router-link v-if="isAuthenticated" to="/article/create" class="inline-flex items-center gap-1.5 bg-emerald-600 text-white px-4 py-2 rounded-lg hover:bg-emerald-500 transition-colors text-sm">
          <Plus :size="16" /> 发布文章
        </router-link>
      </div>

      <!-- Search -->
      <div class="bg-white rounded-xl shadow-sm border border-zinc-100 p-4 mb-6">
        <form @submit.prevent="handleSearch" class="flex">
          <input v-model="searchQuery" type="search" placeholder="搜索文章标题或内容..." class="flex-1 border border-zinc-200 rounded-l-md px-4 py-2 text-sm focus:outline-none focus:border-emerald-500" />
          <button type="submit" class="bg-zinc-800 text-white px-4 py-2 rounded-r-md hover:bg-zinc-700 transition-colors text-sm">
            <Search :size="16" />
          </button>
        </form>
      </div>

      <!-- Loading -->
      <div v-if="loading" class="text-center py-12 text-zinc-400">加载中...</div>

      <!-- Articles -->
      <div v-else-if="articles.length > 0" class="space-y-4">
        <div v-for="article in articles" :key="article.index_id" class="bg-white rounded-xl shadow-sm border border-zinc-100 p-5 hover:shadow-md transition-shadow">
          <div class="flex gap-4">
            <div class="flex-1 min-w-0">
              <h2 class="text-xl font-semibold mb-2">
                <router-link :to="{ name: 'article-detail', params: { indexId: article.index_id } }" class="hover:text-emerald-600 transition-colors">
                  {{ article.title }}
                </router-link>
                <span v-if="article.is_hidden" class="ml-2 align-middle text-xs font-normal bg-amber-100 text-amber-700 border border-amber-200 px-2 py-0.5 rounded-full">已隐藏</span>
              </h2>
              <p class="text-zinc-400 text-sm mb-3">
                作者：{{ article.author_id?.nickname || article.author_id?.username || '未知' }} |
                发布时间：{{ formatDate(article.created_at) }} |
                更新时间：{{ formatDate(article.updated_at) }}
              </p>
              <p class="text-zinc-600 text-sm mb-3 line-clamp-3">{{ getExcerpt(article) }}</p>
              <router-link :to="{ name: 'article-detail', params: { indexId: article.index_id } }" class="text-emerald-600 text-sm font-medium hover:underline">阅读全文 &rarr;</router-link>
            </div>
            <div v-if="getFirstImage(article)" class="flex-shrink-0 hidden sm:block">
              <img :src="getFirstImage(article)" :alt="article.title" class="w-32 h-24 object-cover rounded-lg" />
            </div>
          </div>
        </div>

        <Pagination :current-page="currentPage" :total-pages="totalPages" @page-change="handlePageChange" />
      </div>

      <!-- Empty -->
      <div v-else class="bg-white rounded-xl shadow-sm border border-zinc-100 p-8 text-center text-zinc-400">
        <template v-if="searchQuery">没有找到包含 "{{ searchQuery }}" 的文章。</template>
        <template v-else>
          暂无文章，
          <router-link v-if="isAuthenticated" to="/article/create" class="text-emerald-600 hover:underline">创建第一篇文章</router-link>
          <router-link v-else to="/user/login" class="text-emerald-600 hover:underline">登录</router-link>
          <template v-if="!isAuthenticated">后创建文章</template>
        </template>
      </div>
    </div>

    <!-- Sidebar -->
    <div class="space-y-4">
      <div class="bg-white rounded-xl shadow-sm border border-zinc-100">
        <div class="px-4 py-3 border-b border-zinc-100"><h5 class="font-semibold text-sm">快速链接</h5></div>
        <div class="p-4 space-y-2">
          <router-link v-if="isAuthenticated" to="/article/create" class="flex items-center gap-2 w-full text-sm bg-emerald-600 text-white px-3 py-2 rounded-md hover:bg-emerald-500 transition-colors">
            <PenLine :size="14" /> 写新文章
          </router-link>
          <router-link v-if="isAuthenticated" to="/user/profile" class="flex items-center gap-2 w-full text-sm bg-sky-600 text-white px-3 py-2 rounded-md hover:bg-sky-500 transition-colors">
            <User :size="14" /> 个人中心
          </router-link>
          <router-link v-if="!isAuthenticated" to="/user/login" class="flex items-center gap-2 w-full text-sm bg-zinc-700 text-white px-3 py-2 rounded-md hover:bg-zinc-600 transition-colors">
            <LogIn :size="14" /> 登录
          </router-link>
          <router-link v-if="!isAuthenticated" to="/user/register" class="flex items-center gap-2 w-full text-sm bg-amber-500 text-white px-3 py-2 rounded-md hover:bg-amber-400 transition-colors">
            <UserPlus :size="14" /> 注册
          </router-link>
        </div>
      </div>

      <div class="bg-white rounded-xl shadow-sm border border-zinc-100">
        <div class="px-4 py-3 border-b border-zinc-100"><h5 class="font-semibold text-sm">网站信息</h5></div>
        <div class="p-4">
          <p class="text-zinc-500 text-sm mb-3">欢迎来到校园博客，这里是分享知识和想法的地方。</p>
          <router-link to="/about" class="text-emerald-600 text-sm font-medium hover:underline">了解更多 &rarr;</router-link>
        </div>
      </div>
    </div>
  </div>
</template>
