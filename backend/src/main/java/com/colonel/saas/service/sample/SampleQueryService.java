package com.colonel.saas.service.sample;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.dto.sample.SampleFilterOptionsDTO;
import com.colonel.saas.vo.sample.SampleVO;
import com.colonel.saas.vo.sample.SampleBoardCard;
import com.colonel.saas.vo.sample.SampleLogisticsVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestAttribute;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 寄样查询服务接口：提供寄样列表、详情、筛选选项、导出等只读查询能力。
 *
 * <p>本接口从 {@link SampleApplicationService} 中拆分而来，将只读查询逻辑与状态变更逻辑分离，
 * 遵循单一职责原则。状态变更操作仍保留在 {@link SampleApplicationService} 中。
 *
 * <p>设计原则：
 * <ul>
 *   <li>只读：本接口中的方法不应产生任何状态变更</li>
 *   <li>不变性：字段、筛选条件、导出列顺序保持与原接口完全一致</li>
 *   <li>兼容性：保持原有 API 路径、输入参数、输出字段不变</li>
 * </ul>
 *
 * @see SampleApplicationService
 * @see SampleFilterOptionsService
 */
public interface SampleQueryService {

    /**
     * 分页查询寄样申请列表。
     *
     * @param page            页码（从 1 开始）
     * @param size            每页条数
     * @param keyword         关键字（匹配达人昵称、达人 UID、寄样单号或商品名称）
     * @param status          寄样状态筛选
     * @param channelUserIds  渠道负责人用户 ID 列表（多选）
     * @param recruiterUserId 招商负责人用户 ID
     * @param productKeyword  商品 ID 或商品名称
     * @param shopKeyword     店铺 ID 或店铺名称
     * @param trackingNo      物流单号（精确匹配）
     * @param requestNo       申请编号 / 合作单号（精确匹配）
     * @param talentKeyword   达人昵称或达人号
     * @param cooperationType 合作类型
     * @param sampleOwnerType 寄样负责方
     * @param homeworkType    交作业类型
     * @param recipientName   收货人姓名
     * @param recipientPhone  收货人手机号
     * @param applyStartTime  申请开始时间
     * @param applyEndTime    申请结束时间
     * @param homeworkStartTime 交作业 / 完成开始时间
     * @param homeworkEndTime   交作业 / 完成结束时间
     * @param logisticsCompany  物流公司
     * @param userId          当前登录用户 ID
     * @param deptId          当前用户所属部门 ID
     * @param dataScope       数据权限范围
     * @param roleCodes       当前用户角色编码列表
     * @return 分页查询结果
     */
    PageResult<SampleVO> getSamplePage(
            long page,
            long size,
            String keyword,
            String status,
            List<UUID> channelUserIds,
            UUID recruiterUserId,
            String productKeyword,
            String shopKeyword,
            String trackingNo,
            String requestNo,
            String talentKeyword,
            String cooperationType,
            String sampleOwnerType,
            String homeworkType,
            String recipientName,
            String recipientPhone,
            LocalDateTime applyStartTime,
            LocalDateTime applyEndTime,
            LocalDateTime homeworkStartTime,
            LocalDateTime homeworkEndTime,
            String logisticsCompany,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            Object roleCodes);

    /**
     * 分页查询寄样申请列表（简化版）。
     *
     * @param page      页码
     * @param size      每页条数
     * @param keyword   关键字
     * @param status    寄样状态
     * @param userId    用户 ID
     * @param deptId    部门 ID
     * @param dataScope 数据范围
     * @param roleCodes 角色编码
     * @return 分页查询结果
     */
    PageResult<SampleVO> getSamplePage(
            long page,
            long size,
            String keyword,
            String status,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            Object roleCodes);

    /**
     * 查询单个寄样申请详情。
     *
     * @param id        寄样申请 ID
     * @param userId    当前登录用户 ID
     * @param deptId    当前用户所属部门 ID
     * @param dataScope 数据权限范围
     * @param roleCodes 当前用户角色编码列表
     * @return 寄样申请详情
     */
    SampleVO getSampleById(UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes);

    /**
     * 导出寄样申请列表为 CSV 文件。
     *
     * @param status          寄样状态
     * @param keyword         关键字
     * @param channelUserIds  渠道负责人用户 ID 列表
     * @param recruiterUserId 招商负责人用户 ID
     * @param productKeyword  商品关键词
     * @param shopKeyword     店铺关键词
     * @param trackingNo      物流单号
     * @param requestNo       申请编号
     * @param talentKeyword   达人关键词
     * @param cooperationType 合作类型
     * @param sampleOwnerType 寄样负责方
     * @param homeworkType    交作业类型
     * @param recipientName   收货人姓名
     * @param recipientPhone  收货人手机号
     * @param applyStartTime  申请开始时间
     * @param applyEndTime    申请结束时间
     * @param homeworkStartTime 交作业开始时间
     * @param homeworkEndTime   交作业结束时间
     * @param logisticsCompany  物流公司
     * @param userId          用户 ID
     * @param deptId          部门 ID
     * @param dataScope       数据范围
     * @param roleCodes       角色编码
     * @param response        HTTP 响应
     * @throws IOException IO 异常
     */
    void exportSamples(
            String status,
            String keyword,
            List<UUID> channelUserIds,
            UUID recruiterUserId,
            String productKeyword,
            String shopKeyword,
            String trackingNo,
            String requestNo,
            String talentKeyword,
            String cooperationType,
            String sampleOwnerType,
            String homeworkType,
            String recipientName,
            String recipientPhone,
            LocalDateTime applyStartTime,
            LocalDateTime applyEndTime,
            LocalDateTime homeworkStartTime,
            LocalDateTime homeworkEndTime,
            String logisticsCompany,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            Object roleCodes,
            HttpServletResponse response) throws IOException;

    /**
     * 查询寄样台看板数据。
     *
     * @param userId    用户 ID
     * @param deptId    部门 ID
     * @param dataScope 数据范围
     * @param roleCodes 角色编码
     * @return 看板数据
     */
    Map<String, List<SampleBoardCard>> getSampleBoard(UUID userId, UUID deptId, DataScope dataScope, Object roleCodes);

    /**
     * 查询寄样物流信息。
     *
     * @param id        寄样申请 ID
     * @param userId    用户 ID
     * @param deptId    部门 ID
     * @param dataScope 数据范围
     * @param roleCodes 角色编码
     * @return 物流信息
     */
    SampleLogisticsVO getSampleLogistics(UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes);
}
