# 抖音团长 SaaS 系统 — 项目总览

**版本**：V3.0
**更新日期**：2026-04-23
**适用对象**：开发者 / AI 智能体

---

## 一、项目定位

**项目名称**：Colonel SaaS（抖音团长 SaaS 系统）

**业务定位**：面向抖音电商团长（带货中介）的 B2B SaaS 管理平台，帮助团长高效管理达人资源、商家合作、活动商品、订单归因和佣金结算等核心业务流程。

**核心价值链**：

```
商家入驻 → 选择活动 → 上架商品
                            │
              ┌─────────────┼─────────────┐
              │                           │
        [达人选品]                   [团长推荐]
              │                           │
              └─────────────┬─────────────┘
                            │
                      [发起寄样]
                            │
                      [达人作业]
                            │
              [产生订单（归因）]
                            │
              [计算佣金（按归因结果）]
                            │
              [生成结算单（按配置比例）]
```

---

## 二、技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端 | Spring Boot + MyBatis-Plus | Spring Boot 3.2.5, Java 17 |
| 前端 | Vue 3 + Naive UI + Vite | Vue 3.4+ |
| 数据库 | PostgreSQL | 15+（支持表分区） |
| 缓存 | Redis | 7.0+ |
| 认证 | JWT + Spring Security | jjwt 0.12.5 |
| 三方集成 | 抖音开放平台 SDK | doudian-sdk-java-1.1.0 |
| 文档 | Knife4j OpenAPI 3 | - |
| 工具库 | Hutool | 5.8.26 |

---

## 三、项目结构

```
SAAS/
├── backend/                          # Spring Boot 后端
│   ├── src/main/java/com/colonel/saas/
│   │   ├── auth/                    # 认证授权模块
│   │   ├── common/                  # 公共基础（异常/响应/基类）
│   │   ├── config/                  # 配置类
│   │   ├── controller/              # 业务控制器
│   │   ├── crawler/                 # 爬虫模块
│   │   ├── douyin/                  # 抖音 API 封装
│   │   ├── entity/                  # 数据库实体
│   │   ├── job/                    # 定时任务
│   │   ├── mapper/                 # MyBatis Mapper
│   │   ├── service/                # 业务服务层
│   │   ├── aspect/                 # AOP 切面
│   │   ├── security/               # 安全组件
│   │   └── constant/               # 常量定义
│   └── src/main/resources/
│       ├── application.yml          # 主配置
│       └── db/init-db.sql          # 数据库初始化
│
├── frontend/                        # Vue 3 前端
├── doc/                            # 项目文档
│   ├── README.md                   # 本文档（项目总览）
│   ├── 架构设计.md                  # 技术架构文档
│   ├── 业务流程.md                  # 核心业务流程
│   ├── 开发计划.md                  # 里程碑与进度
│   ├── 执行指南.md                  # AI 角色与操作流程
│   ├── API集成.md                   # 接口集成说明
│   ├── SDK联调.md                   # 抖音 SDK 联调
│   ├── 错误判断.md                  # 错误类型与解决方案
│   ├── requirements/                # 需求主源
│   │   ├── 01-roles-permissions.md
│   │   ├── 02-data-schema.md
│   │   ├── 03-api-specs.md
│   │   ├── 04-product-library.md
│   │   ├── 05-talent-crm.md
│   │   ├── 06-sample-management.md
│   │   ├── 07-data-platform.md
│   │   ├── 08-exclusive-mechanisms.md
│   │   └── 09-development-roadmap.md
│   └── rules/                       # 规则主源（CI 阻断）
│       ├── api-security.md
│       ├── attribution-logic.md
│       ├── crawler-safety.md
│       ├── data-scope-lint.md
│       ├── entity-constraints.md
│       ├── exclusive-triggers.md
│       ├── golden-rules.md
│       └── partition-table.md
│
└── docker-compose.yml               # 开发环境
```

---

## 四、文档体系

本项目采用 **Harness Engineering** 方法论，文档分四层：

| 层级 | 文档 | 作用 | 优先级 |
|------|------|------|--------|
| L1 需求 | `requirements/*.md` | 定义「要做什么」 | **P0** |
| L1 规则 | `rules/*.md` | 定义「绝不能怎么做」 | **P0** |
| L2 计划 | `开发计划.md` | 定义「先后顺序和交付边界」 | P1 |
| L3 执行 | `API集成.md`、`SDK联调.md`、`错误判断.md` | 记录「现在做到哪、还差什么」 | P1 |

### 文档冲突处理

当文档冲突时，按以下顺序裁决：

1. `rules/*.md`（规则最高优先）
2. `requirements/*.md`（需求次之）
3. `开发计划.md`
4. 代码现状（用于确认「已实现什么」）

---

## 五、执行入口

### 开发者入口

1. 阅读 `doc/开发计划.md` 确认当前里程碑
2. 阅读对应 `doc/requirements/*.md` 理解需求
3. 对照 `doc/rules/*.md` 落实现
4. 增加/更新测试并运行 `mvn test`

### AI 智能体入口

1. 阅读 `doc/执行指南.md` 了解角色定位
2. 阅读 `doc/架构设计.md` + `doc/业务流程.md` 理解系统
3. 按「执行指南」中的流程输出架构分析、流程指导、错误判断

---

## 六、常用命令

### 后端

```bash
cd backend
mvn test                              # 运行所有测试
mvn test -Dtest=ClassNameTest        # 运行指定测试类
mvn spring-boot:run                   # 启动应用
```

### 前端

```bash
cd frontend
npm install                           # 安装依赖
npm run dev                           # 开发模式
npm run build                         # 生产构建
```

### 开发环境

```bash
docker-compose up -d                 # 启动所有服务
docker-compose down                   # 停止所有服务
```

---

## 七、当前阶段

- **V0.5**：已完成（骨架与核心流程）
- **V1.0**：M1.5 已完成，M1.6+ 待推进
- **V2.0**：部分前置已完成（独家机制基础服务）

详见 `doc/开发计划.md`

---

## 八、核心风险

| 风险 | 优先级 | 说明 |
|------|--------|------|
| 第三方 SDK 真联调未完成 | P0 | 当前仅本地/Mock验证 |
| 数据看板真实口径待收敛 | P1 | M1.6 待收口 |
| 部署验收未完成 | P2 | M1.7 待完成 |

---

*本文档为项目入口索引，详细内容请查阅对应文档。*
