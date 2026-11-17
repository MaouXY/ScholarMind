<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  Document,
  Download,
  Reading,
  Refresh,
  Star,
  StarFilled,
} from '@element-plus/icons-vue'

import {
  favoriteLiterature,
  getLiteratureDownload,
  getLiteratureView,
  listFavoriteFolders,
  unfavoriteLiterature,
} from '@/api/literature'
import {
  createCitation,
  deleteCitation,
  listCitations,
  listLiteratureCitations,
} from '@/api/citation'
import { downloadCitationReferences, openLiteratureFile } from '@/utils/download'
import { displayCategoryName, displayLiteratureType } from '@/utils/format'
import AiSummaryPanel from '@/components/AiSummaryPanel.vue'
import type {
  CitationRecord,
  CitationStyle,
  CitationStyleItem,
} from '@/types/citation'
import type { FavoriteFolder, Id, Literature } from '@/types/literature'
import '@/styles/literature-ext.css'

const route = useRoute()
const router = useRouter()

const literatureId = computed<Id>(() => {
  const raw = route.params.id
  return Array.isArray(raw) ? String(raw[0] ?? '') : String(raw ?? '')
})

const literature = ref<Literature | null>(null)
const favorited = ref(false)
const loadFailed = ref(false)
const loading = ref(false)

/** 收藏夹：收藏时可选择归属，未选择则由后端放入默认收藏夹 */
const favoriteLoading = ref(false)
const folders = ref<FavoriteFolder[]>([])
const selectedFolderId = ref<Id | undefined>(undefined)
const folderPickerVisible = ref(false)

// 引用：三种格式由后端即时生成，当前选中格式决定导出内容
const citationItems = ref<CitationStyleItem[]>([])
const citationRecords = ref<CitationRecord[]>([])
const citationTotal = ref(0)
const activeCitationStyle = ref<CitationStyle | null>(null)
const citationLoading = ref(false)
const citationListLoading = ref(false)
const citationSaving = ref(false)
const citationExporting = ref(false)
const citationRemovingId = ref<Id | null>(null)
const citationFailed = ref(false)

const downloadLoading = ref(false)

const keywordTokens = computed(() =>
  (literature.value?.keywords ?? '')
    .split(/[,，;；、]/)
    .map((item) => item.trim())
    .filter(Boolean),
)

const abstractParagraphs = computed(() =>
  (literature.value?.abstractText ?? '')
    .split(/\n+/)
    .map((item) => item.trim())
    .filter(Boolean),
)

/** 未绑定原文时下载按钮置灰，避免用户点了才收到 14107 报错 */
const hasOriginalFile = computed(() => Boolean(literature.value?.fileId))
const downloadDisabledReason = computed(() =>
  hasOriginalFile.value ? '' : '该文献未绑定原文文件，可联系管理员补充附件',
)

const activeCitation = computed(
  () =>
    citationItems.value.find(
      (item) => item.style === activeCitationStyle.value,
    ) ?? null,
)

const citationStyleCount = computed(() => citationItems.value.length)

/** 后端已收藏的引用格式集合（本次查询的文献内去重） */
const collectedCitationStyles = computed(() => {
  const styles = citationRecords.value.map((record) => record.style)
  return new Set<CitationStyle>(styles)
})

