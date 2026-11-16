<script setup lang="ts">
import { computed } from 'vue'

import { parseAiSummary } from '@/utils/summary'

/**
 * AI 摘要展示：后端存的是结构化 JSON（背景/问题/方法/结论/创新点/适合读者 + 关键词），
 * 这里解析成分节排版；历史纯文本摘要自动退化为段落渲染。
 */
const props = defineProps<{
  summary?: string | null
  /** 详情页用 compact，阅读页用 full（字号更贴近正文） */
  density?: 'compact' | 'full'
}>()

const parsed = computed(() => parseAiSummary(props.summary))
</script>

<template>
  <div class="ai-summary" :class="`ai-summary--${density ?? 'compact'}`">
    <template v-if="parsed.structured">
      <dl class="ai-summary__sections">
        <div
          v-for="section in parsed.sections"
          :key="section.key"
          class="ai-summary__section"
        >
          <dt>{{ section.label }}</dt>
          <dd>{{ section.content }}</dd>
        </div>
      </dl>
      <div v-if="parsed.keywords.length" class="ai-summary__keywords">
        <span class="ai-summary__keywords-caption">主题词</span>
        <span v-for="keyword in parsed.keywords" :key="keyword">
          {{ keyword }}
        </span>
      </div>
    </template>
    <p v-else-if="parsed.fallbackText" class="ai-summary__fallback">
      {{ parsed.fallbackText }}
    </p>
    <p v-else class="ai-summary__empty">暂无 AI 摘要</p>
  </div>
</template>

<style scoped>
.ai-summary__sections {
  display: grid;
  gap: 12px;
  margin: 0;
}

.ai-summary__section {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
}

.ai-summary__section dt {
  color: #2161b5;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.9;
  letter-spacing: 0.02em;
}

.ai-summary__section dd {
  margin: 0;
  color: #2c3947;
  font-size: 14px;
  line-height: 1.9;
  text-align: justify;
}

.ai-summary__keywords {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--line);
}

.ai-summary__keywords-caption {
  margin-right: 2px;
  color: var(--color-subtle);
  font-size: 12px;
}

.ai-summary__keywords span:not(.ai-summary__keywords-caption) {
  padding: 2px 9px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: #f7f9fc;
  color: #33506f;
  font-size: 12px;
}

.ai-summary__fallback {
  margin: 0;
  color: #2c3947;
  font-size: 14px;
  line-height: 1.95;
  text-align: justify;
  white-space: pre-wrap;
}

.ai-summary__empty {
  margin: 0;
  color: var(--color-subtle);
  font-size: 13px;
}

/* 阅读页：与正文同字号、行长更宽 */
.ai-summary--full .ai-summary__section {
  grid-template-columns: 84px minmax(0, 1fr);
}

.ai-summary--full .ai-summary__section dd,
.ai-summary--full .ai-summary__fallback {
  font-size: 15px;
  line-height: 1.95;
}

@media (max-width: 760px) {
  .ai-summary__section,
  .ai-summary--full .ai-summary__section {
    grid-template-columns: minmax(0, 1fr);
    gap: 2px;
  }
}
</style>
