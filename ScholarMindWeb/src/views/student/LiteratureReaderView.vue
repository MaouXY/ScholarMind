<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  ChatLineRound,
  Collection,
  Cpu,
  Document,
  Download,
  List,
  Notebook,
  Refresh,
  Star,
  StarFilled,
  Timer,
  UploadFilled,
} from '@element-plus/icons-vue'

import {
  createReadingNote,
  deleteReadingNote,
  favoriteLiterature,
  getLiteratureDownload,
  getLiteratureView,
  getReadingHistory,
  listLiteratureChunks,
  listReadingNotes,
  rebuildLiteratureIndex,
  saveReadingHistory,
  unfavoriteLiterature,
  updateReadingNote,
} from '@/api/literature'
import { downloadReadingNotes, openLiteratureFile } from '@/utils/download'
import { displayCategoryName } from '@/utils/format'
import type {
  Id,
  Literature,
  LiteratureChunk,
  ReadingHistory,
  ReadingNote,
  ReadingNotePayload,
} from '@/types/literature'
import '@/styles/literature-ext.css'

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
const selectedNote = ref<ReadingNote | null>(null)

const loading = ref(false)
const chunkLoading = ref(false)
const historyLoading = ref(false)
const notesLoading = ref(false)
const progressSaving = ref(false)
const noteSaving = ref(false)
const favoriteLoading = ref(false)
const indexBuilding = ref(false)
const loadFailed = ref(false)
const favorited = ref(false)

// 原文下载 / 笔记导出：走 blob + Authorization 专用通道
const downloadLoading = ref(false)
const noteExporting = ref(false)
const downloadCount = ref<number | null>(null)

const noteDrawerVisible = ref(false)
const noteMode = ref<'create' | 'edit'>('create')

// 分块浏览：每屏 chunkPageSize 块，可「加载更多」或直接跳转目录
const chunkPageSize = 4
const chunkPage = ref(1)
const currentChunkIndex = ref(0)

// 阅读计时：elapsedSeconds 每秒累加，保存进度时作为 readDuration 上报
const elapsedSeconds = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const readingState = reactive({
  lastPage: 1,
})

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

// 正文分块：按 chunkIndex 升序排列
const orderedChunks = computed(() =>
  [...chunks.value].sort((a, b) => (a.chunkIndex ?? 0) - (b.chunkIndex ?? 0)),
)

const totalChunks = computed(() => orderedChunks.value.length)

const chunkItems = computed(() =>
  orderedChunks.value.map((chunk) => ({
    key: `${chunk.id}-${chunk.chunkIndex}`,
    id: chunk.id,
    chunkIndex: chunk.chunkIndex,
    pageNo: chunk.pageNo ?? null,
    tokenCount: chunk.tokenCount ?? null,
    paragraphs: splitParagraphs(chunk.content),
  })),
)

const visibleChunks = computed(() =>
  chunkItems.value.slice(0, Math.max(1, chunkPage.value) * chunkPageSize),
)

const chunkTotalPages = computed(() =>
  Math.max(1, Math.ceil(totalChunks.value / chunkPageSize)),
)

