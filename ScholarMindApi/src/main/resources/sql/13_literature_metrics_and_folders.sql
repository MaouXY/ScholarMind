-- ============================================================================
-- 13 期改造：检索维度扩展 + 收藏夹
--
-- 客户 2026-11-16 确认的六条口径：
--   1. 文献类别换成四类：学术期刊 / 学位论文（硕士）/ 学位论文（博士）/ 报纸；
--      存量 33 条全部回填「学术期刊」（它们本来就是期刊、会议与预印本论文）。
--   2. 学科换成中图法一级门类 8 类：基础科学 / 工程科技 / 农业科技 / 医药卫生科技 /
--      哲学与人文科学 / 社会科学 / 信息科技 / 经济与管理科学；
--      存量按原 CS 细分主题重新挂靠（原 8 个分类全部落到「信息科技」）。
--   3. 新增 citation_count（被引次数）：库里原本没有任何被引数据，由管理员手填，
--      种子数据补值后标注为演示数据。
--   4. 新增 references_text（参考文献原文）：供「参考文献」检索维度做全文匹配，
--      管理员上传时手填。
--   5. 新增 literature_favorite_folder：收藏夹，单归属（一篇文献只在一个夹）。
--   6. 检索维度：主题 / 关键词 / 全文 / 作者 / 摘要 / 参考文献 / 文献来源。
--
-- 幂等：重复执行不会报错，也不会覆盖已填写的业务数据。
-- 执行：完成 sql/00~12 后执行本文件。
-- ============================================================================

USE scholar_mind;

-- ---------------------------------------------------------------------------
-- 一、literature 新增列
-- ---------------------------------------------------------------------------

-- 1.1 被引次数
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'literature' AND COLUMN_NAME = 'citation_count'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE literature ADD COLUMN citation_count INT NOT NULL DEFAULT 0 COMMENT ''被引次数（管理员手填，演示数据）'' AFTER download_count',
    'SELECT ''citation_count already exists'' AS skip_msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1.2 参考文献原文
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'literature' AND COLUMN_NAME = 'references_text'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE literature ADD COLUMN references_text TEXT NULL COMMENT ''参考文献列表原文，供参考文献检索维度匹配'' AFTER ai_summary',
    'SELECT ''references_text already exists'' AS skip_msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 二、文献类别改口径：六类 -> 四类
--    旧值：期刊论文 / 会议论文 / 学位论文 / 图书 / 预印本 / 其他
--    新值：学术期刊 / 学位论文（硕士）/ 学位论文（博士）/ 报纸
-- ---------------------------------------------------------------------------

-- 期刊、会议、预印本、图书本质都是「学术期刊」口径下的公开出版物，统一归入
UPDATE literature
SET literature_type = '学术期刊'
WHERE deleted = 0
  AND (literature_type IS NULL OR literature_type IN ('期刊论文', '会议论文', '预印本', '图书', '其他', '学术期刊'));

-- 旧「学位论文」无法区分硕博，统一按硕士归档（存量里没有这类数据，属防御）
UPDATE literature
SET literature_type = '学位论文（硕士）'
WHERE deleted = 0
  AND literature_type = '学位论文';

-- 列宽从 32 放宽：'学位论文（硕士）' 是 9 个字符，utf8mb4 下 32 长度仍够，
-- 但为了后续可能的扩展先放宽到 64。
ALTER TABLE literature
    MODIFY COLUMN literature_type VARCHAR(64) NULL COMMENT '文献类别：学术期刊/学位论文（硕士）/学位论文（博士）/报纸';

-- ---------------------------------------------------------------------------
-- 三、学科改口径：CS 细分 -> 中图法一级门类 8 类
--    原 8 个分类（1001~1013）全部是计算机方向，统一挂到「信息科技」下，
--    并保留原分类作为二级节点，便于日后细化。
-- ---------------------------------------------------------------------------

