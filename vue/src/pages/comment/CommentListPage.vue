<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { MessageSquare, ArrowLeft, Eye, EyeOff, Pin, Edit, Trash2 } from 'lucide-vue-next'
import Pagination from '@/components/Pagination.vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { commentApi } from '@/api'
import { useAuth } from '@/composables/useAuth'
import { useMessages } from '@/composables/useMessages'

const route = useRoute()
const router = useRouter()
const { user, isAuthenticated } = useAuth()
const { addMessage } = useMessages()

const articleIndexId = Number(route.params.articleIndexId)
const article = ref<any>(null)
const comments = ref<any[]>([])
const currentPage = ref(1)
const totalPages = ref(1)
const newComment = ref('')
const loading = ref(false)
const submitting = ref(false)
const togglingHideId = ref<number | null>(null)

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

async function fetchComments(page = 1) {
  loading.value = true
  try {
    const res = await commentApi.getList(articleIndexId, page)
    if (res.ok) {
      article.value = res.data.article
      comments.value = res.data.page_obj?.object_list || res.data.comments || []
      totalPages.value = res.data.page_obj?.paginator?.num_pages || 1
      currentPage.value = res.data.page_obj?.number || 1
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  if (!newComment.value.trim()) return
  submitting.value = true
  try {
    const res = await commentApi.create(articleIndexId, newComment.value)
    if (res.ok) {
      addMessage('评论发布成功', 'success')
      newComment.value = ''
      fetchComments(1)
    } else {
      addMessage('评论发布失败', 'error')
    }
  } catch (e) {
    addMessage('评论发布失败', 'error')
  } finally {
    submitting.value = false
  }
}

// 隐藏/取消隐藏评论（仅评论作者，由后端校验）
async function handleToggleHide(comment: any) {
  togglingHideId.value = comment.index_id
  try {
    const res = comment.is_hidden
      ? await commentApi.unhide(comment.index_id)
      : await commentApi.hide(comment.index_id)
    if (res.ok) {
      comment.is_hidden = !comment.is_hidden
      addMessage(comment.is_hidden ? '评论已隐藏，仅您和管理员可见' : '评论已取消隐藏', 'success')
    } else {
      addMessage(res.data?.message || '操作失败', 'error')
    }
  } catch (e) {
    addMessage('操作失败', 'error')
  } finally {
    togglingHideId.value = null
  }
}

onMounted(() => fetchComments())
</script>

<template>
  <div class="grid lg:grid-cols-4 gap-6">
    <div class="lg:col-span-3">
      <!-- Comment Form -->
      <div class="bg-white rounded-xl shadow-sm border border-zinc-100 mb-6">
        <div class="px-5 py-4 border-b border-zinc-100">
          <h4 class="font-semibold flex items-center gap-2"><MessageSquare :size="18" /> {{ article?.title || '文章' }} - 评论</h4>
        </div>
        <div class="p-5">
          <template v-if="isAuthenticated">
            <form @submit.prevent="handleSubmit">
              <label class="block text-sm font-medium mb-1.5">发表评论</label>
              <textarea v-model="newComment" rows="4" placeholder="请输入评论内容（支持Markdown格式）" class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-emerald-500 mb-3"></textarea>
              <button type="submit" :disabled="submitting" class="inline-flex items-center gap-1.5 bg-emerald-600 text-white px-4 py-2 rounded-md hover:bg-emerald-500 transition-colors text-sm disabled:opacity-50">
                <MessageSquare :size="14" /> {{ submitting ? '发布中...' : '发表评论' }}
              </button>
            </form>
          </template>
          <template v-else>
            <div class="bg-sky-50 border border-sky-200 text-sky-800 px-4 py-3 rounded-md text-sm">
              请<router-link to="/user/login" class="text-sky-600 font-medium hover:underline">登录</router-link>后发表评论
            </div>
          </template>
        </div>
      </div>

      <!-- Comment List -->
      <div v-if="loading" class="text-center py-8 text-zinc-400">加载中...</div>
      <div v-else-if="comments.length > 0" class="bg-white rounded-xl shadow-sm border border-zinc-100">
        <div class="px-5 py-4 border-b border-zinc-100">
          <h5 class="font-semibold text-sm">评论列表</h5>
        </div>
        <div class="divide-y">
          <div v-for="comment in comments" :key="comment.index_id" class="p-5" :class="comment.top ? 'border-l-4 border-l-amber-400' : ''">
            <div v-if="comment.top" class="mb-2">
              <span class="inline-flex items-center gap-1 text-xs bg-amber-100 text-amber-800 px-2 py-0.5 rounded-full">
                <Pin :size="10" /> 置顶
              </span>
            </div>
            <div class="flex items-center justify-between mb-2">
              <div>
                <router-link v-if="comment.author?.id" :to="{ name: 'user-profile', params: { userId: comment.author.id } }" class="font-semibold text-sm hover:text-emerald-600 transition-colors">
                  {{ comment.author?.nickname || comment.author?.username }}
                </router-link>
                <span v-else class="font-semibold text-sm">{{ comment.author?.nickname || comment.author?.username }}</span>
                <span class="text-zinc-400 text-xs ml-2">
                  {{ formatDate(comment.create_time) }}
                  <template v-if="comment.create_time !== comment.update_time"> | 编辑于 {{ formatDate(comment.update_time) }}</template>
                </span>
                <span v-if="comment.is_hidden" class="ml-2 align-middle text-xs bg-amber-100 text-amber-700 border border-amber-200 px-2 py-0.5 rounded-full">已隐藏</span>
              </div>
              <div v-if="isAuthenticated && user?.id === comment.author?.id" class="flex gap-1">
                <button @click="handleToggleHide(comment)" :disabled="togglingHideId === comment.index_id" :title="comment.is_hidden ? '取消隐藏' : '隐藏'" class="p-1.5 text-zinc-500 hover:bg-zinc-100 rounded disabled:opacity-50">
                  <EyeOff v-if="!comment.is_hidden" :size="14" />
                  <Eye v-else :size="14" />
                </button>
                <router-link :to="{ name: 'comment-edit', params: { commentIndexId: comment.index_id } }" class="p-1.5 text-amber-600 hover:bg-amber-50 rounded">
                  <Edit :size="14" />
                </router-link>
                <router-link :to="{ name: 'comment-delete', params: { commentIndexId: comment.index_id } }" class="p-1.5 text-red-600 hover:bg-red-50 rounded">
                  <Trash2 :size="14" />
                </router-link>
              </div>
            </div>
            <div class="text-sm text-zinc-600">
              <MarkdownRenderer :content="comment.content || ''" />
            </div>
          </div>
        </div>
        <Pagination v-if="totalPages > 1" :current-page="currentPage" :total-pages="totalPages" @page-change="fetchComments" />
      </div>
      <div v-else class="bg-white rounded-xl shadow-sm border border-zinc-100 p-8 text-center text-zinc-400">
        <MessageSquare :size="32" class="mx-auto mb-2" />
        <p>暂无评论，快来发表第一条评论吧！</p>
      </div>

      <div class="mt-6">
        <router-link :to="{ name: 'article-detail', params: { indexId: articleIndexId } }" class="inline-flex items-center gap-1.5 text-sm px-4 py-2 border border-zinc-300 rounded-md hover:bg-zinc-50 transition-colors">
          <ArrowLeft :size="16" /> 返回文章
        </router-link>
      </div>
    </div>

    <!-- Sidebar -->
    <div class="bg-white rounded-xl shadow-sm border border-zinc-100 h-fit">
      <div class="px-4 py-3 border-b border-zinc-100"><h5 class="font-semibold text-sm">文章信息</h5></div>
      <div class="p-4">
        <h6 class="font-medium text-sm mb-1">{{ article?.title }}</h6>
        <p class="text-zinc-400 text-xs mb-3">
          作者：{{ article?.author_id?.nickname || article?.author_id?.username }}<br />
          发布于：{{ formatDate(article?.created_at) }}
        </p>
        <router-link :to="{ name: 'article-detail', params: { indexId: articleIndexId } }" class="inline-flex items-center gap-1 text-emerald-600 text-sm hover:underline">
          <Eye :size="14" /> 查看文章
        </router-link>
      </div>
    </div>
  </div>
</template>
