package com.colonel.saas.douyin;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "douyin.oauth")
public class DouyinOAuthProperties {

    private String authorizeUrl = "https://op.jinritemai.com/oauth2/authorize";
    private String powerManageUrl = "https://buyin.jinritemai.com/dashboard/institution/power-manage";
    private String redirectUri = "http://localhost:8081/api/douyin/oauth/callback";
    private String frontendSuccessUrl = "http://localhost:3001/system/douyin?oauth=success";
    private String frontendFailureUrl = "http://localhost:3001/system/douyin?oauth=failed";
    private long stateTtlMinutes = 10L;
}