-- 3.1 建 8 个一级门类（父节点为空）
INSERT INTO literature_category (id, name, parent_id, sort, status, deleted, created_at, updated_at)
VALUES
    (1100000000000001001, '基础科学',       NULL, 10, 1, 0, NOW(), NOW()),
    (1100000000000001002, '工程科技',       NULL, 20, 1, 0, NOW(), NOW()),
    (1100000000000001003, '农业科技',       NULL, 30, 1, 0, NOW(), NOW()),
    (1100000000000001004, '医药卫生科技',   NULL, 40, 1, 0, NOW(), NOW()),
    (1100000000000001005, '哲学与人文科学', NULL, 50, 1, 0, NOW(), NOW()),
    (1100000000000001006, '社会科学',       NULL, 60, 1, 0, NOW(), NOW()),
    (1100000000000001007, '信息科技',       NULL, 70, 1, 0, NOW(), NOW()),
    (1100000000000001008, '经济与管理科学', NULL, 80, 1, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    sort = VALUES(sort),
    status = 1,
    deleted = 0;

-- 3.2 原 8 个 CS 细分降级为「信息科技」的二级节点
UPDATE literature_category
SET parent_id = 1100000000000001007
WHERE deleted = 0
  AND id IN (
    1100000000000000001,  -- 数字图书馆
    1100000000000000002,  -- 信息检索
    1100000000000000003,  -- 人工智能
    1100000000000000004,  -- 推荐系统
    1100000000000000010,  -- 数据挖掘
    1100000000000000011,  -- 知识图谱
    1100000000000000012,  -- 学术写作
    1100000000000000013   -- 语义检索
  );

-- 3.3 存量文献全部挂到「信息科技」一级门类
UPDATE literature
SET category_id = 1100000000000001007
WHERE deleted = 0;

-- ---------------------------------------------------------------------------
-- 四、收藏夹
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS literature_favorite_folder (
    id          BIGINT       NOT NULL COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '归属用户',
    name        VARCHAR(64)  NOT NULL COMMENT '收藏夹名称',
    description VARCHAR(255) NULL     COMMENT '备注',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '展示顺序，越小越靠前',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- 同一用户下收藏夹不重名（逻辑删除后允许重建同名夹）
    UNIQUE KEY uk_favorite_folder_user_name_deleted (user_id, name, deleted),
    KEY idx_favorite_folder_user (user_id, sort)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='收藏夹：用户自定义的文献分组';

-- 4.1 favorite 表加 folder_id（NULL 表示「未分类」）
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'literature_favorite' AND COLUMN_NAME = 'folder_id'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE literature_favorite ADD COLUMN folder_id BIGINT NULL COMMENT ''所属收藏夹，NULL 表示未分类'' AFTER literature_id',
    'SELECT ''folder_id already exists'' AS skip_msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'literature_favorite' AND INDEX_NAME = 'idx_favorite_user_folder'
);
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE literature_favorite ADD KEY idx_favorite_user_folder (user_id, folder_id, created_at)',
    'SELECT ''idx_favorite_user_folder already exists'' AS skip_msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4.2 为每个已有收藏的用户建一个「默认收藏夹」，把现有收藏归入其中
INSERT INTO literature_favorite_folder (id, user_id, name, description, sort, deleted, created_at, updated_at)
SELECT
    -- 用 user_id 派生稳定主键，避免依赖 AUTO_INCREMENT 与重复执行
    8800000000000000000 + ROW_NUMBER() OVER (ORDER BY user_id) * 100 + (user_id % 100),
    user_id,
    '默认收藏夹',
    '升级时自动创建，可重命名或删除',
    0,
    0,
    NOW(),
    NOW()
FROM (
    SELECT DISTINCT user_id
    FROM literature_favorite
    WHERE deleted = 0
      AND user_id NOT IN (SELECT user_id FROM literature_favorite_folder WHERE deleted = 0)
) AS pending_users;

-- 4.3 未分夹的历史收藏归入各自的默认收藏夹
UPDATE literature_favorite f
JOIN literature_favorite_folder fo
  ON fo.user_id = f.user_id AND fo.name = '默认收藏夹' AND fo.deleted = 0
SET f.folder_id = fo.id
WHERE f.deleted = 0
  AND f.folder_id IS NULL;

-- ---------------------------------------------------------------------------
-- 五、校验
-- ---------------------------------------------------------------------------

SELECT '文献类别分布' AS metric, literature_type AS value, COUNT(*) AS total
FROM literature WHERE deleted = 0 GROUP BY literature_type;

SELECT '学科分布' AS metric, c.name AS value, COUNT(l.id) AS total
FROM literature l LEFT JOIN literature_category c ON c.id = l.category_id
WHERE l.deleted = 0 GROUP BY c.name ORDER BY total DESC;

SELECT '收藏夹' AS metric, COUNT(*) AS value FROM literature_favorite_folder WHERE deleted = 0;

SELECT '未分夹收藏' AS metric, COUNT(*) AS value FROM literature_favorite WHERE deleted = 0 AND folder_id IS NULL;

SELECT '被引次数非零文献' AS metric, COUNT(*) AS value FROM literature WHERE deleted = 0 AND citation_count > 0;

SELECT '有参考文献的文献' AS metric, COUNT(*) AS value FROM literature WHERE deleted = 0 AND references_text IS NOT NULL;
