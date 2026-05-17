# 19-TEST-mock warning 专项样本清单

更新时间：2026-05-16

## 一、定位

本文只跟踪 2026-05-16 TEST/mock 数据覆盖审计中剩余的 warning 型专项样本。

本轮基线结论：

- 报告目录：`runtime/qa/out/qa-mock-data-audit-20260516-194040-956/`（最新，同日较早轮 `193210-553` 结论一致）
- `overallPass=true`
- `coverage=84.58%`
- `hardMissing=0`
- `api-errors=[]`
- `mock-data-audit.test=6/6 PASS`
- 后端 `mvn -DskipTests compile` 为 `BUILD SUCCESS`
- `scripts/qa-mock-data-audit.ps1` 为 `Exit code 0`

范围说明：

- 仅代表 TEST/mock 主流程数据覆盖硬缺口清零
- 不代表所有业务增强场景 100% 完成
- 不代表 real-pre 或真实三方联调完成
- 本轮未切换 real-pre，未访问真实三方接口，未执行清库操作

## 二、分级原则

| 级别 | 含义 | 当前处理 |
|---|---|---|
| P1 | 建议 real-pre 稳定回归前补专项样本 | 进入 real-pre 前置风险清单 |
| P2 | 专项增强或收益规则回归 | 独立排期，不阻塞 TEST/mock 主流程 |

## 三、warning 清单

| warning 场景 | 审计场景 ID | 当前是否阻塞 | 建议阶段 | 处理方式 |
|---|---|---|---|---|
| 活动无商品样本 | `activity_without_products` | 否 | P2 | 商品 / 活动空状态专项补样本 |
| 投流支持样本 | `support_traffic` | 否 | P2 | 后续补商品属性 / 筛选专项 |
| 投流不支持样本 | `not_support_traffic` | 否 | P2 | 后续补商品属性 / 筛选专项 |
| 需寄样商品样本 | `sample_required` | 否 | P2 | 商品寄样要求展示与筛选专项 |
| 不需寄样商品样本 | `sample_not_required` | 否 | P2 | 商品寄样要求展示与筛选专项 |
| 转链失败 | `convert_failed` | 建议补 | P1 | mock gateway 增加失败返回，验证推广链接兜底与错误提示 |
| 爬虫成功 | `crawler_success` | 建议补 | P1 | 增加达人采集成功样本，和失败兜底形成对照 |
| 爬虫失败手动兜底 | `crawler_failed_manual_fallback` | 建议补 | P1 | 增加采集失败达人 + 手动补录路径 |
| 达人满足寄样资格 | `qualified_for_sample` | 建议补 | P1 | 补寄样候选资格样本，验证申请前置判断 |
| 达人不满足寄样资格 | `not_qualified_for_sample` | 建议补 | P1 | 补不满足资格样本，验证禁用和原因提示 |
| 多人认领数据样本 | `multi_claim` | 否 | P2 | 已有浏览器专项证据，后续补固定 seed |
| 达人资料不完整 | `incomplete_profile` | 否 | P2 | 达人资料质量专项补样本 |
| 寄样由订单完成 | `completed_by_order` | 建议补 | P1 | 补订单命中寄样自动完成样本，并纳入状态流转回归 |
| 歧义映射 | `AMBIGUOUS_MAPPING` | 建议补 | P1 | 增加同一 `pick_source` 多候选或无法安全归因样本 |
| 独家达人 | `EXCLUSIVE_TALENT` | 否 | P2 | 纳入 `exclusive-rule-regression` |
| 独家商家 | `EXCLUSIVE_MERCHANT` | 否 | P2 | 纳入 `exclusive-rule-regression` |
| 无订单日期 | `no_order_date` | 否 | P2 | 订单异常数据专项补样本 |

## 四、P1 real-pre 前置建议

进入下一轮 real-pre 稳定回归前，优先补齐：

1. 转链失败：验证推广链接兜底、错误提示、渠道操作体验。
2. 爬虫失败手动兜底：验证达人外部数据不稳定时仍可继续业务动作。
3. 歧义映射：验证订单归因解释能力，尤其是 Dashboard 已区分未归因分类后。
4. 寄样由订单完成：验证订单副作用能推动寄样状态闭环。
5. 达人寄样资格正反样本：验证申请寄样前置条件和禁用提示。

## 五、P2 专项回归

建议单独建立 `exclusive-rule-regression`，覆盖：

- 独家达人 69.99%
- 独家达人 70%
- 独家达人寄样 9 个
- 独家达人寄样 10 个
- 独家商家 69.99%
- 独家商家 70%
- 独家规则覆盖默认归属
- 下月退出机制

这些场景不纳入 TEST/mock 主流程硬门槛，避免主流程审计继续变重。

## 六、下一阶段路线

TEST/mock 主流程数据覆盖收口后，后续优先级为：

1. 状态流转自动化回归
2. 角色业务操作 smoke
3. Dashboard 聚合对账
4. real-pre 前置 P1 warning 补齐
5. TEST/mock 最终验收报告
6. real-pre 三方联调回归
