package com.colonel.saas.controller;

import com.colonel.saas.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RedisProbeControllerTest {

    @Test
    void redisProbe_returnsConfiguredValuesAndPing() throws Exception {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");

        RedisProbeController controller = new RedisProbeController(factory, "127.0.0.1", 6379, 2, "");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/ops/redis-probe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.host").value("127.0.0.1"))
                .andExpect(jsonPath("$.data.port").value(6379))
                .andExpect(jsonPath("$.data.database").value(2))
                .andExpect(jsonPath("$.data.passwordPresent").value(false))
                .andExpect(jsonPath("$.data.status").value("success"))
                .andExpect(jsonPath("$.data.ping").value("PONG"));
    }
}