async function loadDetail() {
  const id = literatureId.value
  if (!id) {
    loadFailed.value = true
    ElMessage.warning('缺少文献 ID，无法加载详情')
    return
  }

  loading.value = true
  loadFailed.value = false
  try {
    const response = await getLiteratureView(id)
    literature.value = response.literature
    favorited.value = response.favorited
  } catch (error) {
    console.error(
      'literature detail view failed, scenario=literature-detail-view, literatureId=%s',
      id,
      error,
    )
    literature.value = null
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

/** 收藏夹列表只影响收藏入口，失败时降级为「不选夹」 */
async function loadFolders() {
  try {
    folders.value = await listFavoriteFolders()
  } catch (error) {
    console.error(
      'favorite folder list failed, scenario=favorite-folder-list',
      error,
    )
    folders.value = []
  }
}

async function confirmFavorite() {
  const id = literature.value?.id ?? literatureId.value
  if (!id || favoriteLoading.value) return

  favoriteLoading.value = true
  try {
    await favoriteLiterature(id, selectedFolderId.value ?? null)
    favorited.value = true
    folderPickerVisible.value = false
    const folderName = folders.value.find(
      (item) => item.id === selectedFolderId.value,
    )?.name
    ElMessage.success(folderName ? `已收藏到「${folderName}」` : '已收藏该文献')
    await loadFolders()
  } catch (error) {
    console.error(
      'favorite create failed, scenario=literature-favorite-create, literatureId=%s, folderId=%s',
      id,
      selectedFolderId.value,
      error,
    )
  } finally {
    favoriteLoading.value = false
  }
}

async function cancelFavorite() {
  const id = literature.value?.id ?? literatureId.value
  if (!id || favoriteLoading.value) return

  favoriteLoading.value = true
  try {
    await unfavoriteLiterature(id)
    favorited.value = false
    ElMessage.success('已取消收藏')
  } catch (error) {
    console.error(
      'favorite delete failed, scenario=literature-favorite-delete, literatureId=%s',
      id,
      error,
    )
  } finally {
    favoriteLoading.value = false
  }
}

function openReader() {
  router.push(`/student/literature/${literatureId.value}/read`)
}

function goSearch() {
  router.push('/student/search')
}

function goShelf() {
  router.push('/student/shelf')
}

onMounted(async () => {
  await Promise.all([loadDetail(), loadFolders()])
  if (!loadFailed.value) {
    await Promise.all([loadCitations(), loadCitationRecords()])
  }
})

/** 加载三种引用格式，默认选中第一种 */
async function loadCitations() {
  const id = literature.value?.id ?? literatureId.value
  if (!id) return

  citationLoading.value = true
  citationFailed.value = false
  try {
    const result = await listLiteratureCitations(id)
    const items = Array.isArray(result) ? result : []
    citationItems.value = items
    if (!items.some((item) => item.style === activeCitationStyle.value)) {
      activeCitationStyle.value = items[0]?.style ?? null
    }
  } catch (error) {
    console.error(
      'literature citation styles failed, scenario=literature-citation-styles, literatureId=%s',
      id,
      error,
    )
    citationItems.value = []
    activeCitationStyle.value = null
    citationFailed.value = true
  } finally {
    citationLoading.value = false
  }
}

/** 加载该文献已收藏的引用（第一页 5 条） */
async function loadCitationRecords() {
  const id = literature.value?.id ?? literatureId.value
  if (!id) return

  citationListLoading.value = true
  try {
    const result = await listCitations({ page: 1, size: 5, literatureId: id })
    citationRecords.value = result.items
    citationTotal.value = result.total
  } catch (error) {
    console.error(
      'citation list failed, scenario=citation-list, literatureId=%s',
      id,
      error,
    )
    citationRecords.value = []
    citationTotal.value = 0
  } finally {
    citationListLoading.value = false
  }
}

/** 优先使用剪贴板 API，http 源等不可用场景回落到隐藏 textarea + execCommand */
async function copyText(text: string, label = '引用') {
  const content = text.trim()
  if (!content) {
    ElMessage.warning('暂无可复制的内容')
    return
  }

  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(content)
      ElMessage.success(`${label}已复制到剪贴板`)
      return
    } catch (error) {
      console.error(
        'clipboard write failed, scenario=citation-clipboard-write, label=%s',
        label,
        error,
      )
    }
  }

  const textarea = document.createElement('textarea')
  textarea.value = content
  textarea.setAttribute('readonly', 'readonly')
  textarea.style.position = 'fixed'
  textarea.style.top = '-1000px'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  try {
    textarea.select()
    textarea.setSelectionRange(0, content.length)
    const copied = document.execCommand('copy')
    if (copied) {
      ElMessage.success(`${label}已复制到剪贴板`)
    } else {
      ElMessage.error('复制失败，请手动选中引用文本后复制')
    }
  } catch (error) {
    console.error(
      'clipboard fallback failed, scenario=citation-clipboard-fallback, label=%s',
      label,
      error,
    )
    ElMessage.error('当前浏览器不支持自动复制，请手动选中引用文本后复制')
  } finally {
    document.body.removeChild(textarea)
  }
}

function selectCitationStyle(style: CitationStyle) {
  activeCitationStyle.value = style
}

/** 收藏当前格式的引用，成功后刷新已收藏列表 */
async function collectCitation(item: CitationStyleItem) {
  const id = literature.value?.id ?? literatureId.value
  if (!id) return

  citationSaving.value = true
  try {
    await createCitation({ literatureId: id, style: item.style })
    activeCitationStyle.value = item.style
    ElMessage.success('已收藏该格式引用')
    await loadCitationRecords()
  } catch (error) {
    console.error(
      'citation create failed, scenario=citation-create, literatureId=%s, style=%s',
      id,
      item.style,
      error,
    )
  } finally {
    citationSaving.value = false
  }
}

