package com.colonel.saas.common.base;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 带乐观锁版本号的实体基类（LCK-01）。
 *
 * <p>在 {@link BaseEntity} 的基础上增加 {@code version} 字段，
 * 通过 MyBatis-Plus 的 {@link Version} 注解实现乐观锁机制。
 * 仅核心并发写表（如业绩、配置等会被多人同时修改的表）继承此类。</p>
 *
 * <h3>乐观锁原理</h3>
 * <ol>
 *   <li>读取实体时，同时读取当前 version 值</li>
 *   <li>更新时，MyBatis-Plus 自动在 WHERE 子句中追加 {@code AND version = #{原版本号}}</li>
 *   <li>如果其他事务已更新过该行，version 已变更，当前更新影响行数为 0</li>
 *   <li>通过 {@link com.colonel.saas.common.exception.OptimisticLockSupport#requireUpdated(int)}
 *       检查影响行数，为 0 时抛出冲突异常</li>
 * </ol>
 *
 * <h3>典型使用场景</h3>
 * <ul>
 *   <li>达人提成计算表 — 多人可能同时修改同一达人的提成数据</li>
 *   <li>商品配置表 — 招商经理和系统可能同时更新商品状态</li>
 *   <li>业绩归属表 — 冲正操作需要确保数据未被并发修改</li>
 * </ul>
 *
 * @see BaseEntity 基础实体基类，提供公共字段
 * @see OptimisticLockSupport 乐观锁更新结果校验工具
 * @see com.colonel.saas.common.exception.BusinessException#conflict(String) 冲突异常工厂方法
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class VersionedEntity extends BaseEntity {

    /**
     * 乐观锁版本号。
     *
     * <p>每次 UPDATE 成功后自动递增。MyBatis-Plus 在生成 UPDATE SQL 时，
     * 会自动添加 {@code WHERE version = #{version}} 条件，
     * 并在 SET 子句中追加 {@code version = version + 1}。</p>
     *
     * <p>初始值通常为 0 或 1（由数据库默认值或应用层设定），
     * 无需业务代码手动管理。</p>
     */
    @Version
    @TableField("version")
    private Integer version;
}
