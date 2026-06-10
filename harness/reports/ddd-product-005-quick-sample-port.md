# DDD-PRODUCT-005: 商品域快速寄样入口改走 SampleApplicationPort

## 时间
2026-06-10T16:30:00+08:00

## 环境
real-pre (本地开发)

## 分支
当前工作分支

## 改造目标
商品域只负责从商品卡片发起寄样命令，不直接写寄样表、不处理寄样状态机。寄样创建由寄样域完成。

## 变更清单

### 新增文件
| 文件 | 包路径 | 说明 |
|------|--------|------|
| `SampleApplicationPort.java` | `domain.sample.api` | 寄样域应用层端口接口 |
| `ApplySampleFromProductCommand.java` | `domain.sample.api` | 商品域发起的寄样命令 (record) |
| `ApplySampleFromProductResult.java` | `domain.sample.api` | 寄样端口返回结果 (含 TalentResult 内部类) |
| `SampleApplicationPortImpl.java` | `domain.sample.application` | 端口实现：达人解析→私海校验→去重→资质评估→网关→落库→事件 |

### 修改文件
| 文件 | 变更说明 |
|------|----------|
| `ProductQuickSampleService.java` | 移除 7 个直连 Mapper/Service 依赖，改走 SampleApplicationPort。从 801 行减至 ~230 行 |
| `QuickSampleApplyTest.java` | 全面改写：mock SampleApplicationPort，新增端口委托断言、命令字段验证、部分失败等测试 |
| `cross-domain-mapper-legacy-whitelist.txt` | 移除 3 条 ProductQuickSampleService 旧条目，新增 2 条 SampleApplicationPortImpl 条目 |

### 架构变更
```
Before:
ProductController → ProductQuickSampleService → SampleRequestMapper (直写)
                                              → TalentMapper (直连)
                                              → TalentClaimMapper (直连)
                                              → SampleDomainEventPublisher
                                              → SampleStatusLogService

After:
ProductController → ProductQuickSampleService → SampleApplicationPort (端口)
                                                     ↓
                                              SampleApplicationPortImpl → SampleRequestMapper
                                                                       → TalentMapper
                                                                       → TalentClaimMapper
                                                                       → SampleDomainEventPublisher
                                                                       → SampleStatusLogService
```

### 命令字段
- relationId, productId, channelId, talentIds, spec, quantity, remark
- receiverName, receiverPhone, receiverAddress
- requestSource = "quick_product_library"
- userId, channelUserId, roleCodes
- productSnapshotTitle, productSnapshotPrice, activityId, assigneeId
- externalEnabled, externalSupported, skuId

### 依赖变更
**ProductQuickSampleService 移除的依赖:**
- SampleRequestMapper ❌
- TalentMapper ❌
- TalentClaimMapper ❌
- CrawlerTalentInfoService ❌
- SampleEligibilityService ❌
- SampleStatusLogService ❌
- SampleDomainEventPublisher ❌
- ConfigDomainFacade ❌

**ProductQuickSampleService 新增的依赖:**
- SampleApplicationPort ✅

## 测试结果

### 目标测试
| 测试类 | 用例数 | 结果 |
|--------|--------|------|
| QuickSampleApplyTest | 13 | ✅ 全部通过 |
| DddCrossDomainMapperGuardTest | 3 (1 skipped) | ✅ 全部通过 |
| ProductControllerTest | 20 | ✅ 全部通过 |
| SampleLifecycleServiceTest | 12 | ✅ 全部通过 |
| DouyinQuickSampleGatewayTest | 2 | ✅ 全部通过 |

### 全量测试
- 总计: 1861 tests
- 新增失败: 0
- 预存失败: DddConfig003ConfigRoutingTest (2F), Spring Context tests (NoClassDefFound, Docker unavailable) — 均为本次变更前已存在的问题

## 禁止项检查
- ✅ 商品域不直接判断寄样状态机
- ✅ 商品域不直接查达人收货地址
- ✅ 快速寄样前端交互未改变 (API 路径 POST /{relationId}/quick-sample 不变，响应结构不变)
- ✅ 旧 SampleService (SampleApplicationService) 未删除

## 前端兼容性
- API 路径: `POST /products/${relationId}/quick-sample` — 不变
- 响应结构: `QuickSampleApplyResponse` — 不变 (由 ProductQuickSampleService 从 port result 映射)
- QuickSampleModal 组件 — 无需修改

## 结论
**PASS** — 商品域快速寄样已成功改走 SampleApplicationPort，商品域不再直写寄样表。

## 剩余风险
1. `SampleApplicationPortImpl` 仍使用 TalentMapper/TalentClaimMapper（跨域 Mapper），已更新至 whitelist 并记录为技术债
2. 未来应进一步将达人解析和认领校验通过 TalentDomainFacade 解耦
3. Spring Context 依赖测试（CharacterizationBaselineTest 等）需要 Docker 环境才能运行
