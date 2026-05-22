package com.colonel.saas.common.base;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 带乐观锁版本号的实体基类（LCK-01）。仅核心并发写表继承此类。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class VersionedEntity extends BaseEntity {

    @Version
    @TableField("version")
    private Integer version;
}
