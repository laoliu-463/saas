package com.colonel.saas.config;

import cn.hutool.http.HttpGlobalConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HutoolHttpConfig {

    @PostConstruct
    public void init() {
        HttpGlobalConfig.setTimeout(10_000);
    }
}
