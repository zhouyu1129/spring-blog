<script setup lang="ts">
import { Mail, MapPin, Phone, Github } from 'lucide-vue-next'
</script>

<template>
  <div>
    <h1 class="text-3xl font-bold mb-2">关于校园博客</h1>
    <p class="text-zinc-500 text-lg mb-8">分享知识，交流思想，共同成长</p>

    <div class="grid lg:grid-cols-3 gap-6 mb-8">
      <!-- Mission -->
      <div class="lg:col-span-2 bg-white rounded-xl shadow-md border border-zinc-100">
        <div class="bg-emerald-700 text-white px-6 py-4 rounded-t-xl">
          <h3 class="text-lg font-semibold">我们的使命</h3>
        </div>
        <div class="p-6">
          <p class="text-zinc-600 mb-4">校园博客是一个致力于为学生提供知识分享和交流的平台。我们相信每个人都有独特的见解和经验，通过分享和讨论，我们可以共同学习和成长。</p>
          <p class="text-zinc-600">我们的目标是创建一个开放、包容、积极向上的学习社区，鼓励学生表达自己的想法，分享学习心得，探讨学术问题。</p>
        </div>
      </div>

      <!-- Team -->
      <div class="bg-white rounded-xl shadow-md border border-zinc-100">
        <div class="bg-emerald-600 text-white px-6 py-4 rounded-t-xl">
          <h4 class="font-semibold">开发团队</h4>
        </div>
        <div class="p-6 text-center">
          <div class="w-24 h-24 bg-zinc-200 rounded-full mx-auto mb-3 flex items-center justify-center text-zinc-400">
            <span class="text-3xl">ZY</span>
          </div>
          <h5 class="font-semibold">周煜</h5>
          <p class="text-zinc-400 text-sm mb-2">项目负责人 & 主要开发者</p>
          <p class="text-zinc-600 text-sm mb-3">热爱编程和分享，致力于打造一个优质的校园交流平台。</p>
          <div class="flex justify-center gap-2">
            <a href="https://github.com/zhouyu1129" class="inline-flex items-center gap-1 text-xs px-3 py-1.5 border border-zinc-300 rounded-md hover:bg-zinc-50 transition-colors">
              <Github :size="14" /> GitHub
            </a>
            <a href="mailto:3123008706@mail2.gdut.edu.cn" class="inline-flex items-center gap-1 text-xs px-3 py-1.5 border border-zinc-300 rounded-md hover:bg-zinc-50 transition-colors">
              <Mail :size="14" /> 邮箱
            </a>
          </div>
        </div>
      </div>
    </div>

    <!-- Features -->
    <div class="bg-white rounded-xl shadow-md border border-zinc-100 mb-8">
      <div class="bg-sky-600 text-white px-6 py-4 rounded-t-xl">
        <h3 class="text-lg font-semibold">平台特色</h3>
      </div>
      <div class="p-6 grid md:grid-cols-2 gap-6">
        <div>
          <h5 class="font-semibold mb-1">📚 丰富的内容</h5>
          <p class="text-zinc-500 text-sm">涵盖学术研究、生活经验、技术分享等多个领域的文章。</p>
        </div>
        <div>
          <h5 class="font-semibold mb-1">👥 活跃的社区</h5>
          <p class="text-zinc-500 text-sm">来自不同专业背景的学生，带来多元化的观点和讨论。</p>
        </div>
        <div>
          <h5 class="font-semibold mb-1">💬 互动交流</h5>
          <p class="text-zinc-500 text-sm">评论、点赞、分享功能，促进思想的碰撞和交流。</p>
        </div>
        <div>
          <h5 class="font-semibold mb-1">🏆 优质内容</h5>
          <p class="text-zinc-500 text-sm">通过推荐和精选机制，确保高质量内容的传播。</p>
        </div>
      </div>
    </div>

    <!-- Contact -->
    <div class="grid lg:grid-cols-3 gap-6 mb-8">
      <div class="bg-white rounded-xl shadow-md border border-zinc-100">
        <div class="bg-amber-500 text-white px-6 py-4 rounded-t-xl">
          <h4 class="font-semibold">联系我们</h4>
        </div>
        <div class="p-6">
          <ul class="space-y-3 text-sm">
            <li class="flex items-center gap-2"><Mail :size="16" class="text-zinc-400" /> 3123008706@mail2.gdut.edu.cn</li>
            <li class="flex items-center gap-2"><MapPin :size="16" class="text-zinc-400" /> 中国·广东</li>
            <li class="flex items-center gap-2"><Phone :size="16" class="text-zinc-400" /> +86 134 3070 4474</li>
          </ul>
        </div>
      </div>

      <!-- Tech Stack -->
      <div class="lg:col-span-2 bg-white rounded-xl shadow-md border border-zinc-100">
        <div class="bg-zinc-700 text-white px-6 py-4 rounded-t-xl">
          <h3 class="text-lg font-semibold">技术栈</h3>
        </div>
        <div class="p-6 grid grid-cols-2 md:grid-cols-4 gap-4">
          <div class="text-center">
            <div class="text-4xl mb-2">☕</div>
            <h5 class="font-semibold">Java</h5>
            <p class="text-zinc-400 text-xs">后端开发语言</p>
          </div>
          <div class="text-center">
            <div class="text-4xl mb-2">🏹</div>
            <h5 class="font-semibold">Spring boot</h5>
            <p class="text-zinc-400 text-xs">Web框架</p>
          </div>
          <div class="text-center">
            <div class="text-4xl mb-2">💚</div>
            <h5 class="font-semibold">Vue</h5>
            <p class="text-zinc-400 text-xs">前端框架</p>
          </div>
          <div class="text-center">
            <div class="text-4xl mb-2">📄</div>
            <h5 class="font-semibold">PostgreSQL</h5>
            <p class="text-zinc-400 text-xs">数据库</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
