<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ChatLineRound, Reading, Search } from '@element-plus/icons-vue'

import { listCategories, listLiteratures, markLiteratureRead } from '@/api/literature'
import { displayCategoryName } from '@/utils/format'
import type { Id, Literature, LiteratureCategory } from '@/types/literature'

const demoGuides: Literature[] = [
  {
    id: '1200000000000000002',
    title: 'Semantic Retrieval Method for Academic Literature Based on User Intent',
    author: 'Wang Qian',
    source: 'Modern Information',
    publishYear: 2023,
    categoryName: 'Information Retrieval',
    uploaderId: '1000000000000000003',
    visibility: 'PUBLIC',
    status: 'PUBLISHED',
    abstractText:
      'This study analyzes natural language search intent and improves academic literature discovery through structured retrieval conditions.',
    aiSummary:
      '适合指导学生拆解检索意图：把口语化问题转换为主题词、方法词和约束词，再映射到系统检索条件。',
    keywords: 'semantic retrieval,natural language,academic literature,user intent',
    readCount: 28,
    downloadCount: 5,
  },
  {
    id: '1200000000000000001',
    title: 'Research on Intelligent Recommendation Service in University Digital Library',
    author: 'Li Ming',
    source: 'Library Information Service',
    publishYear: 2024,
    categoryName: 'Recommendation System',
    uploaderId: '1000000000000000003',
    visibility: 'PUBLIC',
    status: 'PUBLISHED',
    abstractText:
      'This paper discusses personalized resource recommendation for university digital libraries and proposes a hybrid method based on keywords and user behavior.',
    aiSummary:
      '适合用于“推荐系统如何进入数字图书馆场景”的阅读讲解，可引导学生对比内容推荐、协同过滤与混合推荐。',
    keywords: 'digital library,recommendation system,user behavior,personalization',
    readCount: 36,
    downloadCount: 8,
  },
]

const categories = ref<LiteratureCategory[]>([])
const literatures = ref<Literature[]>([])
const selected = ref<Literature | null>(null)
const loading = ref(false)
const guideDraft = ref('')

const query = reactive({
  page: 1,
  size: 8,
  keyword: '',
  categoryId: undefined as Id | undefined,
  status: 'PUBLISHED' as const,
  visibility: 'PUBLIC' as const,
})

