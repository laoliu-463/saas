package com.colonel.saas.common.typehandler;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JsonbTypeHandlerTest {

    private final JsonbTypeHandler handler = new JsonbTypeHandler();

    @Test
    void setNonNullParameterShouldSerializeMapAsJsonbObject() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);

        handler.setNonNullParameter(statement, 2, Map.of("name", "达人", "count", 3), JdbcType.OTHER);

        verify(statement).setObject(eq(2), json.capture(), eq(Types.OTHER));
        assertThat(json.getValue()).contains("\"name\":\"达人\"", "\"count\":3");
    }

    @Test
    void getNullableResultShouldParseObjectFromAllJdbcSources() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        CallableStatement callableStatement = mock(CallableStatement.class);
        when(resultSet.getString("extra")).thenReturn("{\"status\":\"ok\"}");
        when(resultSet.getString(1)).thenReturn("{\"count\":7}");
        when(callableStatement.getString(3)).thenReturn("{\"source\":\"callable\"}");

        assertThat(handler.getNullableResult(resultSet, "extra")).containsEntry("status", "ok");
        assertThat(handler.getNullableResult(resultSet, 1)).containsEntry("count", 7);
        assertThat(handler.getNullableResult(callableStatement, 3)).containsEntry("source", "callable");
    }

    @Test
    void getNullableResultShouldReturnNullForBlankJson() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("extra")).thenReturn(" ");

        assertThat(handler.getNullableResult(resultSet, "extra")).isNull();
    }

    @Test
    void getNullableResultShouldRejectNonObjectJson() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("extra")).thenReturn("[\"not-object\"]");

        assertThatThrownBy(() -> handler.getNullableResult(resultSet, "extra"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("JSONB value is not an object");
    }
}
