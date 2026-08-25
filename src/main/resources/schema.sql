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
CREATE INDEX IF NOT EXISTS idx_articles_author_id ON articles (author_id);

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

-- 按文章查询评论（文章详情页）、按作者查询评论（用户主页）
CREATE INDEX IF NOT EXISTS idx_comments_article_index_id ON comments (article_index_id);
CREATE INDEX IF NOT EXISTS idx_comments_author_id ON comments (author_id);

-- 角色表
CREATE TABLE IF NOT EXISTS roles (
    id          SERIAL PRIMARY KEY,
    role_name   VARCHAR(64) NOT NULL UNIQUE,
    description VARCHAR(256),
    is_system   BOOLEAN      DEFAULT FALSE NOT NULL -- 系统预置角色，不可删除、不可改名
);

-- 权限表（权限字典，预置固定集合，不开放增删）
CREATE TABLE IF NOT EXISTS permissions (
    id          SERIAL PRIMARY KEY,
    perm_name   VARCHAR(128) NOT NULL UNIQUE,
    description VARCHAR(256)
);

-- 用户-角色关联表（expires_at 为角色的有效期，NULL 表示永久）
CREATE TABLE IF NOT EXISTS user_roles (
    user_id    UUID      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id    INTEGER   NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    expires_at TIMESTAMP,
    PRIMARY KEY (user_id, role_id)
);

-- 已有库补列（幂等）
ALTER TABLE roles ADD COLUMN IF NOT EXISTS is_system BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE user_roles ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;

-- 角色-权限关联表
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id       INTEGER NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id INTEGER NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- ============================================
-- 初始数据（幂等，重复执行不产生副作用）
-- ============================================

-- 权限字典：8 个正面权限 + 2 个负面权限（! 前缀表示禁止，判定规则为
-- 「拥有正面权限且没有对应负面权限，或者是管理员」）
INSERT INTO permissions (perm_name, description) VALUES
    ('article:create',       '创建文章'),
    ('article:update:own',   '修改自己的文章'),
    ('article:update:any',   '修改任何文章'),
    ('article:view:hidden',  '查看任何隐藏文章'),
    ('comment:create',       '发表评论'),
    ('comment:update:own',   '修改自己的评论'),
    ('comment:update:any',   '修改任何评论'),
    ('comment:view:hidden',  '查看任何隐藏评论'),
    ('!article:create',      '禁止创建文章（负面权限）'),
    ('!comment:create',      '禁止发表评论（负面权限）')
ON CONFLICT (perm_name) DO NOTHING;

-- 系统预置角色
INSERT INTO roles (role_name, description, is_system) VALUES
    ('user',      '普通用户（注册默认获得）', TRUE),
    ('observer',  '观测者（可查看任何隐藏内容）', TRUE),
    ('moderator', '版主（可查看隐藏内容并修改任何文章、评论）', TRUE),
    ('muted',     '禁止发言（无法发表文章和评论）', TRUE)
ON CONFLICT (role_name) DO NOTHING;

-- 角色-权限关联
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE (r.role_name = 'user' AND p.perm_name IN
        ('article:create', 'article:update:own', 'comment:create', 'comment:update:own'))
   OR (r.role_name = 'observer' AND p.perm_name IN
        ('article:view:hidden', 'comment:view:hidden'))
   OR (r.role_name = 'moderator' AND p.perm_name IN
        ('article:view:hidden', 'comment:view:hidden', 'article:update:any', 'comment:update:any'))
   OR (r.role_name = 'muted' AND p.perm_name IN
        ('!article:create', '!comment:create'))
ON CONFLICT DO NOTHING;

-- -- 存量用户（含默认管理员）补普通用户角色
-- INSERT INTO user_roles (user_id, role_id)
-- SELECT u.id, r.id
-- FROM users u
-- CROSS JOIN roles r
-- WHERE r.role_name = 'user'
-- ON CONFLICT DO NOTHING;
