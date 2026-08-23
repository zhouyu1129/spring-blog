# 博客系统后端 API 接口文档

> 本文档定义了博客系统后端需要实现的所有 API 接口，基于前端 `vue/src/api/index.ts` 及各页面组件的使用方式逆向整理。

---

## 通用说明

### 基础路径

所有接口前缀为 `/api`

### 认证方式

- 使用 **Session + Cookie** 认证
- 前端通过 `credentials: 'include'` 携带 Cookie
- 登录状态判断：前端检查 Cookie 中是否包含 `SESSION`，或 `localStorage.isAuthenticated === 'true'`
- 未验证邮箱的用户，前端路由守卫只允许访问邮箱验证页、验证结果页和登出，其余页面会被拦截

### CSRF 保护（Spring Security 风格）

- 非 GET 请求需要 CSRF 保护
- 前端从 `XSRF-TOKEN` Cookie 中读取令牌，通过 `X-XSRF-TOKEN` 请求头携带
- 令牌由 `CookieCsrfTokenRepository` 风格的接口下发

### CSRF 令牌接口

| 方法 | 路径          | 说明                                          |
| ---- | ------------- | --------------------------------------------- |
| GET  | `/api/csrf` | 获取 CSRF 令牌（Set-Cookie: XSRF-TOKEN=xxx） |

### 请求格式

- 普通请求：`Content-Type: application/json`
- 文件上传请求：`Content-Type: multipart/form-data`
- 所有请求携带 `Accept: application/json`

### 响应格式

所有接口统一返回 JSON，前端通过 `response.ok`（HTTP 状态码 2xx）判断成功/失败。

> **Markdown 渲染约定**：后端对所有文章/评论接口只返回 **Markdown 原文**（`content` 字段），不返回渲染后的 HTML（无 `content_html` / `content_preview` / `toc` 字段）。HTML 渲染、目录（TOC）生成、列表摘要提取均由前端（`marked` 库）完成。

**成功响应示例：**

```json
{
  "status": "success",
  "message": "操作成功",
  // ... 其他业务数据
}
```

**失败响应示例：**

```json
{
  "status": "error",
  "message": "错误描述",
  // ... 其他错误信息
}
```

### 分页格式

前端使用 Django 风格的分页结构（`page_obj`），后端需兼容此格式：

```json
{
  "page_obj": {
    "number": 1,
    "paginator": {
      "num_pages": 5
    },
    "object_list": [...]
  }
}
```

也可使用简化格式，前端会做兼容：

```json
{
  "articles": [...],
  "page_obj": {
    "number": 1,
    "paginator": {
      "num_pages": 5
    }
  }
}
```

---

## 一、用户模块（User）

### 1.1 用户注册

| 项目           | 内容                   |
| -------------- | ---------------------- |
| **方法** | `POST`               |
| **路径** | `/api/user/register` |
| **认证** | 无需登录               |

**请求参数（JSON）：**

| 字段             | 类型   | 必填 | 说明                                |
| ---------------- | ------ | ---- | ----------------------------------- |
| username         | string | 是   | 用户名，ASCII 字符，不含空格和@符号 |
| email            | string | 是   | 邮箱地址                            |
| student_number   | string | 是   | 学号，必须为10位数字                |
| password         | string | 是   | 密码                                |
| confirm_password | string | 是   | 确认密码，必须与 password 一致      |

**成功响应（200）：**

```json
{
  "status": "success",
  "message": "注册成功，请查看邮箱验证"
}
```

**失败响应：**

```json
{
  "status": "error",
  "message": "用户名已存在 / 两次密码不一致 / 学号格式错误..."
}
```

---

### 1.2 用户登录

| 项目           | 内容                |
| -------------- | ------------------- |
| **方法** | `POST`            |
| **路径** | `/api/user/login` |
| **认证** | 无需登录            |

**请求参数（JSON）：**

| 字段     | 类型   | 必填 | 说明                                                         |
| -------- | ------ | ---- | ------------------------------------------------------------ |
| email    | string | 是   | 邮箱、学号或用户名（前端字段名为 email，但实际可输入三者之一） |
| password | string | 是   | 密码                                                         |

**成功响应（200）：**

```json
{
  "status": "success",
  "user": {
    "id": "1",
    "username": "zhangsan",
    "email": "zhangsan@example.com",
    "nickname": "小张",
    "student_number": "2024000001",
    "email_verified": true,
    "real_name": "张三",
    "mobile": "13800138000",
    "gender": "male",
    "date_joined": "2026-01-01T10:00:00Z",
    "last_login": "2026-08-10T08:30:00Z"
  }
}
```

**失败响应：**

```json
{
  "status": "error",
  "message": "用户名或密码错误"
}
```

> 登录成功后，服务端需 Set-Cookie 会话 Cookie（前端通过 Cookie 中的 `SESSION` 判断登录状态）
>
> 前端登录成功后：若 `user.email_verified` 为 `false`，跳转邮箱验证页；否则跳转首页或 `next` 参数指定的页面
>
> 注册成功后前端会自动调用本接口登录，再跳转邮箱验证页

