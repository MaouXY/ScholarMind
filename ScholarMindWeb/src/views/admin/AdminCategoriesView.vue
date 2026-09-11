<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'

import {
  createAdminCategory,
  deleteAdminCategory,
  listAdminCategories,
  updateAdminCategory,
} from '@/api/admin'
import '@/styles/admin-ext.css'
import type { AdminCategoryPayload } from '@/types/admin'
import type { Id } from '@/types/auth'
import type { LiteratureCategory } from '@/types/literature'

const categories = ref<LiteratureCategory[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<Id | null>(null)
const deletingId = ref<Id | null>(null)

const form = reactive<AdminCategoryPayload>({
  name: '',
  parentId: null,
  sort: 1,
  status: 1,
})

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '停用', value: 0 },
]

const categoryNameMap = computed(() => {
  const map = new Map<Id, string>()
  categories.value.forEach((item) => map.set(item.id, item.name))
  return map
})

const enabledCount = computed(
  () => categories.value.filter((item) => item.status === 1).length,
)

function sortBySort(list: LiteratureCategory[]) {
  return [...list].sort((a, b) => {
    const sortDiff = (a.sort ?? 0) - (b.sort ?? 0)
    if (sortDiff !== 0) return sortDiff
    return a.name.localeCompare(b.name)
  })
}

const orderedCategories = computed(() => {
  const result: LiteratureCategory[] = []
  const childrenOf = (parentId: Id) =>
    sortBySort(categories.value.filter((item) => item.parentId === parentId))
  const push = (item: LiteratureCategory) => {
    result.push(item)
    childrenOf(item.id).forEach(push)
  }
  sortBySort(categories.value.filter((item) => !item.parentId)).forEach(push)
  // 父分类已删除或数据异常的节点也保留展示，避免列表丢数据
  categories.value.forEach((item) => {
    if (!result.some((row) => row.id === item.id)) result.push(item)
  })
  return result
})

const parentOptions = computed(() => {
  const currentId = editingId.value
  if (!currentId) return orderedCategories.value

  const blocked = new Set<Id>([currentId])
  let changed = true
  while (changed) {
    changed = false
    categories.value.forEach((item) => {
      if (item.parentId && blocked.has(item.parentId) && !blocked.has(item.id)) {
        blocked.add(item.id)
        changed = true
      }
    })
  }
  return orderedCategories.value.filter((item) => !blocked.has(item.id))
})

function parentName(parentId?: Id | null) {
  if (!parentId) return '一级分类'
  return categoryNameMap.value.get(parentId) ?? '上级分类已删除'
}

function formatDateTime(value?: string) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

async function fetchCategories() {
  loading.value = true
  try {
    const result = await listAdminCategories({ onlyEnabled: false })
    categories.value = result ?? []
  } catch (error) {
    console.error(
      'admin category list failed, scenario=admin-category-list, onlyEnabled=false',
      error,
    )
    categories.value = []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    name: '',
    parentId: null,
    sort: categories.value.length + 1,
    status: 1,
  })
  dialogVisible.value = true
}

function openEdit(item: LiteratureCategory) {
  editingId.value = item.id
  Object.assign(form, {
    name: item.name,
    parentId: item.parentId ?? null,
    sort: item.sort ?? 0,
    status: item.status,
  })
  dialogVisible.value = true
}

async function saveCategory() {
  const name = form.name.trim()
  if (!name) {
    ElMessage.warning('请填写分类名称')
    return
  }

  const payload: AdminCategoryPayload = {
    name,
    parentId: form.parentId ?? null,
    sort: Number.isFinite(form.sort) ? form.sort : 0,
    status: form.status,
  }

  saving.value = true
  try {
    if (editingId.value) {
      await updateAdminCategory(editingId.value, payload)
      ElMessage.success('分类已更新')
    } else {
      await createAdminCategory(payload)
      ElMessage.success('分类已创建')
    }
    dialogVisible.value = false
    await fetchCategories()
  } catch (error) {
    console.error(
      'admin category save failed, scenario=admin-category-save, categoryId=%s, name=%s, parentId=%s',
      editingId.value,
      payload.name,
      payload.parentId,
      error,
    )
  } finally {
    saving.value = false
  }
}

async function removeCategory(item: LiteratureCategory) {
  const childCount = categories.value.filter(
    (row) => row.parentId === item.id,
  ).length
  const extra = childCount ? `该分类下还有 ${childCount} 个子分类。` : ''

  try {
    await ElMessageBox.confirm(
      `确认删除分类「${item.name}」吗？${extra}删除后不可恢复。`,
      '删除分类',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    console.error(
      'admin category delete confirm failed, scenario=admin-category-delete-confirm, categoryId=%s',
      item.id,
      error,
    )
    return
  }

  deletingId.value = item.id
  try {
    await deleteAdminCategory(item.id)
    ElMessage.success('分类已删除')
    await fetchCategories()
  } catch (error) {
    console.error(
      'admin category delete failed, scenario=admin-category-delete, categoryId=%s, name=%s',
      item.id,
      item.name,
      error,
    )
  } finally {
    deletingId.value = null
  }
}

onMounted(() => {
  void fetchCategories()
})
</script>

<template>
  <section class="admin-page">
    <div class="spatial-hero compact-hero">
      <div>
        <p class="panel__eyebrow">Category Governance</p>
        <h2>维护文献分类体系与展示顺序</h2>
        <p>
          分类管理用于统一文献的一级方向与子方向：新增、编辑、排序、启停与删除都会直接影响文献检索和统计维度的归类结果。
        </p>
      </div>
      <div class="hero-stats">
        <span><strong>{{ categories.length }}</strong>分类总数</span>
        <span><strong>{{ enabledCount }}</strong>启用中</span>
      </div>
    </div>

    <section class="glass-panel admin-table-panel">
      <div class="panel-heading">
        <div>
          <p class="panel__eyebrow">Category Tree</p>
          <h3>分类列表</h3>
        </div>
        <div class="admin-filter-form">
          <el-button
            :icon="Refresh"
            plain
            :loading="loading"
            @click="fetchCategories"
          >
            刷新
          </el-button>
          <el-button :icon="Plus" type="primary" @click="openCreate">
            新增分类
          </el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="orderedCategories"
        row-key="id"
        style="width: 100%"
      >
        <el-table-column label="名称" min-width="220">
          <template #default="{ row }">
            <span>{{ row.parentId ? '└ ' : '' }}{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="上级分类" min-width="160">
          <template #default="{ row }">{{ parentName(row.parentId) }}</template>
        </el-table-column>
        <el-table-column label="排序" width="100">
          <template #default="{ row }">{{ row.sort ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.updatedAt || row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button plain @click="openEdit(row)">编辑</el-button>
            <el-button
              type="danger"
              plain
              :loading="deletingId === row.id"
              @click="removeCategory(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无分类数据" />
        </template>
      </el-table>
    </section>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑分类' : '新增分类'"
      width="520px"
    >
      <el-form label-position="top">
        <el-form-item label="分类名称">
          <el-input
            v-model="form.name"
            maxlength="60"
            show-word-limit
            placeholder="例如：信息检索"
          />
        </el-form-item>
        <el-form-item label="上级分类（可选）">
          <el-select
            v-model="form.parentId"
            clearable
            placeholder="不选择则作为一级分类"
          >
            <el-option
              v-for="item in parentOptions"
              :key="item.id"
              :label="item.parentId ? `└ ${item.name}` : item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number
            v-model="form.sort"
            :min="0"
            :max="9999"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-segmented v-model="form.status" :options="statusOptions" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button plain @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCategory">
          保存
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>
