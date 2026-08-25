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

## 四、管理员模块（Admin）

> **访问控制**：`/api/admin/**` 下所有接口中——
> - **查询（GET）**：需当前用户 `is_staff` 或 `is_admin` 为 `true`
> - **修改（POST / PATCH / DELETE）**：仅 `is_admin` 为 `true`
> - 未登录或权限不足返回 `403`
>
> 本模块遵循 RESTful 风格：分页用查询参数（`?page=`），部分更新用 `PATCH`。

### 4.1 用户列表

| 项目           | 内容                                            |
| -------------- | ----------------------------------------------- |
| **方法** | `GET`                                           |
| **路径** | `/api/admin/users`                              |
| **认证** | 需要登录（`is_staff` 或 `is_admin`）            |

**查询参数：**

| 参数   | 类型 | 说明                                                      |
| ------ | ---- | --------------------------------------------------------- |
| search | text | 可选，按用户名/昵称/邮箱/学号模糊匹配                     |
| page   | int  | 可选，页码，默认 1，每页 10 条                             |

**成功响应（200）：**

```json
{
  "page_obj": {
    "number": 1,
    "paginator": { "num_pages": 1 },
    "object_list": [
      {
        "id": "3f2a...",
        "username": "alice",
        "nickname": "Alice",
        "real_name": null,
        "gender": null,
        "email": "alice@example.com",
        "email_verified": true,
        "mobile": null,
        "student_number": "2026000001",
        "is_staff": false,
        "is_admin": false,
        "is_enabled": true,
        "created_at": "2026-08-23T10:00:00",
        "last_logged_at": null,
        "roles": [
          { "role_name": "user", "description": "普通用户（注册默认获得）", "expires_at": null }
        ]
      }
    ]
  }
}
```

> 用户结构不含 `password`。`roles` 为该用户当前拥有的角色列表（含已过期分配），`expires_at` 为该角色分配的有效期（ISO-8601，`null` 表示永久）。

### 4.2 用户详情

| 项目           | 内容                                            |
| -------------- | ----------------------------------------------- |
| **方法** | `GET`                                           |
| **路径** | `/api/admin/users/{id}`                         |
| **认证** | 需要登录（`is_staff` 或 `is_admin`）            |

**路径参数：**

| 参数 | 类型   | 说明    |
| ---- | ------ | ------- |
| id   | string | 用户 ID（UUID） |

**成功响应（200）：** `{ "user": { ...同列表条目结构 } }`；用户不存在返回 `404`。

### 4.3 创建用户

| 项目           | 内容                                            |
| -------------- | ----------------------------------------------- |
| **方法** | `POST`                                          |
| **路径** | `/api/admin/users`                              |
| **认证** | 需要登录（仅 `is_admin`）                       |

**请求体（JSON）：**

| 字段           | 类型    | 必填 | 说明                                          |
| -------------- | ------- | ---- | --------------------------------------------- |
| username       | String  | 是   | 3-40 位，仅字母数字下划线连字符，非纯数字，唯一 |
| email          | String  | 是   | 邮箱格式，唯一                                 |
| student_number | String  | 是   | 10 位数字，唯一                                 |
| password       | String  | 是   | 6-128 位（明文传输，后端加密存储）              |
| nickname       | String  | 否   | 默认同 username                                |
| real_name      | String  | 否   |                                                |
| mobile         | String  | 否   |                                                |
| gender         | String  | 否   | male / female / other                          |
| email_verified | Boolean | 否   | 默认 false                                     |
| is_staff       | Boolean | 否   | 默认 false                                     |
| is_admin       | Boolean | 否   | 默认 false                                     |
| is_enabled     | Boolean | 否   | 默认 true                                      |

**成功响应（200）：** `{ "user": { ... } }`；格式不合法或字段重复返回 `400`。

### 4.4 编辑用户

| 项目           | 内容                                            |
| -------------- | ----------------------------------------------- |
| **方法** | `PATCH`                                         |
| **路径** | `/api/admin/users/{id}`                         |
| **认证** | 需要登录（仅 `is_admin`）                       |

