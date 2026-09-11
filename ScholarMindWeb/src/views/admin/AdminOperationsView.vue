<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Refresh, TrendCharts } from '@element-plus/icons-vue'

import {
  generateAdminOperationReport,
  getAdminDashboard,
  listAdminKeywords,
} from '@/api/admin'
import '@/styles/admin-ext.css'
import type {
  AdminDashboard,
  AdminKeywordStat,
  AdminOperationReport,
} from '@/types/admin'

const dashboard = ref<AdminDashboard | null>(null)
const keywords = ref<AdminKeywordStat[]>([])
const report = ref<AdminOperationReport | null>(null)
const loadingOverview = ref(false)
const loadingKeywords = ref(false)
const generating = ref(false)
const keywordDays = ref(30)
const reportDays = ref(30)

const keywordDayOptions = [
  { label: '7 天', value: 7 },
  { label: '30 天', value: 30 },
  { label: '90 天', value: 90 },
]

const overviewCards = computed(() => {
  const item = dashboard.value?.overview
  return [
    { label: '用户总数', value: item?.userTotal ?? 0 },
    { label: '文献总量', value: item?.literatureTotal ?? 0 },
    { label: '问答记录', value: item?.qaTotal ?? 0 },
    { label: '收藏总数', value: item?.favoriteTotal ?? 0 },
  ]
})

const aiTaskHealth = computed(() => dashboard.value?.aiTaskHealth ?? [])

const keywordMax = computed(() =>
  Math.max(1, ...keywords.value.map((item) => item.total ?? 0)),
)

const keywordTotals = computed(() =>
  keywords.value.reduce(
    (sum, item) => ({
      literatureCount: sum.literatureCount + (item.literatureCount ?? 0),
      searchCount: sum.searchCount + (item.searchCount ?? 0),
      qaCount: sum.qaCount + (item.qaCount ?? 0),
    }),
    { literatureCount: 0, searchCount: 0, qaCount: 0 },
  ),
)

const reportHighlights = computed(() => report.value?.highlights ?? [])
const reportRisks = computed(() => report.value?.risks ?? [])
const reportSuggestions = computed(() => report.value?.suggestions ?? [])
const reportSections = computed(() => report.value?.sections ?? [])

function formatDateTime(value?: string) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

function keywordPercent(item: AdminKeywordStat) {
  return Math.round(((item.total ?? 0) / keywordMax.value) * 100)
}

function buildReportText(item: AdminOperationReport) {
  const lines: string[] = []
  lines.push(`ScholarMind 运营分析报告（近 ${item.days} 天）`)
  lines.push(
    `生成时间：${formatDateTime(item.generatedAt)}｜数据来源：${
      item.fallbackUsed ? '本地兜底' : 'AI 生成'
    }`,
  )
  lines.push('')
  lines.push('一、运营摘要')
  lines.push(item.summary || '暂无摘要')
  lines.push('')
  lines.push('二、数据亮点')
  const highlights = item.highlights ?? []
  if (highlights.length) highlights.forEach((text) => lines.push(`- ${text}`))
  else lines.push('- 暂无')
  lines.push('')
  lines.push('三、风险提示')
  const risks = item.risks ?? []
  if (risks.length) risks.forEach((text) => lines.push(`- ${text}`))
  else lines.push('- 暂无')
  lines.push('')
  lines.push('四、优化建议')
  const suggestions = item.suggestions ?? []
  if (suggestions.length) suggestions.forEach((text) => lines.push(`- ${text}`))
  else lines.push('- 暂无')
  ;(item.sections ?? []).forEach((section) => {
    lines.push('')
    lines.push(section.title)
    lines.push(section.content)
  })
  return lines.join('\n')
}

async function fetchOverview() {
  loadingOverview.value = true
  try {
    dashboard.value = await getAdminDashboard({ days: 30, limit: 6 })
  } catch (error) {
    console.error(
      'operation overview load failed, scenario=admin-operation-overview, days=%s, limit=%s',
      30,
      6,
      error,
    )
    dashboard.value = null
  } finally {
    loadingOverview.value = false
  }
}

