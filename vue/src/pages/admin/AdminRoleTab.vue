<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Plus, Pencil, Trash2, X, ShieldCheck } from 'lucide-vue-next'
import { adminApi } from '@/api'
import { useAuth } from '@/composables/useAuth'
import { useMessages } from '@/composables/useMessages'

const { isAdmin } = useAuth()
const { addMessage } = useMessages()

const roles = ref<any[]>([])
const permissions = ref<any[]>([])
const loading = ref(false)

// 创建/编辑角色弹窗
const showModal = ref(false)
const editingRole = ref<any>(null) // null = 创建模式
const form = ref({ role_name: '', description: '' })
const selectedPermissions = ref<string[]>([])

async function fetchRoles() {
  loading.value = true
  try {
    const res = await adminApi.listRoles()
    if (res.ok) {
      roles.value = res.data.object_list
    } else {
      addMessage(res.data?.message || '获取角色列表失败', 'error')
    }
  } finally {
    loading.value = false
  }
}

async function fetchPermissions() {
  const res = await adminApi.listPermissions()
  if (res.ok) {
    permissions.value = res.data.object_list
  }
}

// 负面权限（! 前缀）需要特殊样式提示
function isNegative(permName: string) {
  return permName.startsWith('!')
}

function openCreateModal() {
  editingRole.value = null
  form.value = { role_name: '', description: '' }
  selectedPermissions.value = []
  showModal.value = true
}

function openEditModal(role: any) {
  editingRole.value = role
  form.value = {
    role_name: role.role_name,
    description: role.description || '',
  }
  selectedPermissions.value = [...(role.permissions || [])]
  showModal.value = true
}

function togglePermission(permName: string) {
  const idx = selectedPermissions.value.indexOf(permName)
  if (idx >= 0) {
    selectedPermissions.value.splice(idx, 1)
  } else {
    selectedPermissions.value.push(permName)
  }
}

async function handleSave() {
  const data: Record<string, any> = {
    role_name: form.value.role_name,
    description: form.value.description || null,
    permissions: selectedPermissions.value,
  }
  const res = editingRole.value
    ? await adminApi.updateRole(editingRole.value.id, data)
    : await adminApi.createRole(data)
  if (res.ok) {
    showModal.value = false
    addMessage(editingRole.value ? '角色已更新' : '角色创建成功', 'success')
    fetchRoles()
  } else {
    addMessage(res.data?.message || '保存失败', 'error')
  }
}

async function handleDelete(role: any) {
  if (!confirm(`确定删除角色「${role.role_name}」？已分配该角色的用户将失去对应权限。`)) return
  const res = await adminApi.deleteRole(role.id)
  if (res.ok) {
    addMessage('角色已删除', 'success')
    fetchRoles()
  } else {
    addMessage(res.data?.message || '删除失败', 'error')
  }
}

onMounted(() => {
  fetchRoles()
  fetchPermissions()
})
</script>

