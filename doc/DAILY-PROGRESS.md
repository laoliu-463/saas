# 开发进度记录

**项目**：抖音团长 SaaS V2.2  
**最后更新**：2026-04-21

---

## 2026-04-21 进度总结

### 已完成

1. 后端主链路
- RBAC + JWT + DataScope
- 商品、达人、寄样、数据模块核心接口
- 抖音 SDK 封装（Activity/Product/Order/Promotion/Talent）

2. 订单与归因
- 订单滑窗同步 + Redis 分布式锁
- 归因优先级升级：独家商家 > 独家达人 > pick_source

3. 独家机制
- ExclusiveTalent/ExclusiveMerchant 实体、Mapper、Service
- 月度评估定时任务

4. 寄样闭环
- 订单驱动自动完成待交作业
- 30天未出单自动关闭
- 状态日志落库

5. 测试
- 修复 SDK/Crawler 相关失败测试
- `mvn test` 全绿

### 未完成

1. 第三方 SDK 真联调
- 当前仅本地/Mock验证
- 真实 token/真实接口返回待验证

2. 看板真实口径
- M1.6 待收口

3. 部署验收
- M1.7 待完成

---

## 下一步计划

1. 完成 SDK 最小真联调闭环
2. 完成 M1.3 真数据验收（入库/归因/寄样闭环）
3. 推进 M1.6、M1.7
