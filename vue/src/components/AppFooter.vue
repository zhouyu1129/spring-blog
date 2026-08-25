<script setup lang="ts">
import { Heart } from 'lucide-vue-next'
</script>

<template>
  <footer class="bg-zinc-900 text-zinc-400 text-center py-6 mt-8">
    <div class="container mx-auto px-4">
      <p class="flex items-center justify-center gap-1 text-sm">
        &copy; 2025 校园博客 &mdash;
        <Heart :size="14" class="text-red-500" />
        <router-link to="/about" class="hover:text-white transition-colors ml-1">联系我们</router-link>
      </p>
    </div>
  </footer>
</template>
