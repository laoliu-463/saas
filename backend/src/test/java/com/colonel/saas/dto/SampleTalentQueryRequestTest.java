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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SampleTalentQueryRequest DTO 序列化/反序列化测试
 */
class SampleTalentQueryRequestTest {

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
            SampleTalentQueryRequest request = new SampleTalentQueryRequest();
            request.setKeyword("美妆达人");
            request.setRegion("华东");
            request.setMinFans(10000L);
            request.setMaxFans(1000000L);
            request.setMinScore(new BigDecimal("4.0"));
            request.setPage(2);
            request.setSize(50);

            String json = objectMapper.writeValueAsString(request);

            assertThat(json).contains("美妆达人");
            assertThat(json).contains("华东");
            assertThat(json).contains("10000");
            assertThat(json).contains("1000000");
            assertThat(json).contains("4.0");
            assertThat(json).contains("2");
            assertThat(json).contains("50");
        }

        @Test
        void shouldDeserializeFromJson() throws Exception {
            String json = """
                {
                    "keyword": "美妆达人",
                    "region": "华东",
                    "minFans": 10000,
                    "maxFans": 1000000,
                    "minScore": 4.0,
                    "page": 2,
                    "size": 50
                }
                """;

            SampleTalentQueryRequest request = objectMapper.readValue(json, SampleTalentQueryRequest.class);

            assertThat(request.getKeyword()).isEqualTo("美妆达人");
            assertThat(request.getRegion()).isEqualTo("华东");
            assertThat(request.getMinFans()).isEqualTo(10000L);
            assertThat(request.getMaxFans()).isEqualTo(1000000L);
            assertThat(request.getMinScore()).isEqualByComparingTo(new BigDecimal("4.0"));
            assertThat(request.getPage()).isEqualTo(2);
            assertThat(request.getSize()).isEqualTo(50);
        }

        @Test
        void shouldUseDefaultValuesWhenFieldsMissing() throws Exception {
            String json = "{}";

            SampleTalentQueryRequest request = objectMapper.readValue(json, SampleTalentQueryRequest.class);

            assertThat(request.getKeyword()).isNull();
            assertThat(request.getRegion()).isNull();
            assertThat(request.getPage()).isEqualTo(1);
            assertThat(request.getSize()).isEqualTo(20);
        }

        @Test
        void shouldDeserializeNullOptionalFields() throws Exception {
            String json = """
                {
                    "keyword": "达人"
                }
                """;

            SampleTalentQueryRequest request = objectMapper.readValue(json, SampleTalentQueryRequest.class);

            assertThat(request.getKeyword()).isEqualTo("达人");
            assertThat(request.getRegion()).isNull();
            assertThat(request.getMinFans()).isNull();
            assertThat(request.getMaxFans()).isNull();
            assertThat(request.getMinScore()).isNull();
        }
    }

    @Nested
    @DisplayName("字段校验")
    class ValidationTest {

        @Test
        void shouldFailWhenMinFansIsNegative() {
            SampleTalentQueryRequest request = new SampleTalentQueryRequest();
            request.setMinFans(-1L);

            Set<ConstraintViolation<SampleTalentQueryRequest>> violations = validator.validate(request);

            assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("minFans") &&
                v.getMessage().contains("不能小于 0"));
        }

        @Test
        void shouldFailWhenMaxFansIsNegative() {
            SampleTalentQueryRequest request = new SampleTalentQueryRequest();
            request.setMaxFans(-1L);

            Set<ConstraintViolation<SampleTalentQueryRequest>> violations = validator.validate(request);

            assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("maxFans") &&
                v.getMessage().contains("不能小于 0"));
        }

        @Test
        void shouldFailWhenMinScoreIsNegative() {
            SampleTalentQueryRequest request = new SampleTalentQueryRequest();
            request.setMinScore(new BigDecimal("-0.01"));

            Set<ConstraintViolation<SampleTalentQueryRequest>> violations = validator.validate(request);

            assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("minScore") &&
                v.getMessage().contains("不能小于 0"));
        }

        @Test
        void shouldFailWhenPageIsLessThanMin() {
            SampleTalentQueryRequest request = new SampleTalentQueryRequest();
            request.setPage(0);

            Set<ConstraintViolation<SampleTalentQueryRequest>> violations = validator.validate(request);

            assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("page") &&
                v.getMessage().contains("不能小于 1"));
        }

        @Test
        void shouldFailWhenSizeIsLessThanMin() {
            SampleTalentQueryRequest request = new SampleTalentQueryRequest();
            request.setSize(0);

            Set<ConstraintViolation<SampleTalentQueryRequest>> violations = validator.validate(request);

            assertThat(violations).anyMatch(v ->
                v.getPropertyPath().toString().equals("size") &&
                v.getMessage().contains("不能小于 1"));
        }

        @Test
        void shouldPassWhenAllFieldsAreValid() {
            SampleTalentQueryRequest request = new SampleTalentQueryRequest();
            request.setKeyword("达人");
            request.setRegion("华东");
            request.setMinFans(0L);
            request.setMaxFans(1000000L);
            request.setMinScore(new BigDecimal("0.00"));
            request.setPage(1);
            request.setSize(20);

            Set<ConstraintViolation<SampleTalentQueryRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }

        @Test
        void shouldPassWhenAllFieldsAreNull() {
            SampleTalentQueryRequest request = new SampleTalentQueryRequest();
            // 所有字段都是可选的

            Set<ConstraintViolation<SampleTalentQueryRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("默认值测试")
    class DefaultValuesTest {

        @Test
        void shouldHaveDefaultPage() {
            SampleTalentQueryRequest request = new SampleTalentQueryRequest();

            assertThat(request.getPage()).isEqualTo(1);
        }

        @Test
        void shouldHaveDefaultSize() {
            SampleTalentQueryRequest request = new SampleTalentQueryRequest();

            assertThat(request.getSize()).isEqualTo(20);
        }
    }
}
