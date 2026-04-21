# AGENTS.md — 抖音团长 SaaS V2.2 开发地图

**版本**：V2.2 维护版  
**最后更新**：2026-04-21  
**适用对象**：AI 智能体 / 开发者

---

## 1. 当前执行口径

本项目采用 Harness Engineering：
- 需求主源：`doc/requirements/*.md`
- 规则主源：`doc/rules/*.md`
- 代码事实来源：`backend/src/**` + `frontend/src/**`

文档优先级：
1. `doc/requirements/` + `doc/rules/`
2. `doc/DEVELOPMENT-PLAN.md`
3. 其他 `doc/*.md`（历史/协作补充）

---

## 2. 当前阶段（以代码实况为准）

- 已完成：V0.5（M0.1~M0.8）
- 已完成：V1.0 到 M1.5（SDK 封装、订单同步、爬虫、寄样真实数据接入、寄样自动闭环）
- 进行中：M1.3 真数据联调验收（第三方 SDK 真实环境）
- 待完成：M1.6 数据看板真实化、M1.7 部署验证、V2.0 高级能力

关键说明：
- 当前 `mvn test` 全绿（本地/Mock链路）
- 第三方 SDK 真实环境联调尚未完成（Token/真实接口返回/限流分支待验证）

---

## 3. 目录导航

```
SAAS/
├── doc/requirements/          # 需求主源
├── doc/rules/                 # 规则主源（CI 阻断）
├── backend/               # Spring Boot 后端
├── frontend/              # Vue3 前端
├── doc/                   # 执行计划与协作文档
└── docker-compose.yml
```

---

## 4. 任务入口

### 开发新功能
1. 先读 `doc/DEVELOPMENT-PLAN.md` 确认里程碑
2. 再读对应 `doc/requirements/*.md`
3. 对照 `doc/rules/*.md` 落实现
4. 增加/更新测试并跑 `mvn test`

### 修复 Bug
1. 定位模块
2. 查对应规则文件
3. 修复 + 回归测试
4. 更新 `doc/DAILY-PROGRESS.md`

### 文档维护
1. 改实现后必须同步 `doc/DEVELOPMENT-PLAN.md`
2. 涉及 SDK 时同步 `doc/DOUYIN_SDK_INTEGRATION.md`
3. 重大里程碑完成后更新 `doc/README.md`

---

## 5. 当前重点风险

1. 第三方 SDK 真实联调未完成（高优先级）
2. 数据看板真实数据口径待最终收敛
3. V2.0 独家机制虽已落地服务层，需补真实业务验收

---

## 6. 强制规则速查

1. 分区表查询必须带时间条件：`doc/rules/partition-table.md`
2. 归因必须走映射/优先级链路：`doc/rules/attribution-logic.md`
3. 敏感数据不得持久化：`doc/rules/api-security.md`
4. DataScope 不可绕过：`doc/rules/data-scope-lint.md`
5. 提成比例必须走配置：`doc/rules/exclusive-triggers.md`

---

## 7. 常用命令

```bash
cd backend
mvn test

cd frontend
npm run dev
npm run build
```

---

本文件用于“按当前代码推进任务”，不是历史需求归档。