---

### 1.3 用户登出

| 项目           | 内容                 |
| -------------- | -------------------- |
| **方法** | `GET`              |
| **路径** | `/api/user/logout` |
| **认证** | 需要登录             |

**成功响应（200）：**

```json
{
  "status": "success",
  "message": "已登出"
}
```

> 登出后清除会话 Cookie；前端同时清除 `localStorage` 中的登录标记和用户信息

---

### 1.4 获取当前用户信息

| 项目           | 内容                  |
| -------------- | --------------------- |
| **方法** | `GET`               |
| **路径** | `/api/user/profile` |
| **认证** | 需要登录              |

**成功响应（200）：**

```json
{
  "user": {
    "id": "1",
    "username": "zhangsan",
    "email": "zhangsan@example.com",
    "nickname": "小张",
    "student_number": "2024000001",
    "email_verified": true,
    "real_name": "张三",
    "mobile": "13800138000",
    "gender": "male",
    "date_joined": "2026-01-01T10:00:00Z",
    "last_login": "2026-08-10T08:30:00Z"
  }
}
```

---

### 1.5 编辑个人资料

| 项目           | 内容                       |
| -------------- | -------------------------- |
| **方法** | `POST`                   |
| **路径** | `/api/user/profile/edit` |
| **认证** | 需要登录                   |

**请求参数（JSON）：**

| 字段      | 类型   | 必填 | 说明                                            |
| --------- | ------ | ---- | ----------------------------------------------- |
| nickname  | string | 否   | 昵称，最多20个字符                              |
| real_name | string | 否   | 真实姓名，最多50个字符                          |
| mobile    | string | 否   | 手机号，最多11位                                |
| gender    | string | 否   | 性别，可选值：`male` / `female` / `other` |

**成功响应（200）：**

```json
{
  "status": "success",
  "message": "个人资料更新成功"
}
```

---

### 1.6 发送邮箱验证码

| 项目           | 内容                                  |
| -------------- | ------------------------------------- |
| **方法** | `POST`                              |
| **路径** | `/api/user/profile/send_email_code` |
| **认证** | 需要登录                              |

**请求参数：** 无（验证码发送到当前登录用户的邮箱）

**成功响应（200）：**

```json
{
  "status": "success",
  "message": "验证码已发送"
}
```

**失败响应：**

```json
{
  "status": "error",
  "message": "发送失败，请稍后重试"
}
```

> 验证码4位数字，5分钟内有效

---

### 1.7 修改邮箱

| 项目           | 内容                               |
| -------------- | ---------------------------------- |
| **方法** | `POST`                           |
| **路径** | `/api/user/profile/change_email` |
| **认证** | 需要登录                           |

**请求参数（JSON）：**

| 字段              | 类型   | 必填 | 说明                   |
| ----------------- | ------ | ---- | ---------------------- |
| new_email         | string | 是   | 新邮箱地址             |
| verification_code | string | 是   | 发送到当前邮箱的验证码 |

**成功响应（200）：**

```json
{
  "status": "success",
  "message": "邮箱修改成功，请查收验证邮件"
}
```

---

### 1.8 修改密码

| 项目           | 内容                                  |
| -------------- | ------------------------------------- |
| **方法** | `POST`                              |
| **路径** | `/api/user/profile/change_password` |
| **认证** | 需要登录                              |

**请求参数（JSON）：**

| 字段             | 类型   | 必填 | 说明                                 |
| ---------------- | ------ | ---- | ------------------------------------ |
| old_password     | string | 是   | 当前密码                             |
| new_password     | string | 是   | 新密码                               |
| confirm_password | string | 是   | 确认新密码，必须与 new_password 一致 |

**成功响应（200）：**

```json
{
  "status": "success",
  "message": "密码修改成功"
}
```

---

### 1.9 忘记密码

| 项目           | 内容                          |
| -------------- | ----------------------------- |
| **方法** | `POST`                      |
| **路径** | `/api/user/forgot_password` |
| **认证** | 无需登录                      |

**请求参数（JSON）：**

| 字段           | 类型   | 必填 | 说明                 |
| -------------- | ------ | ---- | -------------------- |
| email          | string | 是   | 注册邮箱             |
| student_number | string | 是   | 学号（用于验证身份） |

**成功响应（200）：**

```json
{
  "status": "success",
  "message": "密码重置链接已发送到您的邮箱"
}
```

**失败响应：**

```json
{
  "status": "error",
  "message": "邮箱和学号不匹配"
}
```

> 后端生成密码重置令牌（30 分钟有效，存 Redis），发送包含重置链接的邮件。链接为绝对地址：`{前端地址}/api/user/reset_password?token=xxx`（链接指向前端域名，由前端转发给后端处理）

---

### 1.10 重置密码

#### 1.10.1 重置链接落地（邮件中的链接，非 AJAX 接口）

