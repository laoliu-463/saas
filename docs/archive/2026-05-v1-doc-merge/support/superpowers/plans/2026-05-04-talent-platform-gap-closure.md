> 本文档已归档，仅作为历史参考；当前口径以 docs/ 下主文档为准。

# 达人经营平台补全（第一阶段） Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不破坏当前 `local-mock` 主链路的前提下，把现有 `/talent` 从基础 CRM 补全为“可演示的达人经营工作台第一阶段”，至少覆盖团队公海、我的达人、自然出单达人、黑名单、经营筛选、经营字段展示与认领状态管理。

**Architecture:** 保持现有前后端单体分层不变，前端继续使用一个达人主路由承载多视图，通过 query / tab 状态切换不同池视图；后端在 `/talents` 体系内补充分页查询 DTO、服务过滤能力和黑名单动作接口，避免引入第二套达人域模型。数据仍以 `talent`、`talent_claim`、订单聚合、爬虫信息表为核心，按“先查询整合、后补规则动作”的顺序增量推进。

**Tech Stack:** Vue 3、Vite、Naive UI、Pinia、Spring Boot、MyBatis-Plus、PostgreSQL、Redis、JUnit 5、MockMvc、本地可见浏览器回归。

---

## Scope And Split Decision

本次计划只覆盖“达人经营平台第一阶段”，不把你贴出的整套外延模块一次性塞进同一轮：

- **纳入本计划**
  - 团队公海
  - 我的达人
  - 自然出单达人
  - 达人黑名单
  - 经营筛选
  - 经营字段列表
  - 认领 / 释放 / 拉黑 / 解除拉黑
  - 保护期与认领状态展示

- **拆到后续分案**
  - 客户端找达人
  - 团长管理
  - 商家管理
  - 合作管理
  - 推广效果
  - 公众号 / 客户端 / 旗舰版等产品壳层导航

原因：这些能力已经跨出当前 `达人 CRM` 单模块边界，若一并推进，会同时牵动路由结构、权限矩阵、数据模型和多个业务域，不符合当前项目“先补核心闭环，再扩外围经营台”的节奏。

## File Map

### Frontend

- Modify: `frontend/src/router/index.ts`
  - 为达人经营台增加细分子视图入口或 query 约定，保证 `/talent` 下可稳定切换团队公海、我的达人、自然出单、黑名单。
- Modify: `frontend/src/views/layout/Header.vue`
  - 如有必要，补齐达人经营台一级导航文案，不新增产品壳层菜单。
- Modify: `frontend/src/views/layout/Sider.vue`
  - 为达人模块补充子菜单项，避免所有视图挤在一个无结构列表页。
- Modify: `frontend/src/api/talent.ts`
  - 扩展分页查询参数、列表返回字段、黑名单动作接口。
- Modify: `frontend/src/views/talent/index.vue`
  - 从基础 CRM 列表改造成多视图达人经营台；补筛选器、统计摘要、认领状态与经营字段。
- Create: `frontend/src/views/talent/constants.ts`
  - 管理达人视图、筛选枚举、类目选项和状态映射，避免 `index.vue` 持续膨胀。
- Create: `frontend/src/views/talent/composables/useTalentFilters.ts`
  - 封装筛选表单状态、query 同步、参数构造逻辑。
- Create: `frontend/src/views/talent/components/TalentBoardTabs.vue`
  - 承载“团队公海 / 我的达人 / 自然出单达人 / 黑名单”视图切换。
- Create: `frontend/src/views/talent/components/TalentMetricFilters.vue`
  - 经营筛选面板，承载主推类目、带货数据、达人数据、达人信息四组筛选。
- Create: `frontend/src/views/talent/components/TalentStatusActions.vue`
  - 认领 / 释放 / 拉黑 / 解除拉黑动作按钮，减少 `index.vue` 表格内联复杂度。
- Modify: `frontend/src/views/talent/components/TalentDetailModal.vue`
  - 补展示经营字段、认领状态、黑名单状态、自然出单标签等。

### Backend

- Create: `backend/src/main/java/com/colonel/saas/dto/talent/TalentPageQuery.java`
  - 统一达人分页查询参数，替代当前 Controller 直接散落的 request param。
