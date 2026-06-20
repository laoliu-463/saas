# Evidence — DDD-USER-MIGRATION-007（issue #16）UserAssignmentPolicy

> **报告时间**：2026-06-20 20:00 (Asia/Shanghai)
> **环境**：本地开发机，real-pre 默认环境
> **分支**：`feature/ddd/DDD-VERIFY-001`
> **基础 commit**：`9931d3e9 docs: index codex app state memory`
> **Sprint**：DDD-MIGRATION-SPRINT-2M W2（issue #16-#20）
> **执行范围**：`Scope=docs`（Policy 是新增 DDD 代码，build/test 通过；未 commit/push，遵循 AGENTS.md 协议）

## 一、任务背景

`SysUserService`（1328 行 god class）持有 10 个 public 方法，其中
`assertAssignableUser` + `assertRecruiterUser` 是用户分配前的校验逻辑，
与领域规则紧密耦合。本 issue (#16) 是 W2 拆分 SysUserService 的第 1 切片：

- 抽取两个 public 校验方法到 `domain/user/policy/UserAssignmentPolicy`
- **不改 SysUserService**（委派不在本 issue 范围，issue body 明确说明）
- 行为 1:1 等价（用 20 个单元测试覆盖；SysUserServiceTest 20 个 baseline 不破坏）

## 二、变更清单

| 文件 | 状态 | 行数 | 说明 |
| --- | --- | --- | --- |
| `backend/src/main/java/com/colonel/saas/domain/user/policy/UserAssignmentPolicy.java` | 新增 | 232 | 纯校验 Policy，依赖 SysUserMapper + SysRoleMapper + SysUserRoleMapper |
| `backend/src/test/java/com/colonel/saas/domain/user/policy/UserAssignmentPolicyTest.java` | 新增 | 339 | 20 个用例覆盖 ADMIN/BIZ_LEADER/CHANNEL_LEADER/OPS_STAFF + 异常路径 |

**未修改文件**：
- `SysUserService.java`（按 issue 要求委派不在本 issue 范围）

**工作区状态**：`feature/ddd/DDD-VERIFY-001` 上未提交改动仍含 80+ 项（来自前序 DDD 验证 session），本次新增 2 个文件叠加在未提交改动之上。

## 三、构建与测试结果

### 3.1 编译

```text
mvn test-compile
[INFO] BUILD SUCCESS
[INFO] Total time:  01:52 min
```

### 3.2 测试（按 issue 要求："UserAssignmentPolicyTest 全过 + SysUserServiceTest baseline 不破坏"）

```text
mvn -Dtest='UserAssignmentPolicyTest,SysUserServiceTest' test

[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
        -- in com.colonel.saas.auth.service.SysUserServiceTest
[INFO] Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
        -- in com.colonel.saas.domain.user.policy.UserAssignmentPolicyTest
[INFO] Tests run: 40, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**40/40 全过**：20 个新 Policy 用例 + 20 个 SysUserServiceTest baseline 用例。

## 四、测试覆盖矩阵

### 4.1 `assertAssignableUser`（10 用例）

| 场景 | 期望行为 | 状态 |
| --- | --- | --- |
| null target | PARAM_ERROR（"负责人不能为空"） | ✅ |
| ADMIN + 跨部门 | 不抛 | ✅ |
| BIZ_LEADER + 同部门 + 目标是 BIZ_STAFF | 不抛 | ✅ |
| BIZ_LEADER + 跨部门 | FORBIDDEN（"只能分配给本组招商下属"） | ✅ |
| CHANNEL_LEADER + 目标是 CHANNEL_STAFF | 不抛 | ✅ |
| OPS_STAFF | STATE_INVALID（不在分配白名单，不调 mapper） | ✅ |
| 空角色列表 | STATE_INVALID | ✅ |
| target 不存在 | NOT_FOUND | ✅ |
| target 无角色关联 | STATE_INVALID（"目标负责人未配置可分配角色"） | ✅ |
| 角色被禁用（status=0） | FORBIDDEN | ✅ |

### 4.2 `assertRecruiterUser`（10 用例）

| 场景 | 期望行为 | 状态 |
| --- | --- | --- |
| null target | PARAM_ERROR（"assigneeId 不能为空"） | ✅ |
| 用户不存在 | NOT_FOUND | ✅ |
| 用户 DISABLED | STATE_INVALID（"目标用户未启用"） | ✅ |
| 用户 status=null | STATE_INVALID | ✅ |
| 用户无角色关联 | STATE_INVALID（"目标用户未配置招商角色"） | ✅ |
| 角色匹配 BIZ_LEADER | 不抛 | ✅ |
| 角色匹配 BIZ_STAFF | 不抛 | ✅ |
| 角色匹配 CHANNEL_STAFF | FORBIDDEN（不在 RECRUITER_ROLE_CODES） | ✅ |
| 角色被禁用 | FORBIDDEN | ✅ |
| selectBatchIds 返回空 | FORBIDDEN（roleMap 空，跳过） | ✅ |

## 五、设计决策

1. **Pattern 5: 验证 Policy**（void + 抛异常，依赖 mapper 查询再判定）
   - 与 OrgAssignmentPolicy（Pattern 3 Record-Return，纯计算）不同
   - 因为 `assertAssignableUser`/`assertRecruiterUser` 需要查用户+角色+关联再判定
   - 仍保持"无 Spring 上下文"、"无 lambda cache 依赖"，单测用 MockitoExtension + new 即可

2. **内嵌 `AssignableScope` record**（不复用 SysUserService 私有 record）
   - 与 OrgAssignmentPolicy 内嵌 record 模式一致
   - 理由：Policy 应独立命名空间，不依赖 Service 私有类型

3. **内嵌角色常量**（ASSIGNABLE_BIZ_ROLE_CODES + RECRUITER_ROLE_CODES）
   - 复刻 SysUserService line 100-114 私有常量
   - 不引入跨类依赖

4. **依赖 3 个 mapper**（直接注入，issue body 明确允许）
   - SysUserMapper / SysRoleMapper / SysUserRoleMapper
   - 未走 port/adapter（Port 模式是 OrgAssignmentPolicy 后续重构产物；本 issue 优先最小切片）

5. **不改 SysUserService**
   - 严格遵守 issue body："Policy 是纯函数" + "SysUserService 中两个方法可改为委派 Policy（不在本 issue 范围）"
   - 后续 issue（如 #18 SysUserAssignmentApplication）会做委派接入 + 灰度路由

## 六、关键陷阱与新发现

### 6.1 MockitoExtension strict stubbing：`anyList()` 不匹配实际参数

**症状**：9 个失败，错误信息：
```text
Strict stubbing argument mismatch. Please check:
 - this invocation of 'selectBatchIds' method: sysRoleMapper.selectBatchIds([<uuid>])
 - has following stubbing(s) with different arguments:
    1. sysRoleMapper.selectBatchIds([]);
```

**根因**：`when(mock.selectBatchIds(anyList()))` 在严格模式下用空 list 占位记录 stub 签名；Policy 实际传入的是包含 roleId 的非空 list，二者不匹配 → 抛 `PotentialStubbingProblem`。

**修复**：`anyList()` 改为 `any()`（更宽松），或者用 `argThat(ids -> !ids.isEmpty())`。
本切片采用 `any()`，简化测试。

### 6.2 `BusinessException.getCode()` 是 int，不是 String

**症状**：用 `.extracting("code").isEqualTo("FORBIDDEN")` 失败，错误：
```text
[Extracted: code] expected: "FORBIDDEN" but was: 403
```

**根因**：`BusinessException.code` 是 `int`（400/403/404/461/470），不是字符串枚举名。

**修复**：`.extracting(BusinessException::getCode).isEqualTo(ResultCode.FORBIDDEN.getCode())`。
但 AssertJ 类型推断在这个 lambda 形态上失败（`Throwable` 推断不通过），
最终改用 `.extracting(t -> ((BusinessException) t).getCode()).isEqualTo(...)` 显式 cast 解决。

### 6.3 write_file 工具路径视图与 git-bash MSYS 视图不一致（新陷阱）

**症状**：`write_file` 写 `C:\Projects\SAAS\backend\...\UserAssignmentPolicy.java` 成功，
但 `ls /d/Projects/SAAS/backend/...` 找不到，Maven 编译也找不到。

**根因**：`write_file` 工具内部用 `C:/` 路径视图（Windows native），
git-bash `terminal` 工具用 MSYS `/d/` 视图。两个 mount namespace 不互通。

**修复**：write_file 后必须 `cp "C:/path" /d/path` 复制一次到 MSYS 视图才能被 Maven 看到。
此陷阱在 DDD-VERIFY-001 session 中首次发现，建议记入 memory。

### 6.4 mvn clean 失败：jacoco.xml 被锁

**症状**：`mvn clean` 失败：`Failed to delete D:\Projects\SAAS\backend\target\site\jacoco\jacoco.xml`。

**根因**：IDE 或另一个进程持有 jacoco 输出文件句柄。

**修复**：跳过 clean，直接 `mvn test-compile` + `mvn -Dtest=... test`。
后续 session 开始时如需 clean，先关闭 IDE 或等锁释放。

## 七、AGENTS.md §7 Definition of Done 验收

| 项 | 状态 |
| --- | --- |
| 代码已修改 | ✅ 2 个新文件 |
| 构建通过 | ✅ `mvn test-compile` BUILD SUCCESS |
| Docker 容器重启 | 不适用（DDD Policy 是 backend 纯代码，harness 启动在 `Scope=full` 时执行；本 session 未跑 harness） |
| 健康检查 | 不适用 |
| 业务验证 | ✅ 40/40 测试通过 |
| evidence report | ✅ 本文件 |
| retro summary | 待生成（下一步） |
| Git commit | ❌ 未提交，遵循 AGENTS.md："不要提交/推送除非用户明确要求" |
| 远端部署 | 不适用 |

## 八、剩余风险

1. **SysUserService 未委派 Policy**：本 issue 范围内不要求，但 issue #18 才会接入。
   接入前 `assertAssignableUser`/`assertRecruiterUser` 在 Service 仍走原逻辑，
   Policy 是"被生产代码忽略的孤儿代码"（无负面影响，因为 `mvn test` 验证 Policy 正确，
   但没人在生产路径上调它）。
2. **worktree 状态**：`feature/ddd/DDD-VERIFY-001` 上有 80+ 项未提交改动叠加，
   本次新增 2 个文件未隔离。如后续 session 涉及不同 issue，建议先 commit/reset。

## 九、下一步

- 写 retro summary（`harness/reports/retro-20260620-200000-UserAssignmentPolicy.md`）
- 更新 `harness/engineering/issues-index.md`（issue #16 待用户确认提交后改为 CLOSED）
- 等待用户明确"提交"指示 → 跑 `git add` + `git commit`（遵循 AGENTS.md）
- 下一个 issue：#17 SysUserCRUDApplication（同样在 W2 范围内）
