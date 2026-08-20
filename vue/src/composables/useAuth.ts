import { ref, computed } from 'vue'

interface User {
  id: string
  username: string
  email: string
  nickname: string
  student_number: string
  email_verified: boolean
  real_name?: string
  mobile?: string
  gender?: string
  date_joined?: string
  last_login?: string
}

const user = ref<User | null>(null)
const isAuthenticated = computed(() => !!user.value)

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
    setUser,
    getUser,
  }
}
