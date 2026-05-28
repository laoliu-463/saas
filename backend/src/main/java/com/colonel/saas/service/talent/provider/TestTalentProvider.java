package com.colonel.saas.service.talent.provider;

import com.colonel.saas.common.enums.TalentDataSource;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.service.talent.TalentDataProvider;
import com.colonel.saas.service.talent.TalentEnrichContext;
import com.colonel.saas.service.talent.TalentEnrichResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试数据源 —— 达人补全 Provider（测试/演示专用）。
 *
 * <p>仅在 {@code talent.enrich.mode=test} 激活时注册；默认不激活。
 * 优先级最高（1），确保测试模式下不会命中其他真实 Provider。</p>
 *
 * <ul>
 *   <li><b>职责</b>：基于达人 ID 的哈希值生成确定性的演示数据，用于开发调试和 E2E 验收</li>
 *   <li><b>优先级</b>：priority = 1，最高优先级</li>
 *   <li><b>数据源标识</b>：{@link TalentDataSource#TEST}</li>
 *   <li><b>架构角色</b>：策略模式中的测试策略，由 {@link com.colonel.saas.service.talent.TalentEnrichOrchestrator} 统一调度</li>
 *   <li><b>业务域</b>：达人域 → 数据补全子领域</li>
 *   <li><b>特殊关键词</b>：
 *     <ul>
 *       <li>{@code test_fail} — 模拟异常，抛出 {@link IllegalStateException}</li>
 *       <li>{@code test_empty} — 模拟无数据，返回空结果</li>
 *       <li>{@code test_partial} — 模拟部分数据，移除 ipLocation 和 followingCount</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * @see com.colonel.saas.service.talent.TalentDataProvider  策略接口
 * @see com.colonel.saas.service.talent.TalentEnrichOrchestrator  编排器
 * @see TalentDataSource#TEST  数据源枚举值
 */
@Component
@Order(1)
@ConditionalOnProperty(prefix = "talent.enrich", name = "mode", havingValue = "test")
public class TestTalentProvider implements TalentDataProvider {

    /** 中国地区列表，用于生成演示 ipLocation 字段 */
    private static final String[] REGIONS = {"广东深圳", "浙江杭州", "江苏南京", "上海浦东", "北京朝阳", "山东青岛"};

    /**
     * 返回本 Provider 对应的数据源标识。
     *
     * @return {@link TalentDataSource#TEST}，标识测试数据源
     */
    @Override
    public TalentDataSource source() {
        return TalentDataSource.TEST;
    }

    /**
     * 返回本 Provider 的调度优先级。
     *
     * <p>值越小越优先。本 Provider 优先级为 1，是所有 Provider 中最高的，
     * 确保在测试模式下第一个被调度。</p>
     *
     * @return 优先级数值 1
     */
    @Override
    public int priority() {
        return 1;
    }

    /**
     * 判断当前上下文是否满足本 Provider 的执行条件。
     *
     * @param context 达人补全上下文，包含待补全的 {@code Talent} 实体
     * @return {@code true} 当上下文和达人实体均非空时
     */
    @Override
    public boolean supports(TalentEnrichContext context) {
        return context != null && context.talent() != null;
    }

    /**
     * 基于达人 ID 生成确定性的演示数据。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>通过 {@link #resolveInput} 获取达人标识字符串（优先级：douyinUid > douyinNo > uid > secUid > profileUrl > "test_default"）</li>
     *   <li>检查特殊关键词：<code>test_fail</code> 抛异常、<code>test_empty</code> 返回空结果</li>
     *   <li>以标识字符串的 hashCode 作为种子，生成 7 个字段的演示数据</li>
     *   <li>检查 <code>test_partial</code> 关键词：移除 ipLocation 和 followingCount 以模拟不完整数据</li>
     *   <li>返回包含所有字段的补全结果</li>
     * </ol>
     *
     * @param context 达人补全上下文
     * @return 包含演示字段的补全结果
     * @throws IllegalStateException 当输入包含 {@code test_fail} 关键词时，模拟数据拉取失败
     */
    @Override
    public TalentEnrichResult enrich(TalentEnrichContext context) {
        // 第一步：获取达人标识作为随机种子
        Talent talent = context.talent();
        String input = resolveInput(talent).toLowerCase();

        // 第二步：处理特殊测试关键词 —— test_fail 模拟异常
        if (input.contains("test_fail")) {
            throw new IllegalStateException("test provider simulated failure");
        }

        // 第三步：处理特殊测试关键词 —— test_empty 模拟无数据
        if (input.contains("test_empty")) {
            return TalentEnrichResult.empty(source(), "test provider returns empty data");
        }

        // 第四步：基于 hashCode 生成确定性演示数据
        Map<String, Object> fields = new LinkedHashMap<>();
        long seed = Math.abs(resolveInput(talent).hashCode());
        fields.put("nickname", "演示达人-" + (seed % 1000));
        fields.put("avatarUrl", "https://test.local/avatar/" + (seed % 100) + ".png");
        fields.put("fans", 10_000L + (seed % 900_000L));
        fields.put("likesCount", 100_000L + (seed % 9_000_000L));
        fields.put("followingCount", 100L + (seed % 2000L));
        fields.put("worksCount", 10L + (seed % 500L));
        fields.put("ipLocation", testRegion(seed));

        // 第五步：处理特殊测试关键词 —— test_partial 模拟部分数据缺失
        if (input.contains("test_partial")) {
            fields.remove("ipLocation");
            fields.remove("followingCount");
        }

        return TalentEnrichResult.of(source(), fields, "test provider generated demo talent data");
    }

    /**
     * 从达人实体中解析标识字符串，用于生成确定性种子。
     *
     * <p>解析优先级：douyinUid → douyinNo → uid → secUid → profileUrl → "test_default"。</p>
     *
     * @param talent 达人实体
     * @return 非空的标识字符串（已 trim）
     */
    private String resolveInput(Talent talent) {
        if (StringUtils.hasText(talent.getDouyinUid())) {
            return talent.getDouyinUid().trim();
        }
        if (StringUtils.hasText(talent.getDouyinNo())) {
            return talent.getDouyinNo().trim();
        }
        if (StringUtils.hasText(talent.getUid())) {
            return talent.getUid().trim();
        }
        if (StringUtils.hasText(talent.getSecUid())) {
            return talent.getSecUid().trim();
        }
        if (StringUtils.hasText(talent.getProfileUrl())) {
            return talent.getProfileUrl().trim();
        }
        return "test_default";
    }

    /**
     * 根据种子值选取演示地区。
     *
     * @param seed 哈希种子
     * @return {@link #REGIONS} 中的某个地区名称
     */
    private String testRegion(long seed) {
        return REGIONS[(int) (seed % REGIONS.length)];
    }
}
