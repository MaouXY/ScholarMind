<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'

import { getAdminDashboard } from '@/api/admin'
import { displayRoleName, localizeDimensionName } from '@/utils/format'
import { emptyDashboard } from '@/utils/dashboard'
import type { AdminDashboard, AdminTrendPoint } from '@/types/admin'

const dashboard = ref<AdminDashboard>(emptyDashboard())
const failed = ref(false)
const hasReadingActivity = computed(() => dashboard.value.trends.some((item) => item.readingEvents > 0))
const loading = ref(false)
const query = reactive({
  days: 14,
  limit: 8,
})

const overview = computed(() => dashboard.value.overview)

const kpis = computed(() => [
  { label: '文献总量', value: overview.value.literatureTotal, hint: '均由管理员上传' },
  { label: '阅读事件', value: overview.value.readingHistoryTotal, hint: `${overview.value.readingNoteTotal} 条笔记` },
  { label: '原文文件', value: overview.value.fileTotal, hint: 'PDF / 文本解析建索引' },
])

const maxReading = computed(() =>
  Math.max(...dashboard.value.trends.map((item) => item.readingEvents || 0), 1),
)

const maxCategory = computed(() =>
  Math.max(...dashboard.value.literatureCategoryStats.map((item) => item.count || 0), 1),
)

function barHeight(item: AdminTrendPoint) {
  return Math.round(((item.readingEvents || 0) / maxReading.value) * 160)
}

function percent(value: number, max: number) {
  return Math.max(4, Math.round((value / max) * 100))
}

async function fetchDashboard() {
  loading.value = true
  failed.value = false
  try {
    dashboard.value = await getAdminDashboard(query)
  } catch (error) {
    console.error(
      'analytics dashboard load failed, scenario=analytics-statistics-dashboard, days=%s, limit=%s',
      query.days,
      query.limit,
      error,
    )
    failed.value = true
    dashboard.value = emptyDashboard()
  } finally {
    loading.value = false
  }
}

onMounted(fetchDashboard)
watch(() => query.days, fetchDashboard)
</script>

<template>
  <section class="analytics-page">


    <section class="admin-control-strip glass-panel">
      <div>

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

    <el-empty v-if="failed" description="统计数据加载失败">
      <el-button @click="fetchDashboard">重试</el-button>
    </el-empty>
    <div v-else class="kpi-grid kpi-grid--three" v-loading="loading">
      <article v-for="item in kpis" :key="item.label" class="screen-kpi">
        <p>{{ item.label }}</p>
        <strong>{{ item.value }}</strong>
        <span>{{ item.hint }}</span>
      </article>
    </div>

    <div v-if="!failed" class="analytics-grid analytics-grid--stage5">
      <section class="glass-panel chart-panel">
        <div class="panel-heading">
          <div>

            <h3>阅读趋势</h3>
          </div>
        </div>
        <el-empty v-if="!hasReadingActivity" description="所选时间内暂无阅读活动" />
        <div v-else class="bar-chart" aria-label="每日阅读量">
          <div v-for="item in dashboard.trends" :key="item.date" class="bar-chart__item">
            <span class="bar-chart__bar" :style="{ height: `${barHeight(item)}px` }" />
            <strong>{{ item.readingEvents }}</strong>
            <small>{{ item.date.slice(5) }}</small>
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

            <h3>用户角色分布</h3>
          </div>
        </div>
        <div class="dimension-pill-list">
          <span v-for="item in dashboard.userRoleStats" :key="item.name">
            <strong>{{ item.count }}</strong>{{ displayRoleName(item.name) }}
          </span>
        </div>
      </section>

      <section class="glass-panel ops-panel">
        <div class="panel-heading">
          <div>

            <h3>平台运行信号</h3>
          </div>
        </div>
        <ul class="signal-list">
          <li><strong>文献入库</strong><span>{{ overview.literatureTotal }} 篇文献均由管理员上传，原文 {{ overview.fileTotal }} 份</span></li>
          <li><strong>学科覆盖</strong><span>{{ dashboard.literatureCategoryStats.length }} 个学科，{{ dashboard.literatureTypeStats.length }} 种文献类别</span></li>
          <li><strong>阅读沉淀</strong><span>{{ overview.readingNoteTotal }} 条笔记，{{ overview.qaTotal }} 条问答</span></li>
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