| 项目           | 内容                                        |
| -------------- | ------------------------------------------- |
| **方法** | `GET`（浏览器直接访问）                    |
| **路径** | `/api/user/reset_password?token=xxx`      |
| **认证** | 无需登录（通过邮件中的令牌验证）            |

**行为说明：**

- 重定向到前端重置密码页面：`/user/reset-password?token=xxx`
- 令牌缺失时重定向到 `/user/reset-password`（前端展示"链接无效"提示）
- **不在此处校验令牌**——令牌是一次性的，仅在提交新密码（POST）时消费，避免打开页面即失效

#### 1.10.2 提交新密码

| 项目           | 内容                        |
| -------------- | --------------------------- |
| **方法** | `POST`                    |
| **路径** | `/api/user/reset_password` |
| **认证** | 无需登录（通过令牌验证）    |

**请求参数（JSON）：**

| 字段        | 类型   | 必填 | 说明                   |
| ----------- | ------ | ---- | ---------------------- |
| token       | string | 是   | 邮件中的重置令牌       |
| new_password | string | 是   | 新密码（6-128 位）    |

**成功响应（200）：**

```json
{
  "status": "success",
  "message": "密码重置成功"
}
```

**失败响应：**

```json
{
  "status": "error",
  "message": "重置令牌无效或已过期"
}
```

---

### 1.11 重发邮箱验证邮件

| 项目           | 内容                              |
| -------------- | --------------------------------- |
| **方法** | `POST`                          |
| **路径** | `/api/user/resend_verification` |
| **认证** | 需要登录                          |

**请求参数：** 无（验证邮件发送到当前登录用户的邮箱）

**成功响应（200）：**

```json
{
  "status": "success",
  "message": "验证邮件已重新发送"
}
```

**失败响应：**

```json
{
  "status": "error",
  "message": "发送失败"
}
```

> 前端实现了 60 秒冷却倒计时（localStorage 记录时间戳），后端可不做频率限制或做兜底限制

---

### 1.12 邮箱验证链接（邮件中的链接，非 AJAX 接口）

| 项目           | 内容                                     |
| -------------- | ---------------------------------------- |
| **方法** | `GET`（浏览器直接访问）                 |
| **路径** | `/api/user/verify_email?token=xxx`（邮件中的链接指向前端域名，由前端转发给后端） |
| **认证** | 无需登录（通过邮件中的令牌验证）          |

**行为说明：**

- 链接由后端在验证邮件中生成，绝对地址为 `{前端地址}/api/user/verify_email?token=xxx`
- 后端校验令牌，将用户 `email_verified` 置为 `true`
- 验证成功后重定向到前端页面：`/user/verify-email/result?status=success`
- 验证失败（令牌无效/过期）后重定向到：`/user/verify-email/result`（无 status 或 status 不为 success）
- 前端结果页根据 URL 中的 `status` 参数展示成功/失败界面，并自动刷新本地用户信息的 `email_verified` 状态

---

### 1.13 查看他人主页

| 项目           | 内容                    |
| -------------- | ----------------------- |
| **方法** | `GET`                 |
| **路径** | `/api/user/user/{userId}` |
| **认证** | 无需登录（公开信息）    |

**路径参数：**

| 参数   | 类型   | 说明        |
| ------ | ------ | ----------- |
| userId | string | 目标用户 ID |

**查询参数（可选，均默认 1）：**

| 参数          | 类型 | 说明               |
| ------------- | ---- | ------------------ |
| article_page  | int  | 文章列表页码       |
| comment_page  | int  | 评论列表页码       |

> 每页 10 条。文章按 `created_at` 降序、相同 `index_id` 只保留最新版本；已删除文章不展示；已隐藏文章仅管理员和作者本人可见（查看自己的主页时可见，带 `is_hidden: true` 标记）。当前前端未传分页参数（始终取第 1 页），参数为前端后续分页预留。

**成功响应（200）：**

```json
{
  "target_user": {
    "id": "2",
    "username": "lisi",
    "nickname": "小李",
    "real_name": "李四",
    "gender": "male",
    "date_joined": "2026-02-01T10:00:00Z",
    "email_verified": true
  },
  "article_page_obj": {
    "number": 1,
    "paginator": {
      "num_pages": 3
    },
    "object_list": [
      {
        "index_id": 1,
        "title": "文章标题",
        "is_hidden": false,
        "created_at": "2026-03-01T10:00:00Z",
        "updated_at": "2026-03-01T10:00:00Z",
        "content": "文章Markdown原文...",
        "author_id": {
          "id": "2",
          "nickname": "小李",
          "username": "lisi"
        }
      }
    ]
  },
  "comment_page_obj": {
    "number": 1,
    "paginator": {
      "num_pages": 2
    },
    "object_list": [
      {
        "index_id": 5,
        "content": "评论内容（Markdown原文）",
        "create_time": "2026-03-15T10:00:00Z",
        "update_time": "2026-03-15T10:00:00Z",
        "is_hidden": false,
        "top": false,
        "author": {
          "id": "2",
          "nickname": "小李",
          "username": "lisi"
        },
        "article": {
          "index_id": 1,
          "title": "评论所属文章标题"
        }
      }
    ]
  }
}
```

