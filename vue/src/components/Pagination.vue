<script setup lang="ts">
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'

const props = defineProps<{
  currentPage: number
  totalPages: number
}>()

const emit = defineEmits<{
  'page-change': [page: number]
}>()

function goTo(page: number) {
  if (page >= 1 && page <= props.totalPages && page !== props.currentPage) {
    emit('page-change', page)
  }
}
</script>

<template>
  <nav v-if="totalPages > 1" class="flex justify-center mt-6">
    <ul class="flex items-center gap-1">
      <li>
        <button
          :disabled="currentPage <= 1"
          @click="goTo(1)"
          class="px-3 py-1.5 text-sm rounded-md bg-zinc-800 text-white hover:bg-zinc-700 disabled:opacity-40 disabled:cursor-not-allowed"
        >首页</button>
      </li>
      <li>
        <button
          :disabled="currentPage <= 1"
          @click="goTo(currentPage - 1)"
          class="px-2 py-1.5 text-sm rounded-md bg-zinc-800 text-white hover:bg-zinc-700 disabled:opacity-40 disabled:cursor-not-allowed"
        ><ChevronLeft :size="16" /></button>
      </li>
      <li>
        <span class="px-3 py-1.5 text-sm rounded-md bg-emerald-600 text-white">
          第 {{ currentPage }} 页 / 共 {{ totalPages }} 页
        </span>
      </li>
      <li>
        <button
          :disabled="currentPage >= totalPages"
          @click="goTo(currentPage + 1)"
          class="px-2 py-1.5 text-sm rounded-md bg-zinc-800 text-white hover:bg-zinc-700 disabled:opacity-40 disabled:cursor-not-allowed"
        ><ChevronRight :size="16" /></button>
      </li>
      <li>
        <button
          :disabled="currentPage >= totalPages"
          @click="goTo(totalPages)"
          class="px-3 py-1.5 text-sm rounded-md bg-zinc-800 text-white hover:bg-zinc-700 disabled:opacity-40 disabled:cursor-not-allowed"
        >末页</button>
      </li>
    </ul>
  </nav>
</template>
