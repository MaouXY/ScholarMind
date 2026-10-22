<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Connection,
  DocumentAdd,
  EditPen,
  InfoFilled,
  Notebook,
  Reading,
  Search,
  Star,
  StarFilled,
  UploadFilled,
} from '@element-plus/icons-vue'

import {
  createLiterature,
  createReadingNote,
  deleteReadingNote,
  favoriteLiterature,
  getLiteratureView,
  getReadingHistory,
  hybridSearch,
  listCategories,
  listLiteratures,
  listReadingNotes,
  markLiteratureRead,
  naturalLanguageSearch,
  semanticSearch,
  submitLiterature,
  unfavoriteLiterature,
  updateLiterature,
  updateReadingNote,
  uploadLiteratureFile,
} from '@/api/literature'
import { displayCategoryName } from '@/utils/format'
import { getStoredUser } from '@/utils/storage'
import '@/styles/literature-ext.css'
import type {
  Id,
  Literature,
  LiteratureCategory,
  LiteratureDetail,
  LiteraturePayload,
  LiteratureReferenceChunk,
  LiteratureSearchResult,
  LiteratureStatus,
  LiteratureVisibility,
  ReadingHistory,
  ReadingNote,
  ReadingNotePayload,
} from '@/types/literature'

const demoLiteratures: Literature[] = [
  {
    id: '1200000000000000001',
    title:
      'Research on Intelligent Recommendation Service in University Digital Library',
    author: 'Li Ming',
    source: 'Library Information Service',
    publishYear: 2024,
    language: 'zh-CN',
    categoryName: 'Recommendation System',
    uploaderId: '1000000000000000003',
    visibility: 'PUBLIC',
    status: 'PUBLISHED',
    abstractText:
      'This paper discusses personalized resource recommendation for university digital libraries and proposes a hybrid method based on keywords and user behavior.',
    aiSummary:
      '论文围绕高校数字图书馆的个性化推荐服务展开，重点比较关键词匹配与用户行为建模的协同效果，适合作为推荐系统与数字图书馆结合方向的综述入口。',
    keywords:
      'digital library,recommendation system,user behavior,personalization',
    readCount: 36,
    downloadCount: 8,
  },
  {
    id: '1200000000000000002',
    title:
      'Semantic Retrieval Method for Academic Literature Based on User Intent',
    author: 'Wang Qian',
    source: 'Modern Information',
    publishYear: 2023,
    language: 'zh-CN',
    categoryName: 'Information Retrieval',
    uploaderId: '1000000000000000003',
    visibility: 'PUBLIC',
    status: 'PUBLISHED',
    abstractText:
      'This study analyzes natural language search intent and improves academic literature discovery through structured retrieval conditions.',
    aiSummary:
      '文章从自然语言检索意图识别切入，把用户问题拆解为主题、方法、对象和约束条件，对构建 AI 文献检索助手的查询理解模块很有参考价值。',
    keywords:
      'semantic retrieval,natural language,academic literature,user intent',
    readCount: 28,
    downloadCount: 5,
  },
]

const demoNotes: ReadingNote[] = [
  {
    id: '9000000000000000001',
    literatureId: '1200000000000000001',
    title: '可复用模块',
    content: '推荐系统部分可以抽象为关键词召回、行为权重、混合排序三个模块。',
    aiGenerated: 0,
  },
  {
    id: '9000000000000000002',
    literatureId: '1200000000000000001',
    title: 'AI 解析建议',
    content:
      '适合用作“个性化资源推荐”章节的相关工作，对比用户行为和内容相似度。',
    aiGenerated: 1,
  },
]

const demoReferences: LiteratureReferenceChunk[] = [
  {
    id: '9100000000000000001',
    literatureId: '1200000000000000001',
    chunkIndex: 1,
    content:
      'The hybrid recommendation workflow combines keyword matching with behavior signals to reduce cold-start friction in digital library services.',
    score: 0.91,
  },
  {
    id: '9100000000000000002',
    literatureId: '1200000000000000002',
    chunkIndex: 3,
    content:
      'Natural language intent parsing maps student questions into topic, method, object and constraint fields before retrieval.',
    score: 0.86,
  },
]

