# ScholarMind — AI 图书馆文献检索与阅读助手

面向高校学生的文献检索、精读与知识沉淀平台。项目为前后端分离架构，覆盖「文献采集 → 解析索引 → 检索 → 阅读 → 笔记 → 引用 → 统计」的完整闭环，并在检索与问答环节引入大语言模型与 RAG 能力。

- 后端：Spring Boot 3.5 + MyBatis-Plus + Spring Security(JWT) + LangChain4j（火山方舟 Ark）+ Milvus
- 前端：Vue 3 + TypeScript + Vite + Element Plus + Pinia + ECharts
- 存储：MySQL 8（业务数据）、RustFS（对象存储，S3 兼容）
- AI：文献摘要、自然语言检索意图解析、基于原文片段的问答、多文献对比、运营分析报告（均带本地兜底）
- 检索：**混合检索**——BM25 稀疏召回（关键词精确匹配）+ Milvus 向量稠密召回（语义相似）+ **RRF 融合排序**，
  规避纯向量召回对专有名词、术语缩写不敏感的缺陷；向量不可用时自动退化为纯稀疏检索

## 一、目录结构

```text
ScholarMind/
├─ ScholarMindApi/                 后端（Spring Boot）
│  ├─ src/main/java/com/maou/apptemplateapi/
│  │  ├─ common/                   公共配置（安全、异常、Jackson、存储、AI、RAG）
│  │  └─ module/                   业务模块：user / literature / citation / file / rag / ai / admin
│  ├─ src/main/resources/sql/      数据库脚本（00~13，按序执行）
│  └─ src/test/java/               单元测试（AI 调用重试、引用格式化、上下文装配）
├─ ScholarMindWeb/                 前端（Vue 3 + Vite，开发端口 5173）
│  └─ src/{api,types,views,layouts,styles,router,stores,utils}
├─ doc/                            项目文档（01~10 见第九节；html/ 为可交互图形）
└─ scripts/seed-demo-files.ps1     演示数据：为文献上传并绑定原文文件（需 RustFS 在线）
```

## 二、环境要求

