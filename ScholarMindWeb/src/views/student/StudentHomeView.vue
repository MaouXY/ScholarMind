<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  DocumentAdd,
  EditPen,
  Reading,
  Search,
  UploadFilled,
} from '@element-plus/icons-vue'

import {
  createLiterature,
  listCategories,
  listLiteratures,
  markLiteratureRead,
  submitLiterature,
  updateLiterature,
  uploadLiteratureFile,
} from '@/api/literature'
import type {
  Literature,
  LiteratureCategory,
  LiteraturePayload,
  LiteratureStatus,
  LiteratureVisibility,
} from '@/types/literature'

const demoLiteratures: Literature[] = [
  {
    id: 1200000000000000001,
    title: 'Research on Intelligent Recommendation Service in University Digital Library',
    author: 'Li Ming',
    source: 'Library Information Service',
    publishYear: 2024,
    language: 'zh-CN',
    categoryName: 'Recommendation System',
    uploaderId: 1000000000000000003,
    visibility: 'PUBLIC',
    status: 'PUBLISHED',
    abstractText:
      'This paper discusses personalized resource recommendation for university digital libraries and proposes a hybrid method based on keywords and user behavior.',
    aiSummary:
      '论文围绕高校数字图书馆的个性化推荐服务展开，重点比较关键词匹配与用户行为建模的协同效果，适合作为推荐系统与数字图书馆结合方向的综述入口。',
    keywords: 'digital library,recommendation system,user behavior,personalization',
    readCount: 36,
    downloadCount: 8,
  },
  {
    id: 1200000000000000002,
    title: 'Semantic Retrieval Method for Academic Literature Based on User Intent',
    author: 'Wang Qian',
    source: 'Modern Information',
    publishYear: 2023,
    language: 'zh-CN',
    categoryName: 'Information Retrieval',
    uploaderId: 1000000000000000003,
    visibility: 'PUBLIC',
    status: 'PUBLISHED',
    abstractText:
      'This study analyzes natural language search intent and improves academic literature discovery through structured retrieval conditions.',
    aiSummary:
      '文章从自然语言检索意图识别切入，把用户问题拆解为主题、方法、对象和约束条件，对构建 AI 文献检索助手的查询理解模块很有参考价值。',
    keywords: 'semantic retrieval,natural language,academic literature,user intent',
    readCount: 28,
    downloadCount: 5,
  },
]

const categories = ref<LiteratureCategory[]>([])
const literatures = ref<Literature[]>([])
const selected = ref<Literature | null>(null)
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const editorVisible = ref(false)
const editorMode = ref<'create' | 'edit'>('create')
const readerNotes = ref('')

const query = reactive({
  page: 1,
  size: 8,
  keyword: '',
  categoryId: undefined as number | undefined,
  status: undefined as LiteratureStatus | undefined,
  visibility: undefined as LiteratureVisibility | undefined,
})

const form = reactive<LiteraturePayload>({
  title: '',
  author: '',
  source: '',
  publishYear: new Date().getFullYear(),
  language: 'zh-CN',
  categoryId: undefined,
  visibility: 'PRIVATE',
  fileId: undefined,
  abstractText: '',
  keywords: '',
})

const total = ref(0)