async function removeCitation(record: CitationRecord) {
  citationRemovingId.value = record.id
  try {
    await deleteCitation(record.id)
    ElMessage.success('已取消收藏该引用')
    await loadCitationRecords()
  } catch (error) {
    console.error(
      'citation delete failed, scenario=citation-delete, citationId=%s, literatureId=%s',
      record.id,
      record.literatureId,
      error,
    )
  } finally {
    citationRemovingId.value = null
  }
}

/** 按当前选中格式导出参考文献附件（blob + Authorization） */
async function exportReferences() {
  const item = activeCitation.value
  if (!item) {
    ElMessage.warning('请先选择一种引用格式')
    return
  }

  citationExporting.value = true
  try {
    const fileName = await downloadCitationReferences(
      item.style,
      item.styleLabel,
    )
    ElMessage.success(`已导出参考文献：${fileName}`)
  } catch (error) {
    console.error(
      'citation export failed, scenario=citation-export, literatureId=%s, style=%s',
      literatureId.value,
      item.style,
      error,
    )
    ElMessage.error('导出参考文献失败，请稍后重试')
  } finally {
    citationExporting.value = false
  }
}

/** 换取预签名地址后打开原文 */
async function downloadOriginal() {
  const id = literature.value?.id ?? literatureId.value
  if (!id) return

  downloadLoading.value = true
  try {
    const ticket = await getLiteratureDownload(id)
    await openLiteratureFile(ticket)
    ElMessage.success(`已开始下载：${ticket.fileName}`)
  } catch (error) {
    console.error(
      'literature download failed, scenario=literature-download, literatureId=%s',
      id,
      error,
    )
    if ((error as { code?: number } | null)?.code === 14107) {
      ElMessage.warning('该文献未绑定原文文件，可联系管理员补充附件')
    }
  } finally {
    downloadLoading.value = false
  }
}
</script>

