package com.colonel.saas.dto.sample;

import jakarta.validation.constraints.Size;

/**
 * 当前用户私有备注写入请求。
 */
public record SamplePrivateNoteRequest(
        @Size(max = 200) String content) {
}
