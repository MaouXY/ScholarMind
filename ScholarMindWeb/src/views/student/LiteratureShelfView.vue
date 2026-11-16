<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Delete,
  Document,
  Download,
  EditPen,
  FolderAdd,
  FolderOpened,
  Notebook,
  Reading,
  Refresh,
  StarFilled,
  Timer,
} from '@element-plus/icons-vue'

import {
  createFavoriteFolder,
  deleteFavoriteFolder,
  deleteReadingNote,
  listFavoriteFolders,
  listFavorites,
  listReadingHistory,
  listReadingNotes,
  moveFavoriteFolder,
  unfavoriteLiterature,
  updateFavoriteFolder,
} from '@/api/literature'
import { downloadReadingNotes } from '@/utils/download'
import { displayCategoryName } from '@/utils/format'
import type {
  Favorite,
  FavoriteFolder,
  FavoriteFolderPayload,
  Id,
  ReadingHistoryItem,
  ReadingNote,
} from '@/types/literature'
import '@/styles/literature-ext.css'

const router = useRouter()

const activeTab = ref<'favorites' | 'history' | 'notes'>('favorites')

/* ------------------------------- 收藏 ------------------------------- */
const favorites = ref<Favorite[]>([])
const favoritesTotal = ref(0)
const favoritesLoading = ref(false)
const favoritesRemovingId = ref<Id | null>(null)
const favoriteQuery = ref<{ page: number; size: number; folderId?: Id }>({
  page: 1,
  size: 10,
})

/* ----------------------------- 收藏夹 ----------------------------- */
/** 0 表示「未分类」，undefined 表示「全部收藏」 */
const folders = ref<FavoriteFolder[]>([])
const foldersLoading = ref(false)
const activeFolderId = ref<Id | undefined>(undefined)
const folderDialogVisible = ref(false)
const folderDialogMode = ref<'create' | 'edit'>('create')
const folderSaving = ref(false)
const folderRemovingId = ref<Id | null>(null)
const editingFolder = ref<FavoriteFolder | null>(null)
const folderForm = ref<FavoriteFolderPayload>({ name: '', description: '' })

/** 后端约定：folderId 传 0 表示「未分类」；ID 统一按字符串传输 */
const UNCLASSIFIED: Id = '0'

/* ----------------------------- 阅读历史 ----------------------------- */
const historyItems = ref<ReadingHistoryItem[]>([])
const historyTotal = ref(0)
const historyLoading = ref(false)
const historyQuery = ref({ page: 1, size: 10 })

/* ------------------------------- 笔记 ------------------------------- */
const notes = ref<ReadingNote[]>([])
const notesTotal = ref(0)
const notesLoading = ref(false)
const notesExporting = ref(false)
const notesRemovingId = ref<Id | null>(null)
const noteQuery = ref({ page: 1, size: 10 })

const totalFavoriteReadCount = computed(() =>
  favorites.value.reduce((sum, item) => sum + (item.readCount ?? 0), 0),
)

/** 进度取值 0-100，兼容后端以 0-1 小数返回的情况 */
function progressValue(progress?: number | null) {
  const raw = Number(progress ?? 0)
  if (!Number.isFinite(raw) || raw <= 0) return 0
  const percent = raw <= 1 ? raw * 100 : raw
  return Math.max(0, Math.min(100, Math.round(percent)))
}

const averageHistoryProgress = computed(() => {
  if (!historyItems.value.length) return 0
  const sum = historyItems.value.reduce(
    (total, item) => total + progressValue(item.progress),
    0,
  )
  return Math.round(sum / historyItems.value.length)
})

const aiNoteCount = computed(
  () => notes.value.filter((note) => (note.aiGenerated ?? 0) > 0).length,
)

/** 秒转换为「x 分 y 秒」可读文本 */
function durationText(seconds?: number | null) {
  const total = Math.max(0, Math.floor(Number(seconds ?? 0)))
  if (!total) return '0 秒'
  const minutes = Math.floor(total / 60)
  const rest = total % 60
  if (minutes <= 0) return `${rest} 秒`
  if (minutes < 60) return `${minutes} 分 ${rest} 秒`
  const hours = Math.floor(minutes / 60)
  return `${hours} 小时 ${minutes % 60} 分`
}

