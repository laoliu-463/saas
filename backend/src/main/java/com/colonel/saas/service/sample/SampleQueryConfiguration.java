package com.colonel.saas.service.sample;

import com.colonel.saas.domain.sample.event.SampleDomainEventPublisher;
import com.colonel.saas.mapper.ProductMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.mapper.SampleStatusLogMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.service.CrawlerTalentInfoService;
import com.colonel.saas.service.ProductService;
import com.colonel.saas.service.SampleEligibilityService;
import com.colonel.saas.service.SampleLogisticsImportService;
import com.colonel.saas.service.SampleLogisticsSubscriptionService;
import com.colonel.saas.service.SampleLogisticsSyncService;
import com.colonel.saas.service.SampleStatusLogService;
import com.colonel.saas.service.SampleWriteTransactionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 寄样查询委托 Bean：提供独立于 {@link com.colonel.saas.controller.SampleController} 的
 * {@link SampleApplicationService} 实例，供 {@link LegacySampleQueryService} 委托查询逻辑。
 *
 * <p>避免 Controller 覆盖查询方法后，查询服务再委托回 Controller 形成运行时循环。
 */
@Configuration
public class SampleQueryConfiguration {

    @Bean(name = "sampleQueryApplicationDelegate")
    SampleApplicationService sampleQueryApplicationDelegate(
            SampleRequestMapper sampleRequestMapper,
            ProductMapper productMapper,
            ProductOperationStateMapper productOperationStateMapper,
            ProductSnapshotMapper productSnapshotMapper,
            SysUserMapper sysUserMapper,
            TalentMapper talentMapper,
            TalentClaimMapper talentClaimMapper,
            SampleStatusLogService sampleStatusLogService,
            SampleStatusLogMapper sampleStatusLogMapper,
            CrawlerTalentInfoService crawlerTalentInfoService,
            ConfigDomainFacade configDomainFacade,
            ProductService productService,
            SampleEligibilityService sampleEligibilityService,
            SampleLogisticsSyncService sampleLogisticsSyncService,
            SampleLogisticsImportService sampleLogisticsImportService,
            SampleLogisticsSubscriptionService sampleLogisticsSubscriptionService,
            SampleDomainEventPublisher sampleDomainEventPublisher,
            SampleWriteTransactionService sampleWriteTransactionService) {
        return new SampleApplicationService(
                sampleRequestMapper,
                productMapper,
                productOperationStateMapper,
                productSnapshotMapper,
                sysUserMapper,
                talentMapper,
                talentClaimMapper,
                sampleStatusLogService,
                sampleStatusLogMapper,
                crawlerTalentInfoService,
                configDomainFacade,
                productService,
                sampleEligibilityService,
                sampleLogisticsSyncService,
                sampleLogisticsImportService,
                sampleLogisticsSubscriptionService,
                sampleDomainEventPublisher,
                sampleWriteTransactionService);
    }
}
