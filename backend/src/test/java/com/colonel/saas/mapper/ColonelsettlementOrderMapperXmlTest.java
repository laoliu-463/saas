package com.colonel.saas.mapper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ColonelsettlementOrderMapperXmlTest {

    @Test
    void mapperShouldPersistAndReadBothAttributionSources() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/ColonelsettlementOrderMapper.xml"));

        assertThat(xml).contains("property=\"channelAttributionSource\" column=\"channel_attribution_source\"");
        assertThat(xml).contains("property=\"recruiterAttributionSource\" column=\"recruiter_attribution_source\"");
        assertThat(xml).contains("channel_attribution_source, recruiter_attribution_source");
        assertThat(xml).contains("#{channelAttributionSource}, #{recruiterAttributionSource}");
        assertThat(xml).contains("channel_attribution_source = #{channelAttributionSource}");
        assertThat(xml).contains("recruiter_attribution_source = #{recruiterAttributionSource}");
        assertThat(xml).contains("co.channel_attribution_source");
        assertThat(xml).contains("co.recruiter_attribution_source");
    }
}
