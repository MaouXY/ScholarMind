CREATE TABLE IF NOT EXISTS ai_task_record (
  id BIGINT PRIMARY KEY,
  organization_id BIGINT NULL,
  task_type VARCHAR(64) NOT NULL,
  biz_type VARCHAR(64) NOT NULL,
  biz_id BIGINT NULL,
  model_name VARCHAR(128) NULL,
  request_snapshot TEXT NULL,
  response_snapshot TEXT NULL,
  status VARCHAR(32) NOT NULL,
  error_message TEXT NULL,
  duration_ms BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_ai_task_record_biz (biz_type, biz_id, task_type, status),
  KEY idx_ai_task_record_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS rag_knowledge_document (
  id BIGINT PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  category_id BIGINT NULL,
  content LONGTEXT NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_rag_knowledge_document_category (category_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS rag_knowledge_chunk (
  id BIGINT PRIMARY KEY,
  document_id BIGINT NOT NULL,
  chunk_index INT NOT NULL,
  content TEXT NOT NULL,
  token_count INT NOT NULL DEFAULT 0,
  embedding_provider VARCHAR(128) NULL,
  vector_store_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_rag_knowledge_chunk_document (document_id, chunk_index),
  KEY idx_rag_knowledge_chunk_status (vector_store_status, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS literature_chunk (
  id BIGINT PRIMARY KEY,
  literature_id BIGINT NOT NULL,
  chunk_index INT NOT NULL,
  chunk_text TEXT NOT NULL,
  page_no INT NULL,
  token_count INT NOT NULL DEFAULT 0,
  embedding_provider VARCHAR(128) NULL,
  vector_store_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_literature_chunk_literature (literature_id, chunk_index),
  KEY idx_literature_chunk_status (vector_store_status, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS literature_qa_record (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  literature_id BIGINT NULL,
  question TEXT NOT NULL,
  answer LONGTEXT NOT NULL,
  reference_chunks LONGTEXT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
  error_message TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_literature_qa_user_literature (user_id, literature_id, created_at),
  KEY idx_literature_qa_literature (literature_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS add_stage4_literature_columns;
DELIMITER //
CREATE PROCEDURE add_stage4_literature_columns()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'literature' AND column_name = 'parse_status'
  ) THEN
    ALTER TABLE literature ADD COLUMN parse_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' AFTER reject_reason;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'literature' AND column_name = 'parse_error'
  ) THEN
    ALTER TABLE literature ADD COLUMN parse_error VARCHAR(500) NULL AFTER parse_status;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'literature' AND column_name = 'summary_status'
  ) THEN
    ALTER TABLE literature ADD COLUMN summary_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' AFTER parse_error;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'literature' AND column_name = 'summary_updated_at'
  ) THEN
    ALTER TABLE literature ADD COLUMN summary_updated_at DATETIME NULL AFTER summary_status;
  END IF;
END//
DELIMITER ;
CALL add_stage4_literature_columns();
DROP PROCEDURE IF EXISTS add_stage4_literature_columns;