<template>
  <section class="library-page">
    <section v-if="loading" class="glass-panel" aria-label="文献详情加载中">
      <el-skeleton :rows="8" animated />
    </section>

    <section
      v-else-if="loadFailed || !literature"
      class="glass-panel"
      aria-label="文献详情异常"
    >
      <el-empty description="文献加载失败或不存在，请返回检索页重新选择">
        <div class="detail-actions">
          <el-button :icon="Refresh" type="primary" @click="loadDetail"
            >重新加载</el-button
          >
          <el-button :icon="ArrowLeft" plain @click="goSearch"
            >返回检索</el-button
          >
        </div>
      </el-empty>
    </section>

    <template v-else>
      <!-- 题录头部：左栏读题录，右栏指标与操作 -->
      <section class="glass-panel" aria-label="文献信息">
        <div class="detail-head">
          <div class="detail-head__main">
            <p class="panel__eyebrow">
              {{ displayCategoryName(literature.categoryName ?? null) }}
            </p>
            <h2>{{ literature.title }}</h2>

            <p class="detail-head__line">
              <span>{{ literature.author || '作者未标注' }}</span>
              <em>·</em>
              <span>{{ literature.source || '来源未标注' }}</span>
            </p>
            <p class="detail-head__line detail-head__line--muted">
              <span>发表 {{ literature.publishDate || '未标注' }}</span>
              <em>·</em>
              <span>在线公开 {{ literature.onlinePublishDate || '未标注' }}</span>
            </p>

            <div class="detail-head__tags">
              <el-tag size="small" effect="plain">{{
                displayLiteratureType(literature.literatureType)
              }}</el-tag>
              <span
                v-for="token in keywordTokens"
                :key="token"
                class="detail-keyword"
                >{{ token }}</span
              >
            </div>
          </div>

          <aside class="detail-side">
            <dl class="detail-side__stats">
              <div>
                <dt>被引</dt>
                <dd>{{ literature.citationCount ?? 0 }}</dd>
              </div>
              <div>
                <dt>下载</dt>
                <dd>{{ literature.downloadCount ?? 0 }}</dd>
              </div>
              <div>
                <dt>阅读</dt>
                <dd>{{ literature.readCount ?? 0 }}</dd>
              </div>
            </dl>

            <div class="detail-side__actions">
              <el-button
                :icon="Document"
                type="primary"
                class="detail-side__primary"
                @click="openReader"
                >HTML 阅读</el-button
              >
              <el-tooltip
                :content="downloadDisabledReason"
                :disabled="hasOriginalFile"
                placement="top"
              >
                <span class="detail-action-wrap">
                  <el-button
                    :icon="Download"
                    :loading="downloadLoading"
                    :disabled="!hasOriginalFile"
                    @click="downloadOriginal"
                  >
                    PDF 下载
                  </el-button>
                </span>
              </el-tooltip>

              <el-popover
                v-if="!favorited"
                v-model:visible="folderPickerVisible"
                placement="left-start"
                :width="260"
                trigger="click"
              >
                <template #reference>
                  <el-button :icon="Star" :loading="favoriteLoading">
                    收藏
                  </el-button>
                </template>
                <div class="favorite-picker">
                  <p class="favorite-picker__label">收藏到</p>
                  <el-select
                    v-model="selectedFolderId"
                    class="favorite-picker__select"
                    placeholder="默认收藏夹"
                    clearable
                  >
                    <el-option
                      v-for="folder in folders"
                      :key="folder.id"
                      :label="folder.name"
                      :value="folder.id"
                    />
                  </el-select>
                  <div class="favorite-picker__actions">
                    <el-button text @click="folderPickerVisible = false"
                      >取消</el-button
                    >
                    <el-button
                      type="primary"
                      :loading="favoriteLoading"
                      @click="confirmFavorite"
                    >
                      确定收藏
                    </el-button>
                  </div>
                </div>
              </el-popover>
              <el-button
                v-else
                :icon="StarFilled"
                type="success"
                plain
                :loading="favoriteLoading"
                @click="cancelFavorite"
              >
                已收藏
              </el-button>

              <el-button
                :icon="Reading"
                text
                class="detail-side__link"
                @click="goShelf"
                >我的书房</el-button
              >
            </div>
          </aside>
        </div>
      </section>

      <!-- 摘要：AI 摘要 + 摘要原文 -->
      <div class="paper-stack">
        <section class="glass-panel" aria-label="AI 摘要">
          <div class="panel-heading">
            <div>
              <h3>AI 摘要</h3>
            </div>
          </div>
          <AiSummaryPanel :summary="literature.aiSummary" density="compact" />
        </section>

        <section class="glass-panel" aria-label="摘要原文">
          <div class="panel-heading">
            <div>
              <h3>摘要</h3>
            </div>
          </div>
          <template v-if="abstractParagraphs.length">
            <p v-for="(paragraph, index) in abstractParagraphs" :key="index">
              {{ paragraph }}
            </p>
          </template>
          <el-empty v-else description="该文献暂未填写摘要" />
        </section>
      </div>

      <!-- 引用参考文献 -->
      <section class="glass-panel" aria-label="引用参考文献">
        <div class="panel-heading">
          <div>
            <h3>引用参考文献</h3>
          </div>
          <div class="detail-actions">
            <el-tag effect="plain">{{ citationStyleCount }} 种格式</el-tag>
            <el-tag effect="plain" type="success"
              >已收藏 {{ citationTotal }} 条</el-tag
            >
            <el-button
              :icon="Refresh"
              plain
              :loading="citationLoading || citationListLoading"
              @click="loadCitations"
            >
              刷新引用
            </el-button>
          </div>
        </div>

        <section v-if="citationLoading" aria-label="引用格式加载中">
          <el-skeleton :rows="4" animated />
        </section>

        <el-empty
          v-else-if="citationFailed || !citationStyleCount"
          description="引用格式生成失败或暂不可用，可稍后点击「刷新引用」重试"
        />

        <template v-else>
          <div class="citation-grid">
            <article
              v-for="item in citationItems"
              :key="item.style"
              class="citation-card"
              :class="{ 'is-active': item.style === activeCitationStyle }"
              @click="selectCitationStyle(item.style)"
            >
              <header class="citation-card__head">
                <strong>{{ item.styleLabel || item.style }}</strong>
                <el-tag
                  v-if="collectedCitationStyles.has(item.style)"
                  size="small"
                  type="success"
                >
                  已收藏
                </el-tag>
              </header>
              <p class="citation-card__text">
                {{ item.text || '该格式暂未生成引用文本' }}
              </p>
              <div class="citation-card__actions">
                <el-button
                  text
                  type="primary"
                  @click="copyText(item.text, '引用')"
                  >复制</el-button
                >
                <el-button
                  text
                  :type="
                    collectedCitationStyles.has(item.style)
                      ? 'success'
                      : 'primary'
                  "
                  :loading="citationSaving"
                  :disabled="collectedCitationStyles.has(item.style)"
                  @click="collectCitation(item)"
                >
                  {{
                    collectedCitationStyles.has(item.style)
                      ? '已收藏'
                      : '收藏引用'
                  }}
                </el-button>
              </div>
            </article>
          </div>

          <div class="detail-actions citation-export-bar">
            <el-button
              :icon="Download"
              type="primary"
              plain
              :loading="citationExporting"
              @click="exportReferences"
            >
              导出参考文献（{{ activeCitation?.styleLabel || '未选择格式' }}）
            </el-button>
          </div>

          <section class="citation-records" aria-label="已收藏引用">
            <div class="panel-heading">
              <div>
                <h3>本篇已收藏引用</h3>
              </div>
              <el-tag effect="plain">{{ citationTotal }} 条</el-tag>
            </div>

            <div v-loading="citationListLoading">
              <div class="citation-list" v-if="citationRecords.length">
                <article
                  v-for="record in citationRecords"
                  :key="record.id"
                  class="citation-record"
                >
                  <div>
                    <el-tag size="small" effect="plain">
                      {{ record.styleLabel || record.style }}
                    </el-tag>
                    <h4>{{ record.citationText }}</h4>
                    <p v-if="record.note">备注：{{ record.note }}</p>
                  </div>
                  <div class="note-actions">
                    <el-button
                      text
                      @click="copyText(record.citationText, '引用')"
                      >复制</el-button
                    >
                    <el-button
                      text
                      type="danger"
                      :loading="citationRemovingId === record.id"
                      @click="removeCitation(record)"
                    >
                      取消收藏
                    </el-button>
                  </div>
                </article>
              </div>
              <el-empty
                v-else
                description="本篇文献暂未收藏引用，点击上方「收藏引用」即可沉淀题录"
              />
            </div>
          </section>
        </template>
      </section>
    </template>
  </section>
