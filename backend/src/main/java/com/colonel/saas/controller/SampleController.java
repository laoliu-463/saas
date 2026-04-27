package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.ValidateException;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.SampleApplyRequest;
import com.colonel.saas.dto.SampleTalentQueryRequest;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.mapper.ProductMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.service.CrawlerTalentInfoService;
import com.colonel.saas.service.SampleStatusLogService;
import com.colonel.saas.vo.SampleTalentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Validated
@Tag(name = "寄样管理")
@RestController
@RequestMapping("/samples")
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF, RoleCodes.OPS_STAFF})
public class SampleController extends BaseController {

    private static final DateTimeFormatter REQUEST_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SampleRequestMapper sampleRequestMapper;
    private final ProductMapper productMapper;
    private final ProductOperationStateMapper productOperationStateMapper;
    private final SysUserMapper sysUserMapper;
    private final TalentMapper talentMapper;
    private final SampleStatusLogService sampleStatusLogService;
    private final CrawlerTalentInfoService crawlerTalentInfoService;

    public SampleController(
            SampleRequestMapper sampleRequestMapper,
            ProductMapper productMapper,
            ProductOperationStateMapper productOperationStateMapper,
            SysUserMapper sysUserMapper,
            TalentMapper talentMapper,
            SampleStatusLogService sampleStatusLogService,
            CrawlerTalentInfoService crawlerTalentInfoService) {
        this.sampleRequestMapper = sampleRequestMapper;
        this.productMapper = productMapper;
        this.productOperationStateMapper = productOperationStateMapper;
        this.sysUserMapper = sysUserMapper;
        this.talentMapper = talentMapper;
        this.sampleStatusLogService = sampleStatusLogService;
        this.crawlerTalentInfoService = crawlerTalentInfoService;
    }

    @Operation(summary = "创建寄样申请")
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<SampleVO> createSample(
            @Valid @RequestBody SampleApplyRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        Product product = requireProduct(request.getProductId());
        CrawlerTalentInfo talentInfo = requireCrawlerTalent(request.getTalentId());
        Talent talent = findOrCreateTalentFromCrawler(talentInfo);
        checkSevenDaysLimit(userId, talent.getId(), request.getProductId(), roleCodes);

        SampleRequest sample = new SampleRequest();
        sample.setRequestNo(generateRequestNo());
        sample.setTalentId(talent.getId());
        sample.setTalentUid(talentInfo.getTalentId());
        sample.setTalentNickname(StringUtils.hasText(request.getTalentNickname()) ? request.getTalentNickname() : talentInfo.getNickname());
        sample.setTalentFansCount(request.getTalentFansCount() != null ? request.getTalentFansCount() : talentInfo.getFansCount());
        sample.setTalentCreditScore(request.getTalentCreditScore() != null ? request.getTalentCreditScore() : talentInfo.getCreditScore());
        sample.setTalentMainCategory(StringUtils.hasText(request.getTalentMainCategory()) ? request.getTalentMainCategory() : talentInfo.getMainCategory());
        sample.setProductId(request.getProductId());
        sample.setUserId(userId);
        sample.setChannelUserId(userId);
        sample.setExpectedSampleNum(request.getQuantity());
        sample.setActualSampleNum(0);
        sample.setStatus(SampleStatus.PENDING_AUDIT.code);
        sample.setRemark(request.getRemark());
        sampleRequestMapper.insert(sample);
        sampleStatusLogService.log(sample.getId(), null, sample.getStatus(), userId, "create sample request");

        return ok(toVO(sample, product.getName(), sample.getTalentNickname()));
    }

    @Operation(summary = "寄样分页")
    @GetMapping
    public ApiResult<PageResult<SampleVO>> getSamplePage(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(required = false) String status,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        Page<SampleRequest> pageReq = new Page<>(page, size);
        QueryWrapper<SampleRequest> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq("status", parseStatus(status).code);
        }

