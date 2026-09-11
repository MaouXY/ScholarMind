<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  CircleCheck,
  Cpu,
  DataAnalysis,
  DocumentAdd,
  Refresh,
  Search,
  SwitchButton,
  Timer,
  User,
} from '@element-plus/icons-vue'

import { getAdminDashboard } from '@/api/admin'
import {
  approveLiterature,
  listPendingLiteratures,
  rejectLiterature,
} from '@/api/literature'
import {
  createKnowledgeDocument,
  listKnowledgeDocuments,
  rebuildKnowledgeDocument,
  updateKnowledgeDocument,
} from '@/api/rag'
import { displayCategoryName, displayStatusName } from '@/utils/format'
import type { Id } from '@/types/auth'
import type { AdminDashboard } from '@/types/admin'
import type { Literature } from '@/types/literature'
import type { KnowledgeDocument, KnowledgeDocumentPayload } from '@/types/rag'

const emptyOverview = {
  userTotal: 0,
  studentTotal: 0,
  teacherTotal: 0,
  activeUserTotal: 0,
  literatureTotal: 0,
  publishedLiteratureTotal: 0,
  pendingReviewTotal: 0,
  privateLiteratureTotal: 0,
  fileTotal: 0,
  fileSizeBytes: 0,
  favoriteTotal: 0,
  readingHistoryTotal: 0,
  readingNoteTotal: 0,
  qaTotal: 0,
  ragDocumentTotal: 0,
  aiTaskTotal: 0,
  aiTaskSuccessTotal: 0,
  aiTaskFailedTotal: 0,
  aiSuccessRate: 0,
  avgAiDurationMs: 0,
}

const demoDashboard: AdminDashboard = {
  overview: {
    ...emptyOverview,
    userTotal: 36,
    studentTotal: 28,
    teacherTotal: 7,
    activeUserTotal: 24,
    literatureTotal: 128,
    publishedLiteratureTotal: 94,
    pendingReviewTotal: 9,
    privateLiteratureTotal: 25,
    fileTotal: 116,
    fileSizeBytes: 1024 * 1024 * 384,
    favoriteTotal: 216,
    readingHistoryTotal: 346,
    readingNoteTotal: 142,
    qaTotal: 88,
    ragDocumentTotal: 18,
    aiTaskTotal: 126,
    aiTaskSuccessTotal: 116,
    aiTaskFailedTotal: 6,
    aiSuccessRate: 0.92,
    avgAiDurationMs: 1840,
  },
  trends: [],
  literatureStatusStats: [
    { name: 'PUBLISHED', count: 94 },
    { name: 'PENDING_REVIEW', count: 9 },
    { name: 'DRAFT', count: 18 },
    { name: 'REJECTED', count: 7 },
  ],
  literatureCategoryStats: [
    { name: 'Information Retrieval', count: 36 },
    { name: 'Artificial Intelligence', count: 31 },
    { name: 'Digital Library', count: 26 },
  ],
  userRoleStats: [
    { name: 'STUDENT', count: 28 },
    { name: 'TEACHER', count: 7 },
    { name: 'ADMIN', count: 1 },
  ],
  hotLiteratures: [
    {
      literatureId: '1200000000000000001',
      title: 'Research on Intelligent Recommendation Service in University Digital Library',
      author: 'Li Ming',
      categoryName: 'Recommendation System',
      status: 'PUBLISHED',
      visibility: 'PUBLIC',
      readCount: 86,
      downloadCount: 18,
      favoriteCount: 24,
      noteCount: 11,
    },
  ],
  activeUsers: [
    {
      userId: '1000000000000000002',
      username: 'student01',
      nickname: 'Student One',
      role: 'STUDENT',
      uploadedLiteratureCount: 8,
      readingHistoryCount: 22,
      favoriteCount: 15,
      noteCount: 9,
      qaCount: 12,
      activityScore: 66,
    },
  ],
  aiTaskHealth: [
    {
      taskType: 'LITERATURE_QA',
      total: 88,
      success: 82,
      failed: 4,
      timeout: 2,
      successRate: 0.93,
      avgDurationMs: 1620,
    },
  ],
}

const pending = ref<Literature[]>([])
const knowledgeDocs = ref<KnowledgeDocument[]>([])
const dashboard = ref<AdminDashboard>(demoDashboard)
const loadingDashboard = ref(false)
const loadingPending = ref(false)
const loadingKnowledge = ref(false)
const knowledgeDrawer = ref(false)
const savingKnowledge = ref(false)
const editingKnowledgeId = ref<Id | null>(null)

const dashboardQuery = reactive({
  days: 14,
  limit: 6,
})

