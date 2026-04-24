package com.colonel.saas.service.talent;

import com.colonel.saas.common.enums.TalentDataSource;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class TalentEnrichResult {

    private final TalentDataSource source;
    private final Map<String, Object> fields;
    private final String message;

    private TalentEnrichResult(TalentDataSource source, Map<String, Object> fields, String message) {
        this.source = source;
        this.fields = fields == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        this.message = message;
    }

    public static TalentEnrichResult empty(TalentDataSource source, String message) {
        return new TalentEnrichResult(source, Collections.emptyMap(), message);
    }

    public static TalentEnrichResult of(TalentDataSource source, Map<String, Object> fields, String message) {
        return new TalentEnrichResult(source, fields, message);
    }

    public TalentDataSource source() {
        return source;
    }

    public Map<String, Object> fields() {
        return fields;
    }

    public String message() {
        return message;
    }

    public boolean hasFields() {
        return !fields.isEmpty();
    }
}