**请求体（JSON，部分更新）：** 同 4.3 的字段均可选，仅提交需要修改的字段（`username` 可修改，需保持唯一）。另支持 `roles` 字段（角色整体替换）：

| 字段    | 类型  | 说明                                                                 |
| ------- | ----- | -------------------------------------------------------------------- |
| roles   | Array | 可选，整体替换该用户的角色。每项 `{ "role_name" \| "role_id", "expires_at"? }`，`expires_at` 缺省或 `null` 表示永久（ISO-8601 格式，如 `2030-12-31T23:59:59`） |

示例：

```json
{
  "roles": [
    { "role_name": "user" },
    { "role_name": "muted", "expires_at": "2030-12-31T23:59:59" }
  ]
}
```

**约束：**

- 不能取消自己的 `is_admin`（防止锁死）→ `400`
- 不能禁用自己的 `is_enabled` → `400`
- 邮箱/学号/用户名与其他用户重复 → `400`
- `roles` 中的角色不存在或 `expires_at` 格式不合法 → `400`
- 请求体只含 `roles` 时仅更新角色，不改动 users 表其他字段

**成功响应（200）：** `{ "user": { ...更新后结构 } }`。

### 4.5 删除用户

| 项目           | 内容                                            |
| -------------- | ----------------------------------------------- |
| **方法** | `DELETE`                                        |
| **路径** | `/api/admin/users/{id}`                         |
| **认证** | 需要登录（仅 `is_admin`）                       |

**约束：**

- 不能删除自己的账号 → `400`
- 物理删除，**连同该用户的全部文章与评论一并删除**（不可恢复）

**成功响应（200）：**

```json
{ "status": "success", "message": "用户及其文章、评论已删除" }
```

### 4.6 文章列表

| 项目           | 内容                                            |
| -------------- | ----------------------------------------------- |
| **方法** | `GET`                                           |
| **路径** | `/api/admin/articles`                           |
| **认证** | 需要登录（`is_staff` 或 `is_admin`）            |

**查询参数：**

| 参数    | 类型    | 说明                                                             |
| ------- | ------- | ---------------------------------------------------------------- |
| search  | text    | 可选，按标题模糊匹配                                             |
| deleted | Boolean | 可选，`true` 只看已删除，`false` 只看未删除，缺省为全部           |
| hidden  | Boolean | 可选，`true` 只看已隐藏，`false` 只看未隐藏，缺省为全部           |
| page    | int     | 可选，页码，默认 1，每页 10 条                                    |

> 与公开文章列表不同：**包含已删除和已隐藏文章**，每条带 `is_deleted` / `is_hidden` 状态标志与 `author` 摘要。

**成功响应（200）：**

```json
{
  "page_obj": {
    "number": 1,
    "paginator": { "num_pages": 1 },
    "object_list": [
      {
        "index_id": 1,
        "title": "标题",
        "author": { "id": "3f2a...", "username": "alice", "nickname": "Alice" },
        "is_deleted": false,
        "is_hidden": false,
        "created_at": "2026-08-23T10:00:00",
        "updated_at": "2026-08-23T10:00:00"
      }
    ]
  }
}
```

### 4.7 文章详情

| 项目           | 内容                                            |
| -------------- | ----------------------------------------------- |
| **方法** | `GET`                                           |
| **路径** | `/api/admin/articles/{indexId}`                 |
| **认证** | 需要登录（`is_staff` 或 `is_admin`）            |

**成功响应（200）：** 在 4.6 条目结构基础上增加 `content`（正文）、`images` 和 `files`（图片/文件列表）；文章不存在返回 `404`。

### 4.8 编辑文章

| 项目           | 内容                                            |
| -------------- | ----------------------------------------------- |
| **方法** | `PATCH`                                         |
| **路径** | `/api/admin/articles/{indexId}`                 |
| **认证** | 需要登录（仅 `is_admin`）                       |

**请求体（JSON，部分更新）：**

| 字段       | 类型    | 说明                                                         |
| ---------- | ------- | ------------------------------------------------------------ |
| title      | String  | 修改标题（与 content 一起生成新版本，作者不变）               |
| content    | String  | 修改正文（生成新版本，图片/文件关联完整保留）                 |
| is_hidden  | Boolean | `true` 隐藏 / `false` 取消隐藏（作用于最新版本）              |
| is_deleted | Boolean | `true` 软删除 / `false` **恢复已删除文章**（作用于最新版本）   |