const categories = ref<LiteratureCategory[]>([])
const literatures = ref<Literature[]>([])
const selected = ref<Literature | null>(null)
const detail = ref<LiteratureDetail | null>(null)
const notes = ref<ReadingNote[]>([])
const notesTotal = ref(0)
const selectedNote = ref<ReadingNote | null>(null)
const searchInsight = ref<LiteratureSearchResult | null>(null)
const readingHistory = ref<ReadingHistory | null>(null)
const loading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const noteSaving = ref(false)
const ragSearching = ref(false)
const editorVisible = ref(false)
const noteDrawerVisible = ref(false)
const editorMode = ref<'create' | 'edit'>('create')
const noteMode = ref<'create' | 'edit'>('create')
const favorited = ref(false)
const favoriteLoading = ref(false)
const abstractExpanded = ref(false)

const router = useRouter()

// 跳转到当前文献的独立页面：详情 / 在线阅读
function openLiteraturePage(target: 'detail' | 'read') {
  const literatureId = currentLiterature.value?.id
  if (!literatureId) {
    ElMessage.warning('请先选择一篇文献')
    return
  }
  const suffix = target === 'detail' ? '' : '/read'
  router.push(`/student/literature/${literatureId}${suffix}`)
}

const query = reactive({
  page: 1,
  size: 8,
  keyword: '',
  categoryId: undefined as Id | undefined,
  status: undefined as LiteratureStatus | undefined,
  visibility: undefined as LiteratureVisibility | undefined,
})

const form = reactive<LiteraturePayload>({
  title: '',
  author: '',
  source: '',
  publishYear: new Date().getFullYear(),
  language: 'zh-CN',
  categoryId: undefined,
  visibility: 'PRIVATE',
  fileId: undefined,
  abstractText: '',
  keywords: '',
})

const ragForm = reactive({
  mode: 'natural' as 'natural' | 'semantic' | 'hybrid',
  query: '帮我找出适合构建 AI 文献检索助手的研究方法',
  limit: 5,
})

const noteForm = reactive<ReadingNotePayload>({
  literatureId: '',
  title: '',
  content: '',
  aiGenerated: 0,
})

const total = ref(0)

const currentLiterature = computed(
  () => detail.value?.literature ?? selected.value,
)

const currentUserId = ref(getStoredUser()?.id ?? '')

// 仅当前登录用户上传的文献才展示「编辑信息 / 提交审核」
const isOwnLiterature = computed(() => {
  const uploaderId = currentLiterature.value?.uploaderId
  return Boolean(
    uploaderId &&
      currentUserId.value &&
      String(uploaderId) === String(currentUserId.value),
  )
})

const ragModeLabel = computed(() => {
  if (ragForm.mode === 'semantic') return '语义向量'
  if (ragForm.mode === 'hybrid') return '混合检索'
  return '自然语言'
})

function statusLabel(status?: LiteratureStatus) {
  const labels: Record<LiteratureStatus, string> = {
    DRAFT: '草稿',
    PENDING_REVIEW: '审核中',
    PUBLISHED: '已发布',
    REJECTED: '已退回',
  }
  return status ? labels[status] : '全部'
}

function visibilityLabel(visibility?: LiteratureVisibility) {
  if (visibility === 'PUBLIC') return '公开'
  if (visibility === 'PRIVATE') return '私有'
  return '全部'
}

type RetrievalBadge = {
  label: string
  tone: 'primary' | 'success' | 'info' | 'warning'
}

function scorePercent(score?: number | null) {
  if (typeof score !== 'number' || Number.isNaN(score)) return 0
  return Math.round(Math.min(1, Math.max(0, score)) * 100)
}

// 语义 / 混合检索返回的引用片段可能携带稀疏、稠密名次与 RRF 融合分（旧数据可能为空）
function referenceRankBadges(
  reference: LiteratureReferenceChunk,
): RetrievalBadge[] {
  const badges: RetrievalBadge[] = []
  if (typeof reference.sparseRank === 'number') {
    badges.push({ label: `稀疏#${reference.sparseRank}`, tone: 'info' })
  }
  if (typeof reference.denseRank === 'number') {
    badges.push({ label: `向量#${reference.denseRank}`, tone: 'success' })
  }
  if (typeof reference.rrfScore === 'number') {
    badges.push({
      label: `RRF ${reference.rrfScore.toFixed(3)}`,
      tone: 'warning',
    })
  }
  return badges
}

