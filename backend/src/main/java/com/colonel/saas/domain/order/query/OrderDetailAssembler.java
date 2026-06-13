package com.colonel.saas.domain.order.query;

import com.colonel.saas.dto.order.OrderDetailResponse;
import org.springframework.beans.BeanUtils;

/**
 * 订单详情视图组装器（DDD-ORDER-006）。
 */
public class OrderDetailAssembler {

    public static OrderDetailView toView(OrderDetailResponse response) {
        if (response == null) {
            return null;
        }
        OrderDetailView view = new OrderDetailView();
        BeanUtils.copyProperties(response, view);

        if (response.getProduct() != null) {
            OrderDetailView.ProductInfo product = new OrderDetailView.ProductInfo();
            BeanUtils.copyProperties(response.getProduct(), product);
            view.setProduct(product);
        }
        if (response.getChannel() != null) {
            OrderDetailView.ChannelInfo channel = new OrderDetailView.ChannelInfo();
            BeanUtils.copyProperties(response.getChannel(), channel);
            view.setChannel(channel);
        }
        if (response.getTalent() != null) {
            OrderDetailView.TalentInfo talent = new OrderDetailView.TalentInfo();
            BeanUtils.copyProperties(response.getTalent(), talent);
            view.setTalent(talent);
        }
        if (response.getAmount() != null) {
            OrderDetailView.AmountInfo amount = new OrderDetailView.AmountInfo();
            BeanUtils.copyProperties(response.getAmount(), amount);
            view.setAmount(amount);
        }
        if (response.getPromotion() != null) {
            OrderDetailView.PromotionInfo promotion = new OrderDetailView.PromotionInfo();
            BeanUtils.copyProperties(response.getPromotion(), promotion);
            view.setPromotion(promotion);
        }
        if (response.getSample() != null) {
            OrderDetailView.SampleInfo sample = new OrderDetailView.SampleInfo();
            BeanUtils.copyProperties(response.getSample(), sample);
            view.setSample(sample);
        }
        if (response.getDiagnosis() != null) {
            OrderDetailView.DiagnosisInfo diagnosis = new OrderDetailView.DiagnosisInfo();
            BeanUtils.copyProperties(response.getDiagnosis(), diagnosis);
            view.setDiagnosis(diagnosis);
        }
        if (response.getTime() != null) {
            OrderDetailView.TimeInfo time = new OrderDetailView.TimeInfo();
            BeanUtils.copyProperties(response.getTime(), time);
            view.setTime(time);
        }

        return view;
    }
}