> 四个字段均可选，至少提供一个。仅修改标题/内容时，文章原隐藏/删除状态保持不变。

**成功响应（200）：** `{ "article": { ...详情结构 } }`。

### 4.9 评论列表

| 项目           | 内容                                            |
| -------------- | ----------------------------------------------- |
| **方法** | `GET`                                           |
| **路径** | `/api/admin/comments`                           |
| **认证** | 需要登录（`is_staff` 或 `is_admin`）            |

**查询参数：** `search`（按内容匹配）、`deleted`、`hidden`、`page`，含义同 4.6。

> 包含已删除和已隐藏评论，每条带 `author` 摘要、所属文章 `article_index_id` 与 `article_title`。

**成功响应（200）：**

```json
{
  "page_obj": {
    "number": 1,
    "paginator": { "num_pages": 1 },
    "object_list": [
      {
        "index_id": 1,
        "content": "评论内容",
        "author": { "id": "3f2a...", "username": "alice", "nickname": "Alice" },
        "article_index_id": 1,
        "article_title": "文章标题",
        "is_deleted": false,
        "is_hidden": false,
        "created_at": "2026-08-23T10:00:00",
        "updated_at": "2026-08-23T10:00:00"
      }
    ]
  }
}
```

### 4.10 编辑评论

| 项目           | 内容                                              |
| -------------- | ------------------------------------------------- |
| **方法** | `PATCH`                                           |
| **路径** | `/api/admin/comments/{commentIndexId}`            |
| **认证** | 需要登录（仅 `is_admin`）                         |

**请求体（JSON，部分更新）：**

| 字段       | 类型    | 说明                                                          |
| ---------- | ------- | ------------------------------------------------------------- |
| content    | String  | 修改内容（生成新版本，作者与所属文章不变）                     |
| is_hidden  | Boolean | `true` 隐藏 / `false` 取消隐藏（作用于最新版本）                |
| is_deleted | Boolean | `true` 软删除 / `false` **恢复已删除评论**（作用于最新版本）     |

> 三个字段均可选，至少提供一个。

**成功响应（200）：** `{ "comment": { ...同 4.9 条目结构 } }`。

### 4.11 角色列表

| 项目           | 内容                                            |
| -------------- | ----------------------------------------------- |
| **方法** | `GET`                                           |
| **路径** | `/api/admin/roles`                              |
| **认证** | 需要登录（`is_staff` 或 `is_admin`）            |

**成功响应（200）：**

```json
{
  "object_list": [
    {
      "id": 1,
      "role_name": "user",
      "description": "普通用户（注册默认获得）",
      "is_system": true,
      "user_count": 42,
      "permissions": ["article:create", "article:update:own", "comment:create", "comment:update:own"]
    },
    {
      "id": 5,
      "role_name": "muted",
      "description": "禁止发言（无法发表文章和评论）",
      "is_system": true,
      "user_count": 0,
      "permissions": ["!article:create", "!comment:create"]
    }
  ]
}
```

> `user_count` 为拥有该角色的用户数（含已过期分配）。`is_system` 为系统预置角色（不可删除、不可改名）。`permissions` 中的负面权限以 `!` 前缀表示禁止。

### 4.12 权限字典

| 项目           | 内容                                            |
| -------------- | ----------------------------------------------- |
| **方法** | `GET`                                           |
| **路径** | `/api/admin/permissions`                        |
| **认证** | 需要登录（`is_staff` 或 `is_admin`）            |

**成功响应（200）：**

```json
{
  "object_list": [
    { "id": 1, "perm_name": "article:create", "description": "创建文章" },
    { "id": 9, "perm_name": "!article:create", "description": "禁止创建文章（负面权限）" }
  ]
}
```

> 权限为系统预置的固定集合（8 个正面权限 + 2 个负面权限），不支持增删，供创建/编辑角色时勾选。

### 4.13 创建角色

| 项目           | 内容                                            |
| -------------- | ----------------------------------------------- |
| **方法** | `POST`                                          |
| **路径** | `/api/admin/roles`                              |
| **认证** | 需要登录（仅 `is_admin`）                       |

