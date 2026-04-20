# 需求：用户角色与权限体系

**文档版本**：V1.0
**状态**：已定稿
**智能体入口**：直接读取此文件

---

## 一、岗位定义（6大角色）

| 角色 | 核心职责 | 数据范围 |
|------|----------|----------|
| **管理员** | 系统配置、人员管理、权限分配 | 全部数据 |
| **招商组长** | 绑定活动、分配商品、管理招商团队 | 本组招商 + 自己的数据 |
| **招商** | 审核商品、补充信息、审核寄样 | 自己负责的商品/活动/寄样 |
| **渠道组长** | 管理渠道团队、数据汇总 | 本组渠道 + 自己的数据 |
| **渠道** | 达人对接、申请寄样、跟进产出 | 自己的达人/寄样/业绩 |
| **运营** | 物流录入、辅助操作 | 按需配置 |

---

## 二、权限配置矩阵

### 2.1 数据范围控制（DataScope）

```
1 = 本人数据（user_id = current_user）
2 = 本组数据（dept_id = current_user's dept）
3 = 全部数据（不限）
```

### 2.2 角色-权限映射

| 角色 | 数据范围 | 可配置操作 |
|------|----------|------------|
| 管理员 | 3（全部） | 所有 CRUD + 权限管理 |
| 招商组长 | 2（本组） | 活动绑定、商品分配、查看组内业绩 |
| 招商 | 1（本人） | 商品审核、寄样审核、查看个人业绩 |
| 渠道组长 | 2（本组） | 查看组内业绩、管理组员 |
| 渠道 | 1（本人） | 达人认领、申请寄样、查看个人数据 |
| 运营 | 自定义 | 物流录入、发货操作 |

---

## 三、可配置规则（SystemConfig 表）

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| `sample_limit_days` | 7 | 同一渠道+同一达人+同一商品的重复申请间隔（天） |
| `sample_limit_enabled` | true | 是否启用寄样限制 |
| `exclusive_talent_threshold` | 70 | 独家达人服务费占比阈值（%） |
| `exclusive_talent_samples` | 10 | 独家达人月寄样数量阈值 |
| `exclusive_merchant_threshold` | 70 | 独家商家服务费占比阈值（%） |
| `talent_protection_days` | 30 | 认领后无产出自动释放时间（天） |

---

## 四、开发约束

### 4.1 必须遵守

- [ ] 所有涉及数据范围查询的 Service 方法，必须传入 `userId` 和 `deptId` 参数
- [ ] 查询方法命名规范：`listByDataScope(DataScope scope, ...)`, `countByDataScope(...)`
- [ ] 权限校验在 Controller 层统一处理，不在 Service 层散落

### 4.2 禁止做法

- [ ] 禁止在 Controller 层直接返回完整数据集（不经过数据范围过滤）
- [ ] 禁止硬编码用户 ID 或部门 ID

---

## 五、验收标准

1. **单元测试**：验证 6 个角色的数据范围过滤逻辑正确
2. **集成测试**：模拟管理员、组长、组员登录，验证各自只能看到被授权的数据
3. **配置生效**：修改 SystemConfig 后，权限规则立即生效（无需重启）

---

## 六、相关文件索引

| 文件 | 路径 | 说明 |
|------|------|------|
| 实体定义 | `backend/src/main/java/com/colonel/saas/entity/system/SysRole.java` | 角色实体，含 permissions JSONB |
| 实体定义 | `backend/src/main/java/com/colonel/saas/entity/system/SysUser.java` | 用户实体，含 deptId |
| 配置实体 | `backend/src/main/java/com/colonel/saas/entity/config/SystemConfig.java` | 系统配置实体 |
| 权限服务 | `backend/src/main/java/com/colonel/saas/service/DataScopeService.java` | 数据范围服务（待创建） |
| 验证规则 | `rules/data-scope-lint.md` | Lint 规则：禁止绕过数据范围 |
