<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, MessageSquare, List, Pen, Trash2, User as UserIcon, EyeOff, Eye } from 'lucide-vue-next'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { articleApi } from '@/api'
import { useMessages } from '@/composables/useMessages'
import { renderMarkdownWithToc, buildTocHtml } from '@/lib/markdown'

const route = useRoute()
const router = useRouter()
const { addMessage } = useMessages()

const article = ref<any>(null)
const files = ref<any[]>([])
const images = ref<any[]>([])
const loading = ref(true)
const togglingHide = ref(false)

const indexId = Number(route.params.indexId)

// 文章正文与目录均由前端从原始 Markdown 渲染
const articleTocHtml = computed(() => buildTocHtml(renderMarkdownWithToc(article.value?.content || '').toc))

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function formatFileSize(bytes: number) {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// 隐藏/取消隐藏（作者或管理员，由后端返回的 can_hide 控制）
async function handleToggleHide() {
  if (!article.value) return
  togglingHide.value = true
  try {
    const res = article.value.is_hidden
      ? await articleApi.unhide(indexId)
      : await articleApi.hide(indexId)
    if (res.ok) {
      article.value.is_hidden = !article.value.is_hidden
      addMessage(article.value.is_hidden ? '文章已隐藏，仅作者和管理员可见' : '文章已取消隐藏', 'success')
    } else {
      addMessage(res.data?.message || '操作失败', 'error')
    }
  } catch (e) {
    addMessage('操作失败', 'error')
  } finally {
    togglingHide.value = false
  }
}

onMounted(async () => {
  try {
    const res = await articleApi.getDetail(indexId)
    if (res.ok) {
      article.value = res.data.article
      files.value = res.data.files || []
      images.value = res.data.images || []
    }
  } catch (e) {
    console.error('Failed to fetch article', e)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-if="loading" class="text-center py-16 text-zinc-400">加载中...</div>

  <div v-else-if="article" class="grid lg:grid-cols-4 gap-6">
    <div class="lg:col-span-3">
      <article>
        <header class="mb-6">
          <h1 class="text-3xl font-bold mb-3">
            {{ article.title }}
            <span v-if="article.is_hidden" class="align-middle ml-2 text-xs font-normal bg-amber-100 text-amber-700 border border-amber-200 px-2 py-1 rounded-full">已隐藏 · 仅作者和管理员可见</span>
          </h1>
          <p class="text-zinc-400 text-sm">
            发布于：{{ formatDate(article.created_at) }} |
            作者：{{ article.author_id?.nickname || article.author_id?.username }}
            <template v-if="article.created_at !== article.updated_at">
              | 最后更新于：{{ formatDate(article.updated_at) }}
            </template>
          </p>
          <div v-if="article.can_edit" class="flex gap-2 mt-3">
            <router-link :to="{ name: 'article-edit', params: { indexId } }" class="inline-flex items-center gap-1 text-sm px-3 py-1.5 border border-amber-300 text-amber-700 rounded-md hover:bg-amber-50 transition-colors">
              <Pen :size="14" /> 编辑
            </router-link>
            <button v-if="article.can_hide" @click="handleToggleHide" :disabled="togglingHide" class="inline-flex items-center gap-1 text-sm px-3 py-1.5 border border-zinc-300 text-zinc-600 rounded-md hover:bg-zinc-50 transition-colors disabled:opacity-50">
              <EyeOff v-if="!article.is_hidden" :size="14" />
              <Eye v-else :size="14" />
              {{ togglingHide ? '处理中...' : (article.is_hidden ? '取消隐藏' : '隐藏') }}
            </button>
            <router-link :to="{ name: 'article-delete', params: { indexId } }" class="inline-flex items-center gap-1 text-sm px-3 py-1.5 border border-red-300 text-red-700 rounded-md hover:bg-red-50 transition-colors">
              <Trash2 :size="14" /> 删除
            </router-link>
          </div>
        </header>

        <!-- TOC -->
        <div v-if="articleTocHtml" class="bg-white rounded-xl shadow-sm border border-zinc-100 p-5 mb-6">
          <h5 class="font-semibold mb-2 flex items-center gap-2"><List :size="16" /> 文章目录</h5>
          <div class="text-sm" v-html="articleTocHtml"></div>
        </div>

        <!-- Content -->
        <div class="bg-white rounded-xl shadow-sm border border-zinc-100 p-6">
          <MarkdownRenderer :content="article.content || ''" />
        </div>

        <!-- Attachments -->
        <div v-if="files.length > 0 || images.length > 0" class="mt-8">
          <h3 class="text-lg font-semibold mb-4">📎 文章附件</h3>
          <div v-if="images.length > 0" class="mb-4">
            <h5 class="font-medium mb-2">图片</h5>
            <div class="grid md:grid-cols-3 gap-4">
              <div v-for="image in images" :key="image.id" class="bg-white rounded-xl shadow-sm border border-zinc-100 overflow-hidden">
                <img :src="image.content?.url || image.content" :alt="image.title" class="w-full h-36 object-cover" />
                <div class="p-3">
                  <h6 class="text-sm font-medium">{{ image.title }}</h6>
                  <p class="text-zinc-400 text-xs">{{ formatDate(image.created_at) }}</p>
                </div>
              </div>
            </div>
          </div>
          <div v-if="files.length > 0">
            <h5 class="font-medium mb-2">文件附件</h5>
            <div class="bg-white rounded-xl shadow-sm border border-zinc-100 divide-y">
              <div v-for="file in files" :key="file.id" class="flex items-center justify-between px-4 py-3">
                <span class="text-sm">📄 {{ file.title }}</span>
                <div class="flex items-center gap-2">
                  <span class="text-xs bg-zinc-100 px-2 py-0.5 rounded-full">{{ formatFileSize(file.content?.size || 0) }}</span>
                  <a :href="file.content?.url || file.content" download class="text-emerald-600 text-sm hover:underline">下载</a>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Actions -->
        <div class="flex justify-between mt-8">
          <button @click="router.back()" class="inline-flex items-center gap-1.5 text-sm px-4 py-2 border border-zinc-300 rounded-md hover:bg-zinc-50 transition-colors">
            <ArrowLeft :size="16" /> 返回
          </button>
          <div class="flex gap-2">
            <router-link :to="{ name: 'comment-list', params: { articleIndexId: indexId } }" class="inline-flex items-center gap-1.5 text-sm px-4 py-2 border border-sky-300 text-sky-700 rounded-md hover:bg-sky-50 transition-colors">
              <MessageSquare :size="16" /> 查看评论
            </router-link>
            <router-link to="/article" class="inline-flex items-center gap-1.5 text-sm px-4 py-2 border border-zinc-300 rounded-md hover:bg-zinc-50 transition-colors">
              <List :size="16" /> 文章列表
            </router-link>
          </div>
        </div>
      </article>
    </div>

    <!-- Sidebar -->
    <div class="space-y-4">
      <div class="bg-white rounded-xl shadow-sm border border-zinc-100 p-5 text-center">
        <div class="w-16 h-16 bg-zinc-200 rounded-full mx-auto mb-3 flex items-center justify-center text-zinc-400">
          <UserIcon :size="28" />
        </div>
        <h5 class="font-semibold">
          <router-link v-if="article.author_id?.id" :to="{ name: 'user-profile', params: { userId: article.author_id.id } }" class="hover:text-emerald-600 transition-colors">
            {{ article.author_id?.nickname || article.author_id?.username }}
          </router-link>
        </h5>
        <router-link v-if="article.author_id?.id" :to="{ name: 'user-profile', params: { userId: article.author_id.id } }" class="text-emerald-600 text-sm hover:underline">查看主页</router-link>
      </div>

      <div class="bg-white rounded-xl shadow-sm border border-zinc-100">
        <div class="px-4 py-3 border-b border-zinc-100"><h5 class="font-semibold text-sm">文章信息</h5></div>
        <div class="divide-y">
          <div class="px-4 py-2.5 flex justify-between text-sm"><span class="text-zinc-500">发布时间</span><span class="bg-zinc-800 text-white text-xs px-2 py-0.5 rounded-full">{{ formatDate(article.created_at).split(' ')[0] }}</span></div>
          <div class="px-4 py-2.5 flex justify-between text-sm"><span class="text-zinc-500">最后更新</span><span class="bg-zinc-800 text-white text-xs px-2 py-0.5 rounded-full">{{ formatDate(article.updated_at).split(' ')[0] }}</span></div>
          <div class="px-4 py-2.5 flex justify-between text-sm"><span class="text-zinc-500">字数统计</span><span class="bg-zinc-800 text-white text-xs px-2 py-0.5 rounded-full">{{ (article.content || '').length }}</span></div>
        </div>
      </div>

      <div class="bg-white rounded-xl shadow-sm border border-zinc-100">
        <div class="px-4 py-3 border-b border-zinc-100"><h5 class="font-semibold text-sm">快速链接</h5></div>
        <div class="p-4 space-y-2">
          <router-link :to="{ name: 'comment-list', params: { articleIndexId: indexId } }" class="flex items-center gap-2 w-full text-sm border border-sky-200 text-sky-700 px-3 py-2 rounded-md hover:bg-sky-50 transition-colors">
            <MessageSquare :size="14" /> 查看评论
          </router-link>
          <router-link to="/article" class="flex items-center gap-2 w-full text-sm border border-zinc-200 px-3 py-2 rounded-md hover:bg-zinc-50 transition-colors">
            <List :size="14" /> 文章列表
          </router-link>
        </div>
      </div>
    </div>
  </div>

  <div v-else class="text-center py-16">
    <h1 class="text-6xl font-bold text-zinc-200 mb-4">410</h1>
    <h2 class="text-2xl font-semibold mb-4">文章已删除</h2>
    <p class="text-zinc-500 mb-6">抱歉，您访问的文章已被删除。</p>
    <router-link to="/article" class="bg-emerald-600 text-white px-6 py-2.5 rounded-lg hover:bg-emerald-500 transition-colors">浏览文章</router-link>
  </div>
</template>
