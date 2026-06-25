package com.colonel.saas.domain.sample.facade;

import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.mapper.SampleRequestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacySampleDomainFacadeTest {

    @Mock
    private SampleRequestMapper sampleRequestMapper;

    private SampleDomainFacade facade;

    @BeforeEach
    void setUp() {
        facade = new LegacySampleDomainFacade(sampleRequestMapper);
    }

    @Test
    void existsById_shouldReturnTrueWhenSampleFound() {
        UUID id = UUID.randomUUID();
        when(sampleRequestMapper.selectById(id)).thenReturn(new SampleRequest());

        assertThat(facade.existsById(id)).isTrue();
    }

    @Test
    void existsById_shouldReturnFalseWhenMissing() {
        UUID id = UUID.randomUUID();
        when(sampleRequestMapper.selectById(id)).thenReturn(null);

        assertThat(facade.existsById(id)).isFalse();
        assertThat(facade.existsById(null)).isFalse();
    }
}
