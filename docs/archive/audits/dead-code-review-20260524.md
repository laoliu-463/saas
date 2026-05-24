# 死代码扫描交叉审查报告

**日期**：2026-05-24
**分支**：`feature/auth-system`
**输入**：[dead-code-comprehensive.md](./dead-code-comprehensive.md)、[dead-code-api-layer.md](./dead-code-api-layer.md)
**方法**：对扫描清单逐项 grep `frontend/`、`backend/`、`runtime/qa/`、`tests/e2e/`、`docs/`

---

##  executive 结论

| 维度 | 扫描报告 | 审查后 |
|---|---|---|
| 可安全清理 | Phase 1 含 20+ Controller/Service/API | **约 8–12 项 frontend export + 2–3 冗余后端端点** |
| 误标为死代码 | FP 率 75%（总体） | Phase 1 **至少 6 项不可删**（见下表） |
| Service 层清单 | 33 个方法 | **当前源码无匹配，清单失效** |
| View 层 98 个 | 可批量处理 | **禁止批量删**；文件名错误 + 43.7% 模板误报 |

**验收准则**：删代码后必须 `frontend npm run test`、`backend mvn test`、关键 E2E（`replay-attribution`、寄样物流、部门成员）仍绿。

---

## 一、Controller 层：扫描 vs 审查

### 不可删（扫描误判为「无前端调用」）

| 端点 | 证据 | 分类 |
|---|---|---|
| `POST /orders/replay-attribution` | `runtime/qa/real-pre-exception-audit.cjs`、`tests/e2e/22-v1-admin-config-chain.spec.ts`、`docs/09` | **Ops/QA API** |
| `POST /samples/{id}/logistics/sync` | `SampleDetail.vue` → `syncSampleLogistics()` | **已接 UI** |
| `POST /admin/samples/logistics/sync` | `sample.ts` → `syncAllSampleLogistics()` | **Admin API** |
| `POST/DELETE /depts/groups/{groupId}/members` | `DeptList.vue` → `addDeptGroupMembers` / `removeDeptGroupMembers` | **已接 UI** |

### 可废弃或合并（冗余实现）

| 端点 | 说明 | 建议 |
|---|---|---|
| `GET .../copy-brief` | 前端走 `POST .../promotion-links` + `convertActivityProductLink`；E2E `27-product-copy-brief` | **Deprecated** 后删 Controller 方法 |
| `GET /products/filter-options/colonel-partners` | 商品库筛选用 `GET /colonel/partners` + 文本字段 `colonelName` | 删端点或改前端统一 |
| `PUT /roles/{id}/menus` | `RoleList.vue` 无菜单分配 UI；有 `SysRoleControllerTest` | V1 不做菜单绑定则 **保留+文档**；做 UI 则补前端 |

### 无 UI 的管理员口（保留，勿当死代码删）

| 端点 | 说明 |
|---|---|
| `POST /orders/performance-backfill` | 业绩历史回填；`docs/24` 提过权限口径 |
| `POST /orders/commission-batch` | 佣金批量 upsert |
| `POST /orders/commission-recalculate` | 单订单重算 |
| DouyinController 探针系列 | real-pre 联调 / 取证 |
| `GET /api/admin/products/display/audit-logs` | 展示规则审计；无前台页面 |

---

## 二、Service 层：清单失效

扫描列出的 `ProductService.getProductSelectionForTalent`、`RuleCenterService.createRuleFromTemplate`、`DashboardService.getTopPerformers` 等，在 **`backend/src/main/java` 全文无定义**。

**结论**：图谱 build 时节点名与当前分支不一致，或方法已删除。
**动作**：`code-review-graph build` 后重跑 dead_code，**本轮不做 Service 删改**。

---

## 三、前端 API 层：审查确认

### P0 — 建议本轮清理

```text
frontend/src/api/product.ts
  - getProductDetail, bindProductActivity, convertProductLink, followProduct

frontend/src/api/talent.ts
  - getTalentList（别名）

frontend/src/api/ruleCenter.ts
  - updateRuleCenterGroup（页面用 batchUpdateRuleCenter）

frontend/src/stores/permissionHint.ts
  - setGlobalPermissionHint（无引用）
```

### P1 — 确认 V1 范围后处理

| export | 后端能力 | 缺口 |
|---|---|---|
| `deleteSample` | `DELETE /samples/{id}` 有 | 前端无删除按钮 |
| `deleteTalent` | `DELETE /talents/{id}` 有 | 前端无删除入口 |
| `getSampleBoard` | 看板 API 有 | 无看板页引用 |
| `sys.ts` 部分 getById/getList | CRUD 有 | 列表页用 page/tree 替代 |

### 仍在用（扫描未强调）

| export | 使用处 |
|---|---|
| `getProducts` | `ProductLibrary.vue` |
| `convertActivityProductLink` | `ProductLibrary.vue`、`ProductDetail.vue`、`index.vue` |
| `getTalentPage` | `talent/index.vue` |
| `getUserPage` / `getRolePage` | `UserList.vue` / `RoleList.vue` |
| `getDeptTree` | `DeptList.vue` |

---

## 四、View / 组件层：误报样例

| 扫描结论 | 审查 |
|---|---|
| `ProductSelectionCard.vue` 4 个死函数 | **误报**：template 中 `@click` emit `copyBrief`、`quickSample` |
| `DouyinIntegration.vue` OAuth 8 函数 | 有 `DouyinIntegration.oauth.test.ts`；需逐函数看 template |
| `permissionHint` store 整体死 | **误报**：`PermissionHintAlert.vue` + `layout/index.vue` 使用 `globalPermissionHint` |

---

## 五、修正后的 V1 清理计划

### Phase A — 代码清理（本 sprint 可做）

1. 删除 `product.ts` legacy 4 export（保留注释指向 `activityProduct.ts`）
2. 删除 `getTalentList` 别名、`updateRuleCenterGroup`、`setGlobalPermissionHint`
3. `@Deprecated` + JavaDoc：`ProductController.colonelPartnerFilterOptions`、`ColonelActivityProductController.renderCopyBrief`
4. 同步更新 `frontend/src/api/*.test.ts` 中断言

### Phase B — 产品决策（不自动删）

1. 是否在 V1 提供「删除达人/寄样/订单手动同步」UI
2. 是否保留无 UI 的 commission/backfill 管理员 API（建议保留并写入 `docs/05` ops 小节）
3. `assignRoleMenus`：补 UI vs V2

### Phase C — 重扫（下一迭代）

1. `code-review-graph build` + dead_code，验证范围含 `runtime/qa/**`、`tests/e2e/**`
2. View 层用 Vue compiler 或 `vue-tsc` unused，不用纯 regex
3. Service 层重新出清单后再审

---

## 六、验证命令

```bash
# 前端
cd frontend && npm run test && npm run build

# 后端
cd backend && mvn test

# 关键 E2E（删 API 后必跑）
cd .. && npx playwright test tests/e2e/22-v1-admin-config-chain.spec.ts tests/e2e/27-product-copy-brief.spec.ts tests/e2e/30-system-depts-crud.spec.ts --project=chromium
```

---

## 七、文档索引

| 文件 | 用途 |
|---|---|
| [dead-code-comprehensive.md](./dead-code-comprehensive.md) | 扫描原始结果 + 审查标注 |
| [dead-code-api-layer.md](./dead-code-api-layer.md) | 前端 API 明细 + 修正优先级 |
| 本文 | **执行清理的唯一依据** |

---

**审查人**：AI 交叉验证（2026-05-24）
**状态**：Phase A **已实施**（2026-05-24）；Phase B 需产品确认；Phase C 待重扫
