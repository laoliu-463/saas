# AGENTS.md — 抖音团长 SaaS V2.2 开发地图

**版本**：V2.2
**最后更新**：2026-04-20
**适用对象**：AI 智能体 / 开发者

---

## 📍 概览

本项目是「抖音团长 SaaS 系统 V2.2」的代码实现。采用 **Harness Engineering（驭缰工程）** 方法论开发：
- **需求文档化**：`/requirements` 目录存储原子化需求
- **约束自动化**：`/rules` 目录存储强制执行的业务约束
- **地图导航**：`AGENTS.md` 提供任务引导

---

## 🗺️ 目录结构

```
SAAS/
├── requirements/          # 需求仓库（Source of Truth）
│   ├── 01-roles-permissions.md   # 6大角色 + 权限矩阵
│   ├── 02-data-schema.md         # PostgreSQL 设计规范
│   └── 03-api-specs.md          # 抖音 API 对接规范
│
├── rules/                 # 约束规则（机械执行）
│   ├── golden-rules.md            # 黄金规则（全局强制）
│   ├── attribution-logic.md       # 归因逻辑约束
│   ├── exclusive-triggers.md      # 独家判定约束
│   ├── crawler-safety.md          # 爬虫安全约束
│   ├── partition-table.md         # 分区表约束
│   ├── entity-constraints.md     # 实体类约束
│   ├── data-scope-lint.md         # 数据范围过滤约束
│   ├── api-security.md            # API 安全约束
│   └── README.md                  # 约束规则说明
│
├── backend/               # Java/Spring Boot 后端
│   └── src/main/java/com/colonel/saas/
│       ├── entity/       # 实体类（必须继承 BaseEntity）
│       ├── service/      # 业务服务
│       ├── controller/   # REST API
│       ├── douyin/       # 抖音 SDK 封装
│       └── crawler/      # 爬虫模块
│
├── frontend/             # Vue3 前端（规划中）
├── celery/               # Python 爬虫任务（规划中）
└── doc/
    └── DEVELOPMENT-PLAN.md          # 完整开发计划（含三阶段里程碑）
    └── doc/                  # 原始需求文档
```

---

## 🎯 任务入口

### 开发新功能

1. **查看计划** → `doc/DEVELOPMENT-PLAN.md` 了解模块依赖和实现顺序
2. **阅读需求** → 在 `/requirements` 中找到对应需求文件
3. **理解约束** → 在 `/rules` 中找到相关约束文件
4. **检查现有代码** → 在 `/backend` 中找到相似实现作为参考
5. **编写代码** → 遵循约束规则
6. **运行测试** → 确保测试覆盖核心约束

### 修复 Bug

1. **定位问题** → 查看报错信息，确定涉及的模块
2. **查找约束** → 在 `/rules` 中找到相关约束
3. **修复实现** → 确保修复符合约束
4. **添加测试** → 添加回归测试防止再次出现

### 代码审查

1. **检查黄金规则** → `rules/golden-rules.md`
2. **验证约束合规** → 相关 `/rules/*.md` 文件
3. **审查测试覆盖** → 确保核心路径有测试

---

## 📚 需求索引

### 核心业务

| 需求 | 入口文件 | 关键约束 |
|------|----------|----------|
| 用户角色权限 | `requirements/01-roles-permissions.md` | DataScope 过滤 |
| 数据库设计 | `requirements/02-data-schema.md` | UUID 主键、分区表 |
| 抖音 API 对接 | `requirements/03-api-specs.md` | Token 管理、pick_source 归因 |
| 商品库 | `requirements/04-product-library.md` | CRUD、筛选、商品级分佣 |
| 达人 CRM | `requirements/05-talent-crm.md` | 添加、认领、保护期 |
| 寄样台 | `requirements/06-sample-management.md` | 状态机、快递中、待交作业 |
| 数据平台 | `requirements/07-data-platform.md` | 核心指标、归因、提成 |
| 独家机制 | `requirements/08-exclusive-mechanisms.md` | 独家达人/商家触发 |
| 开发路线图 | `requirements/09-development-roadmap.md` | V0.5/V1.0/V2.0 阶段 |
| **开发计划** | `doc/DEVELOPMENT-PLAN.md` | **模块顺序 + 里程碑清单** |

---

## ⚙️ 约束索引

### 业务约束