function formatDateTime(value?: string | null) {
  if (!value) return ''
  return value.slice(0, 16).replace('T', ' ')
}

function truncateText(value: string, maxLength: number) {
  const normalized = value.replace(/\s+/g, ' ').trim()
  if (!normalized) return '暂无'
  return normalized.length > maxLength
    ? `${normalized.slice(0, maxLength)}…`
    : normalized
}

const categoryNameText = computed(() => {
  const item = currentLiterature.value
  if (!item) return '—'
  if (item.categoryName) return displayCategoryName(item.categoryName)
  const matched = categories.value.find(
    (category) => category.id === item.categoryId,
  )
  return matched ? displayCategoryName(matched.name) : '—'
})

const abstractText = computed(() =>
  (currentLiterature.value?.abstractText ?? '').trim(),
)

const abstractLong = computed(() => abstractText.value.length > 90)

const hasReadingHistory = computed(() => {
  const history = readingHistory.value
  if (!history) return false
  return (
    history.progress != null ||
    history.lastPage != null ||
    history.readDuration != null ||
    Boolean(history.updatedAt)
  )
})

const readingProgressText = computed(() => {
  const history = readingHistory.value
  if (!history || !hasReadingHistory.value) return '暂无阅读记录'
  const parts: string[] = [
    typeof history.progress === 'number'
      ? `进度 ${Math.round(history.progress)}%`
      : '进度 —',
  ]
  if (typeof history.lastPage === 'number' && history.lastPage > 0) {
    parts.push(`分块/页码 ${history.lastPage}`)
  }
  const updatedAt = formatDateTime(history.updatedAt)
  parts.push(updatedAt ? `上次阅读 ${updatedAt}` : '上次阅读时间未记录')
  return parts.join(' · ')
})

const latestNote = computed(() =>
  [...notes.value].sort((left, right) => {
    const leftTime = left.updatedAt ?? left.createdAt ?? ''
    const rightTime = right.updatedAt ?? right.createdAt ?? ''
    return rightTime.localeCompare(leftTime)
  })[0],
)

const noteTotalText = computed(() => {
  const totalCount = notesTotal.value
  if (!totalCount) return '暂无阅读笔记'
  const latest = latestNote.value
  if (!latest) return `共 ${totalCount} 条`
  return `共 ${totalCount} 条 · 最新：${truncateText(latest.content, 60)}`
})

async function loadReadingHistory(literatureId: Id) {
  try {
    readingHistory.value = await getReadingHistory(literatureId)
  } catch (error) {
    console.error(
      'reading history detail failed, scenario=reading-history-detail, literatureId=%s',
      literatureId,
      error,
    )
    readingHistory.value = null
  }
}

function resetForm() {
  Object.assign(form, {
    title: '',
    author: '',
    source: '',
    publishYear: new Date().getFullYear(),
    language: 'zh-CN',
    categoryId: undefined,
    visibility: 'PRIVATE',
    fileId: undefined,
    abstractText: '',
    keywords: '',
  })
}

function fillForm(item: Literature) {
  Object.assign(form, {
    title: item.title,
    author: item.author,
    source: item.source,
    publishYear: item.publishYear,
    language: item.language || 'zh-CN',
    categoryId: item.categoryId ?? undefined,
    visibility: item.visibility,
    fileId: item.fileId ?? undefined,
    abstractText: item.abstractText,
    keywords: item.keywords,
  })
}

function resetNoteForm() {
  Object.assign(noteForm, {
    literatureId: currentLiterature.value?.id ?? '',
    title: '',
    content: '',
    aiGenerated: 0,
  })
  selectedNote.value = null
}

function fillNoteForm(note: ReadingNote) {
  selectedNote.value = note
  Object.assign(noteForm, {
    literatureId: note.literatureId,
    title: note.title,
    content: note.content,
    aiGenerated: note.aiGenerated ?? 0,
  })
}

