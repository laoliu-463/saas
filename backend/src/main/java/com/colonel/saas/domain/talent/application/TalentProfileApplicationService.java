package com.colonel.saas.domain.talent.application;

import com.colonel.saas.dto.talent.TalentBatchImportResult;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.service.TalentService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 达人资料写侧应用层。
 *
 * <p>本层承接 Controller 的资料、标签、手动补全和导入命令入口，
 * 继续委托 Legacy {@link TalentService} 保持 API、DB 和默认 real-pre 行为兼容。</p>
 */
@Service
public class TalentProfileApplicationService {

    private final TalentService talentService;

    public TalentProfileApplicationService(TalentService talentService) {
        this.talentService = talentService;
    }

    public Talent create(Talent talent) {
        return talentService.create(talent);
    }

    public Talent update(UUID id, Talent talent) {
        return talentService.update(id, talent);
    }

    public List<String> updateTags(UUID id, List<String> tags, UUID operatorId) {
        return talentService.updateTags(id, tags, operatorId);
    }

    public Talent manualFill(UUID id, Talent talent) {
        return talentService.manualFill(id, talent);
    }

    public Talent refresh(UUID id) {
        return talentService.refresh(id);
    }

    public TalentBatchImportResult batchImport(List<String> accounts, UUID userId) {
        return talentService.batchImport(accounts, userId);
    }

    public List<String> listPresetTags() {
        return talentService.listPresetTags();
    }

    public void delete(UUID id) {
        talentService.delete(id);
    }
}
