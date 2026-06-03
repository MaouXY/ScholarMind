CREATE TABLE IF NOT EXISTS user_account (
  id BIGINT PRIMARY KEY,
  username VARCHAR(32) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  nickname VARCHAR(32) NOT NULL,
  email VARCHAR(128) NULL,
  avatar VARCHAR(512) NULL,
  role VARCHAR(32) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_user_account_username_deleted (username, deleted),
  KEY idx_user_account_role_status (role, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS file_metadata (
  id BIGINT PRIMARY KEY,
  original_name VARCHAR(255) NOT NULL,
  object_key VARCHAR(512) NOT NULL,
  bucket_name VARCHAR(128) NOT NULL,
  content_type VARCHAR(128) NULL,
  size_bytes BIGINT NOT NULL DEFAULT 0,
  uploader_id BIGINT NOT NULL,
  uploader_role VARCHAR(32) NOT NULL,
  biz_type VARCHAR(64) NOT NULL DEFAULT 'TEMP',
  biz_id BIGINT NULL,
  public_url VARCHAR(1024) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_file_metadata_uploader (uploader_id, biz_type),
  KEY idx_file_metadata_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS literature_category (
  id BIGINT PRIMARY KEY,
  name VARCHAR(64) NOT NULL,
  parent_id BIGINT NULL,
  sort INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_literature_category_parent (parent_id, sort),
  UNIQUE KEY uk_literature_category_name_parent_deleted (name, parent_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS literature (
  id BIGINT PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  author VARCHAR(255) NULL,
  source VARCHAR(255) NULL,
  publish_year INT NULL,
  language VARCHAR(32) NULL,
  category_id BIGINT NULL,
  uploader_id BIGINT NOT NULL,
  visibility VARCHAR(32) NOT NULL DEFAULT 'PRIVATE',
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  file_id BIGINT NULL,
  file_url VARCHAR(1024) NULL,
  file_type VARCHAR(128) NULL,
  file_size BIGINT NULL,
  abstract_text TEXT NULL,
  ai_summary TEXT NULL,
  keywords VARCHAR(512) NULL,
  read_count INT NOT NULL DEFAULT 0,
  download_count INT NOT NULL DEFAULT 0,
  reviewed_by BIGINT NULL,
  reviewed_at DATETIME NULL,
  reject_reason VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_literature_category_status (category_id, status),
  KEY idx_literature_uploader (uploader_id, visibility, status),
  KEY idx_literature_public (visibility, status, read_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
