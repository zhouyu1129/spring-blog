<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { AlertTriangle, X, Trash2 } from 'lucide-vue-next'
import { commentApi } from '@/api'
import { useMessages } from '@/composables/useMessages'

const route = useRoute()
const router = useRouter()
const { addMessage } = useMessages()
const commentIndexId = Number(route.params.commentIndexId)
const loading = ref(false)

async function handleDelete() {
  loading.value = true
  try {
    const res = await commentApi.delete(commentIndexId)
    if (res.ok) {
      addMessage('评论已删除', 'success')
      router.back()
    } else { addMessage('删除失败', 'error') }
  } catch (e) { addMessage('删除失败', 'error') }
  finally { loading.value = false }
}
</script>

<template>
  <div class="max-w-2xl mx-auto mt-8">
    <div class="bg-white rounded-xl shadow-md border border-zinc-100">
      <div class="bg-red-600 text-white px-6 py-4 rounded-t-xl flex items-center gap-2">
        <AlertTriangle :size="20" /> <h4 class="font-semibold">确认删除评论</h4>
      </div>
      <div class="p-6">
        <div class="bg-amber-50 border border-amber-200 text-amber-800 px-4 py-3 rounded-md mb-6 text-sm">
          <strong>警告：</strong>删除后该评论将对所有人不可见（包括您自己），且无法在前端恢复！
        </div>
        <div class="flex justify-between">
          <button @click="router.back()" class="inline-flex items-center gap-1.5 bg-zinc-200 text-zinc-700 px-5 py-2.5 rounded-lg hover:bg-zinc-300 transition-colors">
            <X :size="16" /> 取消
          </button>
          <button @click="handleDelete" :disabled="loading" class="inline-flex items-center gap-1.5 bg-red-600 text-white px-5 py-2.5 rounded-lg hover:bg-red-500 transition-colors disabled:opacity-50">
            <Trash2 :size="16" /> {{ loading ? '删除中...' : '确认删除' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
