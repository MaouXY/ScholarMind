<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  ChatLineRound,
  Collection,
  Cpu,
  Document,
  Download,
  MagicStick,
  Reading,
  Refresh,
  Star,
  StarFilled,
} from '@element-plus/icons-vue'

import {
  favoriteLiterature,
  generateLiteratureSummary,
  getLiteratureDownload,
  getLiteratureView,
  rebuildLiteratureIndex,
  unfavoriteLiterature,
} from '@/api/literature'
import {
  deleteCitation,
  listCitations,
  listLiteratureCitations,
  createCitation,
} from '@/api/citation'
import {
  downloadCitationReferences,
  openLiteratureFile,
} from '@/utils/download'
import { displayCategoryName } from '@/utils/format'
import type {
  CitationRecord,
  CitationStyle,
  CitationStyleItem,
} from '@/types/citation'
import type {
  Id,
  Literature,
  LiteratureDetail,
  LiteratureIndexResult,
  LiteratureSummaryResult,
  LiteratureStatus,
  LiteratureVisibility,
} from '@/types/literature'
import '@/styles/literature-ext.css'

const route = useRoute()
const router = useRouter()

const literatureId = computed<Id>(() => {
  const raw = route.params.id
  return Array.isArray(raw) ? String(raw[0] ?? '') : String(raw ?? '')
})

const literature = ref<Literature | null>(null)
const detail = ref<LiteratureDetail | null>(null)
const favorited = ref(false)
const summaryResult = ref<LiteratureSummaryResult | null>(null)
const indexResult = ref<LiteratureIndexResult | null>(null)
const loadFailed = ref(false)

const loading = ref(false)
const favoriteLoading = ref(false)
const summaryLoading = ref(false)
const indexBuilding = ref(false)

// 引用与下载：三种格式由后端即时生成，当前选中格式决定导出内容
const citationItems = ref<CitationStyleItem[]>([])
const citationRecords = ref<CitationRecord[]>([])
const citationTotal = ref(0)
const activeCitationStyle = ref<CitationStyle | null>(null)
const citationLoading = ref(false)
const citationListLoading = ref(false)
const citationSaving = ref(false)
const citationExporting = ref(false)
const citationRemovingId = ref<Id | null>(null)
const downloadLoading = ref(false)
const downloadCount = ref<number | null>(null)
const citationFailed = ref(false)

const keywordTokens = computed(() =>
  (literature.value?.keywords ?? '')
    .split(/[,，;；、]/)
    .map((item) => item.trim())
    .filter(Boolean),
)

const summaryText = computed(
  () =>
    summaryResult.value?.summary ||
    literature.value?.aiSummary ||
    '暂无 AI 摘要，点击「生成摘要」让模型基于摘要与关键词生成结构化概览。',
)

const summaryStatus = computed(
  () =>
    summaryResult.value?.status ||
    (literature.value?.aiSummary ? '已生成' : '待生成'),
)

const parseStatus = computed(
  () =>
    indexResult.value?.parseStatus ||
    (literature.value ? '未记录（可重建）' : '未建立索引'),
)

const chunkCount = computed(() => indexResult.value?.chunkCount ?? 0)

const abstractParagraphs = computed(() =>
  (literature.value?.abstractText ?? '')
    .split(/\n+/)
    .map((item) => item.trim())
    .filter(Boolean),
)

