<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  ChatLineRound,
  Delete,
  Download,
  EditPen,
  FullScreen,
  Plus,
  Refresh,
  Warning,
} from '@element-plus/icons-vue'

import {
  askLiteratureQuestion,
  createReadingNote,
  deleteReadingNote,
  getLiteratureDownload,
  getLiteraturePreview,
  getLiteratureView,
  getReadingHistory,
  listLiteratureChunks,
  listLiteratureQaRecords,
  listReadingNotes,
  saveReadingHistory,
  updateReadingNote,
} from '@/api/literature'
import { downloadReadingNotes, openLiteratureFile } from '@/utils/download'
import {
  displayCategoryName,
  displayLanguage,
  displayLiteratureType,
} from '@/utils/format'
import { hasAiSummary } from '@/utils/summary'
import AiSummaryPanel from '@/components/AiSummaryPanel.vue'
import type {
  Id,
  Literature,
  LiteratureChunk,
  LiteraturePreviewTicket,
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
const loadFailed = ref(false)
const noteExporting = ref(false)

/** 原文预览票据：内联渲染用，不累加下载次数 */
const preview = ref<LiteraturePreviewTicket | null>(null)
const previewLoading = ref(false)
const previewFailed = ref(false)

const noteDrawerVisible = ref(false)
const noteMode = ref<'create' | 'edit'>('create')
const selectedNote = ref<ReadingNote | null>(null)

/** 右栏：智能问答 / 阅读笔记 */
const sideTab = ref<'qa' | 'note'>('qa')

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

const hasSummary = computed(() => hasAiSummary(literature.value?.aiSummary))

const orderedChunks = computed(() =>
  [...chunks.value].sort((a, b) => (a.chunkIndex ?? 0) - (b.chunkIndex ?? 0)),
)

const totalChunks = computed(() => orderedChunks.value.length)

/** 目录：摘要 / 关键词 / 原文各分块（分块标题取首句，便于定位） */
const tocItems = computed(() => {
  const items: { id: string; label: string; hint: string }[] = []
  if (hasSummary.value) {
    items.push({ id: 'section-ai-summary', label: 'AI 摘要', hint: '智能生成' })
  }
  if (abstractBlocks.value.length) {
    items.push({ id: 'section-abstract', label: '摘要', hint: '原文摘录' })
  }
  if (keywordTokens.value.length) {
    items.push({ id: 'section-keywords', label: '关键词', hint: '主题词' })
  }
  orderedChunks.value.forEach((chunk) => {
    items.push({
      id: `chunk-${chunk.id}`,
      label: `第 ${(chunk.chunkIndex ?? 0) + 1} 段`,
      hint: firstLine(chunk.content).slice(0, 22) || '（空分块）',
    })
  })
  return items
})

const elapsedText = computed(() => {
  const minutes = Math.floor(elapsedSeconds.value / 60)
  const seconds = elapsedSeconds.value % 60
  return minutes > 0 ? `${minutes} 分 ${seconds} 秒` : `${seconds} 秒`
})

/** 原文阅读进度：iframe 内无法读取滚动位置，按分块数量与已读分块估算 */
const progressPercent = computed(() => {
  const total = totalChunks.value
  if (!total) {
    return preview.value ? 100 : Math.min(history.value?.progress ?? 0, 100)
  }
  const saved = Math.min(Math.max(history.value?.lastPage ?? 1, 1), total)
  return Math.round((saved / total) * 100)
})

/** 原文能否内联渲染：docx 等浏览器无法内联，改为提示下载 */
const canInlineView = computed(() => Boolean(preview.value?.inlineViewable && preview.value?.url))
const previewRejectReason = computed(() => {
  if (!literature.value?.fileId) {
    return '该文献未绑定原文文件，可联系管理员补充附件'
  }
  if (previewFailed.value) {
    return '原文读取失败，请稍后重试或直接下载'
  }
  if (preview.value && !preview.value.inlineViewable) {
    return `当前原文为 ${preview.value.contentType || '未知格式'}，浏览器无法内联显示，请下载后阅读`
  }
  return ''
})

const references = computed(() => activeAnswer.value?.references ?? [])

function firstLine(content?: string | null) {
  return (content ?? '')
    .split(/\n+/)
    .map((item) => item.trim())
    .filter(Boolean)[0] ?? ''
}

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

/** 目录点击：定位到摘要 / 关键词 / 原文对应分块 */
function jumpTo(targetId: string) {
  document
    .getElementById(targetId)
    ?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

/** 用一屏高度展示原文，支持浏览器全屏（全屏后由浏览器阅读器接管滚动） */
function openPreviewFullscreen() {
  if (!preview.value?.url) return
  const frame = document.getElementById('original-frame') as HTMLIFrameElement | null
  frame?.requestFullscreen?.().catch((error: unknown) => {
    console.warn(
      'original preview fullscreen failed, scenario=literature-preview-fullscreen',
      error,
    )
  })
}

async function downloadOriginal() {
  const id = literatureId.value
  if (!id) return
  const ticket = await getLiteratureDownload(id)
  await openLiteratureFile(ticket)
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

/** 原文预览票据：不累加下载次数，失败时降级到「下载后阅读」 */
async function loadPreview() {
  const id = literatureId.value
  if (!id || !literature.value?.fileId) {
    preview.value = null
    return
  }

  previewLoading.value = true
  previewFailed.value = false
  try {
    preview.value = await getLiteraturePreview(id)
  } catch (error) {
    console.error(
      'literature preview ticket failed, scenario=literature-preview, literatureId=%s',
      id,
      error,
    )
    preview.value = null
    previewFailed.value = true
  } finally {
    previewLoading.value = false
  }
}

async function fetchChunks() {
  const id = literatureId.value
  if (!id) return

  chunkLoading.value = true
  try {
    const result = await listLiteratureChunks(id)
    chunks.value = Array.isArray(result) ? result : []
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
    history.value = await getReadingHistory(id)
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

async function persistProgress() {
  const id = literatureId.value
  if (!id || loadFailed.value) return

  try {
    history.value = await saveReadingHistory(id, {
      lastPage: Math.max(history.value?.lastPage ?? 1, 1),
      progress: progressPercent.value,
      readDuration: elapsedSeconds.value,
    })
  } catch (error) {
    console.warn(
      'reading progress auto save failed, scenario=reading-history-auto-save, literatureId=%s',
      id,
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

/** 滚动时按已进入视口的分块同步阅读进度 */
function syncProgressFromViewport() {
  if (!totalChunks.value) return
  const cards = document.querySelectorAll<HTMLElement>('.chunk-card')
  let activeIndex: number | null = null
  cards.forEach((card) => {
    if (card.getBoundingClientRect().top <= 160) {
      const index = Number(card.dataset.chunkIndex)
      if (Number.isFinite(index)) activeIndex = index
    }
  })
  if (activeIndex === null) return
  const nextLastPage = activeIndex + 1
  if (nextLastPage === (history.value?.lastPage ?? 0)) return
  history.value = history.value
    ? { ...history.value, lastPage: nextLastPage }
    : history.value
  scheduleProgressSave()
}

function handleScroll() {
  if (scrollFrame !== null) return
  scrollFrame = window.requestAnimationFrame(() => {
    scrollFrame = null
    syncProgressFromViewport()
  })
}

async function loadPage() {
  const id = literatureId.value
  if (!id) return

  await Promise.all([loadLiterature(), fetchChunks()])
  if (loadFailed.value) return

  await Promise.all([loadHistory(), loadPreview(), loadNotes(), fetchQaHistory()])
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
  await loadPage()
  timer = setInterval(() => {
    elapsedSeconds.value += 1
    if (elapsedSeconds.value % 30 === 0) scheduleProgressSave()
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
          <el-button :icon="Refresh" type="primary" @click="loadPage"
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
          <span>{{ elapsedText }}</span>
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
              @click="jumpTo(item.id)"
            >
              <span>{{ item.label }}</span>
              <em>{{ item.hint }}</em>
            </button>
            <p v-if="!tocItems.length" class="reader-toc__empty">暂无目录</p>
          </nav>
        </aside>

        <!-- 中：题录 + AI 摘要 + 摘要 + 关键词 + 原文 -->
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
            v-if="hasSummary"
            id="section-ai-summary"
            class="reader-body__section"
          >
            <h2>AI 摘要</h2>
            <AiSummaryPanel :summary="literature.aiSummary" density="full" />
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
            <div class="reader-body__section-head">
              <h2>原文</h2>
              <div class="reader-original__actions">
                <el-button
                  v-if="canInlineView"
                  :icon="FullScreen"
                  text
                  size="small"
                  @click="openPreviewFullscreen"
                >
                  全屏
                </el-button>
                <el-button
                  :icon="Download"
                  text
                  size="small"
                  :disabled="!literature.fileId"
                  @click="downloadOriginal"
                >
                  下载原文
                </el-button>
              </div>
            </div>

            <div v-if="previewLoading" class="reader-original__placeholder">
              <el-skeleton :rows="6" animated />
            </div>
            <div
              v-else-if="canInlineView"
              class="reader-original__frame-wrap"
            >
              <iframe
                id="original-frame"
                class="reader-original__frame"
                :src="preview?.url"
                :title="`${literature.title} 原文`"
                loading="lazy"
              />
              <p class="reader-leaf__note">
                浏览器原生渲染原文（{{ preview?.contentType }}，{{
                  preview?.fileName
                }}）；翻页与缩放由浏览器阅读器提供。
              </p>
            </div>
            <div v-else class="reader-original__placeholder">
              <el-icon class="reader-original__icon"><Warning /></el-icon>
              <p>{{ previewRejectReason }}</p>
              <el-button
                type="primary"
                plain
                :icon="Download"
                :disabled="!literature.fileId"
                @click="downloadOriginal"
              >
                下载原文后阅读
              </el-button>
            </div>
          </section>

          <!-- 索引分块：只作检索与目录定位，正文由上方原文承载 -->
          <section v-if="orderedChunks.length" class="reader-body__section">
            <h2>切分索引</h2>
            <p class="reader-leaf__note">
              共 {{ totalChunks }} 段，供检索定位与问答引用；正文请在上方原文中阅读。
            </p>
            <div class="reader-chunks">
              <article
                v-for="chunk in orderedChunks"
                :id="`chunk-${chunk.id}`"
                :key="chunk.id"
                :data-chunk-index="chunk.chunkIndex"
                class="chunk-card"
              >
                <header class="chunk-card__head">
                  <div class="chunk-card__meta">
                    <strong>第 {{ (chunk.chunkIndex ?? 0) + 1 }} 段</strong>
                    <span v-if="chunk.tokenCount">
                      {{ chunk.tokenCount }} tokens
                    </span>
                  </div>
                </header>
                <div class="chunk-card__body">
                  <p>{{ firstLine(chunk.content) }}</p>
                </div>
              </article>
            </div>
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
              <span>{{ totalChunks ? `共 ${totalChunks} 段` : '无索引分块' }}</span>
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
        <span>{{ totalChunks ? `第 ${history?.lastPage ?? 1} / ${totalChunks} 段` : '—' }}</span>
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
