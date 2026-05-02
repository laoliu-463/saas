package com.colonel.saas.gateway.douyin.contract;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class DouyinUpstreamModeSupport {

    private final String mode;

    public DouyinUpstreamModeSupport(@Value("${douyin.real.upstream-mode:live}") String rawMode) {
        this.mode = normalize(rawMode);
    }

    public boolean isContract() {
        return "contract".equals(mode);
    }

    public boolean isLive() {
        return "live".equals(mode);
    }

    public String value() {
        return mode;
    }

    private String normalize(String rawMode) {
        if (rawMode == null) {
            return "live";
        }
        String normalized = rawMode.trim().toLowerCase(Locale.ROOT);
        if ("contract".equals(normalized)) {
            return "contract";
        }
        return "live";
    }
}