async function fetchCategories() {
  try {
    categories.value = await listCategories()
  } catch (error) {
    console.error(
      'category load failed, scenario=literature-category-list',
      error,
    )
    categories.value = [
      {
        id: '1100000000000000001',
        name: 'Digital Library',
        sort: 10,
        status: 1,
      },
      {
        id: '1100000000000000002',
        name: 'Information Retrieval',
        sort: 20,
        status: 1,
      },
      {
        id: '1100000000000000003',
        name: 'Artificial Intelligence',
        sort: 30,
        status: 1,
      },
    ]
  }
}

async function fetchLiteratures() {
  loading.value = true
  try {
    const result = await listLiteratures(query)
    literatures.value = result.items
    total.value = result.total
    if (!selected.value && result.items.length > 0) {
      await chooseLiterature(result.items[0])
    }
  } catch (error) {
    console.error(
      'literature list failed, scenario=literature-search, keyword=%s, categoryId=%s, status=%s',
      query.keyword,
      query.categoryId,
      query.status,
      error,
    )
    literatures.value = demoLiteratures
    total.value = demoLiteratures.length
    if (!selected.value) {
      await chooseLiterature(demoLiteratures[0])
    }
  } finally {
    loading.value = false
  }
}

async function runRagSearch() {
  if (!ragForm.query.trim()) {
    ElMessage.warning('请输入语义检索问题')
    return
  }

  ragSearching.value = true
  try {
    const payload = {
      query: ragForm.query,
      categoryId: query.categoryId,
      limit: ragForm.limit,
    }
    const result =
      ragForm.mode === 'semantic'
        ? await semanticSearch(payload)
        : ragForm.mode === 'hybrid'
          ? await hybridSearch(payload)
          : await naturalLanguageSearch(payload)
    searchInsight.value = result
    literatures.value = result.literatures
    total.value = result.literatures.length
    if (result.literatures.length > 0) {
      await chooseLiterature(result.literatures[0])
    }
    ElMessage.success('RAG 检索完成')
  } catch (error) {
    console.error(
      'rag search failed, scenario=literature-rag-search, mode=%s, query=%s, categoryId=%s, limit=%s',
      ragForm.mode,
      ragForm.query,
      query.categoryId,
      ragForm.limit,
      error,
    )
    searchInsight.value = {
      query: ragForm.query,
      literatures: demoLiteratures,
      references: demoReferences,
      parsedIntent:
        '演示模式：意图拆解为主题=AI 文献检索助手，方法=语义检索/RAG，目标=找到可复用设计。',
    }
    literatures.value = demoLiteratures
    total.value = demoLiteratures.length
    if (!selected.value) {
      await chooseLiterature(demoLiteratures[0])
    }
  } finally {
    ragSearching.value = false
  }
}

async function fetchNotes(literatureId: Id) {
  try {
    const result = await listReadingNotes({ literatureId, page: 1, size: 20 })
    notes.value = result.items
    notesTotal.value = result.total
  } catch (error) {
    console.error(
      'reading notes list failed, scenario=reading-note-list, literatureId=%s',
      literatureId,
      error,
    )
    notes.value = demoNotes.filter((note) => note.literatureId === literatureId)
    notesTotal.value = notes.value.length
  }
}

async function chooseLiterature(item: Literature) {
  selected.value = item
  abstractExpanded.value = false
  readingHistory.value = null
  detailLoading.value = true
  try {
    const response = await getLiteratureView(item.id)
    detail.value = response
    selected.value = response.literature
    favorited.value = response.favorited
    notes.value = response.recentNotes ?? []
    notesTotal.value = notes.value.length
    readingHistory.value = response.readingHistory ?? null
    // 旧版详情接口可能不返回阅读记录，仅在字段缺失时补一次请求
    if (response.readingHistory === undefined) {
      await loadReadingHistory(item.id)
    }
    await markLiteratureRead(item.id)
    await fetchNotes(item.id)
  } catch (error) {
    console.error(
      'literature detail view failed, scenario=literature-detail-view, literatureId=%s',
      item.id,
      error,
    )
    detail.value = {
      literature: item,
      favorited: false,
      readingHistory: null,
      recentNotes: demoNotes.filter((note) => note.literatureId === item.id),
    }
    favorited.value = false
    notes.value = detail.value.recentNotes
    notesTotal.value = notes.value.length
    readingHistory.value = null
  } finally {
    detailLoading.value = false
  }
}

function openCreate() {
  editorMode.value = 'create'
  resetForm()
  editorVisible.value = true
}