</template>

<style scoped>
/* 禁用态按钮不派发鼠标事件，用一层 span 承接 tooltip 的 hover */
.detail-action-wrap {
  display: inline-flex;
}

/* 头部两栏：左题录、右指标与操作；窄屏时上下堆叠 */
.detail-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 248px;
  gap: 32px;
  align-items: start;
}

.detail-head__main {
  min-width: 0;
}

.detail-head__main h2 {
  margin: 10px 0 14px;
  font-size: 28px;
  line-height: 1.32;
  font-weight: 700;
  color: var(--color-text);
  overflow-wrap: anywhere;
}

.detail-head__line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: 0 0 6px;
  color: var(--color-text);
  font-size: 14px;
}

.detail-head__line em {
  color: var(--color-subtle);
  font-style: normal;
}

.detail-head__line--muted {
  color: var(--color-muted);
  font-size: 13px;
}

.detail-head__tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
}

.detail-keyword {
  padding: 2px 9px;
  border-radius: 4px;
  background: #eef2f7;
  color: var(--color-muted);
  font-size: 12px;
  line-height: 20px;
}

.detail-side {
  display: grid;
  gap: 16px;
  padding: 16px;
  border-radius: var(--radius);
  border: 1px solid rgba(105, 132, 161, 0.14);
  background: rgba(248, 250, 253, 0.9);
}

.detail-side__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
  text-align: center;
}

.detail-side__stats div {
  display: grid;
  gap: 2px;
}

.detail-side__stats dt {
  color: var(--color-subtle);
  font-size: 12px;
}

.detail-side__stats dd {
  margin: 0;
  color: var(--color-text);
  font-size: 19px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.detail-side__actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.detail-side__actions :deep(.el-button) {
  margin-left: 0;
  width: 100%;
}

.detail-side__actions .detail-action-wrap {
  display: flex;
}

.detail-side__actions .detail-action-wrap :deep(.el-button) {
  width: 100%;
}

/* 主操作独占一整行，下载与收藏并排在下一行 */
.detail-side__primary {
  grid-column: 1 / -1;
}

/* 我的书房是次要出口，占满整行并弱化为文字按钮 */
.detail-side__link {
  grid-column: 1 / -1;
  color: var(--color-muted);
  justify-content: center;
}

@media (max-width: 1180px) {
  .detail-head {
    grid-template-columns: minmax(0, 1fr) 216px;
    gap: 24px;
  }
}

@media (max-width: 900px) {
  .detail-head {
    grid-template-columns: minmax(0, 1fr);
  }

  .detail-head__main h2 {
    font-size: 23px;
  }
}

.favorite-picker__label {
  margin: 0 0 8px;
  color: var(--color-muted);
  font-size: 12px;
}

.favorite-picker__select {
  width: 100%;
}

.favorite-picker__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}
</style>
