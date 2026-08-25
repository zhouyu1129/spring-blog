import { createRouter, createWebHistory } from 'vue-router'
import HomePage from '@/pages/HomePage.vue'
import AboutPage from '@/pages/AboutPage.vue'
import ArticleListPage from '@/pages/article/ArticleListPage.vue'
import ArticleDetailPage from '@/pages/article/ArticleDetailPage.vue'
import ArticleCreatePage from '@/pages/article/ArticleCreatePage.vue'
import ArticleEditPage from '@/pages/article/ArticleEditPage.vue'
import ArticleDeletePage from '@/pages/article/ArticleDeletePage.vue'
import CommentListPage from '@/pages/comment/CommentListPage.vue'
import CommentCreatePage from '@/pages/comment/CommentCreatePage.vue'
import CommentEditPage from '@/pages/comment/CommentEditPage.vue'
import CommentDeletePage from '@/pages/comment/CommentDeletePage.vue'
import LoginPage from '@/pages/user/LoginPage.vue'
import RegisterPage from '@/pages/user/RegisterPage.vue'
import ProfilePage from '@/pages/user/ProfilePage.vue'
import EditProfilePage from '@/pages/user/EditProfilePage.vue'
import ChangeEmailPage from '@/pages/user/ChangeEmailPage.vue'
import ChangePasswordPage from '@/pages/user/ChangePasswordPage.vue'
import ForgotPasswordPage from '@/pages/user/ForgotPasswordPage.vue'
import ResetPasswordPage from '@/pages/user/ResetPasswordPage.vue'
import UserProfilePage from '@/pages/user/UserProfilePage.vue'
import EmailVerifyPage from '@/pages/user/EmailVerifyPage.vue'
import EmailVerifyResultPage from '@/pages/user/EmailVerifyResultPage.vue'
import AdminPage from '@/pages/admin/AdminPage.vue'
import NotFoundPage from '@/pages/NotFoundPage.vue'

const routes = [
  { path: '/', name: 'home', component: HomePage },
  { path: '/about', name: 'about', component: AboutPage },
  { path: '/article', name: 'article-list', component: ArticleListPage },
  { path: '/article/create', name: 'article-create', component: ArticleCreatePage, meta: { requiresAuth: true } },
  { path: '/article/:indexId', name: 'article-detail', component: ArticleDetailPage, props: true },
  { path: '/article/:indexId/edit', name: 'article-edit', component: ArticleEditPage, props: true, meta: { requiresAuth: true } },
  { path: '/article/:indexId/delete', name: 'article-delete', component: ArticleDeletePage, props: true, meta: { requiresAuth: true } },
  { path: '/comment/:articleIndexId', name: 'comment-list', component: CommentListPage, props: true },
  { path: '/comment/:articleIndexId/create', name: 'comment-create', component: CommentCreatePage, props: true, meta: { requiresAuth: true } },
  { path: '/comment/update/:commentIndexId', name: 'comment-edit', component: CommentEditPage, props: true, meta: { requiresAuth: true } },
  { path: '/comment/delete/:commentIndexId', name: 'comment-delete', component: CommentDeletePage, props: true, meta: { requiresAuth: true } },
  { path: '/user/login', name: 'login', component: LoginPage },
  { path: '/user/register', name: 'register', component: RegisterPage },
  { path: '/user/profile', name: 'profile', component: ProfilePage, meta: { requiresAuth: true } },
  { path: '/user/profile/edit', name: 'edit-profile', component: EditProfilePage, meta: { requiresAuth: true } },
  { path: '/user/profile/change-email', name: 'change-email', component: ChangeEmailPage, meta: { requiresAuth: true } },
  { path: '/user/profile/change-password', name: 'change-password', component: ChangePasswordPage, meta: { requiresAuth: true } },
  { path: '/user/forgot-password', name: 'forgot-password', component: ForgotPasswordPage },
  { path: '/user/reset-password', name: 'reset-password', component: ResetPasswordPage },
  { path: '/user/:userId', name: 'user-profile', component: UserProfilePage, props: true },
  { path: '/user/verify-email', name: 'verify-email', component: EmailVerifyPage, meta: { requiresAuth: true, allowUnverified: true } },
  { path: '/user/verify-email/result', name: 'verify-email-result', component: EmailVerifyResultPage },
  { path: '/admin', name: 'admin', component: AdminPage, meta: { requiresAuth: true } },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundPage },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  // 支持文章目录等锚点（href="#xxx"）跳转
  scrollBehavior(to, from, savedPosition) {
    if (to.hash) return { el: to.hash, top: 80 }
    if (savedPosition) return savedPosition
    return { top: 0 }
  },
})

// 从 localStorage 读取用户信息中的 email_verified 状态
function isEmailVerified(): boolean {
  const raw = localStorage.getItem('user')
  if (!raw) return true // 没有用户信息时不阻止
  try {
    const user = JSON.parse(raw)
    return !!user?.email_verified
  } catch {
    return true
  }
}

function isLoggedIn(): boolean {
  return document.cookie.includes('SESSION') || localStorage.getItem('isAuthenticated') === 'true'
}

// 后端地址：邮件验证/重置等链接（/api/...）到达 SPA 时整页转发给后端。
// 开发环境下该请求会先被 Vite 的 /api 代理拦截，此转发仅在 preview / 静态部署时生效。
const BACKEND_URL: string = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080'

router.beforeEach((to, _from, next) => {
  // /api 路径不属于 SPA 页面，直接转发给后端处理（如 /api/user/reset_password?token=...）
  if (to.path.startsWith('/api/')) {
    window.location.replace(BACKEND_URL + to.fullPath)
    next(false)
    return
  }

  const loggedIn = isLoggedIn()

  // 需要认证但未登录 → 跳转登录页
  if (to.meta.requiresAuth && !loggedIn) {
    next({ name: 'login', query: { next: to.fullPath } })
    return
  }

  // 已登录但邮箱未验证 → 只允许访问验证页和登出
  if (loggedIn && !isEmailVerified()) {
    const allowed = ['verify-email', 'verify-email-result', 'logout']
    if (!allowed.includes(String(to.name)) && !to.meta.allowUnverified) {
      next({ name: 'verify-email', query: { reason: 'unverified' } })
      return
    }
  }

  next()
})

export default router