const knowledgeForm = reactive<KnowledgeDocumentPayload>({
  title: '',
  categoryId: undefined,
  content: '',
  enabled: 1,
})

const overviewCards = computed(() => {
  const item = dashboard.value.overview
  return [
    { label: '用户总数', value: item.userTotal, hint: `${item.studentTotal} 学生 / ${item.teacherTotal} 教师`, icon: User },
    { label: '文献总量', value: item.literatureTotal, hint: `${item.publishedLiteratureTotal} 已发布`, icon: DocumentAdd },
    { label: '待审核', value: item.pendingReviewTotal, hint: `${item.privateLiteratureTotal} 私有文献`, icon: Timer },
    { label: 'AI 成功率', value: `${Math.round((item.aiSuccessRate || 0) * 100)}%`, hint: `${item.avgAiDurationMs || 0}ms 平均`, icon: Cpu },
  ]
})

const reviewPressure = computed(() => dashboard.value.overview.pendingReviewTotal + pending.value.length)

const demoPending: Literature[] = [
  {
    id: '1200000000000000003',
    title: 'Application of Large Language Models in Literature Reading Assistance',
    author: 'Zhang Wei',
    source: 'Computer Applications',
    publishYear: 2025,
    categoryName: 'Artificial Intelligence',
    uploaderId: '1000000000000000002',
    visibility: 'PUBLIC',
    status: 'PENDING_REVIEW',
    abstractText:
      'The article explores how large language models support summarization, question answering, and reading note generation in literature reading scenarios.',
    keywords: 'large language model,literature reading,question answering,summary',
    readCount: 9,
    downloadCount: 1,
  },
]

const demoKnowledge: KnowledgeDocument[] = [
  {
    id: '1300000000000000001',
    title: 'AI 文献阅读助手平台说明',
    categoryId: '1100000000000000003',
    content: '平台支持文献检索、AI 摘要、阅读笔记、审核发布与 RAG 知识库维护。',
    enabled: 1,
    chunkCount: 4,
  },
]

function resetKnowledgeForm() {
  editingKnowledgeId.value = null
  Object.assign(knowledgeForm, {
    title: '',
    categoryId: undefined,
    content: '',
    enabled: 1,
  })
}

function openKnowledgeCreate() {
  resetKnowledgeForm()
  knowledgeDrawer.value = true
}

function openKnowledgeEdit(item: KnowledgeDocument) {
  editingKnowledgeId.value = item.id
  Object.assign(knowledgeForm, {
    title: item.title,
    categoryId: item.categoryId ?? undefined,
    content: item.content,
    enabled: item.enabled,
  })
  knowledgeDrawer.value = true
}

