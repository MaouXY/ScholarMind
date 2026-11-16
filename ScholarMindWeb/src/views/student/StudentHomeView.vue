<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'

import {
  hybridSearch,
  listCategories,
  listLiteratures,
  naturalLanguageSearch,
} from '@/api/literature'
import { displayCategoryName, displayLiteratureType } from '@/utils/format'
import '@/styles/literature-ext.css'
import '@/styles/search-results.css'
import {
  LITERATURE_TYPE_OPTIONS,
  SEARCH_FIELD_OPTIONS,
  type Id,
  type Literature,
  type LiteratureCategory,
  type LiteratureSearchField,
  type LiteratureType,
} from '@/types/literature'

const route = useRoute()
const router = useRouter()

/** 年度筛选区间：2017-2026，与需求一致 */
const YEAR_FROM = 2017
const YEAR_TO = 2026
const years = Array.from(
  { length: YEAR_TO - YEAR_FROM + 1 },
  (_, index) => YEAR_TO - index,
)

const subjects = ref<LiteratureCategory[]>([])
const literatures = ref<Literature[]>([])
const total = ref(0)
const loading = ref(false)
const listFailed = ref(false)

/** 检索条件：URL query 是唯一真源，便于分享与前进后退 */
const filters = reactive({
  keyword: '',
  searchField: 'theme' as LiteratureSearchField,
  literatureType: undefined as LiteratureType | string | undefined,
  categoryId: undefined as Id | undefined,
  year: undefined as number | undefined,
  page: 1,
  size: 10,
})

/** RAG 检索结果（来自首页的自然语言 / 混合检索） */
const ragQuery = ref('')
const ragMode = ref<'natural' | 'hybrid'>('natural')
const ragReferences = ref(0)

const isRagMode = computed(() => Boolean(ragQuery.value))

const activeSubjectName = computed(() => {
  const matched = subjects.value.find((item) => item.id === filters.categoryId)
  return matched ? displayCategoryName(matched.name) : ''
})

const resultSummary = computed(() => {
  if (isRagMode.value) {
    return `智能检索「${ragQuery.value}」命中 ${literatures.value.length} 篇`
  }
  if (loading.value) return '正在检索…'
  return `命中 ${total.value} 篇`
})

async function fetchCategories() {
  try {
    const categories = await listCategories()
    subjects.value = categories.filter((item) => !item.parentId)
  } catch (error) {
    console.error(
      'category load failed, scenario=literature-category-list',
      error,
    )
    subjects.value = []
  }
}

/** 把 URL query 同步进本地筛选状态 */
function syncFiltersFromRoute() {
  const query = route.query
  filters.keyword = typeof query.keyword === 'string' ? query.keyword : ''
  filters.searchField =
    typeof query.searchField === 'string'
      ? (query.searchField as LiteratureSearchField)
      : 'theme'
  filters.literatureType =
    typeof query.literatureType === 'string' ? query.literatureType : undefined
  filters.year =
    typeof query.year === 'string' ? Number(query.year) || undefined : undefined
  filters.page = typeof query.page === 'string' ? Number(query.page) || 1 : 1
  ragQuery.value = typeof query.rag === 'string' ? query.rag : ''
  ragMode.value = query.mode === 'hybrid' ? 'hybrid' : 'natural'

  // 学科按名称传参（首页卡片只掌握名称），这里反查 id
  const subject = typeof query.subject === 'string' ? query.subject : ''
  filters.categoryId = subject
    ? subjects.value.find((item) => displayCategoryName(item.name) === subject)?.id
    : undefined
}

