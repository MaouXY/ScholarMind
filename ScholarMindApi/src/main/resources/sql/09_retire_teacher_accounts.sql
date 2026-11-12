-- =====================================================================
-- 第九阶段：教师端下线（客户《完善需求（改）》：删除教师端及其相关功能，只保留用户端和管理端）
--
-- 处理策略：账号不物理删除，改为「转用户端账号」，原因：
--   1) 存量 12 篇演示文献的 literature.uploader_id 指向这 3 个账号（teacher01/02/03），
--      物理删除会让文献归属悬空（数据库无外键，应用层也无法还原归属）。
--   2) 收藏、阅读历史、笔记等历史行为同样按 user_id 记录，删除会丢失演示数据。
-- 结果：管理端用户列表只剩「用户端 / 管理端」两类角色，登录页不再提供教师入口。
-- 可重复执行（幂等）：只在仍为 teacher0x 时改名，改过之后重跑不会重复执行。
-- =====================================================================

USE scholar_mind;

-- 1) 三个演示教师账号转用户端（保留 id，从而保留文献/收藏/笔记归属）
UPDATE user_account
SET username   = 'student08',
    nickname   = '林晓',
    email      = 'student08@scholarmind.local',
    role       = 'STUDENT',
    status     = 1,
    updated_at = NOW()
WHERE id = 1000000000000000003
  AND username = 'teacher01';

UPDATE user_account
SET username   = 'student09',
    nickname   = '郑楠',
    email      = 'student09@scholarmind.local',
    role       = 'STUDENT',
    status     = 1,
    updated_at = NOW()
WHERE id = 1000000000000000016
  AND username = 'teacher02';

UPDATE user_account
SET username   = 'student10',
    nickname   = '王琳',
    email      = 'student10@scholarmind.local',
    role       = 'STUDENT',
    status     = 1,
    updated_at = NOW()
WHERE id = 1000000000000000017
  AND username = 'teacher03';

-- 2) 兜底：任何残留的教师角色账号统一转用户端并停用，确保管理端不再出现「教师」
UPDATE user_account
SET role       = 'STUDENT',
    status     = 0,
    updated_at = NOW()
WHERE role = 'TEACHER'
  AND deleted = 0;

-- 3) 校验：应返回 0
SELECT COUNT(*) AS remaining_teacher_accounts
FROM user_account
WHERE role = 'TEACHER'
  AND deleted = 0;
