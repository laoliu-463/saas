package com.colonel.saas.service.talent;

import com.colonel.saas.common.enums.TalentInputType;
import com.colonel.saas.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TalentInputParser {

    private static final Pattern SEC_UID_PATTERN = Pattern.compile("sec_uid=([^&]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern USER_PATH_PATTERN = Pattern.compile("/user/([^/?]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern UID_PATTERN = Pattern.compile("^\\d{5,30}$");
    private static final Pattern DOUYIN_NO_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]{3,50}$");

    private TalentInputParser() {
    }

    public static TalentInputParseResult parse(String inputValue) {
        if (!StringUtils.hasText(inputValue)) {
            throw new BusinessException("达人抖音号或链接不能为空");
        }
        String input = inputValue.trim();

        if (input.startsWith("http://") || input.startsWith("https://")) {
            return parseUrl(input);
        }

        if (UID_PATTERN.matcher(input).matches()) {
            return TalentInputParseResult.builder()
                    .inputType(TalentInputType.UID)
                    .uid(input)
                    .douyinUid(input)
                    .rawInput(input)
                    .build();
        }

        if (DOUYIN_NO_PATTERN.matcher(input).matches()) {
            return TalentInputParseResult.builder()
                    .inputType(TalentInputType.DOUYIN_NO)
                    .douyinNo(input)
                    .douyinUid(input)
                    .rawInput(input)
                    .build();
        }

        return TalentInputParseResult.builder()
                .inputType(TalentInputType.UNKNOWN)
                .rawInput(input)
                .douyinUid(input)
                .build();
    }

    private static TalentInputParseResult parseUrl(String url) {
        String secUid = extractFirst(SEC_UID_PATTERN, url);
        String userPath = extractFirst(USER_PATH_PATTERN, url);
        TalentInputType type = StringUtils.hasText(secUid) ? TalentInputType.SEC_UID : TalentInputType.PROFILE_URL;
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

    private static String extractFirst(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}

