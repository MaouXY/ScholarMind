<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Delete,
  Document,
  Download,
  Notebook,
  Reading,
  Refresh,
  StarFilled,
  Timer,
} from '@element-plus/icons-vue'

import {
  deleteReadingNote,
  listFavorites,
  listReadingHistory,
  listReadingNotes,
  unfavoriteLiterature,
} from '@/api/literature'
import { downloadReadingNotes } from '@/utils/download'
import { displayCategoryName, displayStatusName } from '@/utils/format'
import type {
  Favorite,
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
const favoriteQuery = ref({ page: 1, size: 10 })

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

function statusTagType(status?: string | null) {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'REJECTED') return 'danger'
  if (status === 'PENDING_REVIEW') return 'warning'
  return 'info'
}

function visibilityText(visibility?: string | null) {
  if (visibility === 'PUBLIC') return '公开'
  if (visibility === 'PRIVATE') return '私有'
  return '可见性未知'
}

/* ----------------------------- 收藏：数据 ----------------------------- */
async function loadFavorites() {
  favoritesLoading.value = true
  try {
    const result = await listFavorites({ ...favoriteQuery.value })
    favorites.value = result.items
    favoritesTotal.value = result.total
  } catch (error) {
    console.error(
      'favorite list failed, scenario=favorite-list, page=%s, size=%s',
      favoriteQuery.value.page,
      favoriteQuery.value.size,
      error,
    )
    favorites.value = []
    favoritesTotal.value = 0
  } finally {
    favoritesLoading.value = false
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
    await loadFavorites()
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
  router.push('/student/library')
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
  await Promise.all([loadFavorites(), loadHistory(), loadNotes()])
})
</script>

<template>
  <section class="library-page">
    <div class="spatial-hero compact-hero">
      <div>
        <p class="panel__eyebrow">My Reading Shelf</p>
        <h2>我的书房：收藏、阅读历史与阅读笔记集中管理</h2>
        <p>
          收藏夹沉淀感兴趣的文献，阅读历史记录每次停留的页码与时长，阅读笔记汇总手动批注与
          AI 解析结果，三处数据都可一键回到原文继续阅读。
        </p>
      </div>
      <div class="hero-stats">
        <span>
          <strong>{{ favoritesTotal }}</strong>
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
      </div>
    </div>

    <section class="glass-panel" aria-label="我的书房">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="收藏" name="favorites">
          <div class="shelf-pane">
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
                  去文献库
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
                      <el-tag
                        size="small"
                        effect="plain"
                        :type="statusTagType(item.status)"
                      >
                        {{ displayStatusName(item.status) }}
                      </el-tag>
                      <span>{{ item.author || '未知作者' }}</span>
                      <span>
                        {{ displayCategoryName(item.categoryName ?? null) }}
                      </span>
                      <span>{{ visibilityText(item.visibility) }}</span>
                      <span>阅读 {{ item.readCount ?? 0 }} 次</span>
                      <span>收藏于 {{ formatDateTime(item.createdAt) }}</span>
                    </div>
                  </div>
                  <div class="shelf-card__actions">
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
                description="还没有收藏文献，去文献库挑几篇感兴趣的论文吧"
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
                      <el-tag
                        size="small"
                        effect="plain"
                        :type="statusTagType(item.status)"
                      >
                        {{ displayStatusName(item.status) }}
                      </el-tag>
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
  </section>
</template>
