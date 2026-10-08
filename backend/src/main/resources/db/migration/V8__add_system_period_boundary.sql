INSERT INTO sys_config(config_name, config_key, config_value, value_type, system_flag, remark)
VALUES ('系统时间边界', 'system.period.start', '2026-Q3', 'PERIOD', 1,
        '边界之前的任务、统计、提醒、隐患整改和档案检查历史不参与系统展示或逾期扫描')
ON DUPLICATE KEY UPDATE
    config_name = VALUES(config_name),
    value_type = VALUES(value_type),
    system_flag = VALUES(system_flag),
    remark = VALUES(remark);
