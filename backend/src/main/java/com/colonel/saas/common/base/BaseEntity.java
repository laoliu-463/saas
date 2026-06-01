package com.colonel.saas.common.base;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.colonel.saas.common.handler.UUIDTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 所有持久化实体的抽象基类。
 *
 * <p>定义了数据库表中所有实体共同拥有的公共字段：
 * 主键 ID、创建/更新时间、创建/更新人、逻辑删除标记。
 * 所有业务实体（如 Talent、Product、Order 等）均应继承此类。</p>
 *
 * <h3>字段填充策略</h3>
 * <ul>
 *   <li>{@code createTime} / {@code createBy}：仅在 INSERT 时由 MyBatis-Plus 自动填充</li>
 *   <li>{@code updateTime} / {@code updateBy}：在 INSERT 和 UPDATE 时均自动填充</li>
 *   <li>填充逻辑由 {@link com.colonel.saas.common.handler.UUIDTypeHandler} 和
 *       项目的 MetaObjectHandler 实现共同完成</li>
 * </ul>
 *
 * <h3>主键策略</h3>
 * <p>采用 {@link IdType#INPUT}，即主键由应用层在 INSERT 前主动赋值（UUID），
 * 而非依赖数据库自增。这样做的好处是：实体在持久化前即可获取 ID，
 * 便于提前关联、缓存和事件发布。</p>
 *
 * <h3>逻辑删除</h3>
 * <p>{@code deleted} 字段配合 {@link TableLogic} 注解，实现逻辑删除：
 * 调用 MyBatis-Plus 的 delete 方法时，实际执行的是 UPDATE SET deleted=1，
 * 查询时自动追加 WHERE deleted=0 条件。</p>
 *
 * @see VersionedEntity 需要乐观锁的实体继承此类的子类
 * @see UUIDTypeHandler UUID 类型处理器
 */
@Data
public abstract class BaseEntity implements Serializable {

    /**
     * 实体主键（UUID 类型）。
     *
     * <p>使用 INPUT 策略，由应用层在持久化前通过 {@code UUID.randomUUID()} 主动生成。
     * {@link UUIDTypeHandler} 负责 MyBatis 层面的 UUID 与 PostgreSQL uuid 类型的双向映射。</p>
     */
    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UUIDTypeHandler.class)
    private UUID id;

    /**
     * 记录创建时间。
     *
     * <p>在 INSERT 操作时由 MyBatis-Plus MetaObjectHandler 自动填充为当前时间，
     * 业务代码无需手动赋值。</p>
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 记录最后更新时间。
     *
     * <p>在 INSERT 和 UPDATE 操作时由 MetaObjectHandler 自动填充，
     * 反映该记录最近一次被修改的时间点。</p>
     */
    @TableField(fill = FieldFill.INSERT_UPDATE, update = "now()")
    private LocalDateTime updateTime;

    /**
     * 创建人用户 ID。
     *
     * <p>在 INSERT 操作时由 MetaObjectHandler 从当前登录上下文中提取并填充。
     * 用于审计追踪，标识数据的初始创建者。</p>
     */
    @TableField(fill = FieldFill.INSERT)
    private UUID createBy;

    /**
     * 最后更新人用户 ID。
     *
     * <p>在 INSERT 和 UPDATE 操作时由 MetaObjectHandler 自动填充，
     * 用于审计追踪，标识最近一次修改数据的操作者。</p>
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private UUID updateBy;

    /**
     * 逻辑删除标记。
     *
     * <p>0 表示未删除（正常状态），1 表示已删除。
     * 配合 {@link TableLogic} 注解，MyBatis-Plus 在查询时自动追加
     * {@code WHERE deleted = 0} 条件，删除操作转为 {@code UPDATE SET deleted = 1}。</p>
     *
     * <p>默认值为 0，新建实体无需显式赋值。</p>
     */
    @TableLogic
    private Integer deleted = 0;
}
