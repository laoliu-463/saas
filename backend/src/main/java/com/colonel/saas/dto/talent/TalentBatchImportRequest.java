package com.colonel.saas.dto.talent;

import java.util.List;

/**
 * 达人批量导入请求 DTO。
 * <p>
 * 用于通过抖音账号列表批量创建或更新达人记录。
 * 关联业务领域：达人域（Talent）。
 * </p>
 */
public record TalentBatchImportRequest(
        /** 抖音账号列表，每个元素为一个达人标识（抖音号、UID 或主页链接） */
        List<String> accounts) {
}
