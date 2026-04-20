# 开发计划：抖音团长 SaaS 系统

**文档版本**：V1.0
**制定日期**：2026-04-20
**制定依据**：`requirements/09-development-roadmap.md` + 全部需求文档 + 约束规则
**状态**：已定稿

---

## 一、系统架构总览

### 1.1 技术栈

| 层级 | 技术选型 | 说明 |
|------|----------|------|
| 后端框架 | Java 17 + Spring Boot 3 | LTS 版本 |
| ORM | MyBatis-Plus 3.5 | UUID 主键、JSONB 支持 |
| 数据库 | PostgreSQL 15+ | 分区表、UUID 主键 |
| 缓存 | Redis | Token 锁、分布式锁 |
| Python 爬虫 | Python 3.11 + Celery | 达人信息采集（独立部署） |
| 前端 | Vue 3 + NaiveUI | 组件化开发 |
| 鉴权 | JWT | RBAC + DataScope |

### 1.2 23 张表清单

```
┌─────────────────────────────────────────────────────────────┐
│  普通表（18张）                                               │
├─────────────────────────────────────────────────────────────┤
│  sys_user | sys_role | sys_user_role | sys_department       │
│  douyin_token | colonel_activity | product                  │
│  colonelsettlement_activity_product | talent | talent_claim   │
│  exclusive_talent | exclusive_merchant | merchant            │
│  pick_source_mapping | sample_request | sample_status_log  │
│  commission_settlement | commission_config | order_detail     │
│  order_decrypt_record | system_config                       │
├─────────────────────────────────────────────────────────────┤
│  分区表（2张）按月 RANGE(create_time)：                        │
│  colonelsettlement_order | operation_log                     │
└─────────────────────────────────────────────────────────────┘
```

### 1.3 模块依赖图

```
基础层
 ├── BaseEntity（统一基类）
 ├── SystemConfig（系统配置）
 └── SysUser / SysRole / SysDepartment（权限体系）
        │
        ▼
  数据层
   ├── Merchant（商家）──────────────┐
   ├── ColonelActivity（团长活动）   │
   ├── Product（商品）              │  依赖独立，无需互指
   ├── PickSourceMapping（归因）     │
   └── Talent（达人）──────────────┘
        │
        ▼
  业务层
   ├── TalentClaim（认领保护）
   ├── SampleRequest（寄样台）──→ 依赖 Product + Talent
   ├── OrderSettlement（订单结算）──→ 依赖全部上游
   ├── Commission（提成计算）─────→ 依赖 OrderSettlement
   └── Exclusive（独家机制）─────→ 依赖 Order + Sample
        │
        ▼
  平台层
   └── DataPlatform（数据看板）───→ 依赖全部业务层
```

---

## 二、模块实现顺序

### 2.1 第一批：基础设施（无依赖）

| 顺序 | 模块 | 交付物 | 约束文件 |
|------|------|--------|----------|
| 1 | BaseEntity | 统一基类 + 审计字段自动填充 | entity-constraints.md |
| 2 | SystemConfig | 系统配置 CRUD + 缓存 | roles-permissions.md §3 |
| 3 | 数据库初始化 | init-db.sql（23张表 + 分区 + 种子数据） | data-schema.md §11 |

### 2.2 第二批：权限体系（依赖第一批）

| 顺序 | 模块 | 交付物 | 约束文件 |
|------|------|--------|----------|
| 4 | SysUser/Role/Dept | 用户角色 CRUD + JWT 鉴权 | roles-permissions.md |
| 5 | DataScope 注解 + 切面 | 多租户数据隔离 AOP | data-scope-lint.md |

### 2.3 第三批：数据采集（依赖第一批）

| 顺序 | 模块 | 交付物 | 约束文件 |
|------|------|--------|----------|
| 6 | DouyinToken 管理 | Token 自动刷新 + Redis 分布式锁 | api-security.md §4 |
| 7 | 达人爬虫（Python） | 达人信息自动采集（3-6秒间隔） | crawler-safety.md |
| 8 | Talent 实体 + Service | 达人 CRUD + 爬虫数据更新 | talent-crm.md |

### 2.4 第四批：核心业务（依赖第二、三批）