function formatDateTime(value?: string | null) {
  if (!value) return '未记录'
  return value.slice(0, 19).replace('T', ' ')
}

function excerpt(content?: string | null, length = 96) {
  const text = (content ?? '').replace(/\s+/g, ' ').trim()
  if (text.length <= length) return text || '（无内容）'
  return `${text.slice(0, length)}…`
}

/* ----------------------------- 收藏：数据 ----------------------------- */
/** 全部收藏总数（不受当前收藏夹筛选影响） */
const allFavoritesTotal = ref(0)

async function refreshAllFavoriteTotal() {
  try {
    const result = await listFavorites({ page: 1, size: 1 })
    allFavoritesTotal.value = result.total
  } catch (error) {
    console.error(
      'favorite total refresh failed, scenario=favorite-total',
      error,
    )
  }
}

async function loadFavorites() {
  favoritesLoading.value = true
  try {
    const result = await listFavorites({
      page: favoriteQuery.value.page,
      size: favoriteQuery.value.size,
      folderId: activeFolderId.value,
    })
    favorites.value = result.items
    favoritesTotal.value = result.total
  } catch (error) {
    console.error(
      'favorite list failed, scenario=favorite-list, page=%s, size=%s, folderId=%s',
      favoriteQuery.value.page,
      favoriteQuery.value.size,
      activeFolderId.value,
      error,
    )
    favorites.value = []
    favoritesTotal.value = 0
  } finally {
    favoritesLoading.value = false
  }
}

/* ---------------------------- 收藏夹：数据 ---------------------------- */
async function loadFolders() {
  foldersLoading.value = true
  try {
    folders.value = await listFavoriteFolders()
  } catch (error) {
    console.error(
      'favorite folder list failed, scenario=favorite-folder-list',
      error,
    )
    folders.value = []
  } finally {
    foldersLoading.value = false
  }
}

/** 切换当前收藏夹；0 为未分类，undefined 为全部 */
async function selectFolder(folderId?: Id) {
  activeFolderId.value = folderId
  favoriteQuery.value.page = 1
  await loadFavorites()
}

function folderCountText(folder: FavoriteFolder) {
  return `${folder.favoriteCount ?? 0}`
}

function openFolderCreate() {
  folderDialogMode.value = 'create'
  editingFolder.value = null
  folderForm.value = { name: '', description: '' }
  folderDialogVisible.value = true
}

function openFolderEdit(folder: FavoriteFolder) {
  folderDialogMode.value = 'edit'
  editingFolder.value = folder
  folderForm.value = {
    name: folder.name,
    description: folder.description ?? '',
  }
  folderDialogVisible.value = true
}

async function submitFolder() {
  if (!folderForm.value.name.trim()) {
    ElMessage.warning('请填写收藏夹名称')
    return
  }

  folderSaving.value = true
  try {
    if (folderDialogMode.value === 'edit' && editingFolder.value) {
      await updateFavoriteFolder(editingFolder.value.id, folderForm.value)
      ElMessage.success('收藏夹已更新')
    } else {
      await createFavoriteFolder(folderForm.value)
      ElMessage.success('收藏夹已创建')
    }
    folderDialogVisible.value = false
    await loadFolders()
  } catch (error) {
    console.error(
      'favorite folder save failed, scenario=favorite-folder-save, mode=%s, folderId=%s',
      folderDialogMode.value,
      editingFolder.value?.id,
      error,
    )
  } finally {
    folderSaving.value = false
  }
}

