<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, Refresh, Search, SwitchButton } from '@element-plus/icons-vue'

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
import type { Literature } from '@/types/literature'
import type { KnowledgeDocument, KnowledgeDocumentPayload } from '@/types/rag'

const pending = ref<Literature[]>([])
const knowledgeDocs = ref<KnowledgeDocument[]>([])
const loadingPending = ref(false)
const loadingKnowledge = ref(false)
const knowledgeDrawer = ref(false)
const savingKnowledge = ref(false)
const editingKnowledgeId = ref<number | null>(null)

const knowledgeForm = reactive<KnowledgeDocumentPayload>({
  title: '',
  categoryId: undefined,
  content: '',
  enabled: 1,
})

const demoPending: Literature[] = [
  {
    id: 1200000000000000003,
    title: 'Application of Large Language Models in Literature Reading Assistance',
    author: 'Zhang Wei',
    source: 'Computer Applications',
    publishYear: 2025,
    categoryName: 'Artificial Intelligence',
    uploaderId: 1000000000000000002,
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
    id: 1300000000000000001,
    title: 'AI 文献阅读助手平台说明',
    categoryId: 1100000000000000003,
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
    await fetchPending()
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
    await fetchPending()
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
    await fetchKnowledge()
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
    await fetchKnowledge()
  } catch (error) {
    console.error(
      'knowledge rebuild failed, scenario=admin-rag-rebuild, knowledgeDocumentId=%s',
      item.id,
      error,
    )
  }
}

onMounted(async () => {
  await Promise.all([fetchPending(), fetchKnowledge()])
})
</script>

<template>
  <section class="admin-page">
    <div class="spatial-hero compact-hero">
      <div>
        <p class="panel__eyebrow">Governance Deck</p>
        <h2>审核文献质量，维护 AI 回答依据</h2>
        <p>
          管理员在这里处理公开文献发布、知识库内容维护和向量分块重建，保证平台可检索、可追踪、可解释。
        </p>
      </div>
      <div class="hero-stats">
        <span><strong>{{ pending.length }}</strong>待审核</span>
        <span><strong>{{ knowledgeDocs.length }}</strong>知识文档</span>
      </div>
    </div>

    <div class="admin-layout">
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
              <p class="panel__eyebrow">{{ item.categoryName || '未分类' }}</p>
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
    </div>

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
