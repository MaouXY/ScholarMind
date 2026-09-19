-- ============================================================================
-- 第六阶段（P1 补齐）：演示数据扩充
-- 目标：让检索、阅读、引用、统计大屏、AI 调用日志与热门关键词都有可观数据
-- 特性：全部为幂等写法（固定 ID + ON DUPLICATE KEY UPDATE / NOT EXISTS），可重复执行
-- 说明：新增用户的密码哈希与 02 号脚本一致，演示密码统一为 123456
-- ============================================================================

-- 1) 用户扩充：新增 6 名学生与 2 名教师
INSERT INTO user_account (id, username, password_hash, nickname, email, role, status, deleted) VALUES
  (1000000000000000010, 'student02', '$2b$10$kUsC1ZV2DZlUKr0w1GlCeeh05BLAInwjuvUqtNESb2x7q/Z7bSBdi', '陈晓', 'student02@scholarmind.local', 'STUDENT', 1, 0),
  (1000000000000000011, 'student03', '$2b$10$kUsC1ZV2DZlUKr0w1GlCeeh05BLAInwjuvUqtNESb2x7q/Z7bSBdi', '刘洋', 'student03@scholarmind.local', 'STUDENT', 1, 0),
  (1000000000000000012, 'student04', '$2b$10$kUsC1ZV2DZlUKr0w1GlCeeh05BLAInwjuvUqtNESb2x7q/Z7bSBdi', '赵敏', 'student04@scholarmind.local', 'STUDENT', 1, 0),
  (1000000000000000013, 'student05', '$2b$10$kUsC1ZV2DZlUKr0w1GlCeeh05BLAInwjuvUqtNESb2x7q/Z7bSBdi', '孙倩', 'student05@scholarmind.local', 'STUDENT', 1, 0),
  (1000000000000000014, 'student06', '$2b$10$kUsC1ZV2DZlUKr0w1GlCeeh05BLAInwjuvUqtNESb2x7q/Z7bSBdi', '周涛', 'student06@scholarmind.local', 'STUDENT', 1, 0),
  (1000000000000000015, 'student07', '$2b$10$kUsC1ZV2DZlUKr0w1GlCeeh05BLAInwjuvUqtNESb2x7q/Z7bSBdi', '吴静', 'student07@scholarmind.local', 'STUDENT', 0, 0),
  (1000000000000000016, 'teacher02', '$2b$10$kUsC1ZV2DZlUKr0w1GlCeeh05BLAInwjuvUqtNESb2x7q/Z7bSBdi', '郑教授', 'teacher02@scholarmind.local', 'TEACHER', 1, 0),
  (1000000000000000017, 'teacher03', '$2b$10$kUsC1ZV2DZlUKr0w1GlCeeh05BLAInwjuvUqtNESb2x7q/Z7bSBdi', '王老师', 'teacher03@scholarmind.local', 'TEACHER', 1, 0)
ON DUPLICATE KEY UPDATE
  nickname = VALUES(nickname),
  role = VALUES(role),
  status = VALUES(status),
  deleted = VALUES(deleted);

-- 2) 分类扩充
INSERT INTO literature_category (id, name, parent_id, sort, status, deleted) VALUES
  (1100000000000000010, 'Data Mining', NULL, 40, 1, 0),
  (1100000000000000011, 'Knowledge Graph', NULL, 50, 1, 0),
  (1100000000000000012, 'Academic Writing', NULL, 60, 1, 0),
  (1100000000000000013, 'Semantic Retrieval', 1100000000000000002, 20, 1, 0)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  parent_id = VALUES(parent_id),
  sort = VALUES(sort),
  status = VALUES(status),
  deleted = VALUES(deleted);