function splitParagraphs(content: string) {
  return (content ?? '')
    .split(/\n+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

// 当前所在分块序号（从 0 开始，取 chunkIndex 的实际值）
const currentChunk = computed(() =>
  Math.min(
    Math.max(currentChunkIndex.value, 0),
    Math.max(totalChunks.value - 1, 0),
  ),
)

// 进度按「当前分块在有序列表中的位置 / 总分块数」换算，不依赖 chunkIndex 是否连续
const progressPercent = computed(() => {
  if (!totalChunks.value) return 0
  const position = orderedChunks.value.findIndex(
    (chunk) => chunk.chunkIndex === currentChunk.value,
  )
  return Math.round(
    ((position < 0 ? 0 : position + 1) / totalChunks.value) * 100,
  )
})

const savedProgressText = computed(() => {
  if (!history.value) return '暂无历史阅读记录'
  return `上次读到第 ${history.value.lastPage ?? 1} 块 · 进度 ${history.value.progress ?? 0}% · 已读 ${history.value.readDuration ?? 0} 秒`
})

const elapsedText = computed(() => {
  const minutes = Math.floor(elapsedSeconds.value / 60)
  const seconds = elapsedSeconds.value % 60
  return minutes > 0 ? `${minutes} 分 ${seconds} 秒` : `${seconds} 秒`
})

const hasChunks = computed(() => totalChunks.value > 0)

// 无分块时的兜底正文：摘要 + AI 摘要 + 关键词
const fallbackBlocks = computed(() => {
  const blocks: { title: string; content: string }[] = []
  if (literature.value?.abstractText?.trim()) {
    blocks.push({
      title: '摘要',
      content: literature.value.abstractText.trim(),
    })
  }
  if (literature.value?.aiSummary?.trim()) {
    blocks.push({
      title: 'AI 摘要',
      content: literature.value.aiSummary.trim(),
    })
  }
  if (keywordTokens.value.length) {
    blocks.push({ title: '关键词', content: keywordTokens.value.join(' / ') })
  }
  return blocks
})

function statusLabel(status?: Literature['status']) {
  const labels: Record<string, string> = {
    DRAFT: '草稿',
    PENDING_REVIEW: '审核中',
    PUBLISHED: '已发布',
    REJECTED: '已退回',
  }
  return status ? (labels[status] ?? status) : '未知状态'
}

function resetNoteForm() {
  Object.assign(noteForm, {
    literatureId: literatureId.value,
    title: '',
    content: '',
    aiGenerated: 0,
  })
  selectedNote.value = null
}

function jumpToChunk(index: number) {
  currentChunkIndex.value = index
  chunkPage.value = Math.floor(index / chunkPageSize) + 1
  readingState.lastPage = index + 1
  const target = chunkItems.value.find((item) => item.chunkIndex === index)
  window.setTimeout(() => {
    if (!target) return
    document
      .getElementById(`chunk-${target.id}`)
      ?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, 60)
}

// 当前分块序号 + 1，即阅读进度接口 lastPage 的语义
const chunkSpan = computed(() => currentChunk.value + 1)

// 输入框回显值：有分块时等于「当前分块序号 + 1」，无分块时沿用后端保存值
const lastPageModel = computed({
  get: () => (hasChunks.value ? chunkSpan.value : readingState.lastPage),
  set: (value: number | undefined | null) => {
    const next = Number(value ?? 1)
    if (!Number.isFinite(next)) return
    if (hasChunks.value) {
      jumpToChunk(next - 1)
    } else {
      readingState.lastPage = Math.max(1, next)
    }
  },
})

watch(chunkSpan, (value) => {
  readingState.lastPage = value
})

function loadMoreChunks() {
  chunkPage.value = Math.min(chunkPage.value + 1, chunkTotalPages.value)
  if (currentChunkIndex.value < (chunkPage.value - 1) * chunkPageSize) {
    currentChunkIndex.value = (chunkPage.value - 1) * chunkPageSize
  }
}

function loadAllChunks() {
  chunkPage.value = chunkTotalPages.value
}

// 把历史 lastPage（= chunkIndex + 1）映射回对应分块并展开到该分块
function restoreFromHistory(source?: ReadingHistory | null) {
  const savedChunkIndex = (source?.lastPage ?? 1) - 1
  const maxIndex = Math.max(totalChunks.value - 1, 0)
  const target = Math.min(Math.max(savedChunkIndex, 0), maxIndex)
  readingState.lastPage = source?.lastPage ?? 1
  jumpToChunk(target)
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
    favorited.value = response.favorited
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

// 详情 + 分块并行加载，分块失败不影响详情展示
async function loadChunks() {
  const id = literatureId.value
  if (!id) return

  await Promise.all([loadLiterature(), fetchChunks(true)])
  if (loadFailed.value) return

  await loadHistory()
  restoreFromHistory(history.value)
  await loadNotes()
}

async function loadHistory() {
  const id = literatureId.value
  if (!id) return

  historyLoading.value = true
  try {
    const result = await getReadingHistory(id)
    history.value = result
    if (!hasChunks.value) {
      // 无分块时直接回显后端保存值，有分块时由 restoreFromHistory 映射到分块序号
      readingState.lastPage = result?.lastPage ?? 1
    }
  } catch (error) {
    console.error(
      'reading history load failed, scenario=reading-history-get, literatureId=%s',
      id,
      error,
    )
  } finally {
    historyLoading.value = false
  }
}

async function refreshHistory() {
  await loadHistory()
  if (hasChunks.value) {
    restoreFromHistory(history.value)
  }
}

async function loadNotes() {
  const id = literatureId.value
  if (!id) return

  notesLoading.value = true
  try {
    const result = await listReadingNotes({
      literatureId: id,
      page: 1,
      size: 20,
    })
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

async function rebuildIndexAndReload() {
  const id = literatureId.value
  if (!id) return

  indexBuilding.value = true
  try {
    const result = await rebuildLiteratureIndex(id)
    await fetchChunks(true)
    ElMessage.success(
      hasChunks.value
        ? `索引已重建，本次加载 ${totalChunks.value} 个分块（后端报告 ${result.chunkCount} 块）`
        : `索引已重建，后端报告 ${result.chunkCount} 个分块，正在等待解析结果`,
    )
    if (hasChunks.value) {
      restoreFromHistory(history.value)
    }
  } catch (error) {
    console.error(
      'literature index rebuild failed, scenario=literature-index-rebuild, literatureId=%s',
      id,
      error,
    )
  } finally {
    indexBuilding.value = false
  }
}

async function saveProgress() {
  const id = literatureId.value
  if (!id) return

  // lastPage 存「当前分块序号 + 1」，无分块时沿用已保存值
  const lastPage = hasChunks.value
    ? currentChunk.value + 1
    : readingState.lastPage

  progressSaving.value = true
  try {
    const result = await saveReadingHistory(id, {
      lastPage,
      progress: progressPercent.value,
      readDuration: elapsedSeconds.value,
    })
    history.value = result
    readingState.lastPage = result?.lastPage ?? lastPage
    ElMessage.success(
      `阅读进度已保存（第 ${lastPage} 块 · ${progressPercent.value}%）`,
    )
  } catch (error) {
    console.error(
      'reading history save failed, scenario=reading-history-upsert, literatureId=%s, lastPage=%s, progress=%s, readDuration=%s',
      id,
      lastPage,
      progressPercent.value,
      elapsedSeconds.value,
      error,
    )
  } finally {
    progressSaving.value = false
  }
}

async function toggleFavorite() {
  const id = literatureId.value
  if (!id) return

  favoriteLoading.value = true
  try {
    if (favorited.value) {
      await unfavoriteLiterature(id)
      favorited.value = false
      ElMessage.success('已取消收藏')
    } else {
      await favoriteLiterature(id)
      favorited.value = true
      ElMessage.success('已收藏该文献')
    }
  } catch (error) {
    console.error(
      'favorite toggle failed, scenario=literature-favorite-toggle, literatureId=%s, favorited=%s',
      id,
      favorited.value,
      error,
    )
  } finally {
    favoriteLoading.value = false
  }
}

function openNoteCreate() {
  noteMode.value = 'create'
  resetNoteForm()
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
      'reading note save failed, scenario=reading-note-save, noteId=%s, literatureId=%s, title=%s',
      selectedNote.value?.id,
      noteForm.literatureId,
      noteForm.title,
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
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
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

function goDetail() {
  router.push(`/student/literature/${literatureId.value}`)
}

/** 业务错误码 14107：该文献未绑定原文文件 */
function isMissingFileError(error: unknown) {
  const target = error as { code?: number; message?: string } | null
  return target?.code === 14107 || (target?.message ?? '').includes('14107')
}

/** 换取 RustFS 预签名地址后打开原文 */
async function downloadLiterature() {
  const id = literatureId.value
  if (!id) return

  downloadLoading.value = true
  try {
    const ticket = await getLiteratureDownload(id)
    await openLiteratureFile(ticket)
    downloadCount.value = ticket.downloadCount
    if (literature.value) {
      literature.value = {
        ...literature.value,
        downloadCount: ticket.downloadCount,
      }
    }
    ElMessage.success(`已开始下载：${ticket.fileName}`)
  } catch (error) {
    console.error(
      'literature download failed, scenario=literature-download, literatureId=%s',
      id,
      error,
    )
    if (isMissingFileError(error)) {
      ElMessage.warning('该文献未绑定原文文件，可联系上传者补充附件')
    }
  } finally {
    downloadLoading.value = false
  }
}

/** 导出本篇阅读笔记（GET /notes/export?literatureId=<id>） */
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

function goQa() {
  router.push(`/student/literature/${literatureId.value}/qa`)
}

function goLibrary() {
  router.push('/student/library')
}

onMounted(async () => {
  await loadChunks()
  timer = setInterval(() => {
    elapsedSeconds.value += 1
  }, 1000)
})

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})
</script>

<template>
  <section class="library-page">
    <div class="spatial-hero compact-hero">
      <div>
        <p class="panel__eyebrow">Immersive Reader</p>
        <h2>在线阅读舱：分块正文、进度与批注一起沉淀</h2>
        <p>
          阅读页直接渲染后端解析出的正文分块，可跳转目录、按屏加载，进度按所处分块实时换算并沉淀为阅读历史与笔记。
        </p>
      </div>
      <div class="hero-orbit" aria-hidden="true">
        <span>Chunk</span>
        <span>Note</span>
        <span>Save</span>
      </div>
    </div>

    <section v-if="loading" class="reader-surface" aria-label="阅读页加载中">
      <el-skeleton :rows="10" animated />
    </section>

    <section
      v-else-if="loadFailed || !literature"
      class="reader-surface"
      aria-label="阅读页异常"
    >
      <el-empty description="文献加载失败或不存在，请返回文献库重新选择">
        <div class="detail-actions">
          <el-button :icon="Refresh" type="primary" @click="loadChunks"
            >重新加载</el-button
          >
          <el-button :icon="ArrowLeft" plain @click="goLibrary"
            >返回文献库</el-button
          >
        </div>
      </el-empty>
    </section>

    <template v-else>
      <section
        class="reader-surface reader-surface--stage3"
        aria-label="在线阅读"
      >
        <div class="reader-toolbar">
          <el-button
            :icon="favorited ? StarFilled : Star"
            type="primary"
            plain
            :loading="favoriteLoading"
            @click="toggleFavorite"
          >
            {{ favorited ? '已收藏' : '收藏' }}
          </el-button>
          <el-button :icon="ArrowLeft" plain @click="goDetail"
            >返回详情</el-button
          >
          <el-button :icon="ChatLineRound" plain @click="goQa"
            >去问答</el-button
          >
          <el-button
            :icon="Timer"
            plain
            type="primary"
            :loading="progressSaving"
            @click="saveProgress"
          >
            保存进度
          </el-button>
          <el-button :icon="Notebook" plain @click="openNoteCreate"
            >新建笔记</el-button
          >
          <el-button
            :icon="Download"
            plain
            :loading="downloadLoading"
            @click="downloadLiterature"
          >
            下载原文
          </el-button>
          <el-button
            :icon="Document"
            plain
            :loading="noteExporting"
            @click="exportNotes"
          >
            导出本篇笔记
          </el-button>
          <el-button
            :icon="Cpu"
            plain
            :loading="indexBuilding"
            @click="rebuildIndexAndReload"
          >
            重建索引并刷新
          </el-button>
          <el-button
            :icon="Refresh"
            plain
            :loading="chunkLoading"
            @click="fetchChunks()"
          >
            刷新分块
          </el-button>
          <el-button
            :icon="Refresh"
            plain
            :loading="historyLoading"
            @click="refreshHistory"
          >
            刷新进度
          </el-button>
        </div>

        <div class="reader-shell">
          <article class="reader-paper">
            <div class="reader-titlebar">
              <div>
                <p class="panel__eyebrow">
                  {{ displayCategoryName(literature.categoryName ?? null) }}
                </p>
                <h2>{{ literature.title }}</h2>
              </div>
              <el-tag :type="favorited ? 'success' : 'info'" effect="light">
                {{ favorited ? '已收藏' : '未收藏' }}
              </el-tag>
            </div>

            <div class="reader-meta">
              <span>{{ literature.author || '未知作者' }}</span>
              <span>{{ literature.source || '未填写来源' }}</span>
              <span>{{ literature.publishYear || '年份未知' }}</span>
              <span>{{ literature.language || '语言未填写' }}</span>
              <span>{{ statusLabel(literature.status) }}</span>
            </div>

            <section class="parse-status-grid">
              <div>
                <span>当前分块</span>
                <strong>{{ hasChunks ? `#${currentChunk}` : '—' }}</strong>
              </div>
              <div>
                <span>分块总数</span>
                <strong>{{ totalChunks }}</strong>
              </div>
              <div>
                <span>阅读进度</span>
                <strong>{{ progressPercent }}%</strong>
              </div>
              <div>
                <span>本次时长</span>
                <strong>{{ elapsedText }}</strong>
              </div>
              <div>
                <span>下载次数</span>
                <strong>{{
                  downloadCount ?? literature.downloadCount ?? 0
                }}</strong>
              </div>
            </section>

            <section class="reader-section" v-if="literature.abstractText">
              <h3>摘要</h3>
              <p v-for="(paragraph, index) in abstractBlocks" :key="index">
                {{ paragraph }}
              </p>
            </section>

            <section class="reader-section">
              <h3>AI 摘要</h3>
              <p>
                {{
                  literature.aiSummary || '暂无 AI 摘要，可在文献详情页生成。'
                }}
              </p>
            </section>

            <section class="keyword-ribbon" v-if="keywordTokens.length">
              <span v-for="token in keywordTokens" :key="token">{{
                token
              }}</span>
            </section>

            <section class="reader-section">
              <h3>正文</h3>
              <p class="reader-leaf__note">
                {{
                  hasChunks
                    ? `共 ${totalChunks} 个分块，当前显示前 ${visibleChunks.length} 个，可使用右侧目录跳转或点击「加载更多」。`
                    : '当前文献尚未建立正文分块索引，请点击「重建索引」或等待解析完成。'
                }}
              </p>
            </section>

            <section
              v-if="hasChunks"
              class="chunk-list"
              :class="{ 'is-busy': chunkLoading }"
            >
              <article
                v-for="item in visibleChunks"
                :id="`chunk-${item.id}`"
                :key="item.key"
                class="chunk-card"
                :class="{ 'is-current': item.chunkIndex === currentChunk }"
                @click="jumpToChunk(item.chunkIndex)"
              >
                <header class="chunk-card__head">
                  <div class="chunk-card__meta">
                    <strong>#{{ item.chunkIndex }}</strong>
                    <span v-if="item.pageNo !== null"
                      >第 {{ item.pageNo }} 页</span
                    >
                    <span v-if="item.tokenCount !== null"
                      >{{ item.tokenCount }} tokens</span
                    >
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
            </section>

            <section v-else class="reading-grid">
              <div
                v-for="block in fallbackBlocks"
                :key="block.title"
                class="reader-leaf"
              >
                <h3>{{ block.title }}</h3>
                <p>{{ block.content }}</p>
              </div>
              <div v-if="!fallbackBlocks.length" class="reader-leaf">
                <h3>暂无可用正文</h3>
                <p class="reader-leaf__note">
                  当前文献尚未建立正文分块索引，请点击「重建索引并刷新」或等待解析完成。
                </p>
              </div>
            </section>

            <section v-if="hasChunks" class="chunk-pager">
              <div class="chunk-pager__info">
                已显示 {{ visibleChunks.length }} / {{ totalChunks }} 块（第
                {{ chunkPage }} / {{ chunkTotalPages }} 屏）
              </div>
              <div class="chunk-pager__actions">
                <el-button
                  :icon="Refresh"
                  plain
                  :disabled="visibleChunks.length >= totalChunks"
                  @click="loadMoreChunks"
                >
                  加载更多
                </el-button>
                <el-button
                  plain
                  :disabled="visibleChunks.length >= totalChunks"
                  @click="loadAllChunks"
                >
                  加载全部
                </el-button>
                <el-button
                  plain
                  :disabled="visibleChunks.length <= chunkPageSize"
                  @click="chunkPage = 1"
                >
                  收起
                </el-button>
              </div>
            </section>

            <section class="reader-section" v-if="literature.fileUrl">
              <h3>原文文件</h3>
              <div class="file-info">
                <span>{{ literature.fileType || '未知类型' }}</span>
                <a :href="literature.fileUrl" target="_blank" rel="noreferrer">
                  <el-button :icon="Document" plain>打开原文</el-button>
                </a>
                <el-button :icon="Download" plain @click="goDetail"
                  >在详情页查看文件信息</el-button
                >
              </div>
            </section>

            <section class="reading-progress-panel">
              <div class="panel-heading">
                <div>
                  <p class="panel__eyebrow">Reading History</p>
                  <h3>阅读进度</h3>
                </div>
                <span>{{ progressPercent }}%</span>
              </div>
              <el-slider
                :model-value="progressPercent"
                :min="0"
                :max="100"
                disabled
              />
              <div class="history-inputs">
                <el-input-number
                  v-model="lastPageModel"
                  :min="1"
                  :max="Math.max(totalChunks, 1)"
                  controls-position="right"
                />
                <span>分块序号 + 1（保存值）</span>
              </div>
              <div class="progress-live">
                <span
                  >当前第 {{ hasChunks ? currentChunk : '—' }} 块 / 共
                  {{ totalChunks }} 块</span
                >
                <span>累计阅读时长：{{ elapsedText }}</span>
                <span>{{ savedProgressText }}</span>
              </div>
              <el-button
                :icon="UploadFilled"
                type="primary"
                class="wide-action"
                :loading="progressSaving"
                @click="saveProgress"
              >
                保存进度
              </el-button>
            </section>
          </article>

          <aside class="glass-panel chunk-toc" aria-label="正文导航">
            <div class="panel-heading">
              <div>
                <p class="panel__eyebrow">Chunk Directory</p>
                <h3>正文导航</h3>
              </div>
              <el-tag effect="plain">{{ totalChunks }} 块</el-tag>
            </div>
            <el-select
              v-if="hasChunks"
              :model-value="currentChunk"
              class="chunk-toc__select"
              @update:model-value="jumpToChunk(Number($event))"
            >
              <el-option
                v-for="item in chunkItems"
                :key="item.key"
                :label="`#${item.chunkIndex}${item.pageNo !== null ? ` · 第 ${item.pageNo} 页` : ''}${item.tokenCount !== null ? ` · ${item.tokenCount} tokens` : ''}`"
                :value="item.chunkIndex"
              />
            </el-select>
            <div class="chunk-toc__list" v-if="hasChunks">
              <button
                v-for="item in chunkItems"
                :key="item.key"
                type="button"
                :class="{ 'is-active': item.chunkIndex === currentChunk }"
                @click="jumpToChunk(item.chunkIndex)"
              >
                <span>#{{ item.chunkIndex }}</span>
                <small v-if="item.pageNo !== null"
                  >第 {{ item.pageNo }} 页</small
                >
                <small v-if="item.tokenCount !== null"
                  >{{ item.tokenCount }} tokens</small
                >
                <em>{{ item.paragraphs[0]?.slice(0, 28) || '（空分块）' }}</em>
              </button>
            </div>
            <el-empty v-else description="暂无分块目录" />
            <el-button
              :icon="Cpu"
              class="wide-action drawer-action"
              :loading="indexBuilding"
              @click="rebuildIndexAndReload"
            >
              重建索引并刷新
            </el-button>
          </aside>
        </div>
      </section>

      <section class="glass-panel note-board" aria-label="阅读笔记">
        <div class="panel-heading">
          <div>
            <p class="panel__eyebrow">Reading Notes</p>
            <h3>阅读笔记</h3>
          </div>
          <div class="detail-actions">
            <el-tag effect="plain">
              <el-icon><List /></el-icon>
              第 {{ hasChunks ? currentChunk : '—' }} 块
            </el-tag>
            <el-button :icon="Collection" plain @click="openNoteCreate"
              >添加笔记</el-button
            >
          </div>
        </div>
        <div class="note-list" v-if="notes.length" v-loading="notesLoading">
          <article v-for="note in notes" :key="note.id" class="note-card">
            <div>
              <el-tag v-if="note.aiGenerated" size="small" type="success"
                >AI</el-tag
              >
              <h4>{{ note.title }}</h4>
              <p>{{ note.content }}</p>
            </div>
            <div class="note-actions">
              <el-button text @click="openNoteEdit(note)">编辑</el-button>
              <el-button text type="danger" @click="removeNote(note)"
                >删除</el-button
              >
            </div>
          </article>
        </div>
        <el-empty v-else description="暂无笔记，创建一条阅读批注" />
      </section>
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