> 也可使用简化格式（`articles` / `comments` 替代 `page_obj.object_list`），前端兼容两种格式。
> 评论按 `created_at` 降序、相同 `index_id` 只保留最新版本；已删除评论不展示；已隐藏评论仅管理员和评论作者本人可见（查看自己的主页时可见，带 `is_hidden: true` 标记）；所属文章已删除/已隐藏的评论不展示。

---

## 二、文章模块（Article）

### 2.1 文章列表

| 项目           | 内容              |
| -------------- | ----------------- |
| **方法** | `GET`           |
| **路径** | `/api/article/` |
| **认证** | 无需登录          |

**查询参数：**

| 参数   | 类型   | 必填 | 说明                         |
| ------ | ------ | ---- | ---------------------------- |
| search | string | 否   | 搜索关键词（搜索标题或内容） |
| page   | int    | 否   | 页码，默认 1                 |

**成功响应（200）：**

```json
{
  "page_obj": {
    "number": 1,
    "paginator": {
      "num_pages": 5
    },
    "object_list": [
      {
        "index_id": 1,
        "title": "文章标题",
        "content": "文章完整内容（Markdown原文，图片为标准Markdown语法）",
        "is_hidden": false,
        "can_edit": true,
        "can_hide": true,
        "created_at": "2026-03-01T10:00:00Z",
        "updated_at": "2026-03-01T12:00:00Z",
        "author_id": {
          "id": "1",
          "nickname": "小张",
          "username": "zhangsan"
        }
      }
    ]
  }
}
```

> 前端兼容 `res.data.page_obj?.object_list || res.data.articles`；列表摘要与首图由前端从 `content` 渲染计算

**可见性规则（列表与详情一致）：**

| 角色 | 可见范围 |
| ---- | -------- |
| 管理员 | 所有未删除文章（含他人已隐藏文章） |
| 作者 | 未隐藏文章 + 自己的已隐藏文章 |
| 其他人/未登录 | 仅未删除且未隐藏的文章 |

> 已删除文章任何人都无法访问；`is_hidden` 为该文章是否隐藏；`can_edit`/`can_hide` 表示当前用户是否可编辑/隐藏该文章（作者本人或管理员）

---

### 2.2 文章详情

| 项目           | 内容                        |
| -------------- | --------------------------- |
| **方法** | `GET`                     |
| **路径** | `/api/article/{indexId}/` |
| **认证** | 无需登录                    |

**路径参数：**

| 参数    | 类型 | 说明    |
| ------- | ---- | ------- |
| indexId | int  | 文章 ID |

**成功响应（200）：**

```json
{
  "article": {
    "index_id": 1,
    "title": "文章标题",
    "content": "Markdown原文内容（图片为标准Markdown语法）",
    "is_hidden": false,
    "can_edit": true,
    "can_hide": true,
    "created_at": "2026-03-01T10:00:00Z",
    "updated_at": "2026-03-01T12:00:00Z",
    "author_id": {
      "id": "1",
      "nickname": "小张",
      "username": "zhangsan"
    }
  },
  "files": [
    {
      "id": 1,
      "title": "文档.pdf",
      "content": {
        "url": "/media/files/xxx.pdf",
        "size": 1048576
      },
      "created_at": "2026-03-01T10:00:00Z"
    }
  ],
  "images": [
    {
      "id": 1,
      "title": "图片1.png",
      "content": {
        "url": "/media/images/xxx.png"
      },
      "created_at": "2026-03-01T10:00:00Z"
    }
  ]
}
```

> 正文 HTML 渲染和文章目录（TOC）由前端从 `content` 生成；`files` 和 `images` 为文章关联的附件和图片

---

### 2.3 创建文章

| 项目                   | 内容                     |
| ---------------------- | ------------------------ |
| **方法**         | `POST`                 |
| **路径**         | `/api/article/create/` |
| **认证**         | 需要登录                 |
| **Content-Type** | `multipart/form-data`  |

**请求参数（FormData）：**

| 字段             | 类型     | 必填 | 说明                                                       |
| ---------------- | -------- | ---- | ---------------------------------------------------------- |
| title            | string   | 是   | 文章标题                                                   |
| content          | string   | 是   | 文章内容（Markdown格式）                                   |
| images           | File[]   | 否   | 文章图片文件（可多个，字段名均为`images`）               |
| image_id_mapping | string   | 否   | JSON 数组字符串，如`[1,2,3]`，对应 images 的临时 ID 映射 |
| selected_files   | string[] | 否   | 临时文件 ID（可多个，字段名均为`selected_files`）        |

