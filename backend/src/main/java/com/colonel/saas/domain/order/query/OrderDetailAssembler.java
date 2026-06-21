package com.colonel.saas.domain.order.query;

import com.colonel.saas.dto.order.OrderDetailResponse;

/**
 * 订单详情视图组装器（DDD-ORDER-006）。
 */
public class OrderDetailAssembler {

    public static OrderDetailView toView(OrderDetailResponse response) {
        if (response == null) {
            return null;
        }
        OrderDetailView view = new OrderDetailView();
        BeanPropertyCopy.copy(response, view);

        if (response.getProduct() != null) {
            OrderDetailView.ProductInfo product = new OrderDetailView.ProductInfo();
            BeanPropertyCopy.copy(response.getProduct(), product);
            view.setProduct(product);
        }
        if (response.getChannel() != null) {
            OrderDetailView.ChannelInfo channel = new OrderDetailView.ChannelInfo();
            BeanPropertyCopy.copy(response.getChannel(), channel);
            view.setChannel(channel);
        }
        if (response.getTalent() != null) {
            OrderDetailView.TalentInfo talent = new OrderDetailView.TalentInfo();
            BeanPropertyCopy.copy(response.getTalent(), talent);
            view.setTalent(talent);
        }
        if (response.getAmount() != null) {
            OrderDetailView.AmountInfo amount = new OrderDetailView.AmountInfo();
            BeanPropertyCopy.copy(response.getAmount(), amount);
            view.setAmount(amount);
        }
        if (response.getPromotion() != null) {
            OrderDetailView.PromotionInfo promotion = new OrderDetailView.PromotionInfo();
            BeanPropertyCopy.copy(response.getPromotion(), promotion);
            view.setPromotion(promotion);
        }
        if (response.getSample() != null) {
            OrderDetailView.SampleInfo sample = new OrderDetailView.SampleInfo();
            BeanPropertyCopy.copy(response.getSample(), sample);
            view.setSample(sample);
        }
        if (response.getDiagnosis() != null) {
            OrderDetailView.DiagnosisInfo diagnosis = new OrderDetailView.DiagnosisInfo();
            BeanPropertyCopy.copy(response.getDiagnosis(), diagnosis);
            view.setDiagnosis(diagnosis);
        }
        if (response.getTime() != null) {
            OrderDetailView.TimeInfo time = new OrderDetailView.TimeInfo();
            BeanPropertyCopy.copy(response.getTime(), time);
            view.setTime(time);
        }

        return view;
    }
}