const fileSizeText = computed(() => {
  const size = literature.value?.fileSize
  if (typeof size !== 'number' || size <= 0) return '未记录'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(2)} MB`
})

const activeCitation = computed(
  () =>
    citationItems.value.find(
      (item) => item.style === activeCitationStyle.value,
    ) ?? null,
)

const citationStyleCount = computed(() => citationItems.value.length)

/** 后端已收藏的引用格式集合（本次查询的文献内去重） */
const collectedCitationStyles = computed(() => {
  const styles = citationRecords.value.map((record) => record.style)
  return new Set<CitationStyle>(styles)
})

const collectedCitationCount = computed(() => citationRecords.value.length)

const downloadCountText = computed(() => {
  if (downloadCount.value !== null) return downloadCount.value
  return literature.value?.downloadCount ?? 0
})

const factRows = computed(() => [
  {
    label: '学科分类',
    value: displayCategoryName(literature.value?.categoryName ?? null),
  },
  { label: '语言', value: literature.value?.language || '未填写' },
  {
    label: '出版年份',
    value: String(literature.value?.publishYear || '未填写'),
  },
  { label: '文件类型', value: literature.value?.fileType || '未绑定' },
  { label: '全文大小', value: fileSizeText.value },
  {
    label: '上传时间',
    value:
      literature.value?.createdAt?.slice(0, 19).replace('T', ' ') || '未记录',
  },
  {
    label: '更新时间',
    value:
      literature.value?.updatedAt?.slice(0, 19).replace('T', ' ') || '未记录',
  },
  { label: '文献编号', value: literature.value?.id ?? literatureId.value },
])

function statusLabel(status?: LiteratureStatus) {
  const labels: Record<LiteratureStatus, string> = {
    DRAFT: '草稿',
    PENDING_REVIEW: '审核中',
    PUBLISHED: '已发布',
    REJECTED: '已退回',
  }
  return status ? labels[status] : '未知状态'
}

function statusTagType(status?: LiteratureStatus) {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'REJECTED') return 'danger'
  if (status === 'PENDING_REVIEW') return 'warning'
  return 'info'
}

function visibilityLabel(visibility?: LiteratureVisibility) {
  if (visibility === 'PUBLIC') return '公开'
  if (visibility === 'PRIVATE') return '私有'
  return '未知'
}

/** 业务错误码 14107：该文献未绑定原文文件 */
function isMissingFileError(error: unknown) {
  const target = error as { code?: number; message?: string } | null
  return target?.code === 14107 || (target?.message ?? '').includes('14107')
}

async function loadDetail() {
  const id = literatureId.value
  if (!id) {
    loadFailed.value = true
    ElMessage.warning('缺少文献 ID，无法加载详情')
    return
  }

  loading.value = true
  loadFailed.value = false
  try {
    const response = await getLiteratureView(id)
    detail.value = response
    literature.value = response.literature
    favorited.value = response.favorited
  } catch (error) {
    console.error(
      'literature detail view failed, scenario=literature-detail-view, literatureId=%s',
      id,
      error,
    )
    detail.value = null
    literature.value = null
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

async function toggleFavorite() {
  const id = literature.value?.id ?? literatureId.value
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

async function regenerateSummary() {
  const id = literature.value?.id ?? literatureId.value
  if (!id) return

  summaryLoading.value = true
  try {
    const result = await generateLiteratureSummary(id)
    summaryResult.value = result
    if (literature.value) {
      literature.value = { ...literature.value, aiSummary: result.summary }
    }
    ElMessage.success('AI 摘要已更新')
  } catch (error) {
    console.error(
      'literature summary generate failed, scenario=literature-ai-summary, literatureId=%s',
      id,
      error,
    )
  } finally {
    summaryLoading.value = false
  }
}

async function rebuildIndex() {
  const id = literature.value?.id ?? literatureId.value
  if (!id) return

  indexBuilding.value = true
  try {
    const result = await rebuildLiteratureIndex(id)
    indexResult.value = result
    ElMessage.success(`索引已重建，生成 ${result.chunkCount} 个分块`)
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

/** 加载三种引用格式，默认选中第一种 */
async function loadCitations() {
  const id = literature.value?.id ?? literatureId.value
  if (!id) return

  citationLoading.value = true
  citationFailed.value = false
  try {
    const result = await listLiteratureCitations(id)
    const items = Array.isArray(result) ? result : []
    citationItems.value = items
    if (!items.some((item) => item.style === activeCitationStyle.value)) {
      activeCitationStyle.value = items[0]?.style ?? null
    }
  } catch (error) {
    console.error(
      'literature citation styles failed, scenario=literature-citation-styles, literatureId=%s',
      id,
      error,
    )
    citationItems.value = []
    activeCitationStyle.value = null
    citationFailed.value = true
  } finally {
    citationLoading.value = false
  }
}

/** 加载该文献已收藏的引用（第一页 5 条） */
async function loadCitationRecords() {
  const id = literature.value?.id ?? literatureId.value
  if (!id) return

  citationListLoading.value = true
  try {
    const result = await listCitations({ page: 1, size: 5, literatureId: id })
    citationRecords.value = result.items
    citationTotal.value = result.total
  } catch (error) {
    console.error(
      'citation list failed, scenario=citation-list, literatureId=%s',
      id,
      error,
    )
    citationRecords.value = []
    citationTotal.value = 0
  } finally {
    citationListLoading.value = false
  }
}

/** 优先使用剪贴板 API，http 源等不可用场景回落到隐藏 textarea + execCommand */
async function copyText(text: string, label = '引用') {
  const content = text.trim()
  if (!content) {
    ElMessage.warning('暂无可复制的内容')
    return
  }

  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(content)
      ElMessage.success(`${label}已复制到剪贴板`)
      return
    } catch (error) {
      console.error(
        'clipboard write failed, scenario=citation-clipboard-write, label=%s',
        label,
        error,
      )
    }
  }

  const textarea = document.createElement('textarea')
  textarea.value = content
  textarea.setAttribute('readonly', 'readonly')
  textarea.style.position = 'fixed'
  textarea.style.top = '-1000px'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  try {
    textarea.select()
    textarea.setSelectionRange(0, content.length)
    const copied = document.execCommand('copy')
    if (copied) {
      ElMessage.success(`${label}已复制到剪贴板`)
    } else {
      ElMessage.error('复制失败，请手动选中引用文本后复制')
    }
  } catch (error) {
    console.error(
      'clipboard fallback failed, scenario=citation-clipboard-fallback, label=%s',
      label,
      error,
    )
    ElMessage.error('当前浏览器不支持自动复制，请手动选中引用文本后复制')
  } finally {
    document.body.removeChild(textarea)
  }
}

function selectCitationStyle(style: CitationStyle) {
  activeCitationStyle.value = style
}

/** 收藏当前格式的引用，成功后刷新已收藏列表 */
async function collectCitation(item: CitationStyleItem) {
  const id = literature.value?.id ?? literatureId.value
  if (!id) return

  citationSaving.value = true
  try {
    await createCitation({ literatureId: id, style: item.style })
    activeCitationStyle.value = item.style
    ElMessage.success('已收藏该格式引用')
    await loadCitationRecords()
  } catch (error) {
    console.error(
      'citation create failed, scenario=citation-create, literatureId=%s, style=%s',
      id,
      item.style,
      error,
    )
  } finally {
    citationSaving.value = false
  }
}

async function removeCitation(record: CitationRecord) {
  citationRemovingId.value = record.id
  try {
    await deleteCitation(record.id)
    ElMessage.success('已取消收藏该引用')
    await loadCitationRecords()
  } catch (error) {
    console.error(
      'citation delete failed, scenario=citation-delete, citationId=%s, literatureId=%s',
      record.id,
      record.literatureId,
      error,
    )
  } finally {
    citationRemovingId.value = null
  }
}

/** 按当前选中格式导出参考文献附件（blob + Authorization） */
async function exportReferences() {
  const item = activeCitation.value
  if (!item) {
    ElMessage.warning('请先选择一种引用格式')
    return
  }

  citationExporting.value = true
  try {
    const fileName = await downloadCitationReferences(
      item.style,
      item.styleLabel,
    )
    ElMessage.success(`已导出参考文献：${fileName}`)
  } catch (error) {
    console.error(
      'citation export failed, scenario=citation-export, literatureId=%s, style=%s',
      literatureId.value,
      item.style,
      error,
    )
    ElMessage.error('导出参考文献失败，请稍后重试')
  } finally {
    citationExporting.value = false
  }
}

/** 换取预签名地址后打开原文，并回显最新下载次数 */
async function downloadOriginal() {
  const id = literature.value?.id ?? literatureId.value
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

function openReader() {
  router.push(`/student/literature/${literatureId.value}/read`)
}

function openQa() {
  router.push(`/student/literature/${literatureId.value}/qa`)
}

function goLibrary() {
  router.push('/student/library')
}

function goShelf() {
  router.push('/student/shelf')
}

onMounted(async () => {
  await loadDetail()
  if (!loadFailed.value) {
    await Promise.all([loadCitations(), loadCitationRecords()])
  }
})
</script>

<template>
  <section class="library-page">
    <div class="spatial-hero compact-hero">
      <div>
        <p class="panel__eyebrow">Literature Detail</p>
        <h2>一页读懂元数据、AI 摘要与索引状态</h2>
        <p>
          文献详情页集中呈现题录信息、AI
          摘要、解析与向量索引状态，并作为进入在线阅读与智能问答的统一入口。
        </p>
      </div>
      <div class="hero-orbit" aria-hidden="true">
        <span>Detail</span>
        <span>Read</span>
        <span>Ask</span>
      </div>
    </div>

    <section v-if="loading" class="glass-panel" aria-label="文献详情加载中">
      <el-skeleton :rows="1" animated />
      <div class="detail-facts">
        <div v-for="index in 4" :key="index">
          <el-skeleton :rows="1" animated />
        </div>
      </div>
      <el-skeleton :rows="6" animated />
    </section>

    <section
      v-else-if="loadFailed || !literature"
      class="glass-panel"
      aria-label="文献详情异常"
    >
      <el-empty description="文献加载失败或不存在，请返回文献库重新选择">
        <div class="detail-actions">
          <el-button :icon="Refresh" type="primary" @click="loadDetail"
            >重新加载</el-button
          >
          <el-button :icon="ArrowLeft" plain @click="goLibrary"
            >返回文献库</el-button
          >
        </div>
      </el-empty>
    </section>

    <template v-else>
      <section class="glass-panel" aria-label="文献信息">
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
        </div>

        <div class="reader-meta">
          <el-tag
            size="small"
            effect="plain"
            :type="statusTagType(literature.status)"
          >
            {{ statusLabel(literature.status) }}
          </el-tag>
          <el-tag size="small" effect="plain">{{
            visibilityLabel(literature.visibility)
          }}</el-tag>
          <el-tag size="small" effect="plain" type="info">{{
            summaryStatus
          }}</el-tag>
        </div>

        <section class="keyword-ribbon" v-if="keywordTokens.length">
          <span v-for="token in keywordTokens" :key="token">{{ token }}</span>
        </section>

        <div class="hero-stats">
          <span>
            <strong>{{ literature.readCount ?? 0 }}</strong>
            阅读次数
          </span>
          <span>
            <strong>{{ downloadCountText }}</strong>
            下载次数
          </span>
          <span>
            <strong>{{ chunkCount }}</strong>
            索引分块
          </span>
          <span>
            <strong>{{ collectedCitationCount }}</strong>
            已存引用
          </span>
        </div>

        <div class="detail-actions">
          <el-button :icon="Document" type="primary" @click="openReader"
            >在线阅读</el-button
          >
          <el-button :icon="ChatLineRound" @click="openQa">智能问答</el-button>
          <el-button
            :icon="Download"
            type="primary"
            plain
            :loading="downloadLoading"
            @click="downloadOriginal"
          >
            下载原文
          </el-button>
          <el-button
            :icon="favorited ? StarFilled : Star"
            plain
            :loading="favoriteLoading"
            @click="toggleFavorite"
          >
            {{ favorited ? '取消收藏' : '收藏文献' }}
          </el-button>
          <el-button :icon="Collection" plain @click="goShelf"
            >我的书房</el-button
          >
          <el-button
            :icon="MagicStick"
            plain
            :loading="summaryLoading"
            @click="regenerateSummary"
          >
            {{ literature.aiSummary ? '重新生成摘要' : '生成摘要' }}
          </el-button>
          <el-button
            :icon="Cpu"
            plain
            :loading="indexBuilding"
            @click="rebuildIndex"
          >
            重建索引
          </el-button>
          <el-button :icon="ArrowLeft" plain @click="goLibrary"
            >返回文献库</el-button
          >
        </div>
      </section>

      <section class="detail-facts" aria-label="题录信息">
        <div v-for="item in factRows" :key="item.label">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </section>

      <section class="glass-panel" aria-label="引用与下载">
        <div class="panel-heading">
          <div>
            <p class="panel__eyebrow">Citation &amp; Download</p>
            <h3>引用与下载</h3>
          </div>
          <div class="detail-actions">
            <el-tag effect="plain">{{ citationStyleCount }} 种格式</el-tag>
            <el-tag effect="plain" type="success"
              >已收藏 {{ collectedCitationCount }} 条</el-tag
            >
            <el-button
              :icon="Refresh"
              plain
              :loading="citationLoading || citationListLoading"
              @click="loadCitations"
            >
              刷新引用
            </el-button>
            <el-button :icon="Reading" plain @click="goShelf">去书房</el-button>
          </div>
        </div>

        <section v-if="citationLoading" aria-label="引用格式加载中">
          <el-skeleton :rows="5" animated />
        </section>

        <el-empty
          v-else-if="citationFailed || !citationStyleCount"
          description="引用格式生成失败或暂不可用，可稍后点击「刷新引用」重试"
        />

        <template v-else>
          <div class="citation-grid">
            <article
              v-for="item in citationItems"
              :key="item.style"
              class="citation-card"
              :class="{ 'is-active': item.style === activeCitationStyle }"
              @click="selectCitationStyle(item.style)"
            >
              <header class="citation-card__head">
                <strong>{{ item.styleLabel || item.style }}</strong>
                <el-tag
                  v-if="collectedCitationStyles.has(item.style)"
                  size="small"
                  type="success"
                >
                  已收藏
                </el-tag>
                <el-tag v-else size="small" effect="plain">{{
                  item.style
                }}</el-tag>
              </header>
              <p class="citation-card__text">
                {{ item.text || '该格式暂未生成引用文本' }}
              </p>
              <div class="citation-card__actions">
                <el-button
                  text
                  type="primary"
                  @click="copyText(item.text, '引用')"
                  >复制</el-button
                >
                <el-button
                  text
                  :type="
                    collectedCitationStyles.has(item.style)
                      ? 'success'
                      : 'primary'
                  "
                  :loading="citationSaving"
                  :disabled="collectedCitationStyles.has(item.style)"
                  @click="collectCitation(item)"
                >
                  {{
                    collectedCitationStyles.has(item.style)
                      ? '已收藏'
                      : '收藏引用'
                  }}
                </el-button>
              </div>
              <el-button
                :icon="Download"
                plain
                class="wide-action"
                :loading="citationExporting"
                @click="exportReferences"
              >
                导出该格式参考文献
              </el-button>
            </article>
          </div>

          <div class="detail-actions citation-export-bar">
            <el-button
              :icon="Download"
              type="primary"
              plain
              :loading="citationExporting"
              @click="exportReferences"
            >
              导出参考文献（{{ activeCitation?.styleLabel || '未选择格式' }}）
            </el-button>
          </div>

          <section class="citation-records" aria-label="已收藏引用">
            <div class="panel-heading">
              <div>
                <p class="panel__eyebrow">Collected Citations</p>
                <h3>本篇已收藏引用</h3>
              </div>
              <div class="detail-actions">
                <el-tag effect="plain">{{ citationTotal }} 条</el-tag>
                <el-button :icon="Reading" plain @click="goShelf"
                  >去书房</el-button
                >
              </div>
            </div>

            <div v-loading="citationListLoading">
              <div class="citation-list" v-if="citationRecords.length">
                <article
                  v-for="record in citationRecords"
                  :key="record.id"
                  class="citation-record"
                >
                  <div>
                    <el-tag size="small" effect="plain">
                      {{ record.styleLabel || record.style }}
                    </el-tag>
                    <h4>{{ record.citationText }}</h4>
                    <p v-if="record.note">备注：{{ record.note }}</p>
                  </div>
                  <div class="note-actions">
                    <el-button
                      text
                      @click="copyText(record.citationText, '引用')"
                      >复制</el-button
                    >
                    <el-button
                      text
                      type="danger"
                      :loading="citationRemovingId === record.id"
                      @click="removeCitation(record)"
                    >
                      取消收藏
                    </el-button>
                  </div>
                </article>
              </div>
              <el-empty
                v-else
                description="本篇文献暂未收藏引用，点击上方「收藏引用」即可沉淀题录"
              />
            </div>
          </section>
        </template>
      </section>

      <div class="paper-stack">
        <section class="glass-panel" aria-label="AI 摘要">
          <div class="panel-heading">
            <div>
              <p class="panel__eyebrow">AI Summary</p>
              <h3>AI 摘要</h3>
            </div>
            <div class="detail-actions">
              <el-tag effect="plain">summaryStatus: {{ summaryStatus }}</el-tag>
              <el-button
                :icon="Refresh"
                plain
                :loading="summaryLoading"
                @click="regenerateSummary"
              >
                {{ literature.aiSummary ? '重新生成摘要' : '生成摘要' }}
              </el-button>
            </div>
          </div>
          <p>{{ summaryText }}</p>
        </section>

        <section class="glass-panel" aria-label="解析摘要">
          <div class="panel-heading">
            <div>
              <p class="panel__eyebrow">Abstract</p>
              <h3>摘要原文</h3>
            </div>
            <span>{{ abstractParagraphs.length }} 段</span>
          </div>
          <template v-if="abstractParagraphs.length">
            <p v-for="(paragraph, index) in abstractParagraphs" :key="index">
              {{ paragraph }}
            </p>
          </template>
          <el-empty v-else description="暂无摘要，可在文献库编辑补充" />
        </section>

        <section class="glass-panel" aria-label="索引与解析状态">
          <div class="panel-heading">
            <div>
              <p class="panel__eyebrow">Index Status</p>
              <h3>索引 / 解析状态</h3>
            </div>
            <el-button
              :icon="Cpu"
              plain
              :loading="indexBuilding"
              @click="rebuildIndex"
            >
              重建索引
            </el-button>
          </div>
          <section class="parse-status-grid">
            <div>
              <span>解析状态</span>
              <strong>{{ parseStatus }}</strong>
            </div>
            <div>
              <span>分块数量</span>
              <strong>{{ indexResult ? chunkCount : '—' }}</strong>
            </div>
            <div>
              <span>可见性</span>
              <strong>{{ visibilityLabel(literature.visibility) }}</strong>
            </div>
            <div>
              <span>审核状态</span>
              <strong>{{ statusLabel(literature.status) }}</strong>
            </div>
          </section>
          <div class="progress-line">
            <span
              :style="{
                width: `${literature.visibility === 'PUBLIC' ? 100 : 56}%`,
              }"
            />
          </div>
          <section class="reading-grid">
            <div>
              <h3>文件信息</h3>
              <div class="file-info">
                <span>类型：{{ literature.fileType || '未绑定原文' }}</span>
                <span>大小：{{ fileSizeText }}</span>
                <span>文件 ID：{{ literature.fileId ?? '—' }}</span>
              </div>
              <a
                v-if="literature.fileUrl"
                :href="literature.fileUrl"
                target="_blank"
                rel="noreferrer"
              >
                <el-button :icon="Document" plain>打开原文链接</el-button>
              </a>
              <span v-else>暂无可用文件链接</span>
            </div>
            <div>
              <h3>阅读记录</h3>
              <ul>
                <li>
                  最后页码：{{ detail?.readingHistory?.lastPage ?? '暂无记录' }}
                </li>
                <li>阅读进度：{{ detail?.readingHistory?.progress ?? 0 }}%</li>
                <li>
                  阅读时长：{{ detail?.readingHistory?.readDuration ?? 0 }} 秒
                </li>
                <li>最近笔记：{{ detail?.recentNotes?.length ?? 0 }} 条</li>
              </ul>
            </div>
          </section>
        </section>

        <section class="glass-panel" aria-label="最近笔记">
          <div class="panel-heading">
            <div>
              <p class="panel__eyebrow">Recent Notes</p>
              <h3>最近阅读笔记</h3>
            </div>
            <el-button :icon="Reading" plain @click="openReader"
              >去阅读页管理</el-button
            >
          </div>
          <div class="note-list" v-if="detail?.recentNotes?.length">
            <article
              v-for="note in detail.recentNotes"
              :key="note.id"
              class="note-card"
            >
              <div>
                <el-tag v-if="note.aiGenerated" size="small" type="success"
                  >AI</el-tag
                >
                <h4>{{ note.title }}</h4>
                <p>{{ note.content }}</p>
              </div>
              <div class="note-actions">
                <el-tag size="small" effect="plain">#{{ note.id }}</el-tag>
              </div>
            </article>
          </div>
          <el-empty v-else description="暂无笔记，可到在线阅读页创建批注" />
        </section>
      </div>
    </template>
  </section>
</template>
