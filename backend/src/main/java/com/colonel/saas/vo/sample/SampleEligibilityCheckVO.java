package com.colonel.saas.vo.sample;

import lombok.Data;

import java.util.List;

/**
 * 达人寄样资格校验结果 VO（Value Object）。
 * <p>
 * 用于展示达人申请寄样时的资质校验结果，包括是否满足条件、不满足的原因、
 * 以及当前值与最低门槛的对比数据。帮助运营人员和达人理解寄样资格判断依据。
 * </p>
 * <p>
 * 主要职责：
 * <ul>
 *   <li>标识达人是否具备寄样资格</li>
 *   <li>列出不满足条件的具体原因</li>
 *   <li>展示门槛值与当前值的对比数据（近30天销量、达人等级）</li>
 *   <li>判断是否需要达人填写申请理由</li>
 * </ul>
 * </p>
 * <p>
 * 业务领域：寄样域（Sample Domain）— 资质校验。
 * </p>
 */
@Data
public class SampleEligibilityCheckVO {

    /**
     * 是否具备寄样资格。
     * <p>
     * {@code true} 表示达人满足所有寄样条件，可以提交寄样申请；
     * {@code false} 表示存在不满足的条件，具体原因见 {@link #reasons}。
     * </p>
     */
    private boolean eligible;

    /**
     * 是否需要填写申请理由。
     * <p>
     * 当达人资质处于"边缘"（如粉丝数接近门槛）时，系统可能要求达人
     * 填写额外的申请理由供运营人员参考审核。
     * </p>
     */
    private boolean needReason;

    /**
     * 不满足条件的原因列表。
     * <p>
     * 当 {@link #eligible} 为 {@code false} 时，列出所有不满足的条件描述，
     * 如"近30天销量不足"、"达人等级未达标"等。
     * </p>
     */
    private List<String> reasons;

    /**
     * 近30天销量最低门槛。
     * <p>
     * 该商品要求达人近30天的最低订单量，用于判断达人是否达到销量要求。
     * </p>
     */
    private Long min30DaySales;

    /**
     * 最低达人等级要求。
     * <p>
     * 该商品要求达人的最低平台等级，如 "L3"、"L4" 等。
     * </p>
     */
    private String minLevel;

    /**
     * 达人当前近30天实际销量。
     * <p>
     * 用于与 {@link #min30DaySales} 对比，展示差距。
     * </p>
     */
    private Long current30DaySales;

    /**
     * 达人当前等级。
     * <p>
     * 用于与 {@link #minLevel} 对比，展示当前等级是否达标。
     * </p>
     */
    private String currentLevel;
}
