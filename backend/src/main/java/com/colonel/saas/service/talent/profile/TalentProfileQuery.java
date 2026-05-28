package com.colonel.saas.service.talent.profile;

import com.colonel.saas.service.talent.TalentInputParseResult;
import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

/**
 * 达人资料同步查询请求 —— 封装一次达人资料同步操作所需的全部输入参数。
 *
 * <p><b>职责：</b></p>
 * <ul>
 *   <li>承载用户原始输入文本（{@link #input}），如抖音号、主页链接、secUid 等</li>
 *   <li>携带已解析的达人标识信息（{@link #parsed}），由
 *       {@link com.colonel.saas.service.talent.TalentInputParser} 提前解析</li>
 *   <li>标识同步模式：自动采集（{@link #manualFill}=false）或手动填写（{@link #manualFill}=true）</li>
 *   <li>在手动填写模式下携带用户填写的原始数据（{@link #manualPayload}）</li>
 *   <li>携带强制刷新标志（{@link #forceRefresh}），决定是否忽略已有的成功同步记录</li>
 * </ul>
 *
 * <p><b>在架构中的角色：</b>由 {@link TalentProfileSyncService} 构造并传递给
 * 各 {@link TalentProfileProvider} 实现。提供者通过此对象获取输入信息、判断采集模式、
 * 定位达人标识。</p>
 *
 * <p><b>业务域：</b>达人域 / 达人资料采集子域</p>
 *
 * @see TalentProfileProvider
 * @see TalentProfileSyncService
 * @see com.colonel.saas.service.talent.TalentInputParseResult
 */
@Data
@Builder
public class TalentProfileQuery {

    /** 用户原始输入文本（如抖音号、主页链接、secUid），已 trim */
    private String input;

    /** 是否强制刷新：{@code true} 表示忽略已有的成功同步记录，强制重新采集 */
    private boolean forceRefresh;

    /** 关联的达人 ID（UUID），若已存在对应达人记录则填充 */
    private UUID talentId;

    /** 是否为手动填写模式：{@code true} 时由 ManualTalentProvider 处理 {@link #manualPayload} */
    private boolean manualFill;

    /**
     * 已解析的达人标识信息，由 {@link com.colonel.saas.service.talent.TalentInputParser} 生成。
     * 包含 douyinNo、uid、secUid、profileUrl 等结构化标识，供各 Provider 定位达人。
     */
    private TalentInputParseResult parsed;

    /**
     * 手动填写模式下的用户原始数据负载。
     * 键为 {@link TalentProfileFieldNames} 中定义的字段名，值为用户填写的值。
     * 仅在 {@link #manualFill}=true 时有值。
     */
    private Map<String, Object> manualPayload;
}
