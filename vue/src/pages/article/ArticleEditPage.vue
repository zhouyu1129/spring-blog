<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Save, X, Upload, Trash2, Copy } from 'lucide-vue-next'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { articleApi } from '@/api'
import { useMessages } from '@/composables/useMessages'
import { renderMarkdown } from '@/lib/markdown'

const route = useRoute()
const router = useRouter()
const { addMessage } = useMessages()
const indexId = Number(route.params.indexId)

const title = ref('')
const content = ref('')
const previewHtml = ref('')
const article = ref<any>(null)
const existingImages = ref<any[]>([])
const existingFiles = ref<any[]>([])
const keepImages = ref<string[]>([])
const keepFiles = ref<string[]>([])
const uploadedImages = ref<Array<{ id: number; name: string; url: string; file: File }>>([])
const nextImageId = ref(1)
const uploadedFiles = ref<Array<{ file_id: string; filename: string; file_size: number }>>([])
const loading = ref(false)

// 预览时把 [[img_id=N]] 占位符替换为本地图片，模拟后端提交后转换成的标准 Markdown 语法
function buildPreviewSource(val: string): string {
  return val.replace(/\[\[img_id=(\d+)\]\]/g, (_, id) => {
    const img = uploadedImages.value.find(i => i.id === Number(id))
    return img ? `![${img.name}](${img.url})` : ''
  })
}

// 预览渲染防抖：避免每次按键/点击引用都同步渲染 Markdown 导致界面卡顿
let previewTimer: ReturnType<typeof setTimeout> | undefined
watch(content, (val) => {
  if (previewTimer) clearTimeout(previewTimer)
  previewTimer = setTimeout(() => {
    previewHtml.value = renderMarkdown(buildPreviewSource(val))
  }, 300)
})

function insertImageRef(imageId: number) { content.value += `[[img_id=${imageId}]]` }
function removeImage(imageId: number) {
  const target = uploadedImages.value.find(img => img.id === imageId)
  if (target) URL.revokeObjectURL(target.url)
  uploadedImages.value = uploadedImages.value.filter(img => img.id !== imageId)
}

function handleImageSelect(event: Event) {
  const input = event.target as HTMLInputElement
  if (!input.files) return
  Array.from(input.files).forEach(file => {
    if (!file.type.startsWith('image/')) return
    const imageId = nextImageId.value++
    // 用 object URL 代替 base64 dataURL：URL 很短，预览渲染不会被兆级 base64 卡死；且同步可用
    uploadedImages.value.push({ id: imageId, name: file.name, url: URL.createObjectURL(file), file })
  })
  input.value = ''
}

async function handleFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  if (!input.files) return
  for (const file of Array.from(input.files)) {
    try {
      const res = await articleApi.uploadFile(file)
      if (res.data.success) {
        uploadedFiles.value.push({ file_id: res.data.file_id, filename: res.data.filename, file_size: res.data.file_size })
      }
    } catch (e) { addMessage('文件上传失败', 'error') }
  }
  input.value = ''
}

async function removeFile(fileId: string) {
  try {
    await articleApi.deleteTempFile(fileId)
    uploadedFiles.value = uploadedFiles.value.filter(f => f.file_id !== fileId)
  } catch (e) { addMessage('删除文件失败', 'error') }
}

