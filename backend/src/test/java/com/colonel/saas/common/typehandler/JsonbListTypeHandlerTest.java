package com.colonel.saas.common.typehandler;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JsonbListTypeHandlerTest {

    private final JsonbListTypeHandler handler = new JsonbListTypeHandler();

    @Test
    void setNonNullParameterShouldSerializeStringListAsJsonbArray() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);

        handler.setNonNullParameter(statement, 1, List.of("食品", "美妆"), JdbcType.OTHER);

        verify(statement).setObject(eq(1), json.capture(), eq(Types.OTHER));
        assertThat(json.getValue()).isEqualTo("[\"食品\",\"美妆\"]");
    }

    @Test
    void getNullableResultShouldParseListFromAllJdbcSources() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        CallableStatement callableStatement = mock(CallableStatement.class);
        when(resultSet.getString("tags")).thenReturn("[\"A\",\"B\"]");
        when(resultSet.getString(1)).thenReturn("[\"C\"]");
        when(callableStatement.getString(3)).thenReturn("[\"D\"]");

        assertThat(handler.getNullableResult(resultSet, "tags")).containsExactly("A", "B");
        assertThat(handler.getNullableResult(resultSet, 1)).containsExactly("C");
        assertThat(handler.getNullableResult(callableStatement, 3)).containsExactly("D");
    }

    @Test
    void getNullableResultShouldReturnNullForBlankJson() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("tags")).thenReturn("");

        assertThat(handler.getNullableResult(resultSet, "tags")).isNull();
    }

    @Test
    void getNullableResultShouldRejectInvalidJson() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("tags")).thenReturn("[invalid");

        assertThatThrownBy(() -> handler.getNullableResult(resultSet, "tags"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("Failed to parse JSONB list value");
    }
}
