package com.colonel.saas.service.talent.profile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.dto.talent.ResolveTalentProfileResponse;
import com.colonel.saas.dto.talent.TalentProfilePayload;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentProfileSyncLog;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.mapper.TalentProfileSyncLogMapper;
import com.colonel.saas.service.TalentService;
import com.colonel.saas.service.talent.TalentInputParseResult;
import com.colonel.saas.service.talent.TalentInputParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class TalentProfileSyncService {

    private static final String DATA_SOURCE_MANUAL = "manual";
    private static final String PRIOR_SUCCESS = TalentProfileResult.STATUS_SUCCESS;
    private static final String PRIOR_PARTIAL = TalentProfileResult.STATUS_PARTIAL_SUCCESS;

    private final List<TalentProfileProvider> providers;
    private final TalentMapper talentMapper;
    private final TalentProfileSyncLogMapper syncLogMapper;
    private final TalentService talentService;

    public TalentProfileSyncService(
            List<TalentProfileProvider> providers,
            TalentMapper talentMapper,
            TalentProfileSyncLogMapper syncLogMapper,
            TalentService talentService) {
        this.providers = providers.stream()
                .sorted(Comparator.comparingInt(TalentProfileProvider::order))
                .toList();
        this.talentMapper = talentMapper;
        this.syncLogMapper = syncLogMapper;
        this.talentService = talentService;
    }

    @Transactional(rollbackFor = Exception.class)
    public ResolveTalentProfileResponse resolveProfile(
            String input,
            boolean forceRefresh,
            boolean manualFill,
            java.util.Map<String, Object> manualPayload) {
        TalentInputParseResult parsed = TalentInputParser.parse(input);
        TalentProfileQuery query = TalentProfileQuery.builder()
                .input(input.trim())
                .forceRefresh(forceRefresh)
                .manualFill(manualFill)
                .manualPayload(manualPayload)
                .parsed(parsed)
                .build();

        Talent existing = findExistingByParsed(parsed);
        if (existing != null) {
            query.setTalentId(existing.getId());
            if (!forceRefresh && isPriorSuccessful(existing)) {
                return toResponse(existing, existing.getDataSource(), true);
            }
        }

        SyncAttempt attempt = syncWithProviders(query);
        writeSyncLog(query, attempt);

        if (existing != null) {
            if (attempt.result().isSuccess()) {
                applySuccess(existing, attempt.result(), attempt.result().getProviderCode());
                talentMapper.updateById(existing);
                return toResponse(existing, existing.getDataSource(), true);
            }
            if (!isPriorSuccessful(existing)) {
                applyFailure(existing, attempt.result());
                talentMapper.updateById(existing);
            }
            return toResponse(existing, existing.getDataSource(), existing.getRawPayload() != null);
        }

        return toResponse(attempt.result(), attempt.result().getProviderCode(), attempt.result().getRawPayload() != null);
    }

    @Transactional(rollbackFor = Exception.class)
    public ResolveTalentProfileResponse syncExistingProfile(UUID talentId, boolean forceRefresh) {
        Talent talent = talentService.getById(talentId);
        if (!forceRefresh && isPriorSuccessful(talent)) {
            return toResponse(talent, talent.getDataSource(), true);
        }
        String input = resolveInputValue(talent);
        TalentInputParseResult parsed = TalentInputParser.parse(input);
        TalentProfileQuery query = TalentProfileQuery.builder()
                .input(input)
                .forceRefresh(forceRefresh)
                .talentId(talentId)
                .parsed(parsed)
                .build();

        SyncAttempt attempt = syncWithProviders(query);
        writeSyncLog(query, attempt);

        if (attempt.result().isSuccess()) {
            applySuccess(talent, attempt.result(), attempt.result().getProviderCode());
            talentMapper.updateById(talent);
            return toResponse(talent, talent.getDataSource(), true);
        }

        if (!isPriorSuccessful(talent)) {
            applyFailure(talent, attempt.result());
            talentMapper.updateById(talent);
        }
        return toResponse(talent, talent.getDataSource(), talent.getRawPayload() != null);
    }

    @Transactional(rollbackFor = Exception.class)
    public ResolveTalentProfileResponse applyManualProfile(UUID talentId, java.util.Map<String, Object> manualPayload) {
        Talent talent = talentService.getById(talentId);
        String input = resolveInputValue(talent);
        TalentProfileQuery query = TalentProfileQuery.builder()
                .input(input)
                .talentId(talentId)
                .manualFill(true)
                .manualPayload(manualPayload)
                .parsed(TalentInputParser.parse(input))
                .build();
        SyncAttempt attempt = syncWithProviders(query);
        writeSyncLog(query, attempt);
        if (attempt.result().isSuccess()) {
            applySuccess(talent, attempt.result(), DATA_SOURCE_MANUAL);
            talent.setDataSource(DATA_SOURCE_MANUAL);
            talentMapper.updateById(talent);
        } else {
            applyFailure(talent, attempt.result());
            talentMapper.updateById(talent);
        }
        return toResponse(talent, talent.getDataSource(), talent.getRawPayload() != null);
    }

    private SyncAttempt syncWithProviders(TalentProfileQuery query) {
        TalentProfileResult lastFailed = null;
        for (TalentProfileProvider provider : providers) {
            if (!provider.supports(query)) {
                continue;
            }
            TalentProfileResult result = provider.fetch(query);
            if (result != null && result.isSuccess() && result.hasRealProfileData()) {
                return new SyncAttempt(result, provider.providerCode());
            }
            if (result != null) {
                lastFailed = result;
            }
        }
        if (lastFailed != null) {
            return new SyncAttempt(lastFailed, lastFailed.getProviderCode());
        }
        return new SyncAttempt(TalentProfileResult.builder()
                .success(false)
                .syncStatus(TalentProfileResult.STATUS_FAILED)
                .errorCode("NO_PROVIDER")
                .errorMessage("no profile provider returned data")
                .unsupportedFields(TalentProfileResult.DEFAULT_UNSUPPORTED)
                .build(), null);
    }

    private void writeSyncLog(TalentProfileQuery query, SyncAttempt attempt) {
        LocalDateTime now = LocalDateTime.now();
        TalentProfileSyncLog log = new TalentProfileSyncLog();
        log.setId(UUID.randomUUID());
        log.setTalentId(query.getTalentId());
        log.setInputValue(query.getInput());
        log.setProviderCode(attempt.providerCode());
        log.setSyncStatus(attempt.result().getSyncStatus());
        log.setFetchedFields(attempt.result().getFetchedFields());
        log.setUnsupportedFields(attempt.result().getUnsupportedFields());
        log.setRawPayload(attempt.result().getRawPayload());
        log.setErrorCode(attempt.result().getErrorCode());
        log.setErrorMessage(attempt.result().getErrorMessage());
        log.setStartedAt(now);
        log.setFinishedAt(now);
        syncLogMapper.insert(log);
    }

    private void applySuccess(Talent talent, TalentProfileResult result, String dataSource) {
        if (StringUtils.hasText(result.getDouyinAccount())) {
            talent.setDouyinAccount(result.getDouyinAccount());
            talent.setDouyinNo(result.getDouyinAccount());
        }
        if (StringUtils.hasText(result.getTalentUid())) {
            talent.setTalentUid(result.getTalentUid());
            talent.setUid(result.getTalentUid());
            if (!StringUtils.hasText(talent.getDouyinUid())) {
                talent.setDouyinUid(result.getTalentUid());
            }
        }
        if (StringUtils.hasText(result.getSecUid())) {
            talent.setSecUid(result.getSecUid());
        }
        if (StringUtils.hasText(result.getNickname())) {
            talent.setNickname(result.getNickname());
        }
        if (StringUtils.hasText(result.getAvatarUrl())) {
            talent.setAvatarUrl(result.getAvatarUrl());
        }
        if (result.getFansCount() != null) {
            talent.setFans(result.getFansCount());
        }
        if (result.getLikeCount() != null) {
            talent.setLikesCount(result.getLikeCount());
        }
        if (result.getFollowingCount() != null) {
            talent.setFollowingCount(result.getFollowingCount());
        }
        if (result.getWorksCount() != null) {
            talent.setWorksCount(result.getWorksCount());
        }
        if (StringUtils.hasText(result.getIpLocation())) {
            talent.setIpLocation(result.getIpLocation());
        }
        if (StringUtils.hasText(result.getTalentLevel())) {
            talent.setTalentLevel(result.getTalentLevel());
        } else {
            talent.setTalentLevel(null);
        }
        if (result.getSales30d() != null) {
            talent.setSales30d(result.getSales30d());
        } else {
            talent.setSales30d(null);
        }
        talent.setDataSource(dataSource);
        talent.setSyncStatus(result.getSyncStatus());
        talent.setLastSyncTime(LocalDateTime.now());
        talent.setSyncErrorCode(null);
        talent.setSyncErrorMessage(null);
        talent.setRawPayload(result.getRawPayload());
        talent.setUnsupportedFields(result.getUnsupportedFields());
    }

    private void applyFailure(Talent talent, TalentProfileResult result) {
        talent.setSyncStatus(TalentProfileResult.STATUS_FAILED);
        talent.setLastSyncTime(LocalDateTime.now());
        talent.setSyncErrorCode(result.getErrorCode());
        talent.setSyncErrorMessage(result.getErrorMessage());
        if (result.getRawPayload() != null && !result.getRawPayload().isEmpty()) {
            talent.setRawPayload(result.getRawPayload());
        }
        talent.setUnsupportedFields(result.getUnsupportedFields());
    }

    private Talent findExistingByParsed(TalentInputParseResult parsed) {
        if (parsed == null) {
            return null;
        }
        if (StringUtils.hasText(parsed.getDouyinUid())) {
            Talent byUid = talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                    .eq(Talent::getDouyinUid, parsed.getDouyinUid())
                    .last("limit 1"));
            if (byUid != null) {
                return byUid;
            }
        }
        if (StringUtils.hasText(parsed.getSecUid())) {
            return talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                    .eq(Talent::getSecUid, parsed.getSecUid())
                    .last("limit 1"));
        }
        if (StringUtils.hasText(parsed.getDouyinNo())) {
            return talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                    .eq(Talent::getDouyinNo, parsed.getDouyinNo())
                    .last("limit 1"));
        }
        return null;
    }

    private boolean isPriorSuccessful(Talent talent) {
        if (talent == null || !StringUtils.hasText(talent.getSyncStatus())) {
            return false;
        }
        String status = talent.getSyncStatus().trim().toLowerCase();
        return PRIOR_SUCCESS.equals(status) || PRIOR_PARTIAL.equals(status);
    }

    private String resolveInputValue(Talent talent) {
        if (StringUtils.hasText(talent.getProfileUrl())) {
            return talent.getProfileUrl().trim();
        }
        if (StringUtils.hasText(talent.getDouyinAccount())) {
            return talent.getDouyinAccount().trim();
        }
        if (StringUtils.hasText(talent.getDouyinNo())) {
            return talent.getDouyinNo().trim();
        }
        if (StringUtils.hasText(talent.getSecUid())) {
            return talent.getSecUid().trim();
        }
        if (StringUtils.hasText(talent.getTalentUid())) {
            return talent.getTalentUid().trim();
        }
        return talent.getDouyinUid();
    }

    private ResolveTalentProfileResponse toResponse(Talent talent, String provider, boolean rawSaved) {
        return ResolveTalentProfileResponse.builder()
                .success(isPriorSuccessful(talent))
                .provider(provider)
                .syncStatus(talent.getSyncStatus())
                .profile(buildPayload(talent))
                .unsupportedFields(talent.getUnsupportedFields())
                .rawPayloadSaved(rawSaved && talent.getRawPayload() != null)
                .dataSource(talent.getDataSource())
                .syncErrorCode(talent.getSyncErrorCode())
                .syncErrorMessage(talent.getSyncErrorMessage())
                .build();
    }

    private ResolveTalentProfileResponse toResponse(TalentProfileResult result, String provider, boolean rawSaved) {
        return ResolveTalentProfileResponse.builder()
                .success(result.isSuccess())
                .provider(provider)
                .syncStatus(result.getSyncStatus())
                .profile(buildPayload(result))
                .unsupportedFields(result.getUnsupportedFields())
                .rawPayloadSaved(rawSaved)
                .dataSource(result.isSuccess() ? provider : null)
                .syncErrorCode(result.getErrorCode())
                .syncErrorMessage(result.getErrorMessage())
                .build();
    }

    private TalentProfilePayload buildPayload(Talent talent) {
        return TalentProfilePayload.builder()
                .douyinAccount(talent.getDouyinAccount())
                .talentUid(talent.getTalentUid())
                .nickname(talent.getNickname())
                .avatarUrl(talent.getAvatarUrl())
                .fansCount(talent.getFans())
                .likeCount(talent.getLikesCount())
                .followingCount(talent.getFollowingCount())
                .worksCount(talent.getWorksCount())
                .ipLocation(talent.getIpLocation())
                .talentLevel(talent.getTalentLevel())
                .sales30d(talent.getSales30d())
                .build();
    }

    private TalentProfilePayload buildPayload(TalentProfileResult result) {
        return TalentProfilePayload.builder()
                .douyinAccount(result.getDouyinAccount())
                .talentUid(result.getTalentUid())
                .nickname(result.getNickname())
                .avatarUrl(result.getAvatarUrl())
                .fansCount(result.getFansCount())
                .likeCount(result.getLikeCount())
                .followingCount(result.getFollowingCount())
                .worksCount(result.getWorksCount())
                .ipLocation(result.getIpLocation())
                .talentLevel(result.getTalentLevel())
                .sales30d(result.getSales30d())
                .build();
    }

    private record SyncAttempt(TalentProfileResult result, String providerCode) {
    }
}
