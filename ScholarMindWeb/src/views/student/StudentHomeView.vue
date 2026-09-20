<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatLineRound,
  Collection,
  CollectionTag,
  Connection,
  Cpu,
  DataAnalysis,
  DocumentAdd,
  EditPen,
  InfoFilled,
  MagicStick,
  Notebook,
  Reading,
  Refresh,
  Search,
  Star,
  StarFilled,
  UploadFilled,
} from '@element-plus/icons-vue'

import {
  askLiteratureQuestion,
  compareLiteratures,
  compareRetrieval,
  createLiterature,
  createReadingNote,
  deleteReadingNote,
  favoriteLiterature,
  generateLiteratureSummary,
  getLiteratureView,
  hybridSearch,
  listCategories,
  listLiteratureQaRecords,
  listLiteratures,
  listReadingNotes,
  markLiteratureRead,
  naturalLanguageSearch,
  rebuildLiteratureIndex,
  saveReadingHistory,
  semanticSearch,
  submitLiterature,
  unfavoriteLiterature,
  updateLiterature,
  updateReadingNote,
  uploadLiteratureFile,
} from '@/api/literature'
import { displayCategoryName } from '@/utils/format'
import '@/styles/literature-ext.css'
import type {
  Id,
  Literature,
  LiteratureCategory,
  LiteratureCompareResult,
  LiteratureDetail,
  LiteratureIndexResult,
  LiteraturePayload,
  LiteratureQuestionResult,
  LiteratureReferenceChunk,
  LiteratureSearchResult,
  LiteratureSummaryResult,
  LiteratureStatus,
  LiteratureVisibility,
  ReadingHistory,
  ReadingNote,
  ReadingNotePayload,
  RetrievalCompareResult,
  RetrievalHit,
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
const selectedNote = ref<ReadingNote | null>(null)
const searchInsight = ref<LiteratureSearchResult | null>(null)
const indexResult = ref<LiteratureIndexResult | null>(null)
const summaryResult = ref<LiteratureSummaryResult | null>(null)
const qaRecords = ref<LiteratureQuestionResult[]>([])
const activeAnswer = ref<LiteratureQuestionResult | null>(null)
const compareResult = ref<LiteratureCompareResult | null>(null)
const loading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const noteSaving = ref(false)
const historySaving = ref(false)
const ragSearching = ref(false)
const indexBuilding = ref(false)
const summaryLoading = ref(false)
const qaLoading = ref(false)
const qaHistoryLoading = ref(false)
const compareLoading = ref(false)
const editorVisible = ref(false)
const noteDrawerVisible = ref(false)
const editorMode = ref<'create' | 'edit'>('create')
const noteMode = ref<'create' | 'edit'>('create')
const favorited = ref(false)

const router = useRouter()

// 跳转到当前文献的独立页面：详情 / 在线阅读 / 智能问答
function openLiteraturePage(target: 'detail' | 'read' | 'qa') {
  const literatureId = currentLiterature.value?.id
  if (!literatureId) {
    ElMessage.warning('请先选择一篇文献')
    return
  }
  const suffix = target === 'detail' ? '' : target === 'read' ? '/read' : '/qa'
  router.push(`/student/literature/${literatureId}${suffix}`)
}

// 跳转到我的书房：收藏、阅读历史与笔记的统一入口
function goShelf() {
  router.push('/student/shelf')
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

const readingState = reactive({
  lastPage: 1,
  progress: 18,
  readDuration: 12,
})

const ragForm = reactive({
  mode: 'natural' as 'natural' | 'semantic' | 'hybrid',
  query: '帮我找出适合构建 AI 文献检索助手的研究方法',
  limit: 5,
})

const retrievalForm = reactive({
  query: '混合检索 如何避免纯向量召回的问题',
  limit: 5,
})

const retrievalCompare = ref<RetrievalCompareResult | null>(null)

const retrievalComparing = ref(false)

const qaForm = reactive({
  question: '这篇论文的研究问题、方法和可复用模块分别是什么？',
  limit: 4,
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

const compareIds = computed(() =>
  literatures.value
    .slice(0, 3)
    .map((item) => item.id)
    .filter((id, index, ids) => ids.indexOf(id) === index),
)

const keywordTokens = computed(() =>
  (currentLiterature.value?.keywords ?? '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean),
)

const parseSignals = computed(() => {
  const item = currentLiterature.value
  return [
    {
      label: '元数据',
      value: item?.author || item?.source ? '已解析' : '待补全',
    },
    { label: '摘要', value: item?.abstractText ? '已提取' : '待解析' },
    {
      label: '向量索引',
      value: indexResult.value?.chunkCount
        ? `${indexResult.value.chunkCount} 块`
        : '可重建',
    },
    {
      label: 'RAG 引用',
      value: activeAnswer.value?.references?.length
        ? `${activeAnswer.value.references.length} 条`
        : '待提问',
    },
  ]
})

const ragModeLabel = computed(() => {
  if (ragForm.mode === 'semantic') return '语义向量'
  if (ragForm.mode === 'hybrid') return '混合检索'
  return '自然语言'
})

// 检索消融对比：三列结果分别来自纯关键词、纯向量与 RRF 融合
const sparseHits = computed(() => retrievalCompare.value?.sparse ?? [])
const denseHits = computed(() => retrievalCompare.value?.dense ?? [])
const hybridHits = computed(() => retrievalCompare.value?.hybrid ?? [])
const denseAvailable = computed(
  () => retrievalCompare.value?.denseAvailable !== false,
)

const outlineItems = computed(() => [
  {
    title: '研究问题',
    content: currentLiterature.value?.abstractText
      ? '从摘要中定位研究对象、用户需求与问题边界。'
      : '等待解析摘要后自动提炼研究问题。',
  },
  {
    title: '方法路径',
    content:
      '拆解模型、系统流程、检索策略或推荐策略，标注可以复用到系统设计的部分。',
  },
  {
    title: '证据材料',
    content: '记录数据来源、实验指标、对比方法和作者给出的局限性。',
  },
])

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

function formatScore(score?: number | null, digits = 3) {
  if (typeof score !== 'number' || Number.isNaN(score)) return '—'
  return score.toFixed(digits)
}

function scorePercent(score?: number | null) {
  if (typeof score !== 'number' || Number.isNaN(score)) return 0
  return Math.round(Math.min(1, Math.max(0, score)) * 100)
}

// 分块摘要：压缩空白并截断，避免三列对比被长正文撑开
function hitExcerpt(content?: string | null, maxLength = 96) {
  const normalized = (content ?? '').replace(/\s+/g, ' ').trim()
  if (!normalized) return '暂无分块正文'
  return normalized.length > maxLength
    ? `${normalized.slice(0, maxLength)}…`
    : normalized
}

function rankText(prefix: string, rank?: number | null) {
  return typeof rank === 'number' ? `${prefix}#${rank}` : `${prefix}#—`
}

function hitSparseBadge(hit: RetrievalHit): RetrievalBadge {
  return { label: rankText('稀疏', hit.sparseRank ?? hit.rank), tone: 'info' }
}

function hitDenseBadge(hit: RetrievalHit): RetrievalBadge {
  return { label: rankText('向量', hit.denseRank ?? hit.rank), tone: 'success' }
}

function hitFusionBadges(hit: RetrievalHit): RetrievalBadge[] {
  return [
    hitSparseBadge(hit),
    hitDenseBadge(hit),
    { label: rankText('融合', hit.rank), tone: 'primary' },
  ]
}

function hitRrfText(hit: RetrievalHit) {
  return typeof hit.rrfScore === 'number'
    ? `RRF ${hit.rrfScore.toFixed(4)}`
    : null
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

function hydrateHistory(history?: ReadingHistory | null) {
  readingState.lastPage = history?.lastPage ?? 1
  readingState.progress =
    history?.progress ??
    Math.min(92, Math.max(18, (currentLiterature.value?.readCount ?? 0) * 2))
  readingState.readDuration = history?.readDuration ?? 12
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

async function runRetrievalCompare() {
  if (!retrievalForm.query.trim()) {
    ElMessage.warning('请输入对比检索问题')
    return
  }

  retrievalComparing.value = true
  try {
    const result = await compareRetrieval({
      query: retrievalForm.query,
      categoryId: query.categoryId,
      limit: retrievalForm.limit,
    })
    retrievalCompare.value = result
    if (result.denseAvailable === false) {
      ElMessage.warning('向量检索未就绪：请先在管理端重建文献向量')
    } else {
      ElMessage.success('混合检索对比完成')
    }
  } catch (error) {
    console.error(
      'retrieval compare failed, scenario=retrieval-compare-hybrid, query=%s, categoryId=%s, limit=%s',
      retrievalForm.query,
      query.categoryId,
      retrievalForm.limit,
      error,
    )
    // 失败时不保留旧结果，也不构造任何演示命中数据
    retrievalCompare.value = null
  } finally {
    retrievalComparing.value = false
  }
}

async function fetchNotes(literatureId: Id) {
  try {
    const result = await listReadingNotes({ literatureId, page: 1, size: 20 })
    notes.value = result.items
  } catch (error) {
    console.error(
      'reading notes list failed, scenario=reading-note-list, literatureId=%s',
      literatureId,
      error,
    )
    notes.value = demoNotes.filter((note) => note.literatureId === literatureId)
  }
}

async function chooseLiterature(item: Literature) {
  selected.value = item
  indexResult.value = null
  activeAnswer.value = null
  summaryResult.value = null
  detailLoading.value = true
  try {
    const response = await getLiteratureView(item.id)
    detail.value = response
    selected.value = response.literature
    favorited.value = response.favorited
    notes.value = response.recentNotes ?? []
    hydrateHistory(response.readingHistory)
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
    hydrateHistory(null)
  } finally {
    detailLoading.value = false
    await fetchQaHistory(item.id)
  }
}

async function fetchQaHistory(literatureId: Id) {
  qaHistoryLoading.value = true
  try {
    qaRecords.value = await listLiteratureQaRecords({
      literatureId,
      page: 1,
      size: 8,
    })
  } catch (error) {
    console.error(
      'qa history load failed, scenario=literature-qa-history, literatureId=%s',
      literatureId,
      error,
    )
    qaRecords.value = [
      {
        recordId: '9200000000000000001',
        literatureId,
        question: qaForm.question,
        answer:
          '演示回答：这篇文献可以从研究问题、检索意图建模、混合召回策略和用户行为反馈四个维度拆解，并把方法部分复用到 ScholarMind 的 RAG 检索链路。',
        references: demoReferences.filter(
          (item) => item.literatureId === literatureId,
        ),
      },
    ]
  } finally {
    qaHistoryLoading.value = false
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

function openNoteCreate(aiGenerated = 0) {
  if (!currentLiterature.value) return
  noteMode.value = 'create'
  resetNoteForm()
  noteForm.literatureId = currentLiterature.value.id
  noteForm.aiGenerated = aiGenerated
  if (aiGenerated) {
    noteForm.title = 'AI 解析笔记'
    noteForm.content = `围绕《${currentLiterature.value.title}》记录研究问题、核心方法、证据材料和可复用模块。`
  }
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
  if (!currentLiterature.value) return
  const literatureId = currentLiterature.value.id
  try {
    if (favorited.value) {
      await unfavoriteLiterature(literatureId)
      favorited.value = false
      ElMessage.success('已取消收藏')
    } else {
      await favoriteLiterature(literatureId)
      favorited.value = true
      ElMessage.success('已收藏')
    }
  } catch (error) {
    console.error(
      'favorite toggle failed, scenario=literature-favorite-toggle, literatureId=%s, favorited=%s',
      literatureId,
      favorited.value,
      error,
    )
  }
}

async function syncReadingHistory() {
  if (!currentLiterature.value) return
  historySaving.value = true
  try {
    const history = await saveReadingHistory(currentLiterature.value.id, {
      lastPage: readingState.lastPage,
      progress: readingState.progress,
      readDuration: readingState.readDuration,
    })
    detail.value = detail.value
      ? { ...detail.value, readingHistory: history }
      : detail.value
    ElMessage.success('阅读进度已同步')
  } catch (error) {
    console.error(
      'reading history save failed, scenario=reading-history-upsert, literatureId=%s, lastPage=%s, progress=%s, readDuration=%s',
      currentLiterature.value.id,
      readingState.lastPage,
      readingState.progress,
      readingState.readDuration,
      error,
    )
  } finally {
    historySaving.value = false
  }
}

async function rebuildCurrentIndex() {
  if (!currentLiterature.value) return
  const literatureId = currentLiterature.value.id
  indexBuilding.value = true
  try {
    indexResult.value = await rebuildLiteratureIndex(literatureId)
    ElMessage.success(`索引已重建，生成 ${indexResult.value.chunkCount} 个分块`)
  } catch (error) {
    console.error(
      'literature index rebuild failed, scenario=literature-index-rebuild, literatureId=%s',
      literatureId,
      error,
    )
    indexResult.value = {
      literatureId,
      chunkCount: 6,
      parseStatus: 'DEMO_READY',
    }
  } finally {
    indexBuilding.value = false
  }
}

async function generateCurrentSummary() {
  if (!currentLiterature.value) return
  const literatureId = currentLiterature.value.id
  summaryLoading.value = true
  try {
    const result = await generateLiteratureSummary(literatureId)
    summaryResult.value = result
    if (detail.value?.literature) {
      detail.value = {
        ...detail.value,
        literature: {
          ...detail.value.literature,
          aiSummary: result.summary,
        },
      }
    }
    ElMessage.success('AI 摘要已生成')
  } catch (error) {
    console.error(
      'literature summary generate failed, scenario=literature-ai-summary, literatureId=%s',
      literatureId,
      error,
    )
    summaryResult.value = {
      literatureId,
      summary:
        currentLiterature.value.aiSummary ||
        '演示摘要：本文围绕学术文献检索中的意图理解与资源推荐展开，可为 ScholarMind 的语义检索、证据引用和个性化推荐模块提供方法参考。',
      status: 'DEMO_READY',
    }
  } finally {
    summaryLoading.value = false
  }
}

async function askCurrentQuestion() {
  if (!currentLiterature.value) return
  if (!qaForm.question.trim()) {
    ElMessage.warning('请输入要追问的问题')
    return
  }
  const literatureId = currentLiterature.value.id
  qaLoading.value = true
  try {
    const result = await askLiteratureQuestion(literatureId, {
      question: qaForm.question,
      limit: qaForm.limit,
    })
    activeAnswer.value = result
    qaRecords.value = [
      result,
      ...qaRecords.value.filter((item) => item.recordId !== result.recordId),
    ]
    ElMessage.success('RAG 问答已生成')
  } catch (error) {
    console.error(
      'literature question failed, scenario=literature-rag-question, literatureId=%s, question=%s, limit=%s',
      literatureId,
      qaForm.question,
      qaForm.limit,
      error,
    )
    const fallbackAnswer: LiteratureQuestionResult = {
      recordId: String(Date.now()),
      literatureId,
      question: qaForm.question,
      answer:
        '演示回答：可以先把论文拆成“问题、方法、数据、结论、局限”五类证据，再用 RAG 引用命中的原文片段，回答时显式区分作者结论和系统推断。',
      references: demoReferences.filter(
        (item) => item.literatureId === literatureId,
      ),
    }
    activeAnswer.value = fallbackAnswer
    qaRecords.value = [fallbackAnswer, ...qaRecords.value]
  } finally {
    qaLoading.value = false
  }
}

async function compareVisibleLiteratures() {
  if (compareIds.value.length < 2) {
    ElMessage.warning('至少需要两篇文献才能对比')
    return
  }
  compareLoading.value = true
  try {
    compareResult.value = await compareLiteratures({
      literatureIds: compareIds.value.slice(0, 6),
    })
    ElMessage.success('文献对比已生成')
  } catch (error) {
    console.error(
      'literature compare failed, scenario=literature-ai-compare, literatureIds=%s',
      compareIds.value.join(','),
      error,
    )
    compareResult.value = {
      literatureIds: compareIds.value,
      analysis:
        '演示对比：第一篇偏个性化推荐和用户行为建模，第二篇偏自然语言意图识别。两者可以组合成“意图解析 -> 语义召回 -> 行为排序 -> 阅读反馈”的完整 RAG 文献检索链路。',
    }
  } finally {
    compareLoading.value = false
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
          第四阶段聚焦 LLM 与 RAG：自然语言检索、向量索引、AI
          摘要、文献问答和多文献对比会围绕当前阅读材料同步运转。
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
          <div class="reader-toolbar">
            <el-button
              :icon="favorited ? StarFilled : Star"
              type="primary"
              plain
              @click="toggleFavorite"
            >
              {{ favorited ? '已收藏' : '收藏' }}
            </el-button>
            <el-button
              :icon="Reading"
              plain
              :loading="historySaving"
              @click="syncReadingHistory"
            >
              同步进度
            </el-button>
            <el-button
              :icon="Cpu"
              plain
              :loading="indexBuilding"
              @click="rebuildCurrentIndex"
            >
              重建索引
            </el-button>
            <el-button
              :icon="MagicStick"
              plain
              :loading="summaryLoading"
              @click="generateCurrentSummary"
            >
              AI 摘要
            </el-button>
            <el-button :icon="Notebook" plain @click="openNoteCreate(0)"
              >新建笔记</el-button
            >
            <el-button :icon="CollectionTag" plain @click="openNoteCreate(1)"
              >AI 解析笔记</el-button
            >
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
          </div>

          <div class="reader-toolbar" aria-label="独立页面入口">
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
            <el-button :icon="ChatLineRound" @click="openLiteraturePage('qa')"
              >智能问答</el-button
            >
            <el-button :icon="Collection" plain @click="goShelf"
              >我的书房</el-button
            >
          </div>

          <article class="reader-paper" v-loading="detailLoading">
            <div class="reader-titlebar">
              <div>
                <p class="panel__eyebrow">
                  {{ displayCategoryName(currentLiterature.categoryName) }}
                </p>
                <h2>{{ currentLiterature.title }}</h2>
              </div>
              <el-tag :type="favorited ? 'success' : 'info'" effect="light">
                {{ favorited ? '已收藏' : '未收藏' }}
              </el-tag>
            </div>
            <div class="reader-meta">
              <span>{{ currentLiterature.author || '未知作者' }}</span>
              <span>{{ currentLiterature.source || '未填写来源' }}</span>
              <span>{{ currentLiterature.publishYear || '年份未知' }}</span>
              <span>{{ currentLiterature.fileType || '未绑定原文' }}</span>
            </div>

            <section class="parse-status-grid">
              <div v-for="item in parseSignals" :key="item.label">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </section>

            <section class="rag-command-panel">
              <div class="panel-heading">
                <div>
                  <p class="panel__eyebrow">Ask With Evidence</p>
                  <h3>论文问答</h3>
                </div>
                <el-button
                  :icon="ChatLineRound"
                  type="primary"
                  :loading="qaLoading"
                  @click="askCurrentQuestion"
                >
                  生成回答
                </el-button>
              </div>
              <el-input
                v-model="qaForm.question"
                type="textarea"
                :rows="3"
                placeholder="围绕当前论文提出一个具体问题"
              />
              <div class="rag-command-panel__footer">
                <span>引用上限</span>
                <el-input-number
                  v-model="qaForm.limit"
                  :min="1"
                  :max="8"
                  controls-position="right"
                />
                <el-button
                  :icon="Refresh"
                  plain
                  :loading="qaHistoryLoading"
                  @click="fetchQaHistory(currentLiterature.id)"
                >
                  刷新历史
                </el-button>
              </div>
            </section>

            <section v-if="activeAnswer" class="answer-panel">
              <div class="answer-panel__bubble">
                <span>{{ activeAnswer.question }}</span>
                <p>{{ activeAnswer.answer }}</p>
              </div>
              <div class="reference-list" v-if="activeAnswer.references.length">
                <article
                  v-for="reference in activeAnswer.references"
                  :key="reference.id"
                >
                  <div>
                    <strong>#{{ reference.chunkIndex ?? reference.id }}</strong>
                    <span>{{ Math.round(reference.score * 100) }}%</span>
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

            <section class="reading-progress-panel">
              <div class="panel-heading">
                <div>
                  <p class="panel__eyebrow">Reading History</p>
                  <h3>阅读进度</h3>
                </div>
                <span>{{ readingState.progress }}%</span>
              </div>
              <el-slider v-model="readingState.progress" :min="0" :max="100" />
              <div class="history-inputs">
                <el-input-number
                  v-model="readingState.lastPage"
                  :min="0"
                  controls-position="right"
                />
                <span>最后页码</span>
                <el-input-number
                  v-model="readingState.readDuration"
                  :min="0"
                  controls-position="right"
                />
                <span>阅读分钟</span>
              </div>
            </section>

            <section class="reader-section">
              <h3>AI 摘要</h3>
              <p>
                {{
                  summaryResult?.summary ||
                  currentLiterature.aiSummary ||
                  currentLiterature.abstractText ||
                  '暂无 AI 摘要。可先阅读摘要并创建 AI 解析笔记，后续接入解析任务后可自动沉淀研究问题、方法和结论。'
                }}
              </p>
            </section>

            <section class="reader-section">
              <h3>解析摘要</h3>
              <p>{{ currentLiterature.abstractText || '暂无摘要。' }}</p>
            </section>

            <section class="keyword-ribbon" v-if="keywordTokens.length">
              <span v-for="token in keywordTokens" :key="token">{{
                token
              }}</span>
            </section>

            <section class="reading-grid">
              <div v-for="item in outlineItems" :key="item.title">
                <h3>{{ item.title }}</h3>
                <p>{{ item.content }}</p>
              </div>
            </section>

            <section class="qa-history-panel" v-if="qaRecords.length">
              <div class="panel-heading">
                <div>
                  <p class="panel__eyebrow">QA Memory</p>
                  <h3>问答历史</h3>
                </div>
                <el-button
                  :icon="DataAnalysis"
                  plain
                  :loading="compareLoading"
                  @click="compareVisibleLiteratures"
                >
                  对比当前结果
                </el-button>
              </div>
              <div class="qa-record-list">
                <button
                  v-for="record in qaRecords"
                  :key="record.recordId ?? record.question"
                  type="button"
                  @click="activeAnswer = record"
                >
                  <span>{{ record.question }}</span>
                  <small>{{ record.references.length }} 条引用</small>
                </button>
              </div>
            </section>

            <section v-if="compareResult" class="compare-panel">
              <div>
                <p class="panel__eyebrow">Literature Compare</p>
                <h3>多文献对比</h3>
              </div>
              <p>{{ compareResult.analysis }}</p>
              <div class="keyword-ribbon">
                <span v-for="id in compareResult.literatureIds" :key="id"
                  >#{{ id }}</span
                >
              </div>
            </section>

            <section class="note-board">
              <div class="panel-heading">
                <div>
                  <p class="panel__eyebrow">Reading Notes</p>
                  <h3>阅读笔记</h3>
                </div>
                <el-button :icon="Notebook" plain @click="openNoteCreate(0)"
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

    <section class="glass-panel retrieval-compare" aria-label="混合检索对比">
      <div class="panel-heading">
        <div>
          <p class="panel__eyebrow">Hybrid Retrieval Ablation</p>
          <h3>混合检索对比</h3>
        </div>
        <el-tag
          v-if="retrievalCompare"
          size="small"
          effect="plain"
          :type="denseAvailable ? 'success' : 'warning'"
        >
          {{ denseAvailable ? '向量检索可用' : '向量检索未就绪' }}
        </el-tag>
      </div>

      <div class="retrieval-compare__form">
        <el-input
          v-model="retrievalForm.query"
          :prefix-icon="Search"
          placeholder="例如：混合检索 如何避免纯向量召回的问题"
          clearable
          @keyup.enter="runRetrievalCompare"
        />
        <el-input-number
          v-model="retrievalForm.limit"
          :min="1"
          :max="10"
          controls-position="right"
        />
        <el-button
          :icon="DataAnalysis"
          type="primary"
          :loading="retrievalComparing"
          @click="runRetrievalCompare"
        >
          执行对比
        </el-button>
      </div>
      <p class="retrieval-compare__hint">
        同一个问题分别走 BM25 关键词召回、Milvus 向量召回与 RRF 融合排序，用来观察三种策略在同一批分块上的名次差异。
      </p>

      <el-alert
        v-if="retrievalCompare && !denseAvailable"
        type="warning"
        :closable="false"
        show-icon
        title="向量检索未就绪：请先在管理端重建文献向量"
        description="当前仅关键词召回有效，向量列与融合列中的向量名次为空，页面仍会展示后端返回的内容。"
      />

      <div v-if="retrievalCompare" class="retrieval-columns">
        <article class="retrieval-column">
          <header class="retrieval-column__head">
            <div>
              <p class="panel__eyebrow">BM25 Sparse</p>
              <h4>① 关键词召回（BM25 稀疏）</h4>
            </div>
            <span>{{ sparseHits.length }} 条</span>
          </header>
          <div v-if="sparseHits.length" class="retrieval-hit-list">
            <div
              v-for="hit in sparseHits"
              :key="`sparse-${hit.chunkId}`"
              class="retrieval-hit"
            >
              <div class="retrieval-hit__head">
                <strong>{{ hit.literatureTitle || '未命名文献' }}</strong>
                <el-tag
                  size="small"
                  effect="plain"
                  :type="hitSparseBadge(hit).tone"
                >
                  {{ hitSparseBadge(hit).label }}
                </el-tag>
              </div>
              <div class="retrieval-hit__meta">
                <span>#{{ hit.chunkIndex ?? '—' }} 分块</span>
                <span>BM25 得分 {{ formatScore(hit.score) }}</span>
              </div>
              <p class="retrieval-hit__excerpt">
                {{ hitExcerpt(hit.content) }}
              </p>
            </div>
          </div>
          <el-empty
            v-else
            :image-size="72"
            description="关键词召回无命中，可换用更短的术语重试"
          />
        </article>

        <article class="retrieval-column">
          <header class="retrieval-column__head">
            <div>
              <p class="panel__eyebrow">Milvus Dense</p>
              <h4>② 向量召回（Milvus 稠密）</h4>
            </div>
            <span>{{ denseHits.length }} 条</span>
          </header>
          <div v-if="denseHits.length" class="retrieval-hit-list">
            <div
              v-for="hit in denseHits"
              :key="`dense-${hit.chunkId}`"
              class="retrieval-hit"
            >
              <div class="retrieval-hit__head">
                <strong>{{ hit.literatureTitle || '未命名文献' }}</strong>
                <el-tag
                  size="small"
                  effect="plain"
                  :type="hitDenseBadge(hit).tone"
                >
                  {{ hitDenseBadge(hit).label }}
                </el-tag>
              </div>
              <div class="retrieval-hit__meta">
                <span>#{{ hit.chunkIndex ?? '—' }} 分块</span>
                <span>相似度 {{ formatScore(hit.score) }}</span>
              </div>
              <p class="retrieval-hit__excerpt">
                {{ hitExcerpt(hit.content) }}
              </p>
            </div>
          </div>
          <el-empty
            v-else
            :image-size="72"
            :description="
              denseAvailable
                ? '向量召回无命中'
                : '向量检索未就绪，未返回稠密召回结果'
            "
          />
        </article>

        <article class="retrieval-column retrieval-column--fusion">
          <header class="retrieval-column__head">
            <div>
              <p class="panel__eyebrow">RRF Fusion</p>
              <h4>③ RRF 融合（最终排序）</h4>
            </div>
            <span>{{ hybridHits.length }} 条</span>
          </header>
          <div v-if="hybridHits.length" class="retrieval-hit-list">
            <div
              v-for="hit in hybridHits"
              :key="`hybrid-${hit.chunkId}`"
              class="retrieval-hit"
            >
              <div class="retrieval-hit__head">
                <strong>{{ hit.literatureTitle || '未命名文献' }}</strong>
                <el-tag size="small" effect="plain">{{
                  rankText('融合', hit.rank)
                }}</el-tag>
              </div>
              <div class="retrieval-hit__meta">
                <span>#{{ hit.chunkIndex ?? '—' }} 分块</span>
                <span>融合得分 {{ formatScore(hit.score) }}</span>
              </div>
              <div class="retrieval-hit__ranks">
                <el-tag
                  v-for="badge in hitFusionBadges(hit)"
                  :key="badge.label"
                  size="small"
                  effect="plain"
                  :type="badge.tone"
                >
                  {{ badge.label }}
                </el-tag>
                <span v-if="hitRrfText(hit)" class="retrieval-hit__rrf">{{
                  hitRrfText(hit)
                }}</span>
              </div>
              <p class="retrieval-hit__excerpt">
                {{ hitExcerpt(hit.content) }}
              </p>
            </div>
          </div>
          <el-empty
            v-else
            :image-size="72"
            description="融合结果为空，可先确认关键词与向量两路是否有召回"
          />
        </article>
      </div>

      <el-empty
        v-else
        description="输入问题后点击「执行对比」，查看关键词、向量与 RRF 融合的差异"
      />
    </section>

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
