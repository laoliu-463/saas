-- 系统达人列表/详情只读取本地订单事实，不触发上游达人接口。
-- 表达式必须与 LegacyOrderReadFacade 的兼容查询保持完全一致，才能命中索引。
-- 在分区父表创建索引会同步覆盖现有分区，并让后续分区继承同一索引定义。
-- real-pre 影响：普通 CREATE INDEX 构建期间可能短暂阻塞订单写入；由受控迁移窗口在后端调度恢复前执行。
CREATE INDEX IF NOT EXISTS idx_cso_talent_lookup_create_time
    ON colonelsettlement_order (
        (COALESCE(extra_data ->> 'talent_uid',
                  extra_data ->> 'author_id',
                  talent_name)),
        create_time DESC
    )
    WHERE deleted = 0;

-- 回滚：DROP INDEX IF EXISTS idx_cso_talent_lookup_create_time;
