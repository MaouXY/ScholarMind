<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'

import { getAdminDashboard } from '@/api/admin'
import { localizeDimensionName } from '@/utils/format'
import type { AdminDashboard, AdminTrendPoint } from '@/types/admin'

const demoDashboard: AdminDashboard = {
  overview: {
    userTotal: 36,
    studentTotal: 28,
    teacherTotal: 7,
    activeUserTotal: 24,
    literatureTotal: 128,
    publishedLiteratureTotal: 94,
    pendingReviewTotal: 9,
    privateLiteratureTotal: 25,
    fileTotal: 116,
    fileSizeBytes: 1024 * 1024 * 384,
    favoriteTotal: 216,
    readingHistoryTotal: 346,
    readingNoteTotal: 142,
    qaTotal: 88,
    ragDocumentTotal: 18,
    aiTaskTotal: 126,
    aiTaskSuccessTotal: 116,
    aiTaskFailedTotal: 6,
    aiSuccessRate: 0.92,
    avgAiDurationMs: 1840,
  },
  trends: [
    { date: '06-01', newUsers: 5, newLiteratures: 8, readingEvents: 42, qaEvents: 9, aiTasks: 13 },
    { date: '06-02', newUsers: 3, newLiteratures: 6, readingEvents: 56, qaEvents: 12, aiTasks: 16 },
    { date: '06-03', newUsers: 7, newLiteratures: 9, readingEvents: 72, qaEvents: 18, aiTasks: 22 },
    { date: '06-04', newUsers: 4, newLiteratures: 5, readingEvents: 64, qaEvents: 15, aiTasks: 18 },
    { date: '06-05', newUsers: 9, newLiteratures: 11, readingEvents: 88, qaEvents: 24, aiTasks: 31 },
    { date: '06-06', newUsers: 2, newLiteratures: 4, readingEvents: 51, qaEvents: 10, aiTasks: 14 },
  ],
  literatureStatusStats: [
    { name: 'PUBLISHED', count: 94 },
    { name: 'PENDING_REVIEW', count: 9 },
    { name: 'DRAFT', count: 18 },
    { name: 'REJECTED', count: 7 },
  ],
  literatureCategoryStats: [
    { name: '信息检索', count: 36 },
    { name: '人工智能', count: 31 },
    { name: '数字图书馆', count: 26 },
    { name: '推荐系统', count: 22 },
  ],
  userRoleStats: [
    { name: 'STUDENT', count: 28 },
    { name: 'TEACHER', count: 7 },
    { name: 'ADMIN', count: 1 },
  ],
  hotLiteratures: [],
  activeUsers: [],
  aiTaskHealth: [
    { taskType: 'LITERATURE_QA', total: 88, success: 82, failed: 4, timeout: 2, successRate: 0.93, avgDurationMs: 1620 },
    { taskType: 'SUMMARY', total: 38, success: 34, failed: 2, timeout: 2, successRate: 0.89, avgDurationMs: 2100 },
  ],
}

const dashboard = ref<AdminDashboard>(demoDashboard)
const loading = ref(false)
const query = reactive({
  days: 14,
  limit: 8,
})

const overview = computed(() => dashboard.value.overview)

const kpis = computed(() => [
  { label: '公开文献', value: overview.value.publishedLiteratureTotal, hint: `总量 ${overview.value.literatureTotal}` },
  { label: '阅读事件', value: overview.value.readingHistoryTotal, hint: `${overview.value.readingNoteTotal} 条笔记` },
  { label: '待审核', value: overview.value.pendingReviewTotal, hint: '公开发布队列' },
])

const maxReading = computed(() =>
  Math.max(...dashboard.value.trends.map((item) => item.readingEvents || 0), 1),
)

const maxCategory = computed(() =>
  Math.max(...dashboard.value.literatureCategoryStats.map((item) => item.count || 0), 1),
)

function barHeight(item: AdminTrendPoint) {
  return Math.max(8, Math.round(((item.readingEvents || 0) / maxReading.value) * 100))
}

function percent(value: number, max: number) {
  return Math.max(4, Math.round((value / max) * 100))
}

async function fetchDashboard() {
  loading.value = true
  try {
    dashboard.value = await getAdminDashboard(query)
  } catch (error) {
    console.error(
      'analytics dashboard load failed, scenario=analytics-statistics-dashboard, days=%s, limit=%s',
      query.days,
      query.limit,
      error,
    )
    dashboard.value = demoDashboard
  } finally {
    loading.value = false
  }
}

