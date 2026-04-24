# 达人 CRM 表结构对比与迁移清单

更新时间：2026-04-24  
适用范围：`backend` PostgreSQL 结构（UUID 主键体系）

---

## 1. 对比结论（目标 vs 现状）

| 模块 | 目标表 | 当前状态 | 结论 |
|---|---|---|---|
| 达人主档 | `talent` | `init-db.sql` 已有；`alter-talent-enrich.sql` 补充了 `douyin_no/uid/sec_uid/profile_url/enrich_status/last_enrich_time/data_source` | 已覆盖（口径基本一致） |
| 认领关系 | `talent_claim` | 已存在 | 已覆盖（字段命名与草案不同） |
| 独家达人 | `exclusive_talent` | 已存在 | 已覆盖（实现口径更偏结算/评估） |
| 自动补全任务 | `talent_enrich_task` | 已建表 + 实体 + Mapper | 已建模，流程待补齐 |
| 字段来源审计 | `talent_field_source` | 已建表 + 实体 + Mapper | 已建模，写入链路待补齐 |
| 达人授权 | `talent_auth` | 已建表 + 实体 + Mapper | 已建模，授权流程待补齐 |
| 联系方式 | `talent_contact` | 未落地 | 缺失 |
| 标签 | `talent_tag` | 未落地 | 缺失 |
| 标签关系 | `talent_tag_relation` | 未落地 | 缺失 |
| 采集明细日志 | `talent_crawl_log` | 未落地（仅有快照表 `crawler_talent_info`） | 缺失 |

---

## 2. 本次补齐内容

已新增增量脚本：`backend/src/main/resources/db/alter-talent-crm-gap-fill.sql`

包含以下对象：
1. `talent_contact`
2. `talent_tag`
3. `talent_tag_relation`
4. `talent_crawl_log`
5. 若干查询索引（`talent.douyin_no`、`talent.last_enrich_time`）

说明：
- 仅新增，不改旧字段，确保向后兼容。
- 统一沿用项目现有 UUID 主键与 `BaseEntity` 通用字段风格。

---

## 3. 执行顺序（建议）

按顺序执行以下 SQL：
1. `backend/src/main/resources/db/init-db.sql`
2. `backend/src/main/resources/db/alter-talent-enrich.sql`
3. `backend/src/main/resources/db/alter-talent-crm-gap-fill.sql`

---

## 4. 落地注意事项

1. 当前 `docker-compose.yml` 只在 PostgreSQL 首次初始化时挂载执行 `init-db.sql`。  
2. `alter-*.sql` 属于增量迁移脚本，需要在已有库中手动执行或接入迁移工具。  
3. 若后续引入 Flyway/Liquibase，建议将上述 3 个脚本转为版本化迁移文件（如 `V1__init.sql`、`V2__talent_enrich.sql`、`V3__talent_crm_gap_fill.sql`）。

---

## 5. 下一步建议（代码层）

1. `TalentService.create()`：创建 `talent_enrich_task(PENDING)`。  
2. `TalentService.refresh()`：任务状态流转 `RUNNING -> SUCCESS/FAILED`。  
3. 新增 `GET /talents/{id}/enrich-task/latest`。  
4. 在补全流程中写入 `talent_field_source`。  
5. 联系方式/标签相关接口落地并接入前端中文展示。