function openEdit() {
  if (!currentLiterature.value) return
  editorMode.value = 'edit'
  fillForm(currentLiterature.value)
  editorVisible.value = true
}

function openNoteCreate() {
  if (!currentLiterature.value) return
  noteMode.value = 'create'
  resetNoteForm()
  noteForm.literatureId = currentLiterature.value.id
  noteDrawerVisible.value = true
}

function openNoteEdit(note: ReadingNote) {
  noteMode.value = 'edit'
  fillNoteForm(note)
  noteDrawerVisible.value = true
}

async function saveLiterature() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写文献标题')
    return
  }

  saving.value = true
  try {
    const saved =
      editorMode.value === 'edit' && currentLiterature.value
        ? await updateLiterature(currentLiterature.value.id, form)
        : await createLiterature(form)
    selected.value = saved
    editorVisible.value = false
    ElMessage.success('文献已保存')
    await fetchLiteratures()
    await chooseLiterature(saved)
  } catch (error) {
    console.error(
      'literature save failed, scenario=literature-save, mode=%s, literatureId=%s, title=%s',
      editorMode.value,
      currentLiterature.value?.id,
      form.title,
      error,
    )
  } finally {
    saving.value = false
  }
}

async function submitCurrent() {
  if (!currentLiterature.value) return
  try {
    selected.value = await submitLiterature(currentLiterature.value.id)
    ElMessage.success('已提交审核')
    await fetchLiteratures()
  } catch (error) {
    console.error(
      'literature submit failed, scenario=literature-submit, literatureId=%s',
      currentLiterature.value.id,
      error,
    )
  }
}

async function toggleFavorite() {
  if (!currentLiterature.value || favoriteLoading.value) return
  const literatureId = currentLiterature.value.id
  const nextFavorited = !favorited.value
  favoriteLoading.value = true
  favorited.value = nextFavorited
  try {
    if (nextFavorited) {
      await favoriteLiterature(literatureId)
      ElMessage.success('已收藏')
    } else {
      await unfavoriteLiterature(literatureId)
      ElMessage.success('已取消收藏')
    }
  } catch (error) {
    // 请求失败时回滚到操作前的收藏状态，避免界面与后端不一致
    favorited.value = !nextFavorited
    ElMessage.error(
      nextFavorited ? '收藏失败，请稍后重试' : '取消收藏失败，请稍后重试',
    )
    console.error(
      'favorite toggle failed, scenario=literature-favorite-toggle, literatureId=%s, favorited=%s',
      literatureId,
      nextFavorited,
      error,
    )
  } finally {
    favoriteLoading.value = false
  }
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
    if (currentLiterature.value) {
      await fetchNotes(currentLiterature.value.id)
    }
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
    if (currentLiterature.value) {
      await fetchNotes(currentLiterature.value.id)
    }
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

async function handleNativeFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  uploading.value = true
  try {
    const uploaded = await uploadLiteratureFile(file)
    form.fileId = uploaded.id
    ElMessage.success('文件已上传并绑定到表单')
  } catch (error) {
    console.error(
      'file upload failed, scenario=literature-file-upload, filename=%s, size=%s',
      file.name,
      file.size,
      error,
    )
  } finally {
    uploading.value = false
    input.value = ''
  }
}

onMounted(async () => {
  await fetchCategories()
  await fetchLiteratures()
})
</script>