/** 学科反查依赖分类已加载，两者都就绪后再查库 */
async function loadResults() {
  if (ragQuery.value) {
    await runRagSearch()
    return
  }
  loading.value = true
  listFailed.value = false
  ragReferences.value = 0
  try {
    const result = await listLiteratures({
      page: filters.page,
      size: filters.size,
      keyword: filters.keyword || undefined,
      searchField: filters.keyword ? filters.searchField : undefined,
      categoryId: filters.categoryId,
      literatureType: filters.literatureType,
      yearFrom: filters.year,
      yearTo: filters.year,
    })
    literatures.value = result.items
    total.value = result.total
  } catch (error) {
    console.error(
      'literature list failed, scenario=literature-search, keyword=%s, searchField=%s, literatureType=%s, categoryId=%s, year=%s',
      filters.keyword,
      filters.searchField,
      filters.literatureType,
      filters.categoryId,
      filters.year,
      error,
    )
    listFailed.value = true
    literatures.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function runRagSearch() {
  loading.value = true
  listFailed.value = false
  try {
    const payload = {
      query: ragQuery.value,
      categoryId: filters.categoryId,
      limit: 12,
    }
    const result =
      ragMode.value === 'hybrid'
        ? await hybridSearch(payload)
        : await naturalLanguageSearch(payload)
    literatures.value = result.literatures
    total.value = result.literatures.length
    ragReferences.value = result.references.length
  } catch (error) {
    console.error(
      'rag search failed, scenario=literature-rag-search, mode=%s, query=%s, categoryId=%s',
      ragMode.value,
      ragQuery.value,
      filters.categoryId,
      error,
    )
    listFailed.value = true
    literatures.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 条件变更统一走路由，watch(route.query) 负责触发查询 */
function applyFilters(patch: Record<string, string | number | undefined>) {
  const nextQuery: Record<string, string | number> = {}
  const merged = {
    keyword: filters.keyword || undefined,
    searchField: filters.keyword ? filters.searchField : undefined,
    literatureType: filters.literatureType,
    subject: activeSubjectName.value || undefined,
    year: filters.year,
    rag: ragQuery.value || undefined,
    mode: ragQuery.value ? ragMode.value : undefined,
    page: filters.page > 1 ? filters.page : undefined,
    ...patch,
  }
  Object.entries(merged).forEach(([key, value]) => {
    if (value !== undefined && value !== '') nextQuery[key] = value as string | number
  })
  // 相同条件重复点击会产生冗余导航，忽略即可，不当作错误
  router.push({ path: '/student/search', query: nextQuery }).catch(() => {})
}

function submitKeyword() {
  applyFilters({ page: undefined })
}

function selectType(type?: LiteratureType | string) {
  applyFilters({ literatureType: type, page: undefined })
}

function selectSubject(categoryId?: Id) {
  filters.categoryId = categoryId
  const name = subjects.value.find((item) => item.id === categoryId)?.name
  applyFilters({
    subject: name ? displayCategoryName(name) : undefined,
    page: undefined,
  })
}

function selectYear(year?: number) {
  applyFilters({ year, page: undefined })
}

function clearAll() {
  router.push({ path: '/student/search' }).catch(() => {})
}

function goPage(page: number) {
  applyFilters({ page })
}

function openDetail(item: Literature) {
  router.push(`/student/literature/${item.id}`)
}

function formatDate(value?: string | null) {
  return value || '未标注'
}

onMounted(async () => {
  await fetchCategories()
  syncFiltersFromRoute()
  await loadResults()
})

watch(
  () => route.query,
  async () => {
    syncFiltersFromRoute()
    await loadResults()
  },
)
</script>

<template>
  <section class="library-page">
    <div class="search-results-layout">
      <aside class="glass-panel search-panel" aria-label="辅助分类">
        <div class="panel-heading">
          <div>
            <p class="panel__eyebrow">辅助分类</p>
            <h3>学科</h3>
          </div>
        </div>
        <div class="filter-chip-stack">
          <button
            type="button"
            class="filter-chip"
            :class="{ 'is-active': !filters.categoryId }"
            @click="selectSubject(undefined)"
          >
            全部学科
          </button>
          <button
            v-for="item in subjects"
            :key="item.id"
            type="button"
            class="filter-chip"
            :class="{ 'is-active': filters.categoryId === item.id }"
            @click="selectSubject(item.id)"
          >
            {{ displayCategoryName(item.name) }}
          </button>
        </div>

        <div class="panel-heading filter-heading">
          <div>
            <h3>年度</h3>
          </div>
        </div>
        <div class="filter-chip-stack filter-chip-stack--year">
          <button
            type="button"
            class="filter-chip"
            :class="{ 'is-active': !filters.year }"
            @click="selectYear(undefined)"
          >
            全部年度
          </button>
          <button
            v-for="year in years"
            :key="year"
            type="button"
            class="filter-chip"
            :class="{ 'is-active': filters.year === year }"
            @click="selectYear(year)"
          >
            {{ year }}
          </button>
        </div>

        <el-button class="wide-action clear-action" plain @click="clearAll">
          清空筛选
        </el-button>
      </aside>

      <div class="results-column">
        <section class="glass-panel type-bar" aria-label="文献类别">
          <button
            type="button"
            class="type-tab"
            :class="{ 'is-active': !filters.literatureType }"
            @click="selectType(undefined)"
          >
            全部
          </button>
          <button
            v-for="item in LITERATURE_TYPE_OPTIONS"
            :key="item"
            type="button"
            class="type-tab"
            :class="{ 'is-active': filters.literatureType === item }"
            @click="selectType(item)"
          >
            {{ item }}
          </button>
        </section>

        <section class="glass-panel search-bar-panel" aria-label="检索">
          <el-select v-model="filters.searchField" class="search-bar__field" aria-label="检索维度">
            <el-option
              v-for="field in SEARCH_FIELD_OPTIONS"
              :key="field.value"
              :label="field.label"
              :value="field.value"
            />
          </el-select>
          <el-input
            v-model="filters.keyword"
            class="search-bar__input"
            clearable
            placeholder="在结果中检索"
            @keyup.enter="submitKeyword"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" @click="submitKeyword">检索</el-button>
        </section>

        <section
          class="result-stack"
          aria-label="检索结果"
          v-loading="loading"
        >
          <div class="results-heading">
            <h2>
              {{ resultSummary }}
              <small v-if="activeSubjectName">· {{ activeSubjectName }}</small>
              <small v-if="filters.year">· {{ filters.year }} 年</small>
              <small v-if="!isRagMode && ragReferences">· 引用片段 {{ ragReferences }} 条</small>
            </h2>
          </div>

          <el-empty
            v-if="listFailed"
            description="检索失败，请稍后重试"
          />

          <article
            v-for="item in literatures"
            v-else
            :key="item.id"
            class="glass-panel result-row"
            @click="openDetail(item)"
          >
            <h3 class="result-row__title">{{ item.title }}</h3>
            <p class="result-row__authors">
              {{ item.author || '作者未标注' }}
            </p>
            <p class="result-row__source">
              {{ item.source || '来源未标注' }}
              <span class="result-row__divider">|</span>
              {{ displayLiteratureType(item.literatureType) }}
              <span class="result-row__divider">|</span>
              {{ formatDate(item.publishDate) }}
            </p>
            <div class="result-row__metrics">
              <span>被引 {{ item.citationCount ?? 0 }}</span>
              <span>下载 {{ item.downloadCount ?? 0 }}</span>
              <span>阅读 {{ item.readCount ?? 0 }}</span>
              <span>{{ displayCategoryName(item.categoryName) }}</span>
            </div>
          </article>

          <el-empty
            v-if="!loading && !listFailed && !literatures.length"
            description="没有匹配的文献，试试换关键词或放宽筛选"
          />

          <el-pagination
            v-if="!isRagMode && total > filters.size"
            class="results-pager"
            background
            layout="prev, pager, next"
            :current-page="filters.page"
            :page-size="filters.size"
            :total="total"
            @current-change="goPage"
          />
        </section>
      </div>
    </div>
  </section>
</template>
