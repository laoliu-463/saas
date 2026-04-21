package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.Merchant;
import com.colonel.saas.mapper.MerchantMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
public class MerchantService {

    private final MerchantMapper merchantMapper;

    public MerchantService(MerchantMapper merchantMapper) {
        this.merchantMapper = merchantMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
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
        merchant.setExtraData(order.getExtraData());
        try {
            merchantMapper.insert(merchant);
        } catch (DuplicateKeyException ignore) {
            // concurrent insert is acceptable
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
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
        merchant.setExtraData(order == null ? null : order.getExtraData());
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
            Object authorId = order.getExtraData().get("author_id");
            if (authorId != null && StringUtils.hasText(authorId.toString())) {
                return authorId.toString();
            }
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
            Object authorName = extra.get("author_name");
            if (authorName != null && StringUtils.hasText(authorName.toString())) {
                return authorName.toString();
            }
            Object merchantName = extra.get("merchant_name");
            if (merchantName != null && StringUtils.hasText(merchantName.toString())) {
                return merchantName.toString();
            }
        }
        return order.getShopName();
    }
}
