<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  ChatLineRound,
  Reading,
  Refresh,
  Search,
} from '@element-plus/icons-vue'

import {
  askLiteratureQuestion,
  getLiteratureView,
  listLiteratureQaRecords,
} from '@/api/literature'
import { displayCategoryName } from '@/utils/format'
import type {
  Id,
  Literature,
  LiteratureQuestionResult,
  LiteratureReferenceChunk,
} from '@/types/literature'
import '@/styles/literature-ext.css'

const route = useRoute()
const router = useRouter()

const literatureId = computed<Id>(() => {
  const raw = route.params.id
  return Array.isArray(raw) ? String(raw[0] ?? '') : String(raw ?? '')
})

const literature = ref<Literature | null>(null)
const qaRecords = ref<LiteratureQuestionResult[]>([])
const activeAnswer = ref<LiteratureQuestionResult | null>(null)

const loading = ref(false)
const asking = ref(false)
const askSlow = ref(false)
const historyLoading = ref(false)
const loadFailed = ref(false)

let askSlowTimer: ReturnType<typeof setTimeout> | null = null

const qaForm = reactive({
  question: '',
  limit: 4,
})

// 建议问题：点击后填入输入框
const suggestedQuestions = ['这篇文献的研究问题是什么？', '作者用了什么方法？', '结论与局限分别是什么？']

const references = computed<LiteratureReferenceChunk[]>(() => activeAnswer.value?.references ?? [])

function scorePercent(score?: number) {
  if (typeof score !== 'number') return 0
  return Math.round(Math.min(1, Math.max(0, score)) * 100)
}

function referenceLabel(reference: LiteratureReferenceChunk) {
  return `#${reference.chunkIndex ?? reference.id}`
}

type RetrievalBadge = {
  label: string
  tone: 'primary' | 'success' | 'info' | 'warning'
}

// 混合检索链路返回的引用片段会带稀疏 / 稠密名次与 RRF 融合分，旧记录可能缺少这些可选字段
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

function recordKey(record: LiteratureQuestionResult, index: number) {
  return String(record.recordId ?? `${record.question}-${index}`)
}

function applyAnswer(record: LiteratureQuestionResult) {
  activeAnswer.value = record
}

