# 抖音 SDK 接入与团队协作指南

**文档版本**：V1.0
**制定日期**：2026-04-20
**来源**：团队协作流程确认
**状态**：已定稿

---

## 一、本地开发环境搭建

| 步骤 | 操作内容 | 说明 |
| :--- | :--- | :--- |
| 1 | 克隆项目仓库 | `git clone <仓库地址>` |
| 2 | 启动 Docker 服务 | `docker-compose up -d` 启动 PostgreSQL、Redis 等依赖服务 |
| 3 | 配置环境变量 | 复制 `.env.example` 为 `.env`，填入抖音 `APP_KEY`、`APP_SECRET` 及数据库、Redis 连接信息。**`.env` 不能提交到 Git！** |
| 4 | 安装后端依赖 | Java/Maven 环境下 `./mvnw dependency:resolve` |
| 5 | 安装前端依赖 | `npm install` |

---

## 二、封装抖音 API 服务层

| 步骤 | 操作内容 | 说明 |
| :--- | :--- | :--- |
| 1 | 创建 `core/douyin_client.py`（或 Java 对应封装类） | 封装 SDK 初始化、Token 管理和接口调用 |
| 2 | 实现 Token 自动管理 | 启动时检查 Redis 中是否有有效 Token，若无则调用 `token.create` 获取；后台任务定期调用 `token.refresh` 刷新 |
| 3 | 封装业务接口方法 | 例如 `get_product_detail(product_id)` 方法，内部调用 `product.detail` 接口，处理异常和日志 |
| 4 | 编写单元测试 | 验证封装的接口是否能成功返回数据 |

---

## 三、前后端协作开发流程

| 阶段 | 前端任务 | 后端任务 |
| :--- | :--- | :--- |
| **V0.5 前期** | 使用 **Mock 数据**搭建页面框架（商品库、达人CRM、寄样台等），实现 UI 交互和路由 | 完善抖音 API 封装，开发业务接口（如商品列表、活动查询），提供 Mock 接口文档 |
| **V0.5 后期** | 逐步替换 Mock 接口为真实后端 API，进行联调 | 开发登录、权限校验接口，实现数据库操作逻辑 |
| **V1.0** | 完善交互细节，处理异常状态 | 接入订单同步定时任务、爬虫任务，进行性能优化 |

---

## 四、Git 分支协作策略

### 4.1 分支规划

| 分支类型 | 命名示例 | 作用 |
| :--- | :--- | :--- |
| **主分支** | `main` | 生产环境代码，只接受来自 `develop` 或 `hotfix` 的合并。严格保护，不允许直接推送 |
| **开发分支** | `develop` | 日常开发集成分支，所有功能分支都合并到这里 |
| **功能分支** | `feature/product-api` | 开发新功能，从 `develop` 拉出，完成后合并回 `develop` |
| **修复分支** | `bugfix/token-refresh-fix` | 修复非紧急 Bug，从 `develop` 拉出，合并回 `develop` |
| **紧急修复** | `hotfix/fix-login-bug` | 修复生产环境紧急问题，从 `main` 拉出，同时合并到 `main` 和 `develop` |

### 4.2 日常工作流

```bash
# 1. 拉取最新代码，切换到 develop 分支
git checkout develop && git pull origin develop

# 2. 创建功能分支
git checkout -b feature/product-api

# 3. 开发并分阶段提交
git add .
git commit -m "feat: 封装商品详情查询接口"

# 4. 推送功能分支
git push origin feature/product-api

# 5. 创建 Pull Request，指定同事 Review 代码

# 6. Review 通过后，合并到 develop 分支，删除功能分支
```

### 4.3 提交信息规范（Conventional Commits）

| 类型 | 说明 | 示例 |
| :--- | :--- | :--- |
| `feat` | 新功能 | `feat: 增加达人认领保护期自动释放定时任务` |
| `fix` | Bug 修复 | `fix: 修复订单金额精度丢失问题` |
| `docs` | 文档更新 | `docs: 更新 API 对接文档` |
| `refactor` | 代码重构 | `refactor: 优化 Token 刷新逻辑` |
| `test` | 测试相关 | `test: 补充订单同步单元测试` |
| `chore` | 构建/工具变动 | `chore: 升级 doudian-sdk 版本` |

---

## 五、协作中的关键注意事项

| 注意点 | 说明 |
| :--- | :--- |
| **密钥安全** | `.env` 文件务必加入 `.gitignore`，任何密钥、Token 严禁提交到仓库。使用 `.env.example` 作为模板文件提交 |
| **数据库迁移** | 使用 Flyway 或 Liquibase 管理数据库版本，每次模型变更生成迁移文件并提交。其他成员 `git pull` 后执行迁移脚本即可同步 |
| **频繁沟通** | 开发新接口或修改公共模块前，先在团队群同步，避免冲突 |
| **Code Review** | 所有合并到 `develop` 或 `main` 的代码，必须经过至少一人 Review |
| **Mock 优先** | V0.5 阶段前后端均以 Mock 数据驱动开发，真实 API 接入放在 V1.0 阶段 |

---

## 六、当前项目分支状态

| 分支 | 状态 | 说明 |
| :--- | :--- | :--- |
| `main` | 保护分支 | 生产环境代码 |
| `master` | 当前工作分支 | 与 main 同步 |
| `develop` | 待创建 | 从 master 创建，作为开发集成分支 |

**建议下一步操作**：
```bash
# 基于 master 创建 develop 分支
git checkout -b develop
git push -u origin develop
```