- Create: `backend/src/main/java/com/colonel/saas/dto/talent/TalentOperateRequest.java`
  - 封装拉黑 / 解除拉黑等动作请求体。
- Modify: `backend/src/main/java/com/colonel/saas/controller/TalentController.java`
  - 接入新的分页查询 DTO，增加黑名单动作接口和视图过滤参数。
- Modify: `backend/src/main/java/com/colonel/saas/service/TalentQueryService.java`
  - 扩展多视图过滤、经营指标过滤、认领状态过滤、类目过滤、自然出单达人过滤。
- Modify: `backend/src/main/java/com/colonel/saas/service/TalentService.java`
  - 增加拉黑 / 解除拉黑 / 自然出单标签计算 / 保护期判定复用逻辑。
- Modify: `backend/src/main/java/com/colonel/saas/entity/Talent.java`
  - 补充黑名单、达人等级、性别、主营类目、直播/视频经营区间等展示字段或临时聚合字段。
- Modify: `backend/src/main/resources/db/init-db.sql`
  - 补齐达人黑名单与经营字段初始化结构、索引、测试数据。
- Create: `backend/src/main/java/com/colonel/saas/mapper/TalentStatsQueryMapper.java`
  - 若现有 Mapper 难以承载复杂经营筛选，则抽出专用查询 Mapper。
- Create: `backend/src/main/resources/mapper/TalentStatsQueryMapper.xml`
  - 承载经营筛选 SQL，避免把长 SQL 塞进 Service。

### Tests

- Modify: `backend/src/test/java/com/colonel/saas/controller/TalentControllerTest.java`
  - 覆盖分页新参数、黑名单动作接口、视图切换接口行为。
- Modify: `backend/src/test/java/com/colonel/saas/service/TalentServiceTest.java`
  - 覆盖拉黑 / 解除拉黑、保护期、重复认领边界。
- Modify: `backend/src/test/java/com/colonel/saas/service/TalentQueryServiceTest.java`
  - 覆盖团队公海、我的达人、自然出单达人、黑名单、经营筛选组合。
- Create: `runtime/qa/talent-platform-smoke.cjs`
  - 新增达人经营台第一阶段浏览器回归脚本。

### Docs

- Modify: `docs/04-开发进度.md`
  - 更新达人经营平台第一阶段完成情况。
- Modify: `docs/10-V2.2场景覆盖矩阵.md`
  - 把达人 CRM 相关场景从“基础已覆盖”拆分为更细状态。
- Create: `docs/archive/records/24-达人经营平台补全一期记录.md`
  - 记录本次范围、取舍和验证证据。

## Task 1: 锁定一期范围与业务口径

**Files:**
- Modify: `docs/10-V2.2场景覆盖矩阵.md`
- Create: `docs/archive/records/24-达人经营平台补全一期记录.md`

- [ ] **Step 1: 把一期范围写入专项记录**

```md
## 一期范围

- 团队公海
- 我的达人
- 自然出单达人
- 达人黑名单
- 经营筛选（类目 / 粉丝 / 地区 / 认领状态 / 直播 / 视频）
- 列表经营字段展示

## 二期暂缓

- 客户端找达人
- 团长管理
- 商家管理
- 合作管理
- 推广效果
```

- [ ] **Step 2: 在覆盖矩阵中补达人经营台说明**

```md
补充说明：

- 当前 `/talent` 已完成基础 CRM 闭环
- 达人经营平台一期将补充团队公海 / 我的达人 / 自然出单达人 / 黑名单与经营筛选
- 客户端找达人、合作管理、推广效果等仍不纳入本轮
```

- [ ] **Step 3: 自检文档口径**

Run: `Get-ChildItem docs -Recurse | Select-String -Pattern '自然出单达人|达人黑名单|客户端找达人'`

Expected: 新增一期记录命中；主干文档仅出现本轮允许的描述，不把二期内容误写成已完成。

- [ ] **Step 4: Commit**

```bash
git add docs/10-V2.2场景覆盖矩阵.md docs/archive/records/24-达人经营平台补全一期记录.md
git commit -m "docs: define phase-one talent platform scope"
```

