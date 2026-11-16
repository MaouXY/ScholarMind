<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  ChatLineRound,
  Delete,
  EditPen,
  Download,
  Plus,
  Refresh,
} from '@element-plus/icons-vue'

import {
  askLiteratureQuestion,
  createReadingNote,
  deleteReadingNote,
  getLiteratureView,
  getReadingHistory,
  listLiteratureChunks,
  listLiteratureQaRecords,
  listReadingNotes,
  rebuildLiteratureIndex,
  saveReadingHistory,
  updateReadingNote,
} from '@/api/literature'
import { downloadReadingNotes } from '@/utils/download'
import {
  displayCategoryName,
  displayLanguage,
  displayLiteratureType,
} from '@/utils/format'
import type {
  Id,
  Literature,
  LiteratureChunk,
  LiteratureQuestionResult,
  ReadingHistory,
  ReadingNote,
  ReadingNotePayload,
} from '@/types/literature'
import '@/styles/literature-ext.css'
import '@/styles/reader.css'

const route = useRoute()
const router = useRouter()

const literatureId = computed<Id>(() => {
  const raw = route.params.id
  return Array.isArray(raw) ? String(raw[0] ?? '') : String(raw ?? '')
})

const literature = ref<Literature | null>(null)
const chunks = ref<LiteratureChunk[]>([])
const history = ref<ReadingHistory | null>(null)
const notes = ref<ReadingNote[]>([])

const loading = ref(false)
const chunkLoading = ref(false)
const notesLoading = ref(false)
const noteSaving = ref(false)
const indexBuilding = ref(false)
const loadFailed = ref(false)
const autoRebuildTried = ref(false)
const autoRebuildFailed = ref(false)
const noteExporting = ref(false)

const noteDrawerVisible = ref(false)
const noteMode = ref<'create' | 'edit'>('create')
const selectedNote = ref<ReadingNote | null>(null)

/** 右栏：智能问答 / 阅读笔记 */
const sideTab = ref<'qa' | 'note'>('qa')

const currentChunkIndex = ref(0)
const elapsedSeconds = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const PROGRESS_SAVE_DEBOUNCE = 1500
let progressSaveTimer: ReturnType<typeof setTimeout> | null = null
let scrollFrame: number | null = null

// 问答
const qaForm = reactive({ question: '', limit: 4 })
const qaRecords = ref<LiteratureQuestionResult[]>([])
const activeAnswer = ref<LiteratureQuestionResult | null>(null)
const asking = ref(false)
const historyLoading = ref(false)
const askError = ref('')

const suggestedQuestions = [
  '这篇文献的研究问题是什么？',
  '作者用了什么方法？',
  '结论与局限分别是什么？',
]

const noteForm = reactive<ReadingNotePayload>({
  literatureId: '',
  title: '',
  content: '',
  aiGenerated: 0,
})

const keywordTokens = computed(() =>
  (literature.value?.keywords ?? '')
    .split(/[,，;；、]/)
    .map((item) => item.trim())
    .filter(Boolean),
)

const abstractBlocks = computed(() =>
  (literature.value?.abstractText ?? '')
    .split(/\n+/)
    .map((item) => item.trim())
    .filter(Boolean),
)

const orderedChunks = computed(() =>
  [...chunks.value].sort((a, b) => (a.chunkIndex ?? 0) - (b.chunkIndex ?? 0)),
)

const totalChunks = computed(() => orderedChunks.value.length)

const hasChunks = computed(() => totalChunks.value > 0)

/** 正文分块渲染项：首段文字兼作目录标题 */
const chunkItems = computed(() =>
  orderedChunks.value.map((chunk) => ({
    key: `${chunk.id}-${chunk.chunkIndex}`,
    id: chunk.id,
    chunkIndex: chunk.chunkIndex,
    paragraphs: splitParagraphs(chunk.content),
  })),
)

/** 左侧目录：章节锚点，含摘要 / 关键词 / 正文各段 */
const tocItems = computed(() => {
  const items: { id: string; label: string; hint: string }[] = []
  if (literature.value?.aiSummary?.trim()) {
    items.push({ id: 'section-ai-summary', label: 'AI 摘要', hint: '智能生成' })
  }
  if (abstractBlocks.value.length) {
    items.push({ id: 'section-abstract', label: '摘要', hint: '原文摘录' })
  }
  if (keywordTokens.value.length) {
    items.push({ id: 'section-keywords', label: '关键词', hint: '主题词' })
  }
  chunkItems.value.forEach((item) => {
    items.push({
      id: `chunk-${item.id}`,
      label: `第 ${item.chunkIndex + 1} 段`,
      hint: item.paragraphs[0]?.slice(0, 22) || '（空分块）',
    })
  })
  return items
})

