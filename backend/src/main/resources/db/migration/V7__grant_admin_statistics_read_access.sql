UPDATE sys_role_menu role_menu
JOIN sys_role role ON role.id = role_menu.role_id AND role.deleted = 0
JOIN sys_menu menu ON menu.id = role_menu.menu_id AND menu.deleted = 0
SET role_menu.deleted = 0,
    role_menu.update_time = NOW(),
    role_menu.remark = '管理员统计分析只读权限'
WHERE role.role_code = 'ADMIN'
  AND menu.permission = 'statistics:view';

INSERT INTO sys_role_menu(role_id, menu_id, create_by, update_by, deleted, remark)
SELECT role.id, menu.id, 0, 0, 0, '管理员统计分析只读权限'
FROM sys_role role
JOIN sys_menu menu ON menu.permission = 'statistics:view' AND menu.deleted = 0
WHERE role.role_code = 'ADMIN'
  AND role.deleted = 0
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_menu role_menu
    WHERE role_menu.role_id = role.id
      AND role_menu.menu_id = menu.id
  );
