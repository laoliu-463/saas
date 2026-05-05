package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.Merchant;
import com.colonel.saas.mapper.MerchantMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;

@Service
public class MerchantService {

    private final MerchantMapper merchantMapper;

    public MerchantService(MerchantMapper merchantMapper) {
        this.merchantMapper = merchantMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void ensureMerchantFromOrder(ColonelsettlementOrder order) {
        String merchantId = resolveMerchantId(order);
        if (!StringUtils.hasText(merchantId)) {
            return;
        }
        Merchant existing = merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getMerchantId, merchantId)
                .last("limit 1"));
        if (existing != null) {
            return;
        }
        Merchant merchant = new Merchant();
        merchant.setMerchantId(merchantId);
        merchant.setMerchantName(resolveMerchantName(order));
        merchant.setShopId(order.getShopId());
        merchant.setShopName(order.getShopName());
        merchant.setSourceOrderId(order.getOrderId());
        merchant.setStatus(1);
        // Keep merchant creation resilient during order sync. The order raw payload
        // already lives on colonelsettlement_order.extra_data, so merchant can be
        // created without duplicating the JSON blob here.
        merchant.setExtraData(null);
        merchant.setId(UUID.randomUUID());
        try {
            merchantMapper.insert(merchant);
        } catch (DuplicateKeyException ignore) {
            // concurrent insert is acceptable
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Merchant findOrCreateByChannel(String channelId, ColonelsettlementOrder order) {
        if (!StringUtils.hasText(channelId)) {
            return null;
        }
        Merchant existing = merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getMerchantId, channelId)
                .last("limit 1"));
        if (existing != null) {
            return existing;
        }
        Merchant merchant = new Merchant();
        merchant.setMerchantId(channelId);
        merchant.setMerchantName(channelId);
        merchant.setShopId(order == null ? null : order.getShopId());
        merchant.setShopName(order == null ? null : order.getShopName());
        merchant.setSourceOrderId(order == null ? null : order.getOrderId());
        merchant.setStatus(0);
        merchant.setExtraData(null);
        merchant.setId(UUID.randomUUID());
        try {
            merchantMapper.insert(merchant);
            return merchant;
        } catch (DuplicateKeyException ignore) {
            return merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                    .eq(Merchant::getMerchantId, channelId)
                    .last("limit 1"));
        }
    }

    private String resolveMerchantId(ColonelsettlementOrder order) {
        if (order.getExtraData() != null) {
            Object merchantId = order.getExtraData().get("merchant_id");
            if (merchantId != null && StringUtils.hasText(merchantId.toString())) {
                return merchantId.toString();
            }
        }
        return order.getShopId() == null ? null : String.valueOf(order.getShopId());
    }

    private String resolveMerchantName(ColonelsettlementOrder order) {
        Map<String, Object> extra = order.getExtraData();
        if (extra != null) {
            Object merchantName = extra.get("merchant_name");
            if (merchantName != null && StringUtils.hasText(merchantName.toString())) {
                return merchantName.toString();
            }
        }
        return order.getShopName();
    }
}
