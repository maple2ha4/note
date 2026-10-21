<script setup lang="ts">
import {
  ref,
  computed,
  onMounted,
  onBeforeUnmount,
  nextTick,
  watch,
  defineAsyncComponent,
} from 'vue'
import Icon from './Icon.vue'
const NoteEditor = defineAsyncComponent(() => import('./NoteEditor.vue'))
import { api, refreshCsrf } from './api'
import type { Note, Space, Session } from './types'
import { excerpt, renderMarkdown } from './markdown'
const spaces: Record<Space, { name: string; icon: string; description: string; en: string }> = {
  knowledge: {
    name: '知识笔记',
    icon: 'BookOpen',
    description: '让知识生根，让理解生长。',
    en: 'KNOWLEDGE',
  },
  thoughts: {
    name: '思考心得',
    icon: 'Lightbulb',
    description: '捕捉灵感，听见自己的声音。',
    en: 'THOUGHTS',
  },
  diary: {
    name: '生活日记',
    icon: 'Leaf',
    description: '收藏日常里，那些微小的美好。',
    en: 'LIFE JOURNAL',
  },
}
let syncTimer: ReturnType<typeof setInterval>
async function syncNotes() {
  if (
    !session.value?.authenticated ||
    editing.value ||
    document.hidden ||
    busy.value ||
    searchOpen.value ||
    shareOpen.value ||
    confirmDelete.value
  )
    return
  try {
    await loadNotes()
    if (current.value) {
      const latest = notes.value.find((n) => n.id === current.value?.id)
      if (latest) current.value = latest
      else current.value = undefined
    }
  } catch {}
}
const session = ref<Session>(),
  notes = ref<Note[]>([]),
  loading = ref(true),
  loginPassword = ref(''),
  loginError = ref(''),
  loggingIn = ref(false),
  error = ref(''),
  view = ref('dashboard'),
  current = ref<Note>(),
  editing = ref<Note>(),
  mobileNav = ref(false),
  searchOpen = ref(false),
  query = ref(''),
  searchInput = ref<HTMLInputElement>(),
  listQuery = ref(''),
  category = ref(''),
  tag = ref(''),
  sort = ref('updated'),
  layout = ref('grid'),
  toast = ref(''),
  busy = ref(false),
  confirmDelete = ref(false),
  shareOpen = ref(false),
  shareConsent = ref(false),
  importInput = ref<HTMLInputElement>(),
  restoreInput = ref<HTMLInputElement>(),
  categoryNew = ref(''),
  categoryFrom = ref(''),
  manageTab = ref('category')
let toastTimer: ReturnType<typeof setTimeout>
const theme = ref(localStorage.getItem('liubai-theme') || 'light')
const mobileQuery = window.matchMedia('(max-width: 720px)')
const isMobile = ref(mobileQuery.matches)
function updateMobile() {
  isMobile.value = mobileQuery.matches
}
mobileQuery.addEventListener('change', updateMobile)
watch(mobileNav, async (open) => {
  await nextTick()
  if (open) document.querySelector<HTMLElement>('.sidebar .brand')?.focus()
  else if (isMobile.value) document.querySelector<HTMLElement>('.mobile-menu')?.focus()
})
const systemDark = window.matchMedia('(prefers-color-scheme: dark)')
function applyTheme() {
  document.documentElement.dataset.theme =
    theme.value === 'system' ? (systemDark.matches ? 'dark' : 'light') : theme.value
  localStorage.setItem('liubai-theme', theme.value)
}
watch(theme, applyTheme)
systemDark.addEventListener('change', applyTheme)
applyTheme()
const today = new Date(),
  dateText = new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long',
    timeZone: 'Asia/Shanghai',
  }).format(today)
const isoToday = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
}).format(today)
const calendarMonth = ref(new Date(isoToday + 'T12:00:00')),
  selectedDay = ref('')
