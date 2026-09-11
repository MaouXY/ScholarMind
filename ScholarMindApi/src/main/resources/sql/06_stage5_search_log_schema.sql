-- 第五阶段：检索行为日志（用于热门关键词与检索热度统计）
CREATE TABLE IF NOT EXISTS literature_search_log (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  organization_id BIGINT NULL,
  query_text VARCHAR(500) NOT NULL,
  keywords VARCHAR(500) NULL,
  search_type VARCHAR(32) NOT NULL,
  category_id BIGINT NULL,
  result_count INT NOT NULL DEFAULT 0,
  reference_count INT NOT NULL DEFAULT 0,
  intent_parsed TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_literature_search_log_created (created_at),
  KEY idx_literature_search_log_user (user_id, created_at),
  KEY idx_literature_search_log_type (search_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
