# ScholarMind — AI 图书馆文献检索与阅读助手

面向高校学生的文献检索、精读与知识沉淀平台。项目为前后端分离架构，覆盖「文献采集 → 解析索引 → 检索 → 阅读 → 笔记 → 引用 → 统计」的完整闭环，并在检索与问答环节引入大语言模型与 RAG 能力。

- 后端：Spring Boot 3.5 + MyBatis-Plus + Spring Security(JWT) + LangChain4j（火山方舟 Ark）+ Milvus
- 前端：Vue 3 + TypeScript + Vite + Element Plus + Pinia + ECharts
- 存储：MySQL 8（业务数据）、RustFS（对象存储，S3 兼容）
- AI：文献摘要、自然语言检索意图解析、基于原文片段的问答、多文献对比、运营分析报告（均带本地兜底）

## 一、目录结构

```text
ScholarMind/
├─ ScholarMindApi/                 后端（Spring Boot）
│  ├─ src/main/java/com/maou/apptemplateapi/
│  │  ├─ common/                   公共配置（安全、异常、Jackson、存储、AI、RAG）
│  │  └─ module/                   业务模块：user / literature / citation / file / rag / ai / admin
│  ├─ src/main/resources/sql/      数据库脚本（00~08，按序执行）
│  └─ src/test/java/               单元测试（AI 调用重试、引用格式化、上下文装配）
├─ ScholarMindWeb/                 前端（Vue 3 + Vite，开发端口 5173）
│  └─ src/{api,types,views,layouts,styles,router,stores,utils}
├─ doc/                            项目文档（01 调研 / 02 分析与设计 / 03 规划 / 04 第三至五阶段实现说明）
└─ scripts/seed-demo-files.ps1     演示数据：为已发布文献上传并绑定原文文件（需 RustFS 在线）
```

## 二、环境要求

| 组件 | 版本/说明 | 默认地址 |
| --- | --- | --- |
| JDK | 21 | — |
| Maven | 3.9+（或用自带 `mvnw`） | — |
| Node.js | 18+（含 npm） | — |
| MySQL | 8.x，库名 `scholar_mind` | `127.0.0.1:3306` |
| RustFS | S3 兼容对象存储，**bucket 需为 `scholar-mind`** | `http://127.0.0.1:9990` |
| Milvus | 可选，向量检索用；未启动时检索走「关键词 + 重排」 | `127.0.0.1:19530` |
| 火山方舟 Ark | 需要可用的 API Key 与模型名（`app.ai.ark.*`） | `https://ark.cn-beijing.volces.com/api/v3` |

> 配置均可在 `ScholarMindApi/src/main/resources/application.yaml` 中通过环境变量覆盖（如 `APP_DB_URL`、`APP_AI_API_KEY`、`APP_AI_MODEL`、`APP_STORAGE_S3_ENDPOINT`）。
> RustFS 未启动时，文献上传/下载会返回明确错误（`FILE_UPLOAD_FAILED` / `LITERATURE_FILE_MISSING`），其余功能不受影响。

## 三、初始化数据库

按顺序执行 `ScholarMindApi/src/main/resources/sql/` 下的脚本：

```text
00_create_database.sql              建库
01_base_platform_schema.sql         基础表（用户、文件、分类、文献）
02_base_platform_demo_data.sql      基础演示数据
03_stage3_reading_schema.sql        收藏、阅读历史、阅读笔记
04_stage3_reading_demo_data.sql     阅读演示数据
05_stage4_llm_rag_schema.sql        AI 任务、RAG 知识库、文献分块、问答记录
06_stage5_search_log_schema.sql     检索行为日志
07_stage6_citation_schema.sql       引用记录（citation_record）
08_seed_demo_scale.sql              演示数据扩充（用户/文献/分块/收藏/笔记/历史/RAG/AI 日志/检索日志）
```

命令行执行示例（**注意指定 utf8mb4**，否则中文演示数据会报 `Incorrect string value`）：

```powershell
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
Get-ChildItem .\ScholarMindApi\src\main\resources\sql\*.sql | Sort-Object Name | ForEach-Object {
  Write-Host "==> $($_.Name)"
  Get-Content $_.FullName -Raw | & $mysql --default-character-set=utf8mb4 -uroot -p<密码> scholar_mind
}
```

`05` 与 `07`、`08` 均为幂等脚本（`CREATE TABLE IF NOT EXISTS` / `ON DUPLICATE KEY UPDATE`），可重复执行。

## 四、启动

```powershell
# 1) 后端（默认端口 8992）
cd ScholarMindApi
mvn spring-boot:run

# 2) 前端（默认端口 5173，/api 反向代理到后端 8992）
cd ScholarMindWeb
npm install
npm run dev
```

访问 `http://127.0.0.1:5173`，接口文档（Knife4j）在 `http://127.0.0.1:8992/doc.html`。

### 演示账号（密码统一 `123456`）

