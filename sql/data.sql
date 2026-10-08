-- Fictional demo identities. Password: Demo-Only-Change-Me!2026
-- Never use these accounts or hashes in production.
-- Development-only bootstrap data. This location is enabled by the dev profile
-- and intentionally contains no archives, tasks, records, dangers, files or logs.
-- Every statement is idempotent so an existing account or customized rule wins.

INSERT IGNORE INTO sys_dept
  (id,parent_id,ancestors,dept_name,dept_type,leader,phone,address,jurisdiction,sort_no,status,archived)
VALUES
  (1,0,'0','演示公安处','BUREAU',NULL,NULL,NULL,'演示公安处管辖范围',1,1,0),
  (101,1,'0,1','演示车站01派出所','STATION',NULL,NULL,NULL,NULL,1,1,0),
  (102,1,'0,1','演示车站02派出所','STATION',NULL,NULL,NULL,NULL,2,1,0),
  (103,1,'0,1','演示车站03派出所','STATION',NULL,NULL,NULL,NULL,3,1,0),
  (104,1,'0,1','演示车站04派出所','STATION',NULL,NULL,NULL,NULL,4,1,0),
  (105,1,'0,1','演示车站05派出所','STATION',NULL,NULL,NULL,NULL,5,1,0),
  (106,1,'0,1','演示车站06派出所','STATION',NULL,NULL,NULL,NULL,6,1,0),
  (107,1,'0,1','演示车站07派出所','STATION',NULL,NULL,NULL,NULL,7,1,0),
  (108,1,'0,1','演示车站08派出所','STATION',NULL,NULL,NULL,NULL,8,1,0),
  (109,1,'0,1','演示车站09派出所','STATION',NULL,NULL,NULL,NULL,9,1,0),
  (110,1,'0,1','演示车站10派出所','STATION',NULL,NULL,NULL,NULL,10,1,0),
  (111,1,'0,1','演示车站11派出所','STATION',NULL,NULL,NULL,NULL,11,1,0),
  (112,1,'0,1','演示车站12派出所','STATION',NULL,NULL,NULL,NULL,12,1,0),
  (113,1,'0,1','演示车站13派出所','STATION',NULL,NULL,NULL,NULL,13,1,0),
  (114,1,'0,1','演示车站14派出所','STATION',NULL,NULL,NULL,NULL,14,1,0),
  (115,1,'0,1','演示车站15派出所','STATION',NULL,NULL,NULL,NULL,15,1,0),
  (116,1,'0,1','演示车站16派出所','STATION',NULL,NULL,NULL,NULL,16,1,0),
  (117,1,'0,1','演示车站17派出所','STATION',NULL,NULL,NULL,NULL,17,1,0),
  (118,1,'0,1','演示车站18派出所','STATION',NULL,NULL,NULL,NULL,18,1,0),
  (119,1,'0,1','演示车站19派出所','STATION',NULL,NULL,NULL,NULL,19,1,0),
  (120,1,'0,1','演示车站20派出所','STATION',NULL,NULL,NULL,NULL,20,1,0),
  (121,1,'0,1','演示车站21派出所','STATION',NULL,NULL,NULL,NULL,21,1,0),
  (122,1,'0,1','演示车站22派出所','STATION',NULL,NULL,NULL,NULL,22,1,0),
  (123,1,'0,1','演示车站23派出所','STATION',NULL,NULL,NULL,NULL,23,1,0),
  (124,1,'0,1','演示车站24派出所','STATION',NULL,NULL,NULL,NULL,24,1,0),
  (125,1,'0,1','演示车站25派出所','STATION',NULL,NULL,NULL,NULL,25,1,0);

INSERT IGNORE INTO sys_role(id,role_name,role_code,data_scope,status) VALUES
  (1,'系统管理员','ADMIN','ALL',1),
  (2,'公安处账号','BUREAU','ALL',1),
  (3,'派出所账号','STATION','DEPT',1);

INSERT IGNORE INTO sys_user
  (id,dept_id,username,password,real_name,phone,status,data_scope,force_change_password)
