-- 清空默认寄样标准。
-- 当前阶段不启用近30天销售额和达人等级门槛，后续由业务配置时再写入具体规则。
UPDATE system_config
SET config_value = '{}',
    update_time = CURRENT_TIMESTAMP
WHERE config_key = 'sample.default_standard'
  AND (deleted IS NULL OR deleted = 0);