        IPage<SampleRequest> samplePage = sampleRequestMapper.findPageWithScope(pageReq, wrapper);
        Map<UUID, Product> productMap = loadProducts(samplePage.getRecords().stream()
                .map(SampleRequest::getProductId)
                .collect(Collectors.toSet()));

        List<SampleVO> records = samplePage.getRecords().stream()
                .map(item -> toVO(
                        item,
                        productMap.get(item.getProductId()) == null ? null : productMap.get(item.getProductId()).getName(),
                        item.getTalentNickname()))
                .toList();

        Page<SampleVO> voPage = new Page<>(samplePage.getCurrent(), samplePage.getSize(), samplePage.getTotal());
        voPage.setRecords(records);
        return okPage(voPage);
    }

    @Operation(summary = "寄样达人搜索")
    @GetMapping("/talent-candidates")
    public ApiResult<PageResult<SampleTalentVO>> searchTalents(@Valid SampleTalentQueryRequest request) {
        IPage<SampleTalentVO> page = crawlerTalentInfoService.searchTalents(
                request.getKeyword(),
                request.getRegion(),
                request.getMinFans(),
                request.getMaxFans(),
                request.getMinScore(),
                request.getPage(),
                request.getSize()
        );
        return okPage(page);
    }

    @Operation(summary = "寄样详情")
    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}")
    public ApiResult<SampleVO> getSampleById(@PathVariable UUID id) {
        SampleRequest sample = requireSample(id);
        Product product = productMapper.selectById(sample.getProductId());
        return ok(toVO(
                sample,
                product == null ? null : product.getName(),
                sample.getTalentNickname()));
    }

    @Operation(summary = "寄样状态流转")
    @PutMapping("/{id:[0-9a-fA-F\\-]{36}}/status")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<SampleVO> actionSample(
            @PathVariable UUID id,
            @Valid @RequestBody SampleActionRequest request,
            @RequestAttribute("userId") UUID userId) {
        SampleRequest sample = requireSample(id);
        LocalDateTime now = LocalDateTime.now();
        int fromStatus = sample.getStatus();
        SampleStatus current = SampleStatus.fromCode(fromStatus);
        String action = normalizeAction(request.getAction());

        if ("PENDING_SHIP".equals(action)) {
            ensureTransition(current, SampleStatus.PENDING_AUDIT);
            sample.setStatus(SampleStatus.PENDING_SHIP.code);
            sample.setAuditTime(now);
        } else if ("REJECTED".equals(action)) {
            ensureTransition(current, SampleStatus.PENDING_AUDIT);
            if (!StringUtils.hasText(request.getReason())) {
                throw new BusinessException("reason is required when reject sample request");
            }
            sample.setStatus(SampleStatus.REJECTED.code);
            sample.setRejectReason(request.getReason());
            sample.setAuditTime(now);
        } else if ("SHIPPING".equals(action)) {
            ensureTransition(current, SampleStatus.PENDING_SHIP);
            if (!StringUtils.hasText(request.getTrackingNo())) {
                throw new BusinessException("trackingNo is required when shipping");
            }
            sample.setStatus(SampleStatus.SHIPPING.code);
            sample.setTrackingNo(request.getTrackingNo());
            sample.setShipTime(now);
        } else if ("DELIVERED".equals(action)) {
            ensureTransition(current, SampleStatus.SHIPPING);
            sample.setStatus(SampleStatus.DELIVERED.code);
            sample.setDeliverTime(now);
        } else if ("PENDING_HOMEWORK".equals(action)) {
            ensureTransition(current, SampleStatus.DELIVERED);
            sample.setStatus(SampleStatus.PENDING_HOMEWORK.code);
        } else if ("COMPLETED".equals(action)) {
            ensureTransition(current, SampleStatus.PENDING_HOMEWORK);
            sample.setStatus(SampleStatus.COMPLETED.code);
            sample.setCompleteTime(now);
        } else if ("CLOSED".equals(action)) {
            sample.setStatus(SampleStatus.CLOSED.code);
            sample.setCloseTime(now);
            sample.setCloseReason(request.getReason());
        } else {
            throw new BusinessException("Unsupported action: " + request.getAction());
        }

        sampleRequestMapper.updateById(sample);
        sampleStatusLogService.log(sample.getId(), fromStatus, sample.getStatus(), userId, request.getReason());
        Product product = productMapper.selectById(sample.getProductId());
        return ok(toVO(
                sample,
                product == null ? null : product.getName(),
                sample.getTalentNickname()));
    }

    @Operation(summary = "删除寄样")
    @DeleteMapping("/{id:[0-9a-fA-F\\-]{36}}")
    public ApiResult<Void> deleteSample(@PathVariable UUID id) {
        SampleRequest sample = requireSample(id);
        SampleStatus status = SampleStatus.fromCode(sample.getStatus());
        if (status != SampleStatus.PENDING_AUDIT && status != SampleStatus.REJECTED) {
            throw new BusinessException("Only pending/rejected sample can be deleted");
        }
        sampleRequestMapper.deleteById(id);
        return ok();
    }

    private void ensureTransition(SampleStatus current, SampleStatus expected) {
        if (current != expected) {
            throw new BusinessException("Current status does not allow this action: " + current.apiStatus);
        }
    }

    private SampleRequest requireSample(UUID id) {
        SampleRequest sample = sampleRequestMapper.selectById(id);
        if (sample == null) {
            throw new BusinessException("Sample request not found");
        }
        return sample;
    }

    private Product requireProduct(UUID productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new ValidateException("Selected product does not exist");
        }
        return product;
    }

    private CrawlerTalentInfo requireCrawlerTalent(String talentId) {
        CrawlerTalentInfo talentInfo = crawlerTalentInfoService.findByTalentId(talentId);
        if (talentInfo == null) {
            throw new ValidateException("Selected talent does not exist");
        }
        return talentInfo;
    }

    private Talent findOrCreateTalentFromCrawler(CrawlerTalentInfo info) {
        Talent existing = talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                .eq(Talent::getDouyinUid, info.getTalentId())
                .last("limit 1"));
        if (existing != null) {
            return existing;
        }
        Talent talent = new Talent();
        talent.setDouyinUid(info.getTalentId());
        talent.setNickname(info.getNickname());
        talent.setFans(info.getFansCount());
        talent.setStatus(1);
        talentMapper.insert(talent);
        return talent;
    }

    private void checkSevenDaysLimit(UUID userId, UUID talentId, UUID productId, Object roleCodes) {
        if (isExemptFromSevenDaysLimit(roleCodes)) {
            return;
        }
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        Long count = sampleRequestMapper.selectCount(new LambdaQueryWrapper<SampleRequest>()
                .eq(SampleRequest::getChannelUserId, userId)
                .eq(SampleRequest::getTalentId, talentId)
                .eq(SampleRequest::getProductId, productId)
                .ne(SampleRequest::getStatus, SampleStatus.REJECTED.code)
                .ge(SampleRequest::getCreateTime, sevenDaysAgo));
        if (count != null && count > 0) {
            throw new BusinessException("Duplicate sample request is blocked within 7 days");
        }
    }

    private boolean isExemptFromSevenDaysLimit(Object roleCodes) {
        if (roleCodes == null) {
            return false;
        }
        if (roleCodes instanceof Collection<?> collection) {
            return collection.stream()
                    .map(item -> item == null ? "" : item.toString().trim().toLowerCase(Locale.ROOT))
                    .anyMatch(this::isExemptRoleCode);
        }
        String raw = roleCodes.toString();
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        for (String role : normalized.split(",")) {
            if (isExemptRoleCode(role.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean isExemptRoleCode(String roleCode) {
        return RoleCodes.ADMIN.equals(roleCode)
                || RoleCodes.BIZ_LEADER.equals(roleCode)
                || RoleCodes.CHANNEL_LEADER.equals(roleCode);
    }

    private String generateRequestNo() {
        String date = LocalDateTime.now().format(REQUEST_NO_DATE);
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "SM" + date + unique;
    }

    private Map<UUID, Product> loadProducts(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<Product> products = productMapper.selectBatchIds(ids);
        Map<UUID, Product> map = new HashMap<>();
        for (Product product : products) {
            map.put(product.getId(), product);
        }
        return map;
    }

    private SampleVO toVO(SampleRequest sample, String productName, String talentName) {
        Product product = sample.getProductId() == null ? null : productMapper.selectById(sample.getProductId());
        UUID colonelUserId = resolveColonelUserId(product);
        SampleVO vo = new SampleVO();
        vo.setId(sample.getId());
        vo.setRequestNo(sample.getRequestNo());
        vo.setTalentId(sample.getTalentId());
        vo.setTalentName(StringUtils.hasText(talentName) ? talentName : sample.getTalentNickname());
        vo.setProductId(sample.getProductId());
        vo.setProductName(productName);
        vo.setQuantity(sample.getExpectedSampleNum() == null ? 1 : sample.getExpectedSampleNum());
        vo.setChannelUserId(sample.getChannelUserId());
        vo.setChannelUserName(resolveUserDisplayName(sample.getChannelUserId()));
        vo.setColonelUserId(colonelUserId);
        vo.setColonelUserName(resolveUserDisplayName(colonelUserId));
        vo.setTrackingNo(sample.getTrackingNo());
        vo.setRejectReason(sample.getRejectReason());
        vo.setCloseReason(sample.getCloseReason());
        vo.setRemark(sample.getRemark());
        vo.setCreateTime(sample.getCreateTime());
        vo.setUpdateTime(sample.getUpdateTime());
        vo.setCompleteTime(sample.getCompleteTime());
        vo.setStatus(toLegacyStatus(SampleStatus.fromCode(sample.getStatus())));
        return vo;
    }

    private UUID resolveColonelUserId(Product product) {
        if (product == null || !StringUtils.hasText(product.getProductId()) || product.getActivityId() == null) {
            return null;
        }
        ProductOperationState state = productOperationStateMapper.selectOne(new LambdaQueryWrapper<ProductOperationState>()
                .eq(ProductOperationState::getActivityId, String.valueOf(product.getActivityId()))
                .eq(ProductOperationState::getProductId, product.getProductId())
                .last("limit 1"));
        return state == null ? null : state.getAssigneeId();
    }

    private String resolveUserDisplayName(UUID userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        String realName = normalizeDisplayText(user.getRealName());
        String username = normalizeDisplayText(user.getUsername());
        if (StringUtils.hasText(realName) && StringUtils.hasText(username)) {
            return realName + " (" + username + ")";
        }
        if (StringUtils.hasText(realName)) {
            return realName;
        }
        if (StringUtils.hasText(username)) {
            return username;
        }
        return null;
    }

    private String normalizeDisplayText(String value) {
        return value == null ? null : value.trim();
    }

    private SampleStatus parseStatus(String status) {
        try {
            return SampleStatus.fromApiStatus(status.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new BusinessException("Invalid status: " + status);
        }
    }

    private String normalizeAction(String action) {
        String normalized = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "APPROVED" -> "PENDING_SHIP";
            case "SHIPPED" -> "SHIPPING";
            case "PENDING_TASK" -> "PENDING_HOMEWORK";
            case "FINISHED" -> "COMPLETED";
            default -> normalized;
        };
    }

    private String toLegacyStatus(SampleStatus status) {
        return switch (status) {
            case SHIPPING, DELIVERED -> "SHIPPED";
            case PENDING_HOMEWORK -> "PENDING_TASK";
            case COMPLETED -> "FINISHED";
            default -> status.apiStatus;
        };
    }

    private enum SampleStatus {
        PENDING_AUDIT(1, "PENDING_AUDIT"),
        PENDING_SHIP(2, "PENDING_SHIP"),
        SHIPPING(3, "SHIPPING"),
        DELIVERED(4, "DELIVERED"),
        PENDING_HOMEWORK(5, "PENDING_HOMEWORK"),
        COMPLETED(6, "COMPLETED"),
        REJECTED(7, "REJECTED"),
        CLOSED(8, "CLOSED");

        private final Integer code;
        private final String apiStatus;

        SampleStatus(Integer code, String apiStatus) {
            this.code = code;
            this.apiStatus = apiStatus;
        }

        static SampleStatus fromCode(Integer code) {
            for (SampleStatus status : values()) {
                if (status.code.equals(code)) {
                    return status;
                }
            }
            throw new BusinessException("Unknown sample status: " + code);
        }

        static SampleStatus fromApiStatus(String status) {
            return switch (status) {
                case "PENDING_TASK" -> PENDING_HOMEWORK;
                case "SHIPPED" -> SHIPPING;
                case "FINISHED" -> COMPLETED;
                default -> SampleStatus.valueOf(status);
            };
        }
    }

    public static class SampleActionRequest {
        @NotBlank(message = "action cannot be empty")
        private String action;
        private String reason;
        private String trackingNo;

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public String getTrackingNo() {
            return trackingNo;
        }

        public void setTrackingNo(String trackingNo) {
            this.trackingNo = trackingNo;
        }
    }

    public static class SampleVO {
        private UUID id;
        private String requestNo;
        private UUID talentId;
        private String talentName;
        private UUID productId;
        private String productName;
        private Integer quantity;
        private UUID channelUserId;
        private String channelUserName;
        private UUID colonelUserId;
        private String colonelUserName;
        private String trackingNo;
        private String rejectReason;
        private String closeReason;
        private String remark;
        private String status;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private LocalDateTime completeTime;

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getRequestNo() {
            return requestNo;
        }

        public void setRequestNo(String requestNo) {
            this.requestNo = requestNo;
        }

        public UUID getTalentId() {
            return talentId;
        }

        public void setTalentId(UUID talentId) {
            this.talentId = talentId;
        }

        public String getTalentName() {
            return talentName;
        }

        public void setTalentName(String talentName) {
            this.talentName = talentName;
        }

        public UUID getProductId() {
            return productId;
        }

        public void setProductId(UUID productId) {
            this.productId = productId;
        }

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public UUID getChannelUserId() {
            return channelUserId;
        }

        public void setChannelUserId(UUID channelUserId) {
            this.channelUserId = channelUserId;
        }

        public String getChannelUserName() {
            return channelUserName;
        }

        public void setChannelUserName(String channelUserName) {
            this.channelUserName = channelUserName;
        }

        public UUID getColonelUserId() {
            return colonelUserId;
        }

        public void setColonelUserId(UUID colonelUserId) {
            this.colonelUserId = colonelUserId;
        }

        public String getColonelUserName() {
            return colonelUserName;
        }

        public void setColonelUserName(String colonelUserName) {
            this.colonelUserName = colonelUserName;
        }

        public String getTrackingNo() {
            return trackingNo;
        }

        public void setTrackingNo(String trackingNo) {
            this.trackingNo = trackingNo;
        }

        public String getRejectReason() {
            return rejectReason;
        }

        public void setRejectReason(String rejectReason) {
            this.rejectReason = rejectReason;
        }

        public String getCloseReason() {
            return closeReason;
        }

        public void setCloseReason(String closeReason) {
            this.closeReason = closeReason;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }

        public LocalDateTime getUpdateTime() {
            return updateTime;
        }

        public void setUpdateTime(LocalDateTime updateTime) {
            this.updateTime = updateTime;
        }

        public LocalDateTime getCompleteTime() {
            return completeTime;
        }

        public void setCompleteTime(LocalDateTime completeTime) {
            this.completeTime = completeTime;
        }
    }
}
