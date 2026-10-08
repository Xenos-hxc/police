UPDATE sys_role_menu role_menu
JOIN sys_role role ON role.id = role_menu.role_id AND role.deleted = 0
JOIN sys_menu menu ON menu.id = role_menu.menu_id AND menu.deleted = 0
SET role_menu.deleted = 1, role_menu.update_time = NOW()
WHERE role.role_code = 'ADMIN'
  AND menu.permission IN ('dashboard:view', 'statistics:view', 'task:list', 'danger:list')
  AND role_menu.deleted = 0;