| 顺序 | 模块 | 交付物 | 约束文件 |
|------|------|--------|----------|
| 9 | Merchant | 商家被动建库（订单同步时提取） | exclusive-mechanisms.md §2.4 |
| 10 | ColonelActivity | 团长活动 CRUD + 抖音 API 同步 | product-library.md |
| 11 | Product | 商品 CRUD + 审核上下架流程 | product-library.md |
| 12 | PickSourceMapping | 归因映射（ShortID 方案B） | attribution-logic.md |
| 13 | TalentClaim | 达人认领 + 保护期逻辑 | talent-crm.md §2.3 |

### 2.5 第五批：寄样台（依赖第四批）

| 顺序 | 模块 | 交付物 | 约束文件 |
|------|------|--------|----------|
| 14 | SampleRequest | 完整状态机 + 7天限制校验 | sample-management.md |
| 15 | SampleStatusLog | 状态变更日志 | sample-management.md §4.2 |

### 2.6 第六批：订单结算（依赖第四批）

| 顺序 | 模块 | 交付物 | 约束文件 |
|------|------|--------|----------|
| 16 | Order（分区表） | 订单入库 + 时间分区 | partition-table.md |
| 17 | OrderSync 定时任务 | 滑窗同步（10分钟窗口） | api-specs.md §10 |
| 18 | AttributionService | 渠道归因（独家优先 → pick_source） | attribution-logic.md |
| 19 | CommissionService | 提成计算（禁止硬编码比例） | exclusive-triggers.md |

### 2.7 第七批：独家机制（依赖第五、六批）

| 顺序 | 模块 | 交付物 | 约束文件 |
|------|------|--------|----------|
| 20 | ExclusiveTalent | 独家达人判断（70% + ≥10样品/月） | exclusive-mechanisms.md |
| 21 | ExclusiveMerchant | 独家商家判断（70% 服务费占比） | exclusive-mechanisms.md |

### 2.8 第八批：数据平台（依赖第六批）

| 顺序 | 模块 | 交付物 | 约束文件 |
|------|------|--------|----------|
| 22 | DataPlatform Controller | 核心指标 + 订单明细 + 多维度筛选 | data-platform.md |
| 23 | OrderDetail + 解密 | 订单解密（不持久化） | api-security.md §5 |

### 2.9 第九批：前端（与后端并行）

| 顺序 | 页面 | 依赖 |
|------|------|------|
| F1 | 登录页 | 后端 #4 |
| F2 | 商品库（选品 + 审核） | 后端 #10-#11 |
| F3 | 达人 CRM（公海私海） | 后端 #8 + #13 |
| F4 | 寄样台（全流程） | 后端 #14-#15 |
| F5 | 数据看板（指标 + 明细） | 后端 #22-#23 |

---

## 三、V0.5 阶段：骨架与核心流程

**目标**：本地开发，核心流程跑通，Mock 所有外部依赖，不上线

### 3.1 完成标准

| 模块 | 标准 |
|------|------|
| 项目骨架 | Spring Boot + MyBatis-Plus + PostgreSQL + Redis（Docker Compose 一键启动） |
| 登录认证 | JWT Token，6个角色权限 |
| 商品库 | CRUD + 分页 + 筛选（Mock 数据） |
| 达人 CRM | 添加 + 认领 + 保护期逻辑 |
| 寄样台 | 完整状态机 + 流转逻辑（Mock 数据） |
| 数据平台 | 看板展示 + DataScope 过滤（Mock 数据） |
| 单元测试 | 核心业务逻辑 80%+ 覆盖 |

### 0.5 里程碑清单

```
□ [M0.1] 项目骨架搭建
    - backend/ Spring Boot 项目结构
    - frontend/ Vue3 项目结构
    - docker-compose.yml（PostgreSQL + Redis）
    - BaseEntity + CustomMetaObjectHandler

□ [M0.2] 数据库初始化
    - init-db.sql（23张表）
    - 分区表创建（2026-04 ~ 2027-03）
    - 种子数据（6个角色 + 测试用户）

□ [M0.3] 权限体系
    - SysUser / SysRole / SysDept 实体
    - JWT 登录认证
    - DataScope AOP 切面

□ [M0.4] 商品库 CRUD（Mock）
    - Product / ColonelActivity 实体
    - 商品列表 + 详情页

□ [M0.5] 达人 CRM（Mock）
    - Talent / TalentClaim 实体
    - 公海私海列表
    - 认领 + 保护期逻辑

□ [M0.6] 寄样台状态机（Mock）
    - SampleRequest / SampleStatusLog 实体
    - 7天限制校验
    - 完整状态流转

□ [M0.7] 数据看板（Mock）
    - 核心指标卡片
    - 订单明细表（Mock 数据）
    - DataScope 过滤

□ [M0.8] 单元测试覆盖
    - DataScope 过滤逻辑测试
    - 寄样7天限制测试
    - 状态机流转测试
    - 覆盖率 ≥ 80%
```

