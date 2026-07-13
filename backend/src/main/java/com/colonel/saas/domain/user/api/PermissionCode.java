package com.colonel.saas.domain.user.api;

import java.util.regex.Pattern;

public record PermissionCode(String value) {

    private static final Pattern CANONICAL =
            Pattern.compile("[a-z][a-z0-9-]{0,62}:[a-z][a-z0-9-]{0,62}");

    public PermissionCode {
        if (value == null || !CANONICAL.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "permissionCode must use canonical resource:action syntax");
        }
    }
}
