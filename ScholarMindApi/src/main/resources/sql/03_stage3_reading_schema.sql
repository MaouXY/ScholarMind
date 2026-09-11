CREATE TABLE IF NOT EXISTS literature_favorite (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  literature_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_literature_favorite_user_literature_deleted (user_id, literature_id, deleted),
  KEY idx_literature_favorite_literature (literature_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reading_history (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  literature_id BIGINT NOT NULL,
  last_page INT NOT NULL DEFAULT 0,
  progress INT NOT NULL DEFAULT 0,
  read_duration INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_reading_history_user_literature_deleted (user_id, literature_id, deleted),
  KEY idx_reading_history_literature (literature_id, updated_at),
  KEY idx_reading_history_user (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reading_note (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  literature_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  ai_generated TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_reading_note_user_literature (user_id, literature_id, updated_at),
  KEY idx_reading_note_literature (literature_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
