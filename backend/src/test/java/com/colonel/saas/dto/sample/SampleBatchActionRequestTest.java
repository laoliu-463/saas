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
 * SampleBatchActionRequest DTO 序列化/反序列化测试
 */
class SampleBatchActionRequestTest {

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
            SampleBatchActionRequest request = new SampleBatchActionRequest();
            request.setRequestNos(List.of("SR001", "SR002", "SR003"));
            request.setRemark("统一驳回原因");

            String json = objectMapper.writeValueAsString(request);

            assertThat(json).contains("SR001");
            assertThat(json).contains("SR002");
            assertThat(json).contains("SR003");
            assertThat(json).contains("统一驳回原因");
        }

        @Test
        void shouldDeserializeFromJson() throws Exception {
            String json = """
                {
                    "requestNos": ["SR001", "SR002"],
                    "remark": "商品缺货"
                }
                """;

            SampleBatchActionRequest request = objectMapper.readValue(json, SampleBatchActionRequest.class);

            assertThat(request.getRequestNos()).hasSize(2);
            assertThat(request.getRequestNos()).containsExactly("SR001", "SR002");
            assertThat(request.getRemark()).isEqualTo("商品缺货");
        }

        @Test
        void shouldDeserializeWithSingleItem() throws Exception {
            String json = """
                {
                    "requestNos": ["SR001"]
                }
                """;

            SampleBatchActionRequest request = objectMapper.readValue(json, SampleBatchActionRequest.class);

            assertThat(request.getRequestNos()).hasSize(1);
            assertThat(request.getRequestNos().get(0)).isEqualTo("SR001");
        }

        @Test
        void shouldDeserializeWithNullRemark() throws Exception {
            String json = """
                {
                    "requestNos": ["SR001", "SR002"]
                }
                """;

            SampleBatchActionRequest request = objectMapper.readValue(json, SampleBatchActionRequest.class);

            assertThat(request.getRequestNos()).hasSize(2);
            assertThat(request.getRemark()).isNull();
        }
    }

    @Nested
    @DisplayName("字段校验")
    class ValidationTest {

        @Test
        void shouldFailWhenRequestNosIsEmpty() {
            SampleBatchActionRequest request = new SampleBatchActionRequest();
            request.setRequestNos(List.of());

            Set<ConstraintViolation<SampleBatchActionRequest>> violations = validator.validate(request);

            assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("requestNos") &&
                v.getMessage().contains("cannot be empty"));
        }

        @Test
        void shouldFailWhenRequestNosIsNull() {
            SampleBatchActionRequest request = new SampleBatchActionRequest();
            request.setRequestNos(null);

            Set<ConstraintViolation<SampleBatchActionRequest>> violations = validator.validate(request);

            assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("requestNos"));
        }

        @Test
        void shouldPassWhenRequestNosIsValid() {
            SampleBatchActionRequest request = new SampleBatchActionRequest();
            request.setRequestNos(List.of("SR001", "SR002"));

            Set<ConstraintViolation<SampleBatchActionRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }

        @Test
        void shouldPassWithRemark() {
            SampleBatchActionRequest request = new SampleBatchActionRequest();
            request.setRequestNos(List.of("SR001"));
            request.setRemark("测试备注");

            Set<ConstraintViolation<SampleBatchActionRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }
    }
}
