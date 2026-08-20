<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Save, ArrowLeft, Upload, Trash2, Copy } from 'lucide-vue-next'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { articleApi } from '@/api'
import { useMessages } from '@/composables/useMessages'
import { renderMarkdown } from '@/lib/markdown'

const router = useRouter()
const { addMessage } = useMessages()

const title = ref('')
const content = ref('')
const previewHtml = ref('')
const uploadedImages = ref<Array<{ id: number; name: string; url: string; file: File }>>([])
const nextImageId = ref(1)
const uploadedFiles = ref<Array<{ file_id: string; filename: string; file_size: number; file_url: string }>>([])
const loading = ref(false)

// 预览时把 [[img_id=N]] 占位符替换为本地图片，模拟后端提交后转换成的标准 Markdown 语法
function buildPreviewSource(val: string): string {
  return val.replace(/\[\[img_id=(\d+)\]\]/g, (_, id) => {
    const img = uploadedImages.value.find(i => i.id === Number(id))
    return img ? `![${img.name}](${img.url})` : ''
  })
}

watch(content, (val) => {
  previewHtml.value = renderMarkdown(buildPreviewSource(val))
})

function insertImageRef(imageId: number) {
  const refText = `[[img_id=${imageId}]]`
  content.value += refText
}

function removeImage(imageId: number) {
  uploadedImages.value = uploadedImages.value.filter(img => img.id !== imageId)
}

function handleImageSelect(event: Event) {
  const input = event.target as HTMLInputElement
  if (!input.files) return
  Array.from(input.files).forEach(file => {
    if (!file.type.startsWith('image/')) return
    const reader = new FileReader()
    const imageId = nextImageId.value++
    reader.onload = (e) => {
      uploadedImages.value.push({ id: imageId, name: file.name, url: e.target?.result as string, file })
    }
    reader.readAsDataURL(file)
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
        uploadedFiles.value.push({
          file_id: res.data.file_id,
          filename: res.data.filename,
          file_size: res.data.file_size,
          file_url: res.data.file_url,
        })
      } else {
        addMessage(res.data.error || '文件上传失败', 'error')
      }
    } catch (e) {
      addMessage('文件上传失败', 'error')
    }
  }
  input.value = ''
}

async function removeFile(fileId: string) {
  try {
    await articleApi.deleteTempFile(fileId)
    uploadedFiles.value = uploadedFiles.value.filter(f => f.file_id !== fileId)
  } catch (e) {
    addMessage('删除文件失败', 'error')
  }
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

    // Add referenced images
    const referencedImgIds = new Set<number>()
    const imgIdRegex = /\[\[img_id=(\d+)\]\]/g
    let match
    while ((match = imgIdRegex.exec(content.value)) !== null) {
      referencedImgIds.add(Number(match[1]))
    }
    const imageIdMapping: number[] = []
    uploadedImages.value.forEach(img => {
      if (referencedImgIds.has(img.id)) {
        formData.append('images', img.file)
        imageIdMapping.push(img.id)
      }
    })
    formData.append('image_id_mapping', JSON.stringify(imageIdMapping))

    // Add selected files
    uploadedFiles.value.forEach(f => {
      formData.append('selected_files', f.file_id)
    })

    const res = await articleApi.create(formData)
    if (res.ok) {
      addMessage('文章创建成功！', 'success')
      router.push({ name: 'article-list' })
    } else {
      addMessage('创建失败', 'error')
    }
  } catch (e) {
    addMessage('创建失败', 'error')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    const res = await articleApi.getTempFiles()
    if (res.data.success) {
      uploadedFiles.value = res.data.files || []
    }
  } catch (e) { /* ignore */ }
})
</script>

