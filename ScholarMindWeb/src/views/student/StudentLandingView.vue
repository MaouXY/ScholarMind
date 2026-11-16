<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Promotion, Search } from '@element-plus/icons-vue'

import '@/styles/landing.css'
import {
  SEARCH_FIELD_OPTIONS,
  type LiteratureSearchField,
} from '@/types/literature'

const router = useRouter()

const query = reactive({
  keyword: '',
  searchField: 'theme' as LiteratureSearchField,
})

/** 智能问答（RAG）是独立层级：默认收起，点大按钮再展开对话式输入 */
const ragOpen = ref(false)
const ragQuery = ref('')
const ragMode = ref<'natural' | 'hybrid'>('natural')

function submitSearch() {
  const keyword = query.keyword.trim()
  if (!keyword) {
    ElMessage.warning('请输入检索词')
    return
  }
  router.push({
    path: '/student/search',
    query: { keyword, searchField: query.searchField },
  })
}

function toggleRag() {
  ragOpen.value = !ragOpen.value
}

function submitRagSearch() {
  const text = ragQuery.value.trim()
  if (!text) {
    ElMessage.warning('请描述你要找的内容')
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
          支持主题、关键词、全文、作者、摘要、参考文献与文献来源七种检索维度。
        </p>
      </header>

      <form class="landing-search" @submit.prevent="submitSearch">
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
          native-type="submit"
        >
          检索
        </el-button>
      </form>

      <!-- RAG 单独一个层级：显眼的大按钮，点开才展开对话式输入 -->
      <div class="landing-rag">
        <p class="landing-rag__divider"><span>或</span></p>
        <button
          type="button"
          class="landing-rag__trigger"
          :class="{ 'is-open': ragOpen }"
          :aria-expanded="ragOpen"
          @click="toggleRag"
        >
          <el-icon class="landing-rag__icon"><ChatDotRound /></el-icon>
          <span class="landing-rag__label">
            <strong>智能问答检索</strong>
            <em>用一句话描述需求，向量 + BM25 召回，RRF 融合排序</em>
          </span>
          <el-icon class="landing-rag__caret"><Promotion /></el-icon>
        </button>

        <transition name="landing-rag">
          <div v-if="ragOpen" class="landing-rag__panel">
            <el-input
              v-model="ragQuery"
              type="textarea"
              :rows="4"
              resize="none"
              placeholder="例如：想找把关键词检索和向量检索融合起来、并用 RRF 排序的中文论文"
              @keyup.ctrl.enter="submitRagSearch"
            />
            <div class="landing-rag__footer">
              <el-segmented
                v-model="ragMode"
                :options="[
                  { label: '自然语言', value: 'natural' },
                  { label: '混合检索', value: 'hybrid' },
                ]"
              />
              <el-button type="primary" @click="submitRagSearch">
                开始智能检索
              </el-button>
            </div>
          </div>
        </transition>
      </div>
    </div>
  </section>
</template>
