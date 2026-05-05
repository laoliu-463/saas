package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ColonelsettlementActivityService {

    private final ColonelsettlementActivityMapper activityMapper;
    private final boolean seedDemoActivities;

    public ColonelsettlementActivityService(
            ColonelsettlementActivityMapper activityMapper,
            @Value("${app.activities.seed-demo-on-empty:false}") boolean seedDemoActivities) {
        this.activityMapper = activityMapper;
        this.seedDemoActivities = seedDemoActivities;
    }

    @Transactional(rollbackFor = Exception.class)
    public IPage<ColonelsettlementActivity> getPage(long page, long size, Integer status) {
        long current = Math.max(page, 1);
        long pageSize = Math.max(size, 1);
        ensureSeedData();
        LocalDateTime now = LocalDateTime.now();
        long offset = (current - 1) * pageSize;
        List<ColonelsettlementActivity> records = activityMapper.selectPage(offset, pageSize, status, now);
        long total = activityMapper.countPage(status, now);

        Page<ColonelsettlementActivity> result = new Page<>(current, pageSize);
        result.setTotal(total);
        result.setRecords(records);
        return result;
    }

    private void ensureSeedData() {
        if (!seedDemoActivities) {
            return;
        }
        if (activityMapper.countLocalActivities() > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (int i = 1; i <= 5; i++) {
            LocalDateTime startTime = now.minusDays(i * 2L);
            LocalDateTime endTime = now.plusDays(15L - i);
            activityMapper.insertSeedActivity(
                    UUID.nameUUIDFromBytes(("activity-" + i).getBytes()),
                    "LOCAL_ACTIVITY_" + i,
                    "主链路演示活动-" + i,
                    startTime,
                    endTime,
                    i % 2 == 0 ? "已结束" : "进行中",
                    startTime
            );
        }
    }
}

