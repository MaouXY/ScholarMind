<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'

import { listAdminUsers, updateAdminUserStatus } from '@/api/admin'
import '@/styles/admin-ext.css'
import type { Id, UserProfile, UserRole } from '@/types/auth'

const users = ref<UserProfile[]>([])
const loading = ref(false)
const total = ref(0)
const statusPendingId = ref<Id | null>(null)

const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  role: undefined as UserRole | undefined,
  status: undefined as number | undefined,
})

const roleOptions: Array<{ label: string; value: UserRole }> = [
  { label: '学生', value: 'STUDENT' },
  { label: '教师', value: 'TEACHER' },
  { label: '管理员', value: 'ADMIN' },
]

const statusOptions: Array<{ label: string; value: number }> = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 },
]

const enabledCount = computed(
  () => users.value.filter((item) => item.status === 1).length,
)

function roleLabel(role: UserRole) {
  if (role === 'ADMIN') return '管理员'
  if (role === 'TEACHER') return '教师'
  return '学生'
}

function roleTagType(role: UserRole) {
  if (role === 'ADMIN') return 'danger'
  if (role === 'TEACHER') return 'warning'
  return 'primary'
}

function formatDateTime(value?: string) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

async function fetchUsers() {
  loading.value = true
  try {
    const result = await listAdminUsers({
      page: query.page,
      size: query.size,
      keyword: query.keyword.trim() || undefined,
      role: query.role,
      status: query.status,
    })
    users.value = result.items ?? []
    total.value = result.total ?? 0
  } catch (error) {
    console.error(
      'admin user list failed, scenario=admin-user-list, page=%s, size=%s, keyword=%s, role=%s, status=%s',
      query.page,
      query.size,
      query.keyword,
      query.role,
      query.status,
      error,
    )
    users.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  void fetchUsers()
}

function handleReset() {
  query.page = 1
  query.keyword = ''
  query.role = undefined
  query.status = undefined
  void fetchUsers()
}

function handleSizeChange() {
  query.page = 1
  void fetchUsers()
}

async function toggleStatus(item: UserProfile) {
  const nextStatus = item.status === 1 ? 0 : 1
  const actionText = nextStatus === 1 ? '启用' : '停用'
  const displayName = item.nickname || item.username

  try {
    await ElMessageBox.confirm(
      `确认${actionText}用户「${displayName}」吗？`,
      `${actionText}用户`,
      {
        confirmButtonText: `确认${actionText}`,
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    console.error(
      'admin user status confirm failed, scenario=admin-user-status-confirm, userId=%s',
      item.id,
      error,
    )
    return
  }

  statusPendingId.value = item.id
  try {
    const updated = await updateAdminUserStatus(item.id, nextStatus)
    const index = users.value.findIndex((row) => row.id === item.id)
    if (index >= 0) users.value[index] = updated
    ElMessage.success(`用户已${actionText}`)
  } catch (error) {
    console.error(
      'admin user status update failed, scenario=admin-user-status-update, userId=%s, status=%s',
      item.id,
      nextStatus,
      error,
    )
  } finally {
    statusPendingId.value = null
  }
}

onMounted(() => {
  void fetchUsers()
})
</script>

<template>
  <section class="admin-page">
    <div class="spatial-hero compact-hero">
      <div>
        <p class="panel__eyebrow">User Governance</p>
        <h2>统一管理平台用户与账号状态</h2>
        <p>
          按角色与状态筛选平台账号，查看用户名、昵称、邮箱与创建时间，并可直接启用或停用账号，停用后该用户将无法继续访问平台功能。
        </p>
      </div>
      <div class="hero-stats">
        <span><strong>{{ total }}</strong>匹配用户</span>
        <span><strong>{{ enabledCount }}</strong>本页启用</span>
      </div>
    </div>

    <section class="glass-panel admin-filter-panel">
      <div class="panel-heading">
        <div>
          <p class="panel__eyebrow">Account Filter</p>
          <h3>用户筛选</h3>
        </div>
        <el-button :icon="Refresh" plain :loading="loading" @click="fetchUsers">
          刷新
        </el-button>
      </div>
      <el-form :inline="true" class="admin-filter-form">
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            :prefix-icon="Search"
            clearable
            placeholder="用户名 / 昵称 / 邮箱"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="query.role" clearable placeholder="全部角色">
            <el-option
              v-for="item in roleOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
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
          <p class="panel__eyebrow">User List</p>
          <h3>用户列表</h3>
        </div>
        <el-tag effect="light">共 {{ total }} 条</el-tag>
      </div>

      <el-table v-loading="loading" :data="users" style="width: 100%">
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column label="昵称" min-width="140">
          <template #default="{ row }">{{ row.nickname || '—' }}</template>
        </el-table-column>
        <el-table-column label="邮箱" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.email || '—' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="110">
          <template #default="{ row }">
            <el-tag :type="roleTagType(row.role)" effect="light">
              {{ roleLabel(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button
              :type="row.status === 1 ? 'danger' : 'success'"
              plain
              :loading="statusPendingId === row.id"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无用户数据" />
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
        @current-change="fetchUsers"
        @size-change="handleSizeChange"
      />
    </section>
  </section>
</template>