const keywordTokens = computed(() =>
  (selected.value?.keywords ?? '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean),
)

const readingProgress = computed(() => {
  const readCount = selected.value?.readCount ?? 0
  return Math.min(92, Math.max(18, readCount * 2))
})

function statusLabel(status?: LiteratureStatus) {
  const labels: Record<LiteratureStatus, string> = {
    DRAFT: '草稿',
    PENDING_REVIEW: '审核中',
    PUBLISHED: '已发布',
    REJECTED: '已退回',
  }
  return status ? labels[status] : '全部'
}

function visibilityLabel(visibility?: LiteratureVisibility) {
  if (visibility === 'PUBLIC') return '公开'
  if (visibility === 'PRIVATE') return '私有'
  return '全部'
}

function resetForm() {
  Object.assign(form, {
    title: '',
    author: '',
    source: '',
    publishYear: new Date().getFullYear(),
    language: 'zh-CN',
    categoryId: undefined,
    visibility: 'PRIVATE',
    fileId: undefined,
    abstractText: '',
    keywords: '',
  })
}

function fillForm(item: Literature) {
  Object.assign(form, {
    title: item.title,
    author: item.author,
    source: item.source,
    publishYear: item.publishYear,
    language: item.language || 'zh-CN',
    categoryId: item.categoryId ?? undefined,
    visibility: item.visibility,
    fileId: item.fileId ?? undefined,
    abstractText: item.abstractText,
    keywords: item.keywords,
  })
}

async function fetchCategories() {
  try {
    categories.value = await listCategories()
  } catch (error) {
    console.error('category load failed, scenario=literature-category-list', error)
    categories.value = [
      { id: 1100000000000000001, name: 'Digital Library', sort: 10, status: 1 },
      { id: 1100000000000000002, name: 'Information Retrieval', sort: 20, status: 1 },
      { id: 1100000000000000003, name: 'Artificial Intelligence', sort: 30, status: 1 },
    ]
  }
}

async function fetchLiteratures() {
  loading.value = true
  try {
    const result = await listLiteratures(query)
    literatures.value = result.items
    total.value = result.total
    if (!selected.value && result.items.length > 0) {
      selected.value = result.items[0]
    }
  } catch (error) {
    console.error(
      'literature list failed, scenario=literature-search, keyword=%s, categoryId=%s, status=%s',
      query.keyword,
      query.categoryId,
      query.status,
      error,
    )
    literatures.value = demoLiteratures
    total.value = demoLiteratures.length
    selected.value = selected.value ?? demoLiteratures[0]
  } finally {
    loading.value = false
  }
}

async function chooseLiterature(item: Literature) {
  selected.value = item
  readerNotes.value = ''
  try {
    selected.value = await markLiteratureRead(item.id)
  } catch (error) {
    console.error(
      'read count failed, scenario=literature-read, literatureId=%s',
      item.id,
      error,
    )
  }
}

function openCreate() {
  editorMode.value = 'create'
  resetForm()
  editorVisible.value = true
}

function openEdit() {
  if (!selected.value) return
  editorMode.value = 'edit'
  fillForm(selected.value)
  editorVisible.value = true
}

async function saveLiterature() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写文献标题')
    return
  }

  saving.value = true
  try {
    const saved =
      editorMode.value === 'edit' && selected.value
        ? await updateLiterature(selected.value.id, form)
        : await createLiterature(form)
    selected.value = saved
    editorVisible.value = false
    ElMessage.success('文献已保存')
    await fetchLiteratures()
  } catch (error) {
    console.error(
      'literature save failed, scenario=literature-save, mode=%s, literatureId=%s, title=%s',
      editorMode.value,
      selected.value?.id,
      form.title,
      error,
    )
  } finally {
    saving.value = false
  }
}

async function submitCurrent() {
  if (!selected.value) return
  try {
    selected.value = await submitLiterature(selected.value.id)
    ElMessage.success('已提交审核')
    await fetchLiteratures()
  } catch (error) {
    console.error(
      'literature submit failed, scenario=literature-submit, literatureId=%s',
      selected.value.id,
      error,
    )
  }
}

async function handleNativeFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  uploading.value = true
  try {
    const uploaded = await uploadLiteratureFile(file)
    form.fileId = uploaded.id
    ElMessage.success('文件已上传并绑定到表单')
  } catch (error) {
    console.error(
      'file upload failed, scenario=literature-file-upload, filename=%s, size=%s',
      file.name,
      file.size,
      error,
    )
  } finally {
    uploading.value = false
    input.value = ''
  }
}

