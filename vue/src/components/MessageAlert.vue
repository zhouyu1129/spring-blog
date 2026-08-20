<script setup lang="ts">
import { X } from 'lucide-vue-next'

interface Message {
  id: number
  text: string
  type: 'success' | 'error' | 'warning' | 'info'
}

defineProps<{
  messages: Message[]
}>()

defineEmits<{
  remove: [id: number]
}>()

const typeClasses: Record<string, string> = {
  success: 'bg-emerald-600 text-white',
  error: 'bg-red-600 text-white',
  warning: 'bg-amber-500 text-white',
  info: 'bg-sky-600 text-white',
}
</script>

<template>
  <div class="fixed top-4 right-4 z-50 space-y-2 max-w-sm">
    <div
      v-for="msg in messages"
      :key="msg.id"
      :class="[typeClasses[msg.type] || typeClasses.info]"
      class="flex items-center justify-between px-4 py-3 rounded-md shadow-lg"
    >
      <span class="text-sm">{{ msg.text }}</span>
      <button @click="$emit('remove', msg.id)" class="ml-3 hover:opacity-80">
        <X :size="16" />
      </button>
    </div>
  </div>
</template>
