package com.colonel.saas.domain.shared.policy;

/**
 * Shared text predicates for domain policies.
 *
 * <p>This keeps policy classes independent from Spring utility APIs while
 * preserving the same "non-null and contains non-whitespace" semantics.</p>
 */
public final class DomainText {

    private DomainText() {
    }

    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
