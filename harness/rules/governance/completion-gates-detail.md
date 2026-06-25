# Completion Gates - 详细定义

> 主文件：[COMPLETION_GATES.md](../COMPLETION_GATES.md)

## Gate 0：Docs Only

适用：只修改 harness / docs / README / 计划 / 报告，不修改 Java / Vue / SQL / Docker / 配置。

必须验证：
- `git diff` 仅包含文档或 harness 文件
- safety-check docs dry-run 通过
- 不得重启容器

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\safety-check.ps1 -Env real-pre -Scope docs -DryRun
git status --short
```

---

## Gate 1：Backend Change

适用：修改 Java / Spring Boot / Mapper / Service / Controller / Security / Scheduler / migration / SQL。

必须验证：
1. 后端编译通过
2. 相关单测通过
3. 容器重新构建后加载新 jar
4. 后端 health = UP
5. 至少一个相关 API smoke 通过
6. 涉及数据库必须有只读 SQL 对账

```powershell
mvn -f backend/pom.xml -DskipTests package
docker compose -f docker-compose.real-pre.yml ps
curl http://localhost:8081/api/system/health
```

---

## Gate 2：Frontend Change

适用：修改 Vue / Vite / Pinia / Naive UI / 路由 / API 调用。

必须验证：
1. 前端构建通过
2. 页面可访问且关键交互可执行
3. 如依赖后端接口，必须验证真实接口返回
4. 浏览器控制台无关键报错

```powershell
cd frontend && npm run build
docker compose -f docker-compose.real-pre.yml ps
```

---

## Gate 3：Domain Change

适用：修改任一领域业务规则。

必须验证：本领域主流程、权限范围、上下游消费者、事件或状态流转、数据库状态与接口返回一致。

### 各领域闭环要求

- **用户域**：登录、菜单、多角色数据范围（all/group/self）
- **商品域**：同步、入库/上架/展示、查询、转链映射
- **达人域**：创建/补全、认领、保护期、标签/地址
- **寄样域**：申请、7天限制、审核、发货、签收、订单触发完成
- **订单域**：同步、落库、pick_source归因、默认渠道/招商、事件发出
- **业绩域**：订单同步后生成、final归属、双轨金额、退款冲正、summary对账
- **配置域**：读取、更新、缓存失效、消费域读取新值
- **分析模块**：事件消费、汇总表更新、Dashboard API、汇总值与明细对账

---

## Gate 4：E2E Business Flow

适用：跑通流程、影响两个以上领域、P0/P1修复、上线前验证。

### 三条主线

1. **渠道链**：认领达人 -> 选品 -> 复制讲解/转链 -> 寄样申请 -> 审核发货 -> 订单同步 -> 寄样完成 -> 业绩生成 -> 看板可见
2. **招商链**：同步活动 -> 商品上架 -> 审核寄样 -> 订单归因默认招商 -> 业绩生成 -> 招商视角可见
3. **管理链**：创建/配置用户 -> 分配角色/部门/数据范围 -> 配置业务规则 -> 各业务域读取规则 -> 权限和数据范围生效

### 完成状态

- 三条主线全部通过：DONE
- 真实订单样本缺失：BLOCKED_BY_SAMPLE
- 有代码 bug：FAILED
- 有外部接口不可用：BLOCKED_BY_EXTERNAL
