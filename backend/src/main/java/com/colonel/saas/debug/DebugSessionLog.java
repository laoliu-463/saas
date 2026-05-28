package com.colonel.saas.debug;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 调试会话日志工具类。
 * <p>
 * 将调试过程中的假设验证、关键位置和诊断数据以 JSON 格式追加写入本地日志文件，
 * 用于 debug session 的可追溯记录。
 *
 * <ul>
 *   <li>日志写入 — 将结构化诊断数据以 JSON 行格式追加写入文件</li>
 *   <li>多路径容错 — 依次尝试多个候选路径，确保至少有一个写入成功</li>
 *   <li>内建 JSON 序列化 — 无需外部依赖即可序列化 Map 为 JSON</li>
 *   <li>自动创建目录 — 写入前自动创建父目录</li>
 * </ul>
 *
 * 所属业务领域：调试 / 开发工具
 */
public final class DebugSessionLog {

    /** 当前调试会话标识 */
    private static final String SESSION_ID = "02e7cc";

    private DebugSessionLog() {
    }

    /**
     * 写入一条调试日志记录。
     * <p>
     * 将诊断数据序列化为 JSON 行格式，依次尝试多个候选路径写入，
     * 直到某一个路径成功为止。
     *
     * <ol>
     *   <li>组装 payload Map（sessionId、hypothesisId、location、message、data、timestamp）</li>
     *   <li>序列化为 JSON 并追加换行符</li>
     *   <li>遍历 {@link #candidatePaths()}，自动创建父目录后写入文件</li>
     *   <li>首次写入成功即返回，所有路径均失败则静默跳过</li>
     * </ol>
     *
     * @param hypothesisId 假设标识（如 "H1"、"H2"）
     * @param location     代码位置描述（如类名+方法名）
     * @param message      日志消息
     * @param data         附加诊断数据（可为 null，null 时写入空 Map）
     */
    public static void write(String hypothesisId, String location, String message, Map<String, Object> data) {
        // 第一步：组装 payload，包含会话标识、假设 ID、位置、消息和附加数据
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", SESSION_ID);
        payload.put("hypothesisId", hypothesisId);
        payload.put("location", location);
        payload.put("message", message);
        payload.put("data", data == null ? Map.of() : data);
        payload.put("timestamp", System.currentTimeMillis());
        // 第二步：序列化为 JSON 行格式
        String line = toJson(payload) + System.lineSeparator();
        // 第三步：遍历候选路径，首次写入成功即返回，全部失败则静默跳过
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

    /**
     * 获取日志写入的候选路径数组。
     * <p>
     * 按优先级排列：当前工作目录、上级目录、容器内固定路径 {@code /app/debug-02e7cc.log}。
     *
     * @return 候选路径数组
     */
    private static Path[] candidatePaths() {
        return new Path[] {
                Path.of(System.getProperty("user.dir", ".")).resolve("debug-02e7cc.log"),
                Path.of(System.getProperty("user.dir", ".")).resolve("../debug-02e7cc.log").normalize(),
                Path.of("/app/debug-02e7cc.log")
        };
    }

    /**
     * 将 Map 序列化为 JSON 字符串。
     * <p>
     * 内建简易 JSON 序列化，无需外部依赖，支持嵌套 Map。
     *
     * @param payload 待序列化的 Map
     * @return JSON 字符串
     */
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

    /**
     * 将单个值序列化为 JSON 片段。
     * <p>
     * 支持 null、Number、Boolean、嵌套 Map 和普通字符串的序列化。
     *
     * @param value 待序列化的值
     * @return JSON 片段字符串
     */
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

    /**
     * JSON 字符串转义。
     * <p>
     * 将反斜杠和双引号进行转义处理。
     *
     * @param value 原始字符串
     * @return 转义后的字符串
     */
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
