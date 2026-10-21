<script setup lang="ts">
import { ref, computed, onBeforeUnmount, onMounted } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Image from '@tiptap/extension-image'
import Link from '@tiptap/extension-link'
import { TableKit } from '@tiptap/extension-table'
import Icon from './Icon.vue'
import type { Note } from './types'
import { api } from './api'
import { renderMarkdown, toMarkdown } from './markdown'
const props = defineProps<{ note: Note }>()
const emit = defineEmits<{ saved: [note: Note]; cancel: []; error: [message: string] }>()
const form = ref<Note>(JSON.parse(JSON.stringify(props.note)) as Note),
  mode = ref('visual'),
  tags = ref(props.note.tags.join(', ')),
  saving = ref(false),
  uploading = ref(false),
  linkOpen = ref(false),
  url = ref(''),
  fileInput = ref<HTMLInputElement>(),
  visualChanged = ref(false)
const initial = JSON.stringify(props.note)
const dirty = computed(
  () => JSON.stringify(form.value) !== initial || tags.value !== props.note.tags.join(', '),
)
const words = computed(() => form.value.content.replace(/\s/g, '').length)
const editor = useEditor({
  extensions: [
    StarterKit.configure({ link: false }),
    Image,
    Link.configure({ openOnClick: false, protocols: ['http', 'https', 'mailto'] }),
    TableKit.configure({ table: { resizable: false } }),
  ],
  content: renderMarkdown(form.value.content),
  editorProps: {
    attributes: { 'aria-label': '正文编辑器', 'data-testid': 'visual-editor' },
    transformPastedHTML: (html) => {
      const el = document.createElement('div')
      el.innerHTML = html
      el.querySelectorAll('img').forEach((img) => {
        const src = img.getAttribute('src') || ''
        if (!src.startsWith('/') || src.startsWith('//')) img.remove()
      })
      return el.innerHTML
    },
    handlePaste: (_v, e) => {
      if (e.clipboardData?.files.length) {
        void upload(e.clipboardData.files[0])
        return true
      }
      return false
    },
  },
  onUpdate: ({ editor }) => {
    visualChanged.value = true
    form.value.content = toMarkdown(editor.getHTML())
  },
})
function switchMode(next: string) {
  if (mode.value === next) return
  if (next === 'visual') {
    editor.value?.commands.setContent(renderMarkdown(form.value.content), { emitUpdate: false })
    visualChanged.value = false
  }
  mode.value = next
}
async function save() {
  if (saving.value || uploading.value) return
  if (!form.value.title.trim()) {
    emit('error', '请为这篇记录取一个标题')
    return
  }
  form.value.tags = [
    ...new Set(
      tags.value
        .split(/[,，]/)
        .map((t) => t.trim())
        .filter(Boolean),
    ),
  ]
  saving.value = true
  try {
    const n = await api<Note>('/notes/' + form.value.id, {
      method: 'PUT',
      body: JSON.stringify(form.value),
    })
    emit('saved', n)
  } catch (e) {
    emit('error', (e as Error).message)
  } finally {
    saving.value = false
  }
}
async function upload(file?: File) {
  if (!file) return
  if (file.size > 10 * 1024 * 1024) {
    emit('error', '附件不能超过 10MB')
    return
  }
  uploading.value = true
  try {
    const data = new FormData()
    data.append('file', file)
    const a = await api<{ url: string; name: string; type: string }>(
      '/notes/' + form.value.id + '/attachments',
      { method: 'POST', body: data },
    )
    const isImage = a.type.startsWith('image/')
    if (mode.value === 'visual') {
      if (isImage) editor.value?.chain().focus().setImage({ src: a.url, alt: a.name }).run()
      else
        editor.value
          ?.chain()
          .focus()
          .insertContent({
            type: 'text',
            text: a.name,
            marks: [{ type: 'link', attrs: { href: a.url } }],
          })
          .run()
    } else
      form.value.content +=
        '\n\n' + (isImage ? '!' : '') + '[' + a.name.replace(/[\[\]]/g, '') + '](' + a.url + ')\n'
  } catch (e) {
    emit('error', (e as Error).message)
  } finally {
    uploading.value = false
    if (fileInput.value) fileInput.value.value = ''
  }
}
function insertLink() {
  if (!/^(https?:\/\/|mailto:)/i.test(url.value)) {
    emit('error', '请输入以 https://、http:// 或 mailto: 开头的链接')
    return
  }
  editor.value?.chain().focus().extendMarkRange('link').setLink({ href: url.value }).run()
  url.value = ''
  linkOpen.value = false
}
function cancel() {
  if (!dirty.value || window.confirm('还有未保存的修改，确定离开吗？')) emit('cancel')
}
function unload(e: BeforeUnloadEvent) {
  if (dirty.value) {
    e.preventDefault()
    e.returnValue = ''
  }
}
function shortcut(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key === 's') {
    e.preventDefault()
    void save()
  }
}
onMounted(() => {
  window.addEventListener('beforeunload', unload)
  window.addEventListener('keydown', shortcut)
})
onBeforeUnmount(() => {
  editor.value?.destroy()
  window.removeEventListener('beforeunload', unload)
  window.removeEventListener('keydown', shortcut)
})
const tools = [
  ['Bold', '加粗'],
  ['Italic', '斜体'],
  ['Heading2', '二级标题'],
  ['List', '无序列表'],
  ['ListOrdered', '有序列表'],
  ['Quote', '引用'],
  ['Code2', '代码块'],
  ['Table2', '表格'],
] as const
function tool(name: string) {
  const chain = editor.value?.chain().focus()
  if (!chain) return
  switch (name) {
    case 'Bold':
      chain.toggleBold().run()
      break
    case 'Italic':
      chain.toggleItalic().run()
      break
    case 'Heading2':
      chain.toggleHeading({ level: 2 }).run()
      break
    case 'List':
      chain.toggleBulletList().run()
      break
    case 'ListOrdered':
      chain.toggleOrderedList().run()
      break
    case 'Quote':
      chain.toggleBlockquote().run()
      break
    case 'Code2':
      chain.toggleCodeBlock().run()
      break
    case 'Table2':
      chain.insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()
  }
}
</script>
<template>
  <section class="editor-page">
    <div class="page-actions">
      <button class="text-button" @click="cancel"><Icon name="ArrowLeft" /> 返回</button
      ><span class="muted small editor-save-hint"
        ><Icon name="LockKeyhole" :size="13" />
        {{ note.published ? '分享状态保留' : '仅自己可见' }} ·
        {{ dirty ? '有未保存的修改' : '已保存到你的花园' }}</span
      ><button class="btn primary" :disabled="saving || uploading" @click="save">
        <Icon :name="saving ? 'LoaderCircle' : 'Check'" :class="{ spin: saving }" />
        {{ saving ? '保存中…' : '保存记录' }}
      </button>
    </div>
    <div class="editor-sheet">
      <div class="editor-heading">
        <div class="eyebrow">A LITTLE SPACE FOR YOUR THOUGHTS</div>
        <input
          v-model="form.title"
          class="title-input"
          aria-label="文章标题"
          maxlength="300"
          placeholder="给这个想法起个名字…"
        />
        <div class="editor-meta">
          <select v-model="form.space" aria-label="内容空间">
            <option value="knowledge">知识笔记</option>
            <option value="thoughts">思考心得</option>
            <option value="diary">生活日记</option></select
          ><span>·</span><input v-model="form.entryDate" type="date" aria-label="记录日期" /><span
            >·</span
          ><select v-model="form.draft" aria-label="记录状态">
            <option :value="true">草稿</option>
            <option :value="false">已完成</option>
          </select>
        </div>
      </div>
      <div class="editor-controls">
        <div class="segmented">
          <button :class="{ selected: mode === 'visual' }" @click="switchMode('visual')">
            <Icon name="PenLine" :size="14" /> 可视化</button
          ><button :class="{ selected: mode === 'markdown' }" @click="switchMode('markdown')">
            <Icon name="Code2" :size="14" /> Markdown
          </button>
        </div>
        <span class="small muted">让想法自然发生。</span>
      </div>
      <div v-if="mode === 'visual'" class="toolbar" role="toolbar" aria-label="格式工具">
        <button
          v-for="t in tools"
          :key="t[0]"
          class="icon-button"
          :title="t[1]"
          :aria-label="t[1]"
          @click="tool(t[0])"
        >
          <Icon :name="t[0]" :size="17" /></button
        ><span class="toolbar-sep"></span
        ><button
          class="icon-button"
          title="插入链接"
          aria-label="插入链接"
          @click="linkOpen = !linkOpen"
        >
          <Icon name="Link2" /></button
        ><button
          class="icon-button"
          title="上传图片或附件"
          aria-label="上传图片或附件"
          :disabled="uploading"
          @click="fileInput?.click()"
        >
          <Icon
            :name="uploading ? 'LoaderCircle' : 'Paperclip'"
            :class="{ spin: uploading }"
          /></button
        ><span class="toolbar-sep"></span
        ><button
          class="icon-button"
          title="撤销"
          aria-label="撤销"
          @click="editor?.chain().focus().undo().run()"
        >
          <Icon name="Undo2" /></button
        ><button
          class="icon-button"
          title="重做"
          aria-label="重做"
          @click="editor?.chain().focus().redo().run()"
        >
          <Icon name="Redo2" />
        </button>
      </div>
      <div v-if="linkOpen" class="link-form">
        <input
          v-model="url"
          autofocus
          placeholder="https://example.com"
          aria-label="链接地址"
          @keydown.enter="insertLink"
        /><button class="btn small-btn" @click="insertLink">插入链接</button>
      </div>
      <EditorContent v-if="mode === 'visual'" :editor="editor" class="rich-editor prose" />
      <div v-else class="markdown-split">
        <div>
          <div class="source-label">
            MARKDOWN
            <button class="text-button" :disabled="uploading" @click="fileInput?.click()">
              <Icon name="Paperclip" :size="14" /> 附件
            </button>
          </div>
          <textarea
            v-model="form.content"
            class="markdown-source"
            aria-label="Markdown 源码"
            spellcheck="false"
            placeholder="从一行文字开始…"
          ></textarea>
        </div>
        <div class="markdown-preview">
          <div class="source-label">实时预览</div>
          <div class="prose" v-html="renderMarkdown(form.content)"></div>
        </div>
      </div>
      <div class="editor-taxonomy">
        <label
          >分类<input
            v-model="form.category"
            placeholder="例如：阅读摘记"
            maxlength="80"
            list="categories" /></label
        ><datalist id="categories">
          <option>前端开发</option>
          <option>后端开发</option>
          <option>日常思考</option>
          <option>阅读摘记</option>
          <option>生活碎片</option></datalist
        ><label class="tags-field"
          >标签<input
            v-model="tags"
            placeholder="用逗号分隔，例如：阅读, 灵感"
            aria-label="标签，以逗号分隔"
        /></label>
      </div>
      <div class="editor-footer">
        <span
          >{{ words.toLocaleString() }} 字 <span class="dot">·</span> 约
          {{ Math.max(1, Math.ceil(words / 400)) }} 分钟阅读</span
        ><span>⌘ / Ctrl + S 保存</span>
      </div>
    </div>
    <input
      ref="fileInput"
      type="file"
      hidden
      accept=".png,.jpg,.jpeg,.gif,.webp,.pdf,.txt,.md,.csv,.zip"
      @change="upload(($event.target as HTMLInputElement).files?.[0])"
    />
  </section>
</template>
