import { marked } from 'marked'
import DOMPurify from 'dompurify'

marked.setOptions({
  breaks: true,
  gfm: true,
})

export interface TocItem {
  level: number
  text: string
  id: string
}

/**
 * 生成标题锚点 id，同一篇文章内重复标题自动加序号
 */
function slugify(text: string, used: Map<string, number>): string {
  const base = text
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s-]/gu, '')
    .replace(/\s+/g, '-')
    .replace(/^-+|-+$/g, '')
  if (!base) {
    const n = used.get('heading') ?? 0
    used.set('heading', n + 1)
    return n === 0 ? 'heading' : `heading-${n}`
  }
  const count = used.get(base) ?? 0
  used.set(base, count + 1)
  return count === 0 ? base : `${base}-${count}`
}

/**
 * 允许的 URI 协议：DOMPurify 默认白名单（http/https/ftp/mailto/tel 等）基础上加 blob:。
 * 创建/编辑文章时图片预览用 URL.createObjectURL 生成 blob: URL，
 * 默认白名单会剥离该 src 导致预览只剩 alt 文件名。
 */
const ALLOWED_URI_REGEXP = /^(?:(?:(?:f|ht)tps?|mailto|tel|callto|sms|cid|xmpp|blob):|[^a-z]|[a-z+.\-]+(?:[^a-z+.\-:]|$))/i

/**
 * 渲染 Markdown 为 HTML，并给标题（h1-h6）添加锚点 id，同时收集标题生成目录。
 * 渲染结果经过 DOMPurify 净化，移除 script/事件属性/javascript: 链接等 XSS 载荷
 */
export function renderMarkdownWithToc(content: string, maxLevel = 3): { html: string; toc: TocItem[] } {
  const html = DOMPurify.sanitize(marked.parse(content || '') as string, {
    ALLOWED_URI_REGEXP,
  })
  const toc: TocItem[] = []
  const used = new Map<string, number>()
  const withIds = html.replace(/<h([1-6])>([\s\S]*?)<\/h\1>/g, (_match, levelStr: string, innerHtml: string) => {
    const level = Number(levelStr)
    const text = innerHtml.replace(/<[^>]+>/g, '').trim()
    const id = slugify(text, used)
    if (level <= maxLevel) {
      toc.push({ level, text, id })
    }
    return `<h${level} id="${id}">${innerHtml}</h${level}>`
  })
  return { html: withIds, toc }
}

/**
 * 渲染 Markdown 为 HTML（标题带锚点 id）
 */
export function renderMarkdown(content: string): string {
  return renderMarkdownWithToc(content).html
}

/**
 * 由标题列表生成目录 HTML（<ul><li><a href="#id">...</a></li></ul>）
 */
export function buildTocHtml(toc: TocItem[]): string {
  if (toc.length === 0) return ''
  const minLevel = Math.min(...toc.map(item => item.level))
  const items = toc
    .map(item => {
      const indent = (item.level - minLevel) * 16
      return `<li style="padding-left:${indent}px"><a href="#${item.id}">${item.text}</a></li>`
    })
    .join('')
  return `<ul>${items}</ul>`
}

/**
 * Markdown 转纯文本（用于列表页摘要），可选截断
 */
export function markdownToText(content: string, maxLength = 0): string {
  const div = document.createElement('div')
  div.innerHTML = renderMarkdown(content || '')
  let text = div.textContent || div.innerText || ''
  text = text.replace(/\s+/g, ' ').trim()
  if (maxLength > 0 && text.length > maxLength) {
    text = text.slice(0, maxLength) + '...'
  }
  return text
}
