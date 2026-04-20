# 需求：达人 CRM 管理

**文档版本**：V1.0
**来源**：V2.2 定稿文档 §3.3
**智能体入口**：直接读取此文件

---

## 一、功能定位

达人 CRM 是渠道管理合作达人的核心工具，覆盖达人信息采集、公海私海、认领保护机制。

---

## 二、功能清单

### 2.1 达人添加

| 功能 | 说明 |
|------|------|
| 手动添加 | 渠道输入达人抖音号或分享链接 |
| 自动补全 | 系统自动触发爬虫任务，采集达人基本信息 |
| 手动兜底 | 爬虫失败时支持手动填写 |

#### 自动采集字段

| 字段 | 来源 |
|------|------|
| 昵称 | 爬虫采集 |
| 粉丝数 | 爬虫采集 |
| 获赞数 | 爬虫采集 |
| 关注数 | 爬虫采集 |
| 作品数 | 爬虫采集 |
| IP 属地 | 爬虫采集 |
| 采集时间 | 系统自动记录 |

### 2.2 公海与私海

| 池 | 可见范围 | 说明 |
|----|----------|------|
| 公海 | 全员可见 | 未认领达人 |
| 私海 | 仅认领人 | 保护期内达人 |

### 2.3 达人认领

| 规则 | 说明 |
|------|------|
| 认领入口 | 公海列表 |
| 认领后可见性 | 仅认领人可见（保护期内） |
| 多人认领 | **允许**，同一达人可被多人同时认领，联系方式均可见 |
| 保护期 | 30 天（可配置） |
| 保护期规则 | 期内无产出 → 到期自动释放回公海 |
| 业绩归属 | 保护期内出单 → 归认领人 |

### 2.4 达人列表展示字段

| 字段 | 来源 |
|------|------|
| 昵称 | 爬虫采集 |
| UID | 用户输入/爬虫 |
| 粉丝数 | 爬虫采集 |
| 等级 | 爬虫采集 |
| 月销 | 爬虫采集 |
| 合作次数 | 系统统计 |
| 总产出 | 系统统计 |
| 认领人 | 系统记录 |
| 认领时间 | 系统记录 |

### 2.5 数据刷新

| 方式 | 频率 | 说明 |
|------|------|------|
| 手动刷新 | 按需 | 点击按钮重新爬取单个达人 |
| 定时任务 | 每周 | 批量更新活跃达人数据 |

---

## 三、寄样限制规则

### 3.1 限制逻辑

```
限制对象：同一渠道商务 + 同一达人 + 同一商品
限制范围：7 天内不允许重复申请（同一渠道商务）
例外：被拒绝的申请不受 7 天限制
豁免角色：组长、管理员不受限制
```

### 3.2 可配置项

| 规则项 | 默认值 | 配置位置 |
|--------|--------|----------|
| 寄样限制天数 | 7 天 | system_config |
| 寄样限制开关 | 启用 | system_config |
| 达人保护期 | 30 天 | system_config |

### 3.3 限制校验实现

```java
// SampleRequestService.checkDuplicate()
public void checkDuplicate(UUID userId, UUID talentId, UUID productId) {
    SystemConfig config = systemConfigService.getConfig();

    // 1. 检查开关
    if (!config.getSampleLimitEnabled()) return;

    // 2. 检查用户角色（组长/管理员豁免）
    if (userRoleService.isLeaderOrAdmin(userId)) return;

    // 3. 查询最近 7 天是否有有效申请（不含已拒绝）
    LocalDateTime since = LocalDateTime.now().minusDays(config.getSampleLimitDays());
    long count = sampleRequestMapper.countValid(userId, talentId, productId, since);

    if (count > 0) {
        throw new BusinessException("该达人+商品在 " + config.getSampleLimitDays() + " 天内已有申请，请等待");
    }
}
```

---

## 四、达人爬虫规范

### 4.1 爬虫安全约束

> 详见 `rules/crawler-safety.md`

| 规则 | 值 |
|------|------|
| 请求间隔 | 3-6 秒（随机） |
| UA 轮换 | 必须 |
| Cookie 管理 | 必须 |
| 重试退避 | 指数退避 |

### 4.2 爬虫失败处理

```java
public void crawlTalentInfo(String douyinUid) {
    try {
        TalentCrawlerResponse resp = crawlerBase.crawl(douyinUid);
        // 更新达人信息
        talentService.updateFromCrawler(douyinUid, resp);
    } catch (CrawlerException e) {
        // 记录失败原因，允许手动填写
        talentService.markCrawlFailed(douyinUid, e.getMessage());
        log.warn("达人信息采集失败: uid={}, reason={}", douyinUid, e.getMessage());
    }
}
```

---

## 五、业务约束

| 约束 | 文件 | 级别 |
|------|------|------|
| 爬虫必须遵循安全间隔 | `rules/crawler-safety.md` | **CRITICAL** |
| 寄样限制必须校验 | 业务逻辑 | HIGH |
| 保护期到期自动释放 | 定时任务 | HIGH |

---

## 六、相关文件索引

| 文件 | 路径 |
|------|------|
| 达人实体 | `backend/src/main/java/com/colonel/saas/entity/Talent.java` |
| 认领记录实体 | `backend/src/main/java/com/colonel/saas/entity/TalentClaim.java` |
| 爬虫基类 | `backend/src/main/java/com/colonel/saas/crawler/base/CrawlerBase.java` |
| 寄样申请服务 | `backend/src/main/java/com/colonel/saas/service/SampleRequestService.java` |
| 爬虫安全约束 | `rules/crawler-safety.md` |
