package com.colonel.saas.debug;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DebugSessionLog {

    private static final String SESSION_ID = "02e7cc";

    private DebugSessionLog() {
    }

    public static void write(String hypothesisId, String location, String message, Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", SESSION_ID);
        payload.put("hypothesisId", hypothesisId);
        payload.put("location", location);
        payload.put("message", message);
        payload.put("data", data == null ? Map.of() : data);
        payload.put("timestamp", System.currentTimeMillis());
        String line = toJson(payload) + System.lineSeparator();
        for (Path path : candidatePaths()) {
            try {
                Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(path, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                return;
            } catch (Exception ignored) {
                // try next path
            }
        }
    }

    private static Path[] candidatePaths() {
        return new Path[] {
                Path.of(System.getProperty("user.dir", ".")).resolve("debug-02e7cc.log"),
                Path.of(System.getProperty("user.dir", ".")).resolve("../debug-02e7cc.log").normalize(),
                Path.of("/app/debug-02e7cc.log")
        };
    }

    private static String toJson(Map<String, Object> payload) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(entry.getKey())).append("\":");
            sb.append(valueToJson(entry.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    private static String valueToJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('"').append(escape(String.valueOf(entry.getKey()))).append("\":");
                sb.append(valueToJson(entry.getValue()));
            }
            sb.append('}');
            return sb.toString();
        }
        return "\"" + escape(String.valueOf(value)) + "\"";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