> 前端使用 `[[img_id=N]]` 在 Markdown 中引用图片，`image_id_mapping` 记录了引用的图片与上传文件的对应关系。
> **后端在保存文章时需将 `[[img_id=N]]` 替换为标准 Markdown 图片语法**（如 `![图片名](/media/images/xxx.png)`），数据库中只存标准 Markdown，浏览类接口返回的 `content` 中不再出现 `[[img_id=N]]`

**成功响应（200）：**

```json
{
  "status": "success",
  "message": "文章创建成功"
}
```

---

### 2.4 编辑文章

| 项目                   | 内容                             |
| ---------------------- | -------------------------------- |
| **方法**         | `POST`                         |
| **路径**         | `/api/article/{indexId}/edit/` |
| **认证**         | 需要登录（作者或管理员可编辑）   |
| **Content-Type** | `multipart/form-data`          |

**路径参数：**

| 参数    | 类型 | 说明    |
| ------- | ---- | ------- |
| indexId | int  | 文章 ID |

**请求参数（FormData）：**

| 字段             | 类型     | 必填 | 说明                                    |
| ---------------- | -------- | ---- | --------------------------------------- |
| title            | string   | 是   | 文章标题                                |
| content          | string   | 是   | 文章内容（Markdown格式）                |
| images           | File[]   | 否   | 新上传的图片文件                        |
| image_id_mapping | string   | 否   | JSON 数组字符串，新图片的临时 ID 映射   |
| keep_images      | string[] | 否   | 要保留的已有图片 ID（前端按正文引用自动生成：正文引用了图片 URL 才保留） |
| keep_files       | string[] | 否   | 要保留的已有文件 ID                     |
| selected_files   | string[] | 否   | 新增的临时文件 ID                       |

**成功响应（200）：**

```json
{
  "status": "success",
  "message": "文章修改成功"
}
```

> 编辑不删除任何图片/文件记录和磁盘文件：未包含在 keep_images/keep_files 中的资源仅解除与新版本的关联，记录保留（旧版本仍引用它们，供后台预览历史版本）

---

### 2.5 删除文章

| 项目           | 内容                               |
| -------------- | ---------------------------------- |
| **方法** | `POST`                           |
| **路径** | `/api/article/{indexId}/delete/` |
| **认证** | 需要登录（作者或管理员可删除）     |

**路径参数：**

| 参数    | 类型 | 说明    |
| ------- | ---- | ------- |
| indexId | int  | 文章 ID |

**成功响应（200）：**

```json
{
  "status": "success",
  "message": "文章已删除"
}
```

---

### 2.6 上传文件

| 项目                   | 内容                          |
| ---------------------- | ----------------------------- |
| **方法**         | `POST`                      |
| **路径**         | `/api/article/upload-file/` |
| **认证**         | 需要登录                      |
| **Content-Type** | `multipart/form-data`       |

**请求参数（FormData）：**

| 字段 | 类型 | 必填 | 说明               |
| ---- | ---- | ---- | ------------------ |
| file | File | 是   | 上传的文件（单个） |

**成功响应（200）：**

```json
{
  "success": true,
  "file_id": "temp_abc123",
  "filename": "文档.pdf",
  "file_size": 1048576,
  "file_url": "/media/temp/xxx.pdf"
}
```

**失败响应：**

```json
{
  "success": false,
  "error": "文件上传失败"
}
```

---

### 2.7 删除临时文件

| 项目           | 内容                                        |
| -------------- | ------------------------------------------- |
| **方法** | `POST`                                    |
| **路径** | `/api/article/delete-temp-file/{fileId}/` |
| **认证** | 需要登录                                    |

**路径参数：**

| 参数   | 类型   | 说明        |
| ------ | ------ | ----------- |
| fileId | string | 临时文件 ID |

**请求参数（JSON）：**

| 字段    | 类型   | 必填 | 说明                                                   |
| ------- | ------ | ---- | ------------------------------------------------------ |
| _method | string | 否   | 值为`DELETE`（模拟 DELETE 方法，Spring Boot 可忽略） |

**成功响应（200）：**

```json
{
  "success": true
}
```

---

### 2.8 获取临时文件列表

| 项目           | 内容                             |
| -------------- | -------------------------------- |
| **方法** | `GET`                          |
| **路径** | `/api/article/get-temp-files/` |
| **认证** | 需要登录                         |

**成功响应（200）：**

```json
{
  "success": true,
  "files": [
    {
      "file_id": "temp_abc123",
      "filename": "文档.pdf",
      "file_size": 1048576,
      "file_url": "/media/temp/xxx.pdf"
    }
  ]
}
```

---

### 2.9 隐藏文章

| 项目           | 内容                             |
| -------------- | -------------------------------- |
| **方法** | `POST`                         |
| **路径** | `/api/article/{indexId}/hide/` |
| **认证** | 需要登录（作者或管理员）         |

**路径参数：**

| 参数    | 类型 | 说明    |
| ------- | ---- | ------- |
| indexId | int  | 文章 ID |

