package com.colonel.saas.service.talent;

import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentFieldSource;
import com.colonel.saas.mapper.TalentFieldSourceMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class TalentEnrichOrchestrator {

    private final List<TalentDataProvider> providers;
    private final TalentFieldSourceMapper talentFieldSourceMapper;

    public TalentEnrichOrchestrator(List<TalentDataProvider> providers, TalentFieldSourceMapper talentFieldSourceMapper) {
        this.providers = providers.stream()
                .sorted(Comparator.comparingInt(TalentDataProvider::priority))
                .toList();
        this.talentFieldSourceMapper = talentFieldSourceMapper;
    }

    public OrchestrateResult enrich(Talent talent, boolean forceRefresh) {
        if (talent == null) {
            return OrchestrateResult.noData("talent is null");
        }
        TalentEnrichContext context = new TalentEnrichContext(talent, forceRefresh);
        for (TalentDataProvider provider : providers) {
            if (!provider.supports(context)) {
                continue;
            }
            TalentEnrichResult result = provider.enrich(context);
            if (result == null || !result.hasFields()) {
                continue;
            }
            applyFields(talent, result.fields());
            talent.setDataSource(result.source().name());
            talent.setEnrichStatus("SUCCESS");
            talent.setLastEnrichTime(LocalDateTime.now());
            recordFieldSources(talent.getId(), result.source().name(), result.fields());
            return OrchestrateResult.updated(result.source().name(), result.message());
        }
        talent.setEnrichStatus("NO_DATA");
        talent.setLastEnrichTime(LocalDateTime.now());
        return OrchestrateResult.noData("no provider returned data");
    }

    private void applyFields(Talent talent, Map<String, Object> fields) {
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            switch (key) {
                case "nickname" -> talent.setNickname(String.valueOf(value));
                case "avatarUrl" -> talent.setAvatarUrl(String.valueOf(value));
                case "fans" -> talent.setFans(toLong(value));
                case "likesCount" -> talent.setLikesCount(toLong(value));
                case "followingCount" -> talent.setFollowingCount(toLong(value));
                case "worksCount" -> talent.setWorksCount(toLong(value));
                case "ipLocation" -> talent.setIpLocation(String.valueOf(value));
                default -> {
                    // ignore unsupported field keys
                }
            }
        }
    }

    private void recordFieldSources(UUID talentId, String sourceType, Map<String, Object> fields) {
        if (talentId == null || !StringUtils.hasText(sourceType)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            TalentFieldSource source = new TalentFieldSource();
            source.setTalentId(talentId);
            source.setFieldName(entry.getKey());
            source.setSourceType(sourceType);
            source.setSourceValue(String.valueOf(entry.getValue()));
            source.setVerifiedTime(now);
            talentFieldSourceMapper.insert(source);
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public record OrchestrateResult(boolean updated, String sourceType, String message) {
        static OrchestrateResult updated(String sourceType, String message) {
            return new OrchestrateResult(true, sourceType, message);
        }

        static OrchestrateResult noData(String message) {
            return new OrchestrateResult(false, null, message);
        }
    }
}