<template>
  <section class="library-page">
    <div class="spatial-hero">
      <div>
        <p class="panel__eyebrow">Stage 4 LLM + RAG Workspace</p>
        <h2>让语义检索、证据引用和论文问答在阅读舱里闭环</h2>
        <p>
          自然语言检索、向量召回与在线阅读围绕当前文献闭环：命中证据可追溯，
          阅读进度与笔记自动沉淀。
        </p>
      </div>
      <div class="hero-orbit" aria-hidden="true">
        <span>RAG</span>
        <span>Ask</span>
        <span>Cite</span>
      </div>
    </div>

    <div class="library-layout">
      <aside class="glass-panel search-panel" aria-label="文献检索">
        <div class="panel-heading">
          <div>
            <p class="panel__eyebrow">Search Lens</p>
            <h3>检索条件</h3>
          </div>
          <el-button :icon="DocumentAdd" type="primary" @click="openCreate">
            上传文献
          </el-button>
        </div>

        <el-form label-position="top">
          <el-form-item label="关键词">
            <el-input
              v-model="query.keyword"
              :prefix-icon="Search"
              placeholder="标题、作者、关键词、摘要"
              clearable
              @keyup.enter="fetchLiteratures"
            />
          </el-form-item>
          <el-form-item label="学科分类">
            <el-select
              v-model="query.categoryId"
              clearable
              placeholder="全部分类"
            >
              <el-option
                v-for="category in categories"
                :key="category.id"
                :label="displayCategoryName(category.name)"
                :value="category.id"
              />
            </el-select>
          </el-form-item>
          <div class="filter-grid">
            <el-form-item label="可见性">
              <el-select v-model="query.visibility" clearable>
                <el-option label="全部" value="" />
                <el-option label="公开" value="PUBLIC" />
                <el-option label="私有" value="PRIVATE" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="query.status" clearable>
                <el-option label="草稿" value="DRAFT" />
                <el-option label="审核中" value="PENDING_REVIEW" />
                <el-option label="已发布" value="PUBLISHED" />
                <el-option label="已退回" value="REJECTED" />
              </el-select>
            </el-form-item>
          </div>
          <el-button
            type="primary"
            class="wide-action"
            :loading="loading"
            @click="fetchLiteratures"
          >
            执行检索
          </el-button>
        </el-form>

        <section class="rag-search-box" aria-label="RAG 语义检索">
          <div class="panel-heading">
            <div>
              <p class="panel__eyebrow">Semantic Retrieval</p>
              <h3>RAG 检索</h3>
            </div>
            <el-tag effect="plain">{{ ragModeLabel }}</el-tag>
          </div>
          <el-segmented
            v-model="ragForm.mode"
            :options="[
              { label: '自然语言', value: 'natural' },
              { label: '语义检索', value: 'semantic' },
              { label: '混合检索', value: 'hybrid' },
            ]"
          />
          <el-input
            v-model="ragForm.query"
            type="textarea"
            :rows="4"
            placeholder="用研究问题描述你想找的文献"
          />
          <div class="rag-search-box__footer">
            <el-input-number
              v-model="ragForm.limit"
              :min="1"
              :max="10"
              controls-position="right"
            />
            <el-button
              :icon="Connection"
              type="primary"
              :loading="ragSearching"
              @click="runRagSearch"
            >
              RAG 检索
            </el-button>
          </div>
        </section>

        <div class="metric-row">
          <span>
            <strong>{{ total }}</strong>
            命中文献
          </span>
          <span>
            <strong>{{ categories.length }}</strong>
            分类
          </span>
        </div>

        <section v-if="searchInsight" class="rag-intent-panel">
          <p class="panel__eyebrow">Parsed Intent</p>
          <p>
            {{
              searchInsight.parsedIntent ||
              '后端返回了检索结果，暂无自然语言意图说明。'
            }}
          </p>
          <strong>{{ searchInsight.references.length }} 条证据片段</strong>
        </section>
      </aside>

      <section class="result-stack" aria-label="检索结果">
        <article
          v-for="item in literatures"
          :key="item.id"
          class="paper-card"
          :class="{ 'is-active': selected?.id === item.id }"
          @click="chooseLiterature(item)"
        >
          <div class="paper-card__status">
            <el-tag size="small" effect="plain">{{
              statusLabel(item.status)
            }}</el-tag>
            <span>{{ visibilityLabel(item.visibility) }}</span>
          </div>
          <h3>{{ item.title }}</h3>
          <p>
            {{
              item.abstractText || '暂无摘要，建议上传 PDF 后补充摘要和关键词。'
            }}
          </p>
          <div class="paper-card__meta">
            <span>{{ item.author || '未知作者' }}</span>
            <span>{{ item.source || '未填写来源' }}</span>
            <span>{{ item.publishYear || '年份未知' }}</span>
          </div>
        </article>

        <section
          v-if="searchInsight && searchInsight.references.length"
          class="glass-panel result-references"
          aria-label="检索命中证据片段"
        >
          <div class="panel-heading">
            <div>
              <p class="panel__eyebrow">Retrieved Chunks</p>
              <h3>命中证据片段</h3>
            </div>
            <el-tag size="small" effect="plain">
              {{ searchInsight.references.length }} 条
            </el-tag>
          </div>
          <div class="reference-list">
            <article
              v-for="reference in searchInsight.references"
              :key="reference.id"
            >
              <div>
                <strong>#{{ reference.chunkIndex ?? reference.id }}</strong>
                <span>{{ scorePercent(reference.score) }}%</span>
              </div>
              <div
                v-if="referenceRankBadges(reference).length"
                class="retrieval-badge-row"
              >
                <el-tag
                  v-for="badge in referenceRankBadges(reference)"
                  :key="badge.label"
                  size="small"
                  effect="plain"
                  :type="badge.tone"
                >
                  {{ badge.label }}
                </el-tag>
              </div>
              <p>{{ reference.content }}</p>
            </article>
          </div>
        </section>
      </section>

      <section
        class="reader-surface reader-surface--stage3"
        aria-label="论文解析阅读器"
      >
        <template v-if="currentLiterature">
          <div class="reader-toolbar" aria-label="文献操作">
            <el-button
              :icon="favorited ? StarFilled : Star"
              type="primary"
              plain
              :loading="favoriteLoading"
              @click="toggleFavorite"
            >
              {{ favorited ? '已收藏' : '收藏' }}
            </el-button>
            <el-button
              :icon="InfoFilled"
              type="primary"
              @click="openLiteraturePage('detail')"
            >
              查看详情
            </el-button>
            <el-button :icon="Reading" @click="openLiteraturePage('read')"
              >在线阅读</el-button
            >
            <template v-if="isOwnLiterature">
              <el-button :icon="EditPen" plain @click="openEdit"
                >编辑信息</el-button
              >
              <el-button
                plain
                :disabled="currentLiterature.visibility !== 'PUBLIC'"
                @click="submitCurrent"
              >
                提交审核
              </el-button>
            </template>
          </div>

          <article class="reader-paper" v-loading="detailLoading">
            <div class="reader-titlebar">
              <div>
                <p class="panel__eyebrow">{{ categoryNameText }}</p>
                <h2>{{ currentLiterature.title }}</h2>
              </div>
              <el-tag :type="favorited ? 'success' : 'info'" effect="light">
                {{ favorited ? '已收藏' : '未收藏' }}
              </el-tag>
            </div>

            <section class="reader-section detail-info" aria-label="文献详情">
              <div class="panel-heading">
                <div>
                  <p class="panel__eyebrow">Literature Profile</p>
                  <h3>文献详情</h3>
                </div>
              </div>
              <dl class="detail-info__list">
                <div class="detail-info__row">
                  <dt>标题</dt>
                  <dd>{{ currentLiterature.title || '—' }}</dd>
                </div>
                <div class="detail-info__row">
                  <dt>作者</dt>
                  <dd>{{ currentLiterature.author || '—' }}</dd>
                </div>
                <div class="detail-info__row">
                  <dt>年份</dt>
                  <dd>{{ currentLiterature.publishYear || '—' }}</dd>
                </div>
                <div class="detail-info__row">
                  <dt>分类</dt>
                  <dd>{{ categoryNameText }}</dd>
                </div>
                <div class="detail-info__row">
                  <dt>关键词</dt>
                  <dd>{{ currentLiterature.keywords || '暂无' }}</dd>
                </div>
                <div class="detail-info__row">
                  <dt>来源</dt>
                  <dd>{{ currentLiterature.source || '—' }}</dd>
                </div>
                <div class="detail-info__row">
                  <dt>摘要</dt>
                  <dd>
                    <p
                      v-if="abstractText"
                      class="detail-info__abstract"
                      :class="{ 'is-expanded': abstractExpanded }"
                    >
                      {{ abstractText }}
                    </p>
                    <span v-else class="detail-info__empty">暂无摘要</span>
                    <el-button
                      v-if="abstractLong"
                      text
                      type="primary"
                      class="detail-info__toggle"
                      @click="abstractExpanded = !abstractExpanded"
                    >
                      {{ abstractExpanded ? '收起' : '展开全文' }}
                    </el-button>
                  </dd>
                </div>
                <div class="detail-info__row">
                  <dt>阅读进度</dt>
                  <dd>{{ readingProgressText }}</dd>
                </div>
                <div class="detail-info__row">
                  <dt>阅读笔记</dt>
                  <dd>{{ noteTotalText }}</dd>
                </div>
              </dl>
            </section>

            <section class="note-board">
              <div class="panel-heading">
                <div>
                  <p class="panel__eyebrow">Reading Notes</p>
                  <h3>阅读笔记</h3>
                </div>
                <el-button :icon="Notebook" plain @click="openNoteCreate()"
                  >添加</el-button
                >
              </div>
              <div class="note-list" v-if="notes.length">
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
          </article>
        </template>
        <el-empty v-else description="检索后选择一篇文献开始阅读" />
      </section>
    </div>

    <el-drawer
      v-model="editorVisible"
      :title="editorMode === 'create' ? '上传文献' : '编辑文献'"
      size="520px"
    >
      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="form.title" />
        </el-form-item>
        <div class="filter-grid">
          <el-form-item label="作者">
            <el-input v-model="form.author" />
          </el-form-item>
          <el-form-item label="年份">
            <el-input-number
              v-model="form.publishYear"
              :min="1900"
              :max="2100"
            />
          </el-form-item>
        </div>
        <el-form-item label="来源">
          <el-input v-model="form.source" />
        </el-form-item>
        <div class="filter-grid">
          <el-form-item label="分类">
            <el-select
              v-model="form.categoryId"
              clearable
              placeholder="选择分类"
            >
              <el-option
                v-for="category in categories"
                :key="category.id"
                :label="displayCategoryName(category.name)"
                :value="category.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="可见性">
            <el-segmented
              v-model="form.visibility"
              :options="[
                { label: '私有', value: 'PRIVATE' },
                { label: '公开', value: 'PUBLIC' },
              ]"
            />
          </el-form-item>
        </div>
        <el-form-item label="关键词">
          <el-input v-model="form.keywords" placeholder="用英文逗号分隔" />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="form.abstractText" type="textarea" :rows="5" />
        </el-form-item>
        <label class="upload-drop">
          <el-icon><UploadFilled /></el-icon>
          <span>{{
            form.fileId ? `已绑定文件 #${form.fileId}` : '选择 PDF 或文本文件'
          }}</span>
          <input
            type="file"
            accept=".pdf,.txt,application/pdf,text/plain"
            @change="handleNativeFile"
          />
        </label>
        <el-button
          class="wide-action drawer-action"
          type="primary"
          :loading="saving || uploading"
          @click="saveLiterature"
        >
          保存文献
        </el-button>
      </el-form>
    </el-drawer>

    <el-drawer
      v-model="noteDrawerVisible"
      :title="noteMode === 'create' ? '新建阅读笔记' : '编辑阅读笔记'"
      size="480px"
    >
      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="noteForm.title" />
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

