ALTER TABLE sys_user
  MODIFY COLUMN force_change_password TINYINT NOT NULL DEFAULT 0;

UPDATE sys_user
SET force_change_password = 0
WHERE force_change_password <> 0;