-- 3) 文献扩充：8 篇已发布（含中文与西文各若干）+ 2 篇待审核 + 2 篇草稿/私有
INSERT INTO literature (
  id, title, author, source, publish_year, language, category_id, uploader_id,
  visibility, status, abstract_text, keywords, read_count, download_count,
  ai_summary, summary_status, parse_status, reviewed_by, reviewed_at, deleted
) VALUES
  (1200000000000000010, '基于关键词与用户行为的高校数字图书馆混合推荐研究', '李明, 张伟', '图书情报工作', 2024, 'zh-CN', 1100000000000000004, 1000000000000000016,
   'PUBLIC', 'PUBLISHED',
   '针对高校数字图书馆推荐精准度不足的问题，本文提出融合馆藏关键词特征与用户行为特征的混合推荐方法，并在真实借阅数据上验证了效果。',
   '数字图书馆,混合推荐,用户行为,个性化服务', 86, 21,
   '{"background":"高校数字图书馆馆藏规模持续扩张，传统被动式服务难以匹配差异化需求。","problem":"现有推荐服务精准度偏低，未同时兼顾内容属性与用户行为偏好。","method":"构建关键词特征与用户行为特征的混合召回，并采用加权融合排序。","conclusion":"在真实借阅数据上，混合方法在准确率与覆盖率上均优于单一策略。","innovation":"将馆藏关键词语义与行为序列结合，降低冷启动影响。","keywords":["数字图书馆","混合推荐","用户行为","个性化服务"],"suitableReaders":"关注推荐系统与图书馆服务优化的学生与研究者。"}',
   'SUCCESS', 'PENDING', 1000000000000000001, DATE_SUB(NOW(), INTERVAL 20 DAY), 0),
  (1200000000000000011, '面向学术文献的自然语言检索意图解析方法', '王倩, 刘洋', '现代情报', 2023, 'zh-CN', 1100000000000000013, 1000000000000000016,
   'PUBLIC', 'PUBLISHED',
   '本文分析学生自然语言检索表达，提出将口语化需求解析为结构化检索条件的方法，提升文献发现的召回率与可用性。',
   '自然语言检索,意图解析,学术文献,检索条件', 64, 15,
   '{"background":"学生检索学术文献时表达方式口语化，传统关键词检索命中率不稳定。","problem":"如何把自然语言需求稳定映射为可执行的检索条件。","method":"基于大语言模型抽取关键词、语种、年份区间与排序偏好，并保留规则兜底。","conclusion":"意图解析后语义检索的召回率明显提升，解析失败时规则兜底可保证可用性。","innovation":"结构化意图与检索执行解耦，便于替换模型与策略。","keywords":["自然语言检索","意图解析","学术文献"],"suitableReaders":"研究检索系统与信息行为的学生。"}',
   'SUCCESS', 'PENDING', 1000000000000000001, DATE_SUB(NOW(), INTERVAL 18 DAY), 0),
  (1200000000000000012, '大语言模型辅助文献精读的问答可信度研究', '张伟', '计算机应用', 2025, 'zh-CN', 1100000000000000003, 1000000000000000003,
   'PUBLIC', 'PUBLISHED',
   '针对大模型问答容易脱离原文的问题，本文提出基于片段引用与可追溯答案的问答流程，并评估其在文献精读场景下的可信度。',
   '大语言模型,文献精读,问答可信度,片段引用', 73, 19,
   '{"background":"大模型在文献问答中可能出现脱离原文的回答。","problem":"如何让问答结果可追溯到原文片段并量化可信度。","method":"先检索文献分块作为引用片段，再基于片段生成回答并记录引用。","conclusion":"带引用的问答显著降低了无依据回答的比例。","innovation":"把引用片段与问答记录一并入库，支持事后复核。","keywords":["大语言模型","文献精读","问答可信度"],"suitableReaders":"关注 RAG 与可信 AI 的读者。"}',
   'SUCCESS', 'PENDING', 1000000000000000001, DATE_SUB(NOW(), INTERVAL 16 DAY), 0),
  (1200000000000000013, '高校图书馆数字资源使用行为的数据挖掘分析', '陈晓, 孙倩', '数据分析与知识发现', 2024, 'zh-CN', 1100000000000000010, 1000000000000000016,
   'PUBLIC', 'PUBLISHED',
   '本文以图书馆日志为对象，通过聚类与关联规则挖掘识别典型使用模式，为资源采购与阅读推广提供依据。',
   '数据挖掘,使用行为,数字资源,聚类分析', 58, 12,
   NULL, 'PENDING', 'PENDING', 1000000000000000001, DATE_SUB(NOW(), INTERVAL 15 DAY), 0),
  (1200000000000000014, '知识图谱在文献关联推荐中的应用', '周涛', '情报理论与实践', 2023, 'zh-CN', 1100000000000000011, 1000000000000000003,
   'PUBLIC', 'PUBLISHED',
   '本文将文献、作者、关键词构建为知识图谱，通过路径特征实现关联文献推荐，缓解同类文献发现困难的问题。',
   '知识图谱,关联推荐,文献网络,路径特征', 51, 9,
   NULL, 'PENDING', 'PENDING', 1000000000000000001, DATE_SUB(NOW(), INTERVAL 13 DAY), 0),
  (1200000000000000015, '本科毕业论文文献综述写作支持系统设计', '赵敏', '教育教学论坛', 2024, 'zh-CN', 1100000000000000012, 1000000000000000017,
   'PUBLIC', 'PUBLISHED',
   '面向本科毕业论文写作，本文设计文献综述写作支持系统，覆盖资料收集、笔记沉淀与引用管理三个环节。',
   '毕业论文,文献综述,写作支持,引用管理', 45, 8,
   NULL, 'PENDING', 'PENDING', 1000000000000000001, DATE_SUB(NOW(), INTERVAL 11 DAY), 0),
  (1200000000000000016, 'Hybrid Retrieval with Reciprocal Rank Fusion for Academic Search', 'Li Ming; Wang Xiaohong', 'Journal of Information Science', 2023, 'en-US', 1100000000000000002, 1000000000000000016,
   'PUBLIC', 'PUBLISHED',
   'This paper combines sparse keyword retrieval and dense vector retrieval with reciprocal rank fusion, showing robustness when either retriever performs poorly.',
   'hybrid retrieval,reciprocal rank fusion,dense retrieval,academic search', 97, 26,
   NULL, 'PENDING', 'PENDING', 1000000000000000001, DATE_SUB(NOW(), INTERVAL 9 DAY), 0),
  (1200000000000000017, 'Evaluating Citation Styles in Digital Library Reading Assistants', 'Chen Xiao', 'Digital Scholarship Quarterly', 2025, 'en-US', 1100000000000000012, 1000000000000000016,
   'PUBLIC', 'PUBLISHED',
   'The study evaluates how automatically generated GB/T 7714, APA and MLA citations affect students writing reference lists.',
   'citation style,reference management,digital library,writing support', 39, 7,
   NULL, 'PENDING', 'PENDING', 1000000000000000001, DATE_SUB(NOW(), INTERVAL 7 DAY), 0),
  (1200000000000000018, '面向学科服务的文献智能推荐与阅读追踪', '刘洋', '图书馆论坛', 2025, 'zh-CN', 1100000000000000004, 1000000000000000011,
   'PUBLIC', 'PENDING_REVIEW',
   '本文讨论学科服务场景下的文献推荐与阅读追踪，提出结合借阅记录与在线阅读行为的追踪方案。',
   '学科服务,智能推荐,阅读追踪', 6, 0,
   NULL, 'PENDING', 'PENDING', NULL, NULL, 0),
  (1200000000000000019, '面向研究生的文献检索课程改革实践', '郑教授', '大学图书馆学报', 2025, 'zh-CN', 1100000000000000012, 1000000000000000016,
   'PUBLIC', 'PENDING_REVIEW',
   '本文总结文献检索课程改革实践，引入 AI 助手辅助检索与精读，并给出课程评价结果。',
   '文献检索,课程改革,信息素养', 4, 0,
   NULL, 'PENDING', 'PENDING', NULL, NULL, 0),
  (1200000000000000020, '个人文献笔记：混合检索与 RRF 阅读摘录', 'Student One', '个人上传', 2026, 'zh-CN', 1100000000000000002, 1000000000000000002,
   'PRIVATE', 'DRAFT',
   '个人阅读摘录，记录 RRF 融合排序的适用场景与调参经验。',
   'RRF,混合检索,阅读摘录', 3, 0,
   NULL, 'PENDING', 'PENDING', NULL, NULL, 0),
  (1200000000000000021, '开题报告文献调研草稿', '陈晓', '个人上传', 2026, 'zh-CN', 1100000000000000011, 1000000000000000010,
   'PRIVATE', 'DRAFT',
   '开题阶段的文献调研草稿，尚未整理为正式综述。',
   '开题报告,文献调研', 1, 0,
   NULL, 'PENDING', 'PENDING', NULL, NULL, 0)
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  author = VALUES(author),
  source = VALUES(source),
  publish_year = VALUES(publish_year),
  language = VALUES(language),
  category_id = VALUES(category_id),
  uploader_id = VALUES(uploader_id),
  visibility = VALUES(visibility),
  status = VALUES(status),
  abstract_text = VALUES(abstract_text),
  keywords = VALUES(keywords),
  read_count = VALUES(read_count),
  download_count = VALUES(download_count),
  deleted = VALUES(deleted);

