INSERT INTO literature_favorite (
  id, user_id, literature_id, deleted
) VALUES
  (1300000000000000001, 1000000000000000002, 1200000000000000001, 0),
  (1300000000000000002, 1000000000000000002, 1200000000000000002, 0)
ON DUPLICATE KEY UPDATE
  deleted = VALUES(deleted);

INSERT INTO reading_history (
  id, user_id, literature_id, last_page, progress, read_duration, deleted
) VALUES
  (1310000000000000001, 1000000000000000002, 1200000000000000001, 12, 65, 48, 0),
  (1310000000000000002, 1000000000000000002, 1200000000000000002, 5, 30, 16, 0)
ON DUPLICATE KEY UPDATE
  last_page = VALUES(last_page),
  progress = VALUES(progress),
  read_duration = VALUES(read_duration),
  deleted = VALUES(deleted);

INSERT INTO reading_note (
  id, user_id, literature_id, title, content, ai_generated, deleted
) VALUES
  (1320000000000000001, 1000000000000000002, 1200000000000000001, 'Hybrid recommendation points', 'The paper combines keyword similarity and behavior weighting, which is suitable for graduation project recommendation demos.', 0, 0),
  (1320000000000000002, 1000000000000000002, 1200000000000000001, 'Possible presentation angle', 'Emphasize explainability: recommendation reason, category match, and reading history can all be shown in the interface.', 1, 0),
  (1320000000000000003, 1000000000000000002, 1200000000000000002, 'Retrieval insight', 'Natural language intent parsing can reduce the difficulty of choosing search keywords for students.', 0, 0)
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  content = VALUES(content),
  ai_generated = VALUES(ai_generated),
  deleted = VALUES(deleted);
