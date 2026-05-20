package com.colonel.saas.dto.talent;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ResolveTalentProfileResponse {

    private boolean success;
    private String provider;
    private String syncStatus;
    private TalentProfilePayload profile;
    private List<String> unsupportedFields;
    private boolean rawPayloadSaved;
    private String dataSource;
    private String syncErrorCode;
    private String syncErrorMessage;
}
