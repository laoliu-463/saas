package com.colonel.saas.vo.sample;

/**
 * 当前用户在合作单上的私有备注。
 */
public record SamplePrivateNoteVO(
        String content,
        Integer version) {
}