## Task 2: 为达人列表查询引入统一 Query DTO

**Files:**
- Create: `backend/src/main/java/com/colonel/saas/dto/talent/TalentPageQuery.java`
- Modify: `backend/src/main/java/com/colonel/saas/controller/TalentController.java`
- Test: `backend/src/test/java/com/colonel/saas/controller/TalentControllerTest.java`

- [ ] **Step 1: 写 Controller 失败测试，覆盖新查询参数透传**

```java
@Test
void page_shouldAcceptViewAndMetricFilters() throws Exception {
    when(talentQueryService.page(any())).thenReturn(new Page<>(1, 10, 0));

    mockMvc.perform(get("/talents")
                    .param("view", "TEAM_PUBLIC")
                    .param("category", "食品饮料")
                    .param("claimStatus", "UNCLAIMED")
                    .param("minFans", "10000")
                    .requestAttr("userId", UUID.randomUUID()))
            .andExpect(status().isOk());

    verify(talentQueryService).page(argThat(query ->
            "TEAM_PUBLIC".equals(query.getView())
                    && "食品饮料".equals(query.getCategory())
                    && "UNCLAIMED".equals(query.getClaimStatus())
                    && Long.valueOf(10000).equals(query.getMinFans())));
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend; mvn -Dtest=TalentControllerTest#page_shouldAcceptViewAndMetricFilters test`

Expected: FAIL，原因是 `TalentPageQuery` 与新的 `page(query)` 签名尚不存在。

- [ ] **Step 3: 定义最小 Query DTO 与 Controller 改造**

```java
public class TalentPageQuery {
    private long page = 1;
    private long size = 10;
    private String keyword;
    private String poolStatus;
    private String view;
    private String category;
    private String claimStatus;
    private Long minFans;
    private Long maxFans;
    private String region;
}
```

```java
@GetMapping
public ApiResult<PageResult<Talent>> page(
        @Valid TalentPageQuery query,
        @RequestAttribute("userId") UUID userId,
        @RequestAttribute(value = "deptId", required = false) UUID deptId,
        @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
    query.setUserId(userId);
    query.setDeptId(deptId);
    query.setDataScope(dataScope);
    IPage<Talent> result = talentQueryService.page(query);
    return okPage(result);
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd backend; mvn -Dtest=TalentControllerTest#page_shouldAcceptViewAndMetricFilters test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/colonel/saas/dto/talent/TalentPageQuery.java backend/src/main/java/com/colonel/saas/controller/TalentController.java backend/src/test/java/com/colonel/saas/controller/TalentControllerTest.java
git commit -m "refactor: add talent page query dto"
```

## Task 3: 补齐团队公海 / 我的达人 / 自然出单达人 / 黑名单四个视图

**Files:**
- Modify: `backend/src/main/java/com/colonel/saas/service/TalentQueryService.java`
- Modify: `backend/src/main/java/com/colonel/saas/entity/Talent.java`
- Test: `backend/src/test/java/com/colonel/saas/service/TalentQueryServiceTest.java`

- [ ] **Step 1: 写失败测试，覆盖四个视图过滤**

```java
@Test
void page_shouldFilterByView() {
    Talent publicTalent = new Talent();
    publicTalent.setId(UUID.randomUUID());
    publicTalent.setPoolStatus("PUBLIC");

    Talent privateTalent = new Talent();
    privateTalent.setId(UUID.randomUUID());
    privateTalent.setPoolStatus("PRIVATE");

    TalentPageQuery query = new TalentPageQuery();
    query.setView("TEAM_PUBLIC");

    when(talentService.pageBaseTalents(query)).thenReturn(List.of(publicTalent, privateTalent));

    IPage<Talent> page = talentQueryService.page(query);

    assertThat(page.getRecords()).allMatch(item -> "PUBLIC".equals(item.getPoolStatus()));
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend; mvn -Dtest=TalentQueryServiceTest#page_shouldFilterByView test`

Expected: FAIL，当前查询尚不支持 `view`。

- [ ] **Step 3: 在 QueryService 中加入视图判定**