-- 4) 正文分块：为所有已发布且尚无分块的文献自动生成 3 个分块
INSERT INTO literature_chunk (
  id, literature_id, chunk_index, chunk_text, page_no, token_count,
  embedding_provider, vector_store_status, enabled, deleted
)
SELECT 8500000000000000000 + ROW_NUMBER() OVER (ORDER BY src.literature_id, src.chunk_index) - 1,
       src.literature_id, src.chunk_index, src.chunk_text, src.chunk_index + 1,
       GREATEST(1, CHAR_LENGTH(src.chunk_text) DIV 2),
       'ark:doubao-embedding-text-240715', 'DB_ONLY', 1, 0
FROM (
  SELECT l.id AS literature_id,
         n.i AS chunk_index,
         CASE n.i
           WHEN 0 THEN CONCAT('研究背景与问题', CHAR(10), CHAR(10), COALESCE(l.abstract_text, ''),
                              CHAR(10), CHAR(10), '关键词：', COALESCE(l.keywords, ''))
           WHEN 1 THEN CONCAT('研究方法与数据', CHAR(10), CHAR(10),
                              '本文围绕“', l.title, '”展开，采用文献调研与实验验证相结合的方法，',
                              '针对 ', COALESCE(l.keywords, '研究主题'), ' 设计对比实验，并统计检索准确率与阅读完成度。')
           ELSE CONCAT('结论与后续工作', CHAR(10), CHAR(10),
                       '研究表明，来自 ', COALESCE(l.source, '相关来源'), ' 的思路在本场景下具备可行性；',
                       '后续可结合更大规模数据与真实用户行为进一步验证。')
         END AS chunk_text
  FROM literature l
  JOIN (SELECT 0 AS i UNION ALL SELECT 1 UNION ALL SELECT 2) n
  WHERE l.status = 'PUBLISHED'
    AND l.deleted = 0
    AND NOT EXISTS (SELECT 1 FROM literature_chunk c WHERE c.literature_id = l.id AND c.deleted = 0)
) src;

