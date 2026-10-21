import { marked } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/common'
import TurndownService from 'turndown'
// @ts-expect-error package has no declaration
import { gfm } from 'turndown-plugin-gfm'
const td = new TurndownService({
  headingStyle: 'atx',
  codeBlockStyle: 'fenced',
  bulletListMarker: '-',
})
td.use(gfm)
td.addRule('fencedCode', {
  filter: (n) => n.nodeName === 'PRE' && !!n.firstChild && n.firstChild.nodeName === 'CODE',
  replacement: (_c, n) => {
    const code = n.firstChild as HTMLElement
    const lang = code.className.replace('language-', '')
    return '\n\n```' + lang + '\n' + code.textContent + '\n```\n\n'
  },
})
export function toMarkdown(html: string) {
  return td.turndown(html)
}
export function renderMarkdown(content: string) {
  const html = DOMPurify.sanitize(marked.parse(content, { async: false, gfm: true }) as string, {
    FORBID_TAGS: ['iframe', 'style', 'form', 'input', 'script'],
    FORBID_ATTR: ['style'],
  })
  const el = document.createElement('div')
  el.innerHTML = html
  el.querySelectorAll('pre code').forEach((code) => hljs.highlightElement(code as HTMLElement))
  el.querySelectorAll('h1,h2,h3').forEach((h, i) => (h.id = 'heading-' + i))
  el.querySelectorAll('a').forEach((a) => {
    a.setAttribute('rel', 'noopener noreferrer')
    if (/^https?:/i.test(a.href) && new URL(a.href).origin !== location.origin) a.target = '_blank'
  })
  el.querySelectorAll('img').forEach((img) => {
    if (!img.getAttribute('src')?.startsWith('/') || img.getAttribute('src')?.startsWith('//')) {
      img.remove()
      return
    }
    img.loading = 'lazy'
    img.alt ||= '笔记图片'
  })
  return el.innerHTML
}
export function excerpt(text: string) {
  return text
    .replace(/!\[[^\]]*\]\([^)]*\)/g, '')
    .replace(/[#>*`\[\]_|~-]/g, '')
    .replace(/\n/g, ' ')
    .trim()
}
