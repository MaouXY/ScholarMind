<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Connection, Promotion, Search } from '@element-plus/icons-vue'

import { listCategories } from '@/api/literature'
import { displayCategoryName } from '@/utils/format'
import '@/styles/landing.css'
import {
  SEARCH_FIELD_OPTIONS,
  type LiteratureCategory,
  type LiteratureSearchField,
} from '@/types/literature'

const router = useRouter()

/** 一级学科（中图法门类）：parentId 为空即顶级分类 */
const subjects = ref<LiteratureCategory[]>([])
const loadingSubjects = ref(false)

const query = reactive({
  keyword: '',
  searchField: 'theme' as LiteratureSearchField,
})

const ragQuery = ref('')
const ragMode = ref<'natural' | 'hybrid'>('natural')

const activeSubject = ref<string>('')

async function fetchSubjects() {
  loadingSubjects.value = true
  try {
    const categories = await listCategories()
    subjects.value = categories.filter((item) => !item.parentId)
  } catch (error) {
    console.error(
      'landing subject load failed, scenario=literature-category-list',
      error,
    )
    subjects.value = []
  } finally {
    loadingSubjects.value = false
  }
}

/** 主检索框：带到结果页，由结果页负责真正查库 */
function submitSearch() {
  const keyword = query.keyword.trim()
  if (!keyword) {
    ElMessage.warning('请输入检索词')
    return
  }
  router.push({
    path: '/student/search',
    query: {
      keyword,
      searchField: query.searchField,
      subject: activeSubject.value || undefined,
    },
  })
}

/** 点学科卡片：直接按学科浏览，不强制带关键词 */
function browseSubject(name: string) {
  activeSubject.value = activeSubject.value === name ? '' : name
  router.push({
    path: '/student/search',
    query: { subject: activeSubject.value || undefined },
  })
}

/** RAG 检索：走语义/混合检索，结果页以智能检索模式展示 */
function submitRagSearch() {
  const text = ragQuery.value.trim()
  if (!text) {
    ElMessage.warning('请输入想检索的问题或描述')
    return
  }
  router.push({
    path: '/student/search',
    query: {
      rag: text,
      mode: ragMode.value,
      subject: activeSubject.value || undefined,
    },
  })
}

onMounted(fetchSubjects)
</script>

<template>
  <section class="landing-page">
    <header class="landing-hero">
      <p class="landing-hero__eyebrow">学术文献智能检索</p>
      <h2>从一个问题，找到该读的那篇文献</h2>
      <p class="landing-hero__text">
        支持主题、关键词、全文、作者、摘要、参考文献与文献来源七种检索维度，
        也可以直接用自然语言描述需求，交给混合检索按语义相关性排序。
      </p>

      <div class="landing-search">
        <el-select
          v-model="query.searchField"
          class="landing-search__field"
          aria-label="检索维度"
        >
          <el-option
            v-for="field in SEARCH_FIELD_OPTIONS"
            :key="field.value"
            :label="field.label"
            :value="field.value"
          />
        </el-select>
        <el-input
          v-model="query.keyword"
          class="landing-search__input"
          size="large"
          clearable
          placeholder="输入关键词，如：混合检索、知识图谱、RRF 融合"
          @keyup.enter="submitSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button
          type="primary"
          size="large"
          class="landing-search__submit"
          @click="submitSearch"
        >
          检索
        </el-button>
      </div>
    </header>

    <div class="landing-grid">
      <section class="glass-panel landing-panel" aria-label="分类检索">
        <div class="panel-heading">
          <div>
            <p class="panel__eyebrow">分类检索</p>
            <h3>按学科浏览</h3>
            <p class="panel-caption">
              采用中图法一级门类；点击学科直接进入结果页，再次点击取消筛选。
            </p>
          </div>
          <span class="landing-count">{{ subjects.length }} 个学科</span>
        </div>
        <div v-loading="loadingSubjects" class="subject-grid">
          <button
            v-for="item in subjects"
            :key="item.id"
            type="button"
            class="subject-chip"
            :class="{ 'is-active': activeSubject === displayCategoryName(item.name) }"
            @click="browseSubject(displayCategoryName(item.name))"
          >
            {{ displayCategoryName(item.name) }}
          </button>
          <p v-if="!loadingSubjects && !subjects.length" class="landing-empty">
            暂无学科分类，请先在管理端维护分类。
          </p>
        </div>
      </section>

      <section class="glass-panel landing-panel" aria-label="RAG 检索">
        <div class="panel-heading">
          <div>
            <p class="panel__eyebrow">RAG 检索</p>
            <h3>用一句话描述你要找的内容</h3>
            <p class="panel-caption">
              向量召回 + BM25 稀疏召回，再用 RRF 融合排序；适合「我想找……」这类模糊需求。
            </p>
          </div>
          <el-icon class="landing-panel__icon"><Connection /></el-icon>
        </div>
        <el-input
          v-model="ragQuery"
          type="textarea"
          :rows="4"
          resize="none"
          placeholder="例如：想找把关键词检索和向量检索融合起来、并用 RRF 排序的中文论文"
        />
        <div class="landing-rag-footer">
          <el-segmented
            v-model="ragMode"
            :options="[
              { label: '自然语言', value: 'natural' },
              { label: '混合检索', value: 'hybrid' },
            ]"
          />
          <el-button type="primary" plain @click="submitRagSearch">
            <el-icon><Promotion /></el-icon>
            智能检索
          </el-button>
        </div>
      </section>
    </div>
  </section>
</template>