-- 5) 收藏：学生 01/02/03/04 各收藏若干篇公开文献
INSERT INTO literature_favorite (id, user_id, literature_id, deleted) VALUES
  (8100000000000000001, 1000000000000000002, 1200000000000000010, 0),
  (8100000000000000002, 1000000000000000002, 1200000000000000012, 0),
  (8100000000000000003, 1000000000000000002, 1200000000000000016, 0),
  (8100000000000000004, 1000000000000000010, 1200000000000000011, 0),
  (8100000000000000005, 1000000000000000010, 1200000000000000013, 0),
  (8100000000000000006, 1000000000000000011, 1200000000000000010, 0),
  (8100000000000000007, 1000000000000000011, 1200000000000000015, 0),
  (8100000000000000008, 1000000000000000012, 1200000000000000016, 0),
  (8100000000000000009, 1000000000000000012, 1200000000000000017, 0),
  (8100000000000000010, 1000000000000000013, 1200000000000000014, 0),
  (8100000000000000011, 1000000000000000016, 1200000000000000010, 0)
ON DUPLICATE KEY UPDATE deleted = VALUES(deleted);

-- 6) 阅读历史：进度分布覆盖“刚开始 / 进行中 / 基本完成”
INSERT INTO reading_history (id, user_id, literature_id, last_page, progress, read_duration, updated_at, deleted) VALUES
  (8200000000000000001, 1000000000000000002, 1200000000000000010, 2, 100, 1860, DATE_SUB(NOW(), INTERVAL 6 DAY), 0),
  (8200000000000000002, 1000000000000000002, 1200000000000000012, 1, 45, 920, DATE_SUB(NOW(), INTERVAL 3 DAY), 0),
  (8200000000000000003, 1000000000000000010, 1200000000000000011, 3, 100, 2400, DATE_SUB(NOW(), INTERVAL 5 DAY), 0),
  (8200000000000000004, 1000000000000000010, 1200000000000000013, 1, 33, 600, DATE_SUB(NOW(), INTERVAL 2 DAY), 0),
  (8200000000000000005, 1000000000000000011, 1200000000000000010, 1, 30, 480, DATE_SUB(NOW(), INTERVAL 4 DAY), 0),
  (8200000000000000006, 1000000000000000012, 1200000000000000016, 3, 100, 3200, DATE_SUB(NOW(), INTERVAL 1 DAY), 0),
  (8200000000000000007, 1000000000000000013, 1200000000000000014, 2, 66, 1500, DATE_SUB(NOW(), INTERVAL 2 DAY), 0),
  (8200000000000000008, 1000000000000000016, 1200000000000000010, 3, 100, 2100, DATE_SUB(NOW(), INTERVAL 8 DAY), 0)
