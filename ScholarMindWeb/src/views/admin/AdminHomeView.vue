<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  DataAnalysis,
  DocumentAdd,
  Refresh,
  Search,
  SwitchButton,
  UploadFilled,
  User,
} from '@element-plus/icons-vue'

import { getAdminDashboard } from '@/api/admin'
import {
  createLiterature,
  deleteAdminLiterature,
  listCategories,
  listLiteratures,
  uploadLiteratureFile,
} from '@/api/literature'
import {
  createKnowledgeDocument,
  listKnowledgeDocuments,
  rebuildKnowledgeDocument,
  updateKnowledgeDocument,
} from '@/api/rag'
import { displayCategoryName, displayLiteratureType, displayRoleName } from '@/utils/format'
import { emptyDashboard } from '@/utils/dashboard'
import type { Id } from '@/types/auth'
import type { AdminActiveUser, AdminDashboard } from '@/types/admin'
import type { Literature, LiteratureCategory, LiteraturePayload } from '@/types/literature'
import type { KnowledgeDocument, KnowledgeDocumentPayload } from '@/types/rag'

const knowledgeDocs = ref<KnowledgeDocument[]>([])
const dashboard = ref<AdminDashboard>(emptyDashboard())
const dashboardFailed = ref(false)
const metrics = computed(() => dashboardFailed.value ? [] : overviewCards.value)
const knowledgeFailed = ref(false)
const archiveFailed = ref(false)
const activeTab = ref('upload')
const loadingDashboard = ref(false)
const loadingKnowledge = ref(false)
const knowledgeDrawer = ref(false)
const savingKnowledge = ref(false)
const editingKnowledgeId = ref<Id | null>(null)

// 上传文献（客户 2026-11-15 口径：文献改由管理员统一上传，上传即发布）
const categories = ref<LiteratureCategory[]>([])
const literatureTypeOptions = ['期刊论文', '会议论文', '学位论文', '图书', '预印本', '其他']
const uploadForm = reactive<LiteraturePayload>({
  title: '',
  author: '',
  source: '',
  literatureType: '期刊论文',
  categoryId: undefined,
  publishDate: '',
  onlinePublishDate: '',
  language: 'zh-CN',
  fileId: undefined,
  keywords: '',
  abstractText: '',
})
const uploadingFile = ref(false)
const uploadingName = ref('')
/** 上传会同步触发索引重建 + AI 摘要，按 id 记录避免重复点击 */
const publishing = ref(false)

// 文献库管理（客户需求：管理中心添加「删除文献」）
const archiveList = ref<Literature[]>([])
const archiveLoading = ref(false)
const archiveTotal = ref(0)
const archiveQuery = reactive({
  page: 1,
  size: 8,
  keyword: '',
})
const deletingId = ref<Id | null>(null)

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
    { label: '用户总数', value: item.userTotal, hint: `${item.studentTotal} 用户端账号`, icon: User },
    { label: '文献总量', value: item.literatureTotal, hint: '均由管理员上传发布', icon: DocumentAdd },
    { label: '原文文件', value: item.fileTotal, hint: '支持 PDF / 文本解析建索引', icon: UploadFilled },
  ]
})

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

/** 活跃分口径与后端 AdminStatisticsService#activeUsers 保持一致：上传×5 + 阅读×2 + 收藏×2 + 笔记×3 + 问答×4 */
function activityBreakdown(item: AdminActiveUser) {
  return [
    `上传 ${item.uploadedLiteratureCount}×5`,
    `阅读 ${item.readingHistoryCount}×2`,
    `收藏 ${item.favoriteCount}×2`,
    `笔记 ${item.noteCount}×3`,
    `问答 ${item.qaCount}×4`,
  ].join(' + ')
}

function resetUploadForm() {
  Object.assign(uploadForm, {
    title: '',
    author: '',
    source: '',
    literatureType: '期刊论文',
    categoryId: undefined,
    publishDate: '',
    onlinePublishDate: '',
    language: 'zh-CN',
    fileId: undefined,
    keywords: '',
    abstractText: '',
  })
  uploadingName.value = ''
}