| 组件 | 版本/说明 | 默认地址 |
| --- | --- | --- |
| JDK | 21 | — |
| Maven | 3.9+（或用自带 `mvnw`） | — |
| Node.js | 18+（含 npm） | — |
| MySQL | 8.x，库名 `scholar_mind` | `127.0.0.1:3306` |
| RustFS | S3 兼容对象存储，**bucket 需为 `scholar-mind`** | `http://127.0.0.1:9990` |
| Milvus | 可选，混合检索的向量召回用；未启动时检索自动退化为纯稀疏（BM25） | `127.0.0.1:19530` |
| 火山方舟 Ark | 需要可用的 API Key 与模型名（`app.ai.ark.*`）；embedding 模型实际维度为 **2560**（见 `app.agent.rag.milvus.dimension`） | `https://ark.cn-beijing.volces.com/api/v3` |

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
09_retire_teacher_accounts.sql     教师端下线与演示用户迁移
10_seed_retrieval_demo.sql         三模式检索演示数据
11_seed_open_access_papers.sql     开放论文题录与摘要译文（无伪造全文）
12_admin_literature_upload.sql     管理员上传改造：文献类别 / 出版日期 / 在线公开日期 + 学科中文化
13_literature_metrics_and_folders.sql  被引次数 / 参考文献 / 中图法八大门类 / 收藏夹
```

命令行执行示例（**注意指定 utf8mb4**，否则中文演示数据会报 `Incorrect string value`）：

```powershell
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
Get-ChildItem .\ScholarMindApi\src\main\resources\sql\*.sql | Sort-Object Name | ForEach-Object {
  Write-Host "==> $($_.Name)"
  Get-Content $_.FullName -Raw | & $mysql --default-character-set=utf8mb4 -uroot -p<密码> scholar_mind
}
```

`05`、`07`、`08`、`09`、`12`、`13` 均为幂等脚本（`CREATE TABLE IF NOT EXISTS` / `ON DUPLICATE KEY UPDATE` / `information_schema` 判存在后 `ALTER`），可重复执行。

已有数据库升级时按需执行增量脚本：升级到「管理员上传」模型执行 `12_admin_literature_upload.sql`，升级到「被引/参考文献/八大门类/收藏夹」执行 `13_literature_metrics_and_folders.sql`（均幂等）；全新初始化按 `00~13` 文件顺序执行。随后在管理端重建文献向量。`11` 号脚本只导入三篇 arXiv 论文的题录、摘要和明确标注的中文摘要译文，不写入假原文附件；正式全文需另行核验许可并上传。

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
| `student01`~`student10` | STUDENT | 检索、阅读、笔记、引用、我的书房 |

> 教师端已按 2026-10-31《完善需求（改）》下线：`student08`~`student10` 由原 `teacher01`~`teacher03` 转换而来（历史文献归属不变），后端保留 `TEACHER` 枚举值仅用于解析旧 JWT，教师账号登录会被拒绝（业务码 `9004`）。

### 上传演示文件（可选，需 RustFS 在线）

演示数据中的文献默认只有元数据与正文分块；执行以下脚本可为所有文献上传并绑定原文文件，使「下载原文」可用：

```powershell
cd ScholarMind
pwsh -NoProfile -File .\scripts\seed-demo-files.ps1
```

脚本以 `admin` 登录，为未绑定文件的文献生成演示文本、调用上传接口并通过文献更新接口绑定（可重复执行，已绑定者自动跳过）。

检索演示文献（`sql/10_seed_retrieval_demo.sql` 的 12 篇）的原文由脚本按库内分块正文生成，保证「下载原文」与「在线阅读」完全一致：

```powershell
pwsh -NoProfile -File .\scripts\seed-retrieval-demo-files.ps1
```

### 辅助脚本一览（`scripts/`）

| 脚本 | 用途 | 前置条件 |
| --- | --- | --- |
| `preflight-check.ps1` | **验收前一键体检**：依赖端口、后端健康、前端可访问、登录与代理、演示数据规模、下载链路抽检、混合检索向量路、AI 任务健康度；输出 `[OK]/[WARN]/[FAIL]` 与结论，退出码 0 表示关键项全通过 | 后端与前端已启动 |
| `seed-demo-files.ps1` | 为文献上传并绑定原文文件（幂等，可重复执行） | RustFS 在线 |
| `seed-retrieval-demo-files.ps1` | 为 12 篇检索演示文献绑定原文（正文取自库内分块，保证在线阅读与下载一致） | 已执行 `sql/10`、RustFS 在线 |
| `make-sample-pdf.ps1` | 生成用于全文解析验证的最小示例 PDF（手写 PDF 结构，无需第三方依赖） | 无 |
| `verify-pdf-parsing.ps1` | 端到端验证「PDF → 正文分块」：管理员登录上传 PDF → 文献直接发布并自动建索引与摘要 → 校验分块正文含 PDF 关键词与摘要已生成 → 调用管理端删除清理；`-KeepData` 可保留数据，`-LogPath` 可打印后端解析日志 | RustFS 在线、后端已启动 |
| `export-retrieval-compare.ps1` | **论文取数**：批量跑 15 条探针（术语型 / 改写型 / 口语型），调 `/search/retrieval-compare` 把稀疏、向量、RRF 三列的名次与分数导出为 CSV（默认落到 `doc/数据/`），并统计两路 Top1 分歧条数 | 后端已启动、Milvus 在线（离线则退化为纯 BM25） |

> 验收步骤与预期结果详见 `doc/...-07-系统验收流程.md`；演示配图清单见 `doc/...-06-系统截图清单.md`。

### 环境迁移与数据导出（`tools/`）

把整个环境（MySQL 业务数据 + Milvus 向量 + RustFS 对象）导出成离线迁移包，或从包恢复：

```powershell
# 导出：一键生成迁移包（可加 -Zip 打包、-IncludeMcExe 附带 mc.exe）
pwsh -NoProfile -File tools\export-migration-bundle.ps1 -IncludeMcExe -Zip

