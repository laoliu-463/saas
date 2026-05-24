package com.colonel.saas.config;

public enum ConfigConsumerDomain {
    SAMPLE("sample"),
    TALENT("talent"),
    PERFORMANCE("performance"),
    PRODUCT("product"),
    USER("user");

    private final String code;

    ConfigConsumerDomain(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
