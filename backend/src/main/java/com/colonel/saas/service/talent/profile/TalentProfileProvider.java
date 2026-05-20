package com.colonel.saas.service.talent.profile;

public interface TalentProfileProvider {

    String providerCode();

    int order();

    boolean supports(TalentProfileQuery query);

    TalentProfileResult fetch(TalentProfileQuery query);
}