onMounted(async () => {
  await fetchCategories()
  await fetchLiteratures()
})
</script>

<template>
  <section class="library-page">
    <div class="spatial-hero">
      <div>
        <p class="panel__eyebrow">AI Literature Reading</p>
        <h2>把检索、精读和笔记放在同一个空间里</h2>
        <p>
          先通过主题、作者、关键词锁定论文，再在右侧阅读面板中查看摘要、关键词、阅读进度和笔记。
        </p>
      </div>
      <div class="hero-orbit" aria-hidden="true">
        <span>PDF</span>
        <span>RAG</span>
        <span>AI</span>
      </div>
    </div>

    <div class="library-layout">
      <aside class="glass-panel search-panel" aria-label="文献检索">
        <div class="panel-heading">
          <div>
            <p class="panel__eyebrow">Search Lens</p>
            <h3>检索条件</h3>
          </div>
          <el-button :icon="DocumentAdd" type="primary" @click="openCreate">
            上传文献
          </el-button>
        </div>

        <el-form label-position="top">
          <el-form-item label="关键词">
            <el-input
              v-model="query.keyword"
              :prefix-icon="Search"
              placeholder="标题、作者、关键词、摘要"
              clearable
              @keyup.enter="fetchLiteratures"
            />
          </el-form-item>
          <el-form-item label="学科分类">
            <el-select v-model="query.categoryId" clearable placeholder="全部分类">
              <el-option
                v-for="category in categories"
                :key="category.id"
                :label="category.name"
                :value="category.id"
              />
            </el-select>
          </el-form-item>
          <div class="filter-grid">
            <el-form-item label="可见性">
              <el-select v-model="query.visibility" clearable>
                <el-option label="全部" value="" />
                <el-option label="公开" value="PUBLIC" />
                <el-option label="私有" value="PRIVATE" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="query.status" clearable>
                <el-option label="草稿" value="DRAFT" />
                <el-option label="审核中" value="PENDING_REVIEW" />
                <el-option label="已发布" value="PUBLISHED" />
                <el-option label="已退回" value="REJECTED" />
              </el-select>
            </el-form-item>
          </div>
          <el-button type="primary" class="wide-action" :loading="loading" @click="fetchLiteratures">
            执行检索
          </el-button>
        </el-form>

        <div class="metric-row">
          <span>
            <strong>{{ total }}</strong>
            命中文献
          </span>
          <span>
            <strong>{{ categories.length }}</strong>
            分类
          </span>
        </div>
      </aside>

      <section class="result-stack" aria-label="检索结果">
        <article
          v-for="item in literatures"
          :key="item.id"
          class="paper-card"
          :class="{ 'is-active': selected?.id === item.id }"
          @click="chooseLiterature(item)"
        >
          <div class="paper-card__status">
            <el-tag size="small" effect="plain">{{ statusLabel(item.status) }}</el-tag>
            <span>{{ visibilityLabel(item.visibility) }}</span>
          </div>
          <h3>{{ item.title }}</h3>
          <p>{{ item.abstractText || '暂无摘要，建议上传 PDF 后补充摘要和关键词。' }}</p>
          <div class="paper-card__meta">
            <span>{{ item.author || '未知作者' }}</span>
            <span>{{ item.source || '未填写来源' }}</span>
            <span>{{ item.publishYear || '年份未知' }}</span>
          </div>
        </article>
      </section>

      <section class="reader-surface" aria-label="论文阅读器">
        <template v-if="selected">
          <div class="reader-toolbar">
            <el-button :icon="Reading" type="primary" plain @click="chooseLiterature(selected)">
              标记阅读
            </el-button>
            <el-button :icon="EditPen" plain @click="openEdit">编辑信息</el-button>
            <el-button
              plain
              :disabled="selected.visibility !== 'PUBLIC'"
              @click="submitCurrent"
            >
              提交审核
            </el-button>
          </div>

          <article class="reader-paper">
            <p class="panel__eyebrow">{{ selected.categoryName || 'Unclassified' }}</p>
            <h2>{{ selected.title }}</h2>
            <div class="reader-meta">
              <span>{{ selected.author || '未知作者' }}</span>
              <span>{{ selected.source || '未填写来源' }}</span>
              <span>{{ selected.publishYear || '年份未知' }}</span>
            </div>

            <div class="progress-line">
              <span :style="{ width: `${readingProgress}%` }" />
            </div>

            <section class="reader-section">
              <h3>AI 摘要</h3>
              <p>
                {{
                  selected.aiSummary ||
                  selected.abstractText ||
                  '暂无 AI 摘要。后续可接入 RAG 问答和摘要生成，把论文核心问题、方法、数据集和结论自动抽取出来。'
                }}
              </p>
            </section>

            <section class="reader-section">
              <h3>摘要</h3>
              <p>{{ selected.abstractText || '暂无摘要。' }}</p>
            </section>

            <section class="keyword-ribbon" v-if="keywordTokens.length">
              <span v-for="token in keywordTokens" :key="token">{{ token }}</span>
            </section>

            <section class="reading-grid">
              <div>
                <h3>阅读提纲</h3>
                <ol>
                  <li>研究问题与使用场景</li>
                  <li>核心方法、模型或系统架构</li>
                  <li>实验指标、数据来源和局限</li>
                  <li>可复用到毕业设计的模块</li>
                </ol>
              </div>
              <div>
                <h3>追问清单</h3>
                <ol>
                  <li>这篇论文解决的是检索、推荐还是阅读辅助问题？</li>
                  <li>哪些变量能变成系统的数据库字段？</li>
                  <li>是否适合进入 RAG 知识库作为回答依据？</li>
                </ol>
              </div>
            </section>

            <el-input
              v-model="readerNotes"
              type="textarea"
              :rows="5"
              resize="none"
              placeholder="记录你的批注、引用句、复现实验想法或论文对比结论"
            />
          </article>
        </template>
        <el-empty v-else description="检索后选择一篇文献开始阅读" />
      </section>
    </div>

    <el-drawer
      v-model="editorVisible"
      :title="editorMode === 'create' ? '上传文献' : '编辑文献'"
      size="520px"
    >
      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="form.title" />
        </el-form-item>
        <div class="filter-grid">
          <el-form-item label="作者">
            <el-input v-model="form.author" />
          </el-form-item>
          <el-form-item label="年份">
            <el-input-number v-model="form.publishYear" :min="1900" :max="2100" />
          </el-form-item>
        </div>
        <el-form-item label="来源">
          <el-input v-model="form.source" />
        </el-form-item>
        <div class="filter-grid">
          <el-form-item label="分类">
            <el-select v-model="form.categoryId" clearable placeholder="选择分类">
              <el-option
                v-for="category in categories"
                :key="category.id"
                :label="category.name"
                :value="category.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="可见性">
            <el-segmented
              v-model="form.visibility"
              :options="[
                { label: '私有', value: 'PRIVATE' },
                { label: '公开', value: 'PUBLIC' },
              ]"
            />
          </el-form-item>
        </div>
        <el-form-item label="关键词">
          <el-input v-model="form.keywords" placeholder="用英文逗号分隔" />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="form.abstractText" type="textarea" :rows="5" />
        </el-form-item>
        <label class="upload-drop">
          <el-icon><UploadFilled /></el-icon>
          <span>{{ form.fileId ? `已绑定文件 #${form.fileId}` : '选择 PDF 或文本文件' }}</span>
          <input type="file" accept=".pdf,.txt,application/pdf,text/plain" @change="handleNativeFile" />
        </label>
        <el-button
          class="wide-action drawer-action"
          type="primary"
          :loading="saving || uploading"
          @click="saveLiterature"
        >
          保存文献
        </el-button>
      </el-form>
    </el-drawer>
  </section>
</template>