async function loadLiterature() {
  const id = literatureId.value
  if (!id) {
    loadFailed.value = true
    ElMessage.warning('缺少文献 ID，无法进入问答')
    return
  }

  loading.value = true
  loadFailed.value = false
  try {
    const response = await getLiteratureView(id)
    literature.value = response.literature
  } catch (error) {
    console.error(
      'qa literature load failed, scenario=literature-qa-detail, literatureId=%s',
      id,
      error,
    )
    literature.value = null
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

async function fetchQaHistory() {
  const id = literatureId.value
  if (!id) return

  historyLoading.value = true
  try {
    qaRecords.value = await listLiteratureQaRecords({ literatureId: id, page: 1, size: 20 })
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

function clearAskSlowTimer() {
  if (askSlowTimer !== null) {
    clearTimeout(askSlowTimer)
    askSlowTimer = null
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
  askSlow.value = false
  clearAskSlowTimer()
  askSlowTimer = setTimeout(() => {
    askSlow.value = true
  }, 10000)
  try {
    const result = await askLiteratureQuestion(id, {
      question: qaForm.question,
      limit: qaForm.limit,
    })
    applyAnswer(result)
    qaRecords.value = [
      result,
      ...qaRecords.value.filter((item) => String(item.recordId) !== String(result.recordId)),
    ]
    ElMessage.success('RAG 问答已生成')
  } catch (error) {
    console.error(
      'literature question failed, scenario=literature-rag-question, literatureId=%s, question=%s, limit=%s',
      id,
      qaForm.question,
      qaForm.limit,
      error,
    )
  } finally {
    asking.value = false
    askSlow.value = false
    clearAskSlowTimer()
  }
}

function useSuggestion(question: string) {
  qaForm.question = question
}

function goDetail() {
  router.push(`/student/literature/${literatureId.value}`)
}

function goReader() {
  router.push(`/student/literature/${literatureId.value}/read`)
}

onBeforeUnmount(() => {
  clearAskSlowTimer()
})

onMounted(async () => {
  await loadLiterature()
  await fetchQaHistory()
})
</script>

<template>
  <section class="library-page">
    <div class="spatial-hero compact-hero">
      <div>
        <p class="panel__eyebrow">RAG Question Answering</p>
        <h2>用检索到的原文片段回答论文问题</h2>
        <p>
          问答页围绕当前文献发起 RAG 提问，回答与引用片段一一对应，并保留历史问答记录。
        </p>
      </div>
      <div class="hero-orbit" aria-hidden="true">
        <span>Ask</span>
        <span>Cite</span>
        <span>Read</span>
      </div>
    </div>

    <section v-if="loading" class="glass-panel" aria-label="问答页加载中">
      <el-skeleton :rows="8" animated />
    </section>

    <section v-else-if="loadFailed || !literature" class="glass-panel" aria-label="问答页异常">
      <el-empty description="文献加载失败或不存在，请返回文献库重新选择">
        <div class="detail-actions">
          <el-button :icon="Refresh" type="primary" @click="loadLiterature">重新加载</el-button>
          <el-button :icon="ArrowLeft" plain @click="goDetail">返回详情</el-button>
        </div>
      </el-empty>
    </section>

    <template v-else>
      <section class="glass-panel" aria-label="当前文献">
        <div class="reader-titlebar">
          <div>
            <p class="panel__eyebrow">{{ displayCategoryName(literature.categoryName ?? null) }}</p>
            <h2>{{ literature.title }}</h2>
          </div>
          <el-tag effect="plain">{{ literature.author || '未知作者' }}</el-tag>
        </div>
        <div class="reader-meta">
          <span>{{ literature.source || '未填写来源' }}</span>
          <span>{{ literature.publishYear || '年份未知' }}</span>
          <span>{{ literature.language || '语言未填写' }}</span>
          <span>文献编号 {{ literature.id }}</span>
        </div>
        <div class="detail-actions">
          <el-button :icon="ArrowLeft" plain @click="goDetail">返回详情</el-button>
          <el-button :icon="Reading" plain @click="goReader">在线阅读</el-button>
          <el-button
            :icon="Refresh"
            plain
            :loading="historyLoading"
            @click="fetchQaHistory"
          >
            刷新历史问答
          </el-button>
        </div>
      </section>

      <div class="analytics-grid">
        <aside class="glass-panel rag-command-panel" aria-label="提问">
          <div class="panel-heading">
            <div>
              <p class="panel__eyebrow">Ask With Evidence</p>
              <h3>提问</h3>
            </div>
            <el-tag effect="plain">引用上限 {{ qaForm.limit }}</el-tag>
          </div>
          <el-input
            v-model="qaForm.question"
            type="textarea"
            :rows="4"
            placeholder="围绕这篇文献提出一个具体问题"
          />
          <div class="rag-command-panel__footer">
            <span>引用上限</span>
            <el-input-number v-model="qaForm.limit" :min="1" :max="8" controls-position="right" />
          </div>
          <el-button
            :icon="ChatLineRound"
            type="primary"
            class="wide-action"
            :loading="asking"
            :disabled="asking"
            @click="askQuestion"
          >
            提问
          </el-button>
          <p v-if="askSlow" class="reader-leaf__note">
            AI 正在生成，请稍候…
          </p>
          <section class="rag-search-box">
            <div class="panel-heading">
              <div>
                <p class="panel__eyebrow">Suggested Questions</p>
                <h3>推荐问题</h3>
              </div>
            </div>
            <div class="suggestion-row">
              <button
                v-for="question in suggestedQuestions"
                :key="question"
                type="button"
                @click="useSuggestion(question)"
              >
                {{ question }}
              </button>
            </div>
          </section>
        </aside>

        <section class="glass-panel" aria-label="回答">
          <div class="panel-heading">
            <div>
              <p class="panel__eyebrow">Answer</p>
              <h3>回答</h3>
            </div>
            <el-tag v-if="activeAnswer?.createdAt" effect="plain">
              {{ activeAnswer.createdAt.slice(0, 19).replace('T', ' ') }}
            </el-tag>
          </div>

          <section v-if="activeAnswer" class="answer-panel">
            <div class="answer-panel__bubble">
              <span>{{ activeAnswer.question }}</span>
              <p>{{ activeAnswer.answer }}</p>
            </div>
          </section>
          <el-empty v-else description="输入问题并点击「提问」，回答与引用片段会显示在这里" />

          <section class="compare-panel answer-references" aria-label="引用片段">
            <div class="panel-heading">
              <div>
                <p class="panel__eyebrow">References</p>
                <h3>引用片段</h3>
              </div>
              <span>{{ references.length }} 条</span>
            </div>
            <div class="reference-list" v-if="references.length">
              <article v-for="reference in references" :key="reference.id">
                <div>
                  <strong>{{ referenceLabel(reference) }}</strong>
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
            <el-empty v-else description="暂无引用片段，提问后展示命中分块" />
          </section>
        </section>
      </div>

      <div class="paper-stack">
        <section class="qa-history-panel" aria-label="历史问答">
          <div class="panel-heading">
            <div>
              <p class="panel__eyebrow">QA Memory</p>
              <h3>历史问答</h3>
            </div>
            <el-button :icon="Search" plain :loading="historyLoading" @click="fetchQaHistory">
              刷新
            </el-button>
          </div>
          <div class="qa-record-list" v-if="qaRecords.length" v-loading="historyLoading">
            <button
              v-for="(record, index) in qaRecords"
              :key="recordKey(record, index)"
              type="button"
              @click="applyAnswer(record)"
            >
              <span>{{ record.question }}</span>
              <small>{{ record.references?.length ?? 0 }} 条引用</small>
            </button>
          </div>
          <el-empty v-else description="暂无历史问答记录" />
        </section>
      </div>
    </template>
  </section>
</template>
