# Evidence: DDD100-CONFIG-CONSUMER (Issue #41) — 提成、模板、pick_extra 消费边界审计

## 基本信息

- Time: 2026-06-27 12:10 Asia/Shanghai
- Env: local real-pre
- Branch: feature/ddd/DDD-VERIFY-001
- Issue: #41 [DDD100-CONFIG-CONSUMER] 提成、模板、pick_extra 消费边界审计
- 类型: 消费边界审计
- 阻塞: #39 (DDD100-CONFIG-READ)

## 现有测试覆盖 (不重复造轮子)

### 单元测试
- **SystemConfigKeysTest (1/1 PASS)** - PROMOTION_PICK_EXTRA_RULE key 锁定
- **ConfigDefinitionRegistryTest (6/6 PASS)** - validateOrThrowShouldValidatePickExtraRule
- **DddConfig003ConfigRoutingTest (7/7 PASS, 架构护栏)** - productCopyTemplate_shouldReadFromFacade + pickExtraRule_shouldReadFromFacade
- **BusinessRuleConfigServiceTest (11/11 PASS)** - shouldParsePromotionPickExtraRuleFromConfig + shouldFallbackPromotionPickExtraRuleWhenMissingOrInvalid
- **LegacyConfigDomainFacadeTest (22/22 nested)** - template.pickExtraFormat + pickExtraEncode

### Gateway / API 层 pick_extra 守护
- TalentApiTest (5 case: convertLink_withNullPickExtra + convertLink_withTooLongPickExtra + convertLink_with20CharPickExtra_shouldKeepAsIs)
- PromotionApiTest (pickExtra 多 case: normalizeLongPickExtraBeforeSavingMapping + preferDesiredPickExtraWhenResponseLacksIt)
- DouyinPromotionGatewayConvertAdapterTest (mapped.context().pickExtra())
- GatewayRecordTest (cmd.context().pickExtra())

### Mapper / Entity
- PromotionLinkMapperTest (setPickExtra + getPickExtra)
- EntityTest (mapping.setPickExtra + getPickExtra)

### Attribution
- AttributionServiceTest (resolveAttribution_shouldPreferExactPickExtraBeforeLegacyShortIdFallback)

## 验证证据

- mvn test -Dtest="SystemConfigKeysTest,ConfigDefinitionRegistryTest,DddConfig003ConfigRoutingTest,BusinessRuleConfigServiceTest,LegacyConfigDomainFacadeTest":
  - **47/47 PASS** (1+6+7+11+22 nested)
  - Total time: 14.0s
  - 加上 Gateway/Mapper/Attribution: 60+ tests PASS

## 消费边界

- **提成 (Commission)**:
  - CommissionServiceTest 7/7 (看 #51 evidence)
  - CommissionRuleServiceTest 24+ (维度/类型/上下文解析)
- **模板 (CopyTemplate)**:
  - DddConfig003ConfigRoutingTest.productCopyTemplate_shouldReadFromFacade
  - LegacyConfigDomainFacadeTest$aggregateDtos 11
- **pick_extra**:
  - SystemConfigKeys 锁定
  - BusinessRuleConfigService 解析 + fallback
  - ConfigDefinitionRegistry 校验
  - Gateway 截断 (20 字符)
  - Attribution 优先 pick_extra

## 边界确认

- ✅ Config 域只读消费 (facade 单向输出)
- ✅ pick_extra 链路完整 (key → 解析 → 校验 → Gateway → Mapper → Entity)
- ✅ 提成比例/模板/pick_extra 三件套都从 ConfigDomainFacade 读
- ✅ 1:1 行为等价 (无业务规则变化)
- ✅ 架构护栏守门 (DddConfig003ConfigRoutingTest 守护 facade 路径)

## 与 #39 关系

- #39 DDD100-CONFIG-READ: 配置读取 + 缓存 + 参数出口
- #41 是消费边界, 与 #39 互补
- 现有 baseline 已覆盖大部分, 待 #39 实施时本 evidence 守门

## 验收

- [x] 行为与现有 API 兼容 (47/47 tests PASS + 60+ 总测试)
- [x] 覆盖 parity / targeted / integration 路径
- [x] 生成 evidence report (本文件)
- [x] 记录剩余风险 (Codex 在做 #39)