**成功响应（200）：**

```json
{
  "status": "success",
  "message": "文章已隐藏"
}
```

> 隐藏后文章仅作者本人和管理员可见（列表和详情均带 `is_hidden: true` 标记）

---

### 2.10 取消隐藏文章

| 项目           | 内容                               |
| -------------- | ---------------------------------- |
| **方法** | `POST`                           |
| **路径** | `/api/article/{indexId}/unhide/` |
| **认证** | 需要登录（作者或管理员）           |

**路径参数：**

| 参数    | 类型 | 说明    |
| ------- | ---- | ------- |
| indexId | int  | 文章 ID |

**成功响应（200）：**

```json
{
  "status": "success",
  "message": "文章已取消隐藏"
}
```

---

## 三、评论模块（Comment）

### 3.1 评论列表

| 项目           | 内容                                      |
| -------------- | ----------------------------------------- |
| **方法** | `GET`                                   |
| **路径** | `/api/comment/{articleIndexId}/{page}/` |
| **认证** | 无需登录                                  |

**路径参数：**

| 参数           | 类型 | 说明    |
| -------------- | ---- | ------- |
| articleIndexId | int  | 文章 ID |
| page           | int  | 页码    |

**成功响应（200）：**

```json
{
  "article": {
    "index_id": 1,
    "title": "文章标题",
    "author_id": {
      "id": "1",
      "nickname": "小张",
      "username": "zhangsan"
    },
    "created_at": "2026-03-01T10:00:00Z"
  },
  "page_obj": {
    "number": 1,
    "paginator": {
      "num_pages": 3
    },
    "object_list": [
      {
        "index_id": 1,
        "content": "评论原文内容（Markdown）",
        "create_time": "2026-03-15T10:00:00Z",
        "update_time": "2026-03-15T10:00:00Z",
        "is_hidden": false,
        "top": false,
        "author": {
          "id": "2",
          "nickname": "小李",
          "username": "lisi"
        }
      }
    ]
  }
}
```

> 前端兼容 `res.data.page_obj?.object_list || res.data.comments`；评论 HTML 由前端从 `content` 渲染
> 可见性：已删除评论任何人都不可见；已隐藏评论仅管理员和评论作者本人可见；已隐藏文章的评论仅管理员和文章作者可见

---

### 3.2 创建评论

| 项目           | 内容                                      |
| -------------- | ----------------------------------------- |
| **方法** | `POST`                                  |
| **路径** | `/api/comment/{articleIndexId}/create/` |
| **认证** | 需要登录                                  |

**路径参数：**

| 参数           | 类型 | 说明    |
| -------------- | ---- | ------- |
| articleIndexId | int  | 文章 ID |

**请求参数（JSON）：**

| 字段    | 类型   | 必填 | 说明                           |
| ------- | ------ | ---- | ------------------------------ |
| content | string | 是   | 评论内容（支持 Markdown 格式） |

**成功响应（200）：**

```json
{
  "status": "success",
  "message": "评论发布成功"
}
```

---

### 3.3 修改评论

| 项目           | 内容                                      |
| -------------- | ----------------------------------------- |
| **方法** | `POST`                                  |
| **路径** | `/api/comment/update/{commentIndexId}/` |
| **认证** | 需要登录（仅作者可修改）                  |

**路径参数：**

| 参数           | 类型 | 说明    |
| -------------- | ---- | ------- |
| commentIndexId | int  | 评论 ID |

**请求参数（JSON）：**

| 字段    | 类型   | 必填 | 说明         |
| ------- | ------ | ---- | ------------ |
| content | string | 是   | 新的评论内容 |

> 修改评论会创建新版本，原版本保留

**成功响应（200）：**

```json
{
  "status": "success",
  "message": "评论修改成功"
}
```

---

### 3.4 删除评论

| 项目           | 内容                                      |
| -------------- | ----------------------------------------- |
| **方法** | `POST`                                  |
| **路径** | `/api/comment/delete/{commentIndexId}/` |
| **认证** | 需要登录（仅作者可删除）                  |

**路径参数：**

| 参数           | 类型 | 说明    |
| -------------- | ---- | ------- |
| commentIndexId | int  | 评论 ID |

> 删除为软删除（标记 `is_deleted`），删除后该评论对所有用户不可见，也无法再编辑

**成功响应（200）：**

```json
{
  "status": "success",
  "message": "评论已删除"
}
```

---

### 3.5 隐藏评论

| 项目           | 内容                                    |
| -------------- | --------------------------------------- |
| **方法** | `POST`                                 |
| **路径** | `/api/comment/hide/{commentIndexId}/`  |
| **认证** | 需要登录（仅作者可隐藏）                |

**路径参数：**

| 参数           | 类型 | 说明    |
| -------------- | ---- | ------- |
| commentIndexId | int  | 评论 ID |

> 隐藏后该评论仅管理员和评论作者本人可见（返回 `is_hidden: true`）

**成功响应（200）：**