VALUES
  (1,1,'admin','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','系统管理员',NULL,1,'ALL',0),
  (2,1,'bureau','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','公安处账号',NULL,1,'ALL',0),
  (101,101,'station01','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','演示车站01派出所账号',NULL,1,'DEPT',0),
  (102,102,'station02','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','演示车站02派出所账号',NULL,1,'DEPT',0),
  (103,103,'station03','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','演示车站03派出所账号',NULL,1,'DEPT',0),
  (104,104,'station04','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','演示车站04派出所账号',NULL,1,'DEPT',0),
  (105,105,'station05','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','演示车站05派出所账号',NULL,1,'DEPT',0),
  (106,106,'station06','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','演示车站06派出所账号',NULL,1,'DEPT',0),
  (107,107,'station07','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','演示车站07派出所账号',NULL,1,'DEPT',0),
  (108,108,'station08','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','演示车站08派出所账号',NULL,1,'DEPT',0),
  (109,109,'station09','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','演示车站09派出所账号',NULL,1,'DEPT',0),
  (110,110,'station10','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','演示车站10派出所账号',NULL,1,'DEPT',0),
  (111,111,'station11','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','演示车站11派出所账号',NULL,1,'DEPT',0),
  (112,112,'station12','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','演示车站12派出所账号',NULL,1,'DEPT',0),
  (113,113,'station13','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','演示车站13派出所账号',NULL,1,'DEPT',0),
  (114,114,'station14','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','演示车站14派出所账号',NULL,1,'DEPT',0),
  (115,115,'station15','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','演示车站15派出所账号',NULL,1,'DEPT',0),
  (116,116,'station16','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','演示车站16派出所账号',NULL,1,'DEPT',0),
  (117,117,'station17','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','演示车站17派出所账号',NULL,1,'DEPT',0),
  (118,118,'station18','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','演示车站18派出所账号',NULL,1,'DEPT',0),
  (119,119,'station19','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','演示车站19派出所账号',NULL,1,'DEPT',0),
  (120,120,'station20','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','演示车站20派出所账号',NULL,1,'DEPT',0),
  (121,121,'station21','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','演示车站21派出所账号',NULL,1,'DEPT',0),
  (122,122,'station22','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','演示车站22派出所账号',NULL,1,'DEPT',0),
  (123,123,'station23','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','演示车站23派出所账号',NULL,1,'DEPT',0),
  (124,124,'station24','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','演示车站24派出所账号',NULL,1,'DEPT',0),
  (125,125,'station25','$2b$12$nJJEFd6sUiuPj1ZSb733C.sGdFN9AHrmBI4RCZ54jjosXuQFReDli','演示车站25派出所账号',NULL,1,'DEPT',0);

INSERT IGNORE INTO sys_user_role(user_id,role_id) VALUES
  (1,1),(2,2),(101,3),(102,3),(103,3),(104,3),(105,3),(106,3),(107,3),(108,3),(109,3),
  (110,3),(111,3),(112,3),(113,3),(114,3),(115,3),(116,3),(117,3),(118,3),(119,3),
  (120,3),(121,3),(122,3),(123,3),(124,3),(125,3);

INSERT IGNORE INTO sys_config(config_name,config_key,config_value,value_type,remark) VALUES
  ('系统时间边界','system.period.start','2026-Q3','PERIOD','边界之前的任务、统计、提醒、隐患整改和档案检查历史不参与系统展示或逾期扫描'),
  ('公安处检查派出所季度覆盖率','coverage.bureau.station.quarter','50','NUMBER','同一半年两个季度对象不重复，合计覆盖100%'),
  ('公安处检查重点单位季度覆盖率','coverage.bureau.unit.quarter','25','NUMBER','同一年度四个季度对象不重复，合计覆盖100%'),
  ('公安处检查重要部位季度覆盖率','coverage.bureau.part.quarter','25','NUMBER','同一年度四个季度对象不重复，合计覆盖100%'),
  ('派出所检查重点单位季度覆盖率','coverage.station.unit.quarter','100','NUMBER','本派出所辖区季度全覆盖，无需筛选'),
  ('派出所检查重要部位季度覆盖率','coverage.station.part.quarter','100','NUMBER','本派出所辖区季度全覆盖，无需筛选'),
  ('提醒天数','task.remind.days','7,3,1,0','STRING',NULL),
  ('逾期后允许补交','task.allow.overdue.submit','true','BOOLEAN',NULL),
  ('默认隐患整改期限天数','danger.default.deadline.days','30','NUMBER','任务详情选择期限改时默认使用的整改期限'),
  ('上传目录','upload.path','./data/uploads','STRING',NULL),
  ('视频大小限制MB','upload.video.max.mb','5120','NUMBER',NULL),
  ('附件和图片大小限制MB','upload.file.max.mb','5120','NUMBER',NULL),
  ('已删除文件保留天数','upload.deleted.retention.days','30','NUMBER','逻辑删除后保留，超过期限仅清理磁盘文件，数据库追溯记录不删除'),
  ('启用验证码','captcha.enabled','false','BOOLEAN',NULL),
  ('Token有效期分钟','token.expire.minutes','120','NUMBER',NULL);