<template>
  <div>
    <div class="bg-white rounded-xl shadow-md border border-zinc-100">
      <div class="bg-emerald-700 text-white px-6 py-4 rounded-t-xl">
        <h2 class="text-xl font-bold">创建新文章</h2>
      </div>
      <div class="p-6">
        <form @submit.prevent="handleSubmit">
          <!-- Title -->
          <div class="mb-4">
            <label class="block text-sm font-medium mb-1.5">文章标题</label>
            <input v-model="title" type="text" placeholder="请输入文章标题" class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-emerald-500" />
          </div>

          <!-- Content + Preview -->
          <div class="mb-4">
            <label class="block text-sm font-medium mb-1.5">文章内容 (支持Markdown)</label>
            <div class="grid md:grid-cols-2 gap-4">
              <div>
                <textarea v-model="content" rows="15" placeholder="请输入文章内容" class="w-full border border-zinc-300 rounded-md px-4 py-2.5 text-sm focus:outline-none focus:border-emerald-500 resize-y font-mono"></textarea>
                <p class="text-xs text-zinc-400 mt-1">字数: {{ content.replace(/\s/g, '').length }}</p>
              </div>
              <div>
                <label class="block text-sm font-medium mb-1.5 text-zinc-500">预览</label>
                <div class="border border-zinc-200 rounded-md p-4 min-h-[300px] max-h-[600px] overflow-y-auto bg-zinc-50">
                  <div v-if="previewHtml" class="markdown-body text-sm" v-html="previewHtml"></div>
                  <p v-else class="text-zinc-300 text-sm">预览区域</p>
                </div>
              </div>
            </div>
          </div>

          <!-- Image Upload -->
          <div class="mb-4">
            <label class="block text-sm font-medium mb-1.5">上传图片</label>
            <input type="file" multiple accept="image/*" @change="handleImageSelect" class="text-sm" />
            <p class="text-xs text-zinc-400 mt-1">支持上传多张图片，可以使用 [[img_id=id]] 在文章中引用图片</p>
            <div v-if="uploadedImages.length > 0" class="grid grid-cols-3 gap-3 mt-3">
              <div v-for="img in uploadedImages" :key="img.id" class="border border-zinc-200 rounded-lg overflow-hidden bg-white">
                <img :src="img.url" class="w-full h-28 object-cover" />
                <div class="p-2 flex items-center justify-between">
                  <span class="text-xs bg-emerald-100 text-emerald-700 px-1.5 py-0.5 rounded">ID: {{ img.id }}</span>
                  <div class="flex gap-1">
                    <button type="button" @click="insertImageRef(img.id)" class="text-xs p-1 text-sky-600 hover:bg-sky-50 rounded" title="引用"><Copy :size="12" /></button>
                    <button type="button" @click="removeImage(img.id)" class="text-xs p-1 text-red-600 hover:bg-red-50 rounded" title="删除"><Trash2 :size="12" /></button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- File Upload -->
          <div class="mb-6">
            <label class="block text-sm font-medium mb-1.5">上传文件附件</label>
            <input type="file" multiple @change="handleFileSelect" class="text-sm" />
            <p class="text-xs text-zinc-400 mt-1">支持上传多个文件，单个文件不超过100MB</p>
            <div v-if="uploadedFiles.length > 0" class="mt-3 divide-y border border-zinc-200 rounded-lg">
              <div v-for="f in uploadedFiles" :key="f.file_id" class="flex items-center justify-between px-3 py-2">
                <div class="text-sm">📄 {{ f.filename }} <span class="text-zinc-400 text-xs">({{ formatFileSize(f.file_size) }})</span></div>
                <button type="button" @click="removeFile(f.file_id)" class="text-red-500 hover:text-red-700"><Trash2 :size="14" /></button>
              </div>
            </div>
          </div>

          <!-- Actions -->
          <div class="flex gap-3">
            <button type="submit" :disabled="loading" class="inline-flex items-center gap-1.5 bg-emerald-600 text-white px-5 py-2.5 rounded-lg hover:bg-emerald-500 transition-colors disabled:opacity-50">
              <Save :size="16" /> {{ loading ? '提交中...' : '发布文章' }}
            </button>
            <router-link to="/article" class="inline-flex items-center gap-1.5 bg-zinc-200 text-zinc-700 px-5 py-2.5 rounded-lg hover:bg-zinc-300 transition-colors">
              <ArrowLeft :size="16" /> 返回列表
            </router-link>
          </div>
        </form>
      </div>
    </div>

    <!-- Markdown Tips -->
    <div class="bg-white rounded-xl shadow-sm border border-zinc-100 mt-6">
      <div class="px-6 py-3 border-b border-zinc-100"><h5 class="font-semibold text-sm">Markdown语法提示</h5></div>
      <div class="p-6 grid md:grid-cols-2 gap-6 text-sm">
        <div>
          <h6 class="font-medium mb-2">基础语法</h6>
          <ul class="space-y-1 text-zinc-500">
            <li><code class="bg-zinc-100 px-1 rounded text-xs"># 标题</code> - 一级标题</li>
            <li><code class="bg-zinc-100 px-1 rounded text-xs">## 标题</code> - 二级标题</li>
            <li><code class="bg-zinc-100 px-1 rounded text-xs">**粗体**</code> - <strong>粗体</strong></li>
            <li><code class="bg-zinc-100 px-1 rounded text-xs">*斜体*</code> - <em>斜体</em></li>
            <li><code class="bg-zinc-100 px-1 rounded text-xs">`代码`</code> - <code>代码</code></li>
            <li><code class="bg-zinc-100 px-1 rounded text-xs">[[img_id=1]]</code> - 引用上传的图片</li>
          </ul>
        </div>
        <div>
          <h6 class="font-medium mb-2">高级语法</h6>
          <ul class="space-y-1 text-zinc-500">
            <li><code class="bg-zinc-100 px-1 rounded text-xs">- 列表项</code> - 无序列表</li>
            <li><code class="bg-zinc-100 px-1 rounded text-xs">1. 列表项</code> - 有序列表</li>
            <li><code class="bg-zinc-100 px-1 rounded text-xs">&gt; 引用</code> - 引用块</li>
            <li><code class="bg-zinc-100 px-1 rounded text-xs">```代码块```</code> - 代码块</li>
            <li><code class="bg-zinc-100 px-1 rounded text-xs">---</code> - 分割线</li>
            <li><code class="bg-zinc-100 px-1 rounded text-xs">|表头|表头|</code> - 表格</li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</template>