---

## 四、V1.0 阶段：接入真实数据

**目标**：接入抖音真实 API + PostgreSQL + 爬虫，部署上线

### 4.1 完成标准

| 模块 | 标准 |
|------|------|
| 抖音 API | Token 自动管理、订单同步、转链归因 |
| 数据库 | 23 张表 + 分区表（按月）+ 索引 |
| 爬虫 | 达人信息自动采集（3-6秒安全间隔） |
| 订单解密 | 按需解密，敏感数据不持久化 |
| 性能 | 50 人并发查询不卡顿 |
| 监控 | 请求日志 + 操作日志（保留90天） |

### 4.2 关键技术实现

#### 4.2.1 订单同步（滑窗机制）

```python
# 滑窗同步伪代码
START = NOW - 10_MINUTES
END = NOW
# 本次同步 [START-1min, END]（1分钟重叠，防止漏单）
# 上次同步点自动记录在 Redis
```

#### 4.2.2 Token Redis 分布式锁

```java
// 防并发冲突：error 31012
Boolean acquired = redisTemplate.opsForValue()
    .setIfAbsent(lockKey, "1", Duration.ofMinutes(5));
if (!acquired) {
    throw new BusinessException("Token 刷新任务正在执行中");
}
```

#### 4.2.3 ShortID 归因（方案B）

```java
// 转链时生成 ShortID
String shortId = ShortCodeGenerator.generate(uuidSeed); // 8位 Base36
String pickExtra = shortId; // ≤10 字符
// 存储：pick_source_mapping(short_id, uuid_seed)
// 反查：uuid_seed → channelId
```

#### 4.2.4 虚拟号解密（不持久化）

```java
// 解密结果仅返回前端，不存入数据库
OrderVO vo = OrderVO.builder()
    .phone(response.getPhoneNoA()) // 仅返回
    .build();
// order_detail 表仅存 decrypt_record，不存明文
```

### V1.0 里程碑清单

```
□ [M1.1] 数据库初始化脚本完成
    - init-db.sql 全部 23 张表
    - 分区表 2026-04 ~ 2027-03
    - 全部索引（含 uuid_seed / sample_status_log）
    - 种子数据（系统配置 + 测试用户）

□ [M1.2] 抖音 API SDK 封装完成
    - TokenManager（Redis 锁 + 自动刷新）
    - OrderAPI（滑窗同步）
    - ProductAPI（活动/商品同步）
    - PromotionAPI（转链 ShortID）

□ [M1.3] 订单同步定时任务完成
    - 滑窗同步（10分钟窗口 + 1分钟重叠）
    - 归因映射自动创建
    - 商家被动建库

□ [M1.4] 达人爬虫模块完成（Python）
    - CrawlerBase（3-6秒间隔 + UA轮换 + Cookie管理）
    - 达人信息采集（昵称/粉丝/等级/月销）
    - 失败重试 + 告警

□ [M1.5] 寄样台接入真实数据
    - 达人/商品来自真实数据库
    - 寄样申请校验真实存在
    - 状态机逻辑不变

□ [M1.6] 数据平台接入真实数据
    - 订单明细来自真实分区表
    - 指标计算基于实际 service_fee
    - DataScope 过滤生效

□ [M1.7] Docker Compose 部署验证
    - 一键启动（backend + frontend + PostgreSQL + Redis + Python爬虫）
    - 健康检查通过

□ [M1.8] 性能测试通过（50并发）
    - 订单查询 P99 < 500ms
    - 看板加载 P99 < 1s

□ [M1.9] 上线部署
```

---

## 五、V2.0 阶段：高级功能

**目标**：独家机制、达人分析、物流 API、数据导出

### V2.0 里程碑清单