async function removeFolder(folder: FavoriteFolder) {
  try {
    await ElMessageBox.confirm(
      `删除收藏夹「${folder.name}」后，夹内 ${folder.favoriteCount ?? 0} 篇收藏会退回「未分类」，收藏本身不会被删除。确认删除？`,
      '删除收藏夹',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch (error) {
    if (error === 'cancel') return
    return
  }

  folderRemovingId.value = folder.id
  try {
    await deleteFavoriteFolder(folder.id)
    ElMessage.success('收藏夹已删除')
    if (activeFolderId.value === folder.id) {
      activeFolderId.value = undefined
    }
    await Promise.all([loadFolders(), loadFavorites()])
  } catch (error) {
    console.error(
      'favorite folder delete failed, scenario=favorite-folder-delete, folderId=%s',
      folder.id,
      error,
    )
  } finally {
    folderRemovingId.value = null
  }
}

/** 单归属：把一篇收藏移动到指定收藏夹（0 表示未分类） */
async function moveFavorite(item: Favorite, folderId: Id) {
  if (String(item.folderId ?? UNCLASSIFIED) === String(folderId)) return

  try {
    await moveFavoriteFolder(item.literatureId, folderId)
    ElMessage.success(
      folderId === UNCLASSIFIED ? '已移到未分类' : '已移动收藏夹',
    )
    await Promise.all([loadFolders(), loadFavorites()])
  } catch (error) {
    console.error(
      'favorite move failed, scenario=favorite-move, literatureId=%s, folderId=%s',
      item.literatureId,
      folderId,
      error,
    )
  }
}

async function removeFavorite(item: Favorite) {
  try {
    await ElMessageBox.confirm(
      `确认将《${item.literatureTitle}》移出收藏？`,
      '取消收藏',
      {
        confirmButtonText: '取消收藏',
        cancelButtonText: '再想想',
        type: 'warning',
      },
    )
  } catch (error) {
    if (error === 'cancel') return
    console.error(
      'favorite remove confirm failed, scenario=favorite-remove-confirm, literatureId=%s',
      item.literatureId,
      error,
    )
    return
  }

  favoritesRemovingId.value = item.literatureId
  try {
    await unfavoriteLiterature(item.literatureId)
    ElMessage.success('已取消收藏')
    if (favorites.value.length === 1 && favoriteQuery.value.page > 1) {
      favoriteQuery.value.page -= 1
    }
    await Promise.all([
      loadFavorites(),
      loadFolders(),
      refreshAllFavoriteTotal(),
    ])
  } catch (error) {
    console.error(
      'favorite remove failed, scenario=favorite-remove, literatureId=%s',
      item.literatureId,
      error,
    )
  } finally {
    favoritesRemovingId.value = null
  }
}

/* --------------------------- 阅读历史：数据 --------------------------- */
async function loadHistory() {
  historyLoading.value = true
  try {
    const result = await listReadingHistory({ ...historyQuery.value })
    historyItems.value = result.items
    historyTotal.value = result.total
  } catch (error) {
    console.error(
      'reading history list failed, scenario=reading-history-list, page=%s, size=%s',
      historyQuery.value.page,
      historyQuery.value.size,
      error,
    )
    historyItems.value = []
    historyTotal.value = 0
  } finally {
    historyLoading.value = false
  }
}

/* ----------------------------- 笔记：数据 ----------------------------- */
async function loadNotes() {
  notesLoading.value = true
  try {
    const result = await listReadingNotes({ ...noteQuery.value })
    notes.value = result.items
    notesTotal.value = result.total
  } catch (error) {
    console.error(
      'reading notes list failed, scenario=reading-note-list, page=%s, size=%s',
      noteQuery.value.page,
      noteQuery.value.size,
      error,
    )
    notes.value = []
    notesTotal.value = 0
  } finally {
    notesLoading.value = false
  }
}

async function removeNote(note: ReadingNote) {
  try {
    await ElMessageBox.confirm(
      '删除后不可恢复，确认删除这条阅读笔记？',
      '删除笔记',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch (error) {
    if (error === 'cancel') return
    console.error(
      'reading note delete confirm failed, scenario=reading-note-delete-confirm, noteId=%s',
      note.id,
      error,
    )
    return
  }

  notesRemovingId.value = note.id
  try {
    await deleteReadingNote(note.id)
    ElMessage.success('笔记已删除')
    if (notes.value.length === 1 && noteQuery.value.page > 1) {
      noteQuery.value.page -= 1
    }
    await loadNotes()
  } catch (error) {
    console.error(
      'reading note delete failed, scenario=reading-note-delete, noteId=%s, literatureId=%s',
      note.id,
      note.literatureId,
      error,
    )
  } finally {
    notesRemovingId.value = null
  }
}

/** 导出全部阅读笔记（不带 literatureId，text/markdown 附件） */
async function exportAllNotes() {
  notesExporting.value = true
  try {
    const fileName = await downloadReadingNotes()
    ElMessage.success(`已导出全部笔记：${fileName}`)
  } catch (error) {
    console.error(
      'reading notes export failed, scenario=reading-note-export-all, total=%s',
      notesTotal.value,
      error,
    )
    ElMessage.error('导出笔记失败，请稍后重试')
  } finally {
    notesExporting.value = false
  }
}

/* ------------------------------- 导航 ------------------------------- */
function openDetail(literatureId: Id) {
  router.push(`/student/literature/${literatureId}`)
}

function openReader(literatureId: Id) {
  router.push(`/student/literature/${literatureId}/read`)
}

function goLibrary() {
  router.push('/student/search')
}

function handleTabChange(name: string | number) {
  if (name === 'history') {
    loadHistory()
    return
  }
  if (name === 'notes') {
    loadNotes()
    return
  }
  loadFavorites()
}

onMounted(async () => {
  await Promise.all([loadFolders(), refreshAllFavoriteTotal(), loadFavorites(), loadHistory(), loadNotes()])
})
</script>

<template>
  <section class="library-page">
    <div class="shelf-summary"><div class="hero-stats">
        <span>
          <strong>{{ allFavoritesTotal }}</strong>
          收藏文献
        </span>
        <span>
          <strong>{{ historyTotal }}</strong>
          阅读记录
        </span>
        <span>
          <strong>{{ notesTotal }}</strong>
          阅读笔记
        </span>
      </div></div>

    <section class="glass-panel" aria-label="我的书房">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="收藏" name="favorites">
          <div class="shelf-pane">
            <div class="folder-rail" v-loading="foldersLoading">
              <div class="folder-rail__head">
                <span class="folder-rail__caption">
                  <el-icon><FolderOpened /></el-icon>
                  收藏夹
                </span>
                <el-button
                  :icon="FolderAdd"
                  text
                  size="small"
                  @click="openFolderCreate"
                >
                  新建
                </el-button>
              </div>
              <div class="folder-rail__list">
                <button
                  type="button"
                  class="folder-chip"
                  :class="{ 'is-active': activeFolderId === undefined }"
                  @click="selectFolder(undefined)"
                >
                  <span>全部收藏</span>
                  <em>{{ allFavoritesTotal }}</em>
                </button>
                <button
                  type="button"
                  class="folder-chip"
                  :class="{ 'is-active': activeFolderId === UNCLASSIFIED }"
                  @click="selectFolder(UNCLASSIFIED)"
                >
                  <span>未分类</span>
                </button>
                <div
                  v-for="folder in folders"
                  :key="folder.id"
                  class="folder-row"
                  :class="{ 'is-active': String(activeFolderId) === String(folder.id) }"
                >
                  <button
                    type="button"
                    class="folder-chip"
                    @click="selectFolder(folder.id)"
                  >
                    <span>{{ folder.name }}</span>
                    <em>{{ folderCountText(folder) }}</em>
                  </button>
                  <span class="folder-row__actions">
                    <el-button
                      :icon="EditPen"
                      text
                      size="small"
                      @click="openFolderEdit(folder)"
                    />
                    <el-button
                      :icon="Delete"
                      text
                      size="small"
                      :loading="folderRemovingId === folder.id"
                      @click="removeFolder(folder)"
                    />
                  </span>
                </div>
                <p v-if="!folders.length && !foldersLoading" class="folder-rail__hint">
                  还没有收藏夹，点「新建」按主题建一个
                </p>
              </div>
            </div>

            <div class="shelf-toolbar">
              <span class="shelf-toolbar__info">
                共 {{ favoritesTotal }} 篇收藏，本页累计阅读
                {{ totalFavoriteReadCount }} 次
              </span>
              <div class="shelf-toolbar__actions">
                <el-button
                  :icon="Refresh"
                  plain
                  :loading="favoritesLoading"
                  @click="loadFavorites"
                >
                  刷新
                </el-button>
                <el-button :icon="StarFilled" plain @click="goLibrary">
                  去检索文献
                </el-button>
              </div>
            </div>

            <div v-loading="favoritesLoading">
              <div v-if="favorites.length" class="shelf-list">
                <article
                  v-for="item in favorites"
                  :key="item.id"
                  class="shelf-card"
                >
                  <div class="shelf-card__main">
                    <h4>{{ item.literatureTitle || '未命名文献' }}</h4>
                    <div class="shelf-card__tags">
                      <span>{{ item.author || '未知作者' }}</span>
                      <span>
                        {{ displayCategoryName(item.categoryName ?? null) }}
                      </span>
                      <span>阅读 {{ item.readCount ?? 0 }} 次</span>
                      <span>收藏于 {{ formatDateTime(item.createdAt) }}</span>
                      <el-tag
                        v-if="item.folderName"
                        size="small"
                        effect="plain"
                      >
                        {{ item.folderName }}
                      </el-tag>
                      <el-tag v-else size="small" effect="plain" type="info">
                        未分类
                      </el-tag>
                    </div>
                  </div>
                  <div class="shelf-card__actions">
                    <el-dropdown
                      trigger="click"
                      @command="(command: Id) => moveFavorite(item, command)"
                    >
                      <el-button :icon="FolderOpened" size="small" plain>
                        移动到
                      </el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item :command="UNCLASSIFIED">
                            未分类
                          </el-dropdown-item>
                          <el-dropdown-item
                            v-for="folder in folders"
                            :key="folder.id"
                            :command="folder.id"
                          >
                            {{ folder.name }}
                          </el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                    <el-button
                      :icon="Document"
                      size="small"
                      type="primary"
                      plain
                      @click="openDetail(item.literatureId)"
                    >
                      查看详情
                    </el-button>
                    <el-button
                      :icon="Reading"
                      size="small"
                      @click="openReader(item.literatureId)"
                    >
                      在线阅读
                    </el-button>
                    <el-button
                      :icon="Delete"
                      size="small"
                      text
                      type="danger"
                      :loading="favoritesRemovingId === item.literatureId"
                      @click="removeFavorite(item)"
                    >
                      取消收藏
                    </el-button>
                  </div>
                </article>
              </div>
              <el-empty
                v-else
                description="该收藏夹下还没有文献，去检索页挑几篇感兴趣的论文吧"
              />
            </div>

            <div class="shelf-pager">
              <el-pagination
                v-model:current-page="favoriteQuery.page"
                v-model:page-size="favoriteQuery.size"
                :total="favoritesTotal"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                background
                @current-change="loadFavorites"
                @size-change="loadFavorites"
              />
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="阅读历史" name="history">
          <div class="shelf-pane">
            <div class="shelf-toolbar">
              <span class="shelf-toolbar__info">
                共 {{ historyTotal }} 条阅读记录，本页平均进度
                {{ averageHistoryProgress }}%
              </span>
              <div class="shelf-toolbar__actions">
                <el-button
                  :icon="Refresh"
                  plain
                  :loading="historyLoading"
                  @click="loadHistory"
                >
                  刷新
                </el-button>
                <el-button :icon="Timer" plain @click="goLibrary">
                  继续找文献
                </el-button>
              </div>
            </div>

            <div v-loading="historyLoading">
              <div v-if="historyItems.length" class="shelf-list">
                <article
                  v-for="item in historyItems"
                  :key="item.id"
                  class="shelf-card"
                >
                  <div class="shelf-card__main">
                    <h4>{{ item.literatureTitle || '未命名文献' }}</h4>
                    <div class="shelf-card__tags">
                      <span>{{ item.author || '未知作者' }}</span>
                      <span>
                        {{ displayCategoryName(item.categoryName ?? null) }}
                      </span>
                      <span>更新于 {{ formatDateTime(item.updatedAt) }}</span>
                    </div>
                    <div class="shelf-card__progress">
                      <el-progress
                        :percentage="progressValue(item.progress)"
                        :stroke-width="10"
                        striped
                      />
                      <div class="shelf-card__progress-info">
                        <span>进度 {{ progressValue(item.progress) }}%</span>
                        <span>上次读到第 {{ item.lastPage ?? 0 }} 块</span>
                        <span
                          >累计阅读 {{ durationText(item.readDuration) }}</span
                        >
                      </div>
                    </div>
                  </div>
                  <div class="shelf-card__actions">
                    <el-button
                      :icon="Reading"
                      size="small"
                      type="primary"
                      @click="openReader(item.literatureId)"
                    >
                      继续阅读
                    </el-button>
                    <el-button
                      :icon="Document"
                      size="small"
                      plain
                      @click="openDetail(item.literatureId)"
                    >
                      查看详情
                    </el-button>
                  </div>
                </article>
              </div>
              <el-empty
                v-else
                description="还没有阅读记录，阅读文献后会自动沉淀进度与时长"
              />
            </div>

            <div class="shelf-pager">
              <el-pagination
                v-model:current-page="historyQuery.page"
                v-model:page-size="historyQuery.size"
                :total="historyTotal"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                background
                @current-change="loadHistory"
                @size-change="loadHistory"
              />
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="笔记" name="notes">
          <div class="shelf-pane">
            <div class="shelf-toolbar">
              <span class="shelf-toolbar__info">
                共 {{ notesTotal }} 条笔记，本页 AI 解析笔记
                {{ aiNoteCount }} 条
              </span>
              <div class="shelf-toolbar__actions">
                <el-button
                  :icon="Download"
                  plain
                  :loading="notesExporting"
                  @click="exportAllNotes"
                >
                  导出全部笔记
                </el-button>
                <el-button
                  :icon="Refresh"
                  plain
                  :loading="notesLoading"
                  @click="loadNotes"
                >
                  刷新
                </el-button>
              </div>
            </div>

            <div v-loading="notesLoading">
              <div v-if="notes.length" class="shelf-list">
                <article
                  v-for="note in notes"
                  :key="note.id"
                  class="shelf-card"
                >
                  <div class="shelf-card__main">
                    <h4>{{ note.title || '未命名笔记' }}</h4>
                    <div class="shelf-card__tags">
                      <el-tag
                        size="small"
                        effect="plain"
                        :type="note.aiGenerated ? 'success' : 'info'"
                      >
                        {{ note.aiGenerated ? 'AI 解析' : '手动笔记' }}
                      </el-tag>
                      <span>《{{ note.literatureTitle || '未知文献' }}》</span>
                      <span>
                        更新于
                        {{ formatDateTime(note.updatedAt ?? note.createdAt) }}
                      </span>
                    </div>
                    <p class="shelf-card__excerpt">
                      {{ excerpt(note.content) }}
                    </p>
                  </div>
                  <div class="shelf-card__actions">
                    <el-button
                      :icon="Notebook"
                      size="small"
                      type="primary"
                      plain
                      @click="openDetail(note.literatureId)"
                    >
                      查看文献
                    </el-button>
                    <el-button
                      :icon="Delete"
                      size="small"
                      text
                      type="danger"
                      :loading="notesRemovingId === note.id"
                      @click="removeNote(note)"
                    >
                      删除
                    </el-button>
                  </div>
                </article>
              </div>
              <el-empty
                v-else
                description="还没有阅读笔记，在阅读页创建批注或使用 AI 解析"
              />
            </div>

            <div class="shelf-pager">
              <el-pagination
                v-model:current-page="noteQuery.page"
                v-model:page-size="noteQuery.size"
                :total="notesTotal"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                background
                @current-change="loadNotes"
                @size-change="loadNotes"
              />
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog
      v-model="folderDialogVisible"
      :title="folderDialogMode === 'create' ? '新建收藏夹' : '编辑收藏夹'"
      width="420px"
    >
      <el-form label-position="top">
        <el-form-item label="名称">
          <el-input
            v-model="folderForm.name"
            maxlength="64"
            placeholder="例如：精读中 / 综述材料"
          />
        </el-form-item>
        <el-form-item label="说明（可选）">
          <el-input
            v-model="folderForm.description"
            maxlength="255"
            placeholder="这个收藏夹放什么"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="folderDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="folderSaving" @click="submitFolder">
          保存
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>
