package com.colonel.saas.dto.douyin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DouyinQuickSampleStatusResponse {
    private boolean supported;
    private String status;
    private boolean realConnected;
    private String message;
    private boolean fallbackEnabled;
}
