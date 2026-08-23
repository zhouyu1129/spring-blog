-- ============================================
-- 用户/角色/权限 建表脚本 (PostgreSQL)
-- ============================================

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username       VARCHAR(64)                    NOT NULL UNIQUE,
    password       VARCHAR(256)                   NOT NULL,
    nickname       VARCHAR(64),
    real_name      VARCHAR(64),
    gender         VARCHAR(16),
    email          VARCHAR(128) UNIQUE,
    email_verified BOOLEAN          DEFAULT FALSE NOT NULL,
    mobile         VARCHAR(32),
    student_number VARCHAR(64)                    NOT NULL UNIQUE,
    is_staff       BOOLEAN          DEFAULT FALSE,
    is_admin       BOOLEAN          DEFAULT FALSE,
    is_enabled     BOOLEAN          DEFAULT TRUE,
    created_at     TIMESTAMP        DEFAULT CURRENT_TIMESTAMP,
    last_logged_at TIMESTAMP
);

-- 文章表（index_id 用序列生成：新文章由 SERIAL 自动分配，新版本沿用同一 index_id）
CREATE TABLE IF NOT EXISTS articles (
    id         UUID PRIMARY KEY   DEFAULT gen_random_uuid(),
    index_id   SERIAL,
    title      VARCHAR(64),
    content    VARCHAR,
    author_id  UUID REFERENCES users (id) ON DELETE CASCADE,
    is_deleted BOOLEAN   NOT NULL DEFAULT FALSE,
    is_hidden  BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_article_index_id ON articles (index_id);

-- 图片表
CREATE TABLE IF NOT EXISTS images (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title      VARCHAR(64),
    path       VARCHAR UNIQUE,
    author_id  UUID REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMP        DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 图片引用表
CREATE TABLE IF NOT EXISTS image_quote (
    article_id UUID REFERENCES articles (id) ON DELETE CASCADE,
    image_id   UUID REFERENCES images (id) ON DELETE CASCADE,
    PRIMARY KEY (article_id, image_id)
);

-- 文件表
CREATE TABLE IF NOT EXISTS files (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title      VARCHAR(64),
    path       VARCHAR UNIQUE,
    author_id  UUID REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMP        DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 文件引用表
CREATE TABLE IF NOT EXISTS file_quote (
    article_id UUID REFERENCES articles (id) ON DELETE CASCADE,
    file_id    UUID REFERENCES files (id) ON DELETE CASCADE,
    PRIMARY KEY (article_id, file_id)
);

-- 评论表
CREATE TABLE IF NOT EXISTS comments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    index_id         SERIAL,
    content          VARCHAR,
    author_id        UUID REFERENCES users (id),
    article_index_id INTEGER, -- 无法使用外键约束，因为article_index_id不唯一
    is_hidden        BOOLEAN          DEFAULT FALSE             NOT NULL,
    is_deleted       BOOLEAN          DEFAULT FALSE             NOT NULL,
    created_at       TIMESTAMP        DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP        DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 角色表
CREATE TABLE IF NOT EXISTS roles (
    id          SERIAL PRIMARY KEY,
    role_name   VARCHAR(64) NOT NULL UNIQUE,
    description VARCHAR(256)
);

-- 权限表
CREATE TABLE IF NOT EXISTS permissions (
    id          SERIAL PRIMARY KEY,
    perm_name   VARCHAR(128) NOT NULL UNIQUE,
    description VARCHAR(256)
);

-- 用户-角色关联表
CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id INTEGER NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- 角色-权限关联表
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id       INTEGER NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id INTEGER NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- ============================================
-- 初始数据
-- ============================================

