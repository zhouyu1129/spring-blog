<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { User as UserIcon, FileText, MessageSquare } from 'lucide-vue-next'
import Pagination from '@/components/Pagination.vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { authApi } from '@/api'
import { useAuth } from '@/composables/useAuth'
import { markdownToText } from '@/lib/markdown'

const route = useRoute()
const router = useRouter()
const { user: currentUser } = useAuth()

const userId = route.params.userId as string
const targetUser = ref<any>(null)
const articles = ref<any[]>([])
const comments = ref<any[]>([])
const articlePage = ref(1)
const commentPage = ref(1)
const articleTotalPages = ref(1)
const commentTotalPages = ref(1)
const activeTab = ref<'articles' | 'comments'>('articles')

const genderMap: Record<string, string> = { male: '男', female: '女', other: '其他' }

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

async function fetchProfile() {
  try {
    const res = await authApi.getUserProfile(userId)
    if (res.ok) {
      targetUser.value = res.data.target_user
      articles.value = res.data.article_page_obj?.object_list || res.data.articles || []
      articleTotalPages.value = res.data.article_page_obj?.paginator?.num_pages || 1
      comments.value = res.data.comment_page_obj?.object_list || res.data.comments || []
      commentTotalPages.value = res.data.comment_page_obj?.paginator?.num_pages || 1
    }
  } catch (e) {
    console.error('Failed to fetch user profile', e)
  }
}

onMounted(fetchProfile)
</script>

<template>
  <div v-if="targetUser" class="grid lg:grid-cols-4 gap-6">
    <!-- Left sidebar - User info -->
    <div class="space-y-4">
      <div class="bg-white rounded-xl shadow-sm border border-zinc-100 p-6 text-center">
        <div class="w-24 h-24 bg-zinc-200 rounded-full mx-auto mb-3 flex items-center justify-center text-zinc-400">
          <UserIcon :size="40" />
        </div>
        <h4 class="font-bold text-lg">{{ targetUser.nickname || targetUser.username }}</h4>
        <p class="text-zinc-400 text-sm">@{{ targetUser.username }}</p>
        <div class="mt-3 flex justify-center gap-2">
          <span class="text-xs bg-emerald-100 text-emerald-700 px-2 py-0.5 rounded-full">文章：{{ articleTotalPages }}</span>
          <span class="text-xs bg-sky-100 text-sky-700 px-2 py-0.5 rounded-full">评论：{{ commentTotalPages }}</span>
        </div>
      </div>

      <div class="bg-white rounded-xl shadow-sm border border-zinc-100">
        <div class="px-4 py-3 border-b border-zinc-100"><h6 class="font-semibold text-sm">个人信息</h6></div>
        <div class="divide-y text-sm">
          <div v-if="targetUser.real_name" class="px-4 py-2.5"><strong>姓名：</strong>{{ targetUser.real_name }}</div>
          <div v-if="targetUser.gender" class="px-4 py-2.5"><strong>性别：</strong>{{ genderMap[targetUser.gender] || targetUser.gender }}</div>
          <div class="px-4 py-2.5"><strong>注册时间：</strong>{{ formatDate(targetUser.date_joined).split(' ')[0] }}</div>
          <div class="px-4 py-2.5"><strong>邮箱已验证：</strong>
            <span v-if="targetUser.email_verified" class="text-xs bg-emerald-100 text-emerald-700 px-1.5 py-0.5 rounded-full">已验证</span>
            <span v-else class="text-xs bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded-full">未验证</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Right content -->
    <div class="lg:col-span-3">
      <!-- Tabs -->
      <div class="flex gap-1 mb-4 bg-white rounded-xl shadow-sm border border-zinc-100 p-1">
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
      </div>

      <!-- Articles Tab -->
      <div v-if="activeTab === 'articles'">
        <div v-if="articles.length > 0" class="space-y-4">
          <div v-for="article in articles" :key="article.index_id" class="bg-white rounded-xl shadow-sm border border-zinc-100 p-5 hover:shadow-md transition-shadow">
            <h5 class="font-semibold mb-1">
              <router-link :to="{ name: 'article-detail', params: { indexId: article.index_id } }" class="hover:text-emerald-600 transition-colors">
                {{ article.title }}
              </router-link>
            </h5>
            <p class="text-zinc-400 text-xs mb-2">
              {{ formatDate(article.created_at) }}
              <template v-if="article.created_at !== article.updated_at"> | 更新于 {{ formatDate(article.updated_at) }}</template>
            </p>
            <p v-if="article.content" class="text-zinc-500 text-sm line-clamp-2 mb-2">{{ markdownToText(article.content, 150) }}</p>
            <router-link :to="{ name: 'article-detail', params: { indexId: article.index_id } }" class="text-emerald-600 text-sm hover:underline">阅读全文 &rarr;</router-link>
          </div>
        </div>
        <div v-else class="bg-white rounded-xl shadow-sm border border-zinc-100 p-8 text-center text-zinc-400">
          <FileText :size="32" class="mx-auto mb-2" />
          <p>暂无文章</p>
        </div>
      </div>

      <!-- Comments Tab -->
      <div v-if="activeTab === 'comments'">
        <div v-if="comments.length > 0" class="space-y-4">
          <div v-for="comment in comments" :key="comment.index_id" class="bg-white rounded-xl shadow-sm border border-zinc-100 p-5">
            <div class="flex items-center justify-between mb-2">
              <div class="text-sm">
                <strong>{{ comment.author?.nickname || comment.author?.username }}</strong>
                <span class="text-zinc-400 text-xs ml-2">{{ formatDate(comment.create_time) }}</span>
              </div>
              <span v-if="comment.top" class="text-xs bg-amber-100 text-amber-800 px-2 py-0.5 rounded-full flex items-center gap-1">
                置顶
              </span>
            </div>
            <div class="text-sm text-zinc-600">
              <MarkdownRenderer :content="comment.content || ''" />
            </div>
            <div v-if="comment.article" class="mt-2">
              <span class="text-xs text-zinc-400">
                评论文章：<router-link :to="{ name: 'article-detail', params: { indexId: comment.article.index_id } }" class="text-emerald-600 hover:underline">{{ comment.article.title }}</router-link>
              </span>
            </div>
          </div>
        </div>
        <div v-else class="bg-white rounded-xl shadow-sm border border-zinc-100 p-8 text-center text-zinc-400">
          <MessageSquare :size="32" class="mx-auto mb-2" />
          <p>暂无评论</p>
        </div>
      </div>
    </div>
  </div>

  <div v-else class="text-center py-16 text-zinc-400">加载中...</div>
</template>
