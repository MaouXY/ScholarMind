INSERT INTO user_account (
  id, username, password_hash, nickname, email, role, status, deleted
) VALUES
  (1000000000000000001, 'admin', '$2b$10$kUsC1ZV2DZlUKr0w1GlCeeh05BLAInwjuvUqtNESb2x7q/Z7bSBdi', 'System Admin', 'admin@scholarmind.local', 'ADMIN', 1, 0),
  (1000000000000000002, 'student01', '$2b$10$kUsC1ZV2DZlUKr0w1GlCeeh05BLAInwjuvUqtNESb2x7q/Z7bSBdi', 'Student One', 'student01@scholarmind.local', 'STUDENT', 1, 0),
  (1000000000000000003, 'teacher01', '$2b$10$kUsC1ZV2DZlUKr0w1GlCeeh05BLAInwjuvUqtNESb2x7q/Z7bSBdi', 'Teacher One', 'teacher01@scholarmind.local', 'TEACHER', 1, 0)
ON DUPLICATE KEY UPDATE
  nickname = VALUES(nickname),
  email = VALUES(email),
  role = VALUES(role),
  status = VALUES(status),
  deleted = VALUES(deleted);

INSERT INTO literature_category (
  id, name, parent_id, sort, status, deleted
) VALUES
  (1100000000000000001, 'Digital Library', NULL, 10, 1, 0),
  (1100000000000000002, 'Information Retrieval', NULL, 20, 1, 0),
  (1100000000000000003, 'Artificial Intelligence', NULL, 30, 1, 0),
  (1100000000000000004, 'Recommendation System', 1100000000000000003, 10, 1, 0)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  parent_id = VALUES(parent_id),
  sort = VALUES(sort),
  status = VALUES(status),
  deleted = VALUES(deleted);

INSERT INTO literature (
  id, title, author, source, publish_year, language, category_id, uploader_id,
  visibility, status, abstract_text, keywords, read_count, download_count, deleted
) VALUES
  (
    1200000000000000001,
    'Research on Intelligent Recommendation Service in University Digital Library',
    'Li Ming',
    'Library Information Service',
    2024,
    'zh-CN',
    1100000000000000004,
    1000000000000000003,
    'PUBLIC',
    'PUBLISHED',
    'This paper discusses personalized resource recommendation for university digital libraries and proposes a hybrid method based on keywords and user behavior.',
    'digital library,recommendation system,user behavior,personalization',
    36,
    8,
    0
  ),
  (
    1200000000000000002,
    'Semantic Retrieval Method for Academic Literature Based on User Intent',
    'Wang Qian',
    'Modern Information',
    2023,
    'zh-CN',
    1100000000000000002,
    1000000000000000003,
    'PUBLIC',
    'PUBLISHED',
    'This study analyzes natural language search intent and improves academic literature discovery through structured retrieval conditions.',
    'semantic retrieval,natural language,academic literature,user intent',
    28,
    5,
    0
  ),
  (
    1200000000000000003,
    'Application of Large Language Models in Literature Reading Assistance',
    'Zhang Wei',
    'Computer Applications',
    2025,
    'zh-CN',
    1100000000000000003,
    1000000000000000002,
    'PUBLIC',
    'PENDING_REVIEW',
    'The article explores how large language models support summarization, question answering, and reading note generation in literature reading scenarios.',
    'large language model,literature reading,question answering,summary',
    9,
    1,
    0
  ),
  (
    1200000000000000004,
    'Personal Notes on Graduation Thesis Literature Management',
    'Student One',
    'Personal Upload',
    2026,
    'zh-CN',
    1100000000000000001,
    1000000000000000002,
    'PRIVATE',
    'DRAFT',
    'A private literature management note used to demonstrate private resource access control.',
    'graduation thesis,literature management,private note',
    2,
    0,
    0
  )
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
