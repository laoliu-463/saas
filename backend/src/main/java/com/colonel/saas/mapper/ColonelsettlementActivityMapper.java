package com.colonel.saas.mapper;

import com.colonel.saas.entity.ColonelsettlementActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 团长结算活动数据访问层
 * <p>
 * 对应数据库表：colonelsettlement_activity
 * 所属业务领域：业绩域 - 结算活动管理
 * 主要操作：结算活动的查询、插入、更新，支持种子数据写入和真实活动元数据同步
 * </p>
 *
 * @see com.colonel.saas.entity.ColonelsettlementActivity
 */
@Mapper
public interface ColonelsettlementActivityMapper {

    /**
     * 统计本地已有活动总数
     *
     * @return 本地活动记录总数
     */
    long countLocalActivities();

    /**
     * 按状态和时间分页统计活动数量
     *
     * @param status 活动状态筛选条件，为 null 时不过滤
     * @param now    当前时间，用于判断活动是否在有效期内
     * @return 满足条件的活动总数
     */
    long countPage(@Param("status") Integer status, @Param("now") LocalDateTime now);

    /**
     * 分页查询活动列表
     *
     * @param offset 分页偏移量
     * @param limit  每页条数
     * @param status 活动状态筛选条件，为 null 时不过滤
     * @param now    当前时间，用于判断活动状态
     * @return 活动列表
     */
    List<ColonelsettlementActivity> selectPage(
            @Param("offset") long offset,
            @Param("limit") long limit,
            @Param("status") Integer status,
            @Param("now") LocalDateTime now
    );

    /**
     * 分页查询活动列表（用于导出功能）
     *
     * @param offset       分页偏移量
     * @param limit        每页条数
     * @param activityName 活动名称模糊搜索条件
     * @param now          当前时间
     * @return 活动列表
     */
    List<ColonelsettlementActivity> selectExportPage(
            @Param("offset") long offset,
            @Param("limit") long limit,
            @Param("activityName") String activityName,
            @Param("now") LocalDateTime now
    );

    /**
     * 插入种子活动数据
     * <p>
     * 用于初始化或导入基础活动数据，不使用 UPSERT 策略
     * </p>
     *
     * @param id           活动主键 UUID
     * @param activityId   抖音活动 ID
     * @param activityName 活动名称
     * @param startTime    活动开始时间
     * @param endTime      活动结束时间
     * @param statusText   活动状态文本描述
     * @param createTime   创建时间
     */
    void insertSeedActivity(
            @Param("id") UUID id,
            @Param("activityId") String activityId,
            @Param("activityName") String activityName,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("statusText") String statusText,
            @Param("createTime") LocalDateTime createTime
    );

    /**
     * 插入或更新真实活动元数据
     * <p>
     * 基于 activityId 做 UPSERT 操作，当活动已存在时更新所有元数据字段。
     * 用于从抖音上游同步真实活动信息到本地。
     * </p>
     *
     * @param id             活动主键 UUID
     * @param activityId     抖音活动 ID（UPSERT 匹配键）
     * @param activityName   活动名称
     * @param shopId         商家店铺 ID
     * @param shopName       商家店铺名称
     * @param colonelBuyinId 团长百应 ID
     * @param commissionRate 佣金费率
     * @param serviceRate    服务费率
     * @param startTime      活动开始时间
     * @param endTime        活动结束时间
     * @param statusText     活动状态文本描述
     * @param lastSyncAt     最后同步时间
     * @param extraData      扩展数据（JSON 格式）
     */
    void upsertRealActivityMeta(
            @Param("id") UUID id,
            @Param("activityId") String activityId,
            @Param("activityName") String activityName,
            @Param("shopId") Long shopId,
            @Param("shopName") String shopName,
            @Param("colonelBuyinId") Long colonelBuyinId,
            @Param("commissionRate") java.math.BigDecimal commissionRate,
            @Param("serviceRate") java.math.BigDecimal serviceRate,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("statusText") String statusText,
            @Param("lastSyncAt") LocalDateTime lastSyncAt,
            @Param("extraData") Map<String, Object> extraData
    );

