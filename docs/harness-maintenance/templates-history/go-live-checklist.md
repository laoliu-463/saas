# 上线验收清单

## 渠道链验收

- 商品库可选品。
- 系统转链生成 `pick_source`。
- `pick_source_mapping` 可追溯商品、达人 / 渠道、时间和第三方响应。
- 真实订单带回 `pick_source`。
- 订单归因后 `default_channel_id`、`default_recruiter_id` 不为空。
- 寄样待交作业自动完成。
- 渠道业绩归属正确。
- dashboard / 报表读取正确。

## 招商链验收

- 活动同步成功。
- 推广中活动商品自动入库。
- 商品上架审核状态合法。
- 审核寄样链路可走通。
- 订单同步后招商业绩归属正确。
- 商品库历史入库 / repair 不依赖裸 SQL 批改。

## 管理链验收

- 用户登录成功。
- 角色、菜单、数据范围生效。
- admin / group / self 账号访问同一列表结果符合预期。
- 配置读取、修改和审计可查。
- 操作日志和权限拦截有证据。

## 订单归因验收

- 订单同步有真实接口响应或明确 BLOCKED 证据。
- 订单事实入库幂等。
- `pick_source -> mapping -> channel_user_id` 成立。
- 订单域不计算提成和最终归属。

## 寄样自动完成验收

- 存在已审批 / 已发货 / 待交作业寄样申请。
- 订单已同步事件包含 `channel_id + talent_id + product_id + pay_time`。
- 寄样域消费订单事件后状态合法流转。
- 操作日志可追溯。

## 业绩计算验收

- 业绩域消费订单事实、商品映射、达人关系和配置参数。
- 输出业绩明细、提成、冲正和汇总。
- 分析模块只读汇总，不重算归属。

## 权限范围验收

- admin 看到全量。
- group 只看到本组。
- self 只看到本人。
- 前端隐藏菜单不替代后端鉴权。
- 自定义角色不自动获得业务写接口权限。

## real-pre 安全验收

- `APP_TEST_ENABLED=false`。
- `DOUYIN_TEST_ENABLED=false`。
- `DOUYIN_REAL_UPSTREAM_MODE=live`。
- 未执行清库、`down -v` 或 volume 删除。
- test/mock 结果未写成 real-pre PASS。

