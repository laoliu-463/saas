package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TalentService {

    private static final int PROTECT_DAYS = 7;
    private static final int PUBLIC_POOL_LIMIT = 500;
    private static final long EXCLUSIVE_SERVICE_FEE_RATIO_THRESHOLD = 70L;
    private static final long EXCLUSIVE_MONTHLY_SAMPLE_THRESHOLD = 10L;

    private final TalentMapper talentMapper;
    private final TalentClaimMapper talentClaimMapper;
    private final ColonelsettlementOrderMapper orderMapper;
    private final SampleRequestMapper sampleRequestMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public TalentService(
            TalentMapper talentMapper,
            TalentClaimMapper talentClaimMapper,
            ColonelsettlementOrderMapper orderMapper,
            SampleRequestMapper sampleRequestMapper,
            RedisTemplate<String, Object> redisTemplate) {
        this.talentMapper = talentMapper;
        this.talentClaimMapper = talentClaimMapper;
        this.orderMapper = orderMapper;
        this.sampleRequestMapper = sampleRequestMapper;
        this.redisTemplate = redisTemplate;
    }

    public List<Talent> getPublicPool() {
        Set<UUID> claimedTalentIds = getClaimedTalentIds();
        return talentMapper.selectList(new LambdaQueryWrapper<Talent>()
                        .eq(Talent::getDeleted, 0)
                        .eq(Talent::getStatus, 1))
                .stream()
                .filter(talent -> !claimedTalentIds.contains(talent.getId()))
                .sorted(Comparator.comparing(Talent::getFans, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(PUBLIC_POOL_LIMIT)
                .toList();
    }

    public List<Talent> getPrivatePool(UUID userId) {
        List<TalentClaim> claims = talentClaimMapper.findActiveByUserId(userId);
        if (claims.isEmpty()) {
            return List.of();
        }
        Set<UUID> talentIds = claims.stream().map(TalentClaim::getTalentId).collect(java.util.stream.Collectors.toSet());
        return talentMapper.selectBatchIds(talentIds).stream()
                .limit(PUBLIC_POOL_LIMIT)
                .toList();
    }

    public IPage<Talent> page(long page, long size, String keyword, DataScope dataScope, UUID userId, UUID deptId) {
        LambdaQueryWrapper<Talent> wrapper = new LambdaQueryWrapper<Talent>()
                .eq(Talent::getDeleted, 0)
                .orderByDesc(Talent::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Talent::getNickname, keyword).or().like(Talent::getDouyinUid, keyword));
        }

        if (dataScope == DataScope.PERSONAL && userId != null) {
            List<TalentClaim> claims = talentClaimMapper.findActiveByUserId(userId);
            Set<UUID> ids = claims.stream().map(TalentClaim::getTalentId).collect(java.util.stream.Collectors.toSet());
            if (ids.isEmpty()) {
                return new Page<>(page, size, 0L);
            }
            wrapper.in(Talent::getId, ids);
        } else if (dataScope == DataScope.DEPT && deptId != null) {
            List<TalentClaim> claims = talentClaimMapper.findActiveByDeptId(deptId);
            Set<UUID> ids = claims.stream().map(TalentClaim::getTalentId).collect(java.util.stream.Collectors.toSet());
            if (ids.isEmpty()) {
                return new Page<>(page, size, 0L);
            }
            wrapper.in(Talent::getId, ids);
        }
        return talentMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public Talent getById(UUID id) {
        Talent talent = talentMapper.selectById(id);
        if (talent == null || talent.getDeleted() != null && talent.getDeleted() == 1) {
            throw new BusinessException("达人不存在");
        }
        return talent;
    }

    @Transactional(rollbackFor = Exception.class)
    public Talent create(Talent request) {
        if (!StringUtils.hasText(request.getDouyinUid())) {
            throw new BusinessException("douyinUid 不能为空");
        }
        Talent existing = talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                .eq(Talent::getDouyinUid, request.getDouyinUid())
                .last("limit 1"));
        if (existing != null) {
            throw new BusinessException("达人 douyinUid 已存在");
        }
        request.setStatus(1);
        talentMapper.insert(request);
        return request;
    }

    @Transactional(rollbackFor = Exception.class)
    public Talent update(UUID id, Talent request) {
        Talent talent = getById(id);
        if (StringUtils.hasText(request.getNickname())) {
            talent.setNickname(request.getNickname());
        }
        if (request.getFans() != null) {
            talent.setFans(request.getFans());
        }
        if (StringUtils.hasText(request.getLevel())) {
            talent.setLevel(request.getLevel());
        }
        if (request.getStatus() != null) {
            talent.setStatus(request.getStatus());
        }
        talentMapper.updateById(talent);
        return talent;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id) {
        getById(id);
        talentMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Talent claim(UUID talentId, UUID userId, UUID deptId) {
        if (userId == null) {
            throw new BusinessException("缺少登录用户");
        }
        String lockKey = "talent:claim:lock:" + talentId;
        String lockValue = userId.toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                Objects.requireNonNull(lockKey),
                Objects.requireNonNull(lockValue),
                10,
                TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException("达人认领处理中，请稍后重试");
        }
        try {
            Talent talent = getById(talentId);

            LocalDateTime now = LocalDateTime.now();
            TalentClaim lastClaim = talentClaimMapper.findLastClaim(talentId);
            if (lastClaim != null && lastClaim.getUserId() != null && !userId.equals(lastClaim.getUserId())) {
                LocalDateTime protectedUntil = lastClaim.getProtectedUntil() == null
                        ? lastClaim.getClaimedAt().plusDays(PROTECT_DAYS)
                        : lastClaim.getProtectedUntil();
                if (protectedUntil != null && protectedUntil.isAfter(now)) {
                    throw new BusinessException("达人处于保护期，暂不可认领");
                }
            }

            TalentClaim claim = new TalentClaim();
            claim.setTalentId(talentId);
            claim.setTalentUid(talent.getDouyinUid());
            claim.setUserId(userId);
            claim.setDeptId(deptId);
            claim.setClaimedAt(now);
            claim.setProtectedUntil(now.plusDays(PROTECT_DAYS));
            claim.setStatus(1);
            talentClaimMapper.insert(claim);

            talent.setOwnerId(userId);
            talent.setClaimedAt(now);
            return talent;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    public ExclusiveCheckResult evaluateExclusive(UUID talentId, DataScope dataScope, UUID userId, UUID deptId) {
        Talent talent = getById(talentId);
        LocalDateTime start = LocalDateTime.now().minusDays(30);
        LambdaQueryWrapper<com.colonel.saas.entity.ColonelsettlementOrder> wrapper =
                new LambdaQueryWrapper<com.colonel.saas.entity.ColonelsettlementOrder>()
                        .ge(com.colonel.saas.entity.ColonelsettlementOrder::getCreateTime, start);
        if (dataScope == DataScope.PERSONAL && userId != null) {
            wrapper.eq(com.colonel.saas.entity.ColonelsettlementOrder::getUserId, userId);
        } else if (dataScope == DataScope.DEPT && deptId != null) {
            wrapper.eq(com.colonel.saas.entity.ColonelsettlementOrder::getDeptId, deptId);
        }
        List<com.colonel.saas.entity.ColonelsettlementOrder> monthOrders = orderMapper.selectList(wrapper);

        long totalServiceFee = 0L;
        long talentServiceFee = 0L;
        for (com.colonel.saas.entity.ColonelsettlementOrder order : monthOrders) {
            long serviceFee = order.getSettleColonelCommission() == null ? 0L : order.getSettleColonelCommission();
            totalServiceFee += serviceFee;
            if (matchesTalent(order, talent.getDouyinUid())) {
                talentServiceFee += serviceFee;
            }
        }
        long serviceRatio = totalServiceFee == 0 ? 0 : (talentServiceFee * 100 / totalServiceFee);
        Long sampleCount = sampleRequestMapper.selectCount(new LambdaQueryWrapper<com.colonel.saas.entity.SampleRequest>()
                .eq(com.colonel.saas.entity.SampleRequest::getTalentId, talentId)
                .ge(com.colonel.saas.entity.SampleRequest::getCreateTime, start));
        long monthlySamples = sampleCount == null ? 0L : sampleCount;

        boolean eligible = serviceRatio >= EXCLUSIVE_SERVICE_FEE_RATIO_THRESHOLD
                && monthlySamples >= EXCLUSIVE_MONTHLY_SAMPLE_THRESHOLD;
        return new ExclusiveCheckResult(eligible, serviceRatio, monthlySamples);
    }

    private boolean matchesTalent(com.colonel.saas.entity.ColonelsettlementOrder order, String douyinUid) {
        if (!StringUtils.hasText(douyinUid)) {
            return false;
        }
        if (order.getExtraData() == null) {
            return false;
        }
        Object authorId = order.getExtraData().get("author_id");
        if (authorId != null && douyinUid.equals(String.valueOf(authorId))) {
            return true;
        }
        Object talentUid = order.getExtraData().get("talent_uid");
        return talentUid != null && douyinUid.equals(String.valueOf(talentUid));
    }

    private Set<UUID> getClaimedTalentIds() {
        List<TalentClaim> claims = talentClaimMapper.selectList(new LambdaQueryWrapper<TalentClaim>()
                .eq(TalentClaim::getStatus, 1)
                .eq(TalentClaim::getDeleted, 0));
        if (claims.isEmpty()) {
            return Collections.emptySet();
        }
        Set<UUID> ids = new HashSet<>();
        for (TalentClaim claim : claims) {
            if (claim.getTalentId() != null) {
                ids.add(claim.getTalentId());
            }
        }
        return ids;
    }

    public record ExclusiveCheckResult(boolean eligible, long serviceFeeRatio, long monthlySamples) {
    }
}
