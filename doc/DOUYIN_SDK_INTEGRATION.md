# 抖音 SDK 接入与联调指南（实况版）

**版本**：V1.2  
**更新日期**：2026-04-21

---

## 1. 当前状态

- SDK 封装代码已完成
- 本地单测已通过
- **第三方真实环境联调尚未完成**（当前重点）

---

## 2. 代码路径

- 客户端：`backend/src/main/java/com/colonel/saas/douyin/DouyinApiClient.java`
- Token：`backend/src/main/java/com/colonel/saas/douyin/DouyinTokenService.java`
- API：
  - `douyin/api/ActivityApi.java`
  - `douyin/api/ProductApi.java`
  - `douyin/api/OrderApi.java`
  - `douyin/api/PromotionApi.java`
  - `douyin/api/TalentApi.java`

---

## 3. 已实现机制

1. Token 刷新
- Redis 锁：`douyin:token:lock:{appId}`
- 缓存：`douyin:token:{appId}` / `douyin:refresh:{appId}`
- 31012 标记重授权：`douyin:token:reauthorize_required:{appId}`

2. 订单同步
- 滑窗任务（每 10 分钟）
- 去重写库
- 归因优先级链路

3. 风险控制
- 异常分支统一抛业务异常
- 不持久化敏感明文

---

## 4. 待联调清单（必须完成）

1. Token 真联调
- 验证 access_token 获取/刷新
- 验证 refresh_token 过期（31012）分支

2. 三接口联调
- 活动列表
- 商品列表
- 订单列表（时间窗口）

3. 联调后回归
- 订单入库
- 归因优先级正确
- 寄样自动完成触发

---

## 5. 联调记录模板

每次联调请补充：
- 日期
- 环境（appId/shopId）
- 接口
- 请求参数
- 关键响应字段
- 结果（成功/失败）
- 问题与处理

---

## 6. 当前结论

SDK 开发状态：**开发完成，联调未完成**。  
下一步必须进入真实环境验证，不建议直接进入上线阶段。
