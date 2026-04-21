package com.colonel.saas.service;

import com.colonel.saas.entity.SampleStatusLog;
import com.colonel.saas.mapper.SampleStatusLogMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class SampleStatusLogService {

    private final SampleStatusLogMapper sampleStatusLogMapper;

    public SampleStatusLogService(SampleStatusLogMapper sampleStatusLogMapper) {
        this.sampleStatusLogMapper = sampleStatusLogMapper;
    }

    public void log(UUID requestId, Integer fromStatus, Integer toStatus, UUID operatorId, String remark) {
        SampleStatusLog log = new SampleStatusLog();
        log.setRequestId(requestId);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setOperatorId(operatorId);
        log.setOperateTime(LocalDateTime.now());
        log.setRemark(remark);
        sampleStatusLogMapper.insert(log);
    }
}
