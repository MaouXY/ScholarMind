<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  CircleCheck,
  Cpu,
  Odometer,
  Refresh,
  Search,
  Timer,
  TrendCharts,
  Warning,
} from '@element-plus/icons-vue'

import { getAdminAiTaskSummary, listAdminAiTasks } from '@/api/admin'
import '@/styles/admin-ext.css'
import type { AdminAiTask, AdminAiTaskSummary } from '@/types/admin'

const tasks = ref<AdminAiTask[]>([])
const summary = ref<AdminAiTaskSummary | null>(null)
const loading = ref(false)
const loadingSummary = ref(false)
const total = ref(0)
const drawerVisible = ref(false)
const detail = ref<AdminAiTask | null>(null)

const query = reactive({
  page: 1,
  size: 10,
  taskType: undefined as string | undefined,
  status: undefined as string | undefined,
  keyword: '',
})

const statusOptions = [
  { label: '排队中', value: 'PENDING' },
  { label: '运行中', value: 'RUNNING' },
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
  { label: '超时', value: 'TIMEOUT' },
]

const presetTaskTypes = [
  'LITERATURE_QA',
  'LITERATURE_SUMMARY',
  'LITERATURE_INDEX',
  'LITERATURE_COMPARE',
  'SEARCH_SEMANTIC',
  'SEARCH_NATURAL_LANGUAGE',
]

const taskTypeOptions = computed(() => {
  const types = new Set<string>(presetTaskTypes)
  summary.value?.items?.forEach((item) => types.add(item.taskType))
  return Array.from(types)
})

const kpiCards = computed(() => {
  const item = summary.value
  return [
    {
      label: '总调用',
      value: item?.total ?? 0,
      hint: `${item?.running ?? 0} 运行中`,
      icon: Cpu,
    },
    {
      label: '成功',
      value: item?.success ?? 0,
      hint: '任务正常返回',
      icon: CircleCheck,
    },
    {
      label: '失败',
      value: item?.failed ?? 0,
      hint: '任务异常终止',
      icon: Warning,
    },
    {
      label: '超时',
      value: item?.timeout ?? 0,
      hint: '超过时限终止',
      icon: Timer,
    },
    {
      label: '成功率',
      value: `${Math.round((item?.successRate ?? 0) * 100)}%`,
      hint: '成功 / 总调用',
      icon: TrendCharts,
    },
    {
      label: '平均耗时',
      value: `${item?.avgDurationMs ?? 0}ms`,
      hint: '全部调用平均',
      icon: Odometer,
    },
  ]
})

function statusLabel(status: string) {
  if (status === 'SUCCESS') return '成功'
  if (status === 'FAILED') return '失败'
  if (status === 'TIMEOUT') return '超时'
  if (status === 'RUNNING') return '运行中'
  if (status === 'PENDING') return '排队中'
  return status || '未知'
}

function statusTagType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'TIMEOUT') return 'warning'
  if (status === 'RUNNING') return 'primary'
  return 'info'
}

function formatDateTime(value?: string) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

function formatDuration(value?: number) {
  if (value === undefined || value === null) return '—'
  return `${value} ms`
}