```json
{
  "status": "success",
  "message": "评论已隐藏"
}
```

---

### 3.6 取消隐藏评论

| 项目           | 内容                                      |
| -------------- | ----------------------------------------- |
| **方法** | `POST`                                   |
| **路径** | `/api/comment/unhide/{commentIndexId}/`  |
| **认证** | 需要登录（仅作者可取消隐藏）              |

**路径参数：**

| 参数           | 类型 | 说明    |
| -------------- | ---- | ------- |
| commentIndexId | int  | 评论 ID |

**成功响应（200）：**

```json
{
  "status": "success",
  "message": "评论已取消隐藏"
}
```

---

## 四、数据模型

### 4.1 User（用户）

| 字段           | 类型     | 说明                                 |
| -------------- | -------- | ------------------------------------ |
| id             | Long     | 主键                                 |
| username       | String   | 用户名，ASCII字符，不含空格和@，唯一 |
| email          | String   | 邮箱地址，唯一                       |
| password       | String   | 加密后的密码                         |
| nickname       | String   | 昵称，最多20字符，可选               |
| student_number | String   | 学号，10位数字，唯一                 |
| email_verified | Boolean  | 邮箱是否已验证，默认 false           |
| real_name      | String   | 真实姓名，最多50字符，可选           |
| mobile         | String   | 手机号，最多11位，可选               |
| gender         | String   | 性别：male / female / other，可选    |
| date_joined    | DateTime | 注册时间                             |
| last_login     | DateTime | 最后登录时间                         |

### 4.2 Article（文章）

| 字段       | 类型     | 说明                                       |
| ---------- | -------- | ------------------------------------------ |
| index_id   | Long     | 文章 ID（主键）                            |
| title      | String   | 文章标题                                   |
| content    | String   | 文章内容（标准 Markdown，图片引用已转换）  |
| created_at | DateTime | 创建时间                                   |
| updated_at | DateTime | 最后更新时间                               |
| author_id  | Long     | 作者 ID（外键关联 User）                   |

### 4.3 Comment（评论）

| 字段        | 类型     | 说明                            |
| ----------- | -------- | ------------------------------- |
| index_id    | Long     | 评论 ID（主键）                 |
| content     | String   | 评论内容（Markdown原文）        |
| create_time | DateTime | 创建时间                        |
| update_time | DateTime | 最后更新时间                    |
| is_hidden   | Boolean  | 是否隐藏，默认 false            |
| is_deleted  | Boolean  | 是否删除（软删除），默认 false  |
| top         | Boolean  | 是否置顶，默认 false            |
| author_id   | Long     | 作者 ID（外键关联 User）        |
| article_id  | Long     | 所属文章 ID（外键关联 Article） |

### 4.4 ArticleFile（文章附件）

| 字段       | 类型     | 说明                                                |
| ---------- | -------- | --------------------------------------------------- |
| id         | Long     | 主键                                                |
| title      | String   | 文件名                                              |
| content    | String   | 文件URL路径                                         |
| file_size  | Long     | 文件大小（字节）                                    |
| file_type  | String   | 文件类型：`image` / `file`                      |
| created_at | DateTime | 上传时间                                            |
| article_id | Long     | 所属文章 ID（外键关联 Article，可为空表示临时文件） |

### 4.5 TempFile（临时文件）

| 字段       | 类型     | 说明                       |
| ---------- | -------- | -------------------------- |
| file_id    | String   | 临时文件 ID                |
| filename   | String   | 文件名                     |
| file_size  | Long     | 文件大小（字节）           |
| file_url   | String   | 文件URL路径                |
| user_id    | Long     | 上传者 ID（外键关联 User） |
| created_at | DateTime | 上传时间                   |

> 临时文件在文章创建/编辑时关联到文章，未关联的可定期清理

---

## 五、接口总览

### 用户模块

| #  | 方法 | 路径                                  | 说明                   | 认证 |
| -- | ---- | ------------------------------------- | ---------------------- | ---- |
| 1  | POST | `/api/user/register`                | 用户注册               | 无   |
| 2  | POST | `/api/user/login`                   | 用户登录               | 无   |
| 3  | GET  | `/api/user/logout`                  | 用户登出               | 是   |
| 4  | GET  | `/api/user/profile`                 | 获取当前用户信息       | 是   |
| 5  | POST | `/api/user/profile/edit`            | 编辑个人资料           | 是   |
| 6  | POST | `/api/user/profile/send_email_code` | 发送邮箱验证码         | 是   |
| 7  | POST | `/api/user/profile/change_email`    | 修改邮箱               | 是   |
| 8  | POST | `/api/user/profile/change_password` | 修改密码               | 是   |
| 9  | POST | `/api/user/forgot_password`         | 忘记密码               | 无   |
| 10 | GET  | `/api/user/reset_password?token=xxx` | 重置密码链接（重定向） | 无   |
| 11 | POST | `/api/user/reset_password`          | 提交重置密码           | 无   |
| 12 | POST | `/api/user/resend_verification`     | 重发邮箱验证邮件       | 是   |
| 13 | GET  | `/api/user/verify-email?token=xxx`  | 邮箱验证链接（重定向） | 无   |
| 14 | GET  | `/api/user/user/{userId}`           | 查看他人主页           | 无   |