async function fetchKeywords() {
  loadingKeywords.value = true
  try {
    const result = await listAdminKeywords({ limit: 20, days: keywordDays.value })
    keywords.value = result ?? []
  } catch (error) {
    console.error(
      'operation keyword load failed, scenario=admin-operation-keywords, limit=%s, days=%s',
      20,
      keywordDays.value,
      error,
    )
    keywords.value = []
  } finally {
    loadingKeywords.value = false
  }
}

async function generateReport() {
  generating.value = true
  try {
    report.value = await generateAdminOperationReport({ days: reportDays.value })
    ElMessage.success('运营分析报告已生成')
  } catch (error) {
    console.error(
      'operation report generate failed, scenario=admin-operation-report-generate, days=%s',
      reportDays.value,
      error,
    )
    report.value = null
  } finally {
    generating.value = false
  }
}

async function copyReport() {
  const current = report.value
  if (!current) {
    ElMessage.warning('请先生成运营分析报告')
    return
  }

  if (!navigator.clipboard?.writeText) {
    console.error(
      'operation report copy unsupported, scenario=admin-operation-report-copy, hasClipboard=false',
    )
    ElMessage.warning('当前浏览器不支持自动复制，请手动选择报告内容')
    return
  }

  try {
    await navigator.clipboard.writeText(buildReportText(current))
    ElMessage.success('报告内容已复制到剪贴板')
  } catch (error) {
    console.error(
      'operation report copy failed, scenario=admin-operation-report-copy, generatedAt=%s',
      current.generatedAt,
      error,
    )
    ElMessage.warning('复制失败，请手动选择报告内容')
  }
}

onMounted(() => {
  void Promise.all([fetchOverview(), fetchKeywords()])
})
</script>