/** 原文是可选项：选文件后先传文件拿到 fileId，保存文献时一并提交 */
async function pickUploadFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  uploadingFile.value = true
  uploadingName.value = file.name
  try {
    const asset = await uploadLiteratureFile(file)
    uploadForm.fileId = asset.id
    ElMessage.success(`原文已上传：${asset.originalName}`)
  } catch (error) {
    uploadForm.fileId = undefined
    uploadingName.value = ''
    console.error(
      'literature file upload failed, scenario=admin-upload-literature-file, fileName=%s',
      file.name,
      error,
    )
  } finally {
    uploadingFile.value = false
  }
}

function clearUploadFile() {
  uploadForm.fileId = undefined
  uploadingName.value = ''
}

async function submitUpload() {
  if (!uploadForm.title.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  if (!uploadForm.categoryId) {
    ElMessage.warning('请选择学科')
    return
  }
  publishing.value = true
  try {
    // 上传即发布，后端在事务提交后同步跑索引重建 + AI 摘要，耗时可达数十秒
    const created = await createLiterature({
      ...uploadForm,
      title: uploadForm.title.trim(),
      author: uploadForm.author?.trim() || undefined,
      source: uploadForm.source?.trim() || undefined,
      publishDate: uploadForm.publishDate || null,
      onlinePublishDate: uploadForm.onlinePublishDate || null,
      keywords: uploadForm.keywords?.trim() || undefined,
      abstractText: uploadForm.abstractText?.trim() || undefined,
    })
    ElMessage.success(
      created.fileId ? '文献已上传，索引与 AI 摘要已自动生成' : '文献已上传，题录索引与 AI 摘要已自动生成',
    )
    resetUploadForm()
    await Promise.all([fetchDashboard(), fetchArchive()])
  } catch (error) {
    console.error(
      'literature upload failed, scenario=admin-upload-literature, title=%s',
      uploadForm.title,
      error,
    )
  } finally {
    publishing.value = false
  }
}

async function fetchDashboard() {
  loadingDashboard.value = true
  dashboardFailed.value = false
  try {
    dashboard.value = await getAdminDashboard(dashboardQuery)
  } catch (error) {
    console.error(
      'admin dashboard load failed, scenario=admin-statistics-dashboard, days=%s, limit=%s',
      dashboardQuery.days,
      dashboardQuery.limit,
      error,
    )
    dashboardFailed.value = true
    dashboard.value = emptyDashboard()
  } finally {
    loadingDashboard.value = false
  }
}

async function fetchCategories() {
  try {
    categories.value = await listCategories()
  } catch (error) {
    console.error('category list failed, scenario=admin-literature-categories', error)
    categories.value = []
  }
}

async function fetchKnowledge() {
  loadingKnowledge.value = true
  knowledgeFailed.value = false
  try {
    const result = await listKnowledgeDocuments({ page: 1, size: 20 })
    knowledgeDocs.value = result.items
  } catch (error) {
    console.error('knowledge list failed, scenario=admin-rag-documents', error)
    knowledgeFailed.value = true
    knowledgeDocs.value = []
  } finally {
    loadingKnowledge.value = false
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

async function fetchArchive() {
  archiveLoading.value = true
  archiveFailed.value = false
  try {
    const result = await listLiteratures({
      page: archiveQuery.page,
      size: archiveQuery.size,
      keyword: archiveQuery.keyword.trim() || undefined,
    })
    archiveList.value = result.items
    archiveTotal.value = result.total
  } catch (error) {
    console.error('archive list failed, scenario=admin-archive-literatures', error)
    archiveFailed.value = true
    archiveList.value = []
    archiveTotal.value = 0
  } finally {
    archiveLoading.value = false
  }
}

function searchArchive() {
  archiveQuery.page = 1
  void fetchArchive()
}

async function removeFromArchive(item: Literature) {
  try {
    await ElMessageBox.confirm(
      `删除《${item.title}》会同时清理它的分块、收藏、阅读历史、笔记、引用、问答记录与附件文件，且不可恢复。确认删除？`,
      '删除文献',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    throw error
  }

  deletingId.value = item.id
  try {
    await deleteAdminLiterature(item.id)
    ElMessage.success('文献已删除')
    if (archiveList.value.length === 1 && archiveQuery.page > 1) {
      archiveQuery.page -= 1
    }
    await Promise.all([fetchArchive(), fetchDashboard()])
  } catch (error) {
    console.error(
      'archive delete failed, scenario=admin-delete-literature, literatureId=%s',
      item.id,
      error,
    )
  } finally {
    deletingId.value = null
  }
}

onMounted(async () => {
  await Promise.all([fetchDashboard(), fetchCategories(), fetchKnowledge(), fetchArchive()])
})
</script>

<template>
  <section class="admin-page">


    <section class="admin-control-strip glass-panel">
      <div>

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

    <el-empty v-if="dashboardFailed" description="统计数据加载失败">
      <el-button @click="fetchDashboard">重试</el-button>
    </el-empty>
    <section class="admin-kpi-grid admin-kpi-grid--three" v-loading="loadingDashboard">
      <article v-for="item in metrics" :key="item.label" class="screen-kpi">
        <el-icon><component :is="item.icon" /></el-icon>
        <p>{{ item.label }}</p>
        <strong>{{ item.value }}</strong>
        <span>{{ item.hint }}</span>
      </article>
    </section>

    <el-tabs v-model="activeTab" class="management-tabs">
      <el-tab-pane label="上传文献" name="upload" />
      <el-tab-pane label="文献管理" name="archive" />
      <el-tab-pane label="知识库" name="knowledge" />
      <el-tab-pane label="活跃概况" name="activity" />
    </el-tabs>
    <div class="admin-layout admin-layout--stage5">
      <section v-show="activeTab === 'upload'" class="glass-panel upload-panel" v-loading="publishing">
        <div class="panel-heading">
          <div>
            <p class="panel__eyebrow">管理员统一上传</p>
            <h3>上传文献</h3>
            <p class="panel-caption">
              提交即发布，系统自动重建检索索引并生成 AI 摘要；原文为可选项，未上传时按题录建索引。
            </p>
          </div>
          <el-button plain @click="resetUploadForm">重置</el-button>
        </div>

        <el-form label-position="top" class="upload-form">
          <el-form-item label="标题" required>
            <el-input v-model="uploadForm.title" maxlength="255" placeholder="文献标题" />
          </el-form-item>
          <div class="filter-grid">
            <el-form-item label="作者">
              <el-input v-model="uploadForm.author" maxlength="255" placeholder="多位作者用分号分隔" />
            </el-form-item>
            <el-form-item label="来源">
              <el-input v-model="uploadForm.source" maxlength="255" placeholder="期刊 / 会议 / 出版社" />
            </el-form-item>
          </div>
          <div class="filter-grid">
            <el-form-item label="文献类别">
              <el-select v-model="uploadForm.literatureType" placeholder="选择文献类别">
                <el-option
                  v-for="type in literatureTypeOptions"
                  :key="type"
                  :label="type"
                  :value="type"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="学科" required>
              <el-select v-model="uploadForm.categoryId" placeholder="选择学科">
                <el-option
                  v-for="category in categories"
                  :key="category.id"
                  :label="displayCategoryName(category.name)"
                  :value="category.id"
                />
              </el-select>
            </el-form-item>
          </div>
          <div class="filter-grid">
            <el-form-item label="发表时间">
              <el-date-picker
                v-model="uploadForm.publishDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择发表日期"
              />
            </el-form-item>
            <el-form-item label="在线公开时间">
              <el-date-picker
                v-model="uploadForm.onlinePublishDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择在线公开日期"
              />
            </el-form-item>
          </div>
          <el-form-item label="关键词">
            <el-input v-model="uploadForm.keywords" maxlength="512" placeholder="用英文逗号分隔" />
          </el-form-item>
          <el-form-item label="摘要">
            <el-input v-model="uploadForm.abstractText" type="textarea" :rows="4" placeholder="文献摘要" />
          </el-form-item>

          <el-form-item label="原文（可选）">
            <div class="upload-field">
              <label class="upload-drop">
                <el-icon><UploadFilled /></el-icon>
                <span v-if="uploadingFile">正在上传 {{ uploadingName }}…</span>
                <span v-else-if="uploadForm.fileId">已绑定原文：{{ uploadingName }}</span>
                <span v-else>选择 PDF 或文本文件</span>
                <input
                  type="file"
                  accept=".pdf,.txt,application/pdf,text/plain"
                  :disabled="uploadingFile || publishing"
                  @change="pickUploadFile"
                />
              </label>
              <el-button v-if="uploadForm.fileId" plain @click="clearUploadFile">移除原文</el-button>
            </div>
          </el-form-item>

          <el-button
            class="wide-action"
            type="primary"
            :loading="publishing"
            :disabled="uploadingFile"
            @click="submitUpload"
          >
            {{ publishing ? '正在建索引并生成摘要…' : '上传并发布' }}
          </el-button>
        </el-form>
      </section>

      <section v-show="activeTab === 'knowledge'" class="glass-panel knowledge-panel" v-loading="loadingKnowledge">
        <div class="panel-heading">
          <div>

            <h3>知识库文档</h3>
          </div>
          <el-button type="primary" :icon="Search" @click="openKnowledgeCreate">
            新增知识
          </el-button>
        </div>

        <div class="knowledge-list">
          <el-empty v-if="!knowledgeDocs.length && !loadingKnowledge" :description="knowledgeFailed ? '知识库加载失败' : '暂无知识文档'">
            <el-button v-if="knowledgeFailed" @click="fetchKnowledge">重试</el-button>
          </el-empty>
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
    </div>

    <section v-show="activeTab === 'archive'" class="glass-panel archive-panel">
      <div class="panel-heading">
        <div>

          <h3>文献库管理</h3>
        </div>
        <div class="archive-actions">
          <el-input
            v-model="archiveQuery.keyword"
            placeholder="按标题 / 作者搜索"
            clearable
            :prefix-icon="Search"
            @keyup.enter="searchArchive"
            @clear="searchArchive"
          />
          <el-button :icon="Refresh" :loading="archiveLoading" @click="searchArchive">
            查询
          </el-button>
        </div>
      </div>

      <el-table v-loading="archiveLoading" :data="archiveList" row-key="id" stripe>
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="author" label="作者" width="140" show-overflow-tooltip />
        <el-table-column label="学科" width="150">
          <template #default="{ row }">{{ displayCategoryName(row.categoryName) }}</template>
        </el-table-column>
        <el-table-column label="文献类别" width="120">
          <template #default="{ row }">{{ displayLiteratureType(row.literatureType) }}</template>
        </el-table-column>
        <el-table-column label="出版日期" width="130">
          <template #default="{ row }">{{ row.publishDate || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="right">
          <template #default="{ row }">
            <el-button
              type="danger"
              link
              :loading="deletingId === row.id"
              @click="removeFromArchive(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="archiveFailed ? '文献加载失败' : '没有匹配的文献'">
            <el-button v-if="archiveFailed" @click="fetchArchive">重试</el-button>
          </el-empty>
        </template>
      </el-table>

      <div class="admin-pagination">
        <el-pagination
          v-model:current-page="archiveQuery.page"
          v-model:page-size="archiveQuery.size"
          :total="archiveTotal"
          :page-sizes="[8, 16, 32]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="fetchArchive"
          @size-change="searchArchive"
        />
      </div>
    </section>

    <section v-show="activeTab === 'activity' && !dashboardFailed" class="admin-insight-grid">
      <div class="glass-panel chart-panel">
        <div class="panel-heading">
          <div>

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
            <small>{{ displayLiteratureType(item.literatureType) }} · 阅读 {{ item.readCount }} · 收藏 {{ item.favoriteCount }} · 笔记 {{ item.noteCount }}</small>
          </article>
        </div>
      </div>

      <div class="glass-panel chart-panel">
        <div class="panel-heading">
          <div>

            <h3>活跃用户</h3>
            <p class="panel-caption">活跃分 = 上传×5 + 阅读×2 + 收藏×2 + 笔记×3 + 问答×4</p>
          </div>
          <el-icon><User /></el-icon>
        </div>
        <div class="active-user-list">
          <article v-for="item in dashboard.activeUsers" :key="item.userId">
            <span>{{ item.nickname || item.username }}</span>
            <el-tooltip placement="top" :content="activityBreakdown(item)">
              <strong class="activity-score">{{ item.activityScore }}<em>分</em></strong>
            </el-tooltip>
            <small>{{ displayRoleName(item.role) }} · 阅读 {{ item.readingHistoryCount }} · 问答 {{ item.qaCount }}</small>
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

<style scoped>
/* 移除「AI 成功率」后仅剩 3 张 KPI 卡片，收窄为三列避免留空 */
.admin-kpi-grid--three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

/* 文献库管理（管理中心内的删除入口） */
.archive-panel {
  display: grid;
  gap: 16px;
}

.archive-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.archive-actions .el-input {
  width: 260px;
}

@media (max-width: 900px) {
  .admin-kpi-grid--three {
    grid-template-columns: 1fr;
  }

  .archive-actions .el-input {
    width: 100%;
  }
}
</style>