### 文章模块

| #  | 方法 | 路径                                        | 说明             | 认证       |
| -- | ---- | ------------------------------------------- | ---------------- | ---------- |
| 15 | GET  | `/api/article/`                           | 文章列表         | 无         |
| 16 | GET  | `/api/article/{indexId}/`                 | 文章详情         | 无         |
| 17 | POST | `/api/article/create/`                    | 创建文章         | 是         |
| 18 | POST | `/api/article/{indexId}/edit/`            | 编辑文章         | 是（作者） |
| 19 | POST | `/api/article/{indexId}/delete/`          | 删除文章         | 是（作者） |
| 20 | POST | `/api/article/upload-file/`               | 上传文件         | 是         |
| 21 | POST | `/api/article/delete-temp-file/{fileId}/` | 删除临时文件     | 是         |
| 22 | GET  | `/api/article/get-temp-files/`            | 获取临时文件列表 | 是         |

### 评论模块

| #  | 方法 | 路径                                      | 说明     | 认证       |
| -- | ---- | ----------------------------------------- | -------- | ---------- |
| 23 | GET  | `/api/comment/{articleIndexId}/{page}/` | 评论列表 | 无         |
| 24 | POST | `/api/comment/{articleIndexId}/create/` | 创建评论 | 是         |
| 25 | POST | `/api/comment/update/{commentIndexId}/` | 修改评论 | 是（作者） |
| 26 | POST | `/api/comment/delete/{commentIndexId}/` | 删除评论 | 是（作者） |
| 27 | POST | `/api/comment/hide/{commentIndexId}/`   | 隐藏评论 | 是（作者） |
| 28 | POST | `/api/comment/unhide/{commentIndexId}/` | 取消隐藏评论 | 是（作者） |

### 系统接口

| #  | 方法 | 路径          | 说明                              | 认证 |
| -- | ---- | ------------- | --------------------------------- | ---- |
| 27 | GET  | `/api/csrf` | 获取 CSRF 令牌（XSRF-TOKEN Cookie） | 无   |

---

## 六、前后端对接注意事项

1. **代理配置**：前端 `vite.config.ts` 已代理 `/api` 和 `/media` 到 `http://127.0.0.1:8080`（Spring Boot）
2. **CSRF 适配**：前端已改用 Spring Security 风格 CSRF 机制（`XSRF-TOKEN` Cookie + `X-XSRF-TOKEN` 请求头），后端使用 `CookieCsrfTokenRepository` 即可直接对接；首次非 GET 请求前前端会自动请求 `/api/csrf` 获取令牌
3. **Session Cookie**：前端通过 Cookie 中是否包含 `SESSION` 判断登录状态，Spring Boot 默认的 `JSESSIONID` 不匹配，需将会话 Cookie 名配置为 `SESSION`（如 `server.servlet.session.cookie.name=SESSION`）
4. **URL 尾部斜杠**：部分接口以 `/` 结尾（如 `/article/create/`），部分不带（如 `/user/user/{userId}`、`/api/csrf`），后端需严格匹配前端实际调用的路径，或开启尾部斜杠兼容
5. **静态资源**：前端代理了 `/media` 路径到后端，Spring Boot 需要配置静态资源映射
6. **邮件链接域名**：验证/重置邮件中的链接使用 `app.frontend-url`（前端域名）拼接 `/api/...` 路径。开发环境下 Vite 代理会将其转发给后端；`npm run preview` 或静态部署时，前端路由会把到达 SPA 的 `/api` 路径整页转发到 `VITE_BACKEND_URL` 环境变量指定的后端地址（未设置时默认 `http://localhost:8080`）
7. **分页格式**：前端优先读取 Django 风格的 `page_obj` 结构，同时兼容简化格式（`articles` / `comments` + 平铺的页码字段），后端可任选其一
8. **邮箱验证流程**：注册/登录后若 `email_verified` 为 `false`，前端强制跳转验证页；邮件中的验证链接处理后需重定向回前端 `/user/verify-email/result?status=success`（失败时不含 `status=success`）
9. **Markdown 渲染职责**：后端只存/返回 Markdown 原文，HTML 渲染、TOC 生成、摘要提取均由前端完成（`vue/src/lib/markdown.ts`，基于 `marked`，`breaks: true` + `gfm: true`），后端无需生成 `content_html` / `content_preview` / `toc`
10. **图片引用转换**：创建/编辑文章时正文含 `[[img_id=N]]` 占位符，后端保存时必须将其替换为标准 Markdown 图片语法（配合 `image_id_mapping` 与上传的 `images` 文件顺序对应），浏览接口返回的正文不再包含占位符
