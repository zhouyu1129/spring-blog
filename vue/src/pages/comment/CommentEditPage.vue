<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Edit, X, Save } from 'lucide-vue-next'
import { commentApi } from '@/api'
import { useMessages } from '@/composables/useMessages'

const route = useRoute()
const router = useRouter()
const { addMessage } = useMessages()
const commentIndexId = Number(route.params.commentIndexId)
const content = ref('')
const loading = ref(false)

async function handleSubmit() {
  if (!content.value.trim()) { addMessage('评论内容不能为空', 'error'); return }
  loading.value = true
  try {
    const res = await commentApi.update(commentIndexId, content.value)
    if (res.ok) {
      addMessage('评论修改成功', 'success')
      router.back()
    } else { addMessage('修改失败', 'error') }
  } catch (e) { addMessage('修改失败', 'error') }
  finally { loading.value = false }
}
</script>

<template>
  <div class="grid lg:grid-cols-4 gap-6">
    <div class="lg:col-span-3">
      <div class="bg-white rounded-xl shadow-sm border border-zinc-100">
        <div class="px-5 py-4 border-b border-zinc-100"><h4 class="font-semibold flex items-center gap-2"><Edit :size="18" /> 修改评论</h4></div>
        <div class="p-5">
          <div class="bg-amber-50 border border-amber-200 text-amber-800 px-4 py-3 rounded-md mb-4 text-sm">
            <strong>注意：</strong>修改评论会创建新版本，原版本将被保留。
          </div>
          <form @submit.prevent="handleSubmit">
            <label class="block text-sm font-medium mb-1.5">评论内容</label>
            <textarea v-model="content" rows="8" placeholder="请输入评论内容" class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-amber-500 mb-4"></textarea>
            <div class="flex justify-between">
              <button type="button" @click="router.back()" class="inline-flex items-center gap-1.5 bg-zinc-200 text-zinc-700 px-4 py-2 rounded-md hover:bg-zinc-300 transition-colors text-sm">
                <X :size="14" /> 取消
              </button>
              <button type="submit" :disabled="loading" class="inline-flex items-center gap-1.5 bg-emerald-600 text-white px-4 py-2 rounded-md hover:bg-emerald-500 transition-colors text-sm disabled:opacity-50">
                <Save :size="14" /> {{ loading ? '保存中...' : '保存修改' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </div>
</template>
