<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Send, X } from 'lucide-vue-next'
import { commentApi } from '@/api'
import { useMessages } from '@/composables/useMessages'

const route = useRoute()
const router = useRouter()
const { addMessage } = useMessages()
const articleIndexId = Number(route.params.articleIndexId)
const content = ref('')
const loading = ref(false)

async function handleSubmit() {
  if (!content.value.trim()) { addMessage('评论内容不能为空', 'error'); return }
  loading.value = true
  try {
    const res = await commentApi.create(articleIndexId, content.value)
    if (res.ok) {
      addMessage('评论发布成功', 'success')
      router.push({ name: 'comment-list', params: { articleIndexId } })
    } else { addMessage('评论发布失败', 'error') }
  } catch (e) { addMessage('评论发布失败', 'error') }
  finally { loading.value = false }
}
</script>

<template>
  <div class="grid lg:grid-cols-4 gap-6">
    <div class="lg:col-span-3">
      <div class="bg-white rounded-xl shadow-sm border border-zinc-100">
        <div class="px-5 py-4 border-b border-zinc-100"><h4 class="font-semibold flex items-center gap-2"><Send :size="18" /> 发表评论</h4></div>
        <div class="p-5">
          <form @submit.prevent="handleSubmit">
            <label class="block text-sm font-medium mb-1.5">评论内容</label>
            <textarea v-model="content" rows="8" placeholder="请输入评论内容（支持Markdown格式）" class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-emerald-500 mb-2"></textarea>
            <p class="text-xs text-zinc-400 mb-4">支持Markdown格式</p>
            <div class="flex justify-between">
              <router-link :to="{ name: 'comment-list', params: { articleIndexId } }" class="inline-flex items-center gap-1.5 bg-zinc-200 text-zinc-700 px-4 py-2 rounded-md hover:bg-zinc-300 transition-colors text-sm">
                <X :size="14" /> 取消
              </router-link>
              <button type="submit" :disabled="loading" class="inline-flex items-center gap-1.5 bg-emerald-600 text-white px-4 py-2 rounded-md hover:bg-emerald-500 transition-colors text-sm disabled:opacity-50">
                <Send :size="14" /> {{ loading ? '发布中...' : '发表评论' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </div>
</template>