ON DUPLICATE KEY UPDATE
  last_page = VALUES(last_page),
  progress = VALUES(progress),
  read_duration = VALUES(read_duration),
  updated_at = VALUES(updated_at),
  deleted = VALUES(deleted);

-- 7) 阅读笔记
INSERT INTO reading_note (id, user_id, literature_id, title, content, ai_generated, created_at, updated_at, deleted) VALUES
  (9100000000000000001, 1000000000000000002, 1200000000000000010, '混合推荐的三个模块', '关键词召回、行为权重、混合排序三个模块可以复用到我自己的系统里，其中行为权重部分需要考虑冷启动。', 0, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), 0),
  (9100000000000000002, 1000000000000000002, 1200000000000000012, '可信度评估口径', '论文用“无依据回答比例”衡量可信度，比单纯看准确率更贴合问答场景，可作为我论文的评测指标。', 0, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), 0),
  (9100000000000000003, 1000000000000000010, 1200000000000000011, '意图解析的可借鉴点', '把口语化查询解析为关键词、年份、排序条件，再执行检索，解析失败时用分词兜底，这个设计可以写进我的相关工作。', 0, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), 0),
  (9100000000000000004, 1000000000000000010, 1200000000000000013, '聚类方法摘要', '使用 K-means 对借阅行为聚类，再统计各簇的资源偏好，为采购提供依据。', 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 0),
  (9100000000000000005, 1000000000000000011, 1200000000000000010, '引用格式摘录', 'GB/T 7714 的期刊条目需要作者、题名、刊名与年份，缺少卷期页码时可先省略。', 0, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), 0),
  (9100000000000000006, 1000000000000000012, 1200000000000000016, 'RRF 公式与调参', 'RRF 的融合公式是 sum(1/(k+rank))，k 一般取 60；两路召回质量差距大时 RRF 比加权求和更稳。', 0, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 0),
  (9100000000000000007, 1000000000000000013, 1200000000000000014, '知识图谱路径特征', '论文用文献—作者—关键词图上的路径特征做推荐，可与我系统的关键词热度统计结合。', 0, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 0),
  (9100000000000000008, 1000000000000000016, 1200000000000000017, '引用格式教学要点', 'APA 与 MLA 对姓名的处理不同，教学时需要强调姓名顺序判定规则。', 0, DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), 0)
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  content = VALUES(content),
  ai_generated = VALUES(ai_generated),
  deleted = VALUES(deleted);

-- 8) RAG 知识库文档 + 分块
INSERT INTO rag_knowledge_document (id, title, category_id, content, enabled, created_by, deleted) VALUES
  (1300000000000000001, 'AI 文献阅读助手平台说明', 1100000000000000012,
   '平台支持文献上传、AI 摘要、在线阅读、阅读笔记、引用管理与 RAG 知识库维护。用户可在书房查看收藏、阅读历史与笔记。', 1, 1000000000000000001, 0),
  (1300000000000000002, '引用格式速查：GB/T 7714 与 APA', 1100000000000000012,
   'GB/T 7714 期刊条目格式为：作者. 题名[J]. 刊名, 年. APA 第 7 版为：作者. (年). 题名. 来源。缺少年份时使用 n.d.。', 1, 1000000000000000001, 0),
  (1300000000000000003, '混合检索与 RRF 融合说明', 1100000000000000002,
   '混合检索同时使用关键词稀疏召回与向量稠密召回；RRF 按 1/(k+rank) 累加各路排名，k 常取 60，可避免单一召回的偏置。', 1, 1000000000000000001, 0),
  (1300000000000000004, '文献精读的提问技巧', 1100000000000000012,
   '精读提问建议围绕研究问题、方法、数据、结论与局限五类证据组织，并要求系统给出引用片段以便回溯原文。', 1, 1000000000000000001, 0)
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  content = VALUES(content),
  enabled = VALUES(enabled),
  deleted = VALUES(deleted);

INSERT INTO rag_knowledge_chunk (id, document_id, chunk_index, content, token_count, embedding_provider, vector_store_status, enabled, deleted)
SELECT 8600000000000000000 + ROW_NUMBER() OVER (ORDER BY d.id) - 1,
       d.id, 0, d.content, GREATEST(1, CHAR_LENGTH(d.content) DIV 2),
       'ark:doubao-embedding-text-240715', 'DB_ONLY', 1, 0
FROM rag_knowledge_document d
WHERE d.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM rag_knowledge_chunk c WHERE c.document_id = d.id AND c.deleted = 0);

