import { useAuth } from '@/composables/useAuth'

const API_BASE = '/api'

interface RequestOptions {
  method?: string
  headers?: Record<string, string>
  body?: any
  isFormData?: boolean
}

interface ApiResponse<T = any> {
  data: T
  ok: boolean
  status: number
}

let csrfTokenPromise: Promise<void> | null = null

/**
 * 从 Cookie 中读取 XSRF-TOKEN（Spring Security CookieCsrfTokenRepository 设置）
 */
function getCSRFToken(): string {
  const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : ''
}

/**
 * 访问 /api/csrf 端点，触发 Spring Security 设置 XSRF-TOKEN Cookie
 */
async function ensureCSRFToken(): Promise<void> {
  if (getCSRFToken()) return
  if (!csrfTokenPromise) {
    csrfTokenPromise = fetch('/api/csrf', {
      headers: { 'Accept': 'application/json' },
      credentials: 'include',
    }).then(() => {})
  }
  await csrfTokenPromise
}

export async function request<T = any>(url: string, options: RequestOptions = {}): Promise<ApiResponse<T>> {
  const { method = 'GET', headers = {}, body, isFormData = false } = options

  // 非 GET 请求前确保 CSRF Token 可用
  if (method !== 'GET') {
    await ensureCSRFToken()
  }

  const defaultHeaders: Record<string, string> = {
    'Accept': 'application/json',
    ...headers,
  }

  if (!isFormData) {
    defaultHeaders['Content-Type'] = 'application/json'
  }

  // 非 GET 请求携带 CSRF Token（Spring Security 使用 X-XSRF-TOKEN 头）
  if (method !== 'GET') {
    const token = getCSRFToken()
    if (token) {
      defaultHeaders['X-XSRF-TOKEN'] = token
    }
  }

  const config: RequestInit = {
    method,
    headers: defaultHeaders,
    credentials: 'include',
  }

  if (body !== undefined) {
    config.body = isFormData ? body : JSON.stringify(body)
  }

  const response = await fetch(`${API_BASE}${url}`, config)

  // 401 拦截：会话已过期（或未登录调用需登录接口）时清除本地登录状态，
  // 导航栏等依赖 useAuth 的组件会自动恢复为未登录显示。
  // 登录接口的 401 表示账号密码错误，不触发清除。
  if (response.status === 401 && !url.startsWith('/user/login')) {
    localStorage.removeItem('isAuthenticated')
    localStorage.removeItem('user')
    useAuth().setUser(null)
  }

  // Security 过滤器层返回的 401 可能没有 JSON body，解析失败时回退为 null
  let data: any = null
  try {
    data = await response.json()
  } catch {
    data = null
  }

  return {
    data,
    ok: response.ok,
    status: response.status,
  }
}

// Auth API
export const authApi = {
  login: (email: string, password: string) =>
    request('/user/login', { method: 'POST', body: { email, password } }),
  register: (data: { username: string; email: string; student_number: string; password: string; confirm_password: string }) =>
    request('/user/register', { method: 'POST', body: data }),
  logout: () =>
    request('/user/logout', { method: 'GET' }),
  getProfile: () =>
    request('/user/profile'),
  editProfile: (data: { nickname?: string; real_name?: string; mobile?: string; gender?: string }) =>
    request('/user/profile/edit', { method: 'POST', body: data }),
  changeEmail: (newEmail: string, verificationCode: string) =>
    request('/user/profile/change_email', { method: 'POST', body: { new_email: newEmail, verification_code: verificationCode } }),
  changePassword: (oldPassword: string, newPassword: string, confirmPassword: string) =>
    request('/user/profile/change_password', { method: 'POST', body: { old_password: oldPassword, new_password: newPassword, confirm_password: confirmPassword } }),
  sendEmailCode: () =>
    request('/user/profile/send_email_code', { method: 'POST' }),
  forgotPassword: (email: string, studentNumber: string) =>
    request('/user/forgot_password', { method: 'POST', body: { email, student_number: studentNumber } }),
  resetPassword: (token: string, newPassword: string) =>
    request('/user/reset_password', { method: 'POST', body: { token, new_password: newPassword } }),
  getUserProfile: (userId: string) =>
    request(`/user/user/${userId}`),
  resendVerification: () =>
    request('/user/resend_verification', { method: 'POST' }),
}