<template>
  <div>
    <!-- 工具栏 -->
    <div class="flex flex-wrap items-center gap-3 mb-4">
      <div class="text-sm text-zinc-500">
        权限判定规则：<span class="text-zinc-700 font-medium">（拥有正面权限 且 无对应负面权限）或 管理员</span>
      </div>
      <button
        v-if="isAdmin"
        @click="openCreateModal"
        class="flex items-center gap-1 text-sm bg-emerald-600 text-white px-3 py-1.5 rounded-md hover:bg-emerald-500 transition-colors"
      >
        <Plus :size="16" /> 创建角色
      </button>
      <span v-else class="text-xs text-zinc-400">仅管理员可修改数据</span>
    </div>

    <!-- 角色卡片列表 -->
    <div v-if="loading" class="bg-white rounded-xl shadow-sm border border-zinc-100 p-8 text-center text-zinc-400">
      加载中...
    </div>
    <div v-else class="grid gap-4 md:grid-cols-2">
      <div
        v-for="role in roles"
        :key="role.id"
        class="bg-white rounded-xl shadow-sm border border-zinc-100 p-4"
      >
        <div class="flex items-start justify-between mb-2">
          <div class="min-w-0">
            <div class="flex items-center gap-2 flex-wrap">
              <span class="font-bold text-sm">{{ role.role_name }}</span>
              <span
                v-if="role.is_system"
                class="inline-flex items-center gap-0.5 text-xs bg-sky-100 text-sky-700 px-1.5 py-0.5 rounded-full"
                title="系统预置角色，不可删除、不可改名"
              >
                <ShieldCheck :size="11" /> 系统
              </span>
              <span class="text-xs text-zinc-400">{{ role.user_count }} 人拥有</span>
            </div>
            <p v-if="role.description" class="text-xs text-zinc-400 mt-0.5">{{ role.description }}</p>
          </div>
          <div v-if="isAdmin" class="flex gap-1 shrink-0">
            <button @click="openEditModal(role)" class="inline-flex items-center gap-1 text-sky-600 hover:text-sky-500 text-sm">
              <Pencil :size="14" /> 编辑
            </button>
            <button
              v-if="!role.is_system"
              @click="handleDelete(role)"
              class="inline-flex items-center gap-1 text-red-600 hover:text-red-500 text-sm"
            >
              <Trash2 :size="14" /> 删除
            </button>
          </div>
        </div>
        <div class="flex flex-wrap gap-1">
          <span v-if="!role.permissions?.length" class="text-xs text-zinc-300">无权限</span>
          <span
            v-for="perm in role.permissions"
            :key="perm"
            :class="isNegative(perm)
              ? 'bg-red-50 text-red-600 border border-red-200'
              : 'bg-emerald-50 text-emerald-700 border border-emerald-200'"
            class="text-xs px-1.5 py-0.5 rounded-full"
          >
            {{ perm }}
          </span>
        </div>
      </div>
    </div>

    <!-- 权限字典说明 -->
    <div class="bg-white rounded-xl shadow-sm border border-zinc-100 p-4 mt-4">
      <h3 class="text-sm font-bold mb-2">权限字典</h3>
      <div class="grid gap-1.5 sm:grid-cols-2 text-xs text-zinc-500">
        <div v-for="p in permissions" :key="p.perm_name" class="flex gap-1.5">
          <span class="font-mono shrink-0" :class="isNegative(p.perm_name) ? 'text-red-500' : 'text-emerald-600'">
            {{ p.perm_name }}
          </span>
          <span>{{ p.description }}</span>
        </div>
      </div>
    </div>

    <!-- 创建/编辑角色弹窗 -->
    <div v-if="showModal" class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" @click.self="showModal = false">
      <div class="bg-white rounded-xl shadow-xl w-full max-w-md p-6 max-h-[90vh] overflow-y-auto">
        <div class="flex items-center justify-between mb-4">
          <h3 class="font-bold text-lg">{{ editingRole ? '编辑角色：' + editingRole.role_name : '创建角色' }}</h3>
          <button @click="showModal = false" class="text-zinc-400 hover:text-zinc-600"><X :size="18" /></button>
        </div>
        <form @submit.prevent="handleSave" class="space-y-3">
          <div>
            <input
              v-model="form.role_name"
              required
              :disabled="editingRole?.is_system"
              placeholder="角色名（2-64 位，小写字母开头）"
              class="w-full border border-zinc-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:border-emerald-500 disabled:bg-zinc-50 disabled:text-zinc-400"
            />
            <p v-if="editingRole?.is_system" class="text-xs text-zinc-400 mt-1">系统预置角色不可改名</p>
          </div>
          <input v-model="form.description" placeholder="描述（可选）" class="w-full border border-zinc-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:border-emerald-500" />
          <div>
            <p class="text-sm font-medium mb-1.5">权限（正面权限与负面权限可共存，负面权限以 ! 前缀表示禁止）</p>
            <div class="space-y-1.5 max-h-56 overflow-y-auto border border-zinc-200 rounded-md p-3">
              <label
                v-for="p in permissions"
                :key="p.perm_name"
                class="flex items-center gap-2 text-sm cursor-pointer"
              >
                <input
                  type="checkbox"
                  :checked="selectedPermissions.includes(p.perm_name)"
                  @change="togglePermission(p.perm_name)"
                  class="rounded"
                />
                <span class="font-mono text-xs shrink-0" :class="isNegative(p.perm_name) ? 'text-red-500' : 'text-emerald-600'">
                  {{ p.perm_name }}
                </span>
                <span class="text-zinc-400 text-xs">{{ p.description }}</span>
              </label>
            </div>
          </div>
          <div class="flex justify-end gap-2 pt-2">
            <button type="button" @click="showModal = false" class="px-4 py-2 text-sm rounded-md bg-zinc-100 hover:bg-zinc-200">取消</button>
            <button type="submit" class="px-4 py-2 text-sm rounded-md bg-emerald-600 text-white hover:bg-emerald-500">保存</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>
