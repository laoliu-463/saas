package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.PromotionLink;
import org.apache.ibatis.annotations.Mapper;

/**
 * 推广链接数据访问层
 * <p>
 * 对应数据库表：promotion_link
 * 所属业务领域：商品域 - 推广链接管理
 * 主要操作：推广链接（转链记录）的 CRUD 基础操作（通过 MyBatis-Plus BaseMapper 提供）
 * </p>
 *
 * @see com.colonel.saas.entity.PromotionLink
 */
@Mapper
public interface PromotionLinkMapper extends BaseMapper<PromotionLink> {
}
