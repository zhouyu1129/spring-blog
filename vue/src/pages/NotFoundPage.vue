<script setup lang="ts">
import { Home, BookOpen } from 'lucide-vue-next'
</script>

<template>
  <div class="text-center py-16">
    <h1 class="text-8xl font-bold text-zinc-200 mb-4">404</h1>
    <h2 class="text-2xl font-semibold mb-4">页面未找到</h2>
    <p class="text-zinc-500 mb-2">抱歉，您访问的页面不存在或已被移除。</p>
    <ul class="text-zinc-400 text-sm space-y-1 mb-8">
      <li>您输入的网址有误</li>
      <li>页面已被删除或移动</li>
      <li>链接已过期</li>
    </ul>
    <div class="flex justify-center gap-4">
      <router-link to="/" class="inline-flex items-center gap-2 bg-emerald-600 text-white px-6 py-2.5 rounded-lg hover:bg-emerald-500 transition-colors">
        <Home :size="18" /> 返回首页
      </router-link>
      <router-link to="/article" class="inline-flex items-center gap-2 bg-zinc-700 text-white px-6 py-2.5 rounded-lg hover:bg-zinc-600 transition-colors">
        <BookOpen :size="18" /> 浏览文章
      </router-link>
    </div>
  </div>
</template>
