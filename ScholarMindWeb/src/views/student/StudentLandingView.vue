<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Search } from '@element-plus/icons-vue'

import '@/styles/landing.css'
import {
  SEARCH_FIELD_OPTIONS,
  type LiteratureSearchField,
} from '@/types/literature'

const router = useRouter()

/** 一个输入框两种用法：关键词检索走题录匹配，智能问答走 BM25 + 向量 + RRF */
type LandingMode = 'keyword' | 'rag'

const mode = ref<LandingMode>('keyword')

const query = reactive({
  text: '',
  searchField: 'theme' as LiteratureSearchField,
})

/** 智能问答的召回方式：自然语言意图解析 / 混合检索 */
const ragMode = ref<'natural' | 'hybrid'>('natural')

const placeholder = computed(() =>
  mode.value === 'keyword'
    ? '输入关键词，如：混合检索、知识图谱、RRF 融合'
    : '用一句话描述需求，如：想找把关键词与向量检索融合、用 RRF 排序的中文论文',
)

const hint = computed(() =>
  mode.value === 'keyword'
    ? '按所选维度匹配题录字段，结果页可继续按学科、年度与文献类别筛选'
    : '向量 + BM25 双路召回，RRF 融合排序，结果附引用片段',
)

const submitLabel = computed(() =>
  mode.value === 'keyword' ? '检索' : '智能检索',
)

function submit() {
  const text = query.text.trim()
  if (!text) {
    ElMessage.warning(
      mode.value === 'keyword' ? '请输入检索词' : '请描述你要找的内容',
    )
    return
  }

  if (mode.value === 'keyword') {
    router.push({
      path: '/student/search',
      query: { keyword: text, searchField: query.searchField },
    })
    return
  }

  router.push({
    path: '/student/search',
    query: { rag: text, mode: ragMode.value },
  })
}
</script>

<template>
  <section class="landing-page">
    <div class="landing-stage">
      <header class="landing-hero">
        <p class="landing-hero__eyebrow">学术文献智能检索</p>
        <h1>从一个问题，找到该读的那篇文献</h1>
        <p class="landing-hero__text">
          一个输入框两种用法：按维度检索题录，或用一句自然语言让向量与关键词一起找。
        </p>
      </header>

      <form class="landing-search" @submit.prevent="submit">
        <el-select
          v-model="mode"
          class="landing-search__mode"
          aria-label="检索方式"
        >
          <el-option label="文献检索" value="keyword" />
          <el-option label="智能问答" value="rag" />
        </el-select>

        <el-select
          v-if="mode === 'keyword'"
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
        <el-select
          v-else
          v-model="ragMode"
          class="landing-search__field"
          aria-label="召回方式"
        >
          <el-option label="自然语言" value="natural" />
          <el-option label="混合检索" value="hybrid" />
        </el-select>

        <el-input
          v-model="query.text"
          class="landing-search__input"
          size="large"
          clearable
          :placeholder="placeholder"
          @keyup.enter="submit"
        >
          <template #prefix>
            <el-icon>
              <ChatDotRound v-if="mode === 'rag'" />
              <Search v-else />
            </el-icon>
          </template>
        </el-input>

        <el-button
          type="primary"
          size="large"
          class="landing-search__submit"
          native-type="submit"
        >
          {{ submitLabel }}
        </el-button>
      </form>

      <p class="landing-search__hint">{{ hint }}</p>
    </div>
  </section>
</template>