```
□ [M2.1] 独家达人机制完成
    - 月终定时评估（每月1日 03:00）
    - 双重条件判断（70%服务费占比 + ≥10寄样/月）
    - 独家生效/退出逻辑

□ [M2.2] 独家商家机制完成
    - 月终定时评估（每月1日 03:30）
    - 单一条件判断（70%服务费占比）
    - 独家招商业绩归因

□ [M2.3] 差异化提成配置完成
    - 活动级 / 商品级 提成比例
    - 优先级：商品 > 活动 > 全局
    - 禁止硬编码，必须引用配置

□ [M2.4] 物流 API 对接完成
    - 快递鸟 / 快递100 API
    - 运单状态自动更新
    - 快递中 → 签收 → 待交作业自动流转

□ [M2.5] 达人数据分析看板完成
    - 粉丝趋势（折线图）
    - 转化率统计
    - 合作达人排行

□ [M2.6] 数据导出功能完成
    - 管理员/组长可导出 CSV
    - 权限校验（同 DataScope）
```

---

## 六、单元测试策略

### 6.1 必须覆盖的场景

| 模块 | 测试场景 |
|------|----------|
| DataScope | PERSONAL/DEPT/ALL 三种范围过滤正确 |
| 寄样限制 | 7天内重复申请拦截、豁免角色放行 |
| 独家判断 | 双重条件阈值判断、独家生效/退出 |
| 归因逻辑 | 独家达人优先 → pick_source 映射 |
| 提成计算 | 差异化配置优先级、禁止硬编码 |
| 分区表 | create_time 为空时抛出明确异常 |

### 6.2 Mock 策略（V0.5）

```java
// Mock 抖音 API
@MockBean
private DouyinApi douyinApi;

// Mock 爬虫
@MockBean
private CrawlerService crawlerService;
```

### 6.3 集成测试策略（V1.0+）

- 使用 Testcontainers 启动真实 PostgreSQL
- 使用 H2 或真实 Redis
- 订单同步使用录制的 API 响应

---

## 七、技术风险与应对

| 风险 | 概率 | 影响 | 应对措施 |
|------|------|------|----------|
| 抖音 API 限流 | 中 | 高 | Redis 令牌桶 + 请求间隔 |
| 爬虫被封 | 中 | 高 | UA 轮换 + 3-6秒间隔 + Cookie 管理 |
| 数据量增长 | 高 | 中 | PostgreSQL 分区表 + 定期归档 |
| 并发性能 | 中 | 高 | 提前压测，必要时水平扩容 |
| Token 并发冲突 | 高 | 高 | Redis 分布式锁（防 error 31012） |
| 归因漏单 | 中 | 高 | 滑窗 1 分钟重叠 + 补偿任务 |
| ShortID 冲突 | 低 | 中 | UUID seed 全局唯一保障 |

---

## 八、开发约束索引

所有开发必须严格遵循以下约束文件：

| 约束文件 | 级别 | 核心要求 |
|----------|------|----------|
| `rules/entity-constraints.md` | **CRITICAL** | UUID 主键、Long 金额、BigDecimal 比例、继承 BaseEntity |
| `rules/partition-table.md` | **CRITICAL** | 分区表必须带时间查询、禁止全表扫描 |
| `rules/attribution-logic.md` | **CRITICAL** | 归因必须通过映射表、pick_source ≤128 字符 |
| `rules/api-security.md` | **CRITICAL** | Token Redis 锁、解密数据不持久化 |
| `rules/crawler-safety.md` | **CRITICAL** | 爬虫 3-6 秒间隔、UA 轮换 |
| `rules/exclusive-triggers.md` | **CRITICAL** | 提成比例必须引用配置 |
| `rules/data-scope-lint.md` | **CRITICAL** | Service 必须接收 UUID userId/deptId |
| `rules/golden-rules.md` | **CRITICAL** | 6 条黄金规则汇总 |

---

## 九、相关文件索引

| 文件 | 说明 |
|------|------|
| `requirements/09-development-roadmap.md` | 原始路线图 |
| `requirements/02-data-schema.md` | 数据库设计规范（23张表） |
| `requirements/03-api-specs.md` | API 集成规范 |
| `requirements/01-roles-permissions.md` | 6 大角色 + DataScope |
| `requirements/04-product-library.md` | 商品库功能 |
| `requirements/05-talent-crm.md` | 达人 CRM 功能 |
| `requirements/06-sample-management.md` | 寄样台功能 |
| `requirements/07-data-platform.md` | 数据平台功能 |
| `requirements/08-exclusive-mechanisms.md` | 独家机制功能 |
| `rules/golden-rules.md` | 6 条黄金规则 |
| `backend/src/main/resources/db/init-db.sql` | 完整 DDL |