    /**
     * 根据抖音活动 ID 查询活动详情
     *
     * @param activityId 抖音活动 ID
     * @return 对应的活动记录，不存在时返回 null
     */
    ColonelsettlementActivity selectByActivityId(@Param("activityId") String activityId);

    /**
     * 根据抖音活动 ID 查询扩展数据
     *
     * @param activityId 抖音活动 ID
     * @return 扩展数据 Map，不存在时返回 null
     */
    Map<String, Object> selectExtraDataByActivityId(@Param("activityId") String activityId);

    /**
     * 活动列表同步时写入活动摘要（状态码/文案、时间窗口等）。
     */
    void upsertListActivitySummary(
            @Param("id") UUID id,
            @Param("activityId") String activityId,
            @Param("activityName") String activityName,
            @Param("colonelBuyinId") Long colonelBuyinId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("activityStatusCode") Integer activityStatusCode,
            @Param("activityStatusText") String activityStatusText,
            @Param("lastSyncAt") LocalDateTime lastSyncAt);

    /**
     * 更新活动级招商组长分配。
     */
    int updateRecruiterAssignment(
            @Param("activityId") String activityId,
            @Param("recruiterUserId") UUID recruiterUserId,
            @Param("recruiterDeptId") UUID recruiterDeptId,
            @Param("assignedAt") LocalDateTime assignedAt,
            @Param("assignedBy") UUID assignedBy);

    /**
     * 批量读取活动分配摘要（用于列表回填）。
     */
    List<ColonelsettlementActivity> selectAssignmentByActivityIds(@Param("activityIds") List<String> activityIds);

    /**
     * 批量读取活动 ID → 活动名映射（轻量字段，仅取 activity_id 和 activity_name）。
     * <p>用于商品库视图构造时回填 {@code Product.activityName}，避免对每条商品单独查库。
     * 上游 activityId 列表为空时直接返回空集合。</p>
     *
     * @param activityIds 抖店活动 ID 集合
     * @return 活动记录列表（仅含 activityId 和 name 两个字段）
     */
    List<ColonelsettlementActivity> selectNamesByActivityIds(@Param("activityIds") List<String> activityIds);

    /**
     * 按分配筛选条件统计活动数量（本地库分页）。
     */
    long countPageByAssignment(
            @Param("activityStatusCode") Integer activityStatusCode,
            @Param("assignmentFilter") String assignmentFilter,
            @Param("recruiterUserId") UUID recruiterUserId,
            @Param("activityKeyword") String activityKeyword);

    /**
     * 按分配筛选条件分页查询活动（本地库分页）。
     */
    List<ColonelsettlementActivity> selectPageByAssignment(
            @Param("offset") long offset,
            @Param("limit") long limit,
            @Param("activityStatusCode") Integer activityStatusCode,
            @Param("assignmentFilter") String assignmentFilter,
            @Param("recruiterUserId") UUID recruiterUserId,
            @Param("activityKeyword") String activityKeyword);

    /**
     * 读取需要执行活动商品快照同步的活动 ID。
     */
    List<String> selectActiveActivityIds(
            @Param("limit") int limit,
            @Param("lastSyncedBefore") LocalDateTime lastSyncedBefore);

    /**
     * 商品同步 dry-run 探针使用的活动范围查询。
     */
    List<String> selectActivityIdsForProductSyncProbe(
            @Param("scope") String scope,
            @Param("limit") int limit,
            @Param("recentSince") LocalDateTime recentSince,
            @Param("activityIds") List<String> activityIds);

    /**
     * 更新活动商品快照同步时间。
     */
    int touchLastSyncAt(
            @Param("activityId") String activityId,
            @Param("syncedAt") LocalDateTime syncedAt);

    /**
     * P8-阻断#4 修复: 单独更新活动状态同步时间戳,
     * 仅在 ColonelActivityListSyncService 成功落库活动状态时调用。
     */
    int touchActivityStatusSyncedAt(
            @Param("activityId") String activityId,
            @Param("syncedAt") LocalDateTime syncedAt);
}