# 目标机恢复：MySQL 走包内 SQL，对象与向量走包内脚本
pwsh -NoProfile -File .\rustfs-import.ps1 -SourceDir . -Bucket scholar-mind
pwsh -NoProfile -File .\milvus\milvus-import.ps1 -Source .\milvus\milvus-scholar_mind_knowledge_literature.jsonl -CreateCollection
```

- 工具清单与用法见 [`tools/README.md`](tools/README.md)；包内会自动生成目标机操作说明 `README-迁移步骤.md`
- **零依赖**：除可选复用已有 `mc.exe` 外，全部用纯 PowerShell 实现（SigV4 签名在 `tools/s3-common.ps1`），国内网络下也能跑通
- 支持「桶对桶在线迁移」（`tools/rustfs-migrate.ps1`）与「跨桶改名恢复」（`rustfs-import.ps1 -Bucket <新桶名>`）

## 五、构建与测试

```powershell
# 后端：编译 + 全部测试（单元测试 + 集成测试）
cd ScholarMindApi
mvn -B test            # 单元：引用格式化 / BM25 / RRF / AI 重试 / 文本抽取
                       # 集成：主链路与权限异常、接口鉴权（真实 MySQL，用例自动回滚）
mvn -B test -Dtest=LiteratureMainFlowIntegrationTest   # 只跑主链路集成测试