function formatBytes(bytes: number) {
  if (!bytes) return '0 MB'
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function statusLabel(status: string) {
  return displayStatusName(status)
}

async function fetchDashboard() {
  loadingDashboard.value = true
  try {
    dashboard.value = await getAdminDashboard(dashboardQuery)
  } catch (error) {
    console.error(
      'admin dashboard load failed, scenario=admin-statistics-dashboard, days=%s, limit=%s',
      dashboardQuery.days,
      dashboardQuery.limit,
      error,
    )
    dashboard.value = demoDashboard
  } finally {
    loadingDashboard.value = false
  }
}

async function fetchPending() {
  loadingPending.value = true
  try {
    const result = await listPendingLiteratures({ page: 1, size: 20 })
    pending.value = result.items
  } catch (error) {
    console.error('pending list failed, scenario=admin-pending-literatures', error)
    pending.value = demoPending
  } finally {
    loadingPending.value = false
  }
}

async function fetchKnowledge() {
  loadingKnowledge.value = true
  try {
    const result = await listKnowledgeDocuments({ page: 1, size: 20 })
    knowledgeDocs.value = result.items
  } catch (error) {
    console.error('knowledge list failed, scenario=admin-rag-documents', error)
    knowledgeDocs.value = demoKnowledge
  } finally {
    loadingKnowledge.value = false
  }
}

async function approve(item: Literature) {
  try {
    await approveLiterature(item.id)
    ElMessage.success('文献已发布')
    await Promise.all([fetchPending(), fetchDashboard()])
  } catch (error) {
    console.error(
      'approve failed, scenario=admin-approve-literature, literatureId=%s',
      item.id,
      error,
    )
  }
}

async function reject(item: Literature) {
  try {
    const { value } = await ElMessageBox.prompt('请输入退回原因', '退回文献', {
      confirmButtonText: '退回',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '退回原因不能为空',
    })
    await rejectLiterature(item.id, value)
    ElMessage.success('文献已退回')
    await Promise.all([fetchPending(), fetchDashboard()])
  } catch (error) {
    if (error === 'cancel') return
    console.error(
      'reject failed, scenario=admin-reject-literature, literatureId=%s',
      item.id,
      error,
    )
  }
}

async function saveKnowledge() {
  if (!knowledgeForm.title.trim() || !knowledgeForm.content.trim()) {
    ElMessage.warning('请填写标题和内容')
    return
  }

  savingKnowledge.value = true
  try {
    if (editingKnowledgeId.value) {
      await updateKnowledgeDocument(editingKnowledgeId.value, knowledgeForm)
    } else {
      await createKnowledgeDocument(knowledgeForm)
    }
    ElMessage.success('知识文档已保存')
    knowledgeDrawer.value = false
    await Promise.all([fetchKnowledge(), fetchDashboard()])
  } catch (error) {
    console.error(
      'knowledge save failed, scenario=admin-rag-save, knowledgeDocumentId=%s, title=%s',
      editingKnowledgeId.value,
      knowledgeForm.title,
      error,
    )
  } finally {
    savingKnowledge.value = false
  }
}

async function rebuild(item: KnowledgeDocument) {
  try {
    const count = await rebuildKnowledgeDocument(item.id)
    ElMessage.success(`已重建 ${count} 个分块`)
    await Promise.all([fetchKnowledge(), fetchDashboard()])
  } catch (error) {
    console.error(
      'knowledge rebuild failed, scenario=admin-rag-rebuild, knowledgeDocumentId=%s',
      item.id,
      error,
    )
  }
}

onMounted(async () => {
  await Promise.all([fetchDashboard(), fetchPending(), fetchKnowledge()])
})
</script>

<template>
  <section class="admin-page">
    <div class="spatial-hero compact-hero">
      <div>
        <p class="panel__eyebrow">Stage 5 Admin Command</p>
        <h2>把审核、知识库和平台健康度放在一个后台管理舱</h2>
        <p>
          第五阶段聚焦后台管理与数据统计：管理员可以处理公开发布、维护 RAG 知识库，并观察用户、文献、AI 任务的关键运行指标。
        </p>
      </div>
      <div class="hero-stats">
        <span><strong>{{ reviewPressure }}</strong>审核压力</span>
        <span><strong>{{ dashboard.overview.ragDocumentTotal }}</strong>RAG 文档</span>
      </div>
    </div>

    <section class="admin-control-strip glass-panel">
      <div>
        <p class="panel__eyebrow">Statistics Window</p>
        <h3>统计窗口</h3>
      </div>
      <el-segmented
        v-model="dashboardQuery.days"
        :options="[
          { label: '7 天', value: 7 },
          { label: '14 天', value: 14 },
          { label: '30 天', value: 30 },
        ]"
      />
      <el-button :icon="Refresh" type="primary" :loading="loadingDashboard" @click="fetchDashboard">
        刷新统计
      </el-button>
    </section>

    <section class="admin-kpi-grid">
      <article v-for="item in overviewCards" :key="item.label" class="screen-kpi">
        <el-icon><component :is="item.icon" /></el-icon>
        <p>{{ item.label }}</p>
        <strong>{{ item.value }}</strong>
        <span>{{ item.hint }}</span>
      </article>
    </section>

    <div class="admin-layout admin-layout--stage5">
      <section class="glass-panel review-panel">
        <div class="panel-heading">
          <div>
            <p class="panel__eyebrow">Publication Queue</p>
            <h3>公开文献审核</h3>
          </div>
          <el-button :icon="Refresh" plain :loading="loadingPending" @click="fetchPending">
            刷新
          </el-button>
        </div>
        <div class="approval-list">
          <article v-for="item in pending" :key="item.id" class="approval-card">
            <div>
              <p class="panel__eyebrow">{{ displayCategoryName(item.categoryName) }}</p>
              <h3>{{ item.title }}</h3>
              <p>{{ item.abstractText || '暂无摘要。' }}</p>
              <div class="paper-card__meta">
                <span>{{ item.author || '未知作者' }}</span>
                <span>{{ item.source || '未填写来源' }}</span>
                <span>{{ item.publishYear || '年份未知' }}</span>
              </div>
            </div>
            <div class="approval-actions">
              <el-button :icon="CircleCheck" type="success" @click="approve(item)">通过</el-button>
              <el-button type="danger" plain @click="reject(item)">退回</el-button>
            </div>
          </article>
          <el-empty v-if="!pending.length && !loadingPending" description="暂无待审核文献" />
        </div>
      </section>

      <section class="glass-panel knowledge-panel">
        <div class="panel-heading">
          <div>
            <p class="panel__eyebrow">RAG Knowledge</p>
            <h3>知识库文档</h3>
          </div>
          <el-button type="primary" :icon="Search" @click="openKnowledgeCreate">
            新增知识
          </el-button>
        </div>

        <div class="knowledge-list">
          <article v-for="item in knowledgeDocs" :key="item.id" class="knowledge-card">
            <div>
              <h3>{{ item.title }}</h3>
              <p>{{ item.content }}</p>
              <div class="paper-card__meta">
                <span>{{ item.enabled ? '已启用' : '已停用' }}</span>
                <span>{{ item.chunkCount || 0 }} 个分块</span>
              </div>
            </div>
            <div class="approval-actions">
              <el-button plain @click="openKnowledgeEdit(item)">编辑</el-button>
              <el-button :icon="SwitchButton" plain @click="rebuild(item)">重建分块</el-button>
            </div>
          </article>
        </div>
      </section>

      <section class="glass-panel ops-panel">
        <div class="panel-heading">
          <div>
            <p class="panel__eyebrow">Operational Health</p>
            <h3>平台运行健康</h3>
          </div>
          <el-tag type="success" effect="light">{{ Math.round((dashboard.overview.aiSuccessRate || 0) * 100) }}%</el-tag>
        </div>
        <div class="admin-signal-grid">
          <span><strong>{{ dashboard.overview.fileTotal }}</strong>文件资产</span>
          <span><strong>{{ formatBytes(dashboard.overview.fileSizeBytes) }}</strong>存储占用</span>
          <span><strong>{{ dashboard.overview.readingNoteTotal }}</strong>阅读笔记</span>
          <span><strong>{{ dashboard.overview.qaTotal }}</strong>问答记录</span>
        </div>
        <div class="ai-health-list" v-if="dashboard.aiTaskHealth.length">
          <article v-for="item in dashboard.aiTaskHealth" :key="item.taskType">
            <div>
              <strong>{{ item.taskType }}</strong>
              <span>{{ item.success }} / {{ item.total }} 成功</span>
            </div>
            <div class="progress-line">
              <span :style="{ width: `${Math.round((item.successRate || 0) * 100)}%` }" />
            </div>
            <small>失败 {{ item.failed }} · 超时 {{ item.timeout }} · 平均 {{ item.avgDurationMs }}ms</small>
          </article>
        </div>
        <el-empty v-else description="暂无 AI 任务统计" />
      </section>
    </div>

    <section class="admin-insight-grid">
      <div class="glass-panel chart-panel">
        <div class="panel-heading">
          <div>
            <p class="panel__eyebrow">Hot Literature</p>
            <h3>热门文献</h3>
          </div>
          <el-icon><DataAnalysis /></el-icon>
        </div>
        <div class="hot-literature-list">
          <article v-for="item in dashboard.hotLiteratures" :key="item.literatureId">
            <div>
              <strong>{{ item.title }}</strong>
              <span>{{ item.author || '未知作者' }} · {{ displayCategoryName(item.categoryName) }}</span>
            </div>
            <small>{{ statusLabel(item.status) }} · 阅读 {{ item.readCount }} · 收藏 {{ item.favoriteCount }} · 笔记 {{ item.noteCount }}</small>
          </article>
        </div>
      </div>

      <div class="glass-panel chart-panel">
        <div class="panel-heading">
          <div>
            <p class="panel__eyebrow">Active Users</p>
            <h3>活跃用户</h3>
          </div>
          <el-icon><User /></el-icon>
        </div>
        <div class="active-user-list">
          <article v-for="item in dashboard.activeUsers" :key="item.userId">
            <span>{{ item.nickname || item.username }}</span>
            <strong>{{ item.activityScore }}</strong>
            <small>{{ item.role }} · 阅读 {{ item.readingHistoryCount }} · QA {{ item.qaCount }}</small>
          </article>
        </div>
      </div>
    </section>

    <el-drawer v-model="knowledgeDrawer" title="知识库文档" size="560px">
      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="knowledgeForm.title" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-segmented
            v-model="knowledgeForm.enabled"
            :options="[
              { label: '启用', value: 1 },
              { label: '停用', value: 0 },
            ]"
          />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="knowledgeForm.content" type="textarea" :rows="12" />
        </el-form-item>
        <el-button
          type="primary"
          class="wide-action drawer-action"
          :loading="savingKnowledge"
          @click="saveKnowledge"
        >
          保存知识文档
        </el-button>
      </el-form>
    </el-drawer>
  </section>
</template>
