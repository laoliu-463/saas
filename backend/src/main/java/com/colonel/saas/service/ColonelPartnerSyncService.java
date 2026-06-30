package com.colonel.saas.service;

import com.colonel.saas.domain.colonel.application.ColonelPartnerSyncApplicationService;
import com.colonel.saas.entity.ColonelPartner;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 团长主数据同步服务（DDD 委派壳，DDD-COLONEL-002 Slice 1）。
 *
 * <p>本类为 1-line 委派壳，所有真实业务逻辑已搬至
 * {@link ColonelPartnerSyncApplicationService}。保留本壳以兼容遗留调用方。</p>
 */
@Service
public class ColonelPartnerSyncService {

    private final ColonelPartnerSyncApplicationService applicationService;

    public ColonelPartnerSyncService(@Lazy ColonelPartnerSyncApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public int syncAll() {
        return applicationService.syncAll();
    }

    public List<ColonelPartner> listByNameKeyword(String keyword, int limit) {
        return applicationService.listByNameKeyword(keyword, limit);
    }

    public Set<String> resolveProductIdsByColonelName(String colonelName) {
        return applicationService.resolveProductIdsByColonelName(colonelName);
    }
}