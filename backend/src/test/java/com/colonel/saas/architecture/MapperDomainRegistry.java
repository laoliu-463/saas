package com.colonel.saas.architecture;

import java.util.Locale;
import java.util.Set;

/**
 * Maps owner classes and MyBatis mapper types to DDD domains for cross-domain guard tests (DDD-BASE-003).
 */
final class MapperDomainRegistry {

    enum Domain {
        USER, ORDER, PRODUCT, SAMPLE, TALENT, PERFORMANCE, CONFIG, ANALYTICS, INFRA, EVENT, UNKNOWN
    }

    private static final Set<String> INFRA_MAPPERS = Set.of(
            "OperationLogMapper",
            "DouyinWebhookEventMapper",
            "DomainEventOutboxMapper",
            "DomainEventConsumeLogMapper",
            "MerchantMapper"
    );

    private MapperDomainRegistry() {
    }

    static Domain mapperDomain(String mapperSimpleName) {
        if (INFRA_MAPPERS.contains(mapperSimpleName)) {
            return Domain.INFRA;
        }
        return switch (mapperSimpleName) {
            case "SysUserMapper", "SysDeptMapper", "SysRoleMapper", "SysMenuMapper",
                 "SysUserRoleMapper", "SysRoleMenuMapper" -> Domain.USER;
            case "ColonelsettlementOrderMapper", "PickSourceMappingMapper", "OrderSyncDedupClaimMapper",
                 "ColonelOrderSettlementMapper", "PromotionLinkMapper" -> Domain.ORDER;
            case "ProductMapper", "ProductSnapshotMapper", "ColonelPartnerMapper",
                 "ColonelsettlementActivityMapper", "ProductOperationStateMapper",
                 "ProductOperationLogMapper", "ProductDisplayAuditLogMapper" -> Domain.PRODUCT;
            case "SampleRequestMapper", "SampleStatusLogMapper", "SampleLogisticsTraceMapper" -> Domain.SAMPLE;
            case "TalentMapper", "TalentClaimMapper", "TalentFollowRecordMapper", "TalentTagMapper",
                 "TalentTagRelationMapper", "CrawlerTalentInfoMapper", "TalentEnrichTaskMapper",
                 "TalentFieldSourceMapper", "TalentProfileSyncLogMapper", "ExclusiveTalentMapper" -> Domain.TALENT;
            case "PerformanceRecordMapper", "CommissionRuleMapper", "ExclusiveMerchantMapper" -> Domain.PERFORMANCE;
            case "SystemConfigMapper", "SystemConfigChangeLogMapper" -> Domain.CONFIG;
            default -> Domain.UNKNOWN;
        };
    }

    static Domain ownerDomain(String ownerFqcn) {
        String lower = ownerFqcn.toLowerCase(Locale.ROOT);
        if (lower.contains(".domain.user.")) {
            return Domain.USER;
        }
        if (lower.contains(".domain.talent.")) {
            return Domain.TALENT;
        }
        if (lower.contains(".domain.product.")) {
            return Domain.PRODUCT;
        }
        if (lower.contains(".domain.order.")) {
            return Domain.ORDER;
        }
        if (lower.contains(".domain.sample.")) {
            return Domain.SAMPLE;
        }
        if (lower.contains(".domain.config.")) {
            return Domain.CONFIG;
        }
        if (lower.contains(".domain.performance.")) {
            return Domain.PERFORMANCE;
        }
        if (lower.contains(".auth.") || lower.contains(".domain.user.")
                || lower.endsWith("userdomainservice")
                || lower.endsWith("usermasterdataservice")
                || lower.endsWith("sysdeptservice")) {
            return Domain.USER;
        }
        if (lower.contains(".service.sample") || lower.contains("sampleapplication")
                || lower.contains("samplefilteroptions") || lower.contains("samplelifecycle")
                || lower.contains("samplelogistics") || lower.contains("kuaidi100")) {
            return Domain.SAMPLE;
        }
        if (lower.contains(".service.data") || lower.contains("dashboardservice")) {
            return Domain.ANALYTICS;
        }
        if (lower.contains(".service.performance") || lower.contains("performance")
                || lower.contains("commissionrule") || lower.contains("exclusivemerchant")) {
            return Domain.PERFORMANCE;
        }
        if (lower.contains(".service.talent") || lower.contains("talentservice")
                || lower.contains("talentquery") || lower.contains("talenttag")
                || lower.contains("talentfollow") || lower.contains("exclusivetalent")
                || lower.contains("crawler")) {
            return Domain.TALENT;
        }
        if (lower.contains("ordersync") || lower.contains("orderservice")
                || lower.contains("orderattribution") || lower.contains("ordercontroller")
                || lower.contains("order6468")) {
            return Domain.ORDER;
        }
        if (lower.contains("picksourcemapping")) {
            return Domain.ORDER;
        }
        if (lower.contains("sysconfigservice")) {
            return Domain.CONFIG;
        }
        if (lower.contains("product") || lower.contains("colonelpartner")
                || lower.contains("colonelactivity") || lower.contains("colonelsettlementactivity")
                || lower.contains("activityaccess") || lower.contains("merchant")
                || lower.contains("attributionservice") || lower.contains("promotionlink")) {
            return Domain.PRODUCT;
        }
        if (lower.contains("businessruleconfig") || lower.contains("rulecenter")
                || lower.contains("talentpresettags")) {
            return Domain.CONFIG;
        }
        if (lower.contains("operationlog") || lower.contains("douyinwebhook")
                || lower.contains("domainevent")) {
            return Domain.INFRA;
        }
        if (lower.contains("listener") || lower.contains(".job.")) {
            return Domain.EVENT;
        }
        if (lower.contains("sysdeptservice")) {
            return Domain.USER;
        }
        return Domain.UNKNOWN;
    }

    static boolean isCrossDomain(Domain owner, Domain mapper) {
        if (mapper == Domain.INFRA || mapper == Domain.UNKNOWN) {
            return false;
        }
        if (owner == Domain.UNKNOWN) {
            return true;
        }
        if (owner == Domain.INFRA || owner == Domain.EVENT) {
            return mapper != Domain.INFRA && mapper != Domain.EVENT;
        }
        return owner != mapper;
    }
}
