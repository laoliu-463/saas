package com.colonel.saas.crawler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class UaPoolTest {

    private UaPool uaPool;

    @BeforeEach
    void setUp() {
        uaPool = new UaPool();
    }

    @Test
    void getPool_returnsFiveUserAgents() {
        List<String> pool = uaPool.getPool();

        assertThat(pool).hasSize(5);
        assertThat(pool).allSatisfy(ua ->
                assertThat(ua).startsWith("Mozilla/5.0"));
    }

    @Test
    void getPool_containsMobileAndDesktopUAs() {
        List<String> pool = uaPool.getPool();

        assertThat(pool).anySatisfy(ua ->
                assertThat(ua).contains("iPhone"));
        assertThat(pool).anySatisfy(ua ->
                assertThat(ua).contains("Windows"));
        assertThat(pool).anySatisfy(ua ->
                assertThat(ua).contains("Android"));
        assertThat(pool).anySatisfy(ua ->
                assertThat(ua).contains("Macintosh"));
    }

    @Test
    void getPool_returnsSameListInstance() {
        List<String> first = uaPool.getPool();
        List<String> second = uaPool.getPool();

        assertThat(second).isSameAs(first);
    }
}