function splitParagraphs(content: string) {
  return (content ?? '')
    .split(/\n+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

const currentChunk = computed(() =>
  Math.min(
    Math.max(currentChunkIndex.value, 0),
    Math.max(totalChunks.value - 1, 0),
  ),
)

const progressPercent = computed(() => {
  if (!totalChunks.value) return 0
  const position = orderedChunks.value.findIndex(
    (chunk) => chunk.chunkIndex === currentChunk.value,
  )
  return Math.round(
    ((position < 0 ? 0 : position + 1) / totalChunks.value) * 100,
  )
})

const elapsedText = computed(() => {
  const minutes = Math.floor(elapsedSeconds.value / 60)
  const seconds = elapsedSeconds.value % 60
  return minutes > 0 ? `${minutes} 分 ${seconds} 秒` : `${seconds} 秒`
})

const emptyChunkHint = computed(() => {
  if (indexBuilding.value) return '正在解析正文，请稍候…'
  if (autoRebuildFailed.value) return '该文献暂无可读正文，请联系管理员'
  if (!literature.value?.fileId) {
    return '该文献未绑定原文文件，暂无可读正文，请联系管理员补充原文'
  }
  return '该文献暂无可读正文，请联系管理员'
})

const references = computed(
  () => activeAnswer.value?.references ?? [],
)

function openNoteCreate() {
  noteMode.value = 'create'
  Object.assign(noteForm, {
    literatureId: literatureId.value,
    title: '',
    content: '',
    aiGenerated: 0,
  })
  selectedNote.value = null
  noteDrawerVisible.value = true
}

function openNoteEdit(note: ReadingNote) {
  noteMode.value = 'edit'
  selectedNote.value = note
  Object.assign(noteForm, {
    literatureId: note.literatureId,
    title: note.title,
    content: note.content,
    aiGenerated: note.aiGenerated ?? 0,
  })
  noteDrawerVisible.value = true
}

/** 目录点击：滚动到锚点并把该段设为当前阅读位置 */
function jumpTo(targetId: string, chunkIndex?: number) {
  if (typeof chunkIndex === 'number') {
    currentChunkIndex.value = chunkIndex
  }
  document
    .getElementById(targetId)
    ?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function loadLiterature() {
  const id = literatureId.value
  if (!id) {
    loadFailed.value = true
    ElMessage.warning('缺少文献 ID，无法进入阅读')
    return
  }

  loading.value = true
  loadFailed.value = false
  try {
    const response = await getLiteratureView(id)
    literature.value = response.literature
    notes.value = response.recentNotes ?? []
  } catch (error) {
    console.error(
      'reader literature load failed, scenario=literature-reader-detail, literatureId=%s',
      id,
      error,
    )
    literature.value = null
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

async function fetchChunks(silent = false) {
  const id = literatureId.value
  if (!id) return

  chunkLoading.value = true
  try {
    const result = await listLiteratureChunks(id)
    chunks.value = Array.isArray(result) ? result : []
    if (!silent) {
      ElMessage.success(`已加载 ${chunks.value.length} 个正文分块`)
    }
  } catch (error) {
    console.error(
      'literature chunk list failed, scenario=literature-chunk-list, literatureId=%s',
      id,
      error,
    )
    chunks.value = []
  } finally {
    chunkLoading.value = false
  }
}

async function loadHistory() {
  const id = literatureId.value
  if (!id) return

  try {
    const result = await getReadingHistory(id)
    history.value = result
    const saved = (result?.lastPage ?? 1) - 1
    currentChunkIndex.value = Math.min(
      Math.max(saved, 0),
      Math.max(totalChunks.value - 1, 0),
    )
  } catch (error) {
    console.error(
      'reading history load failed, scenario=reading-history-get, literatureId=%s',
      id,
      error,
    )
  }
}

async function loadNotes() {
  const id = literatureId.value
  if (!id) return

  notesLoading.value = true
  try {
    const result = await listReadingNotes({ literatureId: id, page: 1, size: 20 })
    notes.value = result.items
  } catch (error) {
    console.error(
      'reading notes list failed, scenario=reading-note-list, literatureId=%s',
      id,
      error,
    )
  } finally {
    notesLoading.value = false
  }
}

async function tryAutoRebuildChunks() {
  if (autoRebuildTried.value || hasChunks.value) return
  const id = literatureId.value
  if (!id || !literature.value?.fileId) return

  autoRebuildTried.value = true
  indexBuilding.value = true
  try {
    await rebuildLiteratureIndex(id)
    await fetchChunks(true)
    if (!hasChunks.value) autoRebuildFailed.value = true
  } catch (error) {
    autoRebuildFailed.value = true
    console.warn(
      'reader chunk auto rebuild failed, scenario=literature-index-auto-rebuild, literatureId=%s',
      id,
      error,
    )
  } finally {
    indexBuilding.value = false
  }
}

async function persistProgress() {
  const id = literatureId.value
  if (!id || loadFailed.value) return

  const lastPage = hasChunks.value
    ? currentChunk.value + 1
    : Math.max(1, history.value?.lastPage ?? 1)

  try {
    const result = await saveReadingHistory(id, {
      lastPage,
      progress: progressPercent.value,
      readDuration: elapsedSeconds.value,
    })
    history.value = result
  } catch (error) {
    console.warn(
      'reading progress auto save failed, scenario=reading-history-auto-save, literatureId=%s, lastPage=%s',
      id,
      lastPage,
      error,
    )
  }
}

let progressSaveChain: Promise<void> = Promise.resolve()

function queueProgressSave() {
  progressSaveChain = progressSaveChain.then(() => persistProgress())
}

function scheduleProgressSave() {
  if (progressSaveTimer !== null) clearTimeout(progressSaveTimer)
  progressSaveTimer = setTimeout(() => {
    progressSaveTimer = null
    queueProgressSave()
  }, PROGRESS_SAVE_DEBOUNCE)
}

function flushProgressSave() {
  if (progressSaveTimer !== null) {
    clearTimeout(progressSaveTimer)
    progressSaveTimer = null
  }
  queueProgressSave()
}

/** 滚动时按视口位置同步当前分块，再触发 debounce 保存 */
function syncCurrentChunkFromViewport() {
  if (!hasChunks.value) return
  const cards = document.querySelectorAll<HTMLElement>('.chunk-card')
  let activeIndex: number | null = null
  cards.forEach((card) => {
    if (card.getBoundingClientRect().top <= 160) {
      const index = Number(card.dataset.chunkIndex)
      if (Number.isFinite(index)) activeIndex = index
    }
  })
  if (activeIndex === null || activeIndex === currentChunkIndex.value) return
  currentChunkIndex.value = activeIndex
  scheduleProgressSave()
}

function handleScroll() {
  if (scrollFrame !== null) return
  scrollFrame = window.requestAnimationFrame(() => {
    scrollFrame = null
    syncCurrentChunkFromViewport()
  })
}

async function loadChunks() {
  const id = literatureId.value
  if (!id) return

  await Promise.all([loadLiterature(), fetchChunks(true)])
  if (loadFailed.value) return

  await Promise.all([loadHistory(), loadNotes(), fetchQaHistory()])
  await tryAutoRebuildChunks()
  if (hasChunks.value) {
    const saved = (history.value?.lastPage ?? 1) - 1
    currentChunkIndex.value = Math.min(
      Math.max(saved, 0),
      Math.max(totalChunks.value - 1, 0),
    )
  }
}

async function fetchQaHistory() {
  const id = literatureId.value
  if (!id) return

  historyLoading.value = true
  try {
    qaRecords.value = await listLiteratureQaRecords({
      literatureId: id,
      page: 1,
      size: 20,
    })
  } catch (error) {
    console.error(
      'qa history load failed, scenario=literature-qa-history, literatureId=%s',
      id,
      error,
    )
  } finally {
    historyLoading.value = false
  }
}

async function askQuestion() {
  const id = literatureId.value
  if (!id) return
  if (!qaForm.question.trim()) {
    ElMessage.warning('请输入要提问的问题')
    return
  }

  asking.value = true
  askError.value = ''
  try {
    const result = await askLiteratureQuestion(id, {
      question: qaForm.question,
      limit: qaForm.limit,
    })
    activeAnswer.value = result
    qaRecords.value = [
      result,
      ...qaRecords.value.filter(
        (item) => String(item.recordId) !== String(result.recordId),
      ),
    ]
    ElMessage.success('回答已生成')
  } catch (error) {
    const failure = error as { message?: string } | null
    askError.value = failure?.message || '回答生成失败，请稍后重试'
    console.error(
      'literature question failed, scenario=literature-rag-question, literatureId=%s, question=%s',
      id,
      qaForm.question,
      error,
    )
  } finally {
    asking.value = false
  }
}

function useSuggestion(question: string) {
  qaForm.question = question
}

async function saveNote() {
  if (!noteForm.title.trim() || !noteForm.content.trim()) {
    ElMessage.warning('请填写笔记标题和内容')
    return
  }

  noteSaving.value = true
  try {
    if (noteMode.value === 'edit' && selectedNote.value) {
      await updateReadingNote(selectedNote.value.id, noteForm)
    } else {
      await createReadingNote(noteForm)
    }
    ElMessage.success('笔记已保存')
    noteDrawerVisible.value = false
    await loadNotes()
  } catch (error) {
    console.error(
      'reading note save failed, scenario=reading-note-save, noteId=%s, literatureId=%s',
      selectedNote.value?.id,
      noteForm.literatureId,
      error,
    )
  } finally {
    noteSaving.value = false
  }
}

async function removeNote(note: ReadingNote) {
  try {
    await ElMessageBox.confirm(
      '删除后不可恢复，确认删除这条阅读笔记？',
      '删除笔记',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
    await deleteReadingNote(note.id)
    ElMessage.success('笔记已删除')
    await loadNotes()
  } catch (error) {
    if (error === 'cancel') return
    console.error(
      'reading note delete failed, scenario=reading-note-delete, noteId=%s, literatureId=%s',
      note.id,
      note.literatureId,
      error,
    )
  }
}

async function exportNotes() {
  const id = literatureId.value
  if (!id) return

  noteExporting.value = true
  try {
    const fileName = await downloadReadingNotes(id)
    ElMessage.success(`已导出本篇笔记：${fileName}`)
  } catch (error) {
    console.error(
      'reading notes export failed, scenario=reading-note-export, literatureId=%s',
      id,
      error,
    )
    ElMessage.error('导出笔记失败，请稍后重试')
  } finally {
    noteExporting.value = false
  }
}

function goDetail() {
  router.push(`/student/literature/${literatureId.value}`)
}

function goSearch() {
  router.push('/student/search')
}

onMounted(async () => {
  window.addEventListener('scroll', handleScroll, { passive: true })
  await loadChunks()
  timer = setInterval(() => {
    elapsedSeconds.value += 1
  }, 1000)
})

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  if (scrollFrame !== null) {
    window.cancelAnimationFrame(scrollFrame)
    scrollFrame = null
  }
  window.removeEventListener('scroll', handleScroll)
  flushProgressSave()
})
</script>

<template>
  <section class="library-page reader-page">
    <section v-if="loading" class="reader-surface" aria-label="阅读页加载中">
      <el-skeleton :rows="10" animated />
    </section>

    <section
      v-else-if="loadFailed || !literature"
      class="reader-surface"
      aria-label="阅读页异常"
    >
      <el-empty description="文献加载失败或不存在，请返回检索页重新选择">
        <div class="detail-actions">
          <el-button :icon="Refresh" type="primary" @click="loadChunks"
            >重新加载</el-button
          >
          <el-button :icon="ArrowLeft" plain @click="goSearch"
            >返回检索</el-button
          >
        </div>
      </el-empty>
    </section>

    <template v-else>
      <div class="reader-topbar">
        <el-button :icon="ArrowLeft" text @click="goDetail">返回详情</el-button>
        <div class="reader-topbar__title">{{ literature.title }}</div>
        <div class="reader-topbar__meta">
          <span>{{ progressPercent }}% · {{ elapsedText }}</span>
          <el-button
            :icon="Download"
            text
            :loading="noteExporting"
            @click="exportNotes"
          >
            导出笔记
          </el-button>
        </div>
      </div>

      <div class="reader-layout">
        <!-- 左：目录 -->
        <aside class="reader-toc" aria-label="目录">
          <p class="reader-toc__caption">目录</p>
          <nav class="reader-toc__list">
            <button
              v-for="item in tocItems"
              :key="item.id"
              type="button"
              class="reader-toc__item"
              :class="{
                'is-active':
                  hasChunks &&
                  item.id === `chunk-${chunkItems[currentChunk]?.id}`,
              }"
              @click="
                jumpTo(
                  item.id,
                  item.id.startsWith('chunk-')
                    ? Number(item.label.replace(/\D/g, '')) - 1
                    : undefined,
                )
              "
            >
              <span>{{ item.label }}</span>
              <em>{{ item.hint }}</em>
            </button>
            <el-empty
              v-if="!tocItems.length"
              description="暂无目录"
              :image-size="70"
            />
          </nav>
        </aside>

        <!-- 中：正文 -->
        <article class="reader-article">
          <header class="reader-article__head">
            <p class="panel__eyebrow">
              {{ displayCategoryName(literature.categoryName ?? null) }}
            </p>
            <h1>{{ literature.title }}</h1>
            <div class="reader-meta">
              <span>{{ literature.author || '作者未标注' }}</span>
              <span>{{ literature.source || '来源未标注' }}</span>
              <span>{{ literature.publishDate || '发表时间未标注' }}</span>
              <span>{{ displayLanguage(literature.language) }}</span>
            </div>
            <div class="reader-meta">
              <span>{{ displayLiteratureType(literature.literatureType) }}</span>
              <span>在线公开 {{ literature.onlinePublishDate || '未标注' }}</span>
              <span>被引 {{ literature.citationCount ?? 0 }}</span>
              <span>下载 {{ literature.downloadCount ?? 0 }}</span>
            </div>
          </header>

          <section
            v-if="literature.aiSummary?.trim()"
            id="section-ai-summary"
            class="reader-body__section"
          >
            <h2>AI 摘要</h2>
            <p>{{ literature.aiSummary }}</p>
          </section>

          <section
            v-if="abstractBlocks.length"
            id="section-abstract"
            class="reader-body__section"
          >
            <h2>摘要</h2>
            <p v-for="(paragraph, index) in abstractBlocks" :key="index">
              {{ paragraph }}
            </p>
          </section>

          <section
            v-if="keywordTokens.length"
            id="section-keywords"
            class="reader-body__section"
          >
            <h2>关键词</h2>
            <section class="keyword-ribbon">
              <span v-for="token in keywordTokens" :key="token">{{
                token
              }}</span>
            </section>
          </section>

          <section class="reader-body__section">
            <h2>正文</h2>
            <p class="reader-leaf__note">
              {{
                hasChunks
                  ? `共 ${totalChunks} 段，当前位置第 ${currentChunk + 1} 段`
                  : emptyChunkHint
              }}
            </p>
            <div v-if="chunkLoading" class="chunk-loading">
              <el-skeleton :rows="4" animated />
            </div>
            <div v-else-if="hasChunks" class="reader-chunks">
              <article
                v-for="item in chunkItems"
                :id="`chunk-${item.id}`"
                :key="item.key"
                :data-chunk-index="item.chunkIndex"
                class="chunk-card"
                :class="{ 'is-current': item.chunkIndex === currentChunk }"
              >
                <header class="chunk-card__head">
                  <div class="chunk-card__meta">
                    <strong>第 {{ item.chunkIndex + 1 }} 段</strong>
                  </div>
                  <el-tag
                    v-if="item.chunkIndex === currentChunk"
                    size="small"
                    type="success"
                  >
                    正在阅读
                  </el-tag>
                </header>
                <div class="chunk-card__body">
                  <p v-for="(paragraph, index) in item.paragraphs" :key="index">
                    {{ paragraph }}
                  </p>
                  <p v-if="!item.paragraphs.length" class="reader-leaf__note">
                    该分块内容为空或仅包含空白字符。
                  </p>
                </div>
              </article>
            </div>
            <el-empty v-else description="暂无可读正文" />
          </section>
        </article>

        <!-- 右：智能问答 / 阅读笔记 -->
        <aside class="reader-side" aria-label="智能问答与阅读笔记">
          <div class="reader-side__tabs">
            <button
              type="button"
              :class="{ 'is-active': sideTab === 'qa' }"
              @click="sideTab = 'qa'"
            >
              智能问答
            </button>
            <button
              type="button"
              :class="{ 'is-active': sideTab === 'note' }"
              @click="sideTab = 'note'"
            >
              阅读笔记（{{ notes.length }}）
            </button>
          </div>

          <div v-show="sideTab === 'qa'" class="reader-side__body">
            <el-input
              v-model="qaForm.question"
              type="textarea"
              :rows="3"
              placeholder="围绕本篇提问，回答基于正文分块"
            />
            <div class="reader-side__row">
              <span>引用上限</span>
              <el-input-number
                v-model="qaForm.limit"
                :min="1"
                :max="8"
                size="small"
                controls-position="right"
              />
              <el-button
                type="primary"
                size="small"
                :icon="ChatLineRound"
                :loading="asking"
                @click="askQuestion"
              >
                提问
              </el-button>
            </div>
            <div class="reader-side__suggestions">
              <button
                v-for="question in suggestedQuestions"
                :key="question"
                type="button"
                @click="useSuggestion(question)"
              >
                {{ question }}
              </button>
            </div>
            <el-alert
              v-if="askError"
              :title="askError"
              type="error"
              :closable="false"
              show-icon
            />

            <section v-if="activeAnswer" class="reader-side__answer">
              <p class="reader-side__question">{{ activeAnswer.question }}</p>
              <p>{{ activeAnswer.answer }}</p>
              <div class="reader-side__refs" v-if="references.length">
                <p class="reader-side__caption">
                  引用片段 {{ references.length }} 条
                </p>
                <article v-for="reference in references" :key="reference.id">
                  <span>#{{ reference.chunkIndex ?? reference.id }}</span>
                  <p>{{ reference.content.slice(0, 120) }}</p>
                </article>
              </div>
            </section>
            <el-empty
              v-else
              description="输入问题并点击「提问」"
              :image-size="70"
            />

            <section class="reader-side__history" v-if="qaRecords.length">
              <p class="reader-side__caption">
                历史问答
                <el-button
                  :icon="Refresh"
                  text
                  size="small"
                  :loading="historyLoading"
                  @click="fetchQaHistory"
                />
              </p>
              <button
                v-for="(record, index) in qaRecords"
                :key="record.recordId ?? index"
                type="button"
                @click="activeAnswer = record"
              >
                {{ record.question }}
              </button>
            </section>
          </div>

          <div v-show="sideTab === 'note'" class="reader-side__body">
            <div class="reader-side__row">
              <span>第 {{ hasChunks ? currentChunk + 1 : '—' }} 段</span>
              <el-button
                type="primary"
                size="small"
                :icon="Plus"
                @click="openNoteCreate"
              >
                新建笔记
              </el-button>
            </div>
            <div v-loading="notesLoading" class="reader-side__notes">
              <article v-for="note in notes" :key="note.id">
                <div class="reader-side__note-head">
                  <el-tag v-if="note.aiGenerated" size="small" type="success"
                    >AI</el-tag
                  >
                  <strong>{{ note.title }}</strong>
                  <span class="reader-side__note-actions">
                    <el-button
                      :icon="EditPen"
                      text
                      size="small"
                      @click="openNoteEdit(note)"
                    />
                    <el-button
                      :icon="Delete"
                      text
                      size="small"
                      @click="removeNote(note)"
                    />
                  </span>
                </div>
                <p>{{ note.content }}</p>
              </article>
              <el-empty
                v-if="!notes.length"
                description="暂无笔记，创建一条阅读批注"
                :image-size="70"
              />
            </div>
          </div>
        </aside>
      </div>

      <div class="reader-progressbar">
        <span>阅读进度 {{ progressPercent }}%</span>
        <div class="progress-line">
          <span :style="{ width: `${progressPercent}%` }" />
        </div>
        <span>{{ hasChunks ? `第 ${currentChunk + 1} / ${totalChunks} 段` : '—' }}</span>
      </div>
    </template>

    <el-drawer
      v-model="noteDrawerVisible"
      :title="noteMode === 'create' ? '新建阅读笔记' : '编辑阅读笔记'"
      size="480px"
    >
      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="noteForm.title" placeholder="例如：方法可复用点" />
        </el-form-item>
        <el-form-item label="来源">
          <el-segmented
            v-model="noteForm.aiGenerated"
            :options="[
              { label: '手动笔记', value: 0 },
              { label: 'AI 解析', value: 1 },
            ]"
          />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="noteForm.content" type="textarea" :rows="10" />
        </el-form-item>
        <el-button
          class="wide-action drawer-action"
          type="primary"
          :loading="noteSaving"
          @click="saveNote"
        >
          保存笔记
        </el-button>
      </el-form>
    </el-drawer>
  </section>
</template>
