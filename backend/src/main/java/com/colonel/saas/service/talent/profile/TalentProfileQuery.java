package com.colonel.saas.service.talent.profile;

import com.colonel.saas.service.talent.TalentInputParseResult;
import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class TalentProfileQuery {

    private String input;
    private boolean forceRefresh;
    private UUID talentId;
    private boolean manualFill;
    private TalentInputParseResult parsed;
    private Map<String, Object> manualPayload;
}