**请求体（JSON）：**

| 字段        | 类型     | 必填 | 说明                                              |
| ----------- | -------- | ---- | ------------------------------------------------- |
| role_name   | String   | 是   | 2-64 位，小写字母开头，仅含小写字母/数字/下划线/连字符，唯一 |
| description | String   | 否   | 角色描述                                          |
| permissions | String[] | 否   | 权限名数组，必须来自权限字典，缺省为空数组         |

**成功响应（200）：** `{ "role": { ...同 4.11 条目结构 } }`；角色名不合法/重复或权限不存在 → `400`。

### 4.14 编辑角色

| 项目           | 内容                                            |
| -------------- | ----------------------------------------------- |
| **方法** | `PATCH`                                         |
| **路径** | `/api/admin/roles/{id}`                         |
| **认证** | 需要登录（仅 `is_admin`）                       |

**路径参数：**

| 参数 | 类型 | 说明     |
| ---- | ---- | -------- |
| id   | int  | 角色 ID  |

**请求体（JSON，部分更新）：**

| 字段        | 类型     | 说明                                       |
| ----------- | -------- | ------------------------------------------ |
| role_name   | String   | 新角色名（系统预置角色不可改名 → `400`）     |
| description | String   | 新描述                                     |
| permissions | String[] | 整体替换该角色的权限（必须来自权限字典）     |

> 三个字段均可选，至少提供一个；角色不存在返回 `404`。

**成功响应（200）：** `{ "role": { ...同 4.11 条目结构 } }`。

### 4.15 删除角色

| 项目           | 内容                                            |
| -------------- | ----------------------------------------------- |
| **方法** | `DELETE`                                        |
| **路径** | `/api/admin/roles/{id}`                         |
| **认证** | 需要登录（仅 `is_admin`）                       |

**约束：**

- 系统预置角色（`is_system=true`）不可删除 → `400`
- 物理删除角色，其用户分配与权限关联由外键级联删除（用户立即失去该角色带来的权限）
- 角色不存在返回 `404`

**成功响应（200）：**

```json
{ "status": "success", "message": "角色已删除" }
```

> **权限判定规则**（全站通用）：访问资源时必须「拥有对应的正面权限 且 没有对应的负面权限（`!` 前缀）」，**或** 当前用户是管理员（`is_admin=true` 直通一切权限）。角色分配带 `expires_at` 有效期的，过期后该角色不再参与权限计算。

---

## 五、数据模型

### 5.1 User（用户）

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
| is_staff       | Boolean  | 是否员工（可访问管理员后端查询），默认 false |
| is_admin       | Boolean  | 是否管理员（可修改管理员后端数据），默认 false |
| is_enabled     | Boolean  | 是否启用（禁用后无法登录），默认 true |
| date_joined    | DateTime | 注册时间                             |
| last_login     | DateTime | 最后登录时间                         |

### 5.2 Article（文章）

| 字段       | 类型     | 说明                                       |
| ---------- | -------- | ------------------------------------------ |
| index_id   | Long     | 文章 ID（主键）                            |
| title      | String   | 文章标题                                   |
| content    | String   | 文章内容（标准 Markdown，图片引用已转换）  |
| created_at | DateTime | 创建时间                                   |
| updated_at | DateTime | 最后更新时间                               |
| author_id  | Long     | 作者 ID（外键关联 User）                   |

### 5.3 Comment（评论）

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

### 5.4 ArticleFile（文章附件）

| 字段       | 类型     | 说明                                                |
| ---------- | -------- | --------------------------------------------------- |
| id         | Long     | 主键                                                |
| title      | String   | 文件名                                              |
| content    | String   | 文件URL路径                                         |
| file_size  | Long     | 文件大小（字节）                                    |
| file_type  | String   | 文件类型：`image` / `file`                      |
| created_at | DateTime | 上传时间                                            |
| article_id | Long     | 所属文章 ID（外键关联 Article，可为空表示临时文件） |

### 5.5 TempFile（临时文件）