<template>
  <section class="admin-page">
    <div class="spatial-hero compact-hero">
      <div>
        <p class="panel__eyebrow">Operation Insight</p>
        <h2>用关键词热度和运营报告判断平台走势</h2>
        <p>
          运营分析页汇总热门关键词的文献标签、检索与问答热度，并支持一键生成包含亮点、风险与建议的运营分析报告，便于汇报与复盘。
        </p>
      </div>
      <div class="hero-stats">
        <span>
          <strong>{{ dashboard?.overview.userTotal ?? 0 }}</strong>平台用户
        </span>
        <span>
          <strong>{{ dashboard?.overview.literatureTotal ?? 0 }}</strong>文献总量
        </span>
      </div>
    </div>

    <div class="admin-insight-grid">
      <section class="glass-panel chart-panel" v-loading="loadingOverview">
        <div class="panel-heading">
          <div>
            <p class="panel__eyebrow">Platform Overview</p>
            <h3>平台概览</h3>
          </div>
          <el-button
            :icon="Refresh"
            plain
            :loading="loadingOverview"
            @click="fetchOverview"
          >
            刷新
          </el-button>
        </div>
        <div class="admin-signal-grid" v-if="dashboard">
          <span v-for="item in overviewCards" :key="item.label">
            <strong>{{ item.value }}</strong>{{ item.label }}
          </span>
        </div>
        <el-empty v-else description="暂无平台概览数据" />
      </section>

      <section class="glass-panel chart-panel" v-loading="loadingOverview">
        <div class="panel-heading">
          <div>
            <p class="panel__eyebrow">AI Task Health</p>
            <h3>AI 任务健康度</h3>
          </div>
          <el-icon><TrendCharts /></el-icon>
        </div>
        <div class="ai-health-list" v-if="aiTaskHealth.length">
          <article v-for="item in aiTaskHealth" :key="item.taskType">
            <div>
              <strong>{{ item.taskType }}</strong>
              <span>{{ Math.round((item.successRate || 0) * 100) }}%</span>
            </div>
            <div class="progress-line">
              <span :style="{ width: `${Math.round((item.successRate || 0) * 100)}%` }" />
            </div>
            <small>
              总计 {{ item.total }} · 失败 {{ item.failed }} · 超时 {{ item.timeout }} · 平均
              {{ item.avgDurationMs }}ms
            </small>
          </article>
        </div>
        <el-empty v-else description="暂无 AI 任务统计" />
      </section>
    </div>

    <section class="glass-panel chart-panel">
      <div class="panel-heading">
        <div>
          <p class="panel__eyebrow">Hot Keywords</p>
          <h3>热门关键词</h3>
        </div>
        <div class="admin-filter-form">
          <el-segmented v-model="keywordDays" :options="keywordDayOptions" />
          <el-button
            :icon="Refresh"
            plain
            :loading="loadingKeywords"
            @click="fetchKeywords"
          >
            刷新
          </el-button>
        </div>
      </div>

      <div class="dimension-pill-list" v-if="keywords.length">
        <span>
          <strong>{{ keywordTotals.literatureCount }}</strong>文献标签
        </span>
        <span>
          <strong>{{ keywordTotals.searchCount }}</strong>检索
        </span>
        <span>
          <strong>{{ keywordTotals.qaCount }}</strong>问答
        </span>
      </div>

      <div class="ai-health-list" v-if="keywords.length">
        <article v-for="(item, index) in keywords" :key="item.keyword">
          <div>
            <strong>{{ index + 1 }}. {{ item.keyword }}</strong>
            <span>总计 {{ item.total ?? 0 }}</span>
          </div>
          <div class="progress-line">
            <span :style="{ width: `${keywordPercent(item)}%` }" />
          </div>
          <small>
            文献标签 {{ item.literatureCount ?? 0 }} · 检索 {{ item.searchCount ?? 0 }} · 问答
            {{ item.qaCount ?? 0 }}
          </small>
        </article>
      </div>
      <el-empty v-else-if="!loadingKeywords" description="暂无关键词统计数据" />
    </section>

    <section class="glass-panel chart-panel">
      <div class="panel-heading">
        <div>
          <p class="panel__eyebrow">Operation Report</p>
          <h3>运营分析报告</h3>
        </div>
        <div class="admin-filter-form">
          <el-segmented v-model="reportDays" :options="keywordDayOptions" />
          <el-button
            :icon="Document"
            type="primary"
            :loading="generating"
            @click="generateReport"
          >
            生成运营分析报告
          </el-button>
          <el-button plain :disabled="!report" @click="copyReport">
            复制报告
          </el-button>
        </div>
      </div>

      <template v-if="report">
        <div class="paper-card__meta">
          <el-tag :type="report.fallbackUsed ? 'warning' : 'success'" effect="light">
            {{ report.fallbackUsed ? '本地兜底' : 'AI 生成' }}
          </el-tag>
          <span>统计窗口 {{ report.days }} 天</span>
          <span>生成时间 {{ formatDateTime(report.generatedAt) }}</span>
        </div>

        <div class="admin-report-stack">
          <section class="admin-report-section">
            <h4>运营摘要</h4>
            <p>{{ report.summary || '暂无摘要' }}</p>
          </section>

          <section class="admin-report-section">
            <h4>数据亮点</h4>
            <ul class="admin-report-list" v-if="reportHighlights.length">
              <li v-for="(text, index) in reportHighlights" :key="`highlight-${index}`">
                {{ text }}
              </li>
            </ul>
            <el-empty v-else description="暂无亮点数据" />
          </section>

          <section class="admin-report-section">
            <h4>风险提示</h4>
            <ul class="admin-report-list" v-if="reportRisks.length">
              <li v-for="(text, index) in reportRisks" :key="`risk-${index}`">
                {{ text }}
              </li>
            </ul>
            <el-empty v-else description="暂无风险提示" />
          </section>

          <section class="admin-report-section">
            <h4>优化建议</h4>
            <ul class="admin-report-list" v-if="reportSuggestions.length">
              <li v-for="(text, index) in reportSuggestions" :key="`suggestion-${index}`">
                {{ text }}
              </li>
            </ul>
            <el-empty v-else description="暂无优化建议" />
          </section>

          <section
            class="admin-report-section"
            v-for="(section, index) in reportSections"
            :key="`section-${index}`"
          >
            <h4>{{ section.title }}</h4>
            <p>{{ section.content }}</p>
          </section>
        </div>
      </template>
      <el-empty v-else description="尚未生成运营分析报告，点击右上角按钮开始生成" />
    </section>
  </section>
</template>
