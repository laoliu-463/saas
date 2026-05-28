package com.colonel.saas.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SampleApplyRequest DTO 序列化/反序列化测试
 */
class SampleApplyRequestTest {

    private ObjectMapper objectMapper;
    private Validator validator;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Nested
    @DisplayName("JSON 序列化/反序列化")
    class SerializationTest {

        @Test
        void shouldSerializeToJson() throws Exception {
            SampleApplyRequest request = new SampleApplyRequest();
            request.setTalentId("TALENT001");
            request.setTalentNickname("测试达人");
            request.setTalentFansCount(100000L);
            request.setTalentCreditScore(new BigDecimal("4.5"));
            request.setTalentMainCategory("美妆");
            request.setProductId(UUID.randomUUID());
            request.setQuantity(3);
            request.setRecipientName("张三");
            request.setRecipientPhone("13800138000");
            request.setRecipientAddress("北京市朝阳区xxx");
            request.setApplySource("MANUAL");
            request.setRemark("测试备注");

            String json = objectMapper.writeValueAsString(request);

            assertThat(json).contains("TALENT001");
            assertThat(json).contains("测试达人");
            assertThat(json).contains("100000");
            assertThat(json).contains("4.5");
            assertThat(json).contains("美妆");
            assertThat(json).contains("3");
            assertThat(json).contains("张三");
            assertThat(json).contains("13800138000");
        }

        @Test
        void shouldDeserializeFromJson() throws Exception {
            String json = """
                {
                    "talentId": "TALENT001",
                    "talentNickname": "测试达人",
                    "talentFansCount": 100000,
                    "talentCreditScore": 4.5,
                    "productId": "%s",
                    "quantity": 3,
                    "recipientName": "张三",
                    "recipientPhone": "13800138000",
                    "recipientAddress": "北京市朝阳区xxx"
                }
                """.formatted(UUID.randomUUID());

            SampleApplyRequest request = objectMapper.readValue(json, SampleApplyRequest.class);

            assertThat(request.getTalentId()).isEqualTo("TALENT001");
            assertThat(request.getTalentNickname()).isEqualTo("测试达人");
            assertThat(request.getTalentFansCount()).isEqualTo(100000L);
            assertThat(request.getQuantity()).isEqualTo(3);
            assertThat(request.getRecipientName()).isEqualTo("张三");
            assertThat(request.getRecipientPhone()).isEqualTo("13800138000");
        }

        @Test
        void shouldHandleJsonAlias() throws Exception {
            // 测试 receiverName -> recipientName 的别名映射
            String json = """
                {
                    "talentId": "TALENT001",
                    "productId": "%s",
                    "quantity": 1,
                    "receiverName": "别名收货人",
                    "receiverPhone": "13900000000"
                }
                """.formatted(UUID.randomUUID());

            SampleApplyRequest request = objectMapper.readValue(json, SampleApplyRequest.class);

            assertThat(request.getRecipientName()).isEqualTo("别名收货人");
            assertThat(request.getRecipientPhone()).isEqualTo("13900000000");
        }

        @Test
        void shouldDeserializeNullOptionalFields() throws Exception {
            String json = """
                {
                    "talentId": "TALENT001",
                    "productId": "%s",
                    "quantity": 1
                }
                """.formatted(UUID.randomUUID());

            SampleApplyRequest request = objectMapper.readValue(json, SampleApplyRequest.class);

            assertThat(request.getTalentNickname()).isNull();
            assertThat(request.getTalentFansCount()).isNull();
            assertThat(request.getRecipientName()).isNull();
            assertThat(request.getApplySource()).isNull();
            assertThat(request.getRemark()).isNull();
        }
    }

    @Nested
    @DisplayName("字段校验")
    class ValidationTest {

        @Test
        void shouldFailWhenTalentIdIsBlank() {
            SampleApplyRequest request = new SampleApplyRequest();
            request.setProductId(UUID.randomUUID());
            request.setQuantity(1);

            Set<ConstraintViolation<SampleApplyRequest>> violations = validator.validate(request);

            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("talentId"));
        }

        @Test
        void shouldFailWhenProductIdIsNull() {
            SampleApplyRequest request = new SampleApplyRequest();
            request.setTalentId("TALENT001");
            request.setQuantity(1);

            Set<ConstraintViolation<SampleApplyRequest>> violations = validator.validate(request);

            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("productId"));
        }

        @Test
        void shouldFailWhenQuantityIsNull() {
            SampleApplyRequest request = new SampleApplyRequest();
            request.setTalentId("TALENT001");
            request.setProductId(UUID.randomUUID());

            Set<ConstraintViolation<SampleApplyRequest>> violations = validator.validate(request);

            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("quantity"));
        }

        @Test
        void shouldFailWhenQuantityIsLessThanMin() {
            SampleApplyRequest request = new SampleApplyRequest();
            request.setTalentId("TALENT001");
            request.setProductId(UUID.randomUUID());
            request.setQuantity(0);

            Set<ConstraintViolation<SampleApplyRequest>> violations = validator.validate(request);

            assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("quantity") &&
                v.getMessage().contains("至少为 1"));
        }

        @Test
        void shouldFailWhenQuantityExceedsMax() {
            SampleApplyRequest request = new SampleApplyRequest();
            request.setTalentId("TALENT001");
            request.setProductId(UUID.randomUUID());
            request.setQuantity(101);

            Set<ConstraintViolation<SampleApplyRequest>> violations = validator.validate(request);

            assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("quantity") &&
                v.getMessage().contains("不能超过 100"));
        }

        @Test
        void shouldFailWhenRecipientNameExceedsMaxLength() {
            SampleApplyRequest request = new SampleApplyRequest();
            request.setTalentId("TALENT001");
            request.setProductId(UUID.randomUUID());
            request.setQuantity(1);
            request.setRecipientName("a".repeat(101));

            Set<ConstraintViolation<SampleApplyRequest>> violations = validator.validate(request);

            assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("recipientName") &&
                v.getMessage().contains("不能超过 100 字符"));
        }

        @Test
        void shouldFailWhenApplySourceExceedsMaxLength() {
            SampleApplyRequest request = new SampleApplyRequest();
            request.setTalentId("TALENT001");
            request.setProductId(UUID.randomUUID());
            request.setQuantity(1);
            request.setApplySource("a".repeat(65));

            Set<ConstraintViolation<SampleApplyRequest>> violations = validator.validate(request);

            assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("applySource") &&
                v.getMessage().contains("不能超过 64 字符"));
        }

        @Test
        void shouldPassWhenAllRequiredFieldsAreValid() {
            SampleApplyRequest request = new SampleApplyRequest();
            request.setTalentId("TALENT001");
            request.setProductId(UUID.randomUUID());
            request.setQuantity(1);

            Set<ConstraintViolation<SampleApplyRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }

        @Test
        void shouldPassWhenOptionalFieldsAreMissing() {
            SampleApplyRequest request = new SampleApplyRequest();
            request.setTalentId("TALENT001");
            request.setProductId(UUID.randomUUID());
            request.setQuantity(1);
            // 不设置可选字段

            Set<ConstraintViolation<SampleApplyRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }
    }
}
