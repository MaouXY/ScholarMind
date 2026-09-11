<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  ChatLineRound,
  Cpu,
  Document,
  MagicStick,
  Reading,
  Refresh,
  Star,
  StarFilled,
} from '@element-plus/icons-vue'

import {
  favoriteLiterature,
  generateLiteratureSummary,
  getLiteratureView,
  rebuildLiteratureIndex,
  unfavoriteLiterature,
} from '@/api/literature'
import { displayCategoryName } from '@/utils/format'
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

// 关键词支持中英文逗号、分号、顿号分隔
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
  () => summaryResult.value?.status || (literature.value?.aiSummary ? '已生成' : '待生成'),
)

const parseStatus = computed(
  () => indexResult.value?.parseStatus || (literature.value ? '未记录（可重建）' : '未建立索引'),
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

const factRows = computed(() => [
  { label: '学科分类', value: displayCategoryName(literature.value?.categoryName ?? null) },
  { label: '语言', value: literature.value?.language || '未填写' },
  { label: '出版年份', value: String(literature.value?.publishYear || '未填写') },
  { label: '文件类型', value: literature.value?.fileType || '未绑定' },
  { label: '全文大小', value: fileSizeText.value },
  { label: '上传时间', value: literature.value?.createdAt?.slice(0, 19).replace('T', ' ') || '未记录' },
  { label: '更新时间', value: literature.value?.updatedAt?.slice(0, 19).replace('T', ' ') || '未记录' },
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

function openReader() {
  router.push(`/student/literature/${literatureId.value}/read`)
}

function openQa() {
  router.push(`/student/literature/${literatureId.value}/qa`)
}

function goLibrary() {
  router.push('/student/library')
}

onMounted(async () => {
  await loadDetail()
})
</script>

<template>
  <section class="library-page">
    <div class="spatial-hero compact-hero">
      <div>
        <p class="panel__eyebrow">Literature Detail</p>
        <h2>一页读懂元数据、AI 摘要与索引状态</h2>
        <p>
          文献详情页集中呈现题录信息、AI 摘要、解析与向量索引状态，并作为进入在线阅读与智能问答的统一入口。
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

    <section v-else-if="loadFailed || !literature" class="glass-panel" aria-label="文献详情异常">
      <el-empty description="文献加载失败或不存在，请返回文献库重新选择">
        <div class="detail-actions">
          <el-button :icon="Refresh" type="primary" @click="loadDetail">重新加载</el-button>
          <el-button :icon="ArrowLeft" plain @click="goLibrary">返回文献库</el-button>
        </div>
      </el-empty>
    </section>

    <template v-else>
      <section class="glass-panel" aria-label="文献信息">
        <div class="reader-titlebar">
          <div>
            <p class="panel__eyebrow">{{ displayCategoryName(literature.categoryName ?? null) }}</p>
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
          <el-tag size="small" effect="plain" :type="statusTagType(literature.status)">
            {{ statusLabel(literature.status) }}
          </el-tag>
          <el-tag size="small" effect="plain">{{ visibilityLabel(literature.visibility) }}</el-tag>
          <el-tag size="small" effect="plain" type="info">{{ summaryStatus }}</el-tag>
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
            <strong>{{ literature.downloadCount ?? 0 }}</strong>
            下载次数
          </span>
          <span>
            <strong>{{ chunkCount }}</strong>
            索引分块
          </span>
          <span>
            <strong>{{ detail?.recentNotes?.length ?? 0 }}</strong>
            最近笔记
          </span>
        </div>

        <div class="detail-actions">
          <el-button
            :icon="favorited ? StarFilled : Star"
            type="primary"
            plain
            :loading="favoriteLoading"
            @click="toggleFavorite"
          >
            {{ favorited ? '取消收藏' : '收藏文献' }}
          </el-button>
          <el-button :icon="Reading" type="primary" @click="openReader">在线阅读</el-button>
          <el-button :icon="ChatLineRound" @click="openQa">智能问答</el-button>
          <el-button :icon="MagicStick" plain :loading="summaryLoading" @click="regenerateSummary">
            {{ literature.aiSummary ? '重新生成摘要' : '生成摘要' }}
          </el-button>
          <el-button :icon="Cpu" plain :loading="indexBuilding" @click="rebuildIndex">
            重建索引
          </el-button>
          <el-button :icon="ArrowLeft" plain @click="goLibrary">返回文献库</el-button>
        </div>
      </section>

      <section class="detail-facts" aria-label="题录信息">
        <div v-for="item in factRows" :key="item.label">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
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
            <el-button :icon="Cpu" plain :loading="indexBuilding" @click="rebuildIndex">
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
            <span :style="{ width: `${literature.visibility === 'PUBLIC' ? 100 : 56}%` }" />
          </div>
          <section class="reading-grid">
            <div>
              <h3>文件信息</h3>
              <div class="file-info">
                <span>类型：{{ literature.fileType || '未绑定原文' }}</span>
                <span>大小：{{ fileSizeText }}</span>
                <span>文件 ID：{{ literature.fileId ?? '—' }}</span>
              </div>
              <a v-if="literature.fileUrl" :href="literature.fileUrl" target="_blank" rel="noreferrer">
                <el-button :icon="Document" plain>打开原文链接</el-button>
              </a>
              <span v-else>暂无可用文件链接</span>
            </div>
            <div>
              <h3>阅读记录</h3>
              <ul>
                <li>最后页码：{{ detail?.readingHistory?.lastPage ?? '暂无记录' }}</li>
                <li>阅读进度：{{ detail?.readingHistory?.progress ?? 0 }}%</li>
                <li>阅读时长：{{ detail?.readingHistory?.readDuration ?? 0 }} 秒</li>
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
            <el-button :icon="Reading" plain @click="openReader">去阅读页管理</el-button>
          </div>
          <div class="note-list" v-if="detail?.recentNotes?.length">
            <article v-for="note in detail.recentNotes" :key="note.id" class="note-card">
              <div>
                <el-tag v-if="note.aiGenerated" size="small" type="success">AI</el-tag>
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
