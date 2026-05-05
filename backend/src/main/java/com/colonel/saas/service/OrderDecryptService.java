package com.colonel.saas.service;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.gateway.douyin.DouyinOrderGateway;
import com.colonel.saas.entity.OperationLog;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderDecryptService {

    private static final int MAX_BATCH_SIZE = 50;

    private final DouyinOrderGateway douyinOrderGateway;
    private final OperationLogService operationLogService;

    public OrderDecryptService(
            DouyinOrderGateway douyinOrderGateway,
            OperationLogService operationLogService) {
        this.douyinOrderGateway = douyinOrderGateway;
        this.operationLogService = operationLogService;
    }

    public List<DecryptPhoneVO> decryptPhones(List<String> orderIds) {
        return decryptPhones(orderIds, null, null);
    }

    public List<DecryptPhoneVO> decryptPhones(List<String> orderIds, UUID operatorId, String username) {
        List<String> normalizedOrderIds = normalizeOrderIds(orderIds);
        Map<String, Object> response;
        try {
            response = douyinOrderGateway.decryptSensitiveData(normalizedOrderIds);
        } catch (DouyinApiException ex) {
            recordDecryptAudit(operatorId, username, normalizedOrderIds, false, ex.getErrorMsg());
            if (isCipherInfoRequired(ex)) {
                throw new BusinessException("当前抖店解密接口已升级为 order.batchSensitive，需传 cipher_infos（加密联系方式）而非 orderIds");
            }
            throw ex;
        }
        List<Map<String, Object>> rows = extractRows(response);

        long nowEpochSeconds = Instant.now().getEpochSecond();
        List<DecryptPhoneVO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            DecryptPhoneVO vo = new DecryptPhoneVO();
            vo.setOrderId(asString(row.get("order_id")));
            boolean virtualTel = asBoolean(row.get("is_virtual_tel"));
            vo.setVirtualTel(virtualTel);
            if (virtualTel) {
                Long expireEpoch = asLongObject(row.get("expire_time"));
                vo.setExpireTime(toDateTime(expireEpoch));
                boolean expired = expireEpoch != null && expireEpoch <= nowEpochSeconds;
                vo.setExpired(expired);
                if (!expired) {
                    vo.setPhoneNoA(asString(row.get("phone_no_a")));
                    vo.setPhoneNoB(asString(row.get("phone_no_b")));
                }
            } else {
                vo.setPhone(asString(row.get("phone")));
                vo.setExpired(false);
            }
            result.add(vo);
        }
        recordDecryptAudit(operatorId, username, normalizedOrderIds, true, null);
        return result;
    }

    private List<String> normalizeOrderIds(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            throw new BusinessException("orderIds cannot be empty");
        }
        List<String> normalized = orderIds.stream()
                .map(item -> item == null ? "" : item.trim())
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new BusinessException("orderIds cannot be empty");
        }
        if (normalized.size() > MAX_BATCH_SIZE) {
            throw new BusinessException("orderIds cannot exceed 50");
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRows(Map<String, Object> response) {
        if (response == null) {
            return List.of();
        }
        Object data = response.get("data");
        if (data instanceof List<?> list) {
            return convertListRows(list);
        }
        if (data instanceof Map<?, ?> map) {
            Object list = map.get("list");
            if (list instanceof List<?> nested) {
                return convertListRows(nested);
            }
            return List.of();
        }
        return List.of();
    }

    private List<Map<String, Object>> convertListRows(List<?> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> source)) {
                continue;
            }
            Map<String, Object> converted = new HashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (entry.getKey() != null) {
                    converted.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            result.add(converted);
        }
        return result;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() == 1;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private Long asLongObject(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    private LocalDateTime toDateTime(Long epochSeconds) {
        if (epochSeconds == null || epochSeconds <= 0L) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
    }

    private boolean isCipherInfoRequired(DouyinApiException ex) {
        if (ex == null) {
            return false;
        }
        String message = ex.getErrorMsg();
        String subCode = ex.getSubCode();
        return containsIgnoreCase(subCode, "parameter-invalid")
                && containsIgnoreCase(message, "cipher_infos");
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        if (source == null || keyword == null) {
            return false;
        }
        return source.toLowerCase().contains(keyword.toLowerCase());
    }

    private void recordDecryptAudit(
            UUID operatorId,
            String username,
            List<String> normalizedOrderIds,
            boolean success,
            String errorMessage) {
        if ((operatorId == null && !StringUtils.hasText(username)) || normalizedOrderIds == null || normalizedOrderIds.isEmpty()) {
            return;
        }
        OperationLog log = new OperationLog();
        log.setUserId(operatorId);
        log.setUsername(username);
        log.setModule("订单明细");
        log.setAction("解密手机号");
        log.setRequestMethod("POST");
        log.setTargetType("Order");
        log.setTargetId(joinOrderIds(normalizedOrderIds));
        log.setTargetName(normalizedOrderIds.get(0));
        log.setContent("解密订单手机号 " + normalizedOrderIds.size() + " 条");
        log.setRequestBody(Map.of(
                "orderIds", normalizedOrderIds,
                "count", normalizedOrderIds.size()
        ));
        log.setResponseCode(success ? "200" : "500");
        if (!success && StringUtils.hasText(errorMessage)) {
            log.setErrorMessage(errorMessage);
        }
        operationLogService.record(log);
    }

    private String joinOrderIds(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return null;
        }
        int limit = Math.min(orderIds.size(), 5);
        String joined = String.join(",", orderIds.subList(0, limit));
        if (orderIds.size() > limit) {
            return joined + "...";
        }
        return joined;
    }

    public static class DecryptPhoneVO {
        private String orderId;
        private boolean isVirtualTel;
        private String phone;
        private String phoneNoA;
        private String phoneNoB;
        private LocalDateTime expireTime;
        private boolean expired;

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        public boolean isVirtualTel() {
            return isVirtualTel;
        }

        public void setVirtualTel(boolean virtualTel) {
            isVirtualTel = virtualTel;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getPhoneNoA() {
            return phoneNoA;
        }

        public void setPhoneNoA(String phoneNoA) {
            this.phoneNoA = phoneNoA;
        }

        public String getPhoneNoB() {
            return phoneNoB;
        }

        public void setPhoneNoB(String phoneNoB) {
            this.phoneNoB = phoneNoB;
        }

        public LocalDateTime getExpireTime() {
            return expireTime;
        }

        public void setExpireTime(LocalDateTime expireTime) {
            this.expireTime = expireTime;
        }

        public boolean isExpired() {
            return expired;
        }

        public void setExpired(boolean expired) {
            this.expired = expired;
        }
    }
}
