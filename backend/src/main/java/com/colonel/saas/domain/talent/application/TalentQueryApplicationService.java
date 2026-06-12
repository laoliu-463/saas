package com.colonel.saas.domain.talent.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.talent.facade.TalentDomainFacade;
import com.colonel.saas.dto.talent.TalentDetailResponse;
import com.colonel.saas.dto.talent.TalentPageQuery;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.service.TalentQueryService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.UUID;

/**
 * 达人查询应用层（DDD-TALENT-003 Batch3 Replace）。
 *
 * <p>Controller 读路径统一经本服务；开关 {@code ddd.refactor.talent-facade.enabled=true}
 * 且根开关开启时，详情/操作校验先走 {@link TalentDomainFacade} 存在性检查，再委派旧 {@link TalentQueryService}。</p>
 */
@Service
public class TalentQueryApplicationService {

    private final TalentQueryService talentQueryService;
    private final TalentDomainFacade talentDomainFacade;
    private final DddRefactorProperties dddRefactorProperties;

    public TalentQueryApplicationService(
            TalentQueryService talentQueryService,
            TalentDomainFacade talentDomainFacade,
            DddRefactorProperties dddRefactorProperties) {
        this.talentQueryService = talentQueryService;
        this.talentDomainFacade = talentDomainFacade;
        this.dddRefactorProperties = dddRefactorProperties;
    }

    /** 是否启用 Facade 路由（需同时打开根开关与 talent-facade 子开关）。 */
    public boolean isRoutingEnabled() {
        return dddRefactorProperties.isEnabled()
                && dddRefactorProperties.getTalentFacade().isEnabled();
    }

    public IPage<Talent> page(TalentPageQuery query) {
        return talentQueryService.page(query);
    }

    public TalentDetailResponse detail(UUID talentId, UUID currentUserId, UUID currentDeptId, DataScope dataScope) {
        if (isRoutingEnabled()) {
            assertTalentExistsViaFacade(talentId);
        }
        return talentQueryService.detail(talentId, currentUserId, currentDeptId, dataScope);
    }

    public void assertCanOperate(UUID talentId, UUID currentUserId, UUID currentDeptId, Collection<?> roleCodes) {
        if (isRoutingEnabled()) {
            assertTalentExistsViaFacade(talentId);
        }
        talentQueryService.assertCanOperate(talentId, currentUserId, currentDeptId, roleCodes);
    }

    private void assertTalentExistsViaFacade(UUID talentId) {
        if (talentId == null || !talentDomainFacade.existsById(talentId)) {
            throw BusinessException.notFound("达人不存在");
        }
    }
}