| 字段       | 类型     | 说明                       |
| ---------- | -------- | -------------------------- |
| file_id    | String   | 临时文件 ID                |
| filename   | String   | 文件名                     |
| file_size  | Long     | 文件大小（字节）           |
| file_url   | String   | 文件URL路径                |
| user_id    | Long     | 上传者 ID（外键关联 User） |
| created_at | DateTime | 上传时间                   |

> 临时文件在文章创建/编辑时关联到文章，未关联的可定期清理

### 5.6 Role（角色）

| 字段        | 类型     | 说明                                                    |
| ----------- | -------- | ------------------------------------------------------- |
| id          | Integer  | 主键                                                    |
| role_name   | String   | 角色名，2-64 位，小写字母开头，唯一                      |
| description | String   | 角色描述，可选                                          |
| is_system   | Boolean  | 是否系统预置角色（不可删除、不可改名），默认 false       |
| expires_at  | DateTime | 用户角色分配的有效期（`user_roles.expires_at`，null 为永久） |

> 系统预置角色：`user`（普通用户，注册默认获得）、`observer`（观测者）、`moderator`（版主）、`muted`（禁止发言）。
> 用户与角色为多对多（`user_roles`），每条分配可独立设置有效期，过期后该角色不参与权限计算。

### 5.7 Permission（权限）

| 字段        | 类型    | 说明                                                    |
| ----------- | ------- | ------------------------------------------------------- |
| id          | Integer | 主键                                                    |
| perm_name   | String  | 权限名，唯一；`!` 前缀表示负面权限（禁止）               |
| description | String  | 权限描述                                                |

> 权限字典（固定集合）：`article:create`、`article:update:own`、`article:update:any`、`article:view:hidden`、`comment:create`、`comment:update:own`、`comment:update:any`、`comment:view:hidden`，以及负面权限 `!article:create`、`!comment:create`。
> 判定规则：`(拥有正面权限 && 无对应负面权限) || is_admin`。

---

## 六、接口总览

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

### 管理员模块

| #  | 方法   | 路径                                | 说明                       | 认证                     |
| -- | ------ | ----------------------------------- | -------------------------- | ------------------------ |
| 29 | GET    | `/api/admin/users`                | 用户列表（分页 + 搜索）    | staff / admin            |
| 30 | GET    | `/api/admin/users/{id}`           | 用户详情                   | staff / admin            |
| 31 | POST   | `/api/admin/users`                | 创建用户                   | 仅 admin                 |
| 32 | PATCH  | `/api/admin/users/{id}`           | 编辑用户（部分更新）       | 仅 admin                 |
| 33 | DELETE | `/api/admin/users/{id}`           | 删除用户（含文章/评论）    | 仅 admin                 |
| 34 | GET    | `/api/admin/articles`             | 文章列表（含已删/已隐藏）  | staff / admin            |
| 35 | GET    | `/api/admin/articles/{indexId}`   | 文章详情                   | staff / admin            |
| 36 | PATCH  | `/api/admin/articles/{indexId}`   | 编辑文章（内容/隐藏/删除） | 仅 admin                 |
| 37 | GET    | `/api/admin/comments`             | 评论列表（含已删/已隐藏）  | staff / admin            |
| 38 | PATCH  | `/api/admin/comments/{indexId}`   | 编辑评论（内容/隐藏/删除） | 仅 admin                 |
| 39 | GET    | `/api/admin/roles`                | 角色列表（含权限/用户数）  | staff / admin            |
| 40 | GET    | `/api/admin/permissions`          | 权限字典                   | staff / admin            |
| 41 | POST   | `/api/admin/roles`                | 创建角色                   | 仅 admin                 |
| 42 | PATCH  | `/api/admin/roles/{id}`           | 编辑角色（名称/描述/权限） | 仅 admin                 |
| 43 | DELETE | `/api/admin/roles/{id}`           | 删除角色（系统角色除外）   | 仅 admin                 |

### 系统接口

| #  | 方法 | 路径          | 说明                              | 认证 |
| -- | ---- | ------------- | --------------------------------- | ---- |
| 27 | GET  | `/api/csrf` | 获取 CSRF 令牌（XSRF-TOKEN Cookie） | 无   |

---

## 七、前后端对接注意事项

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