| 账号 | 角色 | 说明 |
| --- | --- | --- |
| `admin` | ADMIN | 管理中心、用户/分类管理、AI 调用日志、运营分析 |
| `student01`~`student07` | STUDENT | 检索、阅读、笔记、引用、我的书房 |
| `teacher01`~`teacher03` | TEACHER | 阅读指导台 |

### 上传演示文件（可选，需 RustFS 在线）

演示数据中的文献默认只有元数据与正文分块；执行以下脚本可为所有已发布文献上传并绑定原文文件，使「下载原文」可用：

```powershell
cd ScholarMind
pwsh -NoProfile -File .\scripts\seed-demo-files.ps1
```

脚本会登录管理员、为未绑定文件的已发布文献生成演示文本、调用上传接口并通过文献更新接口绑定（可重复执行）。

## 五、构建与测试

```powershell
# 后端：编译 + 全部单元测试（上下文装配、AI 调用重试、引用格式化）
cd ScholarMindApi
mvn -B test

# 前端：真实类型门禁 + 生产构建
cd ScholarMindWeb
npm run type-check      # vue-tsc --noEmit -p tsconfig.app.json
npm run build           # 类型门禁 + vite build
```

## 六、功能地图

| 路由 | 页面 | 主要能力 |
| --- | --- | --- |
| `/student/library` | 文献检索与阅读 | 语义检索、自然语言检索、文献上传/编辑、AI 摘要、问答、对比 |
| `/student/literature/:id` | 文献详情 | 题录、摘要与 AI 摘要、索引状态、收藏、**引用格式（GB/T 7714 / APA / MLA）与下载** |
| `/student/literature/:id/read` | 在线阅读 | 真实分块正文、目录导航、阅读进度与时长、笔记管理、下载与笔记导出 |
| `/student/literature/:id/qa` | 智能问答 | 基于原文片段的问答与引用、历史问答、多文献对比 |
| `/student/shelf` | 我的书房 | 收藏 / 阅读历史 / 笔记三类资产的汇总与导出 |
| `/teacher/review` | 阅读指导台 | 文献浏览与阅读指导 |
| `/admin/dashboard` | 管理中心 | 统计 KPI、公开文献审核、RAG 知识库维护、运行健康度 |
| `/admin/users`、`/admin/categories` | 用户/分类管理 | 账号启停、分类增删改 |
| `/admin/ai-logs` | AI 调用日志 | 任务分页与筛选、请求/响应预览、健康度概要 |
| `/admin/operations` | 运营分析 | 热门关键词、平台指标、AI 运营分析报告（含兜底标识） |
| `/analytics/overview` | 数据总览 | 趋势与维度分布大屏 |

### 核心接口一览（前缀 `/api/v1`）

- 文献：`GET /literatures`、`GET /literatures/{id}/view`、`GET /literatures/{id}/chunks`、`GET /literatures/{id}/citations`、`GET /literatures/{id}/download`
- 阅读：`GET|PUT /reading-history`、`POST|DELETE /favorites`、`GET|POST|PUT|DELETE /notes`、`GET /notes/export`
- 引用：`GET|POST /citations`、`DELETE /citations/{id}`、`GET /citations/export`
- 检索与 AI：`POST /search/semantic`、`POST /search/natural-language`、`POST /ai/literature/{id}/summary|question|index`、`POST /ai/literature/compare`
- 管理：`GET /admin/statistics/dashboard|keywords`、`POST /admin/statistics/report`、`GET /admin/ai-tasks`、`GET /admin/ai-tasks/summary`、`GET|PUT /admin/users`、`POST /admin/literatures/{id}/approve|reject`

## 七、演示路径建议

1. 学生检索「混合检索 如何避免纯向量召回的问题」→ 查看语义检索与意图解析结果；
2. 进入文献详情，生成 AI 摘要、切换三种引用格式并复制 / 收藏引用 / 导出参考文献；
3. 在线阅读真实分块正文，保存进度、记录笔记，导出本篇笔记；
4. 智能问答提问并核对引用片段，做一次多文献对比；
5. 打开「我的书房」查看收藏、阅读历史与笔记汇总；
6. 管理员处理待审核文献、查看 AI 调用日志与热门关键词、一键生成运营分析报告；
7. 打开数据总览大屏查看趋势与分布。

## 八、已知限制

1. **正文来源**：分块索引目前基于题录与摘要生成，尚未接入 PDF/Word 正文抽取；接入解析库后写入 `literature_chunk` 即可复用阅读页。
2. **向量检索**：分块以 `DB_ONLY` 落库，Milvus 写入与向量召回待打通；当前检索为「关键词打分 + 重排」，后续计划实现混合检索 + RRF 融合排序。
3. **AI 依赖**：摘要、问答、运营报告在模型不可用时自动落到本地兜底（响应中的 `fallbackUsed` / `summary_status` 可区分），需要真实 AI 效果时请确保 Ark Key 与模型名可用。
4. **命名遗留**：文件模块仍保留模板期的 `tickets` 路径命名，功能上用于文献附件，后续可统一改名。
5. **前端开发端口**：默认 5173，若被占用请修改 `ScholarMindWeb/vite.config.ts`。
