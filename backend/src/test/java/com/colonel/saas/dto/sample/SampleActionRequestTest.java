package com.colonel.saas.dto.sample;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SampleActionRequest DTO 序列化/反序列化测试
 */
class SampleActionRequestTest {

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
            SampleActionRequest request = new SampleActionRequest();
            request.setAction("SHIPPING");
            request.setReason("顺丰发出");
            request.setTrackingNo("SF1234567890");
            request.setShipperCode("SF");

            String json = objectMapper.writeValueAsString(request);

            assertThat(json).contains("SHIPPING");
            assertThat(json).contains("顺丰发出");
            assertThat(json).contains("SF1234567890");
            assertThat(json).contains("SF");
        }

        @Test
        void shouldDeserializeFromJson() throws Exception {
            String json = """
                {
                    "action": "PENDING_SHIP",
                    "reason": "审核通过",
                    "trackingNo": "YTO123456789",
                    "shipperCode": "YTO"
                }
                """;

            SampleActionRequest request = objectMapper.readValue(json, SampleActionRequest.class);

            assertThat(request.getAction()).isEqualTo("PENDING_SHIP");
            assertThat(request.getReason()).isEqualTo("审核通过");
            assertThat(request.getTrackingNo()).isEqualTo("YTO123456789");
            assertThat(request.getShipperCode()).isEqualTo("YTO");
        }

        @Test
        void shouldDeserializeWithMinimalFields() throws Exception {
            String json = """
                {
                    "action": "COMPLETED"
                }
                """;

            SampleActionRequest request = objectMapper.readValue(json, SampleActionRequest.class);

            assertThat(request.getAction()).isEqualTo("COMPLETED");
            assertThat(request.getReason()).isNull();
            assertThat(request.getTrackingNo()).isNull();
            assertThat(request.getShipperCode()).isNull();
        }
    }

    @Nested
    @DisplayName("字段校验")
    class ValidationTest {

        @Test
        void shouldFailWhenActionIsBlank() {
            SampleActionRequest request = new SampleActionRequest();
            request.setAction("");

            Set<ConstraintViolation<SampleActionRequest>> violations = validator.validate(request);

            assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("action") &&
                v.getMessage().contains("cannot be empty"));
        }

        @Test
        void shouldFailWhenActionIsNull() {
            SampleActionRequest request = new SampleActionRequest();

            Set<ConstraintViolation<SampleActionRequest>> violations = validator.validate(request);

            assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("action"));
        }

        @Test
        void shouldPassWhenOnlyActionIsProvided() {
            SampleActionRequest request = new SampleActionRequest();
            request.setAction("CLOSED");

            Set<ConstraintViolation<SampleActionRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }

        @Test
        void shouldPassWithAllFields() {
            SampleActionRequest request = new SampleActionRequest();
            request.setAction("SHIPPING");
            request.setReason("已发货");
            request.setTrackingNo("SF1234567890");
            request.setShipperCode("SF");

            Set<ConstraintViolation<SampleActionRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("动作值测试")
    class ActionValuesTest {

        @Test
        void shouldSupportStandardActions() {
            List<String> standardActions = List.of(
                "PENDING_SHIP", "REJECTED", "SHIPPING",
                "DELIVERED", "PENDING_HOMEWORK", "COMPLETED", "CLOSED"
            );

            for (String action : standardActions) {
                SampleActionRequest request = new SampleActionRequest();
                request.setAction(action);

                Set<ConstraintViolation<SampleActionRequest>> violations = validator.validate(request);

                assertThat(violations).isEmpty();
            }
        }

        @Test
        void shouldSupportLegacyAliases() {
            List<String> legacyActions = List.of(
                "APPROVED", "SHIPPED", "PENDING_TASK", "FINISHED"
            );

            for (String action : legacyActions) {
                SampleActionRequest request = new SampleActionRequest();
                request.setAction(action);

                Set<ConstraintViolation<SampleActionRequest>> violations = validator.validate(request);

                assertThat(violations).isEmpty();
            }
        }
    }
}
