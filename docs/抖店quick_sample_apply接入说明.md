# 抖店 quick_sample_apply 接入说明

## 调研结论

在仓库与现有 Douyin/Buyin SDK 封装中**未找到**官方 `quick_sample_apply` 接口实现；文档与代码均标记为 `NOT_IMPLEMENTED`。

**不得伪造抖店官方接口。**

## 当前接入状态

| 环境 | 实现 | 状态 |
|------|------|------|
| test / mock | `MockDouyinQuickSampleGateway` | `MOCK_ONLY`，`isSupported()=false` |
| real | `RealDouyinQuickSampleGateway` | `UNSUPPORTED_BY_SDK`，明确错误码 |

Feature Flag：`app.douyin.quick-sample.enabled`（默认 `false`）。

## 业务流程

1. 商品库 `POST /products/{relationId}/quick-sample-apply`（兼容 `/quick-sample`）
2. 若 `enabled && gateway.isSupported()`：调用外部网关，成功则 `apply_source=DOUYIN_QUICK_SAMPLE`
3. 否则：`apply_source=LOCAL_FALLBACK`，创建系统内 `sample_request`

## 响应字段

`externalEnabled` / `externalSupported` / 逐达人 `externalApplied` / `fallback`。

## 后续真实接入条件

1. 抖店/Buyin 官方文档确认 API 路径与权限包
2. SDK 或 Gateway 增加真实请求
3. real-pre 授权验证通过后方可标记 `REAL_CONNECTED`
