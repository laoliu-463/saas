package com.colonel.saas.douyin.api;

import com.colonel.saas.douyin.DouyinApiClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 精选联盟达人转链接 API 客户端。
 * <p>
 * 封装抖音精选联盟达人商品链接转换接口，将商品原始链接转换为带推广标识的推广链接。
 *
 * <ul>
 *   <li>链接转换 — 将商品 URL 转换为带 pick_extra 标识的推广链接</li>
 *   <li>pick_extra 标准化 — 截断超过 20 字符的附加标识</li>
 * </ul>
 *
 * 所属业务领域：精选联盟 / 达人推广
 *
 * @see DouyinApiClient
 */
@Service
public class TalentApi {

    private final DouyinApiClient douyinApiClient;

    public TalentApi(DouyinApiClient douyinApiClient) {
        this.douyinApiClient = douyinApiClient;
    }

    /**
     * 将商品原始链接转换为带推广标识的推广链接。
     * <p>
     * 调用 {@code buyin.instPickSourceConvert} 接口，传入商品 URL 和标准化后的 pick_extra。
     *
     * @param productUrl 商品原始链接
     * @param pickExtra  推广附加标识（超过 20 字符时自动截断至末尾 20 字符）
     * @return 上游 API 响应，包含转换后的推广链接
     */
    public Map<String, Object> convertLink(String productUrl, String pickExtra) {
        // 第一步：组装请求参数，商品原始链接 + 标准化后的推广附加标识
        Map<String, Object> params = new HashMap<>();
        params.put("product_url", productUrl);
        params.put("pick_extra", normalizePickExtra(pickExtra));
        // 第二步：调用抖音精选联盟达人转链接接口
        return douyinApiClient.post("buyin.instPickSourceConvert", params);
    }

    /**
     * 标准化推广附加标识。
     * <p>
     * 当 pick_extra 超过 20 字符时，截取末尾 20 字符以适配上游接口限制。
     *
     * @param pickExtra 原始推广附加标识
     * @return 标准化后的 pick_extra，或 null
     */
    private String normalizePickExtra(String pickExtra) {
        // 第一步：空值直接返回 null
        if (pickExtra == null) {
            return null;
        }
        // 第二步：长度不超过 20 字符，原样返回
        if (pickExtra.length() <= 20) {
            return pickExtra;
        }
        // 第三步：超过 20 字符时截取末尾 20 字符，适配上游接口限制
        return pickExtra.substring(pickExtra.length() - 20);
    }
}