<style scoped>
/* 文献详情信息面板：沿用全局配色 token，窄屏下退化为上下两行 */
.detail-info__list {
  display: grid;
  gap: 8px;
  margin: 4px 0 0;
}

.detail-info__row {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
  padding: 10px 14px;
  border-radius: var(--radius);
  background: rgba(255, 255, 255, 0.66);
  border: 1px solid rgba(105, 132, 161, 0.16);
}

.detail-info__row dt {
  margin: 0;
  color: var(--color-subtle);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.02em;
}

.detail-info__row dd {
  margin: 0;
  color: var(--color-text);
  font-size: 14px;
  line-height: 1.65;
  overflow-wrap: anywhere;
}

.detail-info__abstract {
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  white-space: pre-wrap;
}

.detail-info__abstract.is-expanded {
  display: block;
  overflow: visible;
}

.detail-info__empty {
  color: var(--color-subtle);
}

.detail-info__toggle.el-button {
  height: auto;
  min-height: 28px;
  margin-top: 2px;
  padding: 0;
  font-size: 13px;
}

@media (max-width: 860px) {
  .detail-info__row {
    grid-template-columns: 72px minmax(0, 1fr);
    gap: 8px;
    padding: 9px 12px;
  }
}

@media (max-width: 640px) {
  .detail-info__row {
    grid-template-columns: 1fr;
    gap: 2px;
  }
}
</style>