INSERT IGNORE INTO sys_dict_type(dict_name,dict_type,status) VALUES
  ('任务状态','task_status',1),('检查任务','check_type',1),('检查对象类型','target_type',1),
  ('隐患整改状态','danger_status',1),('隐患整改类型','rectification_type',1);

INSERT INTO sys_dict_data(dict_type,dict_label,dict_value,sort_no,status,color_type)
SELECT seed.dict_type,seed.dict_label,seed.dict_value,seed.sort_no,1,seed.color_type
FROM (
  SELECT 'task_status' dict_type,'未开始' dict_label,'PENDING' dict_value,1 sort_no,'info' color_type UNION ALL
  SELECT 'task_status','已完成','APPROVED',2,'success' UNION ALL
  SELECT 'task_status','已逾期','OVERDUE',3,'danger' UNION ALL
  SELECT 'task_status','逾期完成','OVERDUE_SUBMITTED',4,'warning' UNION ALL
  SELECT 'check_type','内部治安保卫工作监督检查','INTERNAL_SECURITY',1,'primary' UNION ALL
  SELECT 'target_type','派出所','STATION',1,'primary' UNION ALL
  SELECT 'target_type','重点单位','KEY_UNIT',2,'warning' UNION ALL
  SELECT 'target_type','重要部位','IMPORTANT_PART',3,'success' UNION ALL
  SELECT 'danger_status','待整改','PENDING',1,'warning' UNION ALL
  SELECT 'danger_status','已逾期','OVERDUE',2,'danger' UNION ALL
  SELECT 'danger_status','已完成','COMPLETED',3,'success' UNION ALL
  SELECT 'danger_status','逾期完成','OVERDUE_COMPLETED',4,'warning' UNION ALL
  SELECT 'rectification_type','期限改','DEADLINE',1,'danger' UNION ALL
  SELECT 'rectification_type','例行改','ROUTINE',2,'warning'
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_data existing
  WHERE existing.dict_type=seed.dict_type AND existing.dict_value=seed.dict_value
);

INSERT IGNORE INTO sys_menu
  (id,parent_id,menu_name,menu_type,path,component,permission,icon,sort_no)
VALUES
  (1,0,'首页','C','/dashboard','views/DashboardView.vue','dashboard:view','DataBoard',1),
  (10,0,'统计分析','C','/statistics','views/StatisticsView.vue','statistics:view','TrendCharts',2),
  (2,0,'任务列表','C','/tasks/list','views/TaskListView.vue','task:list','Tickets',3),
  (9,0,'隐患整改','C','/hidden-dangers','views/HiddenDangerListView.vue','danger:list','WarningFilled',4),
  (5,0,'档案管理','M','/archives',NULL,NULL,'OfficeBuilding',5),
  (6,5,'派出所档案','C','/archives/stations','views/ArchiveView.vue','station:list',NULL,1),
  (7,5,'重点单位档案','C','/archives/units','views/ArchiveView.vue','unit:list',NULL,2),
  (8,5,'重要部位档案','C','/archives/parts','views/ArchiveView.vue','part:list',NULL,3),
  (11,0,'组织权限','C','/system/users','views/SystemView.vue','system:user:list','User',6),
  (12,0,'系统管理','C','/system/config','views/SystemView.vue','system:config:list','Setting',7);

INSERT IGNORE INTO sys_role_menu(role_id,menu_id)
SELECT 1,id FROM sys_menu WHERE id IN (5,6,7,8,10,11,12);
INSERT IGNORE INTO sys_role_menu(role_id,menu_id)
SELECT 2,id FROM sys_menu WHERE id IN (1,2,5,6,7,8,9,10);
INSERT IGNORE INTO sys_role_menu(role_id,menu_id)
SELECT 3,id FROM sys_menu WHERE id IN (1,2,5,6,7,8,9,10);
