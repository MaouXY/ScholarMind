-- ============================================================================
-- 12 管理员上传文献：题录字段扩展 + 审核/可见性口径归并
-- ----------------------------------------------------------------------------
-- 背景（客户需求 2026-11-15）：
--   文献改由管理员统一上传，学生端只读；界面不再出现「状态」「可见性」「文献审核」。
--   上传字段：标题、作者、来源、文献类别、学科、发表时间、在线公开时间、关键词、摘要。
--
-- 本脚本做四件事，可重复执行（幂等）：
--   1. literature 表新增三列：
--        literature_type      文献类别（期刊论文/会议论文/学位论文/图书/预印本/其他）
--        publish_date         发表时间（精确到日，替代原来的 publish_year 年份口径）
--        online_publish_date  在线公开时间（精确到日，可为空）
--   2. 历史数据回填：
--        publish_date 优先由 publish_year 补成「当年 1 月 1 日」，缺失时退回 created_at 的日期；
--        online_publish_date 默认与 publish_date 相同；
--        literature_type 统一回填为「期刊论文」。
--   3. 状态与可见性归并：全部文献固定 visibility='PUBLIC'、status='PUBLISHED'。
--      列本身保留（检索、统计、AI 索引链路仍按 PUBLISHED 读取），但接口与界面不再暴露。
--   4. 学科（复用 literature_category）名称中文化：Digital Library → 数字图书馆 等 8 条。
--
-- 说明：publish_year 列保留不删，仅作历史存档；应用层改读写 publish_date。
--       旧的种子脚本（02/08/10/11）仍写 publish_year，本脚本在其后执行即可完成回填。
-- ============================================================================

USE scholar_mind;

-- ---------------------------------------------------------------------------
-- 1. 新增列（先查 information_schema，已存在则跳过，保证可重复执行）
-- ---------------------------------------------------------------------------
SET @ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE literature ADD COLUMN literature_type VARCHAR(32) NULL COMMENT ''文献类别'' AFTER source',
        'SELECT ''literature_type already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = 'scholar_mind' AND table_name = 'literature' AND column_name = 'literature_type'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE literature ADD COLUMN publish_date DATE NULL COMMENT ''发表时间'' AFTER publish_year',
        'SELECT ''publish_date already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = 'scholar_mind' AND table_name = 'literature' AND column_name = 'publish_date'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE literature ADD COLUMN online_publish_date DATE NULL COMMENT ''在线公开时间'' AFTER publish_date',
        'SELECT ''online_publish_date already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = 'scholar_mind' AND table_name = 'literature' AND column_name = 'online_publish_date'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 2. 历史数据回填
-- ---------------------------------------------------------------------------
-- 2.1 发表时间：优先年份补 1 月 1 日，其次退回创建日期
UPDATE literature
SET publish_date = MAKEDATE(publish_year, 1)
WHERE publish_date IS NULL
  AND publish_year IS NOT NULL
  AND publish_year BETWEEN 1900 AND 2100;

UPDATE literature
SET publish_date = DATE(created_at)
WHERE publish_date IS NULL AND created_at IS NOT NULL;

-- 2.2 在线公开时间：默认与发表时间一致
UPDATE literature
SET online_publish_date = publish_date
WHERE online_publish_date IS NULL AND publish_date IS NOT NULL;

-- 2.3 文献类别：历史数据统一归为期刊论文
UPDATE literature
SET literature_type = '期刊论文'
WHERE literature_type IS NULL OR literature_type = '';

-- ---------------------------------------------------------------------------
-- 3. 状态与可见性归并（列保留，值固定）
-- ---------------------------------------------------------------------------
UPDATE literature
SET visibility = 'PUBLIC'
WHERE visibility IS NULL OR visibility <> 'PUBLIC';

UPDATE literature
SET status = 'PUBLISHED'
WHERE status IS NULL OR status <> 'PUBLISHED';

-- 审核痕迹不再使用：清空退回原因，reviewed_by 回填为上传者本人（便于追溯上传人）
UPDATE literature
SET reject_reason = NULL
WHERE reject_reason IS NOT NULL;

UPDATE literature
SET reviewed_by = uploader_id,
    reviewed_at = COALESCE(reviewed_at, created_at)
WHERE reviewed_by IS NULL;

-- ---------------------------------------------------------------------------
-- 4. 学科名称中文化（literature_category 复用为「学科」字典）
-- ---------------------------------------------------------------------------
UPDATE literature_category SET name = '数字图书馆' WHERE id = 1100000000000000001 AND name = 'Digital Library';
UPDATE literature_category SET name = '信息检索'   WHERE id = 1100000000000000002 AND name = 'Information Retrieval';
UPDATE literature_category SET name = '人工智能'   WHERE id = 1100000000000000003 AND name = 'Artificial Intelligence';
UPDATE literature_category SET name = '推荐系统'   WHERE id = 1100000000000000004 AND name = 'Recommendation System';
UPDATE literature_category SET name = '数据挖掘'   WHERE id = 1100000000000000010 AND name = 'Data Mining';
UPDATE literature_category SET name = '知识图谱'   WHERE id = 1100000000000000011 AND name = 'Knowledge Graph';
UPDATE literature_category SET name = '学术写作'   WHERE id = 1100000000000000012 AND name = 'Academic Writing';
UPDATE literature_category SET name = '语义检索'   WHERE id = 1100000000000000013 AND name = 'Semantic Retrieval';

-- ---------------------------------------------------------------------------
-- 5. 执行结果校验
-- ---------------------------------------------------------------------------
SELECT
    COUNT(*)                                                        AS literature_total,
    SUM(status = 'PUBLISHED')                                       AS published_total,
    SUM(visibility = 'PUBLIC')                                      AS public_total,
    SUM(publish_date IS NULL)                                       AS missing_publish_date,
    SUM(online_publish_date IS NULL)                                AS missing_online_publish_date,
    SUM(literature_type IS NULL OR literature_type = '')            AS missing_literature_type
FROM literature
WHERE deleted = 0;

SELECT id, name
FROM literature_category
WHERE deleted = 0
ORDER BY sort, id;
