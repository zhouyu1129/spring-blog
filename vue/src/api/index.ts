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
  const data = await response.json()

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
}
