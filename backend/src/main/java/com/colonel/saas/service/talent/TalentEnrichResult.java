package com.colonel.saas.service.talent;

import com.colonel.saas.common.enums.TalentDataSource;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 达人采集结果 —— 封装一次达人资料采集操作返回的不可变结果。
 *
 * <p><b>职责：</b></p>
 * <ul>
 *   <li>记录数据来源类型（{@link #source()}），用于字段来源追踪</li>
 *   <li>携带采集到的字段键值对（{@link #fields()}），字段名与 {@link Talent} 实体属性对应</li>
 *   <li>提供描述性消息（{@link #message()}），如采集失败原因或数据来源说明</li>
 *   <li>保证内部 Map 的不可变性（{@code Collections.unmodifiableMap}）</li>
 * </ul>
 *
 * <p><b>在架构中的角色：</b>由各 {@link TalentDataProvider} 实现类构造并返回，
 * 被 {@link TalentEnrichOrchestrator} 消费。编排器通过 {@link #hasFields()} 判断
 * 是否有有效数据，再通过 {@link #fields()} 将字段回写到 {@link Talent} 实体。</p>
 *
 * <p><b>业务域：</b>达人域 / 达人资料采集子域</p>
 *
 * @see TalentDataProvider
 * @see TalentEnrichOrchestrator
 */
public class TalentEnrichResult {

    /** 数据来源类型（如 DOUYIN_API、CRAWLER、MOCK 等） */
    private final TalentDataSource source;

    /**
     * 采集到的字段键值对（不可变 Map）。
     * <p>键为 {@link Talent} 实体的属性名（如 "nickname"、"fans"、"avatarUrl"），
     * 值为对应的属性值。由 {@link TalentEnrichOrchestrator#applyFields} 消费。</p>
     */
    private final Map<String, Object> fields;

    /** 描述性消息，如采集失败原因或数据来源说明 */
    private final String message;

    /**
     * 私有构造器，通过静态工厂方法调用。
     *
     * @param source  数据来源类型
     * @param fields  采集到的字段（{@code null} 视为空 Map，并转为不可变副本）
     * @param message 描述性消息
     */
    private TalentEnrichResult(TalentDataSource source, Map<String, Object> fields, String message) {
        this.source = source;
        this.fields = fields == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        this.message = message;
    }

    /**
     * 创建空采集结果（无字段数据）。
     *
     * <p>典型场景：供应者尝试采集但未获取到有效数据时使用。</p>
     *
     * @param source  数据来源类型
     * @param message 描述性消息（如"未找到达人资料"）
     * @return 空采集结果实例
     */
    public static TalentEnrichResult empty(TalentDataSource source, String message) {
        return new TalentEnrichResult(source, Collections.emptyMap(), message);
    }

    /**
     * 创建包含字段数据的采集结果。
     *
     * @param source  数据来源类型
     * @param fields  采集到的字段键值对（会被复制为不可变 Map）
     * @param message 描述性消息
     * @return 包含字段数据的采集结果实例
     */
    public static TalentEnrichResult of(TalentDataSource source, Map<String, Object> fields, String message) {
        return new TalentEnrichResult(source, fields, message);
    }

    /**
     * 获取数据来源类型。
     *
     * @return 数据来源类型枚举
     */
    public TalentDataSource source() {
        return source;
    }

    /**
     * 获取采集到的字段键值对（不可变）。
     *
     * @return 不可变 Map，键为字段名，值为字段值
     */
    public Map<String, Object> fields() {
        return fields;
    }

    /**
     * 获取描述性消息。
     *
     * @return 消息文本
     */
    public String message() {
        return message;
    }

    /**
     * 判断是否包含有效的字段数据。
     *
     * @return {@code true} 表示至少有一个字段，{@code false} 表示空结果
     */
    public boolean hasFields() {
        return !fields.isEmpty();
    }
}