onMounted(fetchDashboard)
</script>

<template>
  <section class="analytics-page">
    <div class="spatial-hero compact-hero">
      <div>
        <p class="panel__eyebrow">Stage 5 Analytics Overview</p>
        <h2>观察文献、阅读、RAG 与 AI 任务的运行状态</h2>
        <p>
          数据总览页从后台统计接口同步关键指标，帮助管理员快速判断文献增长、阅读活跃与内容沉淀情况。
        </p>
      </div>
    </div>

    <section class="admin-control-strip glass-panel">
      <div>
        <p class="panel__eyebrow">Data Window</p>
        <h3>统计窗口</h3>
      </div>
      <el-segmented
        v-model="query.days"
        :options="[
          { label: '7 天', value: 7 },
          { label: '14 天', value: 14 },
          { label: '30 天', value: 30 },
        ]"
      />
      <el-button :icon="Refresh" type="primary" :loading="loading" @click="fetchDashboard">
        刷新总览
      </el-button>
    </section>

    <div class="kpi-grid kpi-grid--three">
      <article v-for="item in kpis" :key="item.label" class="screen-kpi">
        <p>{{ item.label }}</p>
        <strong>{{ item.value }}</strong>
        <span>{{ item.hint }}</span>
      </article>
    </div>

    <div class="analytics-grid analytics-grid--stage5">
      <section class="glass-panel chart-panel">
        <div class="panel-heading">
          <div>
            <p class="panel__eyebrow">Reading Trend</p>
            <h3>阅读与 AI 趋势</h3>
          </div>
        </div>
        <div class="bar-chart" aria-label="阅读与 AI 趋势">
          <div v-for="item in dashboard.trends" :key="item.date" class="bar-chart__item">
            <span class="bar-chart__bar" :style="{ height: `${barHeight(item)}%` }" />
            <strong>{{ item.readingEvents }}</strong>
            <small>{{ item.date }}</small>
          </div>
        </div>
        <div class="trend-summary">
          <span>新增用户 {{ dashboard.trends.reduce((sum, item) => sum + item.newUsers, 0) }}</span>
          <span>新增文献 {{ dashboard.trends.reduce((sum, item) => sum + item.newLiteratures, 0) }}</span>
          <span>AI 任务 {{ dashboard.trends.reduce((sum, item) => sum + item.aiTasks, 0) }}</span>
        </div>
      </section>

      <section class="glass-panel chart-panel">
        <div class="panel-heading">
          <div>
            <p class="panel__eyebrow">Domain Heat</p>
            <h3>热门研究方向</h3>
          </div>
        </div>
        <div class="domain-bars">
          <div v-for="item in dashboard.literatureCategoryStats" :key="item.name">
            <span>{{ localizeDimensionName(item.name) }}</span>
            <div><i :style="{ width: `${percent(item.count, maxCategory)}%` }" /></div>
            <strong>{{ item.count }}</strong>
          </div>
        </div>
      </section>

      <section class="glass-panel chart-panel">
        <div class="panel-heading">
          <div>
            <p class="panel__eyebrow">User Roles</p>
            <h3>用户角色分布</h3>
          </div>
        </div>
        <div class="dimension-pill-list">
          <span v-for="item in dashboard.userRoleStats" :key="item.name">
            <strong>{{ item.count }}</strong>{{ item.name }}
          </span>
        </div>
      </section>

      <section class="glass-panel ops-panel">
        <div class="panel-heading">
          <div>
            <p class="panel__eyebrow">Operational Signals</p>
            <h3>平台运行信号</h3>
          </div>
        </div>
        <ul class="signal-list">
          <li><strong>检索服务</strong><span>{{ overview.literatureTotal }} 篇文献可参与检索</span></li>
          <li><strong>RAG 资产</strong><span>{{ overview.ragDocumentTotal }} 个知识库文档维护中</span></li>
          <li><strong>审核流转</strong><span>{{ overview.pendingReviewTotal }} 篇公开文献等待处理</span></li>
          <li><strong>阅读质量</strong><span>{{ overview.favoriteTotal }} 次收藏，{{ overview.readingNoteTotal }} 条笔记</span></li>
        </ul>
      </section>
    </div>
  </section>
</template>

<style scoped>
/* 移除「AI 成功率」后仅剩 3 张 KPI 卡片，收窄为三列避免留空 */
.kpi-grid--three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

@media (max-width: 900px) {
  .kpi-grid--three {
    grid-template-columns: 1fr;
  }
}
</style>
