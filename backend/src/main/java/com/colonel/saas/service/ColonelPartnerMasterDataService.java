package com.colonel.saas.service;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.domain.colonel.application.ColonelPartnerMasterDataApplicationService;
import com.colonel.saas.entity.ColonelPartner;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 团长主数据查询服务（DDD 委派壳，DDD-COLONEL-002 Slice 3）。
 *
 * <p>本类为 1-line 委派壳，所有真实业务逻辑已搬至
 * {@link ColonelPartnerMasterDataApplicationService}。保留本壳以兼容遗留调用方。</p>
 */
@Service
public class ColonelPartnerMasterDataService {

    private final ColonelPartnerMasterDataApplicationService applicationService;

    public ColonelPartnerMasterDataService(@Lazy ColonelPartnerMasterDataApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public PageResult<ColonelPartner> list(String keyword, String source, Boolean hasContact, long page, long size) {
        return applicationService.list(keyword, source, hasContact, page, size);
    }

    public ColonelPartner detail(UUID id) {
        try {
            return applicationService.detail(id);
        } catch (BusinessException ex) {
            throw ex;
        }
    }

    public List<String> listSources() {
        return applicationService.listSources();
    }
}