// Article API
export const articleApi = {
  getList: (params?: { search?: string; page?: number }) => {
    const query = new URLSearchParams()
    if (params?.search) query.set('search', params.search)
    if (params?.page) query.set('page', String(params.page))
    const qs = query.toString()
    return request(`/article/${qs ? '?' + qs : ''}`)
  },
  getDetail: (indexId: number) =>
    request(`/article/${indexId}/`),
  create: (data: FormData) =>
    request('/article/create/', { method: 'POST', body: data, isFormData: true }),
  update: (indexId: number, data: FormData) =>
    request(`/article/${indexId}/edit/`, { method: 'POST', body: data, isFormData: true }),
  delete: (indexId: number) =>
    request(`/article/${indexId}/delete/`, { method: 'POST' }),
  hide: (indexId: number) =>
    request(`/article/${indexId}/hide/`, { method: 'POST' }),
  unhide: (indexId: number) =>
    request(`/article/${indexId}/unhide/`, { method: 'POST' }),
  uploadFile: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return request('/article/upload-file/', { method: 'POST', body: formData, isFormData: true })
  },
  deleteTempFile: (fileId: string) =>
    request(`/article/delete-temp-file/${fileId}/`, { method: 'POST', body: { _method: 'DELETE' } }),
  getTempFiles: () =>
    request('/article/get-temp-files/'),
}

// Comment API
export const commentApi = {
  getList: (articleIndexId: number, page: number = 1) =>
    request(`/comment/${articleIndexId}/${page}/`),
  create: (articleIndexId: number, content: string) =>
    request(`/comment/${articleIndexId}/create/`, { method: 'POST', body: { content } }),
  update: (commentIndexId: number, content: string) =>
    request(`/comment/update/${commentIndexId}/`, { method: 'POST', body: { content } }),
  delete: (commentIndexId: number) =>
    request(`/comment/delete/${commentIndexId}/`, { method: 'POST' }),
  hide: (commentIndexId: number) =>
    request(`/comment/hide/${commentIndexId}/`, { method: 'POST' }),
  unhide: (commentIndexId: number) =>
    request(`/comment/unhide/${commentIndexId}/`, { method: 'POST' }),
}

// Admin API（管理员后端：查询需 staff/admin，修改仅 admin，后端强制校验）
export const adminApi = {
  // 用户管理
  listUsers: (params?: { search?: string; page?: number }) => {
    const query = new URLSearchParams()
    if (params?.search) query.set('search', params.search)
    if (params?.page) query.set('page', String(params.page))
    const qs = query.toString()
    return request(`/admin/users${qs ? '?' + qs : ''}`)
  },
  getUser: (id: string) =>
    request(`/admin/users/${id}`),
  createUser: (data: Record<string, any>) =>
    request('/admin/users', { method: 'POST', body: data }),
  updateUser: (id: string, data: Record<string, any>) =>
    request(`/admin/users/${id}`, { method: 'PATCH', body: data }),
  deleteUser: (id: string) =>
    request(`/admin/users/${id}`, { method: 'DELETE' }),
  // 文章管理
  listArticles: (params?: { search?: string; deleted?: boolean; hidden?: boolean; page?: number }) => {
    const query = new URLSearchParams()
    if (params?.search) query.set('search', params.search)
    if (params?.deleted !== undefined) query.set('deleted', String(params.deleted))
    if (params?.hidden !== undefined) query.set('hidden', String(params.hidden))
    if (params?.page) query.set('page', String(params.page))
    const qs = query.toString()
    return request(`/admin/articles${qs ? '?' + qs : ''}`)
  },
  getArticle: (indexId: number) =>
    request(`/admin/articles/${indexId}`),
  updateArticle: (indexId: number, data: Record<string, any>) =>
    request(`/admin/articles/${indexId}`, { method: 'PATCH', body: data }),
  // 评论管理
  listComments: (params?: { search?: string; deleted?: boolean; hidden?: boolean; page?: number }) => {
    const query = new URLSearchParams()
    if (params?.search) query.set('search', params.search)
    if (params?.deleted !== undefined) query.set('deleted', String(params.deleted))
    if (params?.hidden !== undefined) query.set('hidden', String(params.hidden))
    if (params?.page) query.set('page', String(params.page))
    const qs = query.toString()
    return request(`/admin/comments${qs ? '?' + qs : ''}`)
  },
  updateComment: (commentIndexId: number, data: Record<string, any>) =>
    request(`/admin/comments/${commentIndexId}`, { method: 'PATCH', body: data }),
}
