package com.colonel.saas.service.talent;

import com.colonel.saas.common.enums.TalentInputType;
import com.colonel.saas.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 达人输入解析器 —— 将用户输入的原始文本解析为结构化的达人标识信息。
 *
 * <p><b>职责：</b></p>
 * <ul>
 *   <li>识别用户输入的类型：完整链接（含 secUid / user path）、UID（纯数字）、抖音号（字母数字组合）</li>
 *   <li>从抖音链接中提取 secUid（{@code sec_uid=xxx} 参数）或用户路径（{@code /user/xxx}）</li>
 *   <li>自动补全短链接前缀（v.douyin.com 无协议时补 https://）</li>
 *   <li>解析失败时返回 UNKNOWN 类型，保留原始输入供人工处理</li>
 * </ul>
 *
 * <p><b>在架构中的角色：</b>作为达人资料采集链路的入口工具类，
 * 被前端"添加达人"功能调用。解析结果 {@link TalentInputParseResult}
 * 被传递给 {@link TalentEnrichOrchestrator} 用于定位达人。</p>
 *
 * <p><b>业务域：</b>达人域 / 达人资料采集子域</p>
 *
 * @see TalentInputParseResult
 * @see TalentInputType
 */
public final class TalentInputParser {

    /** 匹配 URL 中的 sec_uid 参数（如 sec_uid=MS4wLjABAAAA...） */
    private static final Pattern SEC_UID_PATTERN = Pattern.compile("sec_uid=([^&]+)", Pattern.CASE_INSENSITIVE);

    /** 匹配 URL 中的 /user/ 路径（如 /user/MS4wLjABAAAA...） */
    private static final Pattern USER_PATH_PATTERN = Pattern.compile("/user/([^/?]+)", Pattern.CASE_INSENSITIVE);

    /** 匹配 v.douyin.com 短链接格式（可能不含协议前缀） */
    private static final Pattern V_DOUYIN_USER_PATTERN = Pattern.compile("v\\.douyin\\.com/[^\\s]+", Pattern.CASE_INSENSITIVE);

    /** 匹配纯数字 UID（5-30 位数字，对应抖音用户数字 ID） */
    private static final Pattern UID_PATTERN = Pattern.compile("^\\d{5,30}$");

    /** 匹配抖音号格式（3-50 位字母、数字、下划线、点、横线组合） */
    private static final Pattern DOUYIN_NO_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]{3,50}$");

    /** 私有构造器，工具类禁止实例化 */
    private TalentInputParser() {
    }

    /**
     * 解析用户输入的达人标识文本，返回结构化的解析结果。
     *
     * <p>解析优先级（从高到低）：</p>
     * <ol>
     *   <li>若输入为 URL（http/https 开头或含 v.douyin.com），调用 {@link #parseUrl(String)} 提取 secUid 或 user path</li>
     *   <li>若输入匹配纯数字 UID 格式（5-30 位数字），识别为 UID 类型</li>
     *   <li>若输入匹配抖音号格式（字母数字组合），识别为 DOUYIN_NO 类型</li>
     *   <li>以上均不匹配，识别为 UNKNOWN 类型，保留原始输入</li>
     * </ol>
     *
     * @param inputValue 用户输入的原始文本（抖音号、链接、UID 等）
     * @return 结构化的解析结果，包含输入类型和提取出的各种标识
     * @throws BusinessException 若输入为空或空白
     */
    public static TalentInputParseResult parse(String inputValue) {
        // 第一步：校验输入非空
        if (!StringUtils.hasText(inputValue)) {
            throw BusinessException.param("达人抖音号或链接不能为空");
        }
        String input = inputValue.trim();

        // 第二步：判断是否为 URL 类型（完整协议或 v.douyin.com 短链接）
        if (input.startsWith("http://") || input.startsWith("https://") || V_DOUYIN_USER_PATTERN.matcher(input).find()) {
            // 补全短链接缺失的协议前缀
            String url = input.startsWith("http://") || input.startsWith("https://") ? input : "https://" + input;
            return parseUrl(url);
        }

        // 第三步：判断是否为纯数字 UID（5-30 位数字）
        if (UID_PATTERN.matcher(input).matches()) {
            return TalentInputParseResult.builder()
                    .inputType(TalentInputType.UID)
                    .uid(input)
                    .douyinUid(input)
                    .rawInput(input)
                    .build();
        }

        // 第四步：判断是否为抖音号格式（字母数字下划线等组合，3-50 位）
        if (DOUYIN_NO_PATTERN.matcher(input).matches()) {
            return TalentInputParseResult.builder()
                    .inputType(TalentInputType.DOUYIN_NO)
                    .douyinNo(input)
                    .douyinUid(input)
                    .rawInput(input)
                    .build();
        }

        // 第五步：以上均不匹配，标记为 UNKNOWN，保留原始输入供人工处理
        return TalentInputParseResult.builder()
                .inputType(TalentInputType.UNKNOWN)
                .rawInput(input)
                .douyinUid(input)
                .build();
    }

    /**
     * 从 URL 中提取达人标识信息。
     *
     * <p>优先尝试从 URL 参数中提取 {@code sec_uid}；
     * 若无则尝试从路径中提取 {@code /user/xxx} 部分作为 user path；
     * 若均无则使用完整 URL 作为兜底标识。</p>
     *
     * @param url 已补全协议前缀的完整 URL
     * @return 包含提取结果的解析结果对象
     */
    private static TalentInputParseResult parseUrl(String url) {
        // 第一步：尝试从 URL 参数中提取 sec_uid
        String secUid = extractFirst(SEC_UID_PATTERN, url);
        // 第二步：尝试从 URL 路径中提取 /user/xxx 部分
        String userPath = extractFirst(USER_PATH_PATTERN, url);
        // 第三步：确定输入类型：有 secUid 则为 SEC_UID，否则为 PROFILE_URL
        TalentInputType type = StringUtils.hasText(secUid) ? TalentInputType.SEC_UID : TalentInputType.PROFILE_URL;
        // 第四步：确定 douyinUid 优先级：secUid > userPath > 完整 URL
        String douyinUid = StringUtils.hasText(secUid) ? secUid : userPath;
        if (!StringUtils.hasText(douyinUid)) {
            douyinUid = url;
        }
        return TalentInputParseResult.builder()
                .inputType(type)
                .profileUrl(url)
                .secUid(secUid)
                .douyinUid(douyinUid)
                .rawInput(url)
                .build();
    }

    /**
     * 从文本中提取正则表达式的第一个捕获组内容。
     *
     * @param pattern 编译好的正则表达式
     * @param text    待匹配的文本
     * @return 第一个捕获组的内容，未匹配时返回 {@code null}
     */
    private static String extractFirst(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}

