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
 * SampleBatchShipRequest DTO 序列化/反序列化测试
 */
class SampleBatchShipRequestTest {

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
            SampleBatchShipItem item1 = new SampleBatchShipItem();
            item1.setRequestNo("SR001");
            item1.setTrackingNo("SF1234567890");
            item1.setShipperCode("SF");

            SampleBatchShipItem item2 = new SampleBatchShipItem();
            item2.setRequestNo("SR002");
            item2.setTrackingNo("YTO9876543210");
            item2.setShipperCode("YTO");

            SampleBatchShipRequest request = new SampleBatchShipRequest();
            request.setItems(List.of(item1, item2));

            String json = objectMapper.writeValueAsString(request);

            assertThat(json).contains("SR001");
            assertThat(json).contains("SF1234567890");
            assertThat(json).contains("YTO9876543210");
        }

        @Test
        void shouldDeserializeFromJson() throws Exception {
            String json = """
                {
                    "items": [
                        {"requestNo": "SR001", "trackingNo": "SF1234567890", "shipperCode": "SF"},
                        {"requestNo": "SR002", "trackingNo": "YTO9876543210", "shipperCode": "YTO"}
                    ]
                }
                """;

            SampleBatchShipRequest request = objectMapper.readValue(json, SampleBatchShipRequest.class);

            assertThat(request.getItems()).hasSize(2);
            assertThat(request.getItems().get(0).getRequestNo()).isEqualTo("SR001");
            assertThat(request.getItems().get(0).getTrackingNo()).isEqualTo("SF1234567890");
            assertThat(request.getItems().get(0).getShipperCode()).isEqualTo("SF");
        }

        @Test
        void shouldDeserializeWithSingleItem() throws Exception {
            String json = """
                {
                    "items": [
                        {"requestNo": "SR001", "trackingNo": "SF1234567890", "shipperCode": "SF"}
                    ]
                }
                """;

            SampleBatchShipRequest request = objectMapper.readValue(json, SampleBatchShipRequest.class);

            assertThat(request.getItems()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("字段校验")
    class ValidationTest {

        @Test
        void shouldFailWhenItemsIsEmpty() {
            SampleBatchShipRequest request = new SampleBatchShipRequest();
            request.setItems(List.of());

            Set<ConstraintViolation<SampleBatchShipRequest>> violations = validator.validate(request);

            assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("items") &&
                v.getMessage().contains("cannot be empty"));
        }

        @Test
        void shouldFailWhenItemsIsNull() {
            SampleBatchShipRequest request = new SampleBatchShipRequest();
            request.setItems(null);

            Set<ConstraintViolation<SampleBatchShipRequest>> violations = validator.validate(request);

            assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("items"));
        }

        @Test
        void shouldPassWhenItemsHasValidItems() {
            SampleBatchShipItem item = new SampleBatchShipItem();
            item.setRequestNo("SR001");
            item.setTrackingNo("SF1234567890");
            item.setShipperCode("SF");

            SampleBatchShipRequest request = new SampleBatchShipRequest();
            request.setItems(List.of(item));

            Set<ConstraintViolation<SampleBatchShipRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }
    }
}
