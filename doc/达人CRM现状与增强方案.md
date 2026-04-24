# 达人 CRM 现状与增强方案

**版本**：V1.0  
**更新日期**：2026-04-24  
**状态**：执行依据

---

## 1. 目标

达人 CRM 是渠道管理合作达人的核心模块，目标是形成以下业务闭环：

1. 渠道新增达人
2. 达人进入公海或私海
3. 渠道认领达人并获得保护期
4. 达人与寄样台联动
5. 订单与服务费反哺达人档案
6. 逐步接入自动采集、周期刷新、独家达人计算

---

## 2. 当前代码实况

以下能力已在当前代码中落地：

### 2.1 已实现

1. 基础实体
- `Talent`
- `TalentClaim`

2. 后端接口
- `GET /talents`
- `GET /talents/{id}`
- `POST /talents`
- `PUT /talents/{id}`
- `DELETE /talents/{id}`
- `GET /talents/pools/public`
- `GET /talents/pools/private`
- `POST /talents/{id}/claims`
- `POST /talents/{id}/release`
- `GET /talents/{id}/exclusive-status`

3. 核心业务规则
- 公海达人基于“未被有效认领”计算
- 私海达人基于当前用户有效认领记录计算
- 认领默认保护期 30 天
- 认领使用 Redis 锁防重入
- 已支持认领人、同组、管理员释放达人
- 已有“过期认领释放”服务方法 `TalentService.releaseExpiredClaims`
- 已有“独家达人资格判断”服务方法 `TalentService.evaluateExclusive`

4. 业务联动
- 独家达人判断已联动订单与寄样数据
- 寄样台已存在达人候选检索与达人字段沉淀能力

### 2.2 当前未实现

以下内容在当前代码中尚未完整落地：

1. 新增达人时按抖音号、主页链接、分享链接自动解析并补全
2. 达人联系方式表与多联系方式管理
3. 达人标签、标签关系、备注增强
4. 达人刷新接口与每周自动刷新任务
5. 达人详情中的合作档案聚合视图
6. 团队达人专用接口
7. 认领转移、管理员强制调整归属
8. 达人采集日志独立落库与可视化追踪
9. 保护期过期定时任务独立 Job 编排

结论：
- 当前项目中的达人 CRM 属于“基础版已可用”
- 还未达到“自动补全 + 合作档案 + 刷新 + 独家自动生效”的完整版本

---

## 3. 当前实现对应代码

### 3.1 控制器

- `backend/src/main/java/com/colonel/saas/controller/TalentController.java`

### 3.2 服务

- `backend/src/main/java/com/colonel/saas/service/TalentService.java`

### 3.3 实体

- `backend/src/main/java/com/colonel/saas/entity/Talent.java`
- `backend/src/main/java/com/colonel/saas/entity/TalentClaim.java`

### 3.4 已有联动模块

- 寄样搜索与候选：`SampleController`、`CrawlerTalentInfoService`
- 独家达人归因：`ExclusiveTalentService`
- 达人爬虫基础能力：`DouyinTalentCrawler`、`CrawlerScheduler`

---

## 4. 建议目标模型

基于当前代码与后续业务目标，达人 CRM 建议拆成 6 个能力层：

1. 达人档案
- 基础信息
- 联系方式
- 标签备注
- 手动维护字段

2. 达人采集
- 输入抖音号、UID、主页链接、分享链接
- 标准化解析
- 自动补全达人基础信息
- 采集日志

3. 公海 / 私海
- 公海达人
- 我的达人
- 团队达人
- 保护期控制

4. 达人认领
- 认领
- 释放
- 自动释放
- 转移与管理员调整

5. 合作档案
- 寄样记录
- 订单记录
- 服务费统计
- 最近合作时间
- 合作次数

6. 独家达人
- 月寄样数量统计
- 服务费占比统计
- 自动生效 / 自动退出

---

## 5. 建议数据库增强方向

当前代码已使用：

1. `talent`
2. `talent_claim`

建议后续补充：

1. `talent_contact`
- 一个达人多联系方式

2. `talent_tag`
- 标签定义

3. `talent_tag_relation`
- 达人标签关系

4. `talent_crawl_log`
- 采集请求、响应、错误日志

5. `exclusive_talent`
- 当前项目已有独家达人实体能力，可继续沿用现有设计扩展，不建议重复造表

说明：
- 若继续沿用当前 UUID 主键体系，应保持新表与现有 `BaseEntity` 体系一致
- 若引入 BigInt Snowflake，需要统一全项目主键策略后再落地

---

## 6. 建议接口增强方向

在保留当前 `/talents` 路由体系基础上，建议分阶段补充：

### 6.1 基础增强

- `GET /talents/pools/team`
- `POST /talents/{id}/refresh`
- `GET /talents/{id}/claims`
- `GET /talents/{id}/cooperations`

### 6.2 联系方式与标签

- `GET /talents/{id}/contacts`
- `POST /talents/{id}/contacts`
- `PUT /talents/{id}/contacts/{contactId}`
- `DELETE /talents/{id}/contacts/{contactId}`
- `GET /talents/tags`
- `POST /talents/{id}/tags`

### 6.3 认领管理增强

- `POST /talents/{id}/transfers`
- `POST /talents/{id}/claims/{claimId}/expire`

---

## 7. 推荐开发顺序

### 阶段 A：补齐当前基础版缺口

目标：让现有达人 CRM 更接近“可运营”

1. 补团队达人接口
2. 补达人详情聚合视图
3. 补释放原因与认领记录查询
4. 补保护期过期定时任务
5. 同步完善接口文档和前端页面

### 阶段 B：接入真实采集

目标：达人新增不再依赖纯手工录入

1. 输入值解析
2. 采集任务创建
3. 基础信息补全
4. 手动刷新
5. 每周刷新
6. 采集日志记录

### 阶段 C：合作档案与独家机制

目标：达人档案与订单、寄样、收益形成闭环

1. 达人详情沉淀寄样记录
2. 达人详情沉淀订单记录
3. 达人服务费统计
4. 独家达人自动判断与生效

---

## 8. 当前项目建议最小闭环

结合当前代码基础，建议优先跑通以下闭环：

```text
新增达人
  ↓
进入达人列表
  ↓
认领达人
  ↓
进入私海
  ↓
寄样台选择达人
  ↓
寄样记录沉淀到达人详情
  ↓
订单与服务费反哺达人档案
```

这个闭环完成后，再推进：

```text
新增达人时自动采集
  ↓
周期刷新达人信息
  ↓
统计近 30 天产出
  ↓
自动判定独家达人
```

---

## 9. 文档口径

从今天起，达人 CRM 相关文档统一按以下口径描述：

1. 当前代码状态：基础版已完成
2. 自动采集、联系方式、标签、刷新、合作档案：待增强
3. 独家达人自动化：已有基础判断能力，完整业务版待继续补齐

避免再把“达人 CRM 已完成”表述为“完整版本已完成”。