| 约束 | 文件 | BLOCK 级别 | 适用场景 |
|------|------|------------|----------|
| 归因逻辑 | `rules/attribution-logic.md` | **CRITICAL** | 订单入库时 |
| 独家判定 | `rules/exclusive-triggers.md` | **HIGH** | 提成计算时 |
| 数据范围 | `rules/data-scope-lint.md` | **CRITICAL** | Controller 返回时 |

### 技术约束

| 约束 | 文件 | BLOCK 级别 | 适用场景 |
|------|------|------------|----------|
| 分区表 | `rules/partition-table.md` | **CRITICAL** | 订单/日志查询 |
| 实体类 | `rules/entity-constraints.md` | **HIGH** | 新增实体时 |
| 爬虫安全 | `rules/crawler-safety.md` | **CRITICAL** | 爬虫开发时 |
| API 安全 | `rules/api-security.md` | **CRITICAL** | 敏感数据处理 |

---

## 🔧 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| 后端 | Java 17 + Spring Boot 3 | REST API |
| ORM | MyBatis-Plus | 数据库访问 |
| 数据库 | PostgreSQL 15+ | 分区表支持 |
| 缓存 | Redis | Token 缓存 |
| 任务 | Celery | 异步爬虫任务 |
| 前端 | Vue 3 + Element Plus | 管理后台 |

---

## 🔑 黄金规则速查

> ⚠️ 以下规则必须严格遵守，违反将 **BLOCK CI**

1. **分区表必须带时间查询** → `rules/partition-table.md`
2. **敏感数据不得持久化** → `rules/api-security.md`
3. **归因必须通过映射表** → `rules/attribution-logic.md`
4. **提成比例必须引用配置** → `rules/exclusive-triggers.md`
5. **爬虫必须遵循安全间隔** → `rules/crawler-safety.md`
6. **业务表必须继承 BaseEntity** → `rules/entity-constraints.md`

---

## 📁 文件命名规范

| 类型 | 命名规范 | 示例 |
|------|----------|------|
| 实体类 | PascalCase，`Entity` 后缀 | `TalentEntity.java` |
| Service | PascalCase，`Service` 后缀 | `AttributionService.java` |
| Controller | PascalCase，`Controller` 后缀 | `OrderController.java` |
| Mapper | PascalCase，`Mapper` 后缀 | `OrderMapper.java` |
| VO | PascalCase，`VO` 后缀 | `OrderVO.java` |
| DTO | PascalCase，`DTO` 后缀 | `OrderCreateDTO.java` |

---

## 🧪 测试要求

| 测试类型 | 覆盖要求 | 运行命令 |
|----------|----------|----------|
| 单元测试 | 核心业务逻辑 80%+ | `mvn test` |
| 集成测试 | API 端点覆盖 | `mvn verify` |
| 约束测试 | 黄金规则回归 | `mvn test -Dtest=*GoldenRuleTest` |

---

## 🚨 常见违规模式

### ❌ 禁止模式

```java
// 1. 禁止分区表全表扫描
orderMapper.selectList(null); // 违反 partition-table.md

// 2. 禁止存储敏感数据
order.setPhone(phone); // 违反 api-security.md

// 3. 禁止硬编码归因
channelId = pickSource.split("_")[0]; // 违反 attribution-logic.md

// 4. 禁止硬编码提成比例
new BigDecimal("0.15"); // 违反 exclusive-triggers.md
```

### ✅ 正确模式

```java
// 1. 分区表必须带时间
.orderMapper.selectList(wrapper.between(Order::getCreateTime, start, end));

// 2. 敏感数据仅展示
return OrderVO.builder().phone(phone).build();

// 3. 必须通过归因映射
channelId = attributionService.resolve(pickSource);

// 4. 必须引用配置
ratio = systemConfig.getCommissionRatio(level);
```

---

## 📞 帮助与反馈

- **开始新功能** → 先阅读 `doc/DEVELOPMENT-PLAN.md` 了解模块依赖和顺序
- **文档问题** → 修改 `/requirements` 或 `/rules` 中的对应文件
- **新增约束** → 在 `/rules` 中添加新约束文件，并更新 `golden-rules.md`
- **Bug 报告** → 在 GitHub Issues 中描述问题，关联相关约束文件

---

*本文件是 Harness Engineering 的核心导航地图。每次启动新的开发任务前，请先阅读本文件。*