function formatFileSize(bytes: number) {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

async function handleSubmit() {
  if (!title.value.trim() || !content.value.trim()) {
    addMessage('标题和内容不能为空', 'error')
    return
  }
  loading.value = true
  try {
    const formData = new FormData()
    formData.append('title', title.value)
    formData.append('content', content.value)

    const referencedImgIds = new Set<number>()
    const imgIdRegex = /\[\[img_id=(\d+)\]\]/g
    let match
    while ((match = imgIdRegex.exec(content.value)) !== null) referencedImgIds.add(Number(match[1]))
    const imageIdMapping: number[] = []
    uploadedImages.value.forEach(img => {
      if (referencedImgIds.has(img.id)) {
        formData.append('images', img.file)
        imageIdMapping.push(img.id)
      }
    })
    formData.append('image_id_mapping', JSON.stringify(imageIdMapping))

    keepImages.value.forEach(id => formData.append('keep_images', id))
    keepFiles.value.forEach(id => formData.append('keep_files', id))
    uploadedFiles.value.forEach(f => formData.append('selected_files', f.file_id))

    const res = await articleApi.update(indexId, formData)
    if (res.ok) {
      addMessage('文章修改成功！', 'success')
      router.push({ name: 'article-detail', params: { indexId } })
    } else {
      addMessage('修改失败', 'error')
    }
  } catch (e) {
    addMessage('修改失败', 'error')
  } finally {
    loading.value = false
  }
}

// 按正文引用同步"已有图片"勾选：正文引用了图片 URL（标准 Markdown）才保留关联，
// 删除正文中的引用时自动取消勾选，避免提交时把已不再引用的图片一并保留
function syncKeepImagesByContent(val: string) {
  keepImages.value = existingImages.value
    .filter((img: any) => img.content?.url && val.includes(img.content.url))
    .map((img: any) => String(img.id))
}

watch(content, (val) => {
  syncKeepImagesByContent(val)
})

onMounted(async () => {
  try {
    const res = await articleApi.getDetail(indexId)
    if (res.ok) {
      article.value = res.data.article
      title.value = res.data.article?.title || ''
      content.value = res.data.article?.content || ''
      existingImages.value = res.data.images || []
      existingFiles.value = res.data.files || []
      nextImageId.value = Math.max(nextImageId.value, existingImages.value.length + 1)
      // 初始勾选按正文实际引用（而非全选）
      syncKeepImagesByContent(content.value)
      keepFiles.value = existingFiles.value.map((f: any) => String(f.id))
    }
    const tempRes = await articleApi.getTempFiles()
    if (tempRes.data.success) uploadedFiles.value = tempRes.data.files || []
  } catch (e) { console.error(e) }
})

onUnmounted(() => {
  if (previewTimer) clearTimeout(previewTimer)
  uploadedImages.value.forEach(img => URL.revokeObjectURL(img.url))
})
</script>

<template>
  <div>
    <div class="bg-white rounded-xl shadow-md border border-zinc-100">
      <div class="bg-amber-500 text-white px-6 py-4 rounded-t-xl">
        <h2 class="text-xl font-bold">修改文章</h2>
      </div>
      <div class="p-6">
        <form @submit.prevent="handleSubmit">
          <div class="mb-4">
            <label class="block text-sm font-medium mb-1.5">文章标题</label>
            <input v-model="title" type="text" class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-amber-500" />
          </div>
          <div class="mb-4">
            <label class="block text-sm font-medium mb-1.5">文章内容 (支持Markdown)</label>
            <div class="grid md:grid-cols-2 gap-4">
              <textarea v-model="content" rows="15" class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-amber-500 resize-y font-mono"></textarea>
              <div class="border border-zinc-200 rounded-md p-4 min-h-[300px] max-h-[600px] overflow-y-auto bg-zinc-50">
                <div v-if="previewHtml" class="markdown-body text-sm" v-html="previewHtml"></div>
                <p v-else class="text-zinc-300 text-sm">预览区域</p>
              </div>
            </div>
          </div>

          <!-- Existing images -->
          <div v-if="existingImages.length > 0" class="mb-4">
            <label class="block text-sm font-medium mb-1.5">已有图片</label>
            <div class="grid grid-cols-4 gap-3">
              <div v-for="(image, idx) in existingImages" :key="image.id" class="border border-zinc-200 rounded-lg overflow-hidden bg-white"
                   :class="{ 'opacity-50': !keepImages.includes(String(image.id)) }">
                <img :src="image.content?.url || image.content" class="w-full h-28 object-cover" />
                <div class="p-2">
                  <div class="flex items-center justify-between">
                    <span class="text-xs text-zinc-500">图片 {{ idx + 1 }}</span>
                    <span v-if="keepImages.includes(String(image.id))" class="text-xs bg-emerald-100 text-emerald-700 px-1.5 py-0.5 rounded">正文中已引用</span>
                    <span v-else class="text-xs bg-zinc-100 text-zinc-400 px-1.5 py-0.5 rounded">未引用，保存后解除关联</span>
                  </div>
                </div>
              </div>
            </div>
            <p class="text-xs text-zinc-400 mt-1">图片是否保留由正文引用决定：删除正文中的图片引用后，保存时将解除关联（图片文件和历史版本不受影响）</p>
          </div>

          <!-- New image upload -->
          <div class="mb-4">
            <label class="block text-sm font-medium mb-1.5">上传新图片</label>
            <input type="file" multiple accept="image/*" @change="handleImageSelect" class="text-sm" />
            <div v-if="uploadedImages.length > 0" class="grid grid-cols-3 gap-3 mt-3">
              <div v-for="img in uploadedImages" :key="img.id" class="border border-zinc-200 rounded-lg overflow-hidden bg-white">
                <img :src="img.url" class="w-full h-28 object-cover" />
                <div class="p-2 flex items-center justify-between">
                  <span class="text-xs bg-emerald-100 text-emerald-700 px-1.5 py-0.5 rounded">ID: {{ img.id }}</span>
                  <div class="flex gap-1">
                    <button type="button" @click="insertImageRef(img.id)" class="text-xs p-1 text-sky-600 hover:bg-sky-50 rounded"><Copy :size="12" /></button>
                    <button type="button" @click="removeImage(img.id)" class="text-xs p-1 text-red-600 hover:bg-red-50 rounded"><Trash2 :size="12" /></button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- File upload -->
          <div class="mb-6">
            <label class="block text-sm font-medium mb-1.5">上传文件附件</label>
            <input type="file" multiple @change="handleFileSelect" class="text-sm" />
            <div v-if="uploadedFiles.length > 0" class="mt-3 divide-y border border-zinc-200 rounded-lg">
              <div v-for="f in uploadedFiles" :key="f.file_id" class="flex items-center justify-between px-3 py-2">
                <div class="text-sm">📄 {{ f.filename }} <span class="text-zinc-400 text-xs">({{ formatFileSize(f.file_size) }})</span></div>
                <button type="button" @click="removeFile(f.file_id)" class="text-red-500 hover:text-red-700"><Trash2 :size="14" /></button>
              </div>
            </div>
          </div>

          <div class="flex gap-3">
            <button type="submit" :disabled="loading" class="inline-flex items-center gap-1.5 bg-amber-500 text-white px-5 py-2.5 rounded-lg hover:bg-amber-400 transition-colors disabled:opacity-50">
              <Save :size="16" /> {{ loading ? '保存中...' : '保存修改' }}
            </button>
            <router-link :to="{ name: 'article-detail', params: { indexId } }" class="inline-flex items-center gap-1.5 bg-zinc-200 text-zinc-700 px-5 py-2.5 rounded-lg hover:bg-zinc-300 transition-colors">
              <X :size="16" /> 取消
            </router-link>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>