-- 9) AI 调用日志：近 14 天约 40 次调用（多数成功，含少量失败与超时，便于演示状态筛选与健康度）
INSERT INTO ai_task_record (
  id, organization_id, task_type, biz_type, biz_id, model_name,
  request_snapshot, response_snapshot, status, error_message, duration_ms, created_at, deleted
)
SELECT 8700000000000000000 + seq.n - 1,
       NULL,
       CASE seq.n % 5
         WHEN 0 THEN 'LITERATURE_SUMMARY'
         WHEN 1 THEN 'LITERATURE_QUESTION'
         WHEN 2 THEN 'LITERATURE_SEARCH_PARSE'
         WHEN 3 THEN 'LITERATURE_COMPARE'
         ELSE 'ADMIN_OPERATION_REPORT'
       END,
       CASE seq.n % 5
         WHEN 0 THEN 'LITERATURE'
         WHEN 1 THEN 'LITERATURE'
         WHEN 2 THEN 'SEARCH'
         WHEN 3 THEN 'LITERATURE_COMPARE'
         ELSE 'ADMIN_STATISTICS'
       END,
       1200000000000000010,
       'doubao-seed-2-0-lite-260428',
       CONCAT('{"promptChars":', 800 + seq.n * 37, ',"scenario":"demo-seed"}'),
       CONCAT('{"responseChars":', 300 + seq.n * 19, ',"scenario":"demo-seed"}'),
       CASE
         WHEN seq.n = 13 THEN 'FAILED'
         WHEN seq.n = 29 THEN 'TIMEOUT'
         ELSE 'SUCCESS'
       END,
       CASE
         WHEN seq.n = 13 THEN 'upstream unavailable: empty response body'
         WHEN seq.n = 29 THEN 'read timeout after 120000ms'
         ELSE NULL
       END,
       1200 + (seq.n * 173) % 9000,
       DATE_SUB(NOW(), INTERVAL (seq.n % 14) DAY) - INTERVAL (seq.n * 37) MINUTE,
       0
FROM (
  SELECT ROW_NUMBER() OVER () AS n
  FROM information_schema.columns
  LIMIT 40
) seq;

-- 10) 检索行为日志：近 14 天约 36 次检索，覆盖语义检索与自然语言检索
INSERT INTO literature_search_log (
  id, user_id, organization_id, query_text, keywords, search_type,
  category_id, result_count, reference_count, intent_parsed, created_at, deleted
)
SELECT 8800000000000000000 + seq.n - 1,
       CASE seq.n % 4
         WHEN 0 THEN 1000000000000000002
         WHEN 1 THEN 1000000000000000010
         WHEN 2 THEN 1000000000000000011
         ELSE 1000000000000000012
       END,
       NULL,
       CASE seq.n % 8
         WHEN 0 THEN '混合检索 如何避免纯向量召回的问题'
         WHEN 1 THEN 'RRF 融合排序 参数 k 怎么取'
         WHEN 2 THEN '数字图书馆 个性化推荐 方法'
         WHEN 3 THEN '大模型问答 如何保证不脱离原文'
         WHEN 4 THEN '自然语言检索 意图解析'
         WHEN 5 THEN '引用格式 GB/T 7714 期刊条目'
         WHEN 6 THEN 'hybrid retrieval reciprocal rank fusion'
         ELSE '知识图谱 文献推荐'
       END,
       CASE seq.n % 8
         WHEN 0 THEN '混合检索,向量召回'
         WHEN 1 THEN 'RRF,融合排序,k'
         WHEN 2 THEN '数字图书馆,个性化推荐'
         WHEN 3 THEN '大模型,问答,原文'
         WHEN 4 THEN '自然语言检索,意图解析'
         WHEN 5 THEN '引用格式,GB/T 7714'
         WHEN 6 THEN 'hybrid retrieval,reciprocal rank fusion'
         ELSE '知识图谱,文献推荐'
       END,
       CASE WHEN seq.n % 3 = 0 THEN 'SEMANTIC' ELSE 'NATURAL_LANGUAGE' END,
       NULL,
       1 + seq.n % 5,
       1 + seq.n % 3,
       CASE WHEN seq.n % 3 = 0 THEN 0 ELSE 1 END,
       DATE_SUB(NOW(), INTERVAL (seq.n % 14) DAY) - INTERVAL (seq.n * 53) MINUTE,
       0
FROM (
  SELECT ROW_NUMBER() OVER () AS n
  FROM information_schema.columns
  LIMIT 36
) seq;