async function fetchTasks() {
  loading.value = true
  try {
    const result = await listAdminAiTasks({
      page: query.page,
      size: query.size,
      taskType: query.taskType || undefined,
      status: query.status || undefined,
      keyword: query.keyword.trim() || undefined,
    })
    tasks.value = result.items ?? []
    total.value = result.total ?? 0
  } catch (error) {
    console.error(
      'admin ai task list failed, scenario=admin-ai-task-list, page=%s, size=%s, taskType=%s, status=%s, keyword=%s',
      query.page,
      query.size,
      query.taskType,
      query.status,
      query.keyword,
      error,
    )
    tasks.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function fetchSummary() {
  loadingSummary.value = true
  try {
    summary.value = await getAdminAiTaskSummary()
  } catch (error) {
    console.error(
      'admin ai task summary failed, scenario=admin-ai-task-summary',
      error,
    )
    summary.value = null
  } finally {
    loadingSummary.value = false
  }
}

async function refreshAll() {
  await Promise.all([fetchSummary(), fetchTasks()])
}

function handleSearch() {
  query.page = 1
  void fetchTasks()
}

function handleReset() {
  query.page = 1
  query.taskType = undefined
  query.status = undefined
  query.keyword = ''
  void fetchTasks()
}

function handleSizeChange() {
  query.page = 1
  void fetchTasks()
}

function openDetail(item: AdminAiTask) {
  detail.value = item
  drawerVisible.value = true
}

onMounted(() => {
  void refreshAll()
})
</script>

<template>
  <section class="admin-page">
    <div class="spatial-hero compact-hero">
      <div>
        <p class="panel__eyebrow">AI Task Observability</p>
        <h2>观察每一次 AI 调用的成功率与耗时</h2>
        <p>
          日志页汇总 AI 任务的调用总量、失败与超时情况，并保留每次调用的输入输出预览，方便排查模型异常、超时和业务参数问题。
        </p>
      </div>
      <div class="hero-stats">
        <span><strong>{{ summary?.total ?? 0 }}</strong>累计调用</span>
        <span>
          <strong>{{ Math.round((summary?.successRate ?? 0) * 100) }}%</strong>成功率
        </span>
      </div>
    </div>

    <section class="admin-kpi-grid" v-loading="loadingSummary">
      <article v-for="item in kpiCards" :key="item.label" class="screen-kpi">
        <el-icon><component :is="item.icon" /></el-icon>
        <p>{{ item.label }}</p>
        <strong>{{ item.value }}</strong>
        <span>{{ item.hint }}</span>
      </article>
    </section>

    <section class="glass-panel admin-filter-panel">
      <div class="panel-heading">
        <div>
          <p class="panel__eyebrow">Log Filter</p>
          <h3>日志筛选</h3>
        </div>
        <el-button
          :icon="Refresh"
          plain
          :loading="loading || loadingSummary"
          @click="refreshAll"
        >
          刷新
        </el-button>
      </div>
      <el-form :inline="true" class="admin-filter-form">
        <el-form-item label="任务类型">
          <el-select
            v-model="query.taskType"
            filterable
            allow-create
            default-first-option
            clearable
            placeholder="全部任务类型"
          >
            <el-option
              v-for="type in taskTypeOptions"
              :key="type"
              :label="type"
              :value="type"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部状态">
            <el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            :prefix-icon="Search"
            clearable
            placeholder="业务类型 / 业务ID / 关键字"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSearch">
            查询
          </el-button>
          <el-button plain @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="glass-panel admin-table-panel">
      <div class="panel-heading">
        <div>
          <p class="panel__eyebrow">Task Logs</p>
          <h3>调用日志</h3>
        </div>
        <el-tag effect="light">共 {{ total }} 条</el-tag>
      </div>

      <el-table v-loading="loading" :data="tasks" style="width: 100%">
        <el-table-column prop="taskType" label="任务类型" min-width="180" />
        <el-table-column label="业务类型" min-width="170">
          <template #default="{ row }">
            {{ row.bizType || '—' }}{{ row.bizId ? ` #${row.bizId}` : '' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="light">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="110">
          <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
        </el-table-column>
        <el-table-column label="输入/输出字符数" width="170">
          <template #default="{ row }">
            {{ row.requestChars ?? 0 }} / {{ row.responseChars ?? 0 }}
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button plain @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无 AI 调用日志" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        class="admin-pagination"
        background
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="fetchTasks"
        @size-change="handleSizeChange"
      />
    </section>

    <el-drawer v-model="drawerVisible" title="AI 调用详情" size="640px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="任务 ID">
            {{ detail.id }}
          </el-descriptions-item>
          <el-descriptions-item label="任务类型">
            {{ detail.taskType }}
          </el-descriptions-item>
          <el-descriptions-item label="业务类型">
            {{ detail.bizType || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="业务 ID">
            {{ detail.bizId || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="模型">
            {{ detail.modelName || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(detail.status)" effect="light">
              {{ statusLabel(detail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="耗时">
            {{ formatDuration(detail.durationMs) }}
          </el-descriptions-item>
          <el-descriptions-item label="字符数">
            {{ detail.requestChars ?? 0 }} / {{ detail.responseChars ?? 0 }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ formatDateTime(detail.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="更新时间">
            {{ formatDateTime(detail.updatedAt) }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="admin-detail-block">
          <strong>请求预览</strong>
          <pre v-if="detail.requestPreview">{{ detail.requestPreview }}</pre>
          <el-empty v-else description="无请求预览" />
        </div>

        <div class="admin-detail-block">
          <strong>响应预览</strong>
          <pre v-if="detail.responsePreview">{{ detail.responsePreview }}</pre>
          <el-empty v-else description="无响应预览" />
        </div>

        <div
          class="admin-detail-block"
          :class="{ 'admin-detail-block--error': detail.errorMessage }"
        >
          <strong>错误信息</strong>
          <pre v-if="detail.errorMessage">{{ detail.errorMessage }}</pre>
          <el-empty v-else description="调用未产生错误信息" />
        </div>
      </template>
      <el-empty v-else description="请选择一条调用日志" />
    </el-drawer>
  </section>
</template>