const selectedKeywords = computed(() =>
  (selected.value?.keywords ?? '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean),
)

async function fetchCategories() {
  try {
    categories.value = await listCategories()
  } catch (error) {
    console.error('teacher category load failed, scenario=teacher-category-list', error)
  }
}

async function fetchLiteratures() {
  loading.value = true
  try {
    const result = await listLiteratures(query)
    literatures.value = result.items
    selected.value = result.items[0] ?? null
  } catch (error) {
    console.error(
      'teacher guide list failed, scenario=teacher-literature-guidance, keyword=%s, categoryId=%s',
      query.keyword,
      query.categoryId,
      error,
    )
    literatures.value = demoGuides
    selected.value = selected.value ?? demoGuides[0]
  } finally {
    loading.value = false
  }
}

async function selectLiterature(item: Literature) {
  selected.value = item
  guideDraft.value = ''
  try {
    selected.value = await markLiteratureRead(item.id)
  } catch (error) {
    console.error(
      'teacher read mark failed, scenario=teacher-literature-read, literatureId=%s',
      item.id,
      error,
    )
  }
}

onMounted(async () => {
  await fetchCategories()
  await fetchLiteratures()
})
</script>

<template>
  <section class="teacher-page">
    <div class="spatial-hero compact-hero">
      <div>
        <p class="panel__eyebrow">Reading Coach</p>
        <h2>把论文变成可讲、可问、可追踪的阅读任务</h2>
        <p>
          教师侧聚焦公开文献的阅读指导：筛选适合学生的论文，整理提纲、追问和批注建议。
        </p>
      </div>
      <div class="hero-stats" aria-label="阅读指导统计">
        <span><strong>{{ literatures.length }}</strong>篇候选</span>
        <span><strong>{{ categories.length }}</strong>个方向</span>
      </div>
    </div>

    <div class="teacher-layout">
      <aside class="glass-panel search-panel">
        <div class="panel-heading">
          <div>
            <p class="panel__eyebrow">Guidance Filter</p>
            <h3>指导筛选</h3>
          </div>
        </div>
        <el-form label-position="top">
          <el-form-item label="研究主题">
            <el-input
              v-model="query.keyword"
              :prefix-icon="Search"
              clearable
              placeholder="检索题目、作者、关键词"
              @keyup.enter="fetchLiteratures"
            />
          </el-form-item>
          <el-form-item label="分类方向">
            <el-select v-model="query.categoryId" clearable placeholder="全部方向">
              <el-option
                v-for="category in categories"
                :key="category.id"
                :label="displayCategoryName(category.name)"
                :value="category.id"
              />
            </el-select>
          </el-form-item>
          <el-button type="primary" class="wide-action" :loading="loading" @click="fetchLiteratures">
            筛选文献
          </el-button>
        </el-form>

        <div class="guidance-list">
          <button
            v-for="item in literatures"
            :key="item.id"
            class="guidance-item"
            :class="{ 'is-active': selected?.id === item.id }"
            @click="selectLiterature(item)"
          >
            <span>{{ displayCategoryName(item.categoryName) }}</span>
            <strong>{{ item.title }}</strong>
          </button>
        </div>
      </aside>

      <section class="reader-surface coach-surface">
        <template v-if="selected">
          <article class="reader-paper">
            <p class="panel__eyebrow">{{ selected.source || 'Teaching Material' }}</p>
            <h2>{{ selected.title }}</h2>
            <div class="reader-meta">
              <span>{{ selected.author || '未知作者' }}</span>
              <span>{{ selected.publishYear || '年份未知' }}</span>
              <span>阅读 {{ selected.readCount || 0 }} 次</span>
            </div>

            <section class="reader-section">
              <h3>教师导读</h3>
              <p>
                {{
                  selected.aiSummary ||
                  '建议先带学生确认研究问题，再拆解方法、数据、实验指标与可复用模块。'
                }}
              </p>
            </section>

            <div class="reading-grid">
              <div>
                <h3>课堂问题</h3>
                <ol>
                  <li>作者解决的真实用户痛点是什么？</li>
                  <li>论文的方法能否转化为系统模块？</li>
                  <li>实验设计是否支撑论文结论？</li>
                  <li>学生毕设中可以复用哪一段思路？</li>
                </ol>
              </div>
              <div>
                <h3>批注维度</h3>
                <ol>
                  <li>概念定义是否清楚</li>
                  <li>方法流程是否可画成图</li>
                  <li>数据来源是否可靠</li>
                  <li>不足与改进空间</li>
                </ol>
              </div>
            </div>

            <section class="keyword-ribbon" v-if="selectedKeywords.length">
              <span v-for="token in selectedKeywords" :key="token">{{ token }}</span>
            </section>

            <el-input
              v-model="guideDraft"
              type="textarea"
              :rows="6"
              resize="none"
              placeholder="为学生整理导读意见、提问清单或阅读任务"
            />
          </article>
        </template>
        <el-empty v-else description="选择一篇公开文献生成导读" />
      </section>

      <aside class="glass-panel coach-panel">
        <div class="panel-heading">
          <div>
            <p class="panel__eyebrow">AI Prompt Pack</p>
            <h3>阅读助手提示词</h3>
          </div>
          <el-icon><ChatLineRound /></el-icon>
        </div>
        <div class="prompt-stack">
          <button>
            <Reading />
            <span>总结论文的研究问题、方法与结论</span>
          </button>
          <button>
            <ChatLineRound />
            <span>生成 5 个适合课堂讨论的问题</span>
          </button>
          <button>
            <Reading />
            <span>把论文摘要改写成学生可理解版本</span>
          </button>
        </div>
      </aside>
    </div>
  </section>
</template>
