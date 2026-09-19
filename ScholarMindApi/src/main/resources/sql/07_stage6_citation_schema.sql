-- 第六阶段（P0 补齐）：引用管理
-- 记录用户收藏的引用条目，引用文本按所选格式（GB/T 7714、APA、MLA）在保存时生成并留痕
CREATE TABLE IF NOT EXISTS citation_record (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  literature_id BIGINT NOT NULL,
  citation_style VARCHAR(32) NOT NULL,
  citation_text TEXT NOT NULL,
  note VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_citation_record_user (user_id, created_at),
  KEY idx_citation_record_literature (literature_id, citation_style)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
