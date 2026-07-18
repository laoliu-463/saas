# 远端寄样提交失败诊断证据

## 元数据

- 时间：2026-07-17（Asia/Shanghai）
- 环境：远端 real-pre
- 本地分支：`codex/ddd-user-role-application`
- 本地 commit：`40929536cd2adb9e23610c5072ef7efd715c3afd`
- 远端分支：`feature/auth-system`
- 远端 commit：`1ed7dd2abef5bcce86221da06ad9db4d21c81446`
- 本地工作区：dirty，未修改既有非本任务变更
- 本次范围：只读日志、数据库权限事实、源码合同核对；未修改代码，未部署

## 现象

远端账号“玄同/壮云”在商品库快速寄样弹窗点击提交失败。

## 远端证据

1. `sys_user`：
   - `username=玄同`
   - `real_name=壮云`
   - `id=1c34b680-30b2-41ec-bdc7-2dde1f37e786`
   - `status=1`、`deleted=0`
2. `sys_user_role` / `sys_role`：当前唯一有效角色为 `biz_staff`，角色名称为“招商专员”，`data_scope=1`。
3. `op_log_2026_07`：2026-07-17 10:36:10、10:36:13、10:36:14、10:36:16、10:37:24，账号“玄同”连续 5 次：
   - `POST /api/products/2165bcb7-d627-37e3-a0fb-bd2fdec76cc4/quick-sample`
   - `response_code=403`
4. 远端 `ApiTimingFilter` 对上述 5 次请求均记录 `status=403`，耗时 4–19ms，说明请求在后端权限层快速拒绝，没有进入商品、达人资格、地址或寄样落库校验。
5. 同期该账号有一次 `PUT /api/talents/.../shipping-address` 返回 200；因此本次失败不是收货地址保存接口失败。
6. 该账号在同期没有 `/api/samples` 创建请求记录；实际使用的是商品库 `/quick-sample` 链路。

## 源码合同核对

远端当前部署源码与本地一致：

- `ProductController.quickSample` 使用 `@RequireRoles({CHANNEL_LEADER, CHANNEL_STAFF})`。
- `ProductQuickSampleService.ensureChannelRole` 只接受 `channel_staff`、`channel_leader` 或 `admin`。
- `SampleActionPermissionPolicy.ensureCanApply` 同样只接受 `admin`、`channel_leader`、`channel_staff`，招商角色会被拒绝。
- 前端寄样申请路由允许 `biz_staff`，但后端创建动作权限没有同步允许招商；这是前后端权限矩阵不一致。
- `docs/领域/寄样域.md` 明确“寄样申请”的使用方为“渠道、招商”，因此当前实现与领域合同不一致。

## 结论

**FAIL：已定位为后端寄样提交权限配置/实现不一致，不是商品、达人、地址或抖音上游异常。**

当前“招商专员”账号可进入寄样相关页面，但提交快速寄样时被商品接口的角色注解直接拦截，返回 403；即使改走 `/api/samples`，现有 `ensureCanApply` 也会因 `biz_staff` 不在允许集合中而拒绝。因此该账号无法提交寄样是确定可复现的权限问题。

## 未执行项

- 未修改权限规则。
- 未构造寄样数据、未写入寄样单。
- 未执行构建、重启、健康检查后的业务回归，因为本次是只读诊断，用户尚未要求修复。
- 未远端部署。

## Retro 结论

本次通过远端操作日志、数据库角色事实、部署版本源码和领域合同交叉核对，确认了请求实际命中的接口及权限拦截点；后续修复必须同时覆盖商品库快速寄样接口、`/api/samples` 创建接口和前端权限展示，并用招商正向、渠道正向及非授权角色负向用例回归。

## 剩余风险

修复前不要通过手工改数据库角色或绕过后端权限验证来验证寄样业务；否则会把权限问题掩盖为数据问题。修复权限后还需另行验证达人认领、商品入库、资格不满足备注、7 天重复申请和寄样状态落库等业务分支。
