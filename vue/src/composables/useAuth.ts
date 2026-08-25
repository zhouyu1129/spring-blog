import { ref, computed } from 'vue'

interface User {
  id: string
  username: string
  email: string
  nickname: string
  student_number: string
  email_verified: boolean
  is_staff?: boolean
  is_admin?: boolean
  real_name?: string
  mobile?: string
  gender?: string
  date_joined?: string
  last_login?: string
}

// 刷新页面后从 localStorage 恢复登录用户（登录/注册成功时写入，登出时清除）
function loadStoredUser(): User | null {
  try {
    const raw = localStorage.getItem('user')
    return raw ? (JSON.parse(raw) as User) : null
  } catch {
    return null
  }
}

const user = ref<User | null>(loadStoredUser())
const isAuthenticated = computed(() => !!user.value)
// 员工/管理员（后台入口与管理操作的可见性控制，实际权限由后端校验）
const isStaff = computed(() => !!user.value?.is_staff)
const isAdmin = computed(() => !!user.value?.is_admin)

export function useAuth() {
  function setUser(userData: User | null) {
    user.value = userData
  }

  function getUser() {
    return user.value
  }

  return {
    user,
    isAuthenticated,
    isStaff,
    isAdmin,
    setUser,
    getUser,
  }
}
