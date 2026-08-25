<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { UserPlus, Pencil, Trash2, X, Search } from 'lucide-vue-next'
import Pagination from '@/components/Pagination.vue'
import { adminApi } from '@/api'
import { useAuth } from '@/composables/useAuth'
import { useMessages } from '@/composables/useMessages'

const { user: currentUser, isAdmin } = useAuth()
const { addMessage } = useMessages()

const users = ref<any[]>([])
const page = ref(1)
const totalPages = ref(1)
const search = ref('')
const loading = ref(false)

// 角色列表（编辑弹窗中分配角色用）
const allRoles = ref<any[]>([])

// 创建用户弹窗
const showCreateModal = ref(false)
const createForm = ref({
  username: '', email: '', student_number: '', password: '', nickname: '',
})

// 编辑用户弹窗
const showEditModal = ref(false)
const editingUser = ref<any>(null)
const editForm = ref({
  username: '', nickname: '', email: '', student_number: '', password: '',
  is_staff: false, is_admin: false, is_enabled: true, email_verified: false,
})
// 已勾选角色：role_name → 有效期（'' 表示永久，格式 yyyy-MM-ddTHH:mm 本地时间）
const editRoleExpiry = ref<Record<string, string>>({})

async function fetchUsers() {
  loading.value = true
  try {
    const res = await adminApi.listUsers({
      search: search.value || undefined,
      page: page.value,
    })
    if (res.ok) {
      users.value = res.data.page_obj.object_list
      totalPages.value = res.data.page_obj.paginator.num_pages
      page.value = res.data.page_obj.number
    } else {
      addMessage(res.data?.message || '获取用户列表失败', 'error')
    }
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchUsers()
}

function handlePageChange(newPage: number) {
  page.value = newPage
  fetchUsers()
}

// 当前登录用户不能对自己执行危险操作（后端同样校验）
function isSelf(u: any) {
  return u.id === currentUser.value?.id
}

function openCreateModal() {
  createForm.value = { username: '', email: '', student_number: '', password: '', nickname: '' }
  showCreateModal.value = true
}

async function handleCreate() {
  const data: Record<string, any> = {
    username: createForm.value.username,
    email: createForm.value.email,
    student_number: createForm.value.student_number,
    password: createForm.value.password,
  }
  if (createForm.value.nickname) data.nickname = createForm.value.nickname
  const res = await adminApi.createUser(data)
  if (res.ok) {
    showCreateModal.value = false
    addMessage('用户创建成功', 'success')
    fetchUsers()
  } else {
    addMessage(res.data?.message || '创建失败', 'error')
  }
}

function openEditModal(u: any) {
  editingUser.value = u
  editForm.value = {
    username: u.username,
    nickname: u.nickname || '',
    email: u.email,
    student_number: u.student_number,
    password: '',
    is_staff: !!u.is_staff,
    is_admin: !!u.is_admin,
    is_enabled: u.is_enabled !== false,
    email_verified: !!u.email_verified,
  }
  // 回显现有角色及其有效期（expires_at 形如 2030-12-31T23:59:59，截断秒）
  editRoleExpiry.value = {}
  for (const r of u.roles || []) {
    editRoleExpiry.value[r.role_name] = r.expires_at ? r.expires_at.slice(0, 16) : ''
  }
  showEditModal.value = true
}

/** 角色勾选状态切换（勾选时默认永久） */
function toggleRole(roleName: string) {
  if (roleName in editRoleExpiry.value) {
    delete editRoleExpiry.value[roleName]
  } else {
    editRoleExpiry.value[roleName] = ''
  }
}

/** 角色是否已过期（用于列表徽章提示） */
function isExpired(expiresAt: string | null) {
  return !!expiresAt && new Date(expiresAt) < new Date()
}

async function handleEdit() {
  const target = editingUser.value
  const data: Record<string, any> = {
    username: editForm.value.username,
    nickname: editForm.value.nickname || null,
    email: editForm.value.email,
    student_number: editForm.value.student_number,
    is_staff: editForm.value.is_staff,
    is_admin: editForm.value.is_admin,
    is_enabled: editForm.value.is_enabled,
    email_verified: editForm.value.email_verified,
    // 角色整体替换：勾选的角色各带有效期（datetime-local 值即 ISO-8601 本地时间，空为永久）
    roles: Object.entries(editRoleExpiry.value).map(([roleName, expiry]) => ({
      role_name: roleName,
      expires_at: expiry || null,
    })),
  }
  if (editForm.value.password) data.password = editForm.value.password
  const res = await adminApi.updateUser(target.id, data)
  if (res.ok) {
    showEditModal.value = false
    addMessage('用户信息已更新', 'success')
    fetchUsers()
  } else {
    addMessage(res.data?.message || '更新失败', 'error')
  }
}

async function handleDelete(u: any) {
  if (!confirm(`确定删除用户「${u.username}」？其全部文章与评论将一并删除，不可恢复。`)) return
  const res = await adminApi.deleteUser(u.id)
  if (res.ok) {
    addMessage('用户已删除', 'success')
    fetchUsers()
  } else {
    addMessage(res.data?.message || '删除失败', 'error')
  }
}

onMounted(() => {
  fetchUsers()
  // 角色列表供编辑弹窗勾选（staff 也可查看）
  adminApi.listRoles().then((res) => {
    if (res.ok) allRoles.value = res.data.object_list
  })
})
</script>

<template>
  <div>
    <!-- 工具栏 -->
    <div class="flex flex-wrap items-center gap-3 mb-4">
      <form @submit.prevent="handleSearch" class="flex">
        <input
          v-model="search"
          type="search"
          placeholder="搜索用户名/昵称/邮箱/学号..."
          class="bg-white text-sm px-3 py-1.5 rounded-l-md border border-zinc-300 focus:outline-none focus:border-emerald-500 w-64"
        />
        <button type="submit" class="bg-zinc-700 text-white px-3 py-1.5 rounded-r-md hover:bg-zinc-600 transition-colors">
          <Search :size="16" />
        </button>
      </form>
      <button
        v-if="isAdmin"
        @click="openCreateModal"
        class="flex items-center gap-1 text-sm bg-emerald-600 text-white px-3 py-1.5 rounded-md hover:bg-emerald-500 transition-colors"
      >
        <UserPlus :size="16" /> 创建用户
      </button>
      <span v-else class="text-xs text-zinc-400">仅管理员可修改数据</span>
    </div>

    <!-- 用户表格 -->
    <div class="bg-white rounded-xl shadow-sm border border-zinc-100 overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
          <tr class="bg-zinc-50 text-left text-zinc-500">
            <th class="px-4 py-3 font-medium">用户名</th>
            <th class="px-4 py-3 font-medium">昵称</th>
            <th class="px-4 py-3 font-medium">邮箱</th>
            <th class="px-4 py-3 font-medium">学号</th>
            <th class="px-4 py-3 font-medium">身份</th>
            <th class="px-4 py-3 font-medium">角色</th>
            <th class="px-4 py-3 font-medium">状态</th>
            <th v-if="isAdmin" class="px-4 py-3 font-medium text-right">操作</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-zinc-100">
          <tr v-if="loading">
            <td colspan="8" class="px-4 py-8 text-center text-zinc-400">加载中...</td>
          </tr>
          <tr v-else-if="users.length === 0">
            <td colspan="8" class="px-4 py-8 text-center text-zinc-400">暂无用户</td>
          </tr>
          <tr v-for="u in users" :key="u.id" class="hover:bg-zinc-50">
            <td class="px-4 py-3 font-medium">{{ u.username }}</td>
            <td class="px-4 py-3">{{ u.nickname || '-' }}</td>
            <td class="px-4 py-3 text-zinc-500">{{ u.email }}</td>
            <td class="px-4 py-3 text-zinc-500">{{ u.student_number }}</td>
            <td class="px-4 py-3">
              <span v-if="u.is_admin" class="text-xs bg-red-100 text-red-700 px-1.5 py-0.5 rounded-full">管理员</span>
              <span v-if="u.is_staff" class="text-xs bg-sky-100 text-sky-700 px-1.5 py-0.5 rounded-full">员工</span>
              <span v-if="!u.is_admin && !u.is_staff" class="text-xs bg-zinc-100 text-zinc-500 px-1.5 py-0.5 rounded-full">用户</span>
            </td>
            <td class="px-4 py-3">
              <div class="flex flex-wrap gap-1 max-w-48">
                <span v-if="!u.roles?.length" class="text-xs text-zinc-300">无</span>
                <span
                  v-for="r in u.roles"
                  :key="r.role_name"
                  :class="isExpired(r.expires_at)
                    ? 'bg-zinc-100 text-zinc-400 line-through'
                    : r.role_name === 'muted' ? 'bg-red-100 text-red-600' : 'bg-emerald-100 text-emerald-700'"
                  class="text-xs px-1.5 py-0.5 rounded-full"
                  :title="r.expires_at ? '有效期至 ' + r.expires_at.replace('T', ' ') : '永久'"
                >
                  {{ r.role_name }}
                </span>
              </div>
            </td>
            <td class="px-4 py-3">
              <span :class="u.is_enabled !== false ? 'text-emerald-600' : 'text-red-500'">
                {{ u.is_enabled !== false ? '正常' : '已禁用' }}
              </span>
            </td>
            <td v-if="isAdmin" class="px-4 py-3 text-right space-x-1 whitespace-nowrap">
              <button @click="openEditModal(u)" class="inline-flex items-center gap-1 text-sky-600 hover:text-sky-500">
                <Pencil :size="14" /> 编辑
              </button>
              <button
                v-if="!isSelf(u)"
                @click="handleDelete(u)"
                class="inline-flex items-center gap-1 text-red-600 hover:text-red-500"
              >
                <Trash2 :size="14" /> 删除
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <Pagination :current-page="page" :total-pages="totalPages" @page-change="handlePageChange" />

    <!-- 创建用户弹窗 -->
    <div v-if="showCreateModal" class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" @click.self="showCreateModal = false">
      <div class="bg-white rounded-xl shadow-xl w-full max-w-md p-6">
        <div class="flex items-center justify-between mb-4">
          <h3 class="font-bold text-lg">创建用户</h3>
          <button @click="showCreateModal = false" class="text-zinc-400 hover:text-zinc-600"><X :size="18" /></button>
        </div>
        <form @submit.prevent="handleCreate" class="space-y-3">
          <input v-model="createForm.username" required placeholder="用户名（3-40 位字母数字下划线连字符）" class="w-full border border-zinc-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:border-emerald-500" />
          <input v-model="createForm.email" required type="email" placeholder="邮箱" class="w-full border border-zinc-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:border-emerald-500" />
          <input v-model="createForm.student_number" required placeholder="学号（10 位数字）" class="w-full border border-zinc-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:border-emerald-500" />
          <input v-model="createForm.password" required type="password" placeholder="密码（至少 6 位）" class="w-full border border-zinc-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:border-emerald-500" />
          <input v-model="createForm.nickname" placeholder="昵称（可选，默认同用户名）" class="w-full border border-zinc-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:border-emerald-500" />
          <div class="flex justify-end gap-2 pt-2">
            <button type="button" @click="showCreateModal = false" class="px-4 py-2 text-sm rounded-md bg-zinc-100 hover:bg-zinc-200">取消</button>
            <button type="submit" class="px-4 py-2 text-sm rounded-md bg-emerald-600 text-white hover:bg-emerald-500">创建</button>
          </div>
        </form>
      </div>
    </div>

    <!-- 编辑用户弹窗 -->
    <div v-if="showEditModal" class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" @click.self="showEditModal = false">
      <div class="bg-white rounded-xl shadow-xl w-full max-w-md p-6 max-h-[90vh] overflow-y-auto">
        <div class="flex items-center justify-between mb-4">
          <h3 class="font-bold text-lg">编辑用户：{{ editingUser?.username }}</h3>
          <button @click="showEditModal = false" class="text-zinc-400 hover:text-zinc-600"><X :size="18" /></button>
        </div>
        <form @submit.prevent="handleEdit" class="space-y-3">
          <input v-model="editForm.username" required placeholder="用户名" class="w-full border border-zinc-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:border-emerald-500" />
          <input v-model="editForm.nickname" placeholder="昵称" class="w-full border border-zinc-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:border-emerald-500" />
          <input v-model="editForm.email" required type="email" placeholder="邮箱" class="w-full border border-zinc-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:border-emerald-500" />
          <input v-model="editForm.student_number" required placeholder="学号" class="w-full border border-zinc-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:border-emerald-500" />
          <input v-model="editForm.password" type="password" placeholder="新密码（留空则不修改）" class="w-full border border-zinc-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:border-emerald-500" />
          <div class="space-y-2 pt-1">
            <label class="flex items-center gap-2 text-sm"><input v-model="editForm.email_verified" type="checkbox" class="rounded"> 邮箱已验证</label>
            <label class="flex items-center gap-2 text-sm"><input v-model="editForm.is_staff" type="checkbox" class="rounded"> 员工（可查看后台）</label>
            <label class="flex items-center gap-2 text-sm">
              <input v-model="editForm.is_admin" type="checkbox" class="rounded" :disabled="isSelf(editingUser)"> 管理员
              <span v-if="isSelf(editingUser)" class="text-xs text-zinc-400">（不能修改自己的管理员身份）</span>
            </label>
            <label class="flex items-center gap-2 text-sm">
              <input v-model="editForm.is_enabled" type="checkbox" class="rounded" :disabled="isSelf(editingUser)"> 账号启用
              <span v-if="isSelf(editingUser)" class="text-xs text-zinc-400">（不能禁用自己的账号）</span>
            </label>
          </div>
          <!-- 角色分配：勾选角色 + 各自有效期（保存时整体替换该用户的角色） -->
          <div class="pt-1">
            <p class="text-sm font-medium mb-1.5">角色（可多选，有效期留空表示永久）</p>
            <div class="space-y-1.5 border border-zinc-200 rounded-md p-3">
              <div v-if="!allRoles.length" class="text-xs text-zinc-400">角色加载中...</div>
              <div v-for="r in allRoles" :key="r.id" class="flex items-center gap-2">
                <label class="flex items-center gap-2 text-sm cursor-pointer min-w-32">
                  <input
                    type="checkbox"
                    class="rounded"
                    :checked="r.role_name in editRoleExpiry"
                    @change="toggleRole(r.role_name)"
                  />
                  <span :class="r.role_name === 'muted' ? 'text-red-600' : ''">{{ r.role_name }}</span>
                  <span v-if="r.is_system" class="text-xs text-zinc-300">系统</span>
                </label>
                <input
                  v-if="r.role_name in editRoleExpiry"
                  v-model="editRoleExpiry[r.role_name]"
                  type="datetime-local"
                  class="flex-1 border border-zinc-300 rounded-md px-2 py-1 text-xs focus:outline-none focus:border-emerald-500"
                />
              </div>
            </div>
          </div>
          <div class="flex justify-end gap-2 pt-2">
            <button type="button" @click="showEditModal = false" class="px-4 py-2 text-sm rounded-md bg-zinc-100 hover:bg-zinc-200">取消</button>
            <button type="submit" class="px-4 py-2 text-sm rounded-md bg-emerald-600 text-white hover:bg-emerald-500">保存</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>