# 前端：真实类型门禁 + 生产构建
cd ScholarMindWeb
npm run type-check      # vue-tsc --noEmit -p tsconfig.app.json
npm run build           # 类型门禁 + vite build
```

> 集成测试需要本机 MySQL 可连（使用 `application.yaml` 中的连接配置）；大模型与 Milvus 在测试中被 Mock，
> 因此测试**不产生外部调用费用**，也不需要 RustFS/Milvus 在线。

## 六、功能地图

| 路由 | 页面 | 主要能力 |
| --- | --- | --- |
| `/student/home` | 首页 | 中央搜索框（可选主题 / 关键词 / 全文 / 作者 / 摘要 / 参考文献 / 文献来源七个检索维度）、按学科分类浏览、RAG 智能检索入口，检索后跳转结果页 |
| `/student/search` | 检索结果 | 左侧学科（中图法八大门类）与年度（2017~2026）筛选、顶部文献类别（学术期刊 / 学位论文（硕士）/ 学位论文（博士）/ 报纸）筛选；每条展示题名、作者、来源、发表时间、被引、下载与阅读数（学生端只读） |
| `/student/literature/:id` | 文献详情 | 题名、作者、来源、摘要与 AI 摘要、关键词、学科、在线公开时间；HTML 阅读、PDF 下载（未绑定原文时按钮置灰）、引用格式（GB/T 7714 / APA / MLA）与导出、收藏（可选择收藏夹） |
| `/student/literature/:id/read` | 在线阅读 | 左侧目录可点击跳转、中间**内嵌原文**（`GET /literatures/{id}/preview` 预签名 URL，PDF 走浏览器原生阅读器、纯文本按 UTF-8 渲染，不累加下载次数）、右侧智能问答与阅读笔记；分块降级为「切分索引」用于定位与问答引用；阅读进度与时长自动保存 |
| `/student/literature/:id/qa` | 智能问答 | 基于原文片段的问答与引用、历史问答 |
| `/student/shelf` | 我的书房 | 收藏（支持自定义收藏夹：新建 / 重命名 / 删除 / 移动归属）/ 阅读历史 / 笔记三类资产的汇总与导出 |
| `/admin/dashboard` | 管理中心 | 统计 KPI、**上传文献（含可选原文）**、文献库管理与删除、RAG 知识库维护 |
| `/admin/users`、`/admin/categories` | 用户/分类管理 | 账号启停、分类增删改 |
| `/admin/ai-logs` | AI 调用日志 | 任务分页与筛选、请求/响应预览、健康度概要 |
| `/admin/operations` | 运营分析 | 热门关键词、平台指标、AI 运营分析报告（含兜底标识） |
| `/analytics/overview` | 数据总览 | 趋势与维度分布大屏 |

> 教师端（`/teacher/review`）已按改版需求移除；用户端即原学生端，路由前缀保持 `/student` 以避免大范围改动。根路径 `/` 重定向到 `/student/home`；旧地址 `/student/library` 重定向到 `/student/search`（原文献库即检索页）。

### 核心接口一览（前缀 `/api/v1`）

- 文献：`GET /literatures`（支持 `keyword` + `searchField`、`categoryId`、`literatureType`、`yearFrom|yearTo`）、`GET /literatures/{id}/view`、`GET /literatures/{id}/chunks`、`GET /literatures/{id}/citations`、`GET /literatures/{id}/download`
- 阅读：`GET|PUT /reading-history`、`GET|POST /favorites`（`POST /favorites/{id}?folderId=`）、`PUT /favorites/{id}/folder`、`GET|POST /favorites/folders`、`PUT|DELETE /favorites/folders/{id}`、`GET|POST|PUT|DELETE /notes`、`GET /notes/export`
- 引用：`GET|POST /citations`、`DELETE /citations/{id}`、`GET /citations/export`
- 检索与 AI：`POST /search/semantic`、`POST /search/hybrid`（混合检索）、`POST /search/retrieval-compare`（稀疏/稠密/RRF 三路对比，用于算法验证与论文数据）、`POST /search/natural-language`、`POST /ai/literature/{id}/summary|question|index`、`POST /ai/literature/compare`
- 管理：`GET /admin/statistics/dashboard|keywords`、`POST /admin/statistics/report`、`GET /admin/ai-tasks`、`GET /admin/ai-tasks/summary`、`GET|PUT /admin/users`、`DELETE /admin/literatures/{id}`（级联清理分块/收藏/历史/笔记/引用/问答、附件对象与向量）、`POST /admin/rag/literature-vectors/rebuild`（把已发布文献分块写入 Milvus 向量库）

## 七、演示路径建议

1. 从首页中央搜索框输入「怎样把几个检索器给出的候选清单合成为一个更可靠的排序」，选择检索维度（默认主题，可切关键词 / 全文 / 作者 / 摘要 / 参考文献 / 文献来源）→ 进入结果页，再用左侧学科与年度、顶部文献类别筛选收窄；
2. 检索「BM25 k1 参数对倒排索引检索效果的影响」→ 术语型问题下稀疏召回占优，可与口语化问题对照演示；也可在首页 RAG 面板切换「自然语言 / 混合」模式对比；
3. 进入文献详情，查看 AI 摘要（新文献的摘要在上传时自动生成；摘要以「研究背景 / 研究问题 / 研究方法 / 主要结论 / 创新点 / 适合读者 + 主题词」分节渲染）、学科与在线公开时间，切换三种引用格式并复制 / 收藏引用 / 导出参考文献；
4. 以 `admin` 进入管理中心「上传文献」，填写题录字段（含被引次数与参考文献）并附上 PDF 原文 → 提交后索引**自动重建**、**AI 摘要自动生成**（无需点按钮，按钮显示「正在建索引并生成摘要…」十余秒），在线阅读页即可看到**内嵌的原文**（浏览器原生渲染），并可用左侧目录跳转；
5. 在线阅读时用左侧目录跳转章节、在右侧提问并核对引用片段、随手记笔记，进度与时长自动保存；
6. 回到「我的书房」新建收藏夹并调整文献归属，查看收藏、阅读历史与笔记汇总；
7. 管理员在「文献库管理」中删除一篇文献（观察分块、收藏、附件与向量一并清理）；
8. 查看 AI 调用日志与热门关键词、一键生成运营分析报告，打开数据总览大屏。

## 八、已知限制

1. **正文来源**：已接入 PDF（PDFBox）、docx（Apache POI）与纯文本的正文抽取，解析失败或类型不支持时回退题录与摘要，
   并把原因写入 `literature.parse_error`；旧版 `.doc` 与扫描件（图片型 PDF）目前按「未解析到正文」处理，走兜底。
2. **向量检索与混合检索**：已实现混合检索（BM25 稀疏 + Milvus 向量稠密 + RRF 融合，见接口一览与文档 04 第八节）。
   分块需先写入向量库：管理端调用 `POST /admin/rag/literature-vectors/rebuild`（或对单篇文献重建索引，
   上传 / 换原文时也会自动重建）；
   未写入向量时检索自动退化为纯稀疏 BM25。**注意** `app.agent.rag.milvus.dimension` 必须与 embedding 模型实际维度一致
   （`doubao-embedding-text-240715` 为 2560），不一致时写入会报 `Incorrect dimension for field 'vector'`；
   服务已在写入时做维度自愈（不匹配则删表重建）。
3. **AI 依赖**：摘要、问答、运营报告在模型不可用时自动落到本地兜底（响应中的 `fallbackUsed` / `summary_status` 可区分），需要真实 AI 效果时请确保 Ark Key 与模型名可用。
   摘要已改为**上传时自动生成**（与索引重建同一条事件链），因此上传接口耗时为秒级到十几秒；
   索引或摘要失败只降级（`parse_status` / `summary_status` 置 `FAILED`），不影响文献本身入库。
4. **文件模块**：已清理模板期遗留的「工单绑定」接口（无调用方的死代码），现仅保留通用上传 `POST /files/upload`；
   业务绑定统一通过相应业务表的外键维护（文献附件为 `literature.file_id`），下载走预签名地址。
5. **检索维度**：「全文」与「参考文献」维度基于 `literature` 表的题录字段（`abstract_text` / `references_text` 等）做 LIKE 匹配，
   原文正文分块在 `literature_chunk` 表、无法跨表 LIKE 命中；被引次数 `citation_count` 为管理员手填的演示数据，未对接外部引文库。
6. **前端开发端口**：默认 5173，若被占用请修改 `ScholarMindWeb/vite.config.ts`。

## 九、设计文档与图表

| 文档 | 内容 |
| --- | --- |
| `doc/...-01-项目调研文档.md` | 研究背景、现状、痛点、可行性 |
| `doc/...-02-项目分析与设.md` | 角色、业务流程、功能与非功能需求、数据库设计 |
| `doc/...-03-项目规划文档.md` | 六阶段开发计划、难点与解决方案、创新点 |
| `doc/...-04-第三至五阶段实现说明.md` | 各阶段实现、接口实测、混合检索与 RRF、全文解析、测试用例与测试发现的缺陷 |
| `doc/...-05-设计图表.md` | Mermaid 图表：分层架构、功能模块、用例图、ER 图、状态图、6 张时序图、活动图、部署图 |
| `doc/...-06-系统截图清单.md` | 毕业论文配图清单：截图前准备、按章节的 44 个截图要点与命名、答辩演示组合建议 |
| `doc/...-07-系统验收流程.md` | 验收流程：环境与数据准备、启动健康检查、A~H 功能验收、X1~X20 异常与边界、Z1~Z8 自动化验收、结论签字表、排查手册与彩排基线 |
| `doc/...-09-客户完善需求与改造清单.md` | 客户两版《完善需求》逐条拆解、P0~P3 排期与实施结论（含第 3 批收尾） |
| `doc/...-10-完善需求改版对照与改造计划.md` | 2026-10-31《完善需求（改）》差异对照：删除教师端、三种检索可区分、用户端改称、第 3 批收尾结果 |
| `doc/数据/` | 论文取数产物：`retrieval-compare-<时间戳>.csv`（15 条探针的稀疏/向量/RRF 三列名次与分数） |

### 可交互图形（`doc/html/`）

浏览器直接打开 `doc/html/index.html` 即可，四张图为独立单文件 HTML，支持缩放、拖拽、视图切换与 PNG/SVG 导出：

| 文件 | 内容 |
| --- | --- |
| `doc/html/01-新架构.html` | 改版后架构：用户端/管理端 SPA、Spring Boot API、检索编排、AI 能力、MySQL/Milvus/RustFS/Ark |
| `doc/html/02-新业务流程.html` | 用户端 6 环节与管理端 6 环节，含管理员统一上传与三种检索模式说明 |
| `doc/html/03-改造计划.html` | 改造交付全景：第 1~4 批全部交付，只剩「管理端指标是否进论文」待客户确认 |
| `doc/html/04-页面功能地图.html` | 用户端 5 页与管理端 6 页的功能与跳转关系，标注代码来源 |

> 图内「来源」徽标可点击跳转到 GitHub 对应代码行；图形由 Archify 生成，全部通过 validate / deliver / check / browser-check 四道校验。
