package com.colonel.saas.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页查询响应结构体。
 *
 * <p>封装 MyBatis-Plus {@link IPage} 的分页结果，提供前端渲染分页组件所需的
 * total（总记录数）、page（当前页码）、size（每页条数）、records（当前页数据列表）。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 在 Service 层执行分页查询
 * IPage<Talent> page = talentMapper.selectPage(new Page<>(1, 20), queryWrapper);
 *
 * // 转换为 PageResult 返回
 * return PageResult.of(page);
 * }</pre>
 *
 * <h3>与 ApiResult 的配合</h3>
 * <p>通常作为 {@link ApiResult#ok(Object)} 的 data 参数使用：</p>
 * <pre>{@code
 * PageResult<Talent> pageResult = PageResult.of(page);
 * return ApiResult.ok(pageResult);
 * }</pre>
 *
 * @param <T> 分页记录的元素类型（通常为 DTO 或 VO）
 * @see ApiResult 统一响应结构
 * @see com.baomidou.mybatisplus.core.metadata.IPage MyBatis-Plus 分页接口
 */
@Data
public class PageResult<T> implements Serializable {

    /** 符合查询条件的总记录数 */
    private long total;

    /** 当前页码（从 1 开始） */
    private long page;

    /** 每页记录数 */
    private long size;

    /** 当前页的记录列表 */
    private List<T> records;

    /**
     * 从 MyBatis-Plus 的 {@link IPage} 转换为 {@link PageResult}。
     *
     * <p>映射关系：</p>
     * <ul>
     *   <li>{@code IPage.getTotal()} → {@code PageResult.total}</li>
     *   <li>{@code IPage.getCurrent()} → {@code PageResult.page}</li>
     *   <li>{@code IPage.getSize()} → {@code PageResult.size}</li>
     *   <li>{@code IPage.getRecords()} → {@code PageResult.records}</li>
     * </ul>
     *
     * @param page MyBatis-Plus 分页查询结果
     * @param <T>  记录元素类型
     * @return 包含分页信息和当前页数据的 PageResult
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setPage(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(page.getRecords());
        return result;
    }
}