```java
private boolean matchesView(Talent talent, TalentPageQuery query) {
    String view = query.getView();
    if (!StringUtils.hasText(view)) return true;
    return switch (view) {
        case "TEAM_PUBLIC" -> "PUBLIC".equals(talent.getPoolStatus());
        case "MY_TALENTS" -> "PRIVATE".equals(talent.getPoolStatus()) && Objects.equals(talent.getOwnerId(), query.getUserId());
        case "NATURAL_ORDERS" -> Boolean.TRUE.equals(talent.getNaturalOrderTalent());
        case "BLACKLIST" -> Boolean.TRUE.equals(talent.getBlacklisted());
        default -> true;
    };
}
```

- [ ] **Step 4: 为 Talent 增加临时视图字段**

```java
private Boolean blacklisted;
private Boolean naturalOrderTalent;
private String blacklistReason;
```

- [ ] **Step 5: 运行测试确认通过**

Run: `cd backend; mvn -Dtest=TalentQueryServiceTest#page_shouldFilterByView test`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/colonel/saas/service/TalentQueryService.java backend/src/main/java/com/colonel/saas/entity/Talent.java backend/src/test/java/com/colonel/saas/service/TalentQueryServiceTest.java
git commit -m "feat: add talent board views"
```

## Task 4: 补经营筛选能力

**Files:**
- Modify: `backend/src/main/java/com/colonel/saas/dto/talent/TalentPageQuery.java`
- Modify: `backend/src/main/java/com/colonel/saas/service/TalentQueryService.java`
- Optional Create: `backend/src/main/java/com/colonel/saas/mapper/TalentStatsQueryMapper.java`
- Optional Create: `backend/src/main/resources/mapper/TalentStatsQueryMapper.xml`
- Test: `backend/src/test/java/com/colonel/saas/service/TalentQueryServiceTest.java`

- [ ] **Step 1: 写失败测试，覆盖类目 / 地区 / 认领状态 / 粉丝区间过滤**

```java
@Test
void page_shouldApplyBusinessFilters() {
    Talent talent = new Talent();
    talent.setCategory("食品饮料");
    talent.setIpLocation("广东广州");
    talent.setFans(120000L);
    talent.setPoolStatus("PUBLIC");

    TalentPageQuery query = new TalentPageQuery();
    query.setCategory("食品饮料");
    query.setRegion("广东");
    query.setClaimStatus("UNCLAIMED");
    query.setMinFans(100000L);

    IPage<Talent> page = talentQueryService.pageFromList(List.of(talent), query);

    assertThat(page.getRecords()).hasSize(1);
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend; mvn -Dtest=TalentQueryServiceTest#page_shouldApplyBusinessFilters test`

Expected: FAIL，当前不存在这些过滤器。

- [ ] **Step 3: 扩展 Query DTO 字段**

```java
private String liveSalesBand;
private String liveViewBand;
private String liveGpmBand;
private String videoSalesBand;
private String videoPlayBand;
private String videoGpmBand;
private String level;
private String gender;
private String contactStatus;
```

- [ ] **Step 4: 在 QueryService 中实现最小过滤器组合**

```java
private boolean matchesClaimStatus(Talent talent, String claimStatus) {
    if (!StringUtils.hasText(claimStatus)) return true;
    return switch (claimStatus) {
        case "CLAIMED" -> "PRIVATE".equals(talent.getPoolStatus());
        case "UNCLAIMED" -> "PUBLIC".equals(talent.getPoolStatus());
        default -> true;
    };
}
```

```java
private boolean matchesCategory(Talent talent, String category) {
    return !StringUtils.hasText(category)
            || (StringUtils.hasText(talent.getCategory()) && talent.getCategory().contains(category));
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `cd backend; mvn -Dtest=TalentQueryServiceTest#page_shouldApplyBusinessFilters test`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/colonel/saas/dto/talent/TalentPageQuery.java backend/src/main/java/com/colonel/saas/service/TalentQueryService.java backend/src/test/java/com/colonel/saas/service/TalentQueryServiceTest.java
git commit -m "feat: add talent business filters"
```

## Task 5: 补黑名单动作与状态

**Files:**
- Create: `backend/src/main/java/com/colonel/saas/dto/talent/TalentOperateRequest.java`
- Modify: `backend/src/main/java/com/colonel/saas/controller/TalentController.java`
- Modify: `backend/src/main/java/com/colonel/saas/service/TalentService.java`
- Modify: `backend/src/main/resources/db/init-db.sql`
- Test: `backend/src/test/java/com/colonel/saas/service/TalentServiceTest.java`
- Test: `backend/src/test/java/com/colonel/saas/controller/TalentControllerTest.java`

- [ ] **Step 1: 写失败测试，覆盖拉黑 / 解除拉黑**

```java
@Test
void blacklist_shouldMarkTalent() {
    UUID talentId = UUID.randomUUID();
    Talent talent = new Talent();
    talent.setId(talentId);
    talent.setDeleted(0);

    when(talentMapper.selectById(talentId)).thenReturn(talent);

    talentService.blacklist(talentId, "重复违约");

    assertThat(talent.getBlacklisted()).isTrue();
    assertThat(talent.getBlacklistReason()).isEqualTo("重复违约");
    verify(talentMapper).updateById(talent);
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend; mvn -Dtest=TalentServiceTest#blacklist_shouldMarkTalent test`

Expected: FAIL，当前不存在 `blacklist` 行为。

- [ ] **Step 3: 增加最小请求 DTO 和 Service 方法**

```java
public class TalentOperateRequest {
    private String reason;
}
```

```java
public Talent blacklist(UUID talentId, String reason) {
    Talent talent = getById(talentId);
    talent.setBlacklisted(true);
    talent.setBlacklistReason(StringUtils.hasText(reason) ? reason.trim() : "手动拉黑");
    talentMapper.updateById(talent);
    return talent;
}

public Talent unblacklist(UUID talentId) {
    Talent talent = getById(talentId);
    talent.setBlacklisted(false);
    talent.setBlacklistReason(null);
    talentMapper.updateById(talent);
    return talent;
}
```

- [ ] **Step 4: 暴露 Controller 接口**

```java
@PostMapping("/{id}/blacklist")
public ApiResult<Talent> blacklist(@PathVariable UUID id, @RequestBody TalentOperateRequest request) {
    return ok(talentService.blacklist(id, request == null ? null : request.getReason()));
}

@PostMapping("/{id}/unblacklist")
public ApiResult<Talent> unblacklist(@PathVariable UUID id) {
    return ok(talentService.unblacklist(id));
}
```

- [ ] **Step 5: 更新初始化 SQL**

```sql
ALTER TABLE talent
    ADD COLUMN IF NOT EXISTS blacklisted BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS blacklist_reason VARCHAR(255);
```

- [ ] **Step 6: 运行测试确认通过**

Run: `cd backend; mvn -Dtest=TalentServiceTest#blacklist_shouldMarkTalent -Dtest=TalentControllerTest test`

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/colonel/saas/dto/talent/TalentOperateRequest.java backend/src/main/java/com/colonel/saas/controller/TalentController.java backend/src/main/java/com/colonel/saas/service/TalentService.java backend/src/main/resources/db/init-db.sql backend/src/test/java/com/colonel/saas/service/TalentServiceTest.java backend/src/test/java/com/colonel/saas/controller/TalentControllerTest.java
git commit -m "feat: add talent blacklist operations"
```

## Task 6: 前端改造成达人经营台

**Files:**
- Modify: `frontend/src/api/talent.ts`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/views/talent/index.vue`
- Create: `frontend/src/views/talent/constants.ts`
- Create: `frontend/src/views/talent/composables/useTalentFilters.ts`
- Create: `frontend/src/views/talent/components/TalentBoardTabs.vue`
- Create: `frontend/src/views/talent/components/TalentMetricFilters.vue`
- Create: `frontend/src/views/talent/components/TalentStatusActions.vue`

- [ ] **Step 1: 先写失败的 API 类型约束**

```ts
export interface TalentQueryParams {
  page?: number
  size?: number
  keyword?: string
  poolStatus?: string
  view?: 'TEAM_PUBLIC' | 'MY_TALENTS' | 'NATURAL_ORDERS' | 'BLACKLIST'
  category?: string
  claimStatus?: 'CLAIMED' | 'UNCLAIMED'
  minFans?: number
  maxFans?: number
  region?: string
}
```

- [ ] **Step 2: 在常量文件中定义达人视图和主类目**

```ts
export const TALENT_BOARD_VIEWS = [
  { label: '团队公海', value: 'TEAM_PUBLIC' },
  { label: '我的达人', value: 'MY_TALENTS' },
  { label: '自然出单达人', value: 'NATURAL_ORDERS' },
  { label: '达人黑名单', value: 'BLACKLIST' }
] as const

export const TALENT_MAIN_CATEGORIES = [
  '玩具乐器', '服饰内衣', '个护家清', '智能家居', '生鲜', '美妆', '母婴宠物',
  '鲜花园艺', '本地生活', '食品饮料', '3C数码家电', '图书教育', '鞋靴箱包'
]
```

- [ ] **Step 3: 在 composable 中统一筛选状态**

```ts
export function useTalentFilters() {
  const filters = reactive({
    view: 'TEAM_PUBLIC',
    keyword: '',
    category: null as string | null,
    claimStatus: null as string | null,
    region: '',
    minFans: null as number | null,
    maxFans: null as number | null
  })

  const buildParams = () => ({
    ...filters,
    category: filters.category || undefined,
    claimStatus: filters.claimStatus || undefined
  })

  return { filters, buildParams }
}
```

- [ ] **Step 4: 在 `index.vue` 中替换现有简易工具栏**

```vue
<TalentBoardTabs v-model:value="filters.view" @update:value="handleSearch" />
<TalentMetricFilters
  v-model:filters="filters"
  :categories="TALENT_MAIN_CATEGORIES"
  @search="handleSearch"
  @reset="resetFilters"
/>
```

- [ ] **Step 5: 在表格操作列中接入黑名单动作**

```ts
row.blacklisted
  ? h(TalentStatusActions, { row, onUnblacklist: () => handleUnblacklist(row) })
  : h(TalentStatusActions, { row, onClaim: () => handleClaim(row), onRelease: () => handleRelease(row), onBlacklist: () => handleBlacklist(row) })
```

- [ ] **Step 6: 手动验证页面可加载**

Run: `cd frontend; npm run build`

Expected: BUILD SUCCESS，无 `talent` 模块编译错误。

- [ ] **Step 7: Commit**

```bash
git add frontend/src/api/talent.ts frontend/src/router/index.ts frontend/src/views/talent/index.vue frontend/src/views/talent/constants.ts frontend/src/views/talent/composables/useTalentFilters.ts frontend/src/views/talent/components/TalentBoardTabs.vue frontend/src/views/talent/components/TalentMetricFilters.vue frontend/src/views/talent/components/TalentStatusActions.vue
git commit -m "feat: upgrade talent crm to operating console"
```

## Task 7: 补达人详情与列表经营字段

**Files:**
- Modify: `frontend/src/api/talent.ts`
- Modify: `frontend/src/views/talent/components/TalentDetailModal.vue`
- Modify: `frontend/src/views/talent/index.vue`
- Modify: `backend/src/main/java/com/colonel/saas/dto/talent/TalentDetailResponse.java`
- Modify: `backend/src/main/java/com/colonel/saas/service/TalentQueryService.java`
- Test: `backend/src/test/java/com/colonel/saas/service/TalentQueryServiceTest.java`

- [ ] **Step 1: 写失败测试，要求详情返回经营字段**

```java
@Test
void detail_shouldReturnBusinessMetrics() {
    TalentDetailResponse response = talentQueryService.detail(UUID.randomUUID());
    assertThat(response.getTalent().getMonthlySales()).isNotNull();
    assertThat(response.getTalent().getMainCategory()).isNotNull();
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend; mvn -Dtest=TalentQueryServiceTest#detail_shouldReturnBusinessMetrics test`

Expected: FAIL，详情 DTO 还没有这些字段。

- [ ] **Step 3: 扩展详情 DTO**

```java
private String mainCategory;
private String liveSalesBand;
private String liveViewBand;
private String liveGpmBand;
private String videoSalesBand;
private String videoPlayBand;
private String videoGpmBand;
private Boolean blacklisted;
private String blacklistReason;
```

- [ ] **Step 4: 在前端详情弹窗中展示这些字段**

```vue
<n-descriptions-item label="主推类目">{{ detail.talent?.mainCategory || '-' }}</n-descriptions-item>
<n-descriptions-item label="直播销售额区间">{{ detail.talent?.liveSalesBand || '-' }}</n-descriptions-item>
<n-descriptions-item label="视频销售额区间">{{ detail.talent?.videoSalesBand || '-' }}</n-descriptions-item>
<n-descriptions-item label="黑名单状态">{{ detail.talent?.blacklisted ? '已拉黑' : '正常' }}</n-descriptions-item>
```

- [ ] **Step 5: 运行测试与构建**

Run: `cd backend; mvn -Dtest=TalentQueryServiceTest#detail_shouldReturnBusinessMetrics test`

Expected: PASS

Run: `cd frontend; npm run build`

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/colonel/saas/dto/talent/TalentDetailResponse.java backend/src/main/java/com/colonel/saas/service/TalentQueryService.java backend/src/test/java/com/colonel/saas/service/TalentQueryServiceTest.java frontend/src/api/talent.ts frontend/src/views/talent/components/TalentDetailModal.vue frontend/src/views/talent/index.vue
git commit -m "feat: expose talent business metrics in list and detail"
```

## Task 8: 回归与可见浏览器验收

**Files:**
- Create: `runtime/qa/talent-platform-smoke.cjs`
- Modify: `docs/04-开发进度.md`
- Modify: `docs/archive/records/24-达人经营平台补全一期记录.md`

- [ ] **Step 1: 编写最小浏览器烟雾脚本**

```js
// 核心断言：
// 1. 团队公海视图可打开
// 2. 我的达人视图可打开
// 3. 自然出单达人视图可打开
// 4. 黑名单视图可打开
// 5. 公海达人可认领
// 6. 私海达人可释放
// 7. 达人可拉黑 / 解除拉黑
```

- [ ] **Step 2: 运行后端测试**

Run: `cd backend; mvn test`

Expected: `418+ tests, 0 failures, 0 errors`

- [ ] **Step 3: 运行前端构建**

Run: `cd frontend; npm run build`

Expected: BUILD SUCCESS

- [ ] **Step 4: 运行达人经营台烟雾脚本**

Run: `node runtime/qa/talent-platform-smoke.cjs`

Expected: 生成 `runtime/qa/out/talent-platform-smoke-*/report.md`

- [ ] **Step 5: 回写开发进度**

```md
- 达人经营平台一期：团队公海、我的达人、自然出单达人、黑名单、经营筛选已补齐
- 浏览器烟雾验收：通过
```

- [ ] **Step 6: Commit**

```bash
git add runtime/qa/talent-platform-smoke.cjs docs/04-开发进度.md docs/archive/records/24-达人经营平台补全一期记录.md
git commit -m "test: add talent platform smoke verification"
```

## Follow-up Plans (Not In This Plan)

- `客户端找达人` 单独立项：涉及端侧产品壳、权限、接口裁剪
- `合作管理 / 推广效果` 单独立项：涉及订单域、商品域、效果分析域
- `团长管理 / 商家管理` 单独立项：涉及组织与商家域，不适合作为达人模块子任务强绑

## Self-Review

### Spec coverage

- 已覆盖：团队公海、我的达人、自然出单达人、达人黑名单、经营筛选、认领状态、达人经营字段
- 明确未纳入：客户端找达人、团长管理、商家管理、合作管理、推广效果
- 风险点：自然出单达人判定规则需要在实现前固定为“近 30 天存在订单且当前无认领冲突”或其他明确口径，否则测试会漂移

### Placeholder scan

- 已避免 `TODO / TBD / 以后再说` 这类占位词
- 计划中所有任务都指向了具体文件和具体命令
- 对未纳入范围的内容已单列 follow-up，而不是混在任务里假装本轮完成

### Type consistency

- 前端统一使用 `view`, `claimStatus`, `category`, `minFans`, `maxFans`
- 后端统一使用 `TalentPageQuery`
- 黑名单动作统一走 `TalentOperateRequest`

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-04-talent-platform-gap-closure.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**

