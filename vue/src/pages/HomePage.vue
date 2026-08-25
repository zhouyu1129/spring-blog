<script setup lang="ts">
import { BookOpen, TrendingUp, Users } from 'lucide-vue-next'
</script>

<template>
  <div>
    <!-- Hero -->
    <div class="bg-gradient-to-br from-emerald-700 to-teal-800 text-white rounded-2xl p-8 md:p-12 mb-8 shadow-xl">
      <h1 class="text-3xl md:text-4xl font-bold mb-4">欢迎来到校园博客!</h1>
      <p class="text-lg text-emerald-100 mb-2">这是一个分享知识、交流思想的平台，我们欢迎每一位热爱学习和分享的同学。</p>
      <p class="text-emerald-200 mb-6">在这里，你可以发表自己的文章，阅读他人的见解，参与讨论，共同成长。</p>
      <router-link to="/about" class="inline-block bg-white text-emerald-800 font-semibold px-6 py-3 rounded-lg hover:bg-emerald-50 transition-colors">
        了解更多
      </router-link>
    </div>

    <!-- Feature Cards -->
    <div class="grid md:grid-cols-3 gap-6">
      <div class="bg-white rounded-xl shadow-md p-6 border border-zinc-100 hover:shadow-lg transition-shadow">
        <div class="w-12 h-12 bg-emerald-100 text-emerald-700 rounded-lg flex items-center justify-center mb-4">
          <BookOpen :size="24" />
        </div>
        <h3 class="text-lg font-semibold mb-2">最新文章</h3>
        <p class="text-zinc-500 text-sm mb-4">查看最新发布的博客文章，了解校园热点和学术动态。</p>
        <router-link to="/article" class="text-emerald-600 text-sm font-medium hover:underline">查看更多 &rarr;</router-link>
      </div>

      <div class="bg-white rounded-xl shadow-md p-6 border border-zinc-100 hover:shadow-lg transition-shadow">
        <div class="w-12 h-12 bg-sky-100 text-sky-700 rounded-lg flex items-center justify-center mb-4">
          <TrendingUp :size="24" />
        </div>
        <h3 class="text-lg font-semibold mb-2">热门话题</h3>
        <p class="text-zinc-500 text-sm mb-4">发现当前最受欢迎的话题，参与讨论，分享你的观点。</p>
        <router-link to="/article" class="text-sky-600 text-sm font-medium hover:underline">查看更多 &rarr;</router-link>
      </div>

      <div class="bg-white rounded-xl shadow-md p-6 border border-zinc-100 hover:shadow-lg transition-shadow">
        <div class="w-12 h-12 bg-amber-100 text-amber-700 rounded-lg flex items-center justify-center mb-4">
          <Users :size="24" />
        </div>
        <h3 class="text-lg font-semibold mb-2">作者推荐</h3>
        <p class="text-zinc-500 text-sm mb-4">认识校园内的优秀作者，关注他们的最新动态。</p>
        <router-link to="/article" class="text-amber-600 text-sm font-medium hover:underline">查看更多 &rarr;</router-link>
      </div>
    </div>
  </div>
</template>
