package com.colonel.saas.service.talent;

import com.colonel.saas.common.enums.TalentInputType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TalentInputParseResult {
    private TalentInputType inputType;
    private String rawInput;
    private String douyinNo;
    private String uid;
    private String secUid;
    private String profileUrl;
    private String douyinUid;
}

