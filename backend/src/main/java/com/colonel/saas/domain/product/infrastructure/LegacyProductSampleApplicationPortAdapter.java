package com.colonel.saas.domain.product.infrastructure;

import com.colonel.saas.domain.product.port.ProductSampleApplicationPort;
import com.colonel.saas.domain.product.port.QuickSampleApplyCommand;
import com.colonel.saas.domain.product.port.QuickSampleApplyPortResult;
import com.colonel.saas.domain.sample.api.ApplySampleFromProductCommand;
import com.colonel.saas.domain.sample.api.ApplySampleFromProductResult;
import com.colonel.saas.domain.sample.api.SampleApplicationPort;
import org.springframework.stereotype.Component;

/**
 * 商品域寄样委派适配器 — 将商品域端口命令映射到寄样域 {@link SampleApplicationPort}。
 */
@Component
public class LegacyProductSampleApplicationPortAdapter implements ProductSampleApplicationPort {

    private final SampleApplicationPort sampleApplicationPort;

    public LegacyProductSampleApplicationPortAdapter(SampleApplicationPort sampleApplicationPort) {
        this.sampleApplicationPort = sampleApplicationPort;
    }

    @Override
    public QuickSampleApplyPortResult applyQuickSample(QuickSampleApplyCommand command) {
        ApplySampleFromProductResult sampleResult = sampleApplicationPort.applyFromProduct(toSampleCommand(command));
        return toPortResult(sampleResult);
    }

    private static ApplySampleFromProductCommand toSampleCommand(QuickSampleApplyCommand command) {
        return new ApplySampleFromProductCommand(
                command.relationId(),
                command.productId(),
                command.channelId(),
                command.talentIds(),
                command.spec(),
                command.quantity(),
                command.remark(),
                command.receiverName(),
                command.receiverPhone(),
                command.receiverAddress(),
                command.requestSource(),
                command.userId(),
                command.channelUserId(),
                command.roleCodes(),
                command.productSnapshotTitle(),
                command.productSnapshotPrice(),
                command.activityId(),
                command.assigneeId(),
                command.externalEnabled(),
                command.externalSupported(),
                command.skuId()
        );
    }

    private static QuickSampleApplyPortResult toPortResult(ApplySampleFromProductResult sampleResult) {
        QuickSampleApplyPortResult result = new QuickSampleApplyPortResult();
        result.setSuccess(sampleResult.isSuccess());
        result.setSuccessCount(sampleResult.getSuccessCount());
        result.setFailureCount(sampleResult.getFailureCount());
        for (ApplySampleFromProductResult.TalentResult source : sampleResult.getItems()) {
            QuickSampleApplyPortResult.TalentResult target = new QuickSampleApplyPortResult.TalentResult();
            target.setTalentId(source.getTalentId());
            target.setSuccess(source.isSuccess());
            target.setSampleRequestId(source.getSampleRequestId());
            target.setExternalApplied(source.isExternalApplied());
            target.setExternalApplyId(source.getExternalApplyId());
            target.setFallback(source.isFallback());
            target.setGatewayStatus(source.getGatewayStatus());
            target.setFallbackType(source.getFallbackType());
            target.setMessage(source.getMessage());
            result.getItems().add(target);
        }
        return result;
    }
}