const calendarLabel = computed(() =>
  new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long' }).format(calendarMonth.value),
)
const calendarDays = computed(() => {
  const d = calendarMonth.value
  const first = new Date(d.getFullYear(), d.getMonth(), 1)
  const count = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate()
  const pad = (first.getDay() + 6) % 7
  return [
    ...Array(pad).fill(null),
    ...Array.from({ length: count }, (_, i) => {
      const date = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(i + 1).padStart(2, '0')}`
      return { day: i + 1, date, has: notes.value.some((n) => n.entryDate === date) }
    }),
  ]
})
function moveMonth(delta: number) {
  calendarMonth.value = new Date(
    calendarMonth.value.getFullYear(),
    calendarMonth.value.getMonth() + delta,
    1,
  )
}
function dayFilter(date: string) {
  go('diary')
  selectedDay.value = date
}
const count = (space: Space) => notes.value.filter((n) => n.space === space).length
const favorites = computed(() => notes.value.filter((n) => n.favorite))
const featured = computed(() => {
  const result: Note[] = []
  for (const s of Object.keys(spaces) as Space[]) {
    const n = favorites.value.find((n) => n.space === s) || notes.value.find((n) => n.space === s)
    if (n) result.push(n)
  }
  return result
})
const categories = computed(() =>
  [...new Set(notes.value.map((n) => n.category).filter(Boolean))].sort(),
)
const tags = computed(() => [...new Set(notes.value.flatMap((n) => n.tags))].sort())
const wordCount = computed(() =>
  notes.value.reduce((a, n) => a + n.content.replace(/\s/g, '').length, 0),
)
const thisWeek = computed(
  () =>
    notes.value.filter((n) => new Date(n.updatedAt).getTime() > Date.now() - 7 * 86400000).length,
)
const title = computed(() =>
  current.value
    ? current.value.title
    : editing.value
      ? '写一篇新记录'
      : view.value === 'dashboard'
        ? '个人工作台'
        : view.value in spaces
          ? spaces[view.value as Space].name
          : view.value === 'favorites'
            ? '我的收藏'
            : view.value === 'drafts'
              ? '草稿箱'
              : view.value === 'settings'
                ? '设置与备份'
                : view.value === 'manage'
                  ? '分类与标签'
                  : '所有记录',
)
const filtered = computed(() => {
  let result = notes.value.filter((n) =>
    view.value in spaces
      ? n.space === view.value
      : view.value === 'favorites'
        ? n.favorite
        : view.value === 'drafts'
          ? n.draft
          : true,
  )
  const q = listQuery.value.trim().toLocaleLowerCase()
  if (q)
    result = result.filter((n) =>
      [n.title, n.content, n.category, ...n.tags].join(' ').toLocaleLowerCase().includes(q),
    )
  if (category.value) result = result.filter((n) => n.category === category.value)
  if (tag.value) result = result.filter((n) => n.tags.includes(tag.value))
  if (selectedDay.value) result = result.filter((n) => n.entryDate === selectedDay.value)
  return [...result].sort((a, b) =>
    sort.value === 'title'
      ? a.title.localeCompare(b.title, 'zh')
      : sort.value === 'created'
        ? b.createdAt.localeCompare(a.createdAt)
        : sort.value === 'date'
          ? b.entryDate.localeCompare(a.entryDate)
          : b.updatedAt.localeCompare(a.updatedAt),
  )
})
const results = computed(() => {
  const q = query.value.trim().toLocaleLowerCase()
  return (
    q
      ? notes.value.filter((n) =>
          [n.title, n.content, n.category, ...n.tags].join(' ').toLocaleLowerCase().includes(q),
        )
      : notes.value
  ).slice(0, 30)
})
const toc = computed(() => {
  const el = document.createElement('div')
  el.innerHTML = renderMarkdown(current.value?.content || '')
  return Array.from(el.querySelectorAll('h1,h2,h3')).map((h) => ({
    id: h.id,
    text: h.textContent,
    level: h.tagName,
  }))
})
const locationOrigin = location.origin
function trapFocus(e: KeyboardEvent) {
  const root = e.currentTarget as HTMLElement
  const els = Array.from(
    root.querySelectorAll<HTMLElement>(
      'button:not([disabled]), input:not([disabled]), a[href], select, textarea, [tabindex="0"]',
    ),
  )
  if (!els.length) return
  const first = els[0],
    last = els[els.length - 1]
  if (e.shiftKey && document.activeElement === first) {
    e.preventDefault()
    last.focus()
  } else if (!e.shiftKey && document.activeElement === last) {
    e.preventDefault()
    first.focus()
  }
}
const publicPath = location.pathname.startsWith('/share/'),
  publicError = ref('')
function notify(message: string) {
  toast.value = message
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (toast.value = ''), 4200)
}
async function loadNotes() {
  notes.value = await api<Note[]>('/notes')
}
async function init() {
  try {
    session.value = await api<Session>('/auth/session')
    if (session.value.authenticated) await loadNotes()
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}
async function login() {
  loggingIn.value = true
  loginError.value = ''
  try {
    await refreshCsrf()
    await api('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ password: loginPassword.value }),
    })
    loginPassword.value = ''
    await refreshCsrf()
    await init()
  } catch (e) {
    loginError.value = (e as Error).message
  } finally {
    loggingIn.value = false
  }
}
async function logout() {
  try {
    await api('/auth/logout', { method: 'POST' })
    notes.value = []
    session.value = undefined
    current.value = undefined
    editing.value = undefined
    view.value = 'dashboard'
    await refreshCsrf()
    await init()
  } catch (e) {
    notify((e as Error).message)
  }
}
function go(next: string) {
  if (editing.value) {
    notify('请先保存记录，或使用编辑器的返回按钮。')
    return
  }
  view.value = next
  current.value = undefined
  mobileNav.value = false
  category.value = ''
  tag.value = ''
  listQuery.value = ''
  selectedDay.value = ''
  window.scrollTo({ top: 0 })
}
function open(n: Note) {
  current.value = n
  searchOpen.value = false
  mobileNav.value = false
  window.scrollTo({ top: 0 })
}
async function createNote(space: Space = 'thoughts') {
  if (editing.value) return
  busy.value = true
  try {
    const n = await api<Note>('/notes', {
      method: 'POST',
      body: JSON.stringify({
        title: '未命名记录',
        content: '',
        space,
        category: '',
        tags: [],
        favorite: false,
        published: false,
        draft: true,
        entryDate: isoToday,
        version: 0,
      }),
    })
    notes.value.unshift(n)
    editing.value = n
    current.value = undefined
    mobileNav.value = false
  } catch (e) {
    notify((e as Error).message)
  } finally {
    busy.value = false
  }
}
async function saved(n: Note) {
  notes.value = notes.value
    .map((x) => (x.id === n.id ? n : x))
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
  editing.value = undefined
  current.value = n
  notify(n.draft ? '草稿已安全保存' : '记录已保存到你的花园')
}
async function updateNote(n: Note, patch: Partial<Note>) {
  const updated = await api<Note>('/notes/' + n.id, {
    method: 'PUT',
    body: JSON.stringify({ ...n, ...patch }),
  })
  notes.value = notes.value.map((x) => (x.id === n.id ? updated : x))
  if (current.value?.id === n.id) current.value = updated
  return updated
}
async function favorite(n: Note) {
  try {
    await updateNote(n, { favorite: !n.favorite })
    notify(n.favorite ? '已取消收藏' : '已加入收藏')
  } catch (e) {
    notify((e as Error).message)
  }
}
async function remove() {
  if (!current.value) return
  busy.value = true
  try {
    await api('/notes/' + current.value.id, { method: 'DELETE' })
    notes.value = notes.value.filter((n) => n.id !== current.value?.id)
    current.value = undefined
    confirmDelete.value = false
    notify('记录已删除')
  } catch (e) {
    notify((e as Error).message)
  } finally {
    busy.value = false
  }
}
async function togglePublish() {
  if (!current.value) return
  if (!current.value.published && !shareConsent.value) {
    notify('请先确认你已检查内容与附件')
    return
  }
  busy.value = true
  try {
    await updateNote(current.value, { published: !current.value.published, draft: false })
    notify(
      current.value.published
        ? session.value?.publicAccess
          ? '记录已公开，可复制分享链接'
          : '已标记公开；私人预览期间外部仍不可访问'
        : '已改为私密，分享链接已失效',
    )
    shareOpen.value = false
    shareConsent.value = false
  } catch (e) {
    notify((e as Error).message)
  } finally {
    busy.value = false
  }
}
async function copyLink() {
  if (!current.value) return
  try {
    await navigator.clipboard.writeText(location.origin + '/share/' + current.value.shareId)
    notify('分享链接已复制')
  } catch {
    notify('当前浏览器不支持复制，请从分享框选中链接复制')
  }
}
function downloadText(text: string, name: string, type = 'text/plain') {
  const url = URL.createObjectURL(new Blob([text], { type }))
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
async function backup() {
  try {
    const data = await api('/backup')
    downloadText(
      JSON.stringify(data, null, 2),
      'liubai-backup-' + isoToday + '.json',
      'application/json',
    )
    notify('JSON 备份已下载，请同时保留服务器附件备份')
  } catch (e) {
    notify((e as Error).message)
  }
}
function exportCurrent() {
  if (current.value)
    downloadText(
      '# ' + current.value.title + '\n\n' + current.value.content,
      current.value.title.replace(/[\\/:*?"<>|]/g, '_') + '.md',
      'text/markdown',
    )
}
async function importFiles(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  if (files.length > 50) {
    notify('每次最多导入 50 个文件')
    input.value = ''
    return
  }
  busy.value = true
  let imported = 0
  try {
    for (const file of files) {
      if (file.size > 1000000 || !file.name.toLowerCase().endsWith('.md'))
        throw new Error('请选择 1MB 以内的 Markdown 文件')
      let content = await file.text(),
        metadata: Record<string, unknown> = {}
      const front = content.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n/)
      if (front) {
        for (const line of front[1].split('\n')) {
          const i = line.indexOf(':')
          if (i < 0) continue
          const key = line.slice(0, i).trim(),
            value = line.slice(i + 1).trim()
          try {
            metadata[key] = JSON.parse(value)
          } catch {
            metadata[key] = value
          }
        }
        content = content.slice(front[0].length).trimStart()
      }
      const heading = content.match(/^# (.+)\r?\n/),
        name =
          typeof metadata.title === 'string'
            ? metadata.title
            : heading?.[1] || file.name.replace(/\.md$/i, '')
      if (heading) content = content.replace(/^# .+\r?\n/, '').trimStart()
      const space =
        typeof metadata.space === 'string' && metadata.space in spaces
          ? metadata.space
          : view.value in spaces
            ? view.value
            : 'knowledge'
      const n = await api<Note>('/notes', {
        method: 'POST',
        body: JSON.stringify({
          title: name,
          content,
          space,
          category: typeof metadata.category === 'string' ? metadata.category : '导入笔记',
          tags: Array.isArray(metadata.tags) ? metadata.tags : [],
          favorite: false,
          published: false,
          draft: true,
          entryDate:
            typeof metadata.date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(metadata.date)
              ? metadata.date
              : isoToday,
          version: 0,
        }),
      })
      notes.value.unshift(n)
      imported++
    }
    notify(`已导入 ${imported} 篇私密草稿`)
    go('drafts')
  } catch (e) {
    notify(`已导入 ${imported} 篇；` + (e as Error).message)
  } finally {
    busy.value = false
    input.value = ''
  }
}
async function restoreFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > 20 * 1024 * 1024) {
    notify('备份文件不能超过 20MB')
    input.value = ''
    return
  }
  busy.value = true
  try {
    const data = JSON.parse(await file.text())
    const result = await api<{ imported: number }>('/restore', {
      method: 'POST',
      body: JSON.stringify(data),
    })
    await loadNotes()
    notify(`已恢复 ${result.imported} 篇记录，全部为私密草稿`)
  } catch (e) {
    notify('恢复失败：' + (e as Error).message)
  } finally {
    busy.value = false
    input.value = ''
  }
}
async function renameTaxonomy() {
  const from = categoryFrom.value.trim(),
    to = categoryNew.value.trim()
  if (!from || !to) {
    notify('请选择原名称并填写新名称')
    return
  }
  if (to.length > (manageTab.value === 'category' ? 80 : 40)) {
    notify('名称过长')
    return
  }
  busy.value = true
  try {
    const affected = notes.value.filter((n) =>
      manageTab.value === 'category' ? n.category === from : n.tags.includes(from),
    )
    let changed = 0
    for (const n of affected) {
      await updateNote(
        n,
        manageTab.value === 'category'
          ? { category: to }
          : { tags: [...new Set(n.tags.map((t) => (t === from ? to : t)))] },
      )
      changed++
    }
    notify(`已更新 ${changed} 篇记录`)
    categoryFrom.value = ''
    categoryNew.value = ''
  } catch (e) {
    notify((e as Error).message)
  } finally {
    busy.value = false
  }
}
function dateShort(date: string) {
  return new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric' }).format(
    new Date(date.slice(0, 10) + 'T12:00:00'),
  )
}
function updatedLabel(n: Note) {
  const d = n.updatedAt.slice(0, 10)
  return d === isoToday ? '今天' : dateShort(d)
}
function search() {
  searchOpen.value = true
  query.value = ''
  nextTick(() => searchInput.value?.focus())
}
function keyboard(e: KeyboardEvent) {
  if (
    (e.metaKey || e.ctrlKey) &&
    e.key.toLowerCase() === 'k' &&
    session.value?.authenticated &&
    !editing.value
  ) {
    e.preventDefault()
    search()
  }
  if (e.key === 'Escape') {
    searchOpen.value = false
    shareOpen.value = false
    confirmDelete.value = false
    mobileNav.value = false
  }
}
function tagFilter(t: string) {
  go('knowledge')
  view.value = 'all'
  tag.value = t
}
onMounted(async () => {
  window.addEventListener('keydown', keyboard)
  window.addEventListener('focus', syncNotes)
  syncTimer = setInterval(syncNotes, 30000)
  if (publicPath) {
    try {
      current.value = await api<Note>(
        '/public/' + encodeURIComponent(location.pathname.split('/')[2] || ''),
      )
    } catch {
      publicError.value = '这篇记录尚未公开，或分享已结束。'
    } finally {
      loading.value = false
    }
  } else await init()
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', keyboard)
  window.removeEventListener('focus', syncNotes)
  clearInterval(syncTimer)
  systemDark.removeEventListener('change', applyTheme)
  mobileQuery.removeEventListener('change', updateMobile)
  clearTimeout(toastTimer)
})
let previousFocus: HTMLElement | null = null
watch([searchOpen, shareOpen, confirmDelete], async ([a, b, c]) => {
  document.body.style.overflow = a || b || c ? 'hidden' : ''
  if (a || b || c) {
    previousFocus = document.activeElement as HTMLElement
    await nextTick()
    document.querySelector<HTMLElement>('.modal-backdrop input, .modal-backdrop button')?.focus()
  } else previousFocus?.focus()
})
</script>

<template>
  <div v-if="loading" class="initial-loading">
    <div class="brand-mark"><Icon name="BookOpen" :size="24" /></div>
    <span>为思绪，留一点白。</span><Icon name="LoaderCircle" class="spin" />
  </div>
  <div v-else-if="publicPath" class="public-page">
    <header>
      <a href="/" class="brand"
        ><span class="brand-mark"><Icon name="BookOpen" :size="22" /></span
        ><span>留白<span class="brand-en"> / Notes</span></span></a
      ><span class="muted small">来自一座私人数字花园</span
      ><button
        class="icon-button"
        aria-label="切换深色模式"
        @click="theme = theme === 'dark' ? 'light' : 'dark'"
      >
        <Icon :name="theme === 'dark' ? 'Sun' : 'Moon'" />
      </button>
    </header>
    <div v-if="publicError" class="empty-state">
      <Icon name="LockKeyhole" :size="36" />
      <h1>留一点空间给自己。</h1>
      <p>{{ publicError }}</p>
      <a class="btn" href="/">返回留白</a>
    </div>
    <article v-else-if="current" class="public-article">
      <span class="eyebrow">{{ spaces[current.space].en }}</span>
      <h1>{{ current.title }}</h1>
      <div class="article-meta">
        <span>{{ dateShort(current.entryDate) }}</span
        ><span>{{ current.category }}</span
        ><span>约 {{ Math.max(1, Math.ceil(current.content.length / 400)) }} 分钟阅读</span>
      </div>
      <div class="prose" v-html="renderMarkdown(current.content)"></div>
      <footer class="article-end">记录于 留白 · Notes</footer>
    </article>
  </div>
  <div v-else-if="!session?.authenticated" class="login-page">
    <div class="login-top">
      <a class="brand" href="/"
        ><span class="brand-mark"><Icon name="BookOpen" :size="23" /></span
        ><span>留白<span class="brand-en"> / Notes</span></span></a
      ><button
        class="icon-button"
        aria-label="切换深色模式"
        @click="theme = theme === 'dark' ? 'light' : 'dark'"
      >
        <Icon :name="theme === 'dark' ? 'Sun' : 'Moon'" />
      </button>
    </div>
    <div class="login-main">
      <section class="login-story">
        <div class="eyebrow"><span class="tiny-line"></span> YOUR PERSONAL DIGITAL GARDEN</div>
        <h1>让思绪落笔，<br />让生活<span>留白。</span></h1>
        <p>一处安静的角落，安放知识与灵感，<br />也珍藏那些平凡而闪光的日子。</p>
        <div class="login-spaces">
          <span><Icon name="BookOpen" /> 知识</span><span><Icon name="Lightbulb" /> 思考</span
          ><span><Icon name="Leaf" /> 生活</span>
        </div>
        <div class="login-art">
          <img src="/landscape.svg" alt="远山、森林与晨光的插画" /><span
            >GROW AT YOUR OWN PACE.</span
          >
        </div>
      </section>
      <section class="login-card">
        <span class="login-flower"><Icon name="Sprout" :size="30" /></span
        ><span class="eyebrow">A SPACE OF YOUR OWN</span>
        <h2>欢迎回到你的花园</h2>
        <p>慢慢记录，每一笔都算数。</p>
        <form v-if="session?.localLogin" @submit.prevent="login">
          <label for="password">私人预览密码</label>
          <div class="password-field">
            <Icon name="LockKeyhole" :size="17" /><input
              id="password"
              v-model="loginPassword"
              type="password"
              autocomplete="current-password"
              required
              placeholder="输入你的预览密码"
            />
          </div>
          <p v-if="loginError" class="form-error" role="alert">{{ loginError }}</p>
          <button class="btn primary login-submit" :disabled="loggingIn">
            <Icon v-if="loggingIn" name="LoaderCircle" class="spin" />{{
              loggingIn ? '正在打开花园…' : '进入我的留白'
            }}<Icon v-if="!loggingIn" name="ArrowRight" :size="17" />
          </button>
        </form>
        <a v-if="session?.oidc" class="btn oidc-button" href="/oauth2/authorization/owner"
          ><Icon name="ShieldCheck" /> 使用已配置的身份账号登录</a
        >
        <div v-if="!session?.oidc" class="login-provider-note">
          <Icon name="ShieldCheck" :size="16" /><span
            >当前为密码保护的私人预览<br /><small>ChatGPT 账号登录尚未接通</small></span
          >
        </div>
        <p v-if="error" class="form-error">{{ error }}</p>
        <div class="login-security">
          <Icon name="LockKeyhole" :size="13" /> 你的记录默认私密，仅对你可见。
        </div>
      </section>
    </div>
    <footer class="login-footer">
      <span>留白 · Notes</span><span>记录，是与自己温柔相处的方式。</span
      ><span>PRIVATE BY DEFAULT</span>
    </footer>
  </div>
  <div v-else class="app-shell" :inert="searchOpen || shareOpen || confirmDelete || undefined">
    <button
      v-if="mobileNav"
      class="sidebar-scrim"
      aria-label="关闭导航"
      @click="mobileNav = false"
    ></button>
    <aside
      class="sidebar"
      :class="{ open: mobileNav }"
      :inert="(isMobile && !mobileNav) || undefined"
      @keydown.tab="isMobile ? trapFocus($event) : undefined"
    >
      <button class="brand" @click="go('dashboard')">
        <span class="brand-mark"><Icon name="BookOpen" :size="23" /></span
        ><span>留白<span class="brand-en"> / Notes</span></span>
      </button>
      <button class="sidebar-search" @click="search">
        <Icon name="Search" :size="16" /><span>搜索你的花园</span><kbd>⌘ K</kbd>
      </button>
      <nav aria-label="主导航">
        <div class="nav-label">我的花园</div>
        <button
          class="nav-item"
          :class="{ active: view === 'dashboard' && !current && !editing }"
          @click="go('dashboard')"
        >
          <Icon name="LayoutDashboard" />个人工作台<span
            v-if="view === 'dashboard'"
            class="nav-active-dot"
          ></span></button
        ><button
          class="nav-item"
          :class="{ active: view === 'favorites' && !current && !editing }"
          @click="go('favorites')"
        >
          <Icon name="Star" />我的收藏<span class="nav-count">{{ favorites.length }}</span></button
        ><button
          class="nav-item"
          :class="{ active: view === 'drafts' && !current && !editing }"
          @click="go('drafts')"
        >
          <Icon name="FileText" />草稿箱<span class="nav-count">{{
            notes.filter((n) => n.draft).length
          }}</span>
        </button>
        <div class="nav-label spaces-label">内容空间<span>SPACE</span></div>
        <button
          v-for="(s, key) in spaces"
          :key="key"
          class="nav-item"
          :class="{ active: view === key && !current && !editing }"
          @click="go(key)"
        >
          <Icon :name="s.icon" :class="'space-icon-' + key" />{{ s.name
          }}<span class="nav-count">{{ count(key) }}</span>
        </button>
        <div class="nav-divider"></div>
        <button
          class="nav-item"
          :class="{ active: view === 'manage' && !current && !editing }"
          @click="go('manage')"
        >
          <Icon name="Folder" />分类与标签</button
        ><button class="nav-item" @click="importInput?.click()">
          <Icon name="Upload" />导入 Markdown
        </button>
      </nav>
      <div class="sidebar-bottom">
        <div class="private-note">
          <span class="private-icon"><Icon name="ShieldCheck" :size="17" /></span>
          <div>
            <strong>这是一座私人花园</strong>
            <p>你的每一篇记录，默认仅自己可见。</p>
          </div>
        </div>
        <div class="sidebar-settings">
          <button
            class="text-button"
            :class="{ active: view === 'settings' }"
            @click="go('settings')"
          >
            <Icon name="Settings2" :size="16" /> 设置与备份
          </button>
          <div class="theme-switch">
            <button
              v-for="t in [
                { id: 'light', icon: 'Sun', name: '浅色模式' },
                { id: 'dark', icon: 'Moon', name: '深色模式' },
                { id: 'system', icon: 'Monitor', name: '跟随系统' },
              ]"
              :key="t.id"
              :class="{ active: theme === t.id }"
              :aria-label="t.name"
              :title="t.name"
              :aria-pressed="theme === t.id"
              @click="theme = t.id"
            >
              <Icon :name="t.icon" :size="13" />
            </button>
          </div>
        </div>
        <div class="profile">
          <span class="avatar">白</span>
          <div>
            <strong>留白的主人</strong><span><i></i>个人空间</span>
          </div>
          <button class="icon-button" aria-label="退出登录" title="退出登录" @click="logout">
            <Icon name="LogOut" :size="16" />
          </button>
        </div>
      </div>
    </aside>
    <div class="main-shell">
      <header class="topbar">
        <div class="breadcrumb">
          <button class="icon-button mobile-menu" aria-label="打开导航" @click="mobileNav = true">
            <Icon name="Menu" /></button
          ><button class="breadcrumb-garden" @click="go('dashboard')">我的花园</button
          ><Icon name="ChevronRight" :size="13" /><span class="breadcrumb-current">{{
            title
          }}</span>
        </div>
        <div class="topbar-right">
          <span class="private-pill"><i></i> 私人预览</span><span class="topbar-divider"></span
          ><button class="icon-button" title="全局搜索" aria-label="全局搜索" @click="search">
            <Icon name="Search" /></button
          ><span class="topbar-avatar">白</span>
        </div>
      </header>
      <main
        id="main-content"
        class="main-content"
        :class="{ 'reading-main': current, 'editing-main': editing }"
      >
        <NoteEditor
          v-if="editing"
          :key="editing.id"
          :note="editing"
          @saved="saved"
          @cancel="editing = undefined"
          @error="notify"
        />
        <section v-else-if="current" class="reader-page">
          <div class="page-actions">
            <button class="text-button" @click="current = undefined">
              <Icon name="ArrowLeft" /> 返回花园
            </button>
            <div class="reader-actions">
              <button
                class="icon-button"
                :class="{ starred: current.favorite }"
                :aria-label="current.favorite ? '取消收藏' : '收藏文章'"
                @click="favorite(current)"
              >
                <Icon name="Star" /></button
              ><button
                class="icon-button"
                title="导出 Markdown"
                aria-label="导出当前文章"
                @click="exportCurrent"
              >
                <Icon name="Download" /></button
              ><button
                class="btn"
                @click="
                  () => {
                    shareOpen = true
                    shareConsent = false
                  }
                "
              >
                <Icon name="Share2" :size="15" /> 分享</button
              ><button
                class="btn primary"
                @click="
                  () => {
                    editing = current
                    current = undefined
                  }
                "
              >
                <Icon name="PenLine" :size="15" /> 编辑
              </button>
            </div>
          </div>
          <div class="reading-layout">
            <article class="article-sheet">
              <div class="article-space" :class="current.space">
                <Icon :name="spaces[current.space].icon" :size="15" />{{ spaces[current.space].name
                }}<span>/</span>{{ current.category || '未分类' }}
              </div>
              <h1>{{ current.title }}</h1>
              <div class="article-meta">
                <span
                  ><Icon name="CalendarDays" :size="14" />{{ dateShort(current.entryDate) }}</span
                ><span>{{ current.content.replace(/\s/g, '').length }} 字</span
                ><span>约 {{ Math.max(1, Math.ceil(current.content.length / 400)) }} 分钟阅读</span
                ><span class="privacy-label"
                  ><Icon :name="current.published ? 'Globe2' : 'LockKeyhole'" :size="13" />{{
                    current.published ? '已标记公开' : '仅自己可见'
                  }}</span
                ><span v-if="current.draft" class="draft-badge">草稿</span>
              </div>
              <div class="article-tags">
                <span v-for="t in current.tags" :key="t"># {{ t }}</span>
              </div>
              <div class="prose article-content" v-html="renderMarkdown(current.content)"></div>
              <div v-if="!current.content" class="empty-state">
                <Icon name="PenLine" :size="30" />
                <p>这张白纸，正在等你的第一个想法。</p>
                <button
                  class="btn"
                  @click="
                    () => {
                      editing = current
                      current = undefined
                    }
                  "
                >
                  开始写作
                </button>
              </div>
              <footer class="article-end">
                <Icon name="Sprout" :size="21" /><span>每一次记录，都在让自己更丰盛。</span>
              </footer>
              <div class="article-bottom">
                <span>更新于 {{ dateShort(current.updatedAt) }}</span
                ><button class="text-button danger" @click="confirmDelete = true">
                  <Icon name="Trash2" :size="14" /> 删除记录
                </button>
              </div>
            </article>
            <aside class="toc">
              <div class="eyebrow">ON THIS PAGE</div>
              <h4>这篇记录</h4>
              <a
                v-for="h in toc"
                :key="h.id"
                :href="'#' + h.id"
                :class="{ 'toc-sub': h.level === 'H3' }"
                >{{ h.text }}</a
              >
              <p v-if="!toc.length" class="small muted">慢慢读，不必着急。</p>
              <div class="toc-note">
                <Icon name="LockKeyhole" :size="16" />
                <p>私密，是留白的默认设置。<br />分享由你决定。</p>
              </div>
            </aside>
          </div>
        </section>
        <template v-else-if="view === 'dashboard'">
          <section class="welcome">
            <div>
              <div class="eyebrow welcome-date"><span class="tiny-line"></span>{{ dateText }}</div>
              <h1>把今天，<span>留一点给自己。</span><span class="greeting-dot">✳</span></h1>
              <p>记录所学，安放所想，收藏生活的每一束微光。</p>
            </div>
            <button class="btn primary new-note" :disabled="busy" @click="createNote()">
              <Icon name="Plus" :size="17" /> 写一篇新记录
            </button>
          </section>
          <div class="dashboard-grid">
            <div class="dashboard-main">
              <section class="stats-strip" aria-label="记录统计">
                <div>
                  <span class="stat-icon"><Icon name="NotebookPen" :size="20" /></span>
                  <div>
                    <strong>{{ notes.length }}<small>篇</small></strong
                    ><span>所有记录</span>
                  </div>
                </div>
                <div>
                  <span class="stat-icon"><Icon name="PenLine" :size="19" /></span>
                  <div>
                    <strong>{{ (wordCount / 1000).toFixed(1) }}<small>k</small></strong
                    ><span>写下的文字</span>
                  </div>
                </div>
                <div>
                  <span class="stat-icon"><Icon name="Sprout" :size="21" /></span>
                  <div>
                    <strong>{{ thisWeek }}<small>篇</small></strong
                    ><span>近七天更新</span>
                  </div>
                </div>
                <div>
                  <span class="stat-icon"><Icon name="Star" :size="19" /></span>
                  <div>
                    <strong>{{ favorites.length }}<small>篇</small></strong
                    ><span>心动收藏</span>
                  </div>
                </div>
              </section>
              <section class="dashboard-section">
                <div class="section-heading">
                  <h2>我的内容空间<span>MY SPACES</span></h2>
                  <span class="small muted">不同的记录，一样的珍贵</span>
                </div>
                <div class="space-cards">
                  <button
                    v-for="(s, key) in spaces"
                    :key="key"
                    class="space-card"
                    :class="key"
                    @click="go(key)"
                  >
                    <div class="space-card-top">
                      <span class="space-card-icon"><Icon :name="s.icon" :size="23" /></span
                      ><Icon name="ArrowUpRight" :size="16" class="space-arrow" />
                    </div>
                    <h3>{{ s.name }}</h3>
                    <p>{{ s.description }}</p>
                    <div class="space-card-bottom">
                      <span>{{ count(key) }} 篇记录</span><span>{{ s.en }}</span>
                    </div>
                  </button>
                </div>
              </section>
              <section class="dashboard-section favorites-section">
                <div class="section-heading">
                  <h2>值得再读<span>WORTH REVISITING</span></h2>
                  <button class="text-button" @click="go('favorites')">
                    我的收藏 <Icon name="ArrowRight" :size="14" />
                  </button>
                </div>
                <div v-if="featured.length" class="featured-grid">
                  <article v-for="n in featured" :key="n.id" class="feature-card" @click="open(n)">
                    <div class="feature-cover" :class="n.space">
                      <template v-if="n.space === 'knowledge'"
                        ><div class="cover-grid"></div>
                        <div class="book-illustration">
                          <div class="book-page"></div>
                          <Icon name="Sprout" :size="48" :stroke-width="1" />
                        </div>
                        <span class="cover-caption">MAKE ROOM TO GROW</span></template
                      ><template v-else-if="n.space === 'thoughts'"
                        ><span class="cover-quote">“</span>
                        <p>向内生长，<br />向外感知。</p>
                        <span class="cover-tiny">A MOMENT TO REFLECT</span
                        ><span class="cover-circle"></span></template
                      ><img v-else src="/landscape.svg" alt="绿色山林插画" /><button
                        class="feature-star"
                        :class="{ starred: n.favorite }"
                        :aria-label="n.favorite ? '取消收藏 ' + n.title : '收藏 ' + n.title"
                        @click.stop="favorite(n)"
                      >
                        <Icon name="Star" :size="14" />
                      </button>
                    </div>
                    <div class="feature-body">
                      <span class="note-space-label" :class="n.space"
                        ><span></span>{{ spaces[n.space].name }}</span
                      >
                      <h3>
                        <button class="card-open" @click.stop="open(n)">{{ n.title }}</button>
                      </h3>
                      <p>{{ excerpt(n.content).slice(0, 56) }}</p>
                      <div class="feature-footer">
                        <span>{{ dateShort(n.entryDate) }}</span
                        ><Icon name="LockKeyhole" :size="12" />
                      </div>
                    </div>
                  </article>
                </div>
                <div v-else class="empty-state compact">
                  <Icon name="Star" :size="26" />
                  <p>收藏喜欢的记录，在这里与它们重逢。</p>
                </div>
              </section>
              <section class="dashboard-section recent-section">
                <div class="section-heading">
                  <h2>最近记录<span>RECENT NOTES</span></h2>
                  <button class="text-button" @click="go('all')">
                    查看全部 <Icon name="ArrowRight" :size="14" />
                  </button>
                </div>
                <div class="recent-list">
                  <button
                    v-for="n in notes.slice(0, 4)"
                    :key="n.id"
                    class="recent-row"
                    @click="open(n)"
                  >
                    <span class="recent-icon" :class="n.space"
                      ><Icon :name="spaces[n.space].icon" :size="17"
                    /></span>
                    <div class="recent-row-title">
                      <strong>{{ n.title }}</strong
                      ><span
                        >{{ n.category || '未分类' }}<span class="dot">·</span
                        >{{ n.tags.slice(0, 2).join(' / ') }}</span
                      >
                    </div>
                    <span v-if="n.draft" class="draft-badge">草稿</span
                    ><span class="recent-row-space">{{ spaces[n.space].name }}</span
                    ><span class="recent-date">{{ updatedLabel(n) }}</span
                    ><Icon name="ChevronRight" :size="14" />
                  </button>
                  <div v-if="!notes.length" class="empty-state compact">
                    <p>花园刚刚开始，写下第一篇记录吧。</p>
                  </div>
                </div>
              </section>
            </div>
            <aside class="dashboard-aside">
              <section class="daily-inspiration">
                <div class="aside-heading">
                  <span><Icon name="Sparkles" :size="14" /> 今日灵感</span
                  ><span class="small">DAILY SPARK</span>
                </div>
                <div class="inspiration-art">
                  <span class="orbit orbit-one"></span><span class="orbit orbit-two"></span
                  ><span class="art-sun"></span
                  ><Icon name="Sprout" :size="68" :stroke-width="0.9" /><span class="art-star"
                    >✧</span
                  >
                </div>
                <blockquote>不必等到想清楚，<br />才开始记录。</blockquote>
                <p>一个念头，也值得拥有一页。</p>
                <button @click="createNote('thoughts')">
                  记下此刻的想法 <Icon name="ArrowUpRight" :size="14" />
                </button>
              </section>
              <section class="calendar-card">
                <div class="calendar-heading">
                  <h3>{{ calendarLabel }}</h3>
                  <div>
                    <button class="icon-button" aria-label="上一个月" @click="moveMonth(-1)">
                      <Icon name="ChevronLeft" :size="14" /></button
                    ><button class="icon-button" aria-label="下一个月" @click="moveMonth(1)">
                      <Icon name="ChevronRight" :size="14" />
                    </button>
                  </div>
                </div>
                <div class="calendar-week">
                  <span v-for="d in ['一', '二', '三', '四', '五', '六', '日']" :key="d">{{
                    d
                  }}</span>
                </div>
                <div class="calendar-days">
                  <template v-for="(d, i) in calendarDays" :key="i"
                    ><button
                      v-if="d"
                      :class="{ today: d.date === isoToday, recorded: d.has }"
                      :aria-label="d.date + (d.has ? ' 有记录' : '')"
                      @click="dayFilter(d.date)"
                    >
                      {{ d.day }}<i v-if="d.has"></i></button
                    ><span v-else></span
                  ></template>
                </div>
                <div class="calendar-legend"><i></i> 留下记录的日子，都在发光</div>
              </section>
              <section class="tags-card">
                <div class="aside-heading">
                  <h3>常用标签</h3>
                  <Icon name="Hash" :size="15" />
                </div>
                <div class="tag-cloud">
                  <button v-for="t in tags.slice(0, 9)" :key="t" @click="tagFilter(t)">
                    <span>#</span> {{ t }}</button
                  ><span v-if="!tags.length" class="small muted">标签会随着记录慢慢生长。</span>
                </div>
              </section>
              <div class="garden-note">
                <Icon name="Leaf" :size="15" />
                <p>不求写满每一天，<br />只愿记住值得的瞬间。</p>
                <span>— 留白 · Notes</span>
              </div>
            </aside>
          </div>
          <footer class="workspace-footer">
            <span><i></i> 数据已保存于你的私人空间</span
            ><span>给思绪一个栖息的地方。<Icon name="Sprout" :size="13" /></span>
          </footer>
        </template>
        <template v-else-if="view === 'settings'"
          ><section class="page-heading">
            <div class="eyebrow">MAKE IT YOURS</div>
            <h1>留一点，属于你的空间。</h1>
            <p>照顾好你的记录，也照顾好你的隐私。</p>
          </section>
          <div class="settings-grid">
            <section class="settings-card">
              <span class="settings-icon"><Icon name="ShieldCheck" :size="24" /></span>
              <h2>隐私与访问</h2>
              <p>每篇新记录始终默认私密。公开分享需要你逐篇确认，并由服务器启用公开访问。</p>
              <div class="setting-row">
                <span>当前访问模式</span
                ><span class="status-badge">{{
                  session.publicAccess ? '公开分享已启用' : '私人预览 · 外部分享关闭'
                }}</span>
              </div>
              <div class="setting-row">
                <span>登录方式</span
                ><strong>{{ session.oidc ? '已配置 OIDC 身份登录' : '私人预览密码' }}</strong>
              </div>
              <p class="small muted">
                ChatGPT 登录尚未接通。只有取得可用的官方身份接口及凭证后，才能启用真实的账号登录。
              </p>
            </section>
            <section class="settings-card">
              <span class="settings-icon"><Icon name="Download" :size="24" /></span>
              <h2>备份与迁移</h2>
              <p>文字属于你。随时导出 Markdown，或保存带有标签、分类和日期的 JSON 备份。</p>
              <div class="settings-buttons">
                <a class="btn" href="/api/export" download
                  ><Icon name="Download" :size="16" />导出 Markdown</a
                ><button class="btn primary" @click="backup">
                  <Icon name="Download" :size="16" />JSON 备份
                </button>
              </div>
              <div class="setting-divider"></div>
              <p class="small muted">
                完整灾备还需备份服务器 .data 目录（含数据库与附件）。JSON
                恢复新增私密草稿，不覆盖现有记录；附件仍需原服务器文件。
              </p>
              <button class="text-button" :disabled="busy" @click="restoreInput?.click()">
                <Icon name="Upload" :size="16" /> 从 JSON 恢复记录
              </button>
            </section>
            <section class="settings-card">
              <span class="settings-icon"><Icon name="Sun" :size="24" /></span>
              <h2>外观与阅读</h2>
              <p>白天清晰，夜晚柔和。选择适合此刻的颜色。</p>
              <div class="appearance-options">
                <button
                  v-for="t in [
                    { id: 'light', name: '浅色', icon: 'Sun' },
                    { id: 'dark', name: '深色', icon: 'Moon' },
                    { id: 'system', name: '跟随系统', icon: 'Monitor' },
                  ]"
                  :key="t.id"
                  :class="{ selected: theme === t.id }"
                  @click="theme = t.id"
                >
                  <Icon :name="t.icon" :size="23" />{{ t.name
                  }}<Icon v-if="theme === t.id" name="Check" :size="13" />
                </button>
              </div>
            </section>
            <section class="settings-card about-card">
              <div class="brand-mark"><Icon name="BookOpen" :size="24" /></div>
              <h2>留白 · Notes</h2>
              <p>一个慢慢生长的数字花园。<br />让知识有脉络，让思考有回声，让生活有痕迹。</p>
              <span class="small muted">VERSION 1.0 · BUILT WITH CARE</span>
            </section>
          </div></template
        >
        <template v-else-if="view === 'manage'"
          ><section class="page-heading">
            <div class="eyebrow">EVERYTHING CONNECTS</div>
            <h1>让想法，有迹可循。</h1>
            <p>用分类梳理脉络，用标签连接灵感。</p>
          </section>
          <div class="manage-layout">
            <section class="settings-card">
              <div class="segmented">
                <button
                  :class="{ selected: manageTab === 'category' }"
                  @click="
                    () => {
                      manageTab = 'category'
                      categoryFrom = ''
                    }
                  "
                >
                  分类</button
                ><button
                  :class="{ selected: manageTab === 'tag' }"
                  @click="
                    () => {
                      manageTab = 'tag'
                      categoryFrom = ''
                    }
                  "
                >
                  标签
                </button>
              </div>
              <div class="taxonomy-list">
                <button
                  v-for="t in manageTab === 'category' ? categories : tags"
                  :key="t"
                  :class="{ selected: categoryFrom === t }"
                  @click="
                    () => {
                      categoryFrom = t
                      categoryNew = t
                    }
                  "
                >
                  <Icon :name="manageTab === 'category' ? 'Folder' : 'Hash'" :size="17" /><span>{{
                    t
                  }}</span
                  ><small
                    >{{
                      notes.filter((n) =>
                        manageTab === 'category' ? n.category === t : n.tags.includes(t),
                      ).length
                    }}
                    篇</small
                  ><Icon name="PenLine" :size="14" />
                </button>
              </div>
              <p v-if="!(manageTab === 'category' ? categories : tags).length" class="muted">
                记录中使用的分类和标签会出现在这里。
              </p>
            </section>
            <section class="settings-card rename-card">
              <Icon name="Sprout" :size="29" />
              <h2>整理，也是温柔的回顾。</h2>
              <p>
                选择一个分类或标签，修改名称后会同步更新相关记录。新分类与标签可以在编辑文章时创建。
              </p>
              <label
                >原名称<input :value="categoryFrom" readonly placeholder="请从左侧选择" /></label
              ><label
                >新名称<input
                  v-model="categoryNew"
                  placeholder="输入新的名称"
                  :maxlength="manageTab === 'category' ? 80 : 40" /></label
              ><button
                class="btn primary"
                :disabled="busy || !categoryFrom || !categoryNew.trim()"
                @click="renameTaxonomy"
              >
                <Icon name="Check" :size="16" />保存名称
              </button>
            </section>
          </div></template
        >
        <template v-else
          ><section class="page-heading list-page-heading">
            <div>
              <div class="eyebrow">
                {{
                  view in spaces
                    ? spaces[view as Space].en
                    : view === 'favorites'
                      ? 'KEEP WHAT MOVES YOU'
                      : view === 'drafts'
                        ? 'IDEAS IN PROGRESS'
                        : 'YOUR GROWING GARDEN'
                }}
              </div>
              <h1>
                {{
                  view in spaces
                    ? spaces[view as Space].name
                    : view === 'favorites'
                      ? '那些，让你心动的记录。'
                      : view === 'drafts'
                        ? '想法，还在慢慢生长。'
                        : '所有记录'
                }}
              </h1>
              <p>
                {{
                  view in spaces
                    ? spaces[view as Space].description
                    : view === 'favorites'
                      ? '值得收藏的文字，总能带来新的回响。'
                      : view === 'drafts'
                        ? '不必急着完成，先让灵感留下来。'
                        : '每一篇，都是成为自己的过程。'
                }}
                <span class="count-label">{{ filtered.length }} 篇记录</span>
              </p>
            </div>
            <button
              class="btn primary"
              :disabled="busy"
              @click="createNote(view in spaces ? (view as Space) : 'thoughts')"
            >
              <Icon name="Plus" :size="17" /> 新建记录
            </button>
          </section>
          <div class="list-controls">
            <div class="list-search">
              <Icon name="Search" :size="16" /><input
                v-model="listQuery"
                aria-label="搜索当前空间"
                placeholder="搜索标题、正文或标签…"
              />
            </div>
            <select v-model="category" aria-label="筛选分类">
              <option value="">全部分类</option>
              <option v-for="c in categories" :key="c">{{ c }}</option></select
            ><select v-model="sort" aria-label="排序方式">
              <option value="updated">最近编辑</option>
              <option value="created">创建时间</option>
              <option value="date">记录日期</option>
              <option value="title">标题排序</option>
            </select>
            <div class="segmented layout-switch">
              <button
                :class="{ selected: layout === 'grid' }"
                aria-label="卡片视图"
                @click="layout = 'grid'"
              >
                <Icon name="Grid2X2" :size="16" /></button
              ><button
                :class="{ selected: layout === 'list' }"
                aria-label="列表视图"
                @click="layout = 'list'"
              >
                <Icon name="AlignLeft" :size="16" />
              </button>
            </div>
          </div>
          <div v-if="tag || selectedDay" class="filter-chips">
            <button v-if="tag" @click="tag = ''"># {{ tag }} <Icon name="X" :size="12" /></button
            ><button v-if="selectedDay" @click="selectedDay = ''">
              {{ selectedDay }} <Icon name="X" :size="12" />
            </button>
          </div>
          <div v-if="!filtered.length" class="empty-state">
            <span class="empty-illustration"
              ><Icon
                :name="view === 'favorites' ? 'Star' : view === 'diary' ? 'Leaf' : 'NotebookPen'"
                :size="36"
                :stroke-width="1.2"
            /></span>
            <h2>
              {{
                listQuery || category || tag || selectedDay
                  ? '这里暂时没有匹配的记录'
                  : '一张白纸，无限可能。'
              }}
            </h2>
            <p>
              {{
                listQuery || category || tag || selectedDay
                  ? '换个关键词，或者清除筛选试试。'
                  : '从一行文字开始，让这片花园慢慢生长。'
              }}
            </p>
            <button
              v-if="listQuery || category || tag || selectedDay"
              class="btn"
              @click="
                () => {
                  listQuery = ''
                  category = ''
                  tag = ''
                  selectedDay = ''
                }
              "
            >
              清除筛选</button
            ><button
              v-else
              class="btn primary"
              @click="createNote(view in spaces ? (view as Space) : 'thoughts')"
            >
              <Icon name="Plus" :size="16" />写下第一篇
            </button>
          </div>
          <div v-else-if="view === 'diary'" class="diary-timeline">
            <article v-for="n in filtered" :key="n.id" class="timeline-item">
              <div class="timeline-date">
                <strong>{{ n.entryDate.slice(8) }}</strong
                ><span>{{ dateShort(n.entryDate).split('月')[0] }}月</span>
              </div>
              <button class="timeline-card" @click="open(n)">
                <div v-if="n.content.includes('/landscape.svg')" class="timeline-image">
                  <img src="/landscape.svg" alt="山林插画" />
                </div>
                <div>
                  <div class="small muted">
                    {{ n.category || '生活碎片' }}
                    <span v-if="n.draft" class="draft-badge">草稿</span>
                  </div>
                  <h2>{{ n.title }}</h2>
                  <p>{{ excerpt(n.content).slice(0, 130) }}</p>
                  <div class="tag-cloud">
                    <span v-for="t in n.tags" :key="t"># {{ t }}</span>
                  </div>
                </div>
                <Icon name="ArrowUpRight" :size="18" />
              </button>
            </article>
          </div>
          <div v-else-if="layout === 'grid'" class="notes-grid">
            <article v-for="n in filtered" :key="n.id" class="note-card" @click="open(n)">
              <div class="note-card-top">
                <span class="note-space-label" :class="n.space"
                  ><Icon :name="spaces[n.space].icon" :size="14" />{{ spaces[n.space].name }}</span
                ><button
                  class="icon-button"
                  :class="{ starred: n.favorite }"
                  :aria-label="n.favorite ? '取消收藏 ' + n.title : '收藏 ' + n.title"
                  @click.stop="favorite(n)"
                >
                  <Icon name="Star" :size="16" />
                </button>
              </div>
              <div class="note-card-category">{{ n.category || '未分类' }}</div>
              <h2>
                <button class="card-open" @click.stop="open(n)">{{ n.title }}</button>
              </h2>
              <p>{{ excerpt(n.content).slice(0, 100) }}</p>
              <div class="note-card-tags">
                <span v-for="t in n.tags.slice(0, 3)" :key="t"># {{ t }}</span>
              </div>
              <div class="note-card-footer">
                <span>{{ dateShort(n.entryDate) }}</span
                ><span v-if="n.draft" class="draft-badge">草稿</span
                ><Icon :name="n.published ? 'Globe2' : 'LockKeyhole'" :size="13" />
              </div>
            </article>
          </div>
          <div v-else class="recent-list full-list">
            <button v-for="n in filtered" :key="n.id" class="recent-row" @click="open(n)">
              <span class="recent-icon" :class="n.space"
                ><Icon :name="spaces[n.space].icon"
              /></span>
              <div class="recent-row-title">
                <strong>{{ n.title }}</strong
                ><span
                  >{{ n.category || '未分类' }}<span class="dot">·</span
                  >{{ n.tags.join(' / ') }}</span
                >
              </div>
              <span v-if="n.draft" class="draft-badge">草稿</span
              ><span class="recent-date">{{ updatedLabel(n) }}</span
              ><Icon name="ChevronRight" :size="14" />
            </button></div
        ></template>
      </main>
    </div>
    <input ref="importInput" type="file" accept=".md" multiple hidden @change="importFiles" /><input
      ref="restoreInput"
      type="file"
      accept=".json,application/json"
      hidden
      @change="restoreFile"
    />
  </div>
  <Transition name="toast"
    ><div v-if="toast" class="toast-message" role="status">
      <Icon name="CheckCheck" :size="17" />{{ toast
      }}<button aria-label="关闭提示" @click="toast = ''"><Icon name="X" :size="14" /></button></div
  ></Transition>
  <div v-if="searchOpen" class="modal-backdrop" @click.self="searchOpen = false">
    <section
      class="search-modal"
      role="dialog"
      aria-modal="true"
      aria-label="搜索你的花园"
      @keydown.tab="($event) => trapFocus($event)"
    >
      <div class="search-modal-input">
        <Icon name="Search" :size="21" /><input
          ref="searchInput"
          v-model="query"
          placeholder="搜索文字、想法与生活…"
          aria-label="全局搜索内容"
        /><button class="keycap" @click="searchOpen = false">ESC</button>
      </div>
      <div class="search-results-label">
        {{ query ? '找到 ' + results.length + ' 篇相关记录' : '最近的记录'
        }}<span><Icon name="LockKeyhole" :size="12" /> 仅搜索你的私人空间</span>
      </div>
      <div class="search-results">
        <button v-for="n in results" :key="n.id" @click="open(n)">
          <span class="recent-icon" :class="n.space"><Icon :name="spaces[n.space].icon" /></span>
          <div>
            <strong>{{ n.title }}</strong>
            <p>{{ excerpt(n.content).slice(0, 90) }}</p>
          </div>
          <Icon name="ArrowUpRight" :size="16" />
        </button>
        <div v-if="!results.length" class="empty-state compact">
          <Icon name="Search" :size="25" />
          <p>没有找到相关记录，换个词试试。</p>
        </div>
      </div>
      <footer>每一个被记住的念头，都有迹可循。</footer>
    </section>
  </div>
  <div v-if="confirmDelete && current" class="modal-backdrop" @click.self="confirmDelete = false">
    <section
      class="dialog"
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="delete-title"
      @keydown.tab="($event) => trapFocus($event)"
    >
      <span class="dialog-icon danger"><Icon name="Trash2" :size="25" /></span>
      <h2 id="delete-title">确定删除这篇记录？</h2>
      <p>「{{ current.title }}」将被永久删除。你可以先导出 Markdown 保存一份副本。</p>
      <div class="dialog-actions">
        <button class="btn" autofocus @click="confirmDelete = false">再想一想</button
        ><button class="btn danger-btn" :disabled="busy" @click="remove">永久删除</button>
      </div>
    </section>
  </div>
  <div v-if="shareOpen && current" class="modal-backdrop" @click.self="shareOpen = false">
    <section
      class="dialog share-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="share-title"
      @keydown.tab="($event) => trapFocus($event)"
    >
      <button class="dialog-close icon-button" aria-label="关闭分享设置" @click="shareOpen = false">
        <Icon name="X" /></button
      ><span class="dialog-icon"
        ><Icon :name="current.published ? 'Globe2' : 'LockKeyhole'" :size="26"
      /></span>
      <h2 id="share-title">分享，由你决定。</h2>
      <p>只有你选择公开的文章，才可能被他人阅读。分类页、搜索和其他私密记录始终需要登录。</p>
      <div v-if="!session?.publicAccess" class="privacy-callout">
        <Icon name="ShieldCheck" :size="20" />
        <div>
          <strong>私人预览期间，外部访问已关闭</strong
          ><span>即使标记公开，分享链接仍不可访问。确认测试后，由服务器开启公开访问。</span>
        </div>
      </div>
      <template v-if="current.published"
        ><label class="form-label">分享链接</label>
        <div class="copy-field">
          <input
            :value="locationOrigin + '/share/' + current.shareId"
            readonly
            aria-label="分享链接"
          /><button class="icon-button" aria-label="复制分享链接" @click="copyLink">
            <Icon name="Copy" :size="16" />
          </button>
        </div>
        <button class="btn full-width" :disabled="busy" @click="togglePublish">
          <Icon name="LockKeyhole" :size="15" />改为私密并撤销分享
        </button></template
      ><template v-else
        ><label class="consent-check"
          ><input
            v-model="shareConsent"
            type="checkbox"
          />我已检查正文与附件，确认没有敏感信息。</label
        ><button
          class="btn primary full-width"
          :disabled="busy || !shareConsent"
          @click="togglePublish"
        >
          <Icon name="Globe2" :size="16" />{{
            session?.publicAccess ? '设为公开并分享' : '标记为公开 · 保持私人预览'
          }}
        </button></template
      >
    </section>
  </div>
</